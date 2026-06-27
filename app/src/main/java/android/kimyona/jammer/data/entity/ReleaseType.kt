package android.kimyona.jammer.data.entity

/**
 * Classification of a track's parent release.
 *
 * Stored in Room as the enum's name() string.
 * null in Track.releaseType means the type is unknown / not yet tagged.
 */
enum class ReleaseType(val label: String) {
    ALBUM("Album"),
    EP("EP"),
    SINGLE("Single"),
    COMPILATION("Compilation"),
    MIX("Mix"),
    SPLIT("Split"),
    BOOTLEG("Bootleg"),
    REMIX("Remix / Re-remix");

    companion object {
        fun fromString(value: String?): ReleaseType? =
            entries.firstOrNull { it.name == value }
    }
}
