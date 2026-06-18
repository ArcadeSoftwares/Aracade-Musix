package com.arcadesoftware.musix.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.music.innertube.models.Artist
import com.music.innertube.models.SongItem

@Entity(tableName = "downloaded_songs")
data class DownloadedSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artistName: String,
    val artistId: String?,
    val thumbnailUrl: String,
    val localFilePath: String,
    val downloadTimestamp: Long = System.currentTimeMillis()
) {
    fun toSongItem(): SongItem {
        return SongItem(
            id = id,
            title = title,
            artists = listOf(Artist(name = artistName, id = artistId)),
            thumbnail = thumbnailUrl,
            explicit = false
        )
    }
}
