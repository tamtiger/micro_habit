package vn.nhip2phut.data.storage

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import vn.nhip2phut.data.crypto.AesGcmEnvelopeCryptoV1
import vn.nhip2phut.data.crypto.AesKeyProviderV1
import vn.nhip2phut.data.crypto.CryptoKeyUnavailableException
import vn.nhip2phut.domain.time.ClockUpdateReason
import vn.nhip2phut.domain.time.DurableClockState
import vn.nhip2phut.domain.time.RawClockSnapshot
import java.time.Instant
import java.time.ZoneId
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

@RunWith(AndroidJUnit4::class)
class ClockStateAtomicUpdateTest {
    private lateinit var database: Nhip2PhutDatabase
    private lateinit var repository: EncryptedClockStateRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, Nhip2PhutDatabase::class.java).build()
        repository = EncryptedClockStateRepository(
            dao = database.clockStateDao(),
            crypto = AesGcmEnvelopeCryptoV1(InMemoryKeyProvider()),
            database = database,
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun concurrentSignalsSerializeWithoutLostGeneration() = runBlocking {
        repository.replace(state(generation = 0, elapsed = 1_000))
        val coordinator = DurableClockStateCoordinator(repository)

        val generations = coroutineScope {
            listOf(
                async(Dispatchers.IO) { coordinator.update(ClockUpdateReason.TIME_SET, raw(2_000)) },
                async(Dispatchers.IO) { coordinator.update(ClockUpdateReason.TIME_SET, raw(2_000)) },
            ).awaitAll().map { it.clockGeneration }.sorted()
        }

        assertEquals(listOf(1L, 2L), generations)
        assertEquals(2L, repository.read()!!.clockGeneration)
    }

    @Test
    fun regressingGenerationRollsBackAndStableStateRemainsReadable() = runBlocking {
        val original = state(generation = 5, elapsed = 1_000)
        repository.replace(original)

        assertThrows<ClockStateRegressionException> {
            repository.updateAtomically { it!!.copy(clockGeneration = 4) }
        }

        assertEquals(original, repository.read())
    }

    private fun raw(elapsed: Long) = RawClockSnapshot(
        instant = Instant.ofEpochMilli(10_000 + elapsed),
        elapsedRealtimeMillis = elapsed,
        bootMarker = 1,
        zoneId = ZoneId.of("UTC"),
        utcOffsetMinutes = 0,
    )

    private fun state(generation: Long, elapsed: Long) =
        raw(elapsed).toDurableState(generation)

    private suspend inline fun <reified T : Throwable> assertThrows(
        crossinline block: suspend () -> Unit,
    ) {
        try {
            block()
            fail("Expected ${T::class.java.simpleName}.")
        } catch (failure: Throwable) {
            if (failure !is T) throw failure
        }
    }

    private class InMemoryKeyProvider : AesKeyProviderV1 {
        private var key: SecretKey? = null

        override fun keyForEncryption(keyVersion: Int, allowCreation: Boolean): SecretKey {
            key?.let { return it }
            if (!allowCreation) throw CryptoKeyUnavailableException()
            return KeyGenerator.getInstance("AES").apply { init(256) }.generateKey().also {
                key = it
            }
        }

        override fun keyForDecryption(keyVersion: Int): SecretKey =
            key ?: throw CryptoKeyUnavailableException()
    }
}
