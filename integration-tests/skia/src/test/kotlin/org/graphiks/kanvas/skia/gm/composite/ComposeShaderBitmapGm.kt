package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
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
 * Port of Skia's gm/composeshader.cpp (ComposeShaderBitmapGM).
 * Two variants: composeshader_bitmap and composeshader_bitmap_lm.
 * @see https://github.com/google/skia/blob/main/gm/composeshader.cpp
 */
open class ComposeShaderBitmapGm(private val useLm: Boolean) : SkiaGm {
    constructor() : this(false)

    override val name = "composeshader_bitmap${if (useLm) "_lm" else ""}"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 7 * (SQUARE_LENGTH + 5)
    override val height = 2 * (SQUARE_LENGTH + 5)

    private lateinit var colorImage: Image
    private lateinit var alpha8Image: Image

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        initImages()

        val mode = BlendMode.DST_OVER
        val lm = Matrix33.translate(0f, SQUARE_LENGTH * 0.5f)

        val colorBmShader = Shader.Image(colorImage, TileMode.REPEAT, TileMode.REPEAT)
        val a8BmShader = Shader.Image(alpha8Image, TileMode.REPEAT, TileMode.REPEAT)
        val grad = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(SQUARE_LENGTH.toFloat(), 0f),
            stops = listOf(
                GradientStop(0f, Color.BLUE),
                GradientStop(1f, Color.fromRGBA(0f, 0f, 1f, 0f)),
            ),
            tileMode = TileMode.CLAMP,
        )

        var shaders: List<Shader> = listOf(
            Shader.Blend(mode, dst = grad, src = colorBmShader),
            Shader.Blend(mode, dst = grad, src = a8BmShader),
        )
        if (useLm) {
            shaders = shaders.map { Shader.WithLocalMatrix(it, lm) }
        }

        val r = Rect(0f, 0f, SQUARE_LENGTH.toFloat(), SQUARE_LENGTH.toFloat())

        for (shader in shaders) {
            canvas.save()
            var alpha = 0xFF
            while (alpha > 0) {
                val paint = Paint(
                    color = Color.fromRGBA(1f, 1f, 0f, alpha / 255f),
                    shader = shader,
                )
                canvas.drawRect(r, paint)
                canvas.translate(r.width + 5f, 0f)
                alpha -= 0x28
            }
            canvas.restore()
            canvas.translate(0f, r.height + 5f)
        }
    }

    private fun initImages() {
        if (::colorImage.isInitialized) return
        val sl = SQUARE_LENGTH
        val cx = sl / 2f
        val cy = sl / 2f

        val colorPixels = ByteArray(sl * sl * 4)
        val a8Pixels = ByteArray(sl * sl)

        for (y in 0 until sl) {
            for (x in 0 until sl) {
                val idx4 = (y * sl + x) * 4
                val dx = x - cx
                val dy = y - cy
                val inCircle = dx * dx + dy * dy <= cx * cx

                if (inCircle) {
                    colorPixels[idx4] = 0x00.toByte()    // R
                    colorPixels[idx4 + 1] = (-1).toByte() // G = 0xFF
                    colorPixels[idx4 + 2] = 0x00.toByte() // B
                    colorPixels[idx4 + 3] = (-1).toByte() // A = 0xFF
                    a8Pixels[y * sl + x] = (-1).toByte()  // A = 0xFF
                } else {
                    colorPixels[idx4] = (-1).toByte()     // R = 0xFF
                    colorPixels[idx4 + 1] = 0x00.toByte() // G
                    colorPixels[idx4 + 2] = 0x00.toByte() // B
                    colorPixels[idx4 + 3] = (-1).toByte() // A = 0xFF
                    a8Pixels[y * sl + x] = 0x00.toByte()  // A = 0x00
                }
            }
        }

        colorImage = Image.fromPixels(sl, sl, colorPixels, ColorType.RGBA_8888)
        alpha8Image = Image.fromPixels(sl, sl, a8Pixels, ColorType.ALPHA_8)
    }

    private companion object {
        const val SQUARE_LENGTH: Int = 20
    }
}

class ComposeShaderBitmapLmGm : ComposeShaderBitmapGm(true) {
    override val renderCost = RenderCost.FAST
}
