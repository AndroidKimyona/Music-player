package android.kimyona.jammer.core.media

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * MediaScanner — BULLETPROOF + SAF-COMPATIBLE.
 *
 * Mudanças:
 * - MediaStore é a fonte PRINCIPAL (funciona com READ_MEDIA_AUDIO, sem MANAGE_EXTERNAL_STORAGE)
 * - Scan manual via File APENAS se tiver permissão total
 * - SAF paths (content://) são resolvidos via ContentResolver
 */

class MediaScanner(private val context: Context) {

    companion object {
        private const val TAG = "JammerScanner"

        private val AUDIO_EXTS = setOf(
            "mp3", "flac", "ogg", "opus", "aac", "m4a", "wav",
            "wma", "mid", "midi"
        )
        private val VIDEO_EXTS = setOf(
            "mp4", "mkv", "webm", "mov", "3gp",
            "avi", "wmv", "flv", "mpeg", "mpg", "bik"
        )
        private val ALL_EXTS = AUDIO_EXTS + VIDEO_EXTS

        private val SYSTEM_FOLDERS = setOf(
            "Android", "DCIM", "Documents", "Download", "Movies",
            "Notifications", "Pictures", "Podcasts", "Ringtones",
            "alarms", "media", "MIUI", "Samsung", "cache", "tmp", "temp",
            "data", "obb", "tencent", "baidu", "alipay"
        )
    }

    data class Track(
        val id: Long,
        val title: String,
        val artist: String,
        val album: String,
        val path: String,
        val duration: Long,
        val extension: String,
        val isNative: Boolean,
        val needsFFmpeg: Boolean,
        val isFromHiddenFolder: Boolean = false,
        val albumArtist: String? = null,
        val genre: String? = null,
        val year: Int? = null,
        val trackNumber: Int? = null
    )

    suspend fun scanAll(onProgress: (Int, Int) -> Unit = { _, _ -> }): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()

        // 1. MediaStore — funciona SEMPRE com READ_MEDIA_AUDIO
        tracks.addAll(scanMediaStore())
        Log.i(TAG, "MediaStore found ${tracks.size} tracks")

        // 2. Scan manual via File — SÓ se tiver permissão total (Android 10- ou MANAGE_EXTERNAL_STORAGE)
        if (canScanFilesystem()) {
            val rustBridge = RustBridge()
            if (rustBridge.isLoaded) {
                tracks.addAll(rustBridge.scanDirectories(arrayOf(getStorageRoot().absolutePath), arrayOf()).map { rustTrack ->
                    Track(
                        id = rustTrack.path.hashCode().toLong(),
                        title = rustTrack.title ?: "Unknown Title",
                        artist = rustTrack.artist ?: "Unknown Artist",
                        album = rustTrack.album ?: "Unknown Album",
                        path = rustTrack.path,
                        duration = rustTrack.durationMs ?: 0L,
                        extension = rustTrack.format?.lowercase() ?: rustTrack.path.substringAfterLast('.', "").lowercase(),
                        isNative = true, // Assuming Rust handles native formats
                        needsFFmpeg = false, // Assuming Rust handles decoding
                        isFromHiddenFolder = false // Rust scanner should handle this
                    )
                })
                Log.i(TAG, "Rust scanner found ${tracks.size} tracks")
            } else {
                tracks.addAll(scanAllFolders(onProgress))
            }
        } else {
            Log.i(TAG, "Skipping filesystem scan — no MANAGE_EXTERNAL_STORAGE permission")
        }

        val unique = tracks.distinctBy { it.path }
        Log.i(TAG, "Total: ${unique.size} unique tracks (${tracks.size - unique.size} duplicates)")
        unique
    }

    /**
     * Scan via SAF (Storage Access Framework).
     * Usa ContentResolver pra listar documentos na árvore URI.
     */
    suspend fun scanSAF(treeUri: Uri, onProgress: (Int, Int) -> Unit = { _, _ -> }): List<Track> = withContext(Dispatchers.IO) {
        val tracks = mutableListOf<Track>()
        val resolver = context.contentResolver

        try {
            val childrenUri = android.provider.DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri,
                android.provider.DocumentsContract.getTreeDocumentId(treeUri)
            )

            val projection = arrayOf(
                android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                android.provider.DocumentsContract.Document.COLUMN_MIME_TYPE,
                android.provider.DocumentsContract.Document.COLUMN_SIZE
            )

            resolver.query(childrenUri, projection, null, null, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DOCUMENT_ID)
                val nameCol = cursor.getColumnIndexOrThrow(android.provider.DocumentsContract.Document.COLUMN_DISPLAY_NAME)

                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol)
                    val name = cursor.getString(nameCol)
                    val ext = name.substringAfterLast('.', "").lowercase()

                    if (ext !in ALL_EXTS) continue

                    val docUri = android.provider.DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    val formatInfo = SupportedFormats.getByExtension(ext)
                    val (title, artist, album) = parseFilename(name.substringBeforeLast('.'))

                    tracks.add(
                        Track(
                            id = docId.hashCode().toLong(),
                            title = title,
                            artist = artist,
                            album = album,
                            path = docUri.toString(),  // content:// URI!
                            duration = 0L,
                            extension = ext,
                            isNative = formatInfo?.isNative ?: false,
                            needsFFmpeg = formatInfo?.needsFFmpeg ?: false,
                            isFromHiddenFolder = false
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "SAF scan failed: ${e.message}", e)
        }

        Log.i(TAG, "SAF scan found ${tracks.size} tracks")
        tracks
    }

    private fun scanMediaStore(): List<Track> {
        val result = mutableListOf<Track>()
        result.addAll(scanMediaType(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, "audio"))
        result.addAll(scanMediaType(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, "video"))
        return result
    }

    private fun scanMediaType(uri: Uri, type: String): List<Track> {
        val result = mutableListOf<Track>()
        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.TITLE,
            MediaStore.MediaColumns.ARTIST,
            MediaStore.MediaColumns.ALBUM,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DURATION,
            MediaStore.MediaColumns.DISPLAY_NAME
        )

        context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.ARTIST)
            val albumCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.ALBUM)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
            val durationCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DURATION)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)

            while (cursor.moveToNext()) {
                val path = cursor.getString(pathCol) ?: continue
                val ext = cursor.getString(nameCol)?.substringAfterLast('.', "")?.lowercase()
                    ?: path.substringAfterLast('.', "").lowercase()
                val formatInfo = SupportedFormats.getByExtension(ext)

                result.add(
                    Track(
                        id = cursor.getLong(idCol),
                        title = cursor.getString(titleCol) ?: "Unknown",
                        artist = cursor.getString(artistCol) ?: "Unknown Artist",
                        album = cursor.getString(albumCol) ?: "Unknown Album",
                        path = path,
                        duration = cursor.getLong(durationCol),
                        extension = ext,
                        isNative = formatInfo?.isNative ?: false,
                        needsFFmpeg = formatInfo?.needsFFmpeg ?: false,
                        isFromHiddenFolder = false
                    )
                )
            }
        }

        return result
    }

    /**
     * Scan manual via File — só funciona com MANAGE_EXTERNAL_STORAGE ou API < 30.
     */
    private fun scanAllFolders(onProgress: (Int, Int) -> Unit): List<Track> {
        val result = mutableListOf<Track>()
        val root = getStorageRoot()

        if (!root.exists() || !root.canRead()) {
            Log.w(TAG, "Cannot read storage root: ${root.absolutePath}")
            return result
        }

        Log.i(TAG, "Scanning filesystem: ${root.absolutePath}")
        val allDirs = findAllDirectories(root)
        Log.i(TAG, "Found ${allDirs.size} directories")

        var nextId = -1L
        var trackCount = 0
        val totalDirs = allDirs.size

        for ((index, dir) in allDirs.withIndex()) {
            val files = dir.listFiles() ?: continue
            for (file in files) {
                if (!file.isFile) continue
                val ext = file.extension.lowercase()
                if (ext !in ALL_EXTS) continue

                val formatInfo = SupportedFormats.getByExtension(ext)
                val (title, artist, album) = parseFilename(file.nameWithoutExtension)

                result.add(
                    Track(
                        id = nextId--,
                        title = title,
                        artist = artist,
                        album = album,
                        path = file.absolutePath,
                        duration = 0L,
                        extension = ext,
                        isNative = formatInfo?.isNative ?: false,
                        needsFFmpeg = formatInfo?.needsFFmpeg ?: false,
                        isFromHiddenFolder = dir.name.startsWith(".")
                    )
                )
                trackCount++
            }

            if (index % 5 == 0 || index == totalDirs - 1) {
                onProgress(index + 1, totalDirs)
            }
        }

        Log.i(TAG, "Filesystem scan: $trackCount tracks")
        return result
    }

    private fun canScanFilesystem(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            android.os.Environment.isExternalStorageManager()
        } else {
            true  // API < 30 não precisa de MANAGE_EXTERNAL_STORAGE
        }
    }

    private fun getStorageRoot(): File {
        val standardPath = File("/storage/emulated/0")
        if (standardPath.exists() && standardPath.isDirectory && standardPath.canRead()) {
            return standardPath
        }
        val fallback = context.getExternalFilesDir(null)?.parentFile?.parentFile?.parentFile
        if (fallback != null && fallback.exists()) {
            return fallback
        }
        return File("/sdcard")
    }

    private fun findAllDirectories(root: File): List<File> {
        val all = mutableListOf<File>()
        val stack = ArrayDeque<File>()
        stack.add(root)

        while (stack.isNotEmpty()) {
            val current = stack.removeLast()
            val children = current.listFiles() ?: continue

            for (child in children) {
                if (!child.isDirectory) continue
                val name = child.name
                if (name in SYSTEM_FOLDERS) continue
                if (name == "cache" || name == "files" || name == "databases") continue
                all.add(child)
                stack.add(child)
            }
        }
        return all
    }

    private fun parseFilename(filename: String): Triple<String, String, String> {
        var clean = filename.replace(Regex("""^\d+[.\s-]+"""), "")
        val parts = clean.split(" - ", limit = 3)

        return when (parts.size) {
            3 -> Triple(parts[2], parts[0], parts[1])
            2 -> Triple(parts[1], parts[0], "Unknown Album")
            else -> Triple(clean, "Unknown Artist", "Unknown Album")
        }
    }
}
