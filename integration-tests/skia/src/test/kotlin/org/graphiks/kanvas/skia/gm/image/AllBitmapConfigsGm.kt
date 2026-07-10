package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/all_bitmap_configs.cpp::DEF_SIMPLE_GM(all_bitmap_configs, ...)`.
 * @see https://github.com/google/skia/blob/main/gm/all_bitmap_configs.cpp
 */
class AllBitmapConfigsGm : SkiaGm {
    override val name = "all_bitmap_configs"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.MEDIUM
    override val minSimilarity = 0.0
    override val width = 128
    override val height = 768

    private val font = Font(
        typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!,
        size = 12f,
    )

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        drawCheckerboard(canvas)

        val colorWheel = loadColorWheel()
        val blackPaint = Paint(color = Color.BLACK, antiAlias = true)
        val redPaint = Paint(color = Color.RED, antiAlias = true)

        draw(canvas, colorWheel, blackPaint, "Native 32")
        canvas.translate(0f, SCALE.toFloat())

        draw(canvas, copyTo(colorWheel, ColorType.RGB_565, "all_bitmap_configs-rgb565"), redPaint, "RGB 565")
        canvas.translate(0f, SCALE.toFloat())

        draw(canvas, copyTo(colorWheel, ColorType.ARGB_4444, "all_bitmap_configs-argb4444"), blackPaint, "ARGB 4444")
        canvas.translate(0f, SCALE.toFloat())

        draw(canvas, copyTo(colorWheel, ColorType.RGBA_F16, "all_bitmap_configs-rgbaf16"), blackPaint, "RGBA F16")
        canvas.translate(0f, SCALE.toFloat())

        draw(canvas, makeRampImage(ColorType.ALPHA_8, "all_bitmap_configs-alpha8"), blackPaint, "Alpha 8")
        canvas.translate(0f, SCALE.toFloat())

        draw(canvas, makeRampImage(ColorType.GRAY_8, "all_bitmap_configs-gray8"), redPaint, "Gray 8")
    }

    private fun draw(canvas: GmCanvas, image: Image, paint: Paint, label: String) {
        canvas.drawImage(image, Rect(0f, 0f, SCALE.toFloat(), SCALE.toFloat()))
        canvas.drawString(label, 0f, 12f, font, paint)
    }

    private fun loadColorWheel(): Image {
        val bytes = this::class.java.classLoader
            ?.getResourceAsStream("images/color_wheel.png")
            ?.readBytes()
            ?: error("Resource not found: images/color_wheel.png")
        val image = Image.decode(bytes)
        require(image.width == SCALE && image.height == SCALE) {
            "images/color_wheel.png decoded as ${image.width}x${image.height}, expected ${SCALE}x$SCALE"
        }
        return image
    }

    private fun copyTo(src: Image, colorType: ColorType, sourceId: String): Image {
        val srcBitmap = Bitmap.fromImage(src)
        val dst = Bitmap(src.width, src.height, colorType)
        for (y in 0 until src.height) {
            for (x in 0 until src.width) {
                dst.setPixel(x, y, srcBitmap.getPixel(x, y))
            }
        }
        return Image(dst.width, dst.height, dst.colorType, sourceId, dst.pixels.copyOf(), dst.colorSpace)
    }

    private fun makeRampImage(colorType: ColorType, sourceId: String): Image {
        val bitmap = Bitmap(SCALE, SCALE, colorType)
        for (y in 0 until SCALE) {
            for (x in 0 until SCALE) {
                val value = ((x + y) and 0xFF) / 255f
                bitmap.setPixel(x, y, Color.fromRGBA(value, value, value, value))
            }
        }
        return Image(bitmap.width, bitmap.height, bitmap.colorType, sourceId, bitmap.pixels.copyOf(), bitmap.colorSpace)
    }

    private fun drawCheckerboard(canvas: GmCanvas) {
        val ltGray = Color.fromRGBA(0.753f, 0.753f, 0.753f, 1f)
        val white = Color.WHITE
        val size = 8f
        for (y in 0..((height / size).toInt())) {
            for (x in 0..((width / size).toInt())) {
                val color = if ((x + y) % 2 == 0) ltGray else white
                canvas.drawRect(
                    Rect(x * size, y * size, (x + 1) * size, (y + 1) * size),
                    Paint.fill(color),
                )
            }
        }
    }

    private companion object {
        private const val SCALE = 128
    }
}
