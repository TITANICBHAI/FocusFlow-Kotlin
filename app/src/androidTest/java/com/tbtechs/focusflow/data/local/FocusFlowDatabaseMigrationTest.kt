package com.tbtechs.focusflow.data.local

import android.content.ContextWrapper
import android.database.sqlite.SQLiteDatabase
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regression coverage for the highest-risk schema transition.
 *
 * The setup uses the same table/column names as the RN database, including both
 * report note types and duplicate active sessions. The migration must preserve
 * both notes and deterministically keep the newest active session.
 */
@RunWith(AndroidJUnit4::class)
class FocusFlowDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FocusFlowDatabase::class.java.canonicalName,
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrateV4PreservesReportNotesAndReconcilesActiveSessions() {
        helper.createDatabase(DB_NAME, 4).apply {
            FocusFlowDatabase.MIGRATION_0_1.migrate(this)
            FocusFlowDatabase.MIGRATION_1_2.migrate(this)
            FocusFlowDatabase.MIGRATION_2_3.migrate(this)
            FocusFlowDatabase.MIGRATION_3_4.migrate(this)

            execSQL(
                """
                CREATE TABLE report_notes (
                    ref_date TEXT NOT NULL,
                    type TEXT NOT NULL,
                    note TEXT NOT NULL,
                    updated_at TEXT NOT NULL,
                    PRIMARY KEY(ref_date, type)
                )
                """.trimIndent(),
            )
            execSQL(
                "INSERT INTO report_notes VALUES ('2026-09-18', 'day', 'day note', '2026-09-18T10:00:00Z')",
            )
            execSQL(
                "INSERT INTO report_notes VALUES ('2026-09-15', 'week', 'week note', '2026-09-15T10:00:00Z')",
            )
            execSQL(
                """
                INSERT INTO focus_sessions
                    (task_id, started_at, ended_at, is_active, allowed_packages)
                VALUES
                    ('old', '2026-09-20T08:00:00Z', NULL, 1, '[]'),
                    ('new', '2026-09-20T09:00:00Z', NULL, 1, '[]')
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME,
            5,
            true,
            FocusFlowDatabase.MIGRATION_4_5,
        )

        migrated.query(
            "SELECT COUNT(*) FROM report_notes WHERE type IN ('day', 'week')",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(2, cursor.getInt(0))
        }
        migrated.query("SELECT COUNT(*) FROM focus_sessions WHERE is_active = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
        }
        migrated.query(
            "SELECT task_id FROM focus_sessions WHERE is_active = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("new", cursor.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migrateV6AddsShadowTablesWithoutChangingLegacyUsageRows() {
        helper.createDatabase(USAGE_DB_NAME, 6).apply {
            execSQL(
                """
                INSERT INTO daily_app_usage
                    (date, package_name, app_name, category, foreground_ms, hourly_ms, launch_count, last_used_at)
                VALUES ('2026-10-07', 'app.example', 'Example', 'utility', 123456,
                        '1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24', 3, 1791331200000)
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO app_sessions
                    (package_name, app_name, started_at, ended_at, duration_ms, local_date)
                VALUES ('app.example', 'Example', 1791390000000, 1791390123456, 123456, '2026-10-07')
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            USAGE_DB_NAME,
            7,
            true,
            FocusFlowDatabase.MIGRATION_6_7,
        )
        migrated.query(
            """
            SELECT date, package_name, app_name, category, foreground_ms, hourly_ms,
                   launch_count, last_used_at
            FROM daily_app_usage
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals("2026-10-07", cursor.getString(0))
            assertEquals("app.example", cursor.getString(1))
            assertEquals("Example", cursor.getString(2))
            assertEquals("utility", cursor.getString(3))
            assertEquals(123456L, cursor.getLong(4))
            assertEquals(
                "1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24",
                cursor.getString(5),
            )
            assertEquals(3, cursor.getInt(6))
            assertEquals(1791331200000L, cursor.getLong(7))
            assertFalse(cursor.moveToNext())
        }
        migrated.query(
            """
            SELECT id, package_name, app_name, started_at, ended_at, duration_ms, local_date
            FROM app_sessions
            """.trimIndent(),
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(1, cursor.getInt(0))
            assertEquals("app.example", cursor.getString(1))
            assertEquals("Example", cursor.getString(2))
            assertEquals(1791390000000L, cursor.getLong(3))
            assertEquals(1791390123456L, cursor.getLong(4))
            assertEquals(123456L, cursor.getLong(5))
            assertEquals("2026-10-07", cursor.getString(6))
            assertFalse(cursor.moveToNext())
        }
        migrated.query(
            "SELECT cutover_date, pipeline_version, shadow_started_on FROM usage_pipeline_state WHERE id = 1",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
            assertEquals(1, cursor.getInt(1))
            assertNotNull(cursor.getString(2))
        }
        listOf("usage_rollup_day", "usage_rollup_app_day", "usage_rollup_session").forEach { table ->
            migrated.query("SELECT COUNT(*) FROM `$table`").use { cursor ->
                assertTrue(cursor.moveToFirst())
                assertEquals(0, cursor.getInt(0))
            }
        }
        migrated.close()
    }

    @Test
    fun freshUserVersionZeroDatabaseCanReachTheShadowSchema() {
        helper.createDatabase(FRESH_DB_NAME, 0).apply {
            FocusFlowDatabase.MIGRATION_0_1.migrate(this)
            FocusFlowDatabase.MIGRATION_1_2.migrate(this)
            FocusFlowDatabase.MIGRATION_2_3.migrate(this)
            FocusFlowDatabase.MIGRATION_3_4.migrate(this)
            FocusFlowDatabase.MIGRATION_4_5.migrate(this)
            FocusFlowDatabase.MIGRATION_5_6.migrate(this)
            execSQL(
                """
                INSERT INTO daily_app_usage
                    (date, package_name, app_name, category, foreground_ms, hourly_ms,
                     launch_count, last_used_at)
                VALUES ('2026-10-07', 'app.fresh', 'Fresh', 'other', 9, '', 1, 10)
                """.trimIndent(),
            )
            execSQL("PRAGMA user_version = 6")
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            FRESH_DB_NAME,
            7,
            true,
            FocusFlowDatabase.MIGRATION_6_7,
        )
        migrated.query(
            "SELECT foreground_ms FROM daily_app_usage WHERE package_name = 'app.fresh'",
        ).use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertEquals(9L, cursor.getLong(0))
        }
        migrated.query("SELECT cutover_date FROM usage_pipeline_state WHERE id = 1").use { cursor ->
            assertTrue(cursor.moveToFirst())
            assertTrue(cursor.isNull(0))
        }
        migrated.close()
    }

    @Test
    fun legacyUserVersionZeroDatabaseIsPreparedForRoomMigrations() {
        val targetContext = InstrumentationRegistry.getInstrumentation().targetContext
        val databaseFile = File(targetContext.cacheDir, "legacy-user-version-zero-test.db")
        val legacyContext = object : ContextWrapper(targetContext) {
            override fun getDatabasePath(name: String): File = databaseFile
        }

        try {
            val legacyDb = SQLiteDatabase.openOrCreateDatabase(databaseFile, null)
            legacyDb.execSQL(
                "CREATE TABLE tasks (id TEXT NOT NULL PRIMARY KEY)",
            )
            legacyDb.version = 0
            legacyDb.close()

            FocusFlowDatabase.prepareLegacyDatabase(legacyContext)

            val preparedDb = SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                null,
                SQLiteDatabase.OPEN_READONLY,
            )
            try {
                assertEquals(1, preparedDb.version)
                preparedDb.rawQuery("PRAGMA table_info(tasks)", null).use { cursor ->
                    val nameIndex = cursor.getColumnIndexOrThrow("name")
                    val columns = buildList {
                        while (cursor.moveToNext()) add(cursor.getString(nameIndex))
                    }
                    assertTrue("focus_allowed_packages" in columns)
                }
            } finally {
                preparedDb.close()
            }
        } finally {
            databaseFile.delete()
        }
    }

    companion object {
        private const val DB_NAME = "focusflow-migration-test.db"
        private const val USAGE_DB_NAME = "focusflow-usage-migration-test.db"
        private const val FRESH_DB_NAME = "focusflow-usage-zero-migration-test.db"
    }
}