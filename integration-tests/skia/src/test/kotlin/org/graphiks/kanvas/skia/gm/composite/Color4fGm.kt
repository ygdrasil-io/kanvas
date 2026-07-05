package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/color4f.cpp`.
 * Draws (shader, colorFilter) combinations into sRGB surfaces.
 * @see https://github.com/google/skia/blob/main/gm/color4f.cpp
 */
class Color4fGm : SkiaGm {
    override val name = "color4f"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1024
    override val height = 260

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(10f, 10f)

        for (pass in 0 until 2) {
            val surface = Surface(1024, 100)
            drawIntoCanvas(surface)
            val image = surface.makeImageSnapshot()
            canvas.drawImage(image, Rect(0f, 0f, 1024f, 100f))
            canvas.translate(0f, 120f)
        }
    }

    private fun drawIntoCanvas(surface: Surface) {
        surface.canvas {
            drawRect(Rect(0f, 0f, 1024f, 100f), Paint(color = Color.WHITE))
            val r = Rect(0f, 0f, 50f, 100f)
            val shaderColors = listOf(Color.RED, Color.fromRGBA(1f, 0f, 0f, 0.5f))
            val filters: List<ColorFilter?> = listOf(
                null,
                ColorFilter.Matrix(saturationMatrix(0.75f)),
                makeCf1(),
                ColorFilter.Blend(Color.fromRGBA(0x80 / 255f, 0x44 / 255f, 0xCC / 255f, 0x88 / 255f), BlendMode.SRC_ATOP),
            )
            var tx = 0f
            for (col in shaderColors) {
                for (cf in filters) {
                    drawRect(Rect(tx, 0f, tx + 50f, 100f), Paint(color = col, colorFilter = cf))
                    tx += 60f
                }
            }
        }
    }

    private fun makeCf1(): ColorFilter {
        val outer = ColorFilter.Matrix(saturationMatrix(0.75f))
        val inner = ColorFilter.Matrix(scaleMatrix(1.1f, 0.9f, 1f))
        return ColorFilter.Compose(outer, inner)
    }

    private fun saturationMatrix(s: Float): FloatArray {
        val r = 0.213f * (1f - s)
        val g = 0.715f * (1f - s)
        val b = 0.072f * (1f - s)
        return floatArrayOf(
            s + r, g,     b,     0f, 0f,
            r,     s + g, b,     0f, 0f,
            r,     g,     s + b, 0f, 0f,
            0f,    0f,    0f,    1f, 0f,
        )
    }

    private fun scaleMatrix(rs: Float, gs: Float, bs: Float): FloatArray = floatArrayOf(
        rs, 0f, 0f, 0f, 0f,
        0f, gs, 0f, 0f, 0f,
        0f, 0f, bs, 0f, 0f,
        0f, 0f, 0f, 1f, 0f,
    )
}
