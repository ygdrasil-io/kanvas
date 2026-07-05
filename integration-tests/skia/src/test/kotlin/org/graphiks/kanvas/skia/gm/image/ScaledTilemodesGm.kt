package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/tilemodes_scaled.cpp::ScaledTilingGM` (880 × 880, 2 variants).
 * Exercises image shaders with tile modes, color types, and sampling options.
 * Missing API: SkColorType.kRGB_565, SkCubicResampler, SkMipmapMode, SkSamplingOptions.Aniso.
 * @see https://github.com/google/skia/blob/main/gm/tilemodes_scaled.cpp
 */
open class ScaledTilemodesGm(
    private val powerOfTwoSize: Boolean = true,
) : SkiaGm {
    override val name = if (powerOfTwoSize) "scaled_tilemodes" else "scaled_tilemodes_npot"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 880
    override val height = 880
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        /* TODO: requires extended sampling options and color types */
    }
}

class ScaledTilemodesNpotGm : ScaledTilemodesGm(powerOfTwoSize = false)
