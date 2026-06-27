package android.kimyona.jammer.data.dao

import androidx.lifecycle.LiveData
import androidx.room.*
import android.kimyona.jammer.data.entity.Track

@Dao
interface TrackDao {

    @Query("SELECT * FROM tracks ORDER BY title COLLATE NOCASE ASC")
    fun getAll(): LiveData<List<Track>>

    @Query("SELECT * FROM tracks WHERE path = :path")
    suspend fun getByPath(path: String): Track?

    /**
     * Full-text search across title, artist, album, alias, and all joined artists.
     * The [alias] field enables romanized / alternate-name search — e.g., searching
     * "Shiina Ringo" will find a track whose artist is "椎名林檎" if alias is set.
     */
    @Query("""
        SELECT * FROM tracks
        WHERE  title        LIKE '%' || :query || '%'
            OR artist       LIKE '%' || :query || '%'
            OR album        LIKE '%' || :query || '%'
            OR alias        LIKE '%' || :query || '%'
            OR artistsJoined LIKE '%' || :query || '%'
        ORDER BY title COLLATE NOCASE ASC
    """)
    fun search(query: String): LiveData<List<Track>>

    @Query("SELECT * FROM tracks WHERE isFavorite = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getFavorites(): LiveData<List<Track>>

    // ─── Content-rating filters ───────────────────────────────────────────────

    /** All tracks with the given content rating (pass [ContentRating.name]). */
    @Query("""
        SELECT * FROM tracks
        WHERE contentRating = :rating
        ORDER BY title COLLATE NOCASE ASC
    """)
    fun getByContentRating(rating: String): LiveData<List<Track>>

    /** Search within a specific content rating. */
    @Query("""
        SELECT * FROM tracks
        WHERE contentRating = :rating
          AND (title        LIKE '%' || :query || '%'
            OR artist       LIKE '%' || :query || '%'
            OR album        LIKE '%' || :query || '%'
            OR alias        LIKE '%' || :query || '%'
            OR artistsJoined LIKE '%' || :query || '%')
        ORDER BY title COLLATE NOCASE ASC
    """)
    fun searchByContentRating(query: String, rating: String): LiveData<List<Track>>

    // ─── Release-type filters ─────────────────────────────────────────────────

    /** All tracks tagged with the given release type (pass [ReleaseType.name]). */
    @Query("""
        SELECT * FROM tracks
        WHERE releaseType = :type
        ORDER BY title COLLATE NOCASE ASC
    """)
    fun getByReleaseType(type: String): LiveData<List<Track>>

    /** Search within a specific release type. */
    @Query("""
        SELECT * FROM tracks
        WHERE releaseType = :type
          AND (title        LIKE '%' || :query || '%'
            OR artist       LIKE '%' || :query || '%'
            OR album        LIKE '%' || :query || '%'
            OR alias        LIKE '%' || :query || '%'
            OR artistsJoined LIKE '%' || :query || '%')
        ORDER BY title COLLATE NOCASE ASC
    """)
    fun searchByReleaseType(query: String, type: String): LiveData<List<Track>>

    // ─── Metadata updates ─────────────────────────────────────────────────────

    @Query("UPDATE tracks SET alias = :alias WHERE path = :path")
    suspend fun setAlias(path: String, alias: String?)

    @Query("UPDATE tracks SET contentRating = :rating WHERE path = :path")
    suspend fun setContentRating(path: String, rating: String)

    @Query("UPDATE tracks SET releaseType = :type WHERE path = :path")
    suspend fun setReleaseType(path: String, type: String?)

    @Query("UPDATE tracks SET artistsJoined = :artistsJoined WHERE path = :path")
    suspend fun setArtistsJoined(path: String, artistsJoined: String?)

    // ─── Utility ──────────────────────────────────────────────────────────────

    /** Distinct non-unknown artist names for autocomplete / artist list screens. */
    @Query("""
        SELECT DISTINCT artist FROM tracks
        WHERE artist NOT IN ('Unknown Artist', '<unknown>')
        ORDER BY artist COLLATE NOCASE ASC
    """)
    suspend fun getAllArtists(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(tracks: List<Track>)

    // @Query("DELETE FROM tracks")
   // suspend fun clearAllTracks()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(track: Track)

    @Delete
    suspend fun delete(track: Track)



    @Query("SELECT COUNT(*) FROM tracks")
    suspend fun count(): Int

    @Query("UPDATE tracks SET isFavorite = :favorite WHERE path = :path")
    suspend fun setFavorite(path: String, favorite: Boolean)
}
