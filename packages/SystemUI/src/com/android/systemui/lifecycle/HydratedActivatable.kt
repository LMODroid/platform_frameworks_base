/*
 * Copyright (C) 2025 The Android Open Source Project
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

package com.android.systemui.lifecycle

import androidx.compose.runtime.State
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * An [Activatable] which manages an internal [Hydrator] which is activated accordingly. Adds
 * convenience methods to easily transform upstream [Flow]s into downstream snapshot-backed [State]s
 * based on the [Hydrator].
 *
 * The activation of this is also guaranteed to be exclusive since the [Hydrator] is an
 * [ExclusiveActivatable] itself.
 *
 * @see [ExclusiveActivatable]
 */
abstract class HydratedActivatable : Activatable {

    private val hydrator = Hydrator("${this::class.simpleName}.hydrator")

    final override suspend fun activate(): Nothing {
        coroutineScope {
            launch { hydrator.activate() }
            onActivated()
            awaitCancellation()
        }
    }

    /**
     * Notifies that the [Activatable] has been activated.
     *
     * Serves as an entrypoint to kick off coroutine work that the object requires in order to keep
     * its state fresh and/or perform side-effects.
     *
     * The method suspends and doesn't return until all work required by the object is finished. In
     * most cases, it's expected for the work to remain ongoing forever so this method will forever
     * suspend its caller until the coroutine that called it is canceled.
     *
     * Implementations could follow this pattern:
     * ```kotlin
     * override suspend fun onActivated() {
     *     coroutineScope {
     *         launch { ... }
     *         launch { ... }
     *         launch { ... }
     *     }
     * }
     * ```
     *
     * @see activate
     */
    protected open suspend fun onActivated() {}

    /** @see [Hydrator.hydratedStateOf] */
    protected fun <T> StateFlow<T>.hydratedStateOf(traceName: String): State<T> =
        hydrator.hydratedStateOf(traceName, this)

    /** @see [Hydrator.hydratedStateOf] */
    protected fun <T> Flow<T>.hydratedStateOf(traceName: String, initialValue: T): State<T> =
        hydrator.hydratedStateOf(traceName, initialValue, this)
}
