package com.tbtechs.focusflow.data.backup

import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.Instant
import kotlinx.serialization.json.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupV1ParserTest {
    private val fixedNow = Instant.parse("2026-10-03T12:00:00Z")

    @Test
    fun acceptsVersionOneWithCanonicalizedTaskTimestamps() {
        val result = parse(backup(tasks = "[${task("one")}]"))

        assertTrue(result is BackupV1ParseResult.Success)
        val parsed = (result as BackupV1ParseResult.Success).backup.envelope.tasks.single()
        assertEquals("2026-10-03T08:00:00.000Z", parsed.startTime)
        assertEquals("2026-10-03T08:30:00.000Z", parsed.endTime)
        assertEquals("2026-10-03T07:00:00.000Z", parsed.wire.getValue("createdAt").toString().trim('"'))
    }

    @Test
    fun acceptsMissingVersionAndLeadingBom() {
        val noVersion = """{"kind":"${BackupV1Parser.ENVELOPE_KIND}","settings":{},"tasks":[]}"""

        assertTrue(parse(noVersion) is BackupV1ParseResult.Success)
        assertTrue(parse("\uFEFF$noVersion") is BackupV1ParseResult.Success)
    }

    @Test
    fun rejectsDuplicateKeysAtAnyDepthAndDuplicateTaskIds() {
        val duplicateSettingKey = backup(settings = """{"nested":{"x":1,"x":2}}""")
        val duplicateIds = backup(tasks = """[{"id":"same"},{"id":"same"}]""")

        assertTrue(parse(duplicateSettingKey) is BackupV1ParseResult.Error)
        assertTrue(parse(duplicateIds) is BackupV1ParseResult.Error)
    }

    @Test
    fun rejectsMalformedUtf8BeforeParsing() {
        val result = BackupV1Parser.parse(
            ByteArrayInputStream(byteArrayOf(0xC3.toByte(), 0x28)),
            fixedNow,
        )

        assertTrue(result is BackupV1ParseResult.Error)
        assertTrue((result as BackupV1ParseResult.Error).message.contains("UTF-8"))
    }

    @Test
    fun streamReadAcceptsExactByteLimitAndRejectsOneByteOver() {
        val valid = backup().toByteArray(StandardCharsets.UTF_8)
        val oneShort = valid + ByteArray(BackupJsonLimits.MAX_FILE_BYTES - valid.size - 1) {
            ' '.code.toByte()
        }
        val exactLimit = valid + ByteArray(BackupJsonLimits.MAX_FILE_BYTES - valid.size) { ' '.code.toByte() }
        val overLimit = exactLimit + byteArrayOf(' '.code.toByte())

        assertTrue(
            BackupV1Parser.parse(ByteArrayInputStream(oneShort), fixedNow) is
                BackupV1ParseResult.Success,
        )
        assertTrue(
            BackupV1Parser.parse(ByteArrayInputStream(exactLimit), fixedNow) is
                BackupV1ParseResult.Success,
        )
        assertTrue(
            BackupV1Parser.parse(ByteArrayInputStream(overLimit), fixedNow) is
                BackupV1ParseResult.Error,
        )
    }

    @Test
    fun compatibilityFixtureDropsOldLiveStateAndKeepsPortableSettings() {
        val fixture = requireNotNull(
            javaClass.classLoader?.getResource("backup/old-export-1.0.6.json"),
        ).readText()

        val result = parse(fixture)

        assertTrue(result is BackupV1ParseResult.Success)
        val settings = (result as BackupV1ParseResult.Success).backup.envelope.settings
        assertEquals(setOf("allowedInFocus"), settings.keys)
    }

    @Test
    fun unknownTopLevelAndTaskFieldsAreAcceptedAndTaskFieldsRemainAvailable() {
        val raw = """
            {
              "kind":"${BackupV1Parser.ENVELOPE_KIND}",
              "settings":{},
              "tasks":[${task("future", """"futureField":{"value":1}""")}],
              "futureEnvelopeField":{"enabled":true}
            }
        """.trimIndent()

        val result = parse(raw)

        assertTrue(result is BackupV1ParseResult.Success)
        val parsed = result as BackupV1ParseResult.Success
        val parsedTask = parsed.backup.envelope.tasks.single()
        assertEquals("""{"value":1}""", parsedTask.wire.getValue("futureField").toString())
        assertTrue(parsed.backup.envelope.raw.containsKey("futureEnvelopeField"))
    }

    @Test
    fun preservesAbsentNullAndEmptyFocusPackageLists() {
        val result = parse(
            backup(
                tasks = "[${task("absent")},${task("null", """"focusAllowedPackages":null""")}," +
                    "${task("empty", """"focusAllowedPackages":[]""")}]",
            ),
        )
        assertTrue(result is BackupV1ParseResult.Success)

        val tasks = (result as BackupV1ParseResult.Success).backup.envelope.tasks
        assertFalse(tasks[0].focusAllowedPackagesPresent)
        assertNull(tasks[0].focusAllowedPackages)
        assertTrue(tasks[1].focusAllowedPackagesPresent)
        assertNull(tasks[1].focusAllowedPackages)
        assertTrue(tasks[2].focusAllowedPackagesPresent)
        assertEquals(emptyList<String>(), tasks[2].focusAllowedPackages)
        assertFalse(tasks[0].wire.containsKey("focusAllowedPackages"))
        assertEquals(JsonNull, tasks[1].wire["focusAllowedPackages"])
    }

    @Test
    fun dropsInvalidTaskRecordsAndWarnsOnInvalidSettings() {
        val result = parse(
            backup(
                settings = """{"darkMode":"not-a-boolean"}""",
                tasks = """[{"id":"invalid","title":"Missing required fields"}]""",
            ),
        )

        assertTrue(result is BackupV1ParseResult.Success)
        val parsed = (result as BackupV1ParseResult.Success).backup
        assertEquals(1, parsed.invalidTaskCount)
        assertTrue(parsed.envelope.tasks.isEmpty())
        assertFalse(parsed.envelope.settings.containsKey("darkMode"))
        assertTrue(parsed.warnings.any { it.contains("darkMode") })
    }

    @Test
    fun rejectsUnsupportedVersionAndInvalidProtectionMode() {
        val unsupportedVersion =
            """{"kind":"${BackupV1Parser.ENVELOPE_KIND}","version":2,"settings":{},"tasks":[]}"""
        val badProtectionMode = backup(settings = """{"protectionMode":"unrecognized"}""")

        assertTrue(parse(unsupportedVersion) is BackupV1ParseResult.Error)
        assertTrue(parse(badProtectionMode) is BackupV1ParseResult.Error)
    }

    private fun parse(text: String) = BackupV1Parser.parse(text, fixedNow)

    private fun backup(settings: String = "{}", tasks: String = "[]"): String =
        """{"kind":"${BackupV1Parser.ENVELOPE_KIND}","settings":$settings,"tasks":$tasks}"""

    private fun task(id: String, extraField: String = ""): String {
        val extra = if (extraField.isBlank()) "" else ",$extraField"
        return """{"id":"$id","title":"Task","startTime":"2026-10-03T10:00:00+02:00","endTime":"2026-10-03T10:30:00+02:00","durationMinutes":30,"status":"scheduled","priority":"medium","createdAt":"2026-10-03T09:00:00+02:00","updatedAt":"2026-10-03T09:15:00+02:00"$extra}"""
    }
}
