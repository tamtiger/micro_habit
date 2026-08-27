package vn.nhip2phut.app.time

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import vn.nhip2phut.domain.time.ClockUpdateReason
import vn.nhip2phut.domain.time.DurableClockState
import vn.nhip2phut.domain.time.RawClockSnapshot
import vn.nhip2phut.platform.time.ClockGenerationUnavailableException
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ClockIntegrityRuntimeTest {
    @Test
    fun `generation remains unavailable until durable startup completes`() {
        val generation = LoadedClockGenerationSource()
        val runtime = runtime(
            generation = generation,
            gateway = ClockStateUpdateGateway { _, _ -> state(7) },
        )
        var completed: Boolean? = null

        assertFailsWith<ClockGenerationUnavailableException> { generation.currentGeneration() }
        runtime.handle(ClockUpdateReason.STARTUP) { completed = it }

        assertEquals(true, completed)
        assertEquals(7L, generation.currentGeneration())
    }

    @Test
    fun `each accepted platform signal reaches the durable increment boundary`() {
        val generation = LoadedClockGenerationSource().apply { publish(0) }
        var updates = 0
        val runtime = runtime(
            generation = generation,
            gateway = ClockStateUpdateGateway { _, _ -> state((++updates).toLong()) },
        )
        val completions = mutableListOf<Boolean>()

        runtime.handle(ClockUpdateReason.TIME_SET) { completions += it }
        runtime.handle(ClockUpdateReason.TIME_SET) { completions += it }

        assertEquals(2, updates)
        assertEquals(listOf(true, true), completions)
        assertEquals(2L, generation.currentGeneration())
    }

    @Test
    fun `cold platform signal applies its reason in one durable transaction`() {
        val generation = LoadedClockGenerationSource()
        val reasons = mutableListOf<ClockUpdateReason>()
        val runtime = runtime(
            generation = generation,
            gateway = ClockStateUpdateGateway { reason, _ ->
                reasons += reason
                state(1)
            },
        )

        runtime.handle(ClockUpdateReason.TIME_SET) { assertTrue(it) }

        assertEquals(listOf(ClockUpdateReason.TIME_SET), reasons)
        assertEquals(1L, generation.currentGeneration())
    }

    @Test
    fun `storage failure invalidates a previously loaded generation`() {
        val generation = LoadedClockGenerationSource().apply { publish(3) }
        val runtime = runtime(
            generation = generation,
            gateway = ClockStateUpdateGateway { _, _ -> error("storage unavailable") },
        )
        var completed = true

        runtime.handle(ClockUpdateReason.APP_RESUME) { completed = it }

        assertFalse(completed)
        assertFailsWith<ClockGenerationUnavailableException> { generation.currentGeneration() }
    }

    @Test
    fun `different signals are serialized and both reach durable gateway`() {
        val generation = LoadedClockGenerationSource()
        val reasons = mutableListOf<ClockUpdateReason>()
        val runtime = runtime(
            generation = generation,
            gateway = ClockStateUpdateGateway { reason, _ ->
                reasons += reason
                state(reasons.size.toLong())
            },
        )

        runtime.handle(ClockUpdateReason.STARTUP) { assertTrue(it) }
        runtime.handle(ClockUpdateReason.TIMEZONE_CHANGED) { assertTrue(it) }

        assertEquals(listOf(ClockUpdateReason.STARTUP, ClockUpdateReason.TIMEZONE_CHANGED), reasons)
        assertEquals(2L, generation.currentGeneration())
    }

    @Test
    fun `generation is unavailable for the whole durable mutation window`() = runBlocking {
        val generation = LoadedClockGenerationSource().apply { publish(3) }
        val entered = CompletableDeferred<Unit>()
        val release = CompletableDeferred<Unit>()
        val completed = CompletableDeferred<Boolean>()
        val runtime = ClockIntegrityRuntime(
            gateway = ClockStateUpdateGateway { _, _ ->
                entered.complete(Unit)
                release.await()
                state(4)
            },
            rawClockSource = { RAW },
            generationSource = generation,
            scope = this,
        )

        runtime.handle(ClockUpdateReason.TIME_SET) { completed.complete(it) }
        entered.await()

        assertFailsWith<ClockGenerationUnavailableException> { generation.currentGeneration() }
        release.complete(Unit)
        assertTrue(completed.await())
        assertEquals(4L, generation.currentGeneration())
    }

    @Test
    fun `earlier failure cannot invalidate a later queued success`() = runBlocking {
        val generation = LoadedClockGenerationSource().apply { publish(3) }
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val completions = mutableListOf<Boolean>()
        val allCompleted = CompletableDeferred<Unit>()
        var call = 0
        val runtime = ClockIntegrityRuntime(
            gateway = ClockStateUpdateGateway { _, _ ->
                call++
                if (call == 1) {
                    firstEntered.complete(Unit)
                    releaseFirst.await()
                    error("first mutation failed")
                }
                state(5)
            },
            rawClockSource = { RAW },
            generationSource = generation,
            scope = this,
        )
        val recordCompletion = { succeeded: Boolean ->
            completions += succeeded
            if (completions.size == 2) allCompleted.complete(Unit)
        }

        runtime.handle(ClockUpdateReason.TIME_SET, recordCompletion)
        firstEntered.await()
        runtime.handle(ClockUpdateReason.TIMEZONE_CHANGED, recordCompletion)
        assertFailsWith<ClockGenerationUnavailableException> { generation.currentGeneration() }
        releaseFirst.complete(Unit)
        allCompleted.await()

        assertEquals(listOf(false, true), completions)
        assertEquals(5L, generation.currentGeneration())
    }

    @Test
    fun `queued signal expires within its own broadcast budget`() = runBlocking {
        val generation = LoadedClockGenerationSource().apply { publish(3) }
        val firstEntered = CompletableDeferred<Unit>()
        val releaseFirst = CompletableDeferred<Unit>()
        val firstCompleted = CompletableDeferred<Boolean>()
        val secondCompleted = CompletableDeferred<Boolean>()
        var firstCompletionCount = 0
        var secondCompletionCount = 0
        var calls = 0
        val runtime = ClockIntegrityRuntime(
            gateway = ClockStateUpdateGateway { _, _ ->
                calls++
                if (calls == 1) {
                    firstEntered.complete(Unit)
                    withContext(NonCancellable) { releaseFirst.await() }
                }
                state(calls.toLong())
            },
            rawClockSource = { RAW },
            generationSource = generation,
            scope = this,
            updateTimeoutMillis = 50,
        )

        runtime.handle(ClockUpdateReason.TIME_SET) {
            firstCompletionCount++
            firstCompleted.complete(it)
        }
        firstEntered.await()
        runtime.handle(ClockUpdateReason.TIMEZONE_CHANGED) {
            secondCompletionCount++
            secondCompleted.complete(it)
        }

        try {
            assertFalse(withTimeout(1_000) { secondCompleted.await() })
            assertFalse(withTimeout(1_000) { firstCompleted.await() })
            assertEquals(1, calls)
            assertEquals(1, firstCompletionCount)
            assertEquals(1, secondCompletionCount)
            assertFailsWith<ClockGenerationUnavailableException> { generation.currentGeneration() }
        } finally {
            releaseFirst.complete(Unit)
        }
        awaitMutationDrain(generation, publish = 4)
        assertEquals(1, firstCompletionCount)
        assertEquals(1, secondCompletionCount)
        assertEquals(4L, generation.currentGeneration())
    }

    @Test
    fun `cancelled scope balances mutation and completes failure`() = runBlocking {
        val generation = LoadedClockGenerationSource().apply { publish(3) }
        val cancelledJob = Job().apply { cancel() }
        val completed = CompletableDeferred<Boolean>()
        val runtime = ClockIntegrityRuntime(
            gateway = ClockStateUpdateGateway { _, _ -> state(4) },
            rawClockSource = { RAW },
            generationSource = generation,
            scope = CoroutineScope(cancelledJob + Dispatchers.Unconfined),
            updateTimeoutMillis = 50,
        )

        runtime.handle(ClockUpdateReason.TIME_SET) { completed.complete(it) }

        assertFalse(withTimeout(1_000) { completed.await() })
        generation.publish(4)
        assertEquals(4L, generation.currentGeneration())
    }

    @Test
    fun `generation outcomes resolve in accepted signal order`() {
        val generation = LoadedClockGenerationSource().apply { publish(3) }
        val first = generation.beginMutation()
        val second = generation.beginMutation()

        generation.finishMutation(second, committedGeneration = null)
        generation.finishMutation(first, committedGeneration = 4)

        assertFailsWith<ClockGenerationUnavailableException> { generation.currentGeneration() }

        val third = generation.beginMutation()
        val fourth = generation.beginMutation()
        generation.finishMutation(fourth, committedGeneration = 6)
        generation.finishMutation(third, committedGeneration = null)

        assertEquals(6L, generation.currentGeneration())
    }

    @Test
    fun `out of order successful completions publish the newest durable generation`() {
        val generation = LoadedClockGenerationSource().apply { publish(3) }
        val first = generation.beginMutation()
        val second = generation.beginMutation()

        generation.finishMutation(second, committedGeneration = 4)
        generation.finishMutation(first, committedGeneration = 5)

        assertEquals(5L, generation.currentGeneration())
    }

    private fun runtime(
        generation: LoadedClockGenerationSource,
        gateway: ClockStateUpdateGateway,
    ) = ClockIntegrityRuntime(
        gateway = gateway,
        rawClockSource = { RAW },
        generationSource = generation,
        scope = CoroutineScope(Dispatchers.Unconfined),
    )

    private fun state(generation: Long) = DurableClockState(
        clockGeneration = generation,
        bootMarker = RAW.bootMarker,
        zoneId = RAW.zoneId,
        elapsedRealtimeMillis = RAW.elapsedRealtimeMillis,
        wallMinusElapsedMillis = 1_000,
    )

    private suspend fun awaitMutationDrain(
        generation: LoadedClockGenerationSource,
        publish: Long,
    ) {
        withTimeout(1_000) {
            while (true) {
                try {
                    generation.publish(publish)
                    return@withTimeout
                } catch (_: IllegalStateException) {
                    yield()
                }
            }
        }
    }

    companion object {
        private val RAW = RawClockSnapshot(
            instant = Instant.ofEpochMilli(2_000),
            elapsedRealtimeMillis = 1_000,
            bootMarker = 1,
            zoneId = ZoneId.of("UTC"),
            utcOffsetMinutes = 0,
        )
    }
}
