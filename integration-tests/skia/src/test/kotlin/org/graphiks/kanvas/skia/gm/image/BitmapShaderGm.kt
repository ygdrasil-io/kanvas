package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bitmapshader.cpp::BitmapShaderGM`.
 * @see https://github.com/google/skia/blob/main/gm/bitmapshader.cpp
 */
class BitmapShaderGm : SkiaGm {
    override val name = "bitmapshaders"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 150
    override val height = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val image = makeBitmapImage()
        val mask = makeMaskImage()

        canvas.drawColor(0.5f, 0.5f, 0.5f)

        repeat(2) { index ->
            canvas.save()
            val localMatrix = if (index == 0) {
                Matrix33.identity()
            } else {
                Matrix33.makeAll(1.5f, 0f, 2f, 0f, 1.5f, 2f)
            }

            val imageShader = image.makeShader().withLocalMatrix(localMatrix)
            var paint = Paint(shader = imageShader)
            canvas.drawImage(mask, Rect(0f, 0f, 20f, 20f), paint)
            canvas.drawImage(mask, Rect(30f, 0f, 50f, 20f), paint)

            canvas.translate(0f, 25f)
            canvas.drawCircle(10f, 10f, 10f, paint)
            canvas.drawCircle(40f, 10f, 10f, paint)

            canvas.translate(0f, 25f)
            paint = Paint(color = Color.GREEN)
            canvas.drawImage(mask, Rect(0f, 0f, 20f, 20f), paint)
            canvas.drawImage(mask, Rect(30f, 0f, 50f, 20f), paint)

            canvas.translate(0f, 25f)
            val maskShader = mask.makeShader(TileMode.REPEAT, TileMode.REPEAT).withLocalMatrix(localMatrix)
            paint = Paint(color = Color.RED, shader = maskShader)
            canvas.drawRect(Rect(0f, 0f, 20f, 20f), paint)
            canvas.drawRect(Rect(30f, 0f, 50f, 20f), paint)

            canvas.restore()
            canvas.translate(60f, 0f)
        }
    }

    private fun makeBitmapImage(): Image {
        val bitmap = Bitmap(20, 20, ColorType.RGBA_8888)
        bitmap.eraseColor(Color.RED)
        bitmap.fillCircle(10f, 10f, 5f, Color.BLUE)
        return Image.fromPixels(20, 20, bitmap.pixels.copyOf(), ColorType.RGBA_8888, "bitmapshaders-color")
    }

    private fun makeMaskImage(): Image {
        val bitmap = Bitmap(20, 20, ColorType.ALPHA_8)
        bitmap.eraseColor(Color.TRANSPARENT)
        bitmap.fillCircle(10f, 10f, 10f, Color.BLACK)
        return Image.fromPixels(20, 20, bitmap.pixels.copyOf(), ColorType.ALPHA_8, "bitmapshaders-mask")
    }

    private fun Bitmap.fillCircle(cx: Float, cy: Float, radius: Float, color: Color) {
        val radiusSq = radius * radius
        for (y in 0 until height) {
            for (x in 0 until width) {
                val dx = x + 0.5f - cx
                val dy = y + 0.5f - cy
                if (dx * dx + dy * dy <= radiusSq) {
                    setPixel(x, y, color)
                }
            }
        }
    }

    private fun Shader.Image.withLocalMatrix(matrix: Matrix33): Shader =
        if (matrix == Matrix33.identity()) this else Shader.WithLocalMatrix(this, matrix)
}
