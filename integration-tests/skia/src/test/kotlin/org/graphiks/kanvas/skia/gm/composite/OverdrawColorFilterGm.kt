package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/overdrawcolorfilter.cpp`.
 * Draws A8 alpha tiles through an OverdrawColorFilter.
 * @see https://github.com/google/skia/blob/main/gm/overdrawcolorfilter.cpp
 */
class OverdrawColorFilterGm : SkiaGm {
    override val name = "overdrawcolorfilter"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 10.0
    override val width = 200
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(colorFilter = ColorFilter.Overdraw)

        val positions = listOf(
            0 to Pair(0f, 0f),
            1 to Pair(0f, 100f),
            2 to Pair(0f, 200f),
            3 to Pair(0f, 300f),
            4 to Pair(100f, 0f),
            5 to Pair(100f, 100f),
            6 to Pair(100f, 200f),
        )

        for ((alpha, pos) in positions) {
            val pixels = ByteArray(100 * 100) { alpha.toByte() }
            val image = Image.fromPixels(100, 100, pixels, ColorType.ALPHA_8, "tile_$alpha")
            canvas.drawImage(image, Rect(pos.first, pos.second, pos.first + 100f, pos.second + 100f), paint)
        }
    }
}
