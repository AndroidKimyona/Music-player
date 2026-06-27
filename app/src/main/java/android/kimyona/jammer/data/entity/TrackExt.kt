package android.kimyona.jammer.data.entity

/**
 * Extension functions para Track que fornecem acesso tipado a contentRating e releaseType.
 *
 * Estas propriedades foram movidas para fora da entidade para evitar erro KSP
 * "MissingType: references a type that is not present".
 */

/** Typed content rating — safe to use anywhere without string comparison. */
val Track.contentRatingEnum: ContentRating
    get() = ContentRating.fromString(contentRating)

/** Typed release type — null if unknown/unset. */
val Track.releaseTypeEnum: ReleaseType?
    get() = ReleaseType.fromString(releaseType)

/** All artists as an ordered list. Prefers [artistsJoined] over the plain [artist] field. */
val Track.artistList: List<String>
    get() = artistsJoined
        ?.split(";")
        ?.map { it.trim() }
        ?.filter { it.isNotEmpty() }
        ?: listOf(artist)

/** Display-ready artist string — joins multiple artists with " · ". */
val Track.displayArtist: String
    get() = artistList.joinToString(" · ")
