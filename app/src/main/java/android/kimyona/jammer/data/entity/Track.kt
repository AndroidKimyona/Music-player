package android.kimyona.jammer.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Track — v3 (KSP-safe)
 *
 * Mudança vs v2: propriedades computadas @get:Ignore removidas do corpo da entidade
 * para evitar erro KSP "MissingType: references a type that is not present".
 * Use TrackExt.kt para acessar contentRatingEnum, releaseTypeEnum, artistList, displayArtist.
 */
@Entity(tableName = "tracks")
data class Track(
    @PrimaryKey
    val path: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val format: String,
    val dateAdded: Long = System.currentTimeMillis(),
    val isFavorite: Boolean = false,
    // Multi-artist: stored as "Artist A; Artist B"
    val artistsJoined: String? = null,
    val genresJoined: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val discNumber: Int? = null,
    val lyricsPath: String? = null,
    val coverPath: String? = null,
    // --- v2 additions ---
    /** Album artist (used for compilations / Various Artists). */
    val albumArtist: String? = null,
    /**
     * Alternate/romanized search name.
     * Example: a track where artist = "椎名林檎" can have alias = "Shiina Ringo"
     * so searching either form finds the track.
     */
    val alias: String? = null,
    /**
     * Content rating, stored as string name.
     * Default "NONE" (clean/unrated).
     */
    val contentRating: String = "NONE",
    /**
     * Release type, stored as string name.
     * null = not yet tagged.
     */
    val releaseType: String? = null
)
