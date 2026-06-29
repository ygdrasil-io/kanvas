package org.graphiks.kanvas.glyph.gpu

/**
 * One GPU-facing layer of a COLRv0 color glyph: a coverage glyph plus a resolved
 * solid color. Renderer-neutral — no parser state, GPU handle, or pixels. The
 * color is a packed ARGB integer resolved from the font's CPAL palette, or null
 * when [useForeground] is true (CPAL index 0xFFFF).
 *
 * @property layerGlyphID Glyph whose A8 coverage fills this layer.
 * @property paletteIndex CPAL palette entry index used by this layer.
 * @property resolvedColorArgb Packed ARGB color resolved from CPAL, or null when [useForeground].
 * @property useForeground True when the layer uses the text foreground color.
 */
data class GPUColorGlyphLayer(
    val layerGlyphID: UInt,
    val paletteIndex: Int,
    val resolvedColorArgb: Int?,
    val useForeground: Boolean,
)

/**
 * GPU-facing COLRv0 color glyph plan: an ordered (bottom -> top) list of
 * solid-color layers for one base glyph. Replaces the count-only [ColorGlyphPlan]
 * on the color render path. Describes shapes and colors, never a finished
 * CPU-rendered texture.
 *
 * @property artifactKey Stable artifact key for this color glyph plan.
 * @property baseGlyphID The base (COLR) glyph this plan paints.
 * @property layers Color layers in paint order, bottom layer first.
 */
data class GPUColorGlyphLayerPlan(
    val artifactKey: GPUTextArtifactKey,
    val baseGlyphID: UInt,
    val layers: List<GPUColorGlyphLayer>,
) {
    init {
        require(layers.isNotEmpty()) {
            "GPUColorGlyphLayerPlan must carry at least one color layer."
        }
    }

    /** Number of color layers in this plan. */
    val layerCount: Int get() = layers.size
}
