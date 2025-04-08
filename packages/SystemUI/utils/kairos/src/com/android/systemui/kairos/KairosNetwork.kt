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

package com.android.systemui.kairos

import com.android.systemui.kairos.internal.BuildScopeImpl
import com.android.systemui.kairos.internal.EvalScope
import com.android.systemui.kairos.internal.Network
import com.android.systemui.kairos.internal.NoScope
import com.android.systemui.kairos.internal.StateScopeImpl
import com.android.systemui.kairos.internal.util.childScope
import com.android.systemui.kairos.internal.util.invokeOnCancel
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.job
import kotlinx.coroutines.launch

/** Marks APIs that are still **experimental** and shouldn't be used in general production code. */
@RequiresOptIn(
    message = "This API is experimental and should not be used in general production code."
)
@Retention(AnnotationRetention.BINARY)
annotation class ExperimentalKairosApi

/**
 * External interface to a Kairos network of reactive components. Can be used to make transactional
 * queries and modifications to the network.
 */
@ExperimentalKairosApi
interface KairosNetwork {
    /**
     * Runs [block] inside of a transaction, suspending until the transaction is complete.
     *
     * The [BuildScope] receiver exposes methods that can be used to query or modify the network. If
     * the network is cancelled while the caller of [transact] is suspended, then the call will be
     * cancelled.
     */
    suspend fun <R> transact(block: TransactionScope.() -> R): R

    /**
     * Activates [spec] in a transaction, suspending indefinitely. While suspended, all observers
     * and long-running effects are kept alive. When cancelled, observers are unregistered and
     * effects are cancelled.
     */
    suspend fun activateSpec(spec: BuildSpec<*>)

    /** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
    fun <In, Out> coalescingMutableEvents(
        coalesce: (old: Out, new: In) -> Out,
        getInitialValue: KairosScope.() -> Out,
    ): CoalescingMutableEvents<In, Out>

    /** Returns a [MutableState] that can emit values into this [KairosNetwork]. */
    fun <T> mutableEvents(): MutableEvents<T>

    /** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
    fun <T> conflatedMutableEvents(): CoalescingMutableEvents<T, T>

    /** Returns a [MutableState]. with initial state [initialValue]. */
    fun <T> mutableStateDeferred(initialValue: DeferredValue<T>): MutableState<T>
}

/** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <In, Out> KairosNetwork.coalescingMutableEvents(
    coalesce: (old: Out, new: In) -> Out,
    initialValue: Out,
): CoalescingMutableEvents<In, Out> =
    coalescingMutableEvents(coalesce, getInitialValue = { initialValue })

/** Returns a [MutableState] with initial state [initialValue]. */
@ExperimentalKairosApi
fun <T> KairosNetwork.mutableState(initialValue: T): MutableState<T> =
    mutableStateDeferred(deferredOf(initialValue))

/** Returns a [MutableState] with initial state [initialValue]. */
@ExperimentalKairosApi
fun <T> MutableState(network: KairosNetwork, initialValue: T): MutableState<T> =
    network.mutableState(initialValue)

/** Returns a [MutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <T> MutableEvents(network: KairosNetwork): MutableEvents<T> = network.mutableEvents()

/** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <In, Out> CoalescingMutableEvents(
    network: KairosNetwork,
    coalesce: (old: Out, new: In) -> Out,
    initialValue: Out,
): CoalescingMutableEvents<In, Out> = network.coalescingMutableEvents(coalesce) { initialValue }

/** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <In, Out> CoalescingMutableEvents(
    network: KairosNetwork,
    coalesce: (old: Out, new: In) -> Out,
    getInitialValue: KairosScope.() -> Out,
): CoalescingMutableEvents<In, Out> = network.coalescingMutableEvents(coalesce, getInitialValue)

/** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <T> ConflatedMutableEvents(network: KairosNetwork): CoalescingMutableEvents<T, T> =
    network.conflatedMutableEvents()

/**
 * Activates [spec] in a transaction and invokes [block] with the result, suspending indefinitely.
 * While suspended, all observers and long-running effects are kept alive. When cancelled, observers
 * are unregistered and effects are cancelled.
 */
@ExperimentalKairosApi
suspend fun <R> KairosNetwork.activateSpec(
    spec: BuildSpec<R>,
    block: suspend KairosCoroutineScope.(R) -> Unit,
) {
    activateSpec {
        val result = spec.applySpec()
        launchEffect { block(result) }
    }
}

