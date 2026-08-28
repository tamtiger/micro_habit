package vn.nhip2phut.data.events

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import vn.nhip2phut.data.storage.Nhip2PhutDatabase

@RunWith(AndroidJUnit4::class)
class ProductEventPhysicalContractTest {
    private lateinit var database: Nhip2PhutDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, Nhip2PhutDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun physicalKeyIsBlobVersionOneFull32BytesAndUniqueAsPair() = runBlocking {
        val physicalKey = ByteArray(32) { it.toByte() }
        database.productEventDao().insert(event(EVENT_A, physicalKey))

        database.openHelper.readableDatabase.query(
            "SELECT typeof(idempotency_key), length(idempotency_key), " +
                "idempotency_key_version FROM product_event WHERE id = ?",
            arrayOf(EVENT_A),
        ).use { cursor ->
            check(cursor.moveToFirst())
            assertEquals("blob", cursor.getString(0))
            assertEquals(32, cursor.getInt(1))
            assertEquals(1, cursor.getInt(2))
        }

        assertThrows<SQLiteConstraintException> {
            database.productEventDao().insert(event(EVENT_B, physicalKey))
        }
        assertEquals(1, database.productEventDao().count())
    }

    private fun event(id: String, physicalKey: ByteArray) = ProductEventEntity(
        id = id,
        idempotencyKeyVersion = 1,
        idempotencyKey = physicalKey,
        decisionId = null,
        sessionId = null,
        reminderOccurrenceId = null,
        scheduleVersionId = null,
        localEpochDay = 20_000,
        deleteAfterEpochDay = 20_090,
        cryptoVersion = 1,
        keyVersion = 1,
        payloadSchemaVersion = 1,
        encryptedPayload = byteArrayOf(9, 8, 7),
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

    private companion object {
        const val EVENT_A = "00000000-0000-0000-0000-000000000101"
        const val EVENT_B = "00000000-0000-0000-0000-000000000102"
    }
}
