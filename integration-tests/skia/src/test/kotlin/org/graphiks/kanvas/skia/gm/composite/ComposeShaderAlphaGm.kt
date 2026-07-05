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
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of upstream Skia `gm/composeshader.cpp::ComposeShaderAlphaGM`.
 *
 * Draws two rows of the same composed gradient shader under decreasing
 * outer paint alpha. The first row uses `kDstIn`, the second `kSrcOver`.
 * Each cell paints an opaque green background first, then overlays the
 * composed shader with decreasing alpha.
 * @see https://github.com/google/skia/blob/main/gm/composeshader.cpp
 */
class ComposeShaderAlphaGm : SkiaGm {
    override val name = "composeshader_alpha"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 12.1
    override val width = 750
    override val height = 220

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val shaders = listOf(
            makeShader(BlendMode.DST_IN),
            makeShader(BlendMode.SRC_OVER),
        )
        val r = Rect.fromXYWH(5f, 5f, 100f, 100f)

        for (shader in shaders) {
            canvas.save()
            var alpha = 0xFF
            while (alpha > 0) {
                val bgPaint = Paint(color = Color.GREEN)
                canvas.drawRect(r, bgPaint)

                val overlayPaint = Paint(
                    color = Color.fromRGBA(1f, 1f, 1f, alpha / 255f),
                    shader = shader,
                )
                canvas.drawRect(r, overlayPaint)

                canvas.translate(r.width + 5f, 0f)
                alpha -= 0x28
            }
            canvas.restore()
            canvas.translate(0f, r.height + 5f)
        }
    }

    private fun makeShader(mode: BlendMode): Shader {
        val shaderA = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(100f, 0f),
            stops = listOf(
                GradientStop(0f, Color.RED),
                GradientStop(1f, Color.BLUE),
            ),
            tileMode = TileMode.CLAMP,
        )
        val shaderB = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(0f, 100f),
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(0f, 0f, 0f, 1f)),
                GradientStop(1f, Color.fromRGBA(0f, 0f, 0f, 0x80 / 255f)),
            ),
            tileMode = TileMode.CLAMP,
        )
        return Shader.Blend(mode, dst = shaderA, src = shaderB)
    }
}
