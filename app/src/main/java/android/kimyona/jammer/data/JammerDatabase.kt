package android.kimyona.jammer.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.kimyona.jammer.data.dao.PlaylistDao
import android.kimyona.jammer.data.dao.QueueDao
import android.kimyona.jammer.data.dao.TrackDao
import android.kimyona.jammer.data.entity.Playlist
import android.kimyona.jammer.data.entity.PlaylistTrackCrossRef
import android.kimyona.jammer.data.entity.QueueItem
import android.kimyona.jammer.data.entity.Track

/**
 * JammerDatabase — v2
 *
 * Schema changes from v1:
 *   Track: added albumArtist, alias, contentRating, releaseType columns.
 *   TrackDao: alias-aware search; content-rating and release-type filter queries.
 *
 * fallbackToDestructiveMigration() handles the upgrade automatically.
 * The library will be re-scanned on first launch after the update.
 */
@Database(
    entities = [
        Track::class,
        Playlist::class,
        PlaylistTrackCrossRef::class,
        QueueItem::class
    ],
    version = 2,
    exportSchema = false
)
abstract class JammerDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun queueDao(): QueueDao

    companion object {
        @Volatile
        private var INSTANCE: JammerDatabase? = null

        fun getDatabase(context: Context): JammerDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }

        private fun buildDatabase(context: Context): JammerDatabase {
            return Room.databaseBuilder(
                context,
                JammerDatabase::class.java,
                "jammer_database"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
