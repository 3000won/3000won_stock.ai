package com.won3000.glowplayer.playback

import android.content.Context
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import com.won3000.glowplayer.data.MusicRepository
import com.won3000.glowplayer.data.PlaybackStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Custom buttons shown next to prev / play / next in the quick panel media player. */
object PlayerCommands {
    const val ACTION_TOGGLE_SHUFFLE = "com.won3000.glowplayer.TOGGLE_SHUFFLE"
    const val ACTION_CYCLE_REPEAT = "com.won3000.glowplayer.CYCLE_REPEAT"

    val toggleShuffle = SessionCommand(ACTION_TOGGLE_SHUFFLE, Bundle.EMPTY)
    val cycleRepeat = SessionCommand(ACTION_CYCLE_REPEAT, Bundle.EMPTY)
}

/** Off → all → one → off, like most music apps. */
fun nextRepeatMode(mode: Int): Int = when (mode) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
}

/** Rebuilds the last saved queue from MediaStore, or returns null when there is nothing to resume. */
object QueueRestorer {
    suspend fun load(context: Context): MediaSession.MediaItemsWithStartPosition? =
        withContext(Dispatchers.IO) {
            val store = PlaybackStore.get(context)
            val songs = MusicRepository(context).songsByIds(store.queueIds)
            if (songs.isEmpty()) return@withContext null
            val savedId = store.currentMediaId
            val index = songs.indexOfFirst { it.id.toString() == savedId }
            MediaSession.MediaItemsWithStartPosition(
                songs.map { it.toMediaItem() },
                index.coerceAtLeast(0),
                if (index >= 0) store.positionMs else 0L,
            )
        }
}
