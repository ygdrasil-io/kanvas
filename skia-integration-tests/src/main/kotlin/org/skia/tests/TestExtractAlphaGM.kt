package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/bitmapfilters.cpp::TestExtractAlphaGM`
 * (`extractalpha`, 540 x 330).
 *
 * Renders a stroked blue circle into a 100x100 8888 bitmap, extracts
 * its alpha channel into a kAlpha_8 bitmap, then draws each into the
 * destination canvas with linear sampling and a red [SkPaint] :
 *  - the 8888 source ignores the paint's colour (stays blue);
 *  - the A8 source uses the paint's colour as the foreground (red).
 *
 * C++ original :
 * ```cpp
 * void onOnceBeforeDraw() override {
 *     fBitmap.allocN32Pixels(100, 100);
 *     SkCanvas canvas(fBitmap);
 *     canvas.clear(0);
 *     SkPaint paint;
 *     paint.setAntiAlias(true);
 *     paint.setColor(SK_ColorBLUE);
 *     paint.setStyle(SkPaint::kStroke_Style);
 *     paint.setStrokeWidth(20);
 *     canvas.drawCircle(50, 50, 39, paint);
 *     fBitmap.extractAlpha(&fAlpha);
 * }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     SkPaint paint;
 *     paint.setAntiAlias(true);
 *     paint.setColor(SK_ColorRED);
 *     SkSamplingOptions sampling(SkFilterMode::kLinear);
 *     canvas->drawImage(fBitmap.asImage(), 10, 10, sampling, &paint);
 *     canvas->drawImage(fAlpha.asImage(), 120, 10, sampling, &paint);
 * }
 * ```
 */
public class TestExtractAlphaGM : GM() {

    private lateinit var bitmap: SkBitmap
    private lateinit var alpha: SkBitmap

    override fun getName(): String = "extractalpha"

    override fun getISize(): SkISize = SkISize.Make(540, 330)

    override fun onOnceBeforeDraw() {
        bitmap = SkBitmap(100, 100)
        val sc = SkCanvas(bitmap)
        sc.clear(0)
        val sourcePaint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorBLUE
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 20f
        }
        sc.drawCircle(50f, 50f, 39f, sourcePaint)

        // Extract alpha into an A8 bitmap of the same size.
        alpha = SkBitmap(100, 100, SkColorSpace.makeSRGB(), SkColorType.kAlpha_8)
        bitmap.extractAlpha(alpha)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorRED
        }
        val sampling = SkSamplingOptions(SkFilterMode.kLinear)
        c.drawImage(bitmap.asImage(), 10f, 10f, sampling, paint)
        c.drawImage(alpha.asImage(), 120f, 10f, sampling, paint)
    }
}
