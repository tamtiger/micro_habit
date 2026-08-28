package vn.nhip2phut.data.storage

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class Phase2PhysicalSchemaContractTest {
    private lateinit var context: Context
    private lateinit var database: Nhip2PhutDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(Nhip2PhutDatabase.DATABASE_NAME)
        database = Nhip2PhutDatabase.open(context)
    }

    @After
    fun tearDown() {
        database.close()
        context.deleteDatabase(Nhip2PhutDatabase.DATABASE_NAME)
    }

    @Test
    fun phase2TablesExposeOnlyTheExactPlaintextAllowlist() {
        val expectedColumns = mapOf(
            "clock_state" to setOf(
                "singleton_id",
                "crypto_version",
                "key_version",
                "payload_schema_version",
                "encrypted_payload",
            ),
            "app_profile" to setOf(
                "singleton_id",
                "crypto_version",
                "key_version",
                "payload_schema_version",
                "encrypted_payload",
            ),
            "work_schedule_version" to setOf(
                "id",
                "delete_after_epoch_day",
                "crypto_version",
                "key_version",
                "payload_schema_version",
                "encrypted_payload",
            ),
            "active_work_schedule" to setOf("singleton_id", "schedule_version_id"),
            "check_in" to setOf(
                "id",
                "parent_check_in_id",
                "schedule_version_id",
                "local_epoch_day",
                "delete_after_epoch_day",
                "rule_version",
                "crypto_version",
                "key_version",
                "payload_schema_version",
                "encrypted_payload",
            ),
            "decision" to setOf(
                "id",
                "check_in_id",
                "schedule_version_id",
                "local_epoch_day",
                "delete_after_epoch_day",
                "rule_version",
                "crypto_version",
                "key_version",
                "payload_schema_version",
                "encrypted_payload",
            ),
            "daily_constraint" to setOf(
                "id",
                "origin_local_epoch_day",
                "delete_after_epoch_day",
                "crypto_version",
                "key_version",
                "payload_schema_version",
                "encrypted_payload",
            ),
            "flow_timing_state" to setOf(
                "singleton_id",
                "crypto_version",
                "key_version",
                "payload_schema_version",
                "encrypted_payload",
            ),
            "product_event" to setOf(
                "id",
                "idempotency_key_version",
                "idempotency_key",
                "decision_id",
                "session_id",
                "reminder_occurrence_id",
                "schedule_version_id",
                "local_epoch_day",
                "delete_after_epoch_day",
                "crypto_version",
                "key_version",
                "payload_schema_version",
                "encrypted_payload",
            ),
            "product_event_entity_ref" to setOf("event_id", "ref_table", "ref_id"),
            "required_companion_event_ref" to setOf("event_id", "source_table", "source_id"),
        )

        assertEquals(expectedColumns.keys, userTableNames())
        expectedColumns.forEach { (table, columns) ->
            assertEquals("Unexpected plaintext columns in $table", columns, columnNames(table))
        }

        val forbiddenSensitiveColumns = setOf(
            "red_flag",
            "acute_issue",
            "energy",
            "stiffness",
            "intent",
            "outcome",
            "event_name",
            "work_start",
            "work_end",
            "reminder_times",
            "occurred_at_utc",
            "expires_at_utc",
            "zone_id",
        )
        expectedColumns.keys.forEach { table ->
            assertTrue(
                "$table leaked a user/event payload column",
                columnNames(table).intersect(forbiddenSensitiveColumns).isEmpty(),
            )
        }

        ENCRYPTED_TABLES.forEach { table ->
            assertEquals("BLOB", columnAffinities(table).getValue("encrypted_payload"))
        }
        assertEquals("BLOB", columnAffinities("product_event").getValue("idempotency_key"))
        assertEquals("BLOB", columnAffinities("product_event_entity_ref").getValue("ref_id"))
        assertEquals("BLOB", columnAffinities("required_companion_event_ref").getValue("source_id"))
    }

    @Test
    fun phase2ForeignKeysAndUniqueIndexesMatchTheClosedGraph() {
        assertEquals(
            setOf(ForeignKey("schedule_version_id", "work_schedule_version", "id", "RESTRICT")),
            foreignKeys("active_work_schedule"),
        )
        assertEquals(
            setOf(
                ForeignKey("parent_check_in_id", "check_in", "id", "RESTRICT"),
                ForeignKey("schedule_version_id", "work_schedule_version", "id", "RESTRICT"),
            ),
            foreignKeys("check_in"),
        )
        assertEquals(
            setOf(
                ForeignKey("check_in_id", "check_in", "id", "RESTRICT"),
                ForeignKey("schedule_version_id", "work_schedule_version", "id", "RESTRICT"),
            ),
            foreignKeys("decision"),
        )
        assertEquals(
            setOf(
                ForeignKey("decision_id", "decision", "id", "RESTRICT"),
                ForeignKey("schedule_version_id", "work_schedule_version", "id", "RESTRICT"),
            ),
            foreignKeys("product_event"),
        )
        assertEquals(
            setOf(ForeignKey("event_id", "product_event", "id", "CASCADE")),
            foreignKeys("product_event_entity_ref"),
        )
        assertEquals(
            setOf(ForeignKey("event_id", "product_event", "id", "RESTRICT")),
            foreignKeys("required_companion_event_ref"),
        )

        assertUniqueIndex("active_work_schedule", listOf("schedule_version_id"))
        assertUniqueIndex("decision", listOf("check_in_id"))
        assertUniqueIndex("daily_constraint", listOf("origin_local_epoch_day"))
        assertUniqueIndex(
            "product_event",
            listOf("idempotency_key_version", "idempotency_key"),
        )
        assertNonUniqueIndex("work_schedule_version", listOf("delete_after_epoch_day"))
        assertNonUniqueIndex("check_in", listOf("delete_after_epoch_day"))
        assertNonUniqueIndex("decision", listOf("delete_after_epoch_day"))
        assertNonUniqueIndex("daily_constraint", listOf("delete_after_epoch_day"))
        assertNonUniqueIndex("product_event", listOf("delete_after_epoch_day"))
    }

    @Test
    fun sqliteRejectsSingletonRuleAndPhysicalHmacInvariantBypass() {
        val sqlite = database.openHelper.writableDatabase
        sqlite.execSQL(
            "INSERT INTO work_schedule_version " +
                "(id, delete_after_epoch_day, crypto_version, key_version, " +
                "payload_schema_version, encrypted_payload) VALUES (?, NULL, 1, 1, 1, ?)",
            arrayOf(SCHEDULE_ID, payload()),
        )

        assertConstraintFailure {
            sqlite.execSQL(
                "INSERT INTO app_profile " +
                    "(singleton_id, crypto_version, key_version, payload_schema_version, " +
                    "encrypted_payload) VALUES (2, 1, 1, 1, ?)",
                arrayOf(payload()),
            )
        }
        assertConstraintFailure {
            sqlite.execSQL(
                "INSERT INTO active_work_schedule (singleton_id, schedule_version_id) " +
                    "VALUES (2, ?)",
                arrayOf(SCHEDULE_ID),
            )
        }
        assertConstraintFailure {
            sqlite.execSQL(
                "INSERT INTO flow_timing_state " +
                    "(singleton_id, crypto_version, key_version, payload_schema_version, " +
                    "encrypted_payload) VALUES (2, 1, 1, 1, ?)",
                arrayOf(payload()),
            )
        }
        assertConstraintFailure {
            insertCheckIn(ruleVersion = 2)
        }

        insertCheckIn(ruleVersion = 1)
        assertConstraintFailure {
            sqlite.execSQL(
                "INSERT INTO decision " +
                    "(id, check_in_id, schedule_version_id, local_epoch_day, " +
                    "delete_after_epoch_day, rule_version, crypto_version, key_version, " +
                    "payload_schema_version, encrypted_payload) " +
                    "VALUES (?, ?, ?, 20000, 20090, 2, 1, 1, 1, ?)",
                arrayOf(DECISION_ID, CHECK_IN_ID, SCHEDULE_ID, payload()),
            )
        }
        assertConstraintFailure {
            insertProductEvent(version = 2, physicalKey = ByteArray(32))
        }
        assertConstraintFailure {
            insertProductEvent(version = 1, physicalKey = ByteArray(31))
        }
    }

    private fun insertCheckIn(ruleVersion: Int) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO check_in " +
                "(id, parent_check_in_id, schedule_version_id, local_epoch_day, " +
                "delete_after_epoch_day, rule_version, crypto_version, key_version, " +
                "payload_schema_version, encrypted_payload) " +
                "VALUES (?, NULL, ?, 20000, 20090, ?, 1, 1, 1, ?)",
            arrayOf(CHECK_IN_ID, SCHEDULE_ID, ruleVersion, payload()),
        )
    }

    private fun insertProductEvent(version: Int, physicalKey: ByteArray) {
        database.openHelper.writableDatabase.execSQL(
            "INSERT INTO product_event " +
                "(id, idempotency_key_version, idempotency_key, decision_id, session_id, " +
                "reminder_occurrence_id, schedule_version_id, local_epoch_day, " +
                "delete_after_epoch_day, crypto_version, key_version, payload_schema_version, " +
                "encrypted_payload) " +
                "VALUES (?, ?, ?, NULL, NULL, NULL, NULL, 20000, 20090, 1, 1, 1, ?)",
            arrayOf(EVENT_ID, version, physicalKey, payload()),
        )
    }

    private fun userTableNames(): Set<String> = buildSet {
        database.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master " +
                "WHERE type = 'table' AND name NOT LIKE 'android_%' " +
                "AND name NOT LIKE 'room_%' AND name NOT LIKE 'sqlite_%'",
        ).use { cursor ->
            while (cursor.moveToNext()) add(cursor.getString(0))
        }
    }

    private fun columnNames(table: String): Set<String> = columnAffinities(table).keys

    private fun columnAffinities(table: String): Map<String, String> = buildMap {
        database.openHelper.readableDatabase.query("PRAGMA table_info(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                put(cursor.getString(cursor.getColumnIndexOrThrow("name")), cursor.getString(
                    cursor.getColumnIndexOrThrow("type"),
                ).uppercase())
            }
        }
    }

    private fun foreignKeys(table: String): Set<ForeignKey> = buildSet {
        database.openHelper.readableDatabase.query("PRAGMA foreign_key_list(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                add(
                    ForeignKey(
                        from = cursor.getString(cursor.getColumnIndexOrThrow("from")),
                        targetTable = cursor.getString(cursor.getColumnIndexOrThrow("table")),
                        targetColumn = cursor.getString(cursor.getColumnIndexOrThrow("to")),
                        onDelete = cursor.getString(cursor.getColumnIndexOrThrow("on_delete"))
                            .uppercase(),
                    ),
                )
            }
        }
    }

    private fun indexes(table: String): List<IndexContract> = buildList {
        val readable = database.openHelper.readableDatabase
        readable.query("PRAGMA index_list(`$table`)").use { cursor ->
            while (cursor.moveToNext()) {
                val indexName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                val unique = cursor.getInt(cursor.getColumnIndexOrThrow("unique")) == 1
                val columns = buildList {
                    readable.query("PRAGMA index_info(`$indexName`)").use { columnCursor ->
                        while (columnCursor.moveToNext()) {
                            add(columnCursor.getString(columnCursor.getColumnIndexOrThrow("name")))
                        }
                    }
                }
                add(IndexContract(unique, columns))
            }
        }
    }

    private fun assertUniqueIndex(table: String, columns: List<String>) {
        assertTrue(
            "$table must have a unique index on $columns",
            indexes(table).any { it.unique && it.columns == columns },
        )
    }

    private fun assertNonUniqueIndex(table: String, columns: List<String>) {
        assertTrue(
            "$table must have an index on $columns",
            indexes(table).any { !it.unique && it.columns == columns },
        )
    }

    private inline fun assertConstraintFailure(block: () -> Unit) {
        try {
            block()
            fail("Expected SQLiteConstraintException.")
        } catch (failure: Throwable) {
            if (failure !is SQLiteConstraintException) throw failure
        }
    }

    private fun payload(): ByteArray = byteArrayOf(1, 2, 3)

    private data class ForeignKey(
        val from: String,
        val targetTable: String,
        val targetColumn: String,
        val onDelete: String,
    )

    private data class IndexContract(
        val unique: Boolean,
        val columns: List<String>,
    )

    private companion object {
        val ENCRYPTED_TABLES = setOf(
            "clock_state",
            "app_profile",
            "work_schedule_version",
            "check_in",
            "decision",
            "daily_constraint",
            "flow_timing_state",
            "product_event",
        )

        const val SCHEDULE_ID = "00000000-0000-0000-0000-000000000201"
        const val CHECK_IN_ID = "00000000-0000-0000-0000-000000000202"
        const val DECISION_ID = "00000000-0000-0000-0000-000000000203"
        const val EVENT_ID = "00000000-0000-0000-0000-000000000204"
    }
}
