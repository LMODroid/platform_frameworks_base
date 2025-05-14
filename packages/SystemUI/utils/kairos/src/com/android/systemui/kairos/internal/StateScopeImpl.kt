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

import com.android.systemui.kairos.DeferredValue
import com.android.systemui.kairos.Events
import com.android.systemui.kairos.EventsInit
import com.android.systemui.kairos.EventsLoop
import com.android.systemui.kairos.Incremental
import com.android.systemui.kairos.IncrementalInit
import com.android.systemui.kairos.KeyedEvents
import com.android.systemui.kairos.State
import com.android.systemui.kairos.StateInit
import com.android.systemui.kairos.StateScope
import com.android.systemui.kairos.Stateful
import com.android.systemui.kairos.changes
import com.android.systemui.kairos.emptyEvents
import com.android.systemui.kairos.groupByKey
import com.android.systemui.kairos.init
import com.android.systemui.kairos.map
import com.android.systemui.kairos.mapCheap
import com.android.systemui.kairos.mapCheapUnsafe
import com.android.systemui.kairos.skipNext
import com.android.systemui.kairos.switchEvents
import com.android.systemui.kairos.switchEventsPromptly
import com.android.systemui.kairos.util.Maybe
import com.android.systemui.kairos.util.NameData
import com.android.systemui.kairos.util.NameTag
import com.android.systemui.kairos.util.forceInit
import com.android.systemui.kairos.util.map
import com.android.systemui.kairos.util.mapName
import com.android.systemui.kairos.util.plus
import com.android.systemui.kairos.util.toNameData

