package android.kimyona.jammer.core.media

import android.kimyona.jammer.core.media.MediaScanner.Track
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Bridge JNI para o scanner Rust.
 * DEFENSIVO: não crasha se libjammer_scanner.so não estiver no APK.
 */
class RustBridge {

    private val isLoaded: Boolean

    init {
        isLoaded = try {
            System.loadLibrary("jammer_scanner")
            Log.i("JammerRust", "libjammer_scanner.so loaded successfully")
            true
        } catch (e: UnsatisfiedLinkError) {
            Log.w("JammerRust", "libjammer_scanner.so not found. Rust scanner disabled. Build with: cargo ndk -t arm64-v8a -t armeabi-v7a -o ../app/src/main/jniLibs build --release")
            false
        } catch (e: Exception) {
            Log.w("JammerRust", "Failed to load Rust library: ${e.message}")
            false
        }
    }

    /**
     * Escaneia diretórios extras, ignorando paths já conhecidos.
     * Se a lib Rust não estiver disponível, retorna lista vazia.
     */
    suspend fun scanDirectories(dirs: Array<String>, exclude: Array<String>): List<Track> {
        if (!isLoaded) {
            Log.d("JammerRust", "Skipping Rust scan — library not loaded")
            return emptyList()
        }

        return withContext(Dispatchers.IO) {
            val results = mutableListOf<Track>()
            dirs.forEach { dir ->
                Log.d("JammerScanner", "Scanning directory: $dir")
                try {
                    val json = nativeScanDirectory(dir)
                    Log.d("JammerScanner", "Raw JSON length: ${json.length}")
                    val tracks = parseJsonTracks(json, exclude.toSet())
                    Log.d("JammerScanner", "Parsed ${tracks.size} tracks from $dir")
                    results.addAll(tracks)
                } catch (e: Exception) {
                    Log.e("JammerRust", "Rust scan failed for $dir: ${e.message}")
                }
            }
            results
        }
    }

    private fun parseJsonTracks(json: String, exclude: Set<String>): List<Track> {
        val tracks = mutableListOf<Track>()
        try {
            val array = JSONArray(json)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val path = obj.getString("path")
                if (path in exclude) continue
                tracks.add(Track(
                    id = path.hashCode().toLong(),
                    title = obj.optString("title", "Unknown Title"),
                    artist = obj.optString("artist", "Unknown Artist"),
                    album = obj.optString("album", "Unknown Album"),
                    path = path,
                    duration = obj.optLong("duration_ms", 0),
                    extension = obj.optString("format", "UNKNOWN").lowercase(),
                    isNative = true,
                    needsFFmpeg = false,
                    isFromHiddenFolder = false,
                    albumArtist = obj.optString("album_artist", null),
                    genre = obj.optString("genre", null),
                    year = obj.optInt("year", 0).takeIf { it > 0 },
                    trackNumber = obj.optInt("track_number", 0).takeIf { it > 0 }
                ))
            }
        } catch (e: Exception) {
            Log.e("RustBridge", "Parse error: ${e.message}")
        }
        return tracks
    }

    // JNI native — só chamado se isLoaded == true
    private external fun nativeScanDirectory(dir: String): String
}
