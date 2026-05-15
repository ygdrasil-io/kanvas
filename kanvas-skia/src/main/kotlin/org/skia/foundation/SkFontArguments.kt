package org.skia.foundation

/**
 * Mirrors Skia's
 * [`SkFontArguments`](https://github.com/google/skia/blob/main/include/core/SkFontArguments.h).
 *
 * The opaque description of how to instantiate a particular variant /
 * instance of a typeface — the parameter passed to
 * [SkTypeface.makeClone] (typeface-side variation) and to
 * `SkFontMgr.makeFromStream(stream, args)` (font-mgr-side variation
 * resolved at load time).
 *
 * Three orthogonal axes :
 *
 *  - [variationDesignPosition] — `fvar` axis design coordinates for
 *    OpenType variable fonts. Each [VariationPosition.Coordinate]
 *    pins one axis (`wght`, `wdth`, `slnt`, `opsz`, custom tags) to a
 *    design value.
 *  - [collectionIndex] — index into a TrueType / OpenType **collection**
 *    file (`.ttc`/`.otc`) selecting which face is loaded. Default `0`
 *    (the first face). The kanvas-skia AWT backend currently ignores
 *    non-zero indices (AWT's `Font.createFont` always loads face 0 —
 *    see [SkTypeface.makeClone] KDoc) ; this matches Skia's contract
 *    of "best-effort" when the backend can't honour the request.
 *  - [palette] — selects a palette entry / palette overrides for COLR
 *    v0 / v1 typefaces. Stub today — full implementation is gated on
 *    `STUB.COLR_V1` (see `API_FINALIZATION_PLAN.md`).
 *
 * **AWT-backed variation status** : the typeface clone path maps the
 * standard 4 OpenType axes (`wght` / `wdth` / `slnt` / `ital`) onto
 * `java.awt.font.TextAttribute` ; tags AWT does not model
 * (`opsz`, `GRAD`, `XHGT`, `XOPQ`, `YOPQ`, custom) are dropped silently
 * and logged at debug level. See [SkTypeface.makeClone] for the per-axis
 * mapping table.
 */
public class SkFontArguments {

    /**
     * `fvar` axis coordinates to apply to the cloned typeface. Empty
     * by default (no variation). Mirrors `SkFontArguments::fVariationDesignPosition`.
     */
    public var variationDesignPosition: VariationPosition = VariationPosition()

    /**
     * Collection face index (for `.ttc`/`.otc` files). `0` selects the
     * first face. Mirrors `SkFontArguments::fCollectionIndex`.
     */
    public var collectionIndex: Int = 0

    /**
     * Palette selection — stub. Full implementation is gated on
     * `STUB.COLR_V1` (see `API_FINALIZATION_PLAN.md`). The data class
     * exists so callers carrying an `SkFontArguments::Palette` through
     * compile and round-trip ; AWT can't honour palette overrides
     * regardless (no COLR-v1 support).
     */
    public var palette: Palette = Palette()

    /**
     * Builder-style setter — returns `this` to support the upstream
     * fluent style :
     *
     * ```kotlin
     * SkFontArguments().setVariationDesignPosition(position)
     * ```
     *
     * Mirrors `SkFontArguments& setVariationDesignPosition(VariationPosition)`.
     */
    public fun setVariationDesignPosition(position: VariationPosition): SkFontArguments {
        variationDesignPosition = position
        return this
    }

    /** Mirrors `SkFontArguments& setCollectionIndex(int)`. */
    public fun setCollectionIndex(index: Int): SkFontArguments {
        collectionIndex = index
        return this
    }

    /** Mirrors `SkFontArguments& setPalette(Palette)`. */
    public fun setPalette(value: Palette): SkFontArguments {
        palette = value
        return this
    }

    /**
     * Mirrors `SkFontArguments::VariationPosition` — a flat list of
     * `(axis, value)` tuples for the typeface's `fvar` axes. Multiple
     * coordinates on the same axis are allowed (last wins, mirroring
     * Skia's deterministic-iteration contract).
     */
    public data class VariationPosition(
        public val coordinates: List<Coordinate> = emptyList(),
    ) {
        public constructor(vararg coordinates: Coordinate) : this(coordinates.toList())

        /**
         * Mirrors `SkFontArguments::VariationPosition::Coordinate`.
         *
         * @property axis  4-byte OpenType axis tag (`wght`, `wdth`, …).
         *                See [SkFontVariation.Tag] for the packed-Int
         *                representation.
         * @property value design value at the requested point on the axis
         *                (axis-specific units : `wght` is 1..1000, `wdth`
         *                is a percentage like 100, `slnt` is degrees
         *                `[-90, 90]`, `opsz` is points).
         */
        public data class Coordinate(public val axis: Int, public val value: Float) {
            public companion object {
                /**
                 * Convenience factory — `Coordinate.of(Tag.of("wght"), 700f)`.
                 * The secondary-constructor form clashes on the JVM
                 * because [SkFontVariation.Tag] is an inline value class
                 * that erases to `Int`.
                 */
                public fun of(tag: SkFontVariation.Tag, value: Float): Coordinate =
                    Coordinate(tag.raw, value)
            }
        }
    }

    /**
     * Stub for `SkFontArguments::Palette`. The full implementation
     * (palette index + per-entry colour overrides) lands in the COLR v1
     * sprint (`STUB.COLR_V1`). Today only the no-arg default exists so
     * that variable-font code paths that never touch palette compile
     * unchanged.
     */
    public class Palette {
        /**
         * Palette index — `0` is the typeface's default palette.
         * Mirrors `SkFontArguments::Palette::index`.
         */
        public var index: Int = 0

        /**
         * Per-entry palette overrides. Empty by default. Each override
         * carries a colour-table index and a 32-bit RGBA colour.
         * Mirrors `SkFontArguments::Palette::overrides`.
         */
        public var overrides: List<Override> = emptyList()

        /** Mirrors `SkFontArguments::Palette::Override`. */
        public data class Override(public val index: Int, public val color: Int)
    }
}
