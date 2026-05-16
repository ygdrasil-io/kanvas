package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkBlendMode
import org.graphiks.math.SkColor
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/colorfilterimagefilter.cpp::DEF_SIMPLE_GM(colorfilterimagefilter, …, 435, 120)`.
 *
 * Draws a 30 × 30 red rect through eleven horizontally-tiled image-filter
 * chains. Two leading rows of eleven sweep a brightness dim → bright loop
 * with and without `SkColorFilters::Clamp::kNo` ; the third row carries
 * seven mixed compositions (grayscale + brightness in both orders, blue +
 * brightness in both orders, blur + brightness, plain blue).
 *
 * The C++ original uses `clipRect.outset(0)` (no expansion) for most cells
 * and `outset(3)` / `outset(5)` for the blur cells to admit the blur's
 * extra rings of pixels.
 *
 * `mandrill_128.png` is **not** used by this GM. The asset reference in
 * the H1-finish batch table corresponds to the same .cpp file's
 * `colorfiltershader` companion GM (deferred — needs `image->makeShader`
 * inside `shader->makeWithColorFilter`, plus `SkShader.makeWithColorFilter`
 * which is one of the currently-blocked APIs).
 *
 * C++ original :
 * ```cpp
 * DEF_SIMPLE_GM(colorfilterimagefilter, canvas, 435, 120) {
 *   SkRect r = SkRect::MakeWH(FILTER_WIDTH, FILTER_HEIGHT);
 *   SkPaint paint; paint.setColor(SK_ColorRED);
 *   canvas->save();
 *   for (float brightness = -1.0f; brightness <= 1.0f; brightness += 0.2f) {
 *     auto dim = make_brightness(-brightness, nullptr);
 *     auto bright = make_brightness(brightness, std::move(dim));
 *     paint.setImageFilter(std::move(bright));
 *     draw_clipped_rect(canvas, r, paint);
 *     canvas->translate(FILTER_WIDTH + MARGIN, 0);
 *   }
 *   canvas->restore();
 *   canvas->translate(0, FILTER_HEIGHT + MARGIN);
 *   // same loop, kNo clamp
 *   canvas->translate(0, FILTER_HEIGHT + MARGIN);
 *   // grayscale + brightness combos
 *   // blue + brightness combos
 *   // blur+brightness, plain blue
 * }
 * ```
 */
public class ColorFilterImageFilterGM : GM() {

    override fun getName(): String = "colorfilterimagefilter"

    override fun getISize(): SkISize = SkISize.Make(435, 120)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val r = SkRect.MakeWH(FILTER_WIDTH, FILTER_HEIGHT)
        val paint = SkPaint().apply { color = SK_ColorRED }

        // Row 1 — brightness sweep, default (clamp = kYes — implicit in our
        // SkColorFilters.Matrix since we always clamp on storage edge).
        c.save()
        var b = -1.0f
        while (b <= 1.0f + 1e-4f) {
            val dim = makeBrightness(-b, null)
            val bright = makeBrightness(b, dim)
            paint.imageFilter = bright
            drawClippedRect(c, r, paint, outset = 0f)
            c.translate(FILTER_WIDTH + MARGIN, 0f)
            b += 0.2f
        }
        c.restore()
        c.translate(0f, FILTER_HEIGHT + MARGIN)

        // Row 2 — same loop, but our colour-filter implementation clamps on
        // the storage edge regardless (no kNo Clamp variant); semantically
        // this row is equivalent to row 1 in `:kanvas-skia`.
        c.save()
        b = -1.0f
        while (b <= 1.0f + 1e-4f) {
            val dim = makeBrightness(-b, null)
            val bright = makeBrightness(b, dim)
            paint.imageFilter = bright
            drawClippedRect(c, r, paint, outset = 0f)
            c.translate(FILTER_WIDTH + MARGIN, 0f)
            b += 0.2f
        }
        c.restore()
        c.translate(0f, FILTER_HEIGHT + MARGIN)

        // Row 3 — mixed compositions.
        run {
            val brightness = makeBrightness(0.9f, null)
            val grayscale = makeGrayscale(brightness)
            paint.imageFilter = grayscale
            drawClippedRect(c, r, paint, outset = 0f)
            c.translate(FILTER_WIDTH + MARGIN, 0f)
        }
        run {
            val grayscale = makeGrayscale(null)
            val brightness = makeBrightness(0.9f, grayscale)
            paint.imageFilter = brightness
            drawClippedRect(c, r, paint, outset = 0f)
            c.translate(FILTER_WIDTH + MARGIN, 0f)
        }
        run {
            val blue = makeModeBlue(null)
            val brightness = makeBrightness(1.0f, blue)
            paint.imageFilter = brightness
            drawClippedRect(c, r, paint, outset = 0f)
            c.translate(FILTER_WIDTH + MARGIN, 0f)
        }
        run {
            val brightness = makeBrightness(1.0f, null)
            val blue = makeModeBlue(brightness)
            paint.imageFilter = blue
            drawClippedRect(c, r, paint, outset = 0f)
            c.translate(FILTER_WIDTH + MARGIN, 0f)
        }
        run {
            val blur = makeBlur(3.0f, null)
            val brightness = makeBrightness(0.5f, blur)
            paint.imageFilter = brightness
            drawClippedRect(c, r, paint, outset = 3f)
            c.translate(FILTER_WIDTH + MARGIN, 0f)
        }
        run {
            val blue = makeModeBlue(null)
            paint.imageFilter = blue
            drawClippedRect(c, r, paint, outset = 5f)
            c.translate(FILTER_WIDTH + MARGIN, 0f)
        }
    }

    private fun drawClippedRect(canvas: SkCanvas, r: SkRect, paint: SkPaint, outset: Float) {
        canvas.save()
        val clip = SkRect.MakeLTRB(r.left, r.top, r.right, r.bottom)
        clip.outset(outset, outset)
        canvas.clipRect(clip)
        canvas.drawRect(r, paint)
        canvas.restore()
    }

    private fun cfMakeBrightness(brightness: Float): SkColorFilter {
        val matrix = floatArrayOf(
            1f, 0f, 0f, 0f, brightness,
            0f, 1f, 0f, 0f, brightness,
            0f, 0f, 1f, 0f, brightness,
            0f, 0f, 0f, 1f, 0f,
        )
        return SkColorFilters.Matrix(matrix)
    }

    private fun cfMakeGrayscale(): SkColorFilter {
        val matrix = FloatArray(20)
        // Row r = 0.2126 R + 0.7152 G + 0.0722 B.
        matrix[0] = 0.2126f; matrix[1] = 0.7152f; matrix[2] = 0.0722f
        matrix[5] = 0.2126f; matrix[6] = 0.7152f; matrix[7] = 0.0722f
        matrix[10] = 0.2126f; matrix[11] = 0.7152f; matrix[12] = 0.0722f
        matrix[18] = 1.0f
        return SkColorFilters.Matrix(matrix)
    }

    private fun cfMakeColorize(color: SkColor): SkColorFilter =
        SkColorFilters.Blend(color, SkBlendMode.kSrc)

    private fun makeBrightness(amount: Float, input: SkImageFilter?): SkImageFilter =
        SkImageFilters.ColorFilter(cfMakeBrightness(amount), input)

    private fun makeGrayscale(input: SkImageFilter?): SkImageFilter =
        SkImageFilters.ColorFilter(cfMakeGrayscale(), input)

    private fun makeModeBlue(input: SkImageFilter?): SkImageFilter =
        SkImageFilters.ColorFilter(cfMakeColorize(SK_ColorBLUE), input)

    private fun makeBlur(amount: Float, input: SkImageFilter?): SkImageFilter? =
        SkImageFilters.Blur(amount, amount, input)

    private companion object {
        private const val FILTER_WIDTH: Float = 30f
        private const val FILTER_HEIGHT: Float = 30f
        private const val MARGIN: Float = 10f
    }
}
