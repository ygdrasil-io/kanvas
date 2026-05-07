package org.skia.foundation

/**
 * Mirrors Skia's `SkFontArguments::VariationPosition::Coordinate`
 * (`include/core/SkFontArguments.h`).
 *
 * A single (axis, value) pair targeting an OpenType `fvar` axis on a
 * variable font. Multiple [SkFontVariation]s combine via
 * [SkFont.variations] to set a variable font's design coordinates.
 *
 * **Backend status (Phase I2.2 light)** : the AWT-backed typeface in
 * [org.skia.foundation.awt.AwtTypeface] does **not** consume
 * variations yet — there is no AWT API that maps fvar coordinates to
 * a derived `java.awt.Font`. The data class is exposed today so direct
 * ports of upstream code that *carry* variation positions through
 * (without acting on them) compile and round-trip ; once an AWT-side
 * mapping (or a non-AWT scaler) lands, only [AwtTypeface] needs to
 * read this list.
 *
 * Serialisation note : Skia stores the axis as a 32-bit OpenType tag
 * (4 packed bytes, `('w'<<24)|('g'<<16)|('h'<<8)|'t'` for `wght`).
 * We expose it as an [Int] for bit-compat with upstream binary blobs
 * and provide [Tag] helpers for ergonomic construction.
 *
 * @property axis OpenType tag packed as a 32-bit big-endian integer
 *                — see [Tag].
 * @property value design value at the requested point on the axis
 *                (axis-specific units : `wght` is 1..1000, `wdth` is
 *                a percentage like 100, `slnt` is degrees `[-90,90]`,
 *                `opsz` is points, `ital` is `0` or `1`).
 */
public data class SkFontVariation(public val axis: Int, public val value: Float) {

    /**
     * 4-byte OpenType axis tag, packed big-endian into a 32-bit
     * integer. Mirrors Skia's `SkSetFourByteTag` macro / `SkFourByteTag`
     * typedef. Construct via [Make] or the [String] constructor — the
     * raw [Int] form is exposed for binary-format passthrough.
     */
    @JvmInline
    public value class Tag(public val raw: Int) {

        /** Render the tag as its 4 ASCII characters (e.g. `Tag(0x77676874).toString() == "wght"`). */
        override fun toString(): String = buildString(4) {
            append(((raw ushr 24) and 0xFF).toChar())
            append(((raw ushr 16) and 0xFF).toChar())
            append(((raw ushr 8) and 0xFF).toChar())
            append((raw and 0xFF).toChar())
        }

        public companion object {
            /** Pack 4 ASCII bytes as a big-endian tag — same as Skia's `SkSetFourByteTag`. */
            public fun Make(c0: Char, c1: Char, c2: Char, c3: Char): Tag = Tag(
                ((c0.code and 0xFF) shl 24) or
                    ((c1.code and 0xFF) shl 16) or
                    ((c2.code and 0xFF) shl 8) or
                    (c3.code and 0xFF),
            )

            /**
             * Parse a 4-character ASCII string into a tag — convenience for
             * the common case `Tag.of("wght")`. Throws [IllegalArgumentException]
             * if [s] is not exactly 4 ASCII characters.
             */
            public fun of(s: String): Tag {
                require(s.length == 4) { "OpenType tag must be 4 characters, got '$s' (length ${s.length})" }
                return Make(s[0], s[1], s[2], s[3])
            }
        }
    }

    public companion object {
        /**
         * Build a variation from a [Tag] + design value — equivalent to
         * the [Int]-axis primary constructor with [Tag.raw] inlined.
         */
        public fun of(axis: Tag, value: Float): SkFontVariation =
            SkFontVariation(axis.raw, value)

        /** Weight axis (`wght`) — design value in `[1, 1000]`, regular = 400. */
        public val WEIGHT: Tag = Tag.of("wght")

        /** Width axis (`wdth`) — percentage, 100 = normal. */
        public val WIDTH: Tag = Tag.of("wdth")

        /** Slant axis (`slnt`) — counterclockwise degrees, 0 = upright. */
        public val SLANT: Tag = Tag.of("slnt")

        /** Italic axis (`ital`) — `0` (off) or `1` (on). */
        public val ITALIC: Tag = Tag.of("ital")

        /** Optical size axis (`opsz`) — design size in points. */
        public val OPTICAL_SIZE: Tag = Tag.of("opsz")
    }
}
