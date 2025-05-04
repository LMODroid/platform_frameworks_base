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

import com.android.systemui.kairos.BuildScope
import com.android.systemui.kairos.BuildSpec
import com.android.systemui.kairos.CoalescingEventProducerScope
import com.android.systemui.kairos.CoalescingMutableEvents
import com.android.systemui.kairos.DeferredValue
import com.android.systemui.kairos.EffectScope
import com.android.systemui.kairos.EventProducerScope
import com.android.systemui.kairos.Events
import com.android.systemui.kairos.EventsInit
import com.android.systemui.kairos.KairosCoroutineScope
import com.android.systemui.kairos.KairosNetwork
import com.android.systemui.kairos.KairosScope
import com.android.systemui.kairos.KeyedEvents
import com.android.systemui.kairos.LocalNetwork
import com.android.systemui.kairos.MutableEvents
import com.android.systemui.kairos.TransactionEffectScope
import com.android.systemui.kairos.TransactionScope
import com.android.systemui.kairos.groupByKey
import com.android.systemui.kairos.init
import com.android.systemui.kairos.internal.util.childScope
import com.android.systemui.kairos.internal.util.invokeOnCancel
import com.android.systemui.kairos.internal.util.launchImmediate
import com.android.systemui.kairos.launchEffect
import com.android.systemui.kairos.util.Maybe
import com.android.systemui.kairos.util.Maybe.Absent
import com.android.systemui.kairos.util.Maybe.Present
import com.android.systemui.kairos.util.map
import java.util.concurrent.atomic.AtomicReference
import kotlin.coroutines.ContinuationInterceptor
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CompletableJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.DisposableHandle
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.job

