package vn.nhip2phut.data.storage

import android.content.Context
import androidx.room.testing.MigrationTestHelper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Nhip2PhutDatabaseMigrationTest {
    private lateinit var context: Context
    private var database: Nhip2PhutDatabase? = null

    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        Nhip2PhutDatabase::class.java,
    )

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database?.close()
        context.deleteDatabase(Nhip2PhutDatabase.DATABASE_NAME)
    }

    @After
    fun tearDown() {
        database?.close()
        database = null
        context.deleteDatabase(Nhip2PhutDatabase.DATABASE_NAME)
    }

    @Test
    fun exportedSchemaV1CreatesAndReopensWithProductionDatabase() = runBlocking {
        val encryptedPayload = byteArrayOf(7, 11, 13, 17)
        val schemaV1 = migrationHelper.createDatabase(
            Nhip2PhutDatabase.DATABASE_NAME,
            Nhip2PhutDatabase.SCHEMA_VERSION,
        )
        try {
            assertEquals(Nhip2PhutDatabase.SCHEMA_VERSION, schemaV1.version)
            schemaV1.execSQL(
                "INSERT INTO clock_state " +
                    "(singleton_id, crypto_version, key_version, payload_schema_version, encrypted_payload) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf(ClockStateEntity.SINGLETON_ID, 1, 1, 1, encryptedPayload),
            )
        } finally {
            schemaV1.close()
        }

        val reopened = Nhip2PhutDatabase.open(context).also { database = it }
        val persisted = reopened.clockStateDao().read()

        assertNotNull(persisted)
        assertEquals(ClockStateEntity.SINGLETON_ID, persisted!!.singletonId)
        assertArrayEquals(encryptedPayload, persisted.encryptedPayload)
        assertEquals(Nhip2PhutDatabase.SCHEMA_VERSION, reopened.openHelper.readableDatabase.version)
    }
}
