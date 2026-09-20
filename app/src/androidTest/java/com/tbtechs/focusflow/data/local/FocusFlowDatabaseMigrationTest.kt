package com.tbtechs.focusflow.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
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

    companion object {
        private const val DB_NAME = "focusflow-migration-test.db"
    }
}