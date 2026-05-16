package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorTRANSPARENT
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkCubicResampler
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/tiledscaledbitmap.cpp::TiledScaledBitmapGM`.
 *
 * Reproduces skbug.com/40034014 — a tiled bitmap shader was failing to
 * draw correctly when fractional image scaling was ignored by the high
 * quality bitmap scaler. The GM allocates a transparent 360×288 bitmap
 * with an anti-aliased filled black circle, then tiles it as a shader
 * scaled by `(121/360, 93/288)` with a `(-72, -72)` translation, sampled
 * with [SkCubicResampler.Mitchell], and paints a 1000×600 rect.
 *
 * C++ original:
 * ```cpp
 * SkString getName() const override { return SkString("tiledscaledbitmap"); }
 * SkISize getISize() override { return SkISize::Make(1016, 616); }
 *
 * static SkBitmap make_bm(int width, int height) {
 *     SkBitmap bm;
 *     bm.allocN32Pixels(width, height);
 *     bm.eraseColor(SK_ColorTRANSPARENT);
 *     SkCanvas canvas(bm);
 *     SkPaint paint;
 *     paint.setAntiAlias(true);
 *     canvas.drawCircle(width/2.f, height/2.f, width/4.f, paint);
 *     return bm;
 * }
 *
 * void onOnceBeforeDraw() override { fBitmap = make_bm(360, 288); }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     SkPaint paint;
 *     paint.setAntiAlias(true);
 *     SkMatrix mat;
 *     mat.setScale(121.f/360.f, 93.f/288.f);
 *     mat.postTranslate(-72, -72);
 *     paint.setShader(fBitmap.makeShader(SkTileMode::kRepeat, SkTileMode::kRepeat,
 *                                        SkSamplingOptions(SkCubicResampler::Mitchell()), mat));
 *     canvas->drawRect({ 8, 8, 1008, 608 }, paint);
 * }
 * ```
 */
public class TiledScaledBitmapGM : GM() {
    override fun getName(): String = "tiledscaledbitmap"
    override fun getISize(): SkISize = SkISize.Make(1016, 616)

    private lateinit var fBitmap: SkBitmap

    override fun onOnceBeforeDraw() {
        fBitmap = makeBm(360, 288)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply { isAntiAlias = true }

        // `setScale(121/360, 93/288)` then `postTranslate(-72, -72)`.
        // `postTranslate` composes as `T · M`, i.e. translate is applied
        // after the scale in the local-coord pipeline.
        val mat = SkMatrix.MakeScale(121f / 360f, 93f / 288f).postTranslate(-72f, -72f)

        paint.shader = fBitmap.makeShader(
            tileX = SkTileMode.kRepeat,
            tileY = SkTileMode.kRepeat,
            sampling = SkSamplingOptions(SkCubicResampler.Mitchell),
            localMatrix = mat,
        )
        c.drawRect(SkRect.MakeLTRB(8f, 8f, 1008f, 608f), paint)
    }

    private fun makeBm(width: Int, height: Int): SkBitmap {
        val bm = SkBitmap(width, height)
        bm.eraseColor(SK_ColorTRANSPARENT)
        val canvas = SkCanvas(bm)
        val paint = SkPaint().apply { isAntiAlias = true }
        canvas.drawCircle(width / 2f, height / 2f, width / 4f, paint)
        return bm
    }
}
