package android.kimyona.jammer.data.entity

/**
 * Content rating labels for tracks.
 *
 * Stored in Room as the enum's name() string so the DB column stays
 * human-readable and survives future renames via fromString().
 *
 * badge  — single-character tag shown in the track row UI.
 * label  — full human-readable name used in menus and dialogs.
 */
enum class ContentRating(val label: String, val badge: String) {
    NONE("Clean", ""),
    EXPLICIT("Explicit", "E"),
    VULGAR("Vulgar", "V"),
    SLURRY("Slurry", "S"),
    LEWD("Lewd", "L"),
    SENSITIVE("Sensitive", "⚠");

    companion object {
        fun fromString(value: String?): ContentRating =
            entries.firstOrNull { it.name == value } ?: NONE

        /** All ratings that are actually flagged (excludes NONE). */
        val flagged: List<ContentRating> = entries.filter { it != NONE }
    }
}