internal class StateScopeImpl(
    val nameData: NameData,
    val createdEpoch: Long,
    val evalScope: EvalScope,
    val aliveLazy: Lazy<State<Boolean>>,
) : InternalStateScope, EvalScope by evalScope {

    init {
        nameData.forceInit()
    }

    override val alive: State<Boolean> by aliveLazy

    override fun <A> deferredStateScope(block: StateScope.() -> A): DeferredValue<A> =
        DeferredValue(deferAsync { block() })

    override fun <A> Events<A>.holdStateDeferred(
        initialValue: DeferredValue<A>,
        name: NameTag?,
    ): State<A> {
        val nameData = name.toNameData("Events.holdStateDeferred")
        // Ensure state is only collected until the end of this scope
        return truncateToScope(this@holdStateDeferred, nameData + "truncatedChanges")
            .holdStateInternalDeferred(nameData, this@StateScopeImpl, initialValue.unwrapped)
    }

    override fun <K, V> Events<Map<K, Maybe<V>>>.foldStateMapIncrementally(
        initialValues: DeferredValue<Map<K, V>>,
        name: NameTag?,
    ): Incremental<K, V> {
        val nameData = name.toNameData("Events.foldStateMapIncrementally")
        return IncrementalInit(
            constInit(
                nameData,
                activatedIncremental(
                    nameData,
                    evalScope,
                    { init.connect(this) },
                    initialValues.unwrapped,
                ),
            )
        )
    }

    override fun <K, A, B> Events<Map<K, Maybe<Stateful<A>>>>.applyLatestStatefulForKey(
        initialValues: DeferredValue<Map<K, Stateful<B>>>,
        numKeys: Int?,
        name: NameTag?,
    ): Pair<Events<Map<K, Maybe<A>>>, DeferredValue<Map<K, B>>> {
        val nameData = name.toNameData("Events.applyLatestStatefulForKey")
        val eventsByKey: KeyedEvents<K, Maybe<Stateful<A>>> =
            groupByKey(nameData + "eventsByKey", numKeys)
        val initOut: Lazy<Map<K, B>> = deferAsync {
            initialValues.unwrapped.value.mapValues { (k, stateful) ->
                val newEnd: Events<Maybe<Stateful<A>>> = eventsByKey[k]
                val newScope =
                    childStateScope(
                        newEnd,
                        nameData.mapName { "$it[key=$k, epoch=$epoch, init=true]" },
                    )
                newScope.stateful()
            }
        }
        val changesImpl: EventsImpl<Map<K, Maybe<A>>> =
            mapImpl(
                upstream = { this@applyLatestStatefulForKey.init.connect(evalScope = this) },
                nameData + "changes",
            ) { upstreamMap, _ ->
                reenterStateScope(this@StateScopeImpl).run {
                    upstreamMap.mapValues { (k: K, ma: Maybe<Stateful<A>>) ->
                        ma.map { stateful ->
                            val newName =
                                nameData.mapName { "$it[key=$k, epoch=$epoch, init=false]" }
                            val newEnd: Events<Maybe<Stateful<A>>> =
                                skipNext(newName + "newEnd", eventsByKey[k])
                            val newScope = childStateScope(newEnd, newName)
                            newScope.stateful()
                        }
                    }
                }
            }
        val changes: Events<Map<K, Maybe<A>>> =
            EventsInit(constInit(nameData, changesImpl.cached(nameData)))
        return changes to DeferredValue(initOut)
    }

    override fun <A> Events<Stateful<A>>.applyStatefuls(name: NameTag?): Events<A> {
        val nameData = name.toNameData("Events.applyStatefuls")
        return EventsInit(
            constInit(
                nameData,
                mapImpl(
                        upstream = { this@applyStatefuls.init.connect(evalScope = this) },
                        nameData,
                    ) { stateful, _ ->
                        reenterStateScope(outerScope = this@StateScopeImpl).stateful()
                    }
                    .cached(nameData),
            )
        )
    }

    fun childStateScope(childEndSignal: Events<Any>, nameData: NameData) =
        StateScopeImpl(
            nameData,
            epoch,
            evalScope,
            aliveLazy =
                lazy {
                    alive
                        .changes(nameData + "parentAliveChanges")
                        .mapCheap { now }
                        .holdStateInternalDeferred(
                            nameData + "deathSignalInner",
                            this,
                            deferAsync {
                                if (alive.sample()) {
                                    childEndSignal.nextOnlyInternal(
                                        nameData + "firstEndSignal",
                                        this,
                                    )
                                } else {
                                    now
                                }
                            },
                        )
                        .switchEventsPromptly(nameData + "deathSignal")
                        .mapCheap(nameData + "mapFalse") { false }
                        .holdStateInternal(nameData + "isAlive", this, true)
                },
        )

    override fun <A> truncateToScope(events: Events<A>, nameData: NameData): Events<A> =
        alive
            .mapCheapUnsafe(nameData + "mapCheapSwitchOff") { if (it) events else emptyEvents }
            .switchEvents(nameData)

    override fun toString(): String = "${super.toString()}[$nameData]"
}

private fun EvalScope.reenterStateScope(outerScope: StateScopeImpl) =
    StateScopeImpl(
        outerScope.nameData,
        outerScope.createdEpoch,
        evalScope = this,
        aliveLazy = outerScope.aliveLazy,
    )

private fun <A> Events<A>.nextOnlyInternal(nameData: NameData, evalScope: EvalScope): Events<A> =
    if (this === emptyEvents) {
        this
    } else {
        EventsLoop<A>().apply {
            loopback =
                mapCheap(nameData + "shutoffEvent") { emptyEvents }
                    .holdStateInternal(nameData + "state", evalScope, this@nextOnlyInternal)
                    .switchEvents(nameData)
        }
    }

private fun <A> Events<A>.holdStateInternal(
    nameData: NameData,
    evalScope: EvalScope,
    initialValue: A,
): State<A> = holdStateInternalDeferred(nameData, evalScope, lazyOf(initialValue))

private fun <A> Events<A>.holdStateInternalDeferred(
    nameData: NameData,
    evalScope: EvalScope,
    initialValue: Lazy<A>,
): State<A> {
    val changes = this@holdStateInternalDeferred
    val impl =
        activatedStateSource(
            nameData,
            evalScope,
            { changes.init.connect(evalScope = this) },
            initialValue,
        )
    return StateInit(constInit(nameData, impl))
}
