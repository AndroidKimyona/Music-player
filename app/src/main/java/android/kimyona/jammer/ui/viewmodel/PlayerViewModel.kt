package android.kimyona.jammer.ui.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import android.util.Log
import androidx.lifecycle.map
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import android.kimyona.jammer.data.entity.ContentRating
import android.kimyona.jammer.data.entity.ReleaseType
import android.kimyona.jammer.data.entity.Track
import android.kimyona.jammer.data.repository.MediaRepository
import android.kimyona.jammer.service.JammerPlaybackService

/**
 * PlayerViewModel — SIMPLIFIED EDITION.
 *
 * Conecta com JammerPlaybackService via bind direto (LocalBinder).
 * Remove MediaBrowserCompat / MediaControllerCompat quebrados.
 */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)

    // ─── Service bind ───────────────────────────────────────────────────────
    private var service: JammerPlaybackService? = null
    private var serviceBound = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
            val localBinder = binder as JammerPlaybackService.LocalBinder
            service = localBinder.getService()
            serviceBound = true
            Log.d("PlayerVM", "Service bound successfully")
            updatePlaybackState()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            service = null
            serviceBound = false
            Log.w("PlayerVM", "Service disconnected")
        }
    }

    // ─── LiveData ───────────────────────────────────────────────────────────
    val allTracks: LiveData<List<Track>> = repository.allTracks

    private val _currentTrack = MutableLiveData<Track?>(null)
    val currentTrack: LiveData<Track?> = _currentTrack

    private val _isPlaying = MutableLiveData(false)
    val isPlaying: LiveData<Boolean> = _isPlaying

    private val _currentPosition = MutableLiveData(0L)
    val currentPosition: LiveData<Long> = _currentPosition

    private val _shuffleEnabled = MutableLiveData(false)
    val shuffleEnabled: LiveData<Boolean> = _shuffleEnabled

    private val _repeatMode = MutableLiveData(RepeatMode.NONE)
    val repeatMode: LiveData<RepeatMode> = _repeatMode

    private val _queueTracks = MutableLiveData<List<Track>>(emptyList())
    val queueTracks: LiveData<List<Track>> = _queueTracks

    val queueSize: LiveData<Int> = _queueTracks.map { it.size }

    private val _showMiniPlayer = MutableLiveData(false)
    val showMiniPlayer: LiveData<Boolean> = _showMiniPlayer

    private val _scanProgress = MutableLiveData<String?>(null)
    val scanProgress: LiveData<String?> = _scanProgress

    // ─── Scan state ─────────────────────────────────────────────────────────
    private var isScanning = false

    // ─── Position updater ───────────────────────────────────────────────────

    init {
        bindService()
        startPositionUpdates()
    }

    // ─── Service bind ─────────────────────────────────────────────────────────

    private fun bindService() {
        try {
            val intent = Intent(getApplication(), JammerPlaybackService::class.java)
            getApplication<Application>().bindService(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE
            )
        } catch (e: Exception) {
            Log.e("PlayerVM", "bindService failed", e)
        }
    }

    private fun updatePlaybackState() {
        try {
            _isPlaying.value = service?.isPlaying() ?: false
        } catch (e: Exception) {
            Log.e("PlayerVM", "updatePlaybackState error", e)
        }
    }

    // ─── Playback controls ──────────────────────────────────────────────────

    fun playTrack(track: Track) {
        try {
            // Envia Intent pro service (funciona mesmo sem bind)
            val intent = Intent(getApplication(), JammerPlaybackService::class.java).apply {
                action = JammerPlaybackService.ACTION_PLAY_SINGLE
                putExtra(JammerPlaybackService.EXTRA_PATH, track.path)
            }
            getApplication<Application>().startService(intent)

            _currentTrack.value = track
            _showMiniPlayer.value = true
        } catch (e: Exception) {
            Log.e("PlayerVM", "playTrack error", e)
        }
    }

    fun togglePlayPause() {
        try {
            service?.togglePlayPause()
            _isPlaying.value = service?.isPlaying() ?: false
        } catch (e: Exception) {
            Log.e("PlayerVM", "togglePlayPause error", e)
        }
    }

    fun skipNext() {
        try {
            service?.skipToNext()
        } catch (e: Exception) {
            Log.e("PlayerVM", "skipNext error", e)
        }
    }

    fun skipPrevious() {
        try {
            service?.skipToPrevious()
        } catch (e: Exception) {
            Log.e("PlayerVM", "skipPrevious error", e)
        }
    }

    fun seekTo(positionMs: Long) {
        try {
            service?.seekTo(positionMs)
        } catch (e: Exception) {
            Log.e("PlayerVM", "seekTo error", e)
        }
    }

    fun toggleShuffle() {
        try {
            val newState = !(_shuffleEnabled.value ?: false)
            _shuffleEnabled.value = newState
            service?.setShuffle(newState)
        } catch (e: Exception) {
            Log.e("PlayerVM", "toggleShuffle error", e)
        }
    }

    fun toggleRepeat() {
        try {
            val current = _repeatMode.value ?: RepeatMode.NONE
            val next = when (current) {
                RepeatMode.NONE -> RepeatMode.ALL
                RepeatMode.ALL -> RepeatMode.ONE
                RepeatMode.ONE -> RepeatMode.NONE
            }
            _repeatMode.value = next
            service?.setRepeat(
                when (next) {
                    RepeatMode.NONE -> JammerPlaybackService.RepeatMode.NONE
                    RepeatMode.ALL -> JammerPlaybackService.RepeatMode.ALL
                    RepeatMode.ONE -> JammerPlaybackService.RepeatMode.ONE
                }
            )
        } catch (e: Exception) {
            Log.e("PlayerVM", "toggleRepeat error", e)
        }
    }

    // ─── Queue ──────────────────────────────────────────────────────────────

    fun addToQueue(track: Track) {
        try {
            val current = _queueTracks.value ?: emptyList()
            _queueTracks.value = current + track
            service?.addToQueue(track.path)
        } catch (e: Exception) {
            Log.e("PlayerVM", "addToQueue error", e)
        }
    }

    fun removeFromQueue(index: Int) {
        try {
            val current = _queueTracks.value ?: emptyList()
            if (index in current.indices) {
                _queueTracks.value = current.toMutableList().apply { removeAt(index) }
            }
        } catch (e: Exception) {
            Log.e("PlayerVM", "removeFromQueue error", e)
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        try {
            val current = _queueTracks.value?.toMutableList() ?: return
            if (fromIndex in current.indices && toIndex in current.indices) {
                val item = current.removeAt(fromIndex)
                current.add(toIndex, item)
                _queueTracks.value = current
            }
        } catch (e: Exception) {
            Log.e("PlayerVM", "moveQueueItem error", e)
        }
    }

    fun clearQueue() {
        _queueTracks.value = emptyList()
        try {
            service?.clearQueue()
        } catch (e: Exception) {
            Log.e("PlayerVM", "clearQueue error", e)
        }
    }

    // ─── Favorites ──────────────────────────────────────────────────────────

    fun toggleFavorite(track: Track) {
        viewModelScope.launch {
            try {
                repository.toggleFavorite(track.path, track.isFavorite)
            } catch (e: Exception) {
                Log.e("PlayerVM", "toggleFavorite error", e)
            }
        }
    }

    // ─── Scan ───────────────────────────────────────────────────────────────

    fun scanLibrary() {
        if (isScanning) return
        isScanning = true
        _scanProgress.value = "🔍 Scanning…"
        viewModelScope.launch {
            try {
                repository.scanLibrary().collect { progress ->
                    when (progress) {
                        is MediaRepository.ScanProgress.Running -> {
                            _scanProgress.value = "Scanning ${progress.current}/${progress.total}…"
                        }
                        is MediaRepository.ScanProgress.Done -> {
                            _scanProgress.value = "✅ ${progress.count} tracks found"
                            isScanning = false
                        }
                        is MediaRepository.ScanProgress.Error -> {
                            _scanProgress.value = "❌ ${progress.message}"
                            isScanning = false
                        }
                    }
                }
            } catch (e: Exception) {
                _scanProgress.value = "❌ Scan failed: ${e.message}"
                isScanning = false
            }
        }
    }

    fun scanSAF(uri: Uri) {
        if (isScanning) return
        isScanning = true
        _scanProgress.value = "🔍 Scanning folder…"
        viewModelScope.launch {
            try {
                repository.scanSAF(uri).collect { progress ->
                    when (progress) {
                        is MediaRepository.ScanProgress.Running -> {
                            _scanProgress.value = "Scanning ${progress.current}/${progress.total}…"
                        }
                        is MediaRepository.ScanProgress.Done -> {
                            _scanProgress.value = "✅ ${progress.count} tracks found"
                            isScanning = false
                        }
                        is MediaRepository.ScanProgress.Error -> {
                            _scanProgress.value = "❌ ${progress.message}"
                            isScanning = false
                        }
                    }
                }
            } catch (e: Exception) {
                _scanProgress.value = "❌ SAF scan failed: ${e.message}"
                isScanning = false
            }
        }
    }

    // ─── Search & Filter ────────────────────────────────────────────────────

    fun searchTracks(query: String): LiveData<List<Track>> =
        repository.searchTracks(query)

    fun searchWithFilter(
        query: String,
        contentRating: ContentRating? = null,
        releaseType: ReleaseType? = null
    ): LiveData<List<Track>> = when {
        contentRating != null -> repository.searchByContentRating(query, contentRating)
        releaseType != null -> repository.searchByReleaseType(query, releaseType)
        else -> repository.searchTracks(query)
    }

    fun filterByContentRating(rating: ContentRating): LiveData<List<Track>> =
        repository.getByContentRating(rating)

    fun filterByReleaseType(type: ReleaseType): LiveData<List<Track>> =
        repository.getByReleaseType(type)

    // ─── Metadata writers ───────────────────────────────────────────────────

    fun setAlias(path: String, alias: String?) {
        viewModelScope.launch {
            try { repository.setAlias(path, alias) }
            catch (e: Exception) { Log.e("PlayerVM", "setAlias error", e) }
        }
    }

    fun setContentRating(path: String, rating: ContentRating) {
        viewModelScope.launch {
            try { repository.setContentRating(path, rating) }
            catch (e: Exception) { Log.e("PlayerVM", "setContentRating error", e) }
        }
    }

    fun setReleaseType(path: String, type: ReleaseType?) {
        viewModelScope.launch {
            try { repository.setReleaseType(path, type) }
            catch (e: Exception) { Log.e("PlayerVM", "setReleaseType error", e) }
        }
    }

    fun setArtistsJoined(path: String, artistsJoined: String?) {
        viewModelScope.launch {
            try { repository.setArtistsJoined(path, artistsJoined) }
            catch (e: Exception) { Log.e("PlayerVM", "setArtistsJoined error", e) }
        }
    }

    // ─── Helpers ────────────────────────────────────────────────────────────

    fun getDuration(): Long {
        return try {
            service?.getDuration() ?: 0L
        } catch (e: Exception) { 0L }
    }

    private var positionJob: kotlinx.coroutines.Job? = null

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                try {
                    val pos = service?.getCurrentPosition() ?: 0L
                    _currentPosition.postValue(pos)
                    _isPlaying.postValue(service?.isPlaying() ?: false)
                } catch (e: Exception) {
                    Log.e("PlayerVM", "Position update error", e)
                }
                delay(1000)
            }
        }
    }

    override fun onCleared() {
        positionJob?.cancel()
        try {
            if (serviceBound) {
                getApplication<Application>().unbindService(serviceConnection)
                serviceBound = false
            }
        } catch (_: Exception) {}
        service = null
        super.onCleared()
    }

    // ─── Playlist playback ──────────────────────────────────────────────────

    fun playPlaylist(tracks: List<Track>, startIndex: Int = 0) {
        viewModelScope.launch {
            try {
                _queueTracks.value = tracks
                if (tracks.isNotEmpty() && startIndex in tracks.indices) {
                    playTrack(tracks[startIndex])
                }
            } catch (e: Exception) {
                Log.e("PlayerVM", "playPlaylist error", e)
            }
        }
    }

    enum class RepeatMode { NONE, ALL, ONE }
}