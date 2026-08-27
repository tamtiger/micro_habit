package vn.nhip2phut.data.storage

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import vn.nhip2phut.data.crypto.AesGcmEnvelopeCryptoV1
import vn.nhip2phut.data.crypto.AndroidKeyStoreKeyProviderV1
import vn.nhip2phut.data.crypto.CryptoAuthenticationException
import vn.nhip2phut.data.crypto.CryptoKeyUnavailableException
import vn.nhip2phut.domain.time.DurableClockState
import java.time.ZoneId

@RunWith(AndroidJUnit4::class)
class EncryptedClockStateRoomTest {
    private lateinit var context: Context
    private lateinit var keyProvider: AndroidKeyStoreKeyProviderV1
    private var database: Nhip2PhutDatabase? = null

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        keyProvider = AndroidKeyStoreKeyProviderV1()
        database?.close()
        context.deleteDatabase(Nhip2PhutDatabase.DATABASE_NAME)
        if (keyProvider.containsKey(KEY_VERSION)) keyProvider.deleteKey(KEY_VERSION)
    }

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(Nhip2PhutDatabase.DATABASE_NAME)
        if (keyProvider.containsKey(KEY_VERSION)) keyProvider.deleteKey(KEY_VERSION)
    }

    @Test
    fun productionDatabaseRoundTripsAfterReopen() = runBlocking {
        val expected = state(generation = 9)
        val first = openDatabase()
        repository(first).replace(expected)
        val physical = first.clockStateDao().read()
        assertNotNull(physical)
        assertFalse(physical!!.encryptedPayload.contentEquals(ClockStatePayloadCodecV1.encode(expected)))
        assertNull(keyProvider.keyForDecryption(KEY_VERSION).encoded)

        first.close()
        database = null
        val reopened = openDatabase()

        assertEquals(expected, repository(reopened).read())
    }

    @Test
    fun tamperedCiphertextFailsClosed() = runBlocking {
        val db = openDatabase()
        val repository = repository(db)
        repository.replace(state(generation = 1))
        val original = requireNotNull(db.clockStateDao().read())
        val tampered = original.encryptedPayload.copyOf().also {
            it[it.lastIndex] = (it.last().toInt() xor 1).toByte()
        }
        db.clockStateDao().replace(original.copy(encryptedPayload = tampered))

        assertThrows<CryptoAuthenticationException> { repository.read() }
    }

    @Test
    fun plaintextDispatchMetadataMismatchFailsClosedBeforeOverwrite() = runBlocking {
        val db = openDatabase()
        val repository = repository(db)
        repository.replace(state(generation = 1))
        val original = requireNotNull(db.clockStateDao().read())
        db.clockStateDao().replace(original.copy(keyVersion = original.keyVersion + 1))

        assertThrows<ClockStateStorageException> { repository.read() }
    }

    @Test
    fun missingAliasCannotBeRegeneratedOverExistingCiphertext() = runBlocking {
        val db = openDatabase()
        val repository = repository(db)
        repository.replace(state(generation = 1))
        keyProvider.deleteKey(KEY_VERSION)

        assertThrows<CryptoKeyUnavailableException> { repository.read() }
        assertThrows<CryptoKeyUnavailableException> {
            repository.replace(state(generation = 2))
        }
        assertFalse(keyProvider.containsKey(KEY_VERSION))
    }

    private fun openDatabase(): Nhip2PhutDatabase =
        Nhip2PhutDatabase.open(context)
            .also { database = it }

    private fun repository(db: Nhip2PhutDatabase) = EncryptedClockStateRepository(
        dao = db.clockStateDao(),
        crypto = AesGcmEnvelopeCryptoV1(keyProvider),
        database = db,
    )

    private fun state(generation: Long) = DurableClockState(
        clockGeneration = generation,
        bootMarker = 12,
        zoneId = ZoneId.of("Asia/Bangkok"),
        elapsedRealtimeMillis = 45_000,
        wallMinusElapsedMillis = 1_700_000_000_000,
    )

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

    companion object {
        private const val KEY_VERSION = 1
    }
}
