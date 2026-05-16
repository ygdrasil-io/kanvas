package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkCubicResampler
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/bmpfilterqualityrepeat.cpp::BmpFilterQualityRepeat`.
 *
 * Inspired by `svg/as-border-image/svg-as-border-image.html`. A 40 × 40
 * four-colour checkerboard bitmap (each quadrant a solid colour) is
 * tiled with `SkTileMode.kRepeat` and stretched / sampled with four
 * different filter qualities (nearest / linear / linear+mipmap /
 * Mitchell cubic). The test exercises whether the bitmap sampler
 * respects the repeat mode at tile seams under the various sampling
 * options.
 *
 * The drawing is repeated twice : first at the upstream scale, then
 * after `translate(0, 250)` + `scale(0.5, 0.5)` so the lower band
 * draws the same content at half the size.
 *
 * **kanvas-skia adaptation** : upstream constructs the local-matrix
 * via mutator calls (`lm.setScaleX(scaleX); lm.setTranslateX(423);
 * lm.setTranslateY(330);`). Our [SkMatrix] is immutable, so we build
 * the equivalent matrix directly with [SkMatrix.MakeAll] :
 *   `[ scaleX 0 423 ]`
 *   `[ 0      1 330 ]`
 *   `[ 0      0 1   ]`
 * — same six affine elements upstream's three setters land at on top
 * of the identity.
 *
 * C++ original (truncated to the rendering body — full file at
 * `gm/bmpfilterqualityrepeat.cpp`) :
 * ```cpp
 * onOnceBeforeDraw() {
 *   fBmp.allocN32Pixels(40, 40, true);
 *   SkCanvas canvas(fBmp);
 *   SkBitmap colorBmp; colorBmp.allocN32Pixels(20, 20, true);
 *   colorBmp.eraseColor(0xFFFF0000);
 *   canvas.drawImage(colorBmp.asImage(),  0,  0);
 *   colorBmp.eraseColor(ToolUtils::color_to_565(0xFF008200));
 *   canvas.drawImage(colorBmp.asImage(), 20,  0);
 *   colorBmp.eraseColor(ToolUtils::color_to_565(0xFFFF9000));
 *   canvas.drawImage(colorBmp.asImage(),  0, 20);
 *   colorBmp.eraseColor(ToolUtils::color_to_565(0xFF2000FF));
 *   canvas.drawImage(colorBmp.asImage(), 20, 20);
 * }
 * void onDraw(SkCanvas* canvas) {
 *   this->drawAll(canvas, 2.5f);
 *   canvas->translate(0, 250);
 *   canvas->scale(0.5, 0.5);
 *   this->drawAll(canvas, 1);
 * }
 * void drawAll(SkCanvas* canvas, SkScalar scaleX) const {
 *   SkRect rect = SkRect::MakeLTRB(20, 60, 220, 210);
 *   SkMatrix lm = SkMatrix::I();
 *   lm.setScaleX(scaleX);
 *   lm.setTranslateX(423);
 *   lm.setTranslateY(330);
 *   SkPaint textPaint; textPaint.setAntiAlias(true);
 *   SkPaint bmpPaint(textPaint);
 *   SkFont font = ToolUtils::DefaultPortableFont();
 *   SkAutoCanvasRestore acr(canvas, true);
 *   const struct { const char* name; SkSamplingOptions sampling; } recs[] = {
 *     { "none",   SkSamplingOptions(SkFilterMode::kNearest) },
 *     { "low",    SkSamplingOptions(SkFilterMode::kLinear) },
 *     { "medium", SkSamplingOptions(SkFilterMode::kLinear, SkMipmapMode::kLinear) },
 *     { "high",   SkSamplingOptions(SkCubicResampler::Mitchell()) },
 *   };
 *   for (const auto& rec : recs) {
 *     constexpr SkTileMode kTM = SkTileMode::kRepeat;
 *     bmpPaint.setShader(fBmp.makeShader(kTM, kTM, rec.sampling, lm));
 *     canvas->drawRect(rect, bmpPaint);
 *     canvas->drawString(rec.name, 20, 40, font, textPaint);
 *     canvas->translate(250, 0);
 *   }
 * }
 * ```
 */
public class BmpFilterQualityRepeatGM : GM() {

    init {
        setBGColor(ToolUtils.colorTo565(0xFFCCBBAA.toInt()))
    }

    private lateinit var fBmp: SkBitmap

    override fun getName(): String = "bmp_filter_quality_repeat"
    override fun getISize(): SkISize = SkISize.Make(1000, 400)

    override fun onOnceBeforeDraw() {
        // 40 × 40 four-colour quadrant board, built by drawing four
        // 20 × 20 solid bitmaps into a 40 × 40 host. The three non-red
        // colours are 565-quantised — upstream depends on this so the
        // captured reference (rendered to an 8888 buffer derived from a
        // 565-quantised palette) lines up bit-for-bit.
        fBmp = SkBitmap(40, 40)
        val host = SkCanvas(fBmp)

        val colorBmp = SkBitmap(20, 20)
        colorBmp.eraseColor(0xFFFF0000.toInt())
        host.drawImage(colorBmp.asImage(), 0f, 0f)
        colorBmp.eraseColor(ToolUtils.colorTo565(0xFF008200.toInt()))
        host.drawImage(colorBmp.asImage(), 20f, 0f)
        colorBmp.eraseColor(ToolUtils.colorTo565(0xFFFF9000.toInt()))
        host.drawImage(colorBmp.asImage(), 0f, 20f)
        colorBmp.eraseColor(ToolUtils.colorTo565(0xFF2000FF.toInt()))
        host.drawImage(colorBmp.asImage(), 20f, 20f)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawAll(c, 2.5f)
        c.translate(0f, 250f)
        c.scale(0.5f, 0.5f)
        drawAll(c, 1f)
    }

    private fun drawAll(canvas: SkCanvas, scaleX: Float) {
        val rect = SkRect.MakeLTRB(20f, 60f, 220f, 210f)
        // Upstream : SkMatrix lm = I(); lm.setScaleX(scaleX);
        //            lm.setTranslateX(423); lm.setTranslateY(330);
        // Equivalent in MakeAll form (sx, kx, tx, ky, sy, ty).
        val lm = SkMatrix.MakeAll(
            sx = scaleX, kx = 0f, tx = 423f,
            ky = 0f,     sy = 1f, ty = 330f,
        )

        val textPaint = SkPaint().apply { isAntiAlias = true }
        val bmpPaint = SkPaint().apply { isAntiAlias = true }
        val font = ToolUtils.DefaultPortableFont()

        // Wrap the per-record translates in an SkAutoCanvasRestore so
        // the caller's CTM is preserved on exit (upstream uses
        // `SkAutoCanvasRestore acr(canvas, true)`).
        canvas.withSave {
            val recs = arrayOf(
                "none"   to SkSamplingOptions(SkFilterMode.kNearest),
                "low"    to SkSamplingOptions(SkFilterMode.kLinear),
                "medium" to SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear),
                "high"   to SkSamplingOptions(SkCubicResampler.Mitchell),
            )
            for ((name, sampling) in recs) {
                bmpPaint.shader = fBmp.makeShader(
                    SkTileMode.kRepeat, SkTileMode.kRepeat, sampling, lm,
                )
                drawRect(rect, bmpPaint)
                drawString(name, 20f, 40f, font, textPaint)
                translate(250f, 0f)
            }
        }
    }
}