internal class BuildScopeImpl(val stateScope: StateScopeImpl, val coroutineScope: CoroutineScope) :
    InternalBuildScope, InternalStateScope by stateScope {

    private val job: Job
        get() = coroutineScope.coroutineContext.job

    override val kairosNetwork: LocalNetwork by lazy {
        LocalNetwork(network, coroutineScope, stateScope.aliveLazy)
    }

    override fun <T> events(builder: suspend EventProducerScope<T>.() -> Unit): Events<T> =
        buildEvents(
            constructEvents = { inputNode ->
                val events = MutableEvents(network, inputNode)
                events to EventProducerScope<T> { value -> events.emit(value) }
            },
            builder = builder,
        )

    override fun <In, Out> coalescingEvents(
        getInitialValue: KairosScope.() -> Out,
        coalesce: (old: Out, new: In) -> Out,
        builder: suspend CoalescingEventProducerScope<In>.() -> Unit,
    ): Events<Out> =
        buildEvents(
            constructEvents = { inputNode ->
                val events =
                    CoalescingMutableEvents(
                        name = null,
                        coalesce = { old, new: In -> coalesce(old.value, new) },
                        network = network,
                        getInitialValue = { NoScope.getInitialValue() },
                        impl = inputNode,
                    )
                events to CoalescingEventProducerScope<In> { value -> events.emit(value) }
            },
            builder = builder,
        )

    override fun <A> asyncScope(
        coroutineContext: CoroutineContext,
        block: BuildSpec<A>,
    ): Pair<DeferredValue<A>, Job> {
        val childScope = mutableChildBuildScope(coroutineContext)
        return DeferredValue(deferAsync { block(childScope) }) to childScope.job
    }

    override fun <R> deferredBuildScope(block: BuildScope.() -> R): DeferredValue<R> =
        DeferredValue(deferAsync { block() })

    override fun deferredBuildScopeAction(block: BuildScope.() -> Unit) {
        deferAction { block() }
    }

    override fun <A> Events<A>.observe(
        coroutineContext: CoroutineContext,
        block: EffectScope.(A) -> Unit,
    ): DisposableHandle {
        val interceptor = coroutineContext[ContinuationInterceptor]
        return observeInternal(coroutineContext) { effectScope, output ->
            scheduleDispatchedOutput(interceptor = interceptor) { effectScope.block(output) }
        }
    }

    override fun <A> Events<A>.observeSync(
        block: TransactionEffectScope.(A) -> Unit
    ): DisposableHandle =
        observeInternal(EmptyCoroutineContext) { effectScope, output ->
            val scope =
                object :
                    TransactionEffectScope,
                    TransactionScope by this@observeInternal,
                    EffectScope by effectScope {}
            scope.block(output)
        }

    override fun <A, B> Events<A>.mapBuild(transform: BuildScope.(A) -> B): Events<B> {
        val childScope = coroutineScope.childScope()
        return EventsInit(
            constInit(
                "mapBuild",
                mapImpl({ init.connect(evalScope = this) }) { spec, _ ->
                        reenterBuildScope(outerScope = this@BuildScopeImpl, childScope)
                            .transform(spec)
                    }
                    .cached(),
            )
        )
    }

    override fun <K, A, B> Events<Map<K, Maybe<BuildSpec<A>>>>.applyLatestSpecForKey(
        initialSpecs: DeferredValue<Map<K, BuildSpec<B>>>,
        numKeys: Int?,
    ): Pair<Events<Map<K, Maybe<A>>>, DeferredValue<Map<K, B>>> {
        val eventsByKey: KeyedEvents<K, Maybe<BuildSpec<A>>> = groupByKey(numKeys)
        val initOut: Lazy<Map<K, B>> = deferAsync {
            initialSpecs.unwrapped.value.mapValues { (k, spec) ->
                val newEnd = eventsByKey[k]
                val newScope = childBuildScope(newEnd)
                newScope.spec()
            }
        }
        val childScope = coroutineScope.childScope()
        val changesNode: EventsImpl<Map<K, Maybe<A>>> =
            mapImpl(upstream = { this@applyLatestSpecForKey.init.connect(evalScope = this) }) {
                upstreamMap,
                _ ->
                reenterBuildScope(this@BuildScopeImpl, childScope).run {
                    upstreamMap.mapValues { (k: K, ma: Maybe<BuildSpec<A>>) ->
                        ma.map { spec ->
                            val newEnd = eventsByKey[k].skipNext()
                            val newScope = childBuildScope(newEnd)
                            newScope.spec()
                        }
                    }
                }
            }
        val changes: Events<Map<K, Maybe<A>>> =
            EventsInit(constInit("applyLatestForKey", changesNode.cached()))
        // Ensure effects are observed; otherwise init will stay alive longer than expected
        changes.observeSync()
        return changes to DeferredValue(initOut)
    }

    private fun <A> Events<A>.observeInternal(
        context: CoroutineContext,
        block: EvalScope.(EffectScope, A) -> Unit,
    ): DisposableHandle {
        val subRef = AtomicReference<Maybe<Output<A>>?>(null)
        val childScope: CoroutineScope = coroutineScope.childScope(context)
        var cancelHandle: DisposableHandle? = null
        val handle = DisposableHandle {
            cancelHandle?.dispose()
            subRef.getAndSet(Absent)?.let { output ->
                if (output is Present) {
                    @Suppress("DeferredResultUnused")
                    network.transaction("observeEffect cancelled") {
                        scheduleDeactivation(output.value)
                    }
                }
            }
        }
        // When our scope is cancelled, deactivate this observer.
        cancelHandle = childScope.coroutineContext.job.invokeOnCompletion { handle.dispose() }
        val effectScope: EffectScope = effectScope(childScope)
        val outputNode =
            Output<A>(
                onDeath = { subRef.set(Absent) },
                onEmit = onEmit@{ output ->
                        if (subRef.get() !is Present) return@onEmit
                        // Not cancelled, safe to emit]
                        block(effectScope, output)
                    },
            )
        // Defer, in case any EventsLoops / StateLoops still need to be set
        deferAction {
            // Check for immediate cancellation
            if (subRef.get() != null) return@deferAction
            truncateToScope(this@observeInternal)
                .init
                .connect(evalScope = stateScope.evalScope)
                .activate(evalScope = stateScope.evalScope, outputNode.schedulable)
                ?.let { (conn, needsEval) ->
                    outputNode.upstream = conn
                    if (!subRef.compareAndSet(null, Maybe.present(outputNode))) {
                        // Job's already been cancelled, schedule deactivation
                        scheduleDeactivation(outputNode)
                    } else if (needsEval) {
                        outputNode.schedule(0, evalScope = stateScope.evalScope)
                    }
                } ?: run { childScope.cancel() }
        }
        return handle
    }

    private fun effectScope(childScope: CoroutineScope) =
        object : EffectScope {
            override fun <R> async(
                context: CoroutineContext,
                start: CoroutineStart,
                block: suspend KairosCoroutineScope.() -> R,
            ): Deferred<R> =
                childScope.async(context, start) newScope@{
                    val childEndSignal: Events<Unit> =
                        this@BuildScopeImpl.newStopEmitter("EffectScope.async").apply {
                            this@newScope.invokeOnCancel { emit(Unit) }
                        }
                    val childStateScope: StateScopeImpl =
                        this@BuildScopeImpl.stateScope.childStateScope(childEndSignal)
                    val localNetwork =
                        LocalNetwork(
                            network = this@BuildScopeImpl.network,
                            scope = this@newScope,
                            aliveLazy = childStateScope.aliveLazy,
                        )
                    val scope =
                        object : KairosCoroutineScope, CoroutineScope by this@newScope {
                            override val kairosNetwork: KairosNetwork = localNetwork
                        }
                    scope.block()
                }

            override val kairosNetwork: KairosNetwork =
                LocalNetwork(
                    network = this@BuildScopeImpl.network,
                    scope = childScope,
                    aliveLazy = this@BuildScopeImpl.stateScope.aliveLazy,
                )
        }

    private fun <A, T : Events<A>, S> buildEvents(
        name: String? = null,
        constructEvents: (InputNode<A>) -> Pair<T, S>,
        builder: suspend S.() -> Unit,
    ): Events<A> {
        var job: Job? = null
        val stopEmitter = newStopEmitter("buildEvents[$name]")
        // Create a child scope that will be kept alive beyond the end of this transaction.
        val childScope = coroutineScope.childScope()
        lateinit var emitter: Pair<T, S>
        val inputNode =
            InputNode<A>(
                activate = {
                    // It's possible that activation occurs after all effects have been run, due
                    // to a MuxDeferred switch-in. For this reason, we need to activate in a new
                    // transaction.
                    check(job == null) { "[$name] already activated" }
                    job =
                        childScope.launchImmediate {
                            network
                                .transaction("buildEvents") {
                                    reenterBuildScope(this@BuildScopeImpl, childScope)
                                        .launchEffect {
                                            builder(emitter.second)
                                            stopEmitter.emit(Unit)
                                        }
                                }
                                .await()
                                .join()
                        }
                },
                deactivate = {
                    checkNotNull(job) { "[$name] already deactivated" }.cancel()
                    job = null
                },
            )
        emitter = constructEvents(inputNode)
        return truncateToScope(emitter.first.takeUntil(stopEmitter))
    }

    private fun newStopEmitter(name: String): CoalescingMutableEvents<Unit, Unit> =
        CoalescingMutableEvents(
            name = name,
            coalesce = { _, _: Unit -> },
            network = network,
            getInitialValue = {},
        )

    fun childBuildScope(newEnd: Events<Any>): BuildScopeImpl {
        val newCoroutineScope: CoroutineScope = coroutineScope.childScope()
        return BuildScopeImpl(
                stateScope = stateScope.childStateScope(newEnd),
                coroutineScope = newCoroutineScope,
            )
            .apply {
                // Ensure that once this transaction is done, the new child scope enters the
                // completing state (kept alive so long as there are child jobs).
                scheduleOutput(
                    OneShot {
                        // TODO: don't like this cast
                        (newCoroutineScope.coroutineContext.job as CompletableJob).complete()
                    }
                )
                alive.observeSync { if (!it) newCoroutineScope.cancel() }
            }
    }

    private fun mutableChildBuildScope(coroutineContext: CoroutineContext): BuildScopeImpl {
        val childScope = coroutineScope.childScope(coroutineContext)
        val stopEmitter =
            newStopEmitter("mutableChildBuildScope").apply {
                childScope.invokeOnCancel { emit(Unit) }
            }
        return BuildScopeImpl(
            stateScope = stateScope.childStateScope(stopEmitter),
            coroutineScope = childScope,
        )
    }
}

private fun EvalScope.reenterBuildScope(
    outerScope: BuildScopeImpl,
    coroutineScope: CoroutineScope,
) =
    BuildScopeImpl(
        stateScope = StateScopeImpl(evalScope = this, aliveLazy = outerScope.stateScope.aliveLazy),
        coroutineScope,
    )
