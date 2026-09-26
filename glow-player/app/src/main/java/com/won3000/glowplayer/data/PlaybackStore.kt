package com.won3000.glowplayer.data

import android.content.Context
import androidx.core.content.edit
import androidx.media3.common.Player

/**
 * Remembers the queue, the current song and the play modes so that playback can be resumed
 * from the app, the Quick Settings tile or the quick panel after the process was killed.
 */
class PlaybackStore private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("playback_state", Context.MODE_PRIVATE)

    val queueIds: List<Long>
        get() = prefs.getString(KEY_QUEUE, null).orEmpty()
            .split(',')
            .mapNotNull { it.toLongOrNull() }

    val currentMediaId: String? get() = prefs.getString(KEY_CURRENT_ID, null)
    val positionMs: Long get() = prefs.getLong(KEY_POSITION, 0L)
    val shuffleEnabled: Boolean get() = prefs.getBoolean(KEY_SHUFFLE, false)
    val repeatMode: Int get() = prefs.getInt(KEY_REPEAT, Player.REPEAT_MODE_OFF)

    /** Last known song, used by the tile while the player service is not running. */
    val lastTitle: String? get() = prefs.getString(KEY_TITLE, null)
    val lastArtist: String? get() = prefs.getString(KEY_ARTIST, null)

    fun saveQueue(mediaIds: List<String>) = prefs.edit {
        putString(KEY_QUEUE, mediaIds.joinToString(","))
    }

    fun saveModes(shuffleEnabled: Boolean, repeatMode: Int) = prefs.edit {
        putBoolean(KEY_SHUFFLE, shuffleEnabled)
        putInt(KEY_REPEAT, repeatMode)
    }

    fun saveNowPlaying(player: Player) {
        val item = player.currentMediaItem ?: return
        prefs.edit {
            putString(KEY_CURRENT_ID, item.mediaId)
            putLong(KEY_POSITION, player.currentPosition.coerceAtLeast(0L))
            putString(KEY_TITLE, player.mediaMetadata.title?.toString())
            putString(KEY_ARTIST, player.mediaMetadata.artist?.toString())
        }
    }

    companion object {
        private const val KEY_QUEUE = "queue"
        private const val KEY_CURRENT_ID = "current_id"
        private const val KEY_POSITION = "position"
        private const val KEY_SHUFFLE = "shuffle"
        private const val KEY_REPEAT = "repeat"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"

        @Volatile
        private var instance: PlaybackStore? = null

        fun get(context: Context): PlaybackStore =
            instance ?: synchronized(this) {
                instance ?: PlaybackStore(context.applicationContext).also { instance = it }
            }
    }
}
