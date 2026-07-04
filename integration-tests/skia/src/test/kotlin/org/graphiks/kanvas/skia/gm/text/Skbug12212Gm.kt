package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/skbug_12212.cpp` (400 × 400, cyan background).
 * Validates Alpha_8 offscreen rendering with subpixel-AA text.
 * Missing API: A8 surface drawing path with kSrc blend mode.
 * @see https://github.com/google/skia/blob/main/gm/skbug_12212.cpp
 */
class Skbug12212Gm : SkiaGm {
    override val name = "skbug_12212"
    override val renderFamily = RenderFamily.TEXT
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 400
    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        /* TODO: requires A8 offscreen surface with kSrc blend */
    }
}
