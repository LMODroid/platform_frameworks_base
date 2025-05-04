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

import com.android.systemui.kairos.CoalescingPolicy
import com.android.systemui.kairos.State
import com.android.systemui.kairos.internal.util.HeteroMap
import com.android.systemui.kairos.internal.util.LogIndent
import com.android.systemui.kairos.internal.util.logDuration
import com.android.systemui.kairos.util.Maybe
import com.android.systemui.kairos.util.Maybe.Present
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.ContinuationInterceptor
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

private val nextNetworkId = AtomicLong()

internal class Network(
    val coroutineScope: CoroutineScope,
    private val coalescingPolicy: CoalescingPolicy,
) : NetworkScope {

    override val networkId: Any = nextNetworkId.getAndIncrement()

    @Volatile
    override var epoch: Long = 0L
        private set

    override val network
        get() = this

    override val compactor = SchedulerImpl {
        if (it.markedForCompaction) false
        else {
            it.markedForCompaction = true
            true
        }
    }
    override val scheduler = SchedulerImpl {
        if (it.markedForEvaluation) false
        else {
            it.markedForEvaluation = true
            true
        }
    }
    override val transactionStore = TransactionStore()

    private val deferScopeImpl = DeferScopeImpl()
    private val stateWrites = ArrayDeque<StateSource<*>>()
    private val fastOutputs = ArrayDeque<Output<*>>()
    private val outputsByDispatcher = HashMap<ContinuationInterceptor, ArrayDeque<() -> Unit>>()
    private val muxMovers = ArrayDeque<MuxDeferredNode<*, *, *>>()
    private val deactivations = ArrayDeque<PushNode<*>>()
    private val outputDeactivations = ArrayDeque<Output<*>>()
    private val inputScheduleChan = Channel<ScheduledAction<*>>()

    override fun scheduleOutput(output: Output<*>) {
        fastOutputs.add(output)
    }

    override fun scheduleDispatchedOutput(
        interceptor: ContinuationInterceptor?,
        block: () -> Unit,
    ) {
        outputsByDispatcher
            .computeIfAbsent(interceptor ?: Dispatchers.Unconfined) { ArrayDeque() }
            .add(block)
    }

    override fun scheduleMuxMover(muxMover: MuxDeferredNode<*, *, *>) {
        muxMovers.add(muxMover)
    }

    override fun schedule(state: StateSource<*>) {
        stateWrites.add(state)
    }

    override fun scheduleDeactivation(node: PushNode<*>) {
        deactivations.add(node)
    }

    override fun scheduleDeactivation(output: Output<*>) {
        outputDeactivations.add(output)
    }

    /** Listens for external events and starts Kairos transactions. Runs forever. */
    suspend fun runInputScheduler() {
        val actions = mutableListOf<ScheduledAction<*>>()
        for (first in inputScheduleChan) {
            // Drain and conflate all transaction requests into a single transaction
            actions.add(first)
            when (coalescingPolicy) {
                CoalescingPolicy.None -> {}
                CoalescingPolicy.Normal -> {
                    while (true) {
                        val func = inputScheduleChan.tryReceive().getOrNull() ?: break
                        actions.add(func)
                    }
                }

                CoalescingPolicy.Eager -> {
                    while (true) {
                        yield()
                        val func = inputScheduleChan.tryReceive().getOrNull() ?: break
                        actions.add(func)
                    }
                }
            }
            val e = epoch
            logDuration(indent = 0, { "Kairos Transaction epoch=$e" }, trace = true) {
                val evalScope =
                    EvalScopeImpl(networkScope = this@Network, deferScope = deferScopeImpl)
                try {
                    logDuration(getPrefix = { "process inputs" }, trace = true) {
                        // Run all actions
                        runThenDrainDeferrals {
                            for (action in actions) {
                                action.started(evalScope)
                            }
                        }
                    }
                    // Step through the network
                    coroutineScope { doTransaction(evalScope, coroutineScope = this) }
                } catch (e: Exception) {
                    // Signal failure
                    while (actions.isNotEmpty()) {
                        actions.removeLast().fail(e)
                    }
                    // re-throw, cancelling this coroutine
                    throw e
                } finally {
                    logDuration(getPrefix = { "signal completions" }, trace = true) {
                        // Signal completion
                        while (actions.isNotEmpty()) {
                            actions.removeLast().completed()
                        }
                    }
                }
            }
        }
    }

    /** Evaluates [block] inside of a new transaction when the network is ready. */
    fun <R> transaction(reason: String, block: EvalScope.() -> R): Deferred<R> =
        CompletableDeferred<R>(parent = coroutineScope.coroutineContext.job).also { onResult ->
            if (!coroutineScope.isActive) {
                onResult.cancel()
                return@also
            }
            val job =
                coroutineScope.launch {
                    inputScheduleChan.send(
                        ScheduledAction(reason, onStartTransaction = block, onResult = onResult)
                    )
                }
            onResult.invokeOnCompletion { job.cancel() }
        }

    inline fun <R> runThenDrainDeferrals(block: () -> R): R =
        block().also { deferScopeImpl.drainDeferrals() }

    /** Performs a transactional update of the Kairos network. */
    private fun LogIndent.doTransaction(evalScope: EvalScope, coroutineScope: CoroutineScope) {
        // Traverse network, then run outputs
        logDuration({ "traverse network" }, trace = true) {
            do {
                val numNodes: Int =
                    logDuration({ "drain scheduler" }, trace = true) {
                        scheduler.drainEval(currentLogIndent, this@Network, evalScope)
                    }
                logLn { "drained $numNodes nodes" }
            } while (
                logDuration({ "drain outputs" }, trace = true) {
                    runThenDrainDeferrals { evalFastOutputs(evalScope) }
                }
            )
        }
        evalLaunchedOutputs(coroutineScope)
        // Update states
        logDuration({ "write states" }, trace = true) {
            runThenDrainDeferrals { evalStateWriters(currentLogIndent, evalScope) }
        }
        // Invalidate caches
        // Note: this needs to occur before deferred switches
        logDuration({ "clear store" }) { transactionStore.clear() }
        epoch++
        // Perform deferred switches
        logDuration({ "deferred moves" }, trace = true) {
            runThenDrainDeferrals { evalMuxMovers(currentLogIndent, evalScope) }
        }
        // Compact depths
        logDuration({ "compact depths" }, trace = true) {
            scheduler.drainCompact(currentLogIndent)
            compactor.drainCompact(currentLogIndent)
        }

        // Deactivate nodes with no downstream
        logDuration({ "deactivations" }, trace = true) { evalDeactivations() }
    }

    private fun evalFastOutputs(evalScope: EvalScope): Boolean {
        if (fastOutputs.isEmpty()) {
            return false
        }
        while (true) {
            fastOutputs.removeFirstOrNull()?.visit(evalScope) ?: break
        }
        return true
    }

    private fun evalLaunchedOutputs(coroutineScope: CoroutineScope) {
        if (outputsByDispatcher.isEmpty()) return
        for ((key, outputs) in outputsByDispatcher) {
            if (outputs.isNotEmpty()) {
                coroutineScope.launch(key) {
                    while (outputs.isNotEmpty()) {
                        val output = outputs.removeFirst()
                        launch { output() }
                    }
                }
            }
        }
        outputsByDispatcher.clear()
    }

    private fun evalMuxMovers(logIndent: Int, evalScope: EvalScope) {
        while (muxMovers.isNotEmpty()) {
            val toMove = muxMovers.removeFirst()
            toMove.performMove(logIndent, evalScope)
        }
    }

    /** Updates all [State]es that have changed within this transaction. */
    private fun evalStateWriters(logIndent: Int, evalScope: EvalScope) {
        while (stateWrites.isNotEmpty()) {
            val latch = stateWrites.removeFirst()
            latch.updateState(logIndent, evalScope)
        }
    }

    private fun evalDeactivations() {
        while (deactivations.isNotEmpty()) {
            // traverse in reverse order
            //   - deactivations are added in depth-order during the node traversal phase
            //   - perform deactivations in reverse order, in case later ones propagate to
            //     earlier ones
            val toDeactivate = deactivations.removeLast()
            toDeactivate.deactivateIfNeeded()
        }

        while (outputDeactivations.isNotEmpty()) {
            val toDeactivate = outputDeactivations.removeFirst()
            toDeactivate.upstream?.removeDownstreamAndDeactivateIfNeeded(
                downstream = toDeactivate.schedulable
            )
        }
        check(deactivations.isEmpty()) { "unexpected lingering deactivations" }
        check(outputDeactivations.isEmpty()) { "unexpected lingering output deactivations" }
    }
}

