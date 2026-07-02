package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/colormatrix.cpp`.
 * Exercises ColorFilter.Matrix pipeline through drawImage.
 * @see https://github.com/google/skia/blob/main/gm/colormatrix.cpp
 */
class ColorMatrixGm : SkiaGm {
    override val name = "colormatrix"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 160

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.5f, 0.5f, 0.5f, 1f)

        val solidImg = createSolidBitmap(64, 64)
        val transparentImg = createTransparentBitmap(64, 64)
        val images = listOf(solidImg, transparentImg)

        for (image in images) {
            drawWithMatrix(canvas, image, 0f, identityMatrix())
            drawWithMatrix(canvas, image, 80f, saturationMatrix(0.0f))
            drawWithMatrix(canvas, image, 160f, saturationMatrix(0.5f))
            drawWithMatrix(canvas, image, 240f, saturationMatrix(1.0f))
            drawWithMatrix(canvas, image, 320f, saturationMatrix(2.0f))
            drawWithMatrix(canvas, image, 400f, redToAlphaWhiteMatrix())
            canvas.translate(0f, 80f)
        }
    }

    private fun drawWithMatrix(
        canvas: GmCanvas,
        image: Image,
        x: Float,
        matrix: FloatArray,
    ) {
        val paint = Paint(
            blendMode = BlendMode.SRC,
            colorFilter = ColorFilter.Matrix(matrix),
        )
        canvas.drawImage(image, Rect(x, 0f, x + 64f, 64f), paint)
    }

    private fun createSolidBitmap(w: Int, h: Int): Image {
        val surface = Surface(w, h)
        surface.canvas {
            for (y in 0 until h) {
                for (x in 0 until w) {
                    val r = x * 255 / w
                    val g = y * 255 / h
                    val color = Color.fromRGBA(r / 255f, g / 255f, 0f, 1f)
                    drawRect(Rect(x.toFloat(), y.toFloat(), (x + 1).toFloat(), (y + 1).toFloat()), Paint(color = color))
                }
            }
        }
        return surface.makeImageSnapshot()
    }

    private fun createTransparentBitmap(w: Int, h: Int): Image {
        val surface = Surface(w, h)
        surface.canvas {
            val shader = Shader.LinearGradient(
                start = Point(0f, 0f),
                end = Point(w.toFloat(), h.toFloat()),
                stops = listOf(
                    GradientStop(0f, Color.fromRGBA(0f, 0f, 0f, 0f)),
                    GradientStop(1f, Color.WHITE),
                ),
            )
            drawRect(Rect(0f, 0f, w.toFloat(), h.toFloat()), Paint(shader = shader))
        }
        return surface.makeImageSnapshot()
    }

    companion object {
        fun identityMatrix(): FloatArray = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )

        fun saturationMatrix(s: Float): FloatArray {
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

        fun redToAlphaWhiteMatrix(): FloatArray = floatArrayOf(
            0f, 0f, 0f, 0f, 1f,
            0f, 0f, 0f, 0f, 1f,
            0f, 0f, 0f, 0f, 1f,
            1f, 0f, 0f, 0f, 0f,
        )
    }
}
