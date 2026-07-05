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
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/clipshader.cpp::ClipShaderGM`.
 * Tests clipShader with a matrix transformation applied to a radial gradient shader via SRC_IN blend mode.
 * @see https://github.com/google/skia/blob/main/gm/clipshader.cpp
 */
class ClipShaderGm : SkiaGm {
    override val name = "clipshadermatrix"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 145
    override val height = 128

    private val maskRect = Rect.fromXYWH(0f, 10f, 64f, 44f)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(5f, 0f)
        for (tx in floatArrayOf(0f, 68.5f)) {
            for (ty in floatArrayOf(0f, 66.5f)) {
                canvas.save()
                canvas.translate(tx, ty)
                drawMaskAndContent(canvas, tx, ty)
                canvas.restore()
            }
        }
    }

    private fun drawMaskAndContent(canvas: GmCanvas, tx: Float, ty: Float) {
        canvas.drawOval(maskRect, Paint())
        canvas.saveLayer(null, Paint(blendMode = BlendMode.SRC_IN))
        canvas.translate(-tx, -ty)

        val m0 = Matrix33.makeAll(
            1.2f, 0.03f, 0f,
            0f, 0.8f, 0f,
            -0.002f, 0.0007f, 1f,
        )
        val rot = Matrix33.rotate(30f)
        canvas.concat(rot * m0)

        val gradient = Shader.RadialGradient(
            center = Point(64f, 64f),
            radius = 32f,
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(1f, 1f, 0f, 1f)),
                GradientStop(0.2f, Color.fromRGBA(0f, 1f, 0f, 1f)),
                GradientStop(0.4f, Color.fromRGBA(0f, 0f, 1f, 1f)),
                GradientStop(0.6f, Color.fromRGBA(1f, 0f, 1f, 1f)),
                GradientStop(0.8f, Color.fromRGBA(0f, 1f, 1f, 1f)),
                GradientStop(1f, Color.fromRGBA(1f, 1f, 0f, 1f)),
            ),
            tileMode = TileMode.MIRROR,
        )
        canvas.drawRect(Rect(0f, 0f, 145f, 128f), Paint(shader = gradient))
        canvas.restore()
    }
}
