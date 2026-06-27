package android.kimyona.jammer.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.lifecycle.LiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import android.kimyona.jammer.data.JammerDatabase
import android.kimyona.jammer.data.entity.ContentRating
import android.kimyona.jammer.data.entity.ReleaseType
import android.kimyona.jammer.data.entity.Track
import android.kimyona.jammer.core.media.MediaScanner

class MediaRepository(private val context: Context) {

    private val db = JammerDatabase.getDatabase(context)
    private val scanner = MediaScanner(context)

    val allTracks: LiveData<List<Track>> = db.trackDao().getAll()
    val favorites: LiveData<List<Track>> = db.trackDao().getFavorites()

    // v─── Scan ───v
    // FIX: Removido clearAllTracks(). Agora faz upsert — mantém existentes,
    // atualiza só o que foi escaneado. Não perde favoritos nem metadados.

    suspend fun scanLibrary(): Flow<ScanProgress> = flow {
        emit(ScanProgress.Running(0, 1))
        try {
            val scanned = scanner.scanAll()

            val entities = scanned.mapIndexed { index, scannedTrack ->
                emit(ScanProgress.Running(index + 1, scanned.size))
                Track(
                    path = scannedTrack.path,
                    title = scannedTrack.title,
                    artist = scannedTrack.artist,
                    album = scannedTrack.album,
                    durationMs = scannedTrack.duration,
                    format = scannedTrack.extension.uppercase(),
                    dateAdded = System.currentTimeMillis(),
                    albumArtist = scannedTrack.albumArtist,
                    genre = scannedTrack.genre,
                    year = scannedTrack.year,
                    trackNumber = scannedTrack.trackNumber
                )
            }

            withContext(Dispatchers.IO) {
                db.trackDao().insertAll(entities)
            }

            emit(ScanProgress.Done(entities.size))
        } catch (e: Exception) {
            Log.e("MediaRepository", "Scan failed: ${e.message}", e)
            emit(ScanProgress.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    suspend fun scanSAF(uri: Uri): Flow<ScanProgress> = flow {
        emit(ScanProgress.Running(0, 1))
        try {
            val scanned = scanner.scanSAF(uri)

            val entities = scanned.mapIndexed { index, scannedTrack ->
                emit(ScanProgress.Running(index + 1, scanned.size))
                Track(
                    path = scannedTrack.path,
                    title = scannedTrack.title,
                    artist = scannedTrack.artist,
                    album = scannedTrack.album,
                    durationMs = scannedTrack.duration,
                    format = scannedTrack.extension.uppercase(),
                    dateAdded = System.currentTimeMillis(),
                    albumArtist = scannedTrack.albumArtist,
                    genre = scannedTrack.genre,
                    year = scannedTrack.year,
                    trackNumber = scannedTrack.trackNumber
                )
            }

            withContext(Dispatchers.IO) {
                db.trackDao().insertAll(entities)
            }

            emit(ScanProgress.Done(entities.size))
        } catch (e: Exception) {
            Log.e("MediaRepository", "SAF scan failed: ${e.message}", e)
            emit(ScanProgress.Error(e.message ?: "Unknown error"))
        }
    }.flowOn(Dispatchers.IO)

    // v─── Queries ───v

    fun searchTracks(query: String): LiveData<List<Track>> =
        db.trackDao().search("%$query%")

    fun getByContentRating(rating: ContentRating): LiveData<List<Track>> =
        db.trackDao().getByContentRating(rating.name)

    fun getByReleaseType(type: ReleaseType): LiveData<List<Track>> =
        db.trackDao().getByReleaseType(type.name)

    fun searchByContentRating(query: String, rating: ContentRating): LiveData<List<Track>> =
        db.trackDao().searchByContentRating("%$query%", rating.name)

    fun searchByReleaseType(query: String, type: ReleaseType): LiveData<List<Track>> =
        db.trackDao().searchByReleaseType("%$query%", type.name)

    // v─── Metadata writers ───v

    suspend fun setAlias(path: String, alias: String?) =
        db.trackDao().setAlias(path, alias)

    suspend fun setContentRating(path: String, rating: ContentRating) =
        db.trackDao().setContentRating(path, rating.name)

    suspend fun setReleaseType(path: String, type: ReleaseType?) =
        db.trackDao().setReleaseType(path, type?.name)

    suspend fun setArtistsJoined(path: String, artistsJoined: String?) =
        db.trackDao().setArtistsJoined(path, artistsJoined)

    suspend fun toggleFavorite(path: String, current: Boolean) =
        db.trackDao().setFavorite(path, !current)

    suspend fun getTrackByPath(path: String): Track? =
        db.trackDao().getByPath(path)

    // ─── Sealed class para progresso de scan ───

    sealed class ScanProgress {
        data class Running(val current: Int, val total: Int) : ScanProgress()
        data class Done(val count: Int) : ScanProgress()
        data class Error(val message: String) : ScanProgress()
    }
}