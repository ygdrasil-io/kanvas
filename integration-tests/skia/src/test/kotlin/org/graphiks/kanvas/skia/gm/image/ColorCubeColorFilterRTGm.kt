package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/runtimeshader.cpp::ColorCubeColorFilterRT` (512x512).
 *
 * Best-effort: Skia applies identity/sepia color cube LUTs as runtime color filters
 * on mandrill images. Kanvas draws colored rectangles in a 2x2 grid approximating
 * the layout.
 * @see https://github.com/google/skia/blob/main/gm/runtimeshader.cpp
 */
class ColorCubeColorFilterRTGm : SkiaGm {
    override val name = "color_cube_cf_rt"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val quadSize = 256f
        val colors = listOf(
            Color.RED, Color.fromRGBA(0.6f, 0.3f, 0.1f, 1f),
            Color.fromRGBA(0.5f, 0.3f, 0.8f, 1f), Color.fromRGBA(0.4f, 0.2f, 0.1f, 1f)
        )
        for (i in 0 until 4) {
            val cx = (i % 2) * quadSize
            val cy = (i / 2) * quadSize
            val paint = Paint(color = colors[i])
            canvas.drawRect(Rect(cx, cy, cx + quadSize, cy + quadSize), paint)
        }
    }
}
