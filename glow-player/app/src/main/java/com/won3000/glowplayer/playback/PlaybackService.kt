package com.won3000.glowplayer.playback

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.service.quicksettings.TileService
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.won3000.glowplayer.MainActivity
import com.won3000.glowplayer.R
import com.won3000.glowplayer.data.MusicRepository
import com.won3000.glowplayer.data.PlaybackStore
import com.won3000.glowplayer.tile.MusicTileService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.guava.future

/**
 * Owns the ExoPlayer and the MediaSession.
 *
 * Because the session is published through a MediaSessionService with a media-style
 * notification, One UI shows it in the quick panel media player (the card in the second
 * screenshot), on the lock screen and in the Now bar. The system draws that card itself from
 * the session's metadata, artwork and buttons.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private lateinit var store: PlaybackStore
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val playerListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) {
                mediaSession?.player?.let { player -> store.saveQueue(player.queueMediaIds()) }
            }
        }

        override fun onEvents(player: Player, events: Player.Events) {
            if (events.containsAny(
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                    Player.EVENT_REPEAT_MODE_CHANGED,
                )
            ) {
                store.saveModes(player.shuffleModeEnabled, player.repeatMode)
                mediaSession?.setCustomLayout(customLayout(player))
            }
            if (events.containsAny(
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_MEDIA_METADATA_CHANGED,
                )
            ) {
                store.saveNowPlaying(player)
                requestTileUpdate()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true
        store = PlaybackStore.get(this)

        val player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()
        player.shuffleModeEnabled = store.shuffleEnabled
        player.repeatMode = store.repeatMode
        player.addListener(playerListener)

        val session = MediaSession.Builder(this, player)
            .setSessionActivity(openAppIntent())
            .setCallback(SessionCallback())
            .build()
        session.setCustomLayout(customLayout(player))
        mediaSession = session

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.notification_channel_name)
                .build()
                .apply { setSmallIcon(R.drawable.ic_music_note) },
        )
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaSession?.player
        if (player == null ||
            !player.playWhenReady ||
            player.mediaItemCount == 0 ||
            player.playbackState == Player.STATE_ENDED
        ) {
            // Nothing is playing: don't keep the service alive after the app is swiped away.
            stopSelf()
        }
    }

    override fun onDestroy() {
        isRunning = false
        mediaSession?.run {
            store.saveNowPlaying(player)
            player.removeListener(playerListener)
            player.release()
            release()
        }
        mediaSession = null
        serviceScope.cancel()
        requestTileUpdate()
        super.onDestroy()
    }

    /** Repeat on the left, shuffle on the right – the slots Samsung uses for "like" and shuffle. */
    private fun customLayout(player: Player): ImmutableList<CommandButton> {
        val (repeatIcon, repeatLabel) = when (player.repeatMode) {
            Player.REPEAT_MODE_ONE -> R.drawable.ic_repeat_one to R.string.action_repeat_one
            Player.REPEAT_MODE_ALL -> R.drawable.ic_repeat to R.string.action_repeat_all
            else -> R.drawable.ic_repeat_off to R.string.action_repeat_off
        }
        val repeat = CommandButton.Builder()
            .setDisplayName(getString(repeatLabel))
            .setIconResId(repeatIcon)
            .setSessionCommand(PlayerCommands.cycleRepeat)
            .build()

        val shuffleOn = player.shuffleModeEnabled
        val shuffle = CommandButton.Builder()
            .setDisplayName(getString(if (shuffleOn) R.string.action_shuffle_off else R.string.action_shuffle_on))
            .setIconResId(if (shuffleOn) R.drawable.ic_shuffle else R.drawable.ic_shuffle_off)
            .setSessionCommand(PlayerCommands.toggleShuffle)
            .build()

        return ImmutableList.of(repeat, shuffle)
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun requestTileUpdate() {
        runCatching {
            TileService.requestListeningState(this, ComponentName(this, MusicTileService::class.java))
        }
    }

    private inner class SessionCallback : MediaSession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(PlayerCommands.toggleShuffle)
                .add(PlayerCommands.cycleRepeat)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            val player = session.player
            when (customCommand.customAction) {
                PlayerCommands.ACTION_TOGGLE_SHUFFLE ->
                    player.shuffleModeEnabled = !player.shuffleModeEnabled
                PlayerCommands.ACTION_CYCLE_REPEAT ->
                    player.repeatMode = nextRepeatMode(player.repeatMode)
                else -> return super.onCustomCommand(session, controller, customCommand, args)
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        /** Controllers may drop the file URI; rebuild it from the MediaStore id. */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> {
            val resolved = mediaItems.map { item ->
                val id = item.mediaId.toLongOrNull()
                if (item.localConfiguration != null || id == null) {
                    item
                } else {
                    item.buildUpon().setUri(MusicRepository.contentUriFor(id)).build()
                }
            }
            return Futures.immediateFuture(resolved.toMutableList())
        }

        /** Called by System UI ("resume" in the quick panel) and by headset play buttons. */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> =
            serviceScope.future {
                QueueRestorer.load(this@PlaybackService)
                    ?: throw UnsupportedOperationException("Nothing to resume")
            }
    }

    companion object {
        /** True while the service (and therefore the player) exists in this process. */
        @Volatile
        var isRunning: Boolean = false
            private set
    }
}

private fun Player.queueMediaIds(): List<String> = List(mediaItemCount) { getMediaItemAt(it).mediaId }
