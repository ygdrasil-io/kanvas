package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/colorfilteralpha8.cpp` (`ColorFilterAlpha8` GM).
 *
 * Phase G4a proof-of-concept : exercises the `kAlpha_8` `SkBitmap`
 * accessors landed alongside this GM by building a 200×200 alpha-only
 * bitmap, filling it with 0x88FFFFFF (alpha = 0x88, RGB ignored), then
 * drawing it through a "force opaque grey" colour matrix on top of a
 * red canvas clear.
 *
 * C++ original :
 * ```cpp
 * SkString getName() const override { return SkString("colorfilteralpha8"); }
 * SkISize getISize() override { return SkISize::Make(400, 400); }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     canvas->clear(SK_ColorRED);
 *
 *     SkBitmap bitmap;
 *     SkImageInfo info = SkImageInfo::MakeA8(200, 200);
 *     bitmap.allocPixels(info);
 *     bitmap.eraseColor(0x88FFFFFF);
 *
 *     SkPaint paint;
 *     float opaqueGrayMatrix[20] = {
 *             0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
 *             0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
 *             0.0f, 0.0f, 0.0f, 1.0f, 0.0f,
 *             0.0f, 0.0f, 0.0f, 0.0f, 1.0f
 *     };
 *     paint.setColorFilter(SkColorFilters::Matrix(opaqueGrayMatrix));
 *
 *     canvas->drawImage(bitmap.asImage(), 100.0f, 100.0f, SkSamplingOptions(), &paint);
 * }
 * ```
 *
 * The "opaque grey" matrix takes the sampled alpha channel of the
 * Alpha8 image (alpha is the only signal it carries — RGB is zero) and
 * routes it to the R/G/B output channels via the `A → R/G/B`
 * coefficients in column 4, while column 5's `+1` term forces the
 * output alpha to fully opaque. So the rendered square is solid grey
 * with intensity = 0x88, painted on top of the red background.
 */
public class ColorFilterAlpha8GM : GM() {

    override fun getName(): String = "colorfilteralpha8"

    override fun getISize(): SkISize = SkISize.Make(400, 400)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorRED)

        val bitmap = SkBitmap.allocPixels(SkImageInfo.MakeA8(200, 200))
        bitmap.eraseColor(0x88FFFFFF.toInt())

        // 4 × 5 colour matrix laid out row-major (R G B A bias) — same
        // coefficients as upstream :
        //
        //   R' = A + 0
        //   G' = A + 0
        //   B' = A + 0
        //   A' = 0 + 1     (force fully opaque)
        val opaqueGrayMatrix = floatArrayOf(
            0f, 0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f, 0f,
            0f, 0f, 0f, 1f, 0f,
            0f, 0f, 0f, 0f, 1f,
        )
        val paint = SkPaint().apply {
            colorFilter = SkColorFilters.Matrix(opaqueGrayMatrix)
        }

        c.drawImage(bitmap.asImage(), 100f, 100f, SkSamplingOptions.Default, paint)
    }
}
