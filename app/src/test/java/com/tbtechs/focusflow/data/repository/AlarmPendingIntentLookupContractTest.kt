package com.tbtechs.focusflow.data.repository

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlarmPendingIntentLookupContractTest {

    @Test
    fun noCreateLookupsAreNullableAndCancellationUsesThem() {
        val source = alarmRepositorySource()
        val cancellation = between(
            source,
            "internal fun cancelTaskEndAlarmWithinReconciliation",
            "    /**\n     * Finishes visible TaskAlarmActivity",
        )

        assertTrue(
            source.contains("internal fun findAlarmPendingIntent(") &&
                source.contains("): PendingIntent? ="),
        )
        assertTrue(
            source.contains("internal fun findShowPendingIntent(") &&
                source.contains("): PendingIntent? {"),
        )
        assertTrue(cancellation.contains("findAlarmPendingIntent(context, id)"))
        assertTrue(cancellation.contains("findShowPendingIntent(context, id)"))
        assertFalse(cancellation.contains("buildAlarmPendingIntent(context, id"))
        assertFalse(cancellation.contains("buildShowPendingIntent(context, id"))

        val removeRegistry = cancellation.indexOf("registry.remove(id)")
        assertTrue(removeRegistry > cancellation.indexOf("if (alarmPi != null)"))
        assertTrue(removeRegistry > cancellation.indexOf("if (showPi != null)"))
    }

    @Test
    fun creationAndLookupShareCanonicalTaskIntents() {
        val source = alarmRepositorySource()
        val alarmBuilder = between(
            source,
            "fun buildAlarmPendingIntent(",
            "        /** Find an existing task-end alarm",
        )
        val alarmLookup = between(
            source,
            "internal fun findAlarmPendingIntent(",
            "        /** Build the task-specific show/full-screen",
        )
        val showBuilder = between(
            source,
            "fun buildShowPendingIntent(",
            "        /** Find an existing task-end Activity",
        )
        val showLookup = between(
            source,
            "internal fun findShowPendingIntent(",
            "\n    }\n\n    /**\n     * Schedules a wake-up alarm",
        )

        assertTrue(alarmBuilder.contains("alarmIntent(ctx, taskId, taskName, endMs)"))
        assertTrue(alarmLookup.contains("alarmIntent(ctx, taskId, \"\", 0L)"))
        assertTrue(showBuilder.contains("showIntent(ctx, taskId, taskName, endMs)"))
        assertTrue(showLookup.contains("showIntent(ctx, taskId, \"\", 0L)"))
    }

    private fun between(source: String, start: String, end: String): String {
        val startIndex = source.indexOf(start)
        assertTrue("Missing source marker: $start", startIndex >= 0)
        val endIndex = source.indexOf(end, startIndex + start.length)
        assertTrue("Missing source marker: $end", endIndex > startIndex)
        return source.substring(startIndex, endIndex)
    }

    private fun alarmRepositorySource(): String {
        val start = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize()
        var current: Path? = start
        while (current != null) {
            val direct = current.resolve("src/main/java/com/tbtechs/focusflow")
            if (Files.isDirectory(direct)) {
                return direct.resolve("data/repository/AlarmRepository.kt")
                    .toFile()
                    .readText(Charsets.UTF_8)
            }
            val inApp = current.resolve("app/src/main/java/com/tbtechs/focusflow")
            if (Files.isDirectory(inApp)) {
                return inApp.resolve("data/repository/AlarmRepository.kt")
                    .toFile()
                    .readText(Charsets.UTF_8)
            }
            current = current.parent
        }
        throw AssertionError("Could not find the app source from $start")
    }
}
