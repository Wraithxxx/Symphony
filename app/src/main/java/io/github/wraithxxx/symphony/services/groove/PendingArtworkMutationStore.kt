package io.github.wraithxxx.symphony.services.groove

import android.content.Context
import android.net.Uri
import android.util.AtomicFile
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class PendingArtworkMutationStore(context: Context) {
    enum class Kind {
        Replace,
        Remove,
    }

    data class Record(
        val operationId: String,
        val songId: String,
        val uri: Uri,
        val coverFile: String?,
        val kind: Kind,
        val mimeType: String?,
        val payloadName: String?,
    )

    private val root = File(context.filesDir, DIRECTORY_NAME)

    suspend fun load(): List<Record> = withContext(Dispatchers.IO) {
        if (!root.exists()) return@withContext emptyList()
        val records = root.listFiles { file -> file.extension == MANIFEST_EXTENSION }
            .orEmpty()
            .mapNotNull(::readManifest)
        val referencedPayloads = records.mapNotNullTo(hashSetOf(), Record::payloadName)
        root.listFiles { file -> file.extension == PAYLOAD_EXTENSION }
            .orEmpty()
            .filterNot { it.name in referencedPayloads }
            .forEach(File::delete)
        records
    }

    suspend fun put(
        song: Song,
        coverFile: String?,
        artwork: MediaMetadataEditingService.ArtworkChange,
    ): Record = withContext(Dispatchers.IO) {
        check(artwork !is MediaMetadataEditingService.ArtworkChange.Keep)
        root.mkdirs()
        val operationId = UUID.randomUUID().toString()
        val key = stableKey(song.id)
        val payloadName = when (artwork) {
            is MediaMetadataEditingService.ArtworkChange.Replace ->
                "$key-$operationId.$PAYLOAD_EXTENSION".also { name ->
                    writeAtomically(File(root, name), artwork.bytes)
                }
            else -> null
        }
        val record = Record(
            operationId = operationId,
            songId = song.id,
            uri = song.uri,
            coverFile = coverFile,
            kind = when (artwork) {
                is MediaMetadataEditingService.ArtworkChange.Replace -> Kind.Replace
                MediaMetadataEditingService.ArtworkChange.Remove -> Kind.Remove
                MediaMetadataEditingService.ArtworkChange.Keep -> error("unreachable")
            },
            mimeType = (artwork as? MediaMetadataEditingService.ArtworkChange.Replace)?.mimeType,
            payloadName = payloadName,
        )
        val manifest = JSONObject().apply {
            put("operationId", record.operationId)
            put("songId", record.songId)
            put("uri", record.uri.toString())
            put("coverFile", record.coverFile ?: JSONObject.NULL)
            put("kind", record.kind.name)
            put("mimeType", record.mimeType ?: JSONObject.NULL)
            put("payloadName", record.payloadName ?: JSONObject.NULL)
        }.toString().toByteArray(Charsets.UTF_8)
        writeAtomically(manifestFile(key), manifest)
        root.listFiles { file ->
            file.extension == PAYLOAD_EXTENSION &&
                file.name.startsWith("$key-") &&
                file.name != payloadName
        }.orEmpty().forEach(File::delete)
        record
    }

    suspend fun readPayload(record: Record): ByteArray? = withContext(Dispatchers.IO) {
        record.payloadName?.let { File(root, it).takeIf(File::isFile)?.readBytes() }
    }

    suspend fun remove(record: Record) = withContext(Dispatchers.IO) {
        val manifest = manifestFile(stableKey(record.songId))
        val current = readManifest(manifest)
        if (current?.operationId != record.operationId) return@withContext
        manifest.delete()
        record.payloadName?.let { File(root, it).delete() }
    }

    private fun readManifest(file: File): Record? = runCatching {
        val json = JSONObject(file.readText())
        val payloadName = json.nullableString("payloadName")
        val kind = Kind.valueOf(json.getString("kind"))
        if (kind == Kind.Replace && payloadName?.let { File(root, it).isFile } != true) {
            file.delete()
            return null
        }
        Record(
            operationId = json.getString("operationId"),
            songId = json.getString("songId"),
            uri = Uri.parse(json.getString("uri")),
            coverFile = json.nullableString("coverFile"),
            kind = kind,
            mimeType = json.nullableString("mimeType"),
            payloadName = payloadName,
        )
    }.getOrElse {
        file.delete()
        null
    }

    private fun manifestFile(key: String) = File(root, "$key.$MANIFEST_EXTENSION")

    private fun JSONObject.nullableString(key: String): String? =
        if (isNull(key)) null else getString(key).takeIf(String::isNotBlank)

    private fun writeAtomically(file: File, bytes: ByteArray) {
        val atomic = AtomicFile(file)
        val output = atomic.startWrite()
        try {
            output.write(bytes)
            atomic.finishWrite(output)
        } catch (error: Exception) {
            atomic.failWrite(output)
            throw error
        }
    }

    companion object {
        private const val DIRECTORY_NAME = "pending-artwork-mutations"
        private const val MANIFEST_EXTENSION = "json"
        private const val PAYLOAD_EXTENSION = "artwork"

        private fun stableKey(songId: String): String = MessageDigest
            .getInstance("SHA-256")
            .digest(songId.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }
}
