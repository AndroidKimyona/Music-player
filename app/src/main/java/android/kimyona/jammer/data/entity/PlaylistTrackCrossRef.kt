package android.kimyona.jammer.data.entity

import androidx.room.Entity

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackPath"]
)
data class PlaylistTrackCrossRef(
    val playlistId: Long,
    val trackPath: String,
    val position: Int = 0
)
