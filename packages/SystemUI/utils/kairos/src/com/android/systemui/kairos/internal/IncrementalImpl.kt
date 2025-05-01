/*
 * Copyright (C) 2024 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.kairos.internal

import com.android.systemui.kairos.internal.store.StoreEntry
import com.android.systemui.kairos.util.MapPatch
import com.android.systemui.kairos.util.Maybe
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.map
import com.android.systemui.kairos.util.plus
import com.android.systemui.kairos.util.toMaybe

internal class IncrementalImpl<K, out V>(
    nameData: NameData,
    changes: EventsImpl<Map<K, V>>,
    val patches: EventsImpl<Map<K, Maybe<V>>>,
    store: StateStore<Map<K, V>>,
) : StateImpl<Map<K, V>>(nameData, changes, store)

internal fun <K, V> constIncremental(nameData: NameData, init: Map<K, V>): IncrementalImpl<K, V> =
    IncrementalImpl(nameData, neverImpl, neverImpl, StateSource(init, nameData))

internal inline fun <K, V> activatedIncremental(
    nameData: NameData,
    evalScope: EvalScope,
    crossinline getPatches: EvalScope.() -> EventsImpl<Map<K, Maybe<V>>>,
    init: Lazy<Map<K, V>>,
): IncrementalImpl<K, V> {
    val store = StateSource(init, nameData)
    val maybeChanges =
        mapImpl(getPatches, nameData + "maybeChanges") { patch, _ ->
                val (current, _) = store.getCurrentWithEpoch(evalScope = this)
                current.applyPatchCalm(patch).toMaybe()
            }
            .cached(nameData + "cached")
    val calm = filterPresentImpl(nameData + "calm") { maybeChanges }
    val changes = mapImpl({ calm }, nameData + "stateChanges") { (_, change), _ -> change }
    val patches = mapImpl({ calm }, nameData + "incPatches") { (patch, _), _ -> patch }
    evalScope.scheduleOutput(
        OneShot(nameData + "activateIncremental") {
            changes.activate(evalScope = this, downstream = Schedulable.S(store))?.let {
                (connection, needsEval) ->
                store.upstreamConnection = connection
                if (needsEval) {
                    schedule(store)
                }
            }
        }
    )
    return IncrementalImpl(nameData, changes, patches, store)
}

private fun <K, V> Map<K, V>.applyPatchCalm(
    patch: MapPatch<K, V>
): Pair<MapPatch<K, V>, Map<K, V>>? {
    val current = this
    val filteredPatch = mutableMapOf<K, Maybe<V>>()
    val new = current.toMutableMap()
    patch.forEach { key, change ->
        when (change) {
            is Maybe.Present -> {
                if (key !in current || current.getValue(key) != change.value) {
                    filteredPatch[key] = change
                    new[key] = change.value
                }
            }
            Maybe.Absent -> {
                if (key in current) {
                    filteredPatch[key] = change
                    new.remove(key)
                }
            }
        }
    }
    return if (filteredPatch.isNotEmpty()) filteredPatch to new else null
}

internal fun <K, V> EventsImpl<Map<K, Maybe<V>>>.calmUpdates(
    nameData: NameData,
    state: StateDerived<Map<K, V>>,
): Pair<EventsImpl<Map<K, Maybe<V>>>, EventsImpl<Map<K, V>>> {
    val maybeUpdate =
        mapImpl({ this@calmUpdates }, nameData + "maybeUpdate") { patch, _ ->
                val (current, _) = state.getCurrentWithEpoch(evalScope = this)
                current
                    .applyPatchCalm(patch)
                    ?.also { (_, newMap) -> state.setCacheFromPush(newMap, epoch) }
                    .toMaybe()
            }
            .cached(nameData + "cached")
    val calm = filterPresentImpl(nameData + "calmUpdates") { maybeUpdate }
    val patches = mapImpl({ calm }, nameData + "updatePatches") { (p, _), _ -> p }
    val changes = mapImpl({ calm }, nameData + "updateChanges") { (_, s), _ -> s }
    return patches to changes
}

internal fun <K, A, B> mapValuesImpl(
    incrementalImpl: InitScope.() -> IncrementalImpl<K, A>,
    nameData: NameData,
    transform: EvalScope.(Map.Entry<K, A>) -> B,
): IncrementalImpl<K, B> {
    val store = DerivedMap(nameData, incrementalImpl) { map -> map.mapValues { transform(it) } }
    val mappedPatches =
        mapImpl({ incrementalImpl().patches }, nameData + "mappedPatches") { patch, _ ->
                patch.mapValues { (k, mv) -> mv.map { v -> transform(StoreEntry(k, v)) } }
            }
            .cached(nameData + "cached")
    val (calmPatches, calmChanges) = mappedPatches.calmUpdates(nameData, store)
    return IncrementalImpl(nameData, calmChanges, calmPatches, store)
}
