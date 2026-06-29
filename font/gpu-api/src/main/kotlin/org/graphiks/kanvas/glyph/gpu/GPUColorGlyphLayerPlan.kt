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

    /**
     * Dumpable canonical description of the plan for PM/route evidence. Lists the
     * base glyph and each layer's coverage glyph, palette index, and resolved
     * ARGB color (or "foreground"), without leaking renderer or parser state.
     */
    fun toColorLayerDump(): String = buildString {
        append("GPUColorGlyphLayerPlan(")
        append("baseGlyphID=").append(baseGlyphID.toString())
        append(", contentFingerprint=").append(artifactKey.contentFingerprint)
        append(", layerCount=").append(layerCount)
        append(", layers=[")
        layers.forEachIndexed { index, layer ->
            if (index > 0) append(", ")
            append("{layerGlyphID=").append(layer.layerGlyphID.toString())
            append(", paletteIndex=").append(layer.paletteIndex)
            append(", color=")
            append(if (layer.useForeground) "foreground" else gpuColorGlyphArgbHex(layer.resolvedColorArgb))
            append("}")
        }
        append("])")
    }
}

private fun gpuColorGlyphArgbHex(color: Int?): String {
    if (color == null) return "null"
    val unsigned = color.toLong() and 0xFFFF_FFFFL
    return "#" + unsigned.toString(16).uppercase().padStart(8, '0')
}
