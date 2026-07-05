package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect

open class ClippedBitmapShadersBase(
    private val mode: TileMode,
    private val hq: Boolean,
) : SkiaGm {
    override val name: String
        get() {
            val descriptor = when (mode) {
                TileMode.REPEAT -> "tile"
                TileMode.MIRROR -> "mirror"
                TileMode.CLAMP -> "clamp"
                TileMode.DECAL -> "decal"
            }
            return if (hq) "clipped-bitmap-shaders-$descriptor-hq" else "clipped-bitmap-shaders-$descriptor"
        }

    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 300
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bmp = createBitmap()
        val s = Matrix33.translate(SLIDE_SIZE / 2f, SLIDE_SIZE / 2f) * Matrix33.scale(8f, 8f)
        val shader = Shader.WithLocalMatrix(Shader.Image(bmp, mode, mode), s)
        val paint = Paint(shader = shader)

        val margin = (SLIDE_SIZE / 3f - RECT_SIZE) / 2f
        for (i in 0 until 3) {
            val yOrigin = SLIDE_SIZE / 3f * i + margin
            for (j in 0 until 3) {
                val xOrigin = SLIDE_SIZE / 3f * j + margin
                if (i == 1 && j == 1) continue
                val rect = Rect.fromXYWH(xOrigin, yOrigin, RECT_SIZE, RECT_SIZE)
                canvas.save()
                canvas.clipRect(rect)
                canvas.drawRect(rect, paint)
                canvas.restore()
            }
        }
    }

    private fun createBitmap(): Image {
        val w = 2
        val h = 2
        val pixels = ByteArray(w * h * 4)
        fun setPixel(x: Int, y: Int, r: Byte, g: Byte, b: Byte) {
            val i = (y * w + x) * 4
            pixels[i] = r
            pixels[i + 1] = g
            pixels[i + 2] = b
            pixels[i + 3] = (-1).toByte()
        }
        setPixel(0, 0, (-1).toByte(), 0, 0)
        setPixel(1, 0, 0, (-1).toByte(), 0)
        setPixel(0, 1, 0, 0, 0)
        setPixel(1, 1, 0, 0, (-1).toByte())
        return Image.fromPixels(w, h, pixels, ColorType.RGBA_8888, "clipped_bitmap_shaders")
    }

    private companion object {
        const val RECT_SIZE: Float = 64f
        const val SLIDE_SIZE: Float = 300f
    }
}

class ClippedBitmapShadersTileGm : ClippedBitmapShadersBase(TileMode.REPEAT, false)
class ClippedBitmapShadersMirrorGm : ClippedBitmapShadersBase(TileMode.MIRROR, false)
class ClippedBitmapShadersClampGm : ClippedBitmapShadersBase(TileMode.CLAMP, false)
class ClippedBitmapShadersTileHqGm : ClippedBitmapShadersBase(TileMode.REPEAT, true)
class ClippedBitmapShadersMirrorHqGm : ClippedBitmapShadersBase(TileMode.MIRROR, true)
class ClippedBitmapShadersClampHqGm : ClippedBitmapShadersBase(TileMode.CLAMP, true)
