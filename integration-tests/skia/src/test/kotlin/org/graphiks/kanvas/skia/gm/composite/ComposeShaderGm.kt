package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's gm/composeshader.cpp (ComposeShaderGM).
 * Renders a green rect then a DstIn-blend composed shader over it.
 * @see https://github.com/google/skia/blob/main/gm/composeshader.cpp
 */
class ComposeShaderGm : SkiaGm {
    override val name = "composeshader"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 25.6
    override val width = 120
    override val height = 120

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val shader = makeShader(BlendMode.DST_IN)
        val paint = Paint(color = ColorARGB.Green)
        val r = RectF32(0f, 0f, 100f, 100f)
        canvas.drawRect(r, paint)
        val shaderPaint = Paint(shader = shader)
        canvas.drawRect(r, shaderPaint)
    }

    private fun makeShader(mode: BlendMode): Shader {
        val shaderA = Shader.LinearGradient(
            start = Point2F32(0f, 0f),
            end = Point2F32(100f, 0f),
            stops = listOf(GradientStop(0f, ColorARGB.Red), GradientStop(1f, ColorARGB.Blue)),
            tileMode = TileMode.CLAMP,
        )
        val shaderB = Shader.LinearGradient(
            start = Point2F32(0f, 0f),
            end = Point2F32(0f, 100f),
            stops = listOf(
                GradientStop(0f, ColorARGB.fromRGBA(0f, 0f, 0f, 1f)),
                GradientStop(1f, ColorARGB.fromRGBA(0f, 0f, 0f, 0x80 / 255f)),
            ),
            tileMode = TileMode.CLAMP,
        )
        return Shader.Blend(mode, dst = shaderA, src = shaderB)
    }
}
