package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's gm/colorfilterimagefilter.cpp.
 * Tests chained ImageFilter.ColorFilter with brightness, grayscale, blur and blue filters.
 * @see https://github.com/google/skia/blob/main/gm/colorfilterimagefilter.cpp
 */
class ColorFilterImageFilterGm : SkiaGm {
    override val name = "colorfilterimagefilter"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 44.8
    override val width = 435
    override val height = 120

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val r = Rect(0f, 0f, FILTER_WIDTH, FILTER_HEIGHT)
        val redPaint = Paint(color = Color.RED)

        canvas.save()
        var b = -1.0f
        while (b <= 1.0f + 1e-4f) {
            val dim = makeBrightness(-b, null)
            val bright = makeBrightness(b, dim)
            val p = redPaint.copy(imageFilter = bright)
            drawClippedRect(canvas, r, p, outset = 0f)
            canvas.translate(FILTER_WIDTH + MARGIN, 0f)
            b += 0.2f
        }
        canvas.restore()
        canvas.translate(0f, FILTER_HEIGHT + MARGIN)

        canvas.save()
        b = -1.0f
        while (b <= 1.0f + 1e-4f) {
            val dim = makeBrightness(-b, null)
            val bright = makeBrightness(b, dim)
            val p = redPaint.copy(imageFilter = bright)
            drawClippedRect(canvas, r, p, outset = 0f)
            canvas.translate(FILTER_WIDTH + MARGIN, 0f)
            b += 0.2f
        }
        canvas.restore()
        canvas.translate(0f, FILTER_HEIGHT + MARGIN)

        run {
            val brightness = makeBrightness(0.9f, null)
            val grayscale = makeGrayscale(brightness)
            canvas.drawRect(r, redPaint.copy(imageFilter = grayscale))
            canvas.translate(FILTER_WIDTH + MARGIN, 0f)
        }
        run {
            val grayscale = makeGrayscale(null)
            val brightness = makeBrightness(0.9f, grayscale)
            canvas.drawRect(r, redPaint.copy(imageFilter = brightness))
            canvas.translate(FILTER_WIDTH + MARGIN, 0f)
        }
        run {
            val blue = makeModeBlue(null)
            val brightness = makeBrightness(1.0f, blue)
            canvas.drawRect(r, redPaint.copy(imageFilter = brightness))
            canvas.translate(FILTER_WIDTH + MARGIN, 0f)
        }
        run {
            val brightness = makeBrightness(1.0f, null)
            val blue = makeModeBlue(brightness)
            canvas.drawRect(r, redPaint.copy(imageFilter = blue))
            canvas.translate(FILTER_WIDTH + MARGIN, 0f)
        }
        run {
            val blur = makeBlur(3.0f, null)
            val brightness = makeBrightness(0.5f, blur)
            drawClippedRect(canvas, r, redPaint.copy(imageFilter = brightness), outset = 3f)
            canvas.translate(FILTER_WIDTH + MARGIN, 0f)
        }
        run {
            val blue = makeModeBlue(null)
            drawClippedRect(canvas, r, redPaint.copy(imageFilter = blue), outset = 5f)
            canvas.translate(FILTER_WIDTH + MARGIN, 0f)
        }
    }

    private fun drawClippedRect(canvas: GmCanvas, r: Rect, paint: Paint, outset: Float) {
        canvas.save()
        val clip = Rect(r.left - outset, r.top - outset, r.right + outset, r.bottom + outset)
        canvas.clipRect(clip)
        canvas.drawRect(r, paint)
        canvas.restore()
    }

    private fun cfMakeBrightness(brightness: Float): ColorFilter {
        val matrix = floatArrayOf(
            1f, 0f, 0f, 0f, brightness,
            0f, 1f, 0f, 0f, brightness,
            0f, 0f, 1f, 0f, brightness,
            0f, 0f, 0f, 1f, 0f,
        )
        return ColorFilter.Matrix(matrix)
    }

    private fun cfMakeGrayscale(): ColorFilter {
        val matrix = FloatArray(20)
        matrix[0] = 0.2126f; matrix[1] = 0.7152f; matrix[2] = 0.0722f
        matrix[5] = 0.2126f; matrix[6] = 0.7152f; matrix[7] = 0.0722f
        matrix[10] = 0.2126f; matrix[11] = 0.7152f; matrix[12] = 0.0722f
        matrix[18] = 1.0f
        return ColorFilter.Matrix(matrix)
    }

    private fun cfMakeColorize(color: Color): ColorFilter =
        ColorFilter.Blend(color, BlendMode.SRC)

    private fun makeBrightness(amount: Float, input: ImageFilter?): ImageFilter =
        ImageFilter.ColorFilter(cfMakeBrightness(amount), input)

    private fun makeGrayscale(input: ImageFilter?): ImageFilter =
        ImageFilter.ColorFilter(cfMakeGrayscale(), input)

    private fun makeModeBlue(input: ImageFilter?): ImageFilter =
        ImageFilter.ColorFilter(cfMakeColorize(Color.BLUE), input)

    private fun makeBlur(amount: Float, input: ImageFilter?): ImageFilter =
        ImageFilter.Blur(amount, amount, input = input)

    private companion object {
        private const val FILTER_WIDTH: Float = 30f
        private const val FILTER_HEIGHT: Float = 30f
        private const val MARGIN: Float = 10f
    }
}
