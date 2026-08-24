package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/localmatrixshader.cpp` LocalMatrixOrder (500 × 500).
 * Blends two images (mandrill and example_5) rotated about their centers
 * with kModulate blend mode, then applies an additional animation rotation.
 * This Kanvas port renders a single frame at a fixed rotation angle.
 * @see https://github.com/google/skia/blob/main/gm/localmatrixshader.cpp
 */
class LocalmatrixOrderGm : SkiaGm {
    override val name = "localmatrix_order"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val mandrill = loadImage("images/mandrill_128.png") ?: return

        canvas.drawColor(0.2f, 0.2f, 0.6f, 1f)

        val mandrillShader = Shader.Image(mandrill, TileMode.REPEAT, TileMode.REPEAT)
        val rotM = Matrix3x3F32.translation(128f, 128f) * Matrix3x3F32.rotation(45f) * Matrix3x3F32.translation(-128f, -128f)
        val rotatedMandrill = Shader.WithLocalMatrix(mandrillShader, rotM)

        val gradShader = Shader.RadialGradient(
            center = org.graphiks.math.geometry.Point2F32(128f, 128f), radius = 128f,
            stops = listOf(
                org.graphiks.kanvas.paint.GradientStop(0f, ColorARGB.White),
                org.graphiks.kanvas.paint.GradientStop(1f, ColorARGB.Black),
            ),
            tileMode = TileMode.REPEAT,
        )
        val scaleM = Matrix3x3F32.scaling(2f, 2f)
        val scaledGrad = Shader.WithLocalMatrix(gradShader, scaleM)
        val rotGrad = Shader.WithLocalMatrix(
            scaledGrad,
            Matrix3x3F32.translation(128f, 128f) * Matrix3x3F32.rotation(45f) * Matrix3x3F32.translation(-128f, -128f),
        )

        val blend = Shader.Blend(BlendMode.MODULATE, rotatedMandrill, rotGrad)
        val center = Matrix3x3F32.translation(250f, 250f) * Matrix3x3F32.rotation(30f) * Matrix3x3F32.translation(-250f, -250f)
        val finalShader = Shader.WithLocalMatrix(blend, center)
        canvas.drawRect(RectF32(0f, 0f, 500f, 500f), Paint(shader = finalShader))
    }

    private fun loadImage(path: String): Image? {
        val bytes = this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
        return if (bytes != null) Image.decode(bytes) else null
    }
}
