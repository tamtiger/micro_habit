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
import org.junit.Assert.assertTrue
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
    fun migrationV1ToV2PreservesClockStateAndValidatesEveryPhase2Table() = runBlocking {
        val encryptedPayload = byteArrayOf(7, 11, 13, 17)
        val schemaV1 = migrationHelper.createDatabase(
            Nhip2PhutDatabase.DATABASE_NAME,
            SOURCE_SCHEMA_VERSION,
        )
        try {
            assertEquals(SOURCE_SCHEMA_VERSION, schemaV1.version)
            schemaV1.execSQL(
                "INSERT INTO clock_state " +
                    "(singleton_id, crypto_version, key_version, payload_schema_version, encrypted_payload) " +
                    "VALUES (?, ?, ?, ?, ?)",
                arrayOf(ClockStateEntity.SINGLETON_ID, 1, 1, 1, encryptedPayload),
            )
        } finally {
            schemaV1.close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(
            Nhip2PhutDatabase.DATABASE_NAME,
            TARGET_SCHEMA_VERSION,
            true,
            Nhip2PhutDatabase.MIGRATION_1_2,
        )
        try {
            val actualTables = buildSet {
                migrated.query(
                    "SELECT name FROM sqlite_master WHERE type = 'table'",
                ).use { cursor ->
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            EXPECTED_PHASE2_TABLES.forEach { tableName ->
                assertTrue("Missing migrated table $tableName", tableName in actualTables)
            }
        } finally {
            migrated.close()
        }

        val reopened = Nhip2PhutDatabase.open(context).also { database = it }
        val persisted = reopened.clockStateDao().read()

        assertNotNull(persisted)
        assertEquals(ClockStateEntity.SINGLETON_ID, persisted!!.singletonId)
        assertArrayEquals(encryptedPayload, persisted.encryptedPayload)
        assertEquals(TARGET_SCHEMA_VERSION, Nhip2PhutDatabase.SCHEMA_VERSION)
        assertEquals(TARGET_SCHEMA_VERSION, reopened.openHelper.readableDatabase.version)
    }

    @Test
    fun productionOpenRegistersExplicitV1ToV2MigrationAndPreservesTheEntireClockRow() =
        runBlocking {
            val encryptedPayload = byteArrayOf(23, 29, 31, 37)
            val schemaV1 = migrationHelper.createDatabase(
                Nhip2PhutDatabase.DATABASE_NAME,
                SOURCE_SCHEMA_VERSION,
            )
            try {
                schemaV1.execSQL(
                    "INSERT INTO clock_state " +
                        "(singleton_id, crypto_version, key_version, payload_schema_version, " +
                        "encrypted_payload) VALUES (?, ?, ?, ?, ?)",
                    arrayOf(ClockStateEntity.SINGLETON_ID, 1, 7, 1, encryptedPayload),
                )
            } finally {
                schemaV1.close()
            }

            val openedByProductionBuilder = Nhip2PhutDatabase.open(context).also { database = it }
            val persisted = requireNotNull(openedByProductionBuilder.clockStateDao().read())

            assertEquals(
                listOf(SOURCE_SCHEMA_VERSION to TARGET_SCHEMA_VERSION),
                Nhip2PhutDatabase.MIGRATIONS.map { it.startVersion to it.endVersion },
            )
            assertEquals(TARGET_SCHEMA_VERSION, openedByProductionBuilder.openHelper.readableDatabase.version)
            assertEquals(ClockStateEntity.SINGLETON_ID, persisted.singletonId)
            assertEquals(1, persisted.cryptoVersion)
            assertEquals(7, persisted.keyVersion)
            assertEquals(1, persisted.payloadSchemaVersion)
            assertArrayEquals(encryptedPayload, persisted.encryptedPayload)

            val migratedTables = buildSet {
                openedByProductionBuilder.openHelper.readableDatabase.query(
                    "SELECT name FROM sqlite_master WHERE type = 'table'",
                ).use { cursor ->
                    while (cursor.moveToNext()) add(cursor.getString(0))
                }
            }
            EXPECTED_PHASE2_TABLES.forEach { tableName ->
                assertTrue("Production open omitted $tableName", tableName in migratedTables)
            }
        }

    private companion object {
        const val SOURCE_SCHEMA_VERSION = 1
        const val TARGET_SCHEMA_VERSION = 2

        val EXPECTED_PHASE2_TABLES = setOf(
            "clock_state",
            "app_profile",
            "work_schedule_version",
            "active_work_schedule",
            "check_in",
            "decision",
            "daily_constraint",
            "flow_timing_state",
            "product_event",
            "product_event_entity_ref",
            "required_companion_event_ref",
        )
    }
}
