package org.skia.foundation

/**
 * Iso-aligned port of upstream's
 * [`SkFontArguments`](https://github.com/google/skia/blob/main/include/core/SkFontArguments.h)
 * — the bag of options Skia passes through `SkFontMgr::makeFromStream(stream,
 * args)` and `SkTypeface::makeClone(args)` to influence which face a
 * collection / variable / colour font produces.
 *
 * `:kanvas-skia` exposes this surface so direct ports of upstream
 * `.cpp` code that *carry* font arguments through the pipeline
 * (without acting on them on every axis) compile. The fields are
 * partially honoured today :
 *  - [collectionIndex] is read by [org.skia.foundation.awt.AwtFontMgr]
 *    when opening a TTC face (variants are picked at index N) ;
 *  - [variationDesignPosition] is stored on [SkFont.variations] but
 *    not consumed by the AWT scaler (cf. [SkFontVariation] kdoc) ;
 *  - [palette] is part of the **STUB.COLR_V1** shape — see
 *    [org.skia.foundation.colr.SkColrV1] for the runtime stub.
 */
public data class SkFontArguments(
    /**
     * Index into a TrueType / OpenType collection (`.ttc`). 0 picks
     * the first face ; out-of-range falls back to 0.
     */
    public val collectionIndex: Int = 0,

    /**
     * Variable-font design coordinates ; mirrors
     * `SkFontArguments::VariationPosition::coordinates`. See
     * [SkFontVariation] for axis-tag semantics.
     */
    public val variationDesignPosition: List<SkFontVariation> = emptyList(),

    /**
     * Colour-font palette selection. Matches Skia's
     * `SkFontArguments::Palette` field-for-field. **STUB** : the
     * runtime path that consumes this list (COLR v1 glyph rendering)
     * lives in [org.skia.foundation.colr.SkColrV1] and throws — see
     * [`API_FINALIZATION_PLAN.md`](../../../../../../../../API_FINALIZATION_PLAN.md).
     */
    public val palette: Palette = Palette(),
) {
    /**
     * Mirrors `SkFontArguments::Palette` upstream. Names a built-in
     * palette by [index] and optionally overrides a list of palette
     * entries via [overrides].
     *
     * @property index palette index in the font's `CPAL` table.
     *                 Default `0` (the font-author-suggested default).
     * @property overrides per-entry colour overrides ;
     *                 [PaletteOverride.index] picks the entry within
     *                 the palette.
     */
    public data class Palette(
        public val index: Int = 0,
        public val overrides: List<PaletteOverride> = emptyList(),
    )

    /**
     * Mirrors `SkFontArguments::Palette::Override`. Replaces palette
     * entry [index] with the given [color] (RGBA in the same byte
     * layout as [SkColor]).
     */
    public data class PaletteOverride(
        public val index: Int,
        public val color: SkColor,
    )
}
