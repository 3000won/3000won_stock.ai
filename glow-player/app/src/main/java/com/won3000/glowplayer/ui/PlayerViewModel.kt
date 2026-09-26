package com.won3000.glowplayer.ui

import android.app.Application
import android.content.ComponentName
import android.net.Uri
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.won3000.glowplayer.data.MusicRepository
import com.won3000.glowplayer.data.Song
import com.won3000.glowplayer.playback.PlaybackService
import com.won3000.glowplayer.playback.QueueRestorer
import com.won3000.glowplayer.playback.nextRepeatMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
data class PlayerUiState(
    val mediaId: String? = null,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val year: Int? = null,
    val artworkUri: Uri? = null,
    val artworkData: ByteArray? = null,
    /** True while playback is requested (also while buffering) – drives the pause icon. */
    val showPause: Boolean = false,
    val isPlaying: Boolean = false,
    val durationMs: Long = 0L,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val upNext: String? = null,
) {
    val hasMedia: Boolean get() = mediaId != null
}

enum class LibraryState { NeedsPermission, Loading, Empty, Ready }

/** Talks to [PlaybackService] through a MediaController and exposes UI state. */
class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)

    private val _ui = MutableStateFlow(PlayerUiState())
    val ui: StateFlow<PlayerUiState> = _ui.asStateFlow()

    private val _position = MutableStateFlow(0L)
    val position: StateFlow<Long> = _position.asStateFlow()

    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()

    private val _library = MutableStateFlow(LibraryState.Loading)
    val library: StateFlow<LibraryState> = _library.asStateFlow()

    private var hasPermission = false
    private var libraryRequested = false
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var controller: MediaController? = null
    private var positionJob: Job? = null

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = publish(player)
    }

    /** Called from Activity.onStart. */
    fun connect() {
        if (controllerFuture != null) return
        val app = getApplication<Application>()
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        controllerFuture = future
        viewModelScope.launch {
            val connected = runCatching { future.await() }.getOrNull()
            if (connected == null) {
                if (controllerFuture === future) controllerFuture = null
                return@launch
            }
            // disconnect() ran while we were waiting; releaseFuture already released it.
            if (controllerFuture !== future) return@launch

            controller = connected
            connected.addListener(listener)
            publish(connected)
            if (hasPermission && connected.mediaItemCount == 0) restoreQueue(connected)
            startPositionUpdates()
        }
    }

    /** Called from Activity.onStop so the service can stop when nothing is playing. */
    fun disconnect() {
        positionJob?.cancel()
        positionJob = null
        controller?.removeListener(listener)
        controller = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    override fun onCleared() {
        disconnect()
    }

    fun onPermissionResult(granted: Boolean) {
        hasPermission = granted
        if (!granted) {
            libraryRequested = false
            _library.value = LibraryState.NeedsPermission
            return
        }
        if (!libraryRequested) loadLibrary()
        controller?.let { c ->
            if (c.mediaItemCount == 0) viewModelScope.launch { restoreQueue(c) }
        }
    }

    private fun loadLibrary() {
        libraryRequested = true
        _library.value = LibraryState.Loading
        viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repository.loadSongs() }
            _songs.value = list
            _library.value = if (list.isEmpty()) LibraryState.Empty else LibraryState.Ready
            controller?.let(::publish)
        }
    }

    /** Shows the last played song (paused) when the app opens. */
    private suspend fun restoreQueue(c: MediaController) {
        val resume = QueueRestorer.load(getApplication<Application>()) ?: return
        if (controller !== c || c.mediaItemCount != 0) return
        c.setMediaItems(resume.mediaItems, resume.startIndex, resume.startPositionMs)
        c.prepare()
    }

    // ---- Controls ---------------------------------------------------------------------------

    fun playPause() {
        val c = controller ?: return
        if (c.mediaItemCount == 0) {
            if (_songs.value.isNotEmpty()) playSongAt(0)
            return
        }
        if (_ui.value.showPause) {
            c.pause()
        } else {
            when (c.playbackState) {
                Player.STATE_IDLE -> c.prepare()
                Player.STATE_ENDED -> c.seekToDefaultPosition()
            }
            c.play()
        }
    }

    fun skipPrevious() {
        controller?.seekToPrevious()
    }

    fun skipNext() {
        controller?.seekToNext()
    }

    fun seekTo(positionMs: Long) {
        val c = controller ?: return
        c.seekTo(positionMs)
        _position.value = positionMs
    }

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let { it.repeatMode = nextRepeatMode(it.repeatMode) }
    }

    /** Plays the whole library starting at [index]. */
    fun playSongAt(index: Int) {
        val c = controller ?: return
        val list = _songs.value
        if (index !in list.indices) return
        val sameQueue = c.mediaItemCount == list.size &&
            list.indices.all { c.getMediaItemAt(it).mediaId == list[it].id.toString() }
        if (sameQueue) {
            c.seekToDefaultPosition(index)
        } else {
            c.setMediaItems(list.map { it.toMediaItem() }, index, 0L)
        }
        if (c.playbackState == Player.STATE_IDLE) c.prepare()
        c.play()
    }

    // ---- State ------------------------------------------------------------------------------

    private fun publish(player: Player) {
        val item = player.currentMediaItem
        val metadata = player.mediaMetadata
        val state = player.playbackState
        val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 }
            ?: item?.mediaId?.let { id -> _songs.value.firstOrNull { it.id.toString() == id }?.durationMs }
            ?: 0L
        val nextIndex = player.nextMediaItemIndex
        val upNext = if (nextIndex != C.INDEX_UNSET && nextIndex != player.currentMediaItemIndex) {
            player.getMediaItemAt(nextIndex).mediaMetadata.title?.toString()
        } else {
            null
        }

        _ui.value = PlayerUiState(
            mediaId = item?.mediaId,
            title = metadata.title?.toString().orEmpty(),
            artist = metadata.artist?.toString().orEmpty(),
            album = metadata.albumTitle?.toString().orEmpty(),
            year = metadata.releaseYear ?: metadata.recordingYear,
            artworkUri = metadata.artworkUri,
            artworkData = metadata.artworkData,
            showPause = player.playWhenReady &&
                state != Player.STATE_IDLE &&
                state != Player.STATE_ENDED,
            isPlaying = player.isPlaying,
            durationMs = duration,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
            upNext = upNext,
        )
        _position.value = player.currentPosition
    }

    private fun startPositionUpdates() {
        positionJob?.cancel()
        positionJob = viewModelScope.launch {
            while (isActive) {
                controller?.let { _position.value = it.currentPosition }
                delay(if (_ui.value.isPlaying) 250L else 1000L)
            }
        }
    }
}
