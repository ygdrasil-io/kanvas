package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Rect

private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

/**
 * Port of Skia's `gm/overdrawcanvas.cpp` (500 × 500, white background).
 * Renders overlapping rectangles and text through an overdraw color filter.
 * Uses ColorFilter.Overdraw to visualize draw count as a heat map.
 * @see https://github.com/google/skia/blob/main/gm/overdrawcanvas.cpp
 */
class OverdrawCanvasGm : SkiaGm {
    override val name = "overdraw_canvas"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // Build A8 alpha tiles representing draw count
        val positions = listOf(
            1 to Pair(0f, 0f),
            2 to Pair(0f, 100f),
            3 to Pair(0f, 200f),
            4 to Pair(0f, 300f),
            5 to Pair(100f, 0f),
            6 to Pair(100f, 100f),
        )

        val paint = Paint(colorFilter = ColorFilter.Overdraw)

        for ((alpha, pos) in positions) {
            val pixels = ByteArray(100 * 100) { alpha.toByte() }
            val image = Image.fromPixels(100, 100, pixels, ColorType.ALPHA_8, "tile_$alpha")
            canvas.drawImage(image, Rect(pos.first, pos.second, pos.first + 100f, pos.second + 100f), paint)
        }

        canvas.drawString("This is some text:", 180f, 300f, Font(typeface, size = 24f), Paint())
    }
}