internal class LocalNetwork(
    private val network: Network,
    private val scope: CoroutineScope,
    private val aliveLazy: Lazy<State<Boolean>>,
) : KairosNetwork {

    override suspend fun <R> transact(block: TransactionScope.() -> R): R =
        network.transaction("KairosNetwork.transact") { block() }.awaitOrCancel()

    override suspend fun activateSpec(spec: BuildSpec<*>): Unit = coroutineScope {
        lateinit var completionHandle: DisposableHandle
        val childEndSignal = conflatedMutableEvents<Unit>().apply { invokeOnCancel { emit(Unit) } }
        val job =
            launch(start = CoroutineStart.LAZY) {
                network
                    .transaction("KairosNetwork.activateSpec") {
                        reenterBuildScope(this@coroutineScope).childBuildScope(childEndSignal).run {
                            launchScope { spec.applySpec() }
                        }
                    }
                    .awaitOrCancel()
                    .joinOrCancel()
                completionHandle.dispose()
            }
        completionHandle = scope.coroutineContext.job.invokeOnCompletion { job.cancel() }
        job.start()
    }

    private fun EvalScope.reenterBuildScope(coroutineScope: CoroutineScope) =
        BuildScopeImpl(
            stateScope = StateScopeImpl(evalScope = this, aliveLazy = aliveLazy),
            coroutineScope = coroutineScope,
        )

    private suspend fun <T> Deferred<T>.awaitOrCancel(): T =
        try {
            await()
        } catch (ex: CancellationException) {
            cancel(ex)
            throw ex
        }

    private suspend fun Job.joinOrCancel(): Unit =
        try {
            join()
        } catch (ex: CancellationException) {
            cancel(ex)
            throw ex
        }

    override fun <In, Out> coalescingMutableEvents(
        coalesce: (old: Out, new: In) -> Out,
        getInitialValue: KairosScope.() -> Out,
    ): CoalescingMutableEvents<In, Out> =
        CoalescingMutableEvents(
            null,
            coalesce = { old, new -> coalesce(old.value, new) },
            network,
            { NoScope.getInitialValue() },
        )

    override fun <T> conflatedMutableEvents(): CoalescingMutableEvents<T, T> =
        CoalescingMutableEvents(
            null,
            coalesce = { _, new -> new },
            network,
            { error("WTF: init value accessed for conflatedMutableEvents") },
        )

    override fun <T> mutableEvents(): MutableEvents<T> = MutableEvents(network)

    override fun <T> mutableStateDeferred(initialValue: DeferredValue<T>): MutableState<T> =
        MutableState(network, initialValue.unwrapped)
}

/**
 * Combination of an [KairosNetwork] and a [Job] that, when cancelled, will cancel the entire Kairos
 * network.
 */
@ExperimentalKairosApi
class RootKairosNetwork
internal constructor(private val network: Network, private val scope: CoroutineScope, job: Job) :
    Job by job, KairosNetwork by LocalNetwork(network, scope, lazyOf(stateOf(true)))

/** Constructs a new [RootKairosNetwork] in the given [CoroutineScope] and [CoalescingPolicy]. */
@ExperimentalKairosApi
fun CoroutineScope.launchKairosNetwork(
    context: CoroutineContext = EmptyCoroutineContext,
    coalescingPolicy: CoalescingPolicy = CoalescingPolicy.Normal,
): RootKairosNetwork {
    val scope = childScope(context)
    val network = Network(scope, coalescingPolicy)
    scope.launch(CoroutineName("launchKairosNetwork scheduler")) { network.runInputScheduler() }
    return RootKairosNetwork(network, scope, scope.coroutineContext.job)
}

/** Constructs a new [RootKairosNetwork] in the given [CoroutineScope] and [CoalescingPolicy]. */
fun KairosNetwork(
    scope: CoroutineScope,
    coalescingPolicy: CoalescingPolicy = CoalescingPolicy.Normal,
): RootKairosNetwork = scope.launchKairosNetwork(coalescingPolicy = coalescingPolicy)

/** Configures how multiple input events are processed by the network. */
enum class CoalescingPolicy {
    /**
     * Each input event is processed in its own transaction. This policy has the least overhead but
     * can cause backpressure if the network becomes flooded with inputs.
     */
    None,
    /**
     * Input events are processed as they appear. Compared to [Eager], this policy will not
     * internally [yield][kotlinx.coroutines.yield] to allow more inputs to be processed before
     * starting a transaction. This means that if there is a race between an input and a transaction
     * occurring, it is beholden to the
     * [CoroutineDispatcher][kotlinx.coroutines.CoroutineDispatcher] to determine the ordering.
     *
     * Note that any input events which miss being included in a transaction will be immediately
     * scheduled for a subsequent transaction.
     */
    Normal,
    /**
     * Input events are processed eagerly. Compared to [Normal], this policy will internally
     * [yield][kotlinx.coroutines.yield] to allow for as many input events to be processed as
     * possible. This can be useful for noisy networks where many inputs can be handled
     * simultaneously, potentially improving throughput.
     */
    Eager,
}

@ExperimentalKairosApi
interface HasNetwork : KairosScope {
    /**
     * A [KairosNetwork] handle that is bound to the lifetime of a [BuildScope].
     *
     * It supports all of the standard functionality by which external code can interact with this
     * Kairos network, but all [activated][KairosNetwork.activateSpec] [BuildSpec]s are bound as
     * children to the [BuildScope], such that when the [BuildScope] is destroyed, all children are
     * also destroyed.
     */
    val kairosNetwork: KairosNetwork
}

/** Returns a [MutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <T> HasNetwork.MutableEvents(): MutableEvents<T> = MutableEvents(kairosNetwork)

/** Returns a [MutableState] with initial state [initialValue]. */
@ExperimentalKairosApi
fun <T> HasNetwork.MutableState(initialValue: T): MutableState<T> =
    MutableState(kairosNetwork, initialValue)

/** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <In, Out> HasNetwork.CoalescingMutableEvents(
    coalesce: (old: Out, new: In) -> Out,
    initialValue: Out,
): CoalescingMutableEvents<In, Out> = CoalescingMutableEvents(kairosNetwork, coalesce, initialValue)

/** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <In, Out> HasNetwork.CoalescingMutableEvents(
    coalesce: (old: Out, new: In) -> Out,
    getInitialValue: KairosScope.() -> Out,
): CoalescingMutableEvents<In, Out> =
    CoalescingMutableEvents(kairosNetwork, coalesce, getInitialValue)

/** Returns a [CoalescingMutableEvents] that can emit values into this [KairosNetwork]. */
@ExperimentalKairosApi
fun <T> HasNetwork.ConflatedMutableEvents(): CoalescingMutableEvents<T, T> =
    ConflatedMutableEvents(kairosNetwork)
