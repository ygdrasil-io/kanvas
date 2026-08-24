package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.math.color.ColorMatrixF32

import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's gm/colorfilters.cpp.
 * Renders a 600x50 rainbow linear-gradient bar through 7 color filter filters.
 * @see https://github.com/google/skia/blob/main/gm/colorfilters.cpp
 */
class ColorFiltersGm : SkiaGm {
    override val name = "lightingcolorfilter"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 0.0
    override val width = 620
    override val height = 430

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val r = RectF32(0f, 0f, 600f, 50f)

        val paint = Paint(shader = makeShader(r))

        // (mul, add) pairs
        val rec: List<Pair<Int?, Int>> = listOf(
            null to 0,
            0xFF0000 to 0,
            0x00FF00 to 0,
            0x0000FF to 0,
            0x000000 to 0xFF0000,
            0x000000 to 0x00FF00,
            0x000000 to 0x0000FF,
        )

        canvas.translate(10f, 10f)
        for ((mul, add) in rec) {
            val colorFilter = if (mul == null) null else lighting(mul, add)
            val currentPaint = paint.copy(colorFilter = colorFilter)
            canvas.drawRect(r, currentPaint)
            canvas.translate(0f, r.height() + 10f)
        }
    }

    private fun makeShader(bounds: RectF32): Shader {
        return Shader.LinearGradient(
            start = Point2F32(bounds.left, bounds.top),
            end = Point2F32(bounds.right, bounds.bottom),
            stops = listOf(
                GradientStop(0f, ColorARGB.fromRGBA(1f, 0f, 0f, 1f)),   // red
                GradientStop(1f/6f, ColorARGB.fromRGBA(0f, 1f, 0f, 1f)),   // green
                GradientStop(2f/6f, ColorARGB.fromRGBA(0f, 0f, 1f, 1f)),   // blue
                GradientStop(3f/6f, ColorARGB.fromRGBA(0f, 0f, 0f, 1f)),   // black
                GradientStop(4f/6f, ColorARGB.fromRGBA(0f, 1f, 1f, 1f)),   // cyan
                GradientStop(5f/6f, ColorARGB.fromRGBA(1f, 0f, 1f, 1f)),   // magenta
                GradientStop(1f, ColorARGB.fromRGBA(1f, 1f, 0f, 1f)),   // yellow
            ),
        )
    }

    private fun lighting(mul: Int, add: Int): ColorFilter {
        val mulR = (mul ushr 16) and 0xFF
        val mulG = (mul ushr 8) and 0xFF
        val mulB = mul and 0xFF
        val addR = (add ushr 16) and 0xFF
        val addG = (add ushr 8) and 0xFF
        val addB = add and 0xFF
        val matrix = floatArrayOf(
            mulR / 255f, 0f,   0f,   0f, addR / 255f,
            0f,   mulG / 255f, 0f,   0f, addG / 255f,
            0f,   0f,   mulB / 255f, 0f, addB / 255f,
            0f,   0f,   0f,   1f, 0f,
        )
        return ColorFilter.Matrix(ColorMatrixF32.of(matrix))
    }
}
