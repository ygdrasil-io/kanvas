package org.skia.foundation

/**
 * Mirrors Skia's `SkFontStyle` (`include/core/SkFontStyle.h`).
 *
 * Three orthogonal axes:
 *  - **weight** — integer in `[0, 1000]`. Sentinel values exposed via [Weight].
 *  - **width** — integer in `[1, 9]`. Sentinel values via [Width].
 *  - **slant** — `kUpright_Slant` / `kItalic_Slant` / `kOblique_Slant`.
 *
 * Skia clamps both `weight` and `width` to their valid range in the C++
 * ctor; we do the same in [SkFontStyle.invoke] (the public Kotlin
 * factory acting as the constructor stand-in) so that calling code
 * matches upstream byte-for-byte.
 *
 * Convenience presets ([Normal], [Bold], [Italic], [BoldItalic]) match
 * Skia's `SkFontStyle::Normal()` etc. — they're top-level companion
 * functions rather than `static constexpr` members.
 */
public class SkFontStyle public constructor(
    weight: Int = kNormal_Weight,
    width: Int = kNormal_Width,
    slant: Slant = Slant.kUpright_Slant,
) {
    public val weight: Int = weight.coerceIn(kInvisible_Weight, kExtraBlack_Weight)
    public val width: Int = width.coerceIn(kUltraCondensed_Width, kUltraExpanded_Width)
    public val slant: Slant = slant

    public enum class Slant { kUpright_Slant, kItalic_Slant, kOblique_Slant }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkFontStyle) return false
        return weight == other.weight && width == other.width && slant == other.slant
    }

    override fun hashCode(): Int =
        weight * 31 * 31 + width * 31 + slant.ordinal

    override fun toString(): String =
        "SkFontStyle(weight=$weight, width=$width, slant=$slant)"

    public companion object {
        public const val kInvisible_Weight: Int = 0
        public const val kThin_Weight: Int = 100
        public const val kExtraLight_Weight: Int = 200
        public const val kLight_Weight: Int = 300
        public const val kNormal_Weight: Int = 400
        public const val kMedium_Weight: Int = 500
        public const val kSemiBold_Weight: Int = 600
        public const val kBold_Weight: Int = 700
        public const val kExtraBold_Weight: Int = 800
        public const val kBlack_Weight: Int = 900
        public const val kExtraBlack_Weight: Int = 1000

        public const val kUltraCondensed_Width: Int = 1
        public const val kExtraCondensed_Width: Int = 2
        public const val kCondensed_Width: Int = 3
        public const val kSemiCondensed_Width: Int = 4
        public const val kNormal_Width: Int = 5
        public const val kSemiExpanded_Width: Int = 6
        public const val kExpanded_Width: Int = 7
        public const val kExtraExpanded_Width: Int = 8
        public const val kUltraExpanded_Width: Int = 9

        public fun Normal(): SkFontStyle =
            SkFontStyle(kNormal_Weight, kNormal_Width, Slant.kUpright_Slant)

        public fun Bold(): SkFontStyle =
            SkFontStyle(kBold_Weight, kNormal_Width, Slant.kUpright_Slant)

        public fun Italic(): SkFontStyle =
            SkFontStyle(kNormal_Weight, kNormal_Width, Slant.kItalic_Slant)

        public fun BoldItalic(): SkFontStyle =
            SkFontStyle(kBold_Weight, kNormal_Width, Slant.kItalic_Slant)
    }
}
