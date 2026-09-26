package com.won3000.glowplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import java.text.Collator

/** Reads the songs stored on the device from MediaStore. Call from a background thread. */
class MusicRepository(context: Context) {

    private val resolver = context.applicationContext.contentResolver

    fun loadSongs(): List<Song> {
        val collator = Collator.getInstance()
        return query(selection = null, args = null).sortedWith(compareBy(collator) { it.title })
    }

    /** Returns the songs for [ids] in the same order, skipping files that no longer exist. */
    fun songsByIds(ids: List<Long>): List<Song> {
        if (ids.isEmpty()) return emptyList()
        val found = HashMap<Long, Song>(ids.size)
        ids.distinct().chunked(500).forEach { chunk ->
            val placeholders = chunk.joinToString(",") { "?" }
            val args = chunk.map { it.toString() }.toTypedArray()
            query("${MediaStore.Audio.Media._ID} IN ($placeholders)", args).forEach { found[it.id] = it }
        }
        return ids.mapNotNull { found[it] }
    }

    private fun query(selection: String?, args: Array<String>?): List<Song> {
        val where = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            if (selection != null) append(" AND ").append(selection)
        }
        val songs = ArrayList<Song>()
        try {
            resolver.query(COLLECTION, PROJECTION, where, args, null)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    songs += Song(
                        id = id,
                        title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: "#$id",
                        artist = cursor.getString(artistCol).knownOrNull(),
                        album = cursor.getString(albumCol).knownOrNull(),
                        albumId = cursor.getLong(albumIdCol),
                        year = cursor.getInt(yearCol).takeIf { it > 0 },
                        durationMs = cursor.getLong(durationCol),
                    )
                }
            }
        } catch (e: SecurityException) {
            // Permission not granted (or revoked while running).
            return emptyList()
        }
        return songs
    }

    private fun String?.knownOrNull(): String? =
        this?.takeIf { it.isNotBlank() && it != MediaStore.UNKNOWN_STRING }

    companion object {
        private val COLLECTION: Uri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        private val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")

        private val PROJECTION = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DURATION,
        )

        fun contentUriFor(id: Long): Uri = ContentUris.withAppendedId(COLLECTION, id)

        fun albumArtUriFor(albumId: Long): Uri = ContentUris.withAppendedId(ALBUM_ART, albumId)
    }
}
