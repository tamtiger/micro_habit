package vn.nhip2phut.app.time

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import vn.nhip2phut.domain.time.ClockUpdateReason
import vn.nhip2phut.domain.time.DurableClockState
import vn.nhip2phut.domain.time.RawClockSnapshot
import vn.nhip2phut.platform.notification.ClockSignalCompletion
import vn.nhip2phut.platform.notification.ClockSignalHandler
import vn.nhip2phut.platform.time.ClockGenerationSource
import vn.nhip2phut.platform.time.ClockGenerationUnavailableException
import vn.nhip2phut.platform.time.RawClockSource
import java.util.concurrent.atomic.AtomicBoolean

fun interface ClockStateUpdateGateway {
    suspend fun update(reason: ClockUpdateReason, raw: RawClockSnapshot): DurableClockState
}

class LoadedClockGenerationSource : ClockGenerationSource {
    private val monitor = Any()
    private var generation: Long? = null
    private var nextTicket = 0L
    private var nextTicketToResolve = 0L
    private val activeTickets = mutableSetOf<Long>()
    private val completedTickets = mutableMapOf<Long, Long?>()

    override fun currentGeneration(): Long = synchronized(monitor) {
        if (activeTickets.isNotEmpty()) throw ClockGenerationUnavailableException()
        generation ?: throw ClockGenerationUnavailableException()
    }

    internal fun publish(value: Long) {
        require(value >= 0) { "Clock generation must be nonnegative." }
        synchronized(monitor) {
            check(activeTickets.isEmpty() && completedTickets.isEmpty()) {
                "Cannot publish during a clock mutation."
            }
            generation = value
        }
    }

    internal fun beginMutation(): Long = synchronized(monitor) {
        val followingTicket = try {
            Math.incrementExact(nextTicket)
        } catch (_: ArithmeticException) {
            throw ClockGenerationUnavailableException()
        }
        val ticket = nextTicket
        check(activeTickets.add(ticket)) { "Clock mutation ticket collision." }
        nextTicket = followingTicket
        ticket
    }

    internal fun finishMutation(ticket: Long, committedGeneration: Long?) {
        if (committedGeneration != null) require(committedGeneration >= 0)
        synchronized(monitor) {
            check(activeTickets.remove(ticket)) { "Clock mutation completion is unbalanced." }
            check(!completedTickets.containsKey(ticket)) { "Clock mutation completed twice." }
            completedTickets[ticket] = committedGeneration
            if (activeTickets.isNotEmpty()) return@synchronized

            var newestOutcome: Long? = null
            var greatestCommittedGeneration: Long? = null
            while (nextTicketToResolve < nextTicket) {
                check(completedTickets.containsKey(nextTicketToResolve)) {
                    "Clock mutation outcome sequence has a gap."
                }
                val outcome = completedTickets.remove(nextTicketToResolve)
                newestOutcome = outcome
                if (outcome != null) {
                    greatestCommittedGeneration = maxOf(
                        greatestCommittedGeneration ?: outcome,
                        outcome,
                    )
                }
                nextTicketToResolve = Math.incrementExact(nextTicketToResolve)
            }
            generation = when {
                newestOutcome == null -> null
                greatestCommittedGeneration == null -> null
                generation != null && greatestCommittedGeneration < generation!! -> null
                else -> greatestCommittedGeneration
            }
        }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ClockIntegrityRuntime(
    private val gateway: ClockStateUpdateGateway,
    private val rawClockSource: RawClockSource,
    private val generationSource: LoadedClockGenerationSource,
    private val scope: CoroutineScope,
    private val updateTimeoutMillis: Long = CLOCK_UPDATE_TIMEOUT_MILLIS,
) : ClockSignalHandler {
    private data class AcceptedMutation(
        val ticket: Long,
        val previousTurn: CompletableDeferred<Unit>,
        val turn: CompletableDeferred<Unit>,
    )

    private val acceptanceMonitor = Any()
    private var queueTail = CompletableDeferred<Unit>().apply { complete(Unit) }

    init {
        require(updateTimeoutMillis in 1..CLOCK_UPDATE_TIMEOUT_MILLIS) {
            "Clock update timeout must fit the broadcast execution budget."
        }
    }

    override fun handle(reason: ClockUpdateReason, completion: ClockSignalCompletion) {
        val deadlineNanos = System.nanoTime() + updateTimeoutMillis * NANOS_PER_MILLISECOND
        val accepted = synchronized(acceptanceMonitor) {
            val turn = CompletableDeferred<Unit>()
            AcceptedMutation(
                ticket = generationSource.beginMutation(),
                previousTurn = queueTail,
                turn = turn,
            ).also { queueTail = turn }
        }
        val completionClosed = AtomicBoolean(false)
        val mutationClosed = AtomicBoolean(false)
        fun releaseTurnWhenReady() {
            accepted.previousTurn.invokeOnCompletion { accepted.turn.complete(Unit) }
        }
        fun completeOnce(succeeded: Boolean) {
            if (!completionClosed.compareAndSet(false, true)) return
            try {
                completion.complete(succeeded)
            } catch (_: Throwable) {
                // Broadcast completion owns its own at-most-once guard.
            }
        }
        fun closeMutation(succeeded: Boolean, committedGeneration: Long?) {
            if (!mutationClosed.compareAndSet(false, true)) return
            generationSource.finishMutation(
                accepted.ticket,
                committedGeneration.takeIf { succeeded },
            )
        }

        val watchdog = try {
            scope.launch(start = CoroutineStart.ATOMIC) {
                delay(remainingTimeoutMillis(deadlineNanos))
                completeOnce(succeeded = false)
            }
        } catch (_: Throwable) {
            null
        }
        val worker = try {
            scope.launch(start = CoroutineStart.ATOMIC) {
                var committedGeneration: Long? = null
                var succeeded = false
                try {
                    withTimeout(remainingTimeoutMillis(deadlineNanos)) {
                        accepted.previousTurn.await()
                        val raw = rawClockSource.snapshot()
                        val stable = gateway.update(reason, raw)
                        committedGeneration = stable.clockGeneration
                    }
                    succeeded = true
                } catch (_: Throwable) {
                    succeeded = false
                } finally {
                    try {
                        closeMutation(succeeded, committedGeneration)
                    } finally {
                        releaseTurnWhenReady()
                        watchdog?.cancel()
                        completeOnce(succeeded)
                    }
                }
            }
        } catch (_: Throwable) {
            try {
                closeMutation(succeeded = false, committedGeneration = null)
            } finally {
                releaseTurnWhenReady()
                watchdog?.cancel()
                completeOnce(succeeded = false)
            }
            return
        }
        worker.invokeOnCompletion { failure ->
            if (failure != null) {
                try {
                    closeMutation(succeeded = false, committedGeneration = null)
                } finally {
                    releaseTurnWhenReady()
                    watchdog?.cancel()
                    completeOnce(succeeded = false)
                }
            }
        }
    }

    fun start() {
        handle(ClockUpdateReason.STARTUP) { }
    }

    fun onAppResume() {
        handle(ClockUpdateReason.APP_RESUME) { }
    }

    private fun remainingTimeoutMillis(deadlineNanos: Long): Long {
        val remainingNanos = deadlineNanos - System.nanoTime()
        if (remainingNanos <= 0) return 0
        return (remainingNanos - 1) / NANOS_PER_MILLISECOND + 1
    }

    private companion object {
        const val CLOCK_UPDATE_TIMEOUT_MILLIS = 9_000L
        const val NANOS_PER_MILLISECOND = 1_000_000L
    }
}