internal class ScheduledAction<T>(
    val reason: String,
    private val onResult: CompletableDeferred<T>? = null,
    private val onStartTransaction: EvalScope.() -> T,
) {
    private var result: Maybe<T> = Maybe.absent

    fun started(evalScope: EvalScope) {
        result = Maybe.present(onStartTransaction(evalScope))
    }

    fun fail(ex: Exception) {
        result = Maybe.absent
        onResult?.completeExceptionally(ex)
    }

    fun completed() {
        if (onResult != null) {
            when (val result = result) {
                is Present -> onResult.complete(result.value)
                else -> {}
            }
        }
        result = Maybe.absent
    }
}

internal class TransactionStore private constructor(private val storage: HeteroMap) {
    constructor(capacity: Int) : this(HeteroMap(capacity))

    constructor() : this(HeteroMap())

    operator fun <A> get(key: HeteroMap.Key<A>): A =
        storage.getOrError(key) { "no value for $key in this transaction" }

    operator fun <A> set(key: HeteroMap.Key<A>, value: A) {
        storage[key] = value
    }

    fun clear() = storage.clear()
}

internal class TransactionCache<A> {
    private val key = object : HeteroMap.Key<A> {}
    @Volatile
    var epoch: Long = Long.MIN_VALUE
        private set

    fun getOrPut(evalScope: EvalScope, block: () -> A): A =
        if (epoch < evalScope.epoch) {
            epoch = evalScope.epoch
            block().also { evalScope.transactionStore[key] = it }
        } else {
            evalScope.transactionStore[key]
        }

    fun put(evalScope: EvalScope, value: A) {
        epoch = evalScope.epoch
        evalScope.transactionStore[key] = value
    }

    fun getCurrentValue(evalScope: EvalScope): A = evalScope.transactionStore[key]
}
