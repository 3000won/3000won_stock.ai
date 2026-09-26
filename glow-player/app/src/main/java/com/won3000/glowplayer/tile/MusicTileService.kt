package com.won3000.glowplayer.tile

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.won3000.glowplayer.MainActivity
import com.won3000.glowplayer.R
import com.won3000.glowplayer.data.PlaybackStore
import com.won3000.glowplayer.playback.PlaybackService
import com.won3000.glowplayer.playback.QueueRestorer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/**
 * Quick Settings tile ("상단바" tile).
 *
 * Tap: play / pause (resumes the last queue when nothing is loaded).
 * Long-press: opens the player (MainActivity handles QS_TILE_PREFERENCES).
 */
class MusicTileService : TileService() {

    private val scope = MainScope()
    private var controllerFuture: ListenableFuture<MediaController>? = null

    private val controller: MediaController?
        get() = controllerFuture
            ?.takeIf { it.isDone && !it.isCancelled }
            ?.let { runCatching { it.get() }.getOrNull() }

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) = render()
    }

    override fun onStartListening() {
        super.onStartListening()
        render()
        // Only bind to the player when it already exists; otherwise show the saved song.
        if (PlaybackService.isRunning) scope.launch { connect() }
    }

    override fun onStopListening() {
        disconnect()
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val c = connect() ?: return@launch
            when {
                c.mediaItemCount == 0 -> {
                    val resume = QueueRestorer.load(applicationContext)
                    if (resume == null) {
                        openPlayer()
                        return@launch
                    }
                    c.setMediaItems(resume.mediaItems, resume.startIndex, resume.startPositionMs)
                    c.prepare()
                    c.play()
                }
                c.playWhenReady && c.playbackState != Player.STATE_ENDED -> c.pause()
                else -> {
                    if (c.playbackState == Player.STATE_IDLE) c.prepare()
                    if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
                    c.play()
                }
            }
            render()
        }
    }

    override fun onDestroy() {
        disconnect()
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun connect(): MediaController? {
        val future = controllerFuture ?: MediaController.Builder(
            this,
            SessionToken(this, ComponentName(this, PlaybackService::class.java)),
        ).buildAsync().also { controllerFuture = it }
        return try {
            future.await().also { c ->
                c.removeListener(listener)
                c.addListener(listener)
                render()
            }
        } catch (e: CancellationException) {
            null
        } catch (e: Exception) {
            if (controllerFuture === future) controllerFuture = null
            null
        }
    }

    private fun disconnect() {
        controller?.removeListener(listener)
        controllerFuture?.let { MediaController.releaseFuture(it) }
        controllerFuture = null
    }

    private fun render() {
        val tile = qsTile ?: return
        val store = PlaybackStore.get(this)
        val c = controller
        val playing = c?.isPlaying == true
        val title = c?.mediaMetadata?.title?.toString() ?: store.lastTitle
        val artist = c?.mediaMetadata?.artist?.toString() ?: store.lastArtist

        tile.state = if (playing) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = title?.takeIf { it.isNotBlank() } ?: getString(R.string.tile_label)
        tile.icon = Icon.createWithResource(
            this,
            when {
                title == null -> R.drawable.ic_music_note
                playing -> R.drawable.ic_pause
                else -> R.drawable.ic_play
            },
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            tile.subtitle = when {
                title == null -> getString(R.string.tile_subtitle_idle)
                !artist.isNullOrBlank() -> artist
                playing -> getString(R.string.state_playing)
                else -> getString(R.string.state_paused)
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            tile.stateDescription = getString(if (playing) R.string.state_playing else R.string.state_paused)
        }
        tile.updateTile()
    }

    @SuppressLint("StartActivityAndCollapseDeprecated")
    private fun openPlayer() {
        val intent = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
