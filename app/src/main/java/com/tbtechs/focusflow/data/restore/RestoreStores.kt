package com.tbtechs.focusflow.data.restore

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject

@Serializable
data class PendingImportRecord(
    val documentVersion: Int = DOCUMENT_VERSION,
    val displayName: String,
    val normalizedBackupJson: String,
    val warnings: List<String>,
    val invalidTaskCount: Int,
) {
    companion object {
        const val DOCUMENT_VERSION = 1
    }
}

sealed interface PendingImportRead {
    data object Missing : PendingImportRead
    data class Value(val record: PendingImportRecord) : PendingImportRead
    data class Error(val message: String) : PendingImportRead
}

interface PendingImportStore {
    fun exists(): Boolean
    suspend fun read(): PendingImportRead
    suspend fun write(record: PendingImportRecord)
    suspend fun delete()
}

sealed interface RestoreJournalRead {
    data object Missing : RestoreJournalRead
    data class Value(val journal: RestoreJournal) : RestoreJournalRead
    data class Corrupt(val message: String) : RestoreJournalRead
    data class UnknownVersion(val version: Int?) : RestoreJournalRead
}

interface RestoreJournalStore {
    fun hasJournal(): Boolean
    fun hasQuarantine(): Boolean
    suspend fun read(): RestoreJournalRead
    suspend fun write(journal: RestoreJournal)
    suspend fun quarantine()
    suspend fun restoreQuarantineForRetry()
    suspend fun deleteJournal()
    suspend fun deleteQuarantine()
}

class AtomicPendingImportStore(context: Context) : PendingImportStore {
    private val atomicFile = AtomicFile(
        File(File(context.applicationContext.noBackupFilesDir, "pending-import"), "import.json"),
    )
    private val json = Json { ignoreUnknownKeys = false }

    override fun exists(): Boolean = atomicFile.baseFile.exists() ||
        File(atomicFile.baseFile.path + ".bak").exists()

    override suspend fun read(): PendingImportRead = withContext(Dispatchers.IO) {
        if (!exists()) return@withContext PendingImportRead.Missing
        try {
            val text = atomicFile.openRead().use(::readBoundedText)
            val record = json.decodeFromString<PendingImportRecord>(text)
            if (record.documentVersion != PendingImportRecord.DOCUMENT_VERSION) {
                PendingImportRead.Error("The saved import uses an unsupported version.")
            } else {
                PendingImportRead.Value(record)
            }
        } catch (error: Exception) {
            PendingImportRead.Error(error.message ?: "The saved import could not be read.")
        }
    }

    override suspend fun write(record: PendingImportRecord) = withContext(Dispatchers.IO) {
        require(record.documentVersion == PendingImportRecord.DOCUMENT_VERSION)
        atomicWrite(atomicFile, json.encodeToString(record))
    }

    override suspend fun delete() = withContext(Dispatchers.IO) {
        atomicFile.delete()
    }
}

class AtomicRestoreJournalStore(context: Context) : RestoreJournalStore {
    private val directory = File(context.applicationContext.noBackupFilesDir, "restore")
    private val journalFile = File(directory, "journal.json")
    private val quarantineFile = File(directory, "journal.quarantine")
    private val atomicFile = AtomicFile(journalFile)
    private val json = Json { ignoreUnknownKeys = false }

    override fun hasJournal(): Boolean = journalFile.exists() ||
        File(journalFile.path + ".bak").exists()

    override fun hasQuarantine(): Boolean = quarantineFile.exists()

    override suspend fun read(): RestoreJournalRead = withContext(Dispatchers.IO) {
        if (!hasJournal()) return@withContext RestoreJournalRead.Missing
        try {
            val text = atomicFile.openRead().use(::readBoundedText)
            val root = json.parseToJsonElement(text) as? JsonObject
                ?: return@withContext RestoreJournalRead.Corrupt("Journal root is not an object.")
            val version = root["journalVersion"]?.let { (it as? kotlinx.serialization.json.JsonPrimitive)?.intOrNull }
            if (version != RestoreJournal.JOURNAL_VERSION) {
                return@withContext RestoreJournalRead.UnknownVersion(version)
            }
            val journal = json.decodeFromString<RestoreJournal>(text)
            RestoreJournalRead.Value(journal)
        } catch (error: FileNotFoundException) {
            RestoreJournalRead.Missing
        } catch (error: Exception) {
            RestoreJournalRead.Corrupt(error.message ?: "The restore journal could not be read.")
        }
    }

    override suspend fun write(journal: RestoreJournal) = withContext(Dispatchers.IO) {
        require(journal.journalVersion == RestoreJournal.JOURNAL_VERSION)
        atomicWrite(atomicFile, json.encodeToString(journal))
    }

    override suspend fun quarantine() = withContext(Dispatchers.IO) {
        directory.mkdirs()
        check(!quarantineFile.exists()) {
            "A quarantined restore journal already exists."
        }
        if (journalFile.exists()) {
            val moved = journalFile.renameTo(quarantineFile)
            check(moved) { "Could not quarantine the unreadable restore journal." }
        } else {
            check(quarantineFile.exists()) { "No restore journal was available to quarantine." }
        }
        File(journalFile.path + ".bak").delete()
        Unit
    }

    override suspend fun restoreQuarantineForRetry() = withContext(Dispatchers.IO) {
        if (quarantineFile.exists()) {
            check(!journalFile.exists()) {
                "A restore journal already exists; refusing to replace it with quarantine."
            }
            check(quarantineFile.renameTo(journalFile)) {
                "Could not restore the quarantined journal for retry."
            }
        }
    }

    override suspend fun deleteJournal() = withContext(Dispatchers.IO) {
        atomicFile.delete()
        File(journalFile.path + ".bak").delete()
        Unit
    }

    override suspend fun deleteQuarantine() = withContext(Dispatchers.IO) {
        check(!quarantineFile.exists() || quarantineFile.delete()) {
            "Could not remove the quarantined restore journal."
        }
    }
}

private const val MAX_STORED_DOCUMENT_BYTES = 24 * 1024 * 1024

private fun readBoundedText(input: InputStream): String {
    val output = input.readBytesBounded(MAX_STORED_DOCUMENT_BYTES)
    return output.toString(Charsets.UTF_8)
}

private fun InputStream.readBytesBounded(limit: Int): ByteArray {
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    val output = java.io.ByteArrayOutputStream()
    var total = 0
    while (true) {
        val count = read(buffer)
        if (count < 0) break
        total += count
        if (total > limit) throw SerializationException("Stored restore data exceeds its size limit.")
        output.write(buffer, 0, count)
    }
    return output.toByteArray()
}

private fun atomicWrite(file: AtomicFile, text: String) {
    file.baseFile.parentFile?.mkdirs()
    val stream = file.startWrite()
    try {
        stream.write(text.toByteArray(Charsets.UTF_8))
        stream.flush()
        stream.fd.sync()
        file.finishWrite(stream)
    } catch (error: Throwable) {
        file.failWrite(stream)
        throw error
    }
}