package org.skia.tests

import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/alpha_image.cpp::DEF_SIMPLE_GM(alpha_bitmap_is_coverage_ANDROID, …, 128, 128)`.
 *
 * Gated upstream by `#if defined(SK_SUPPORT_LEGACY_ALPHA_BITMAP_AS_COVERAGE)` —
 * an Android-framework-only workaround that preserves a legacy
 * pre-[skbug.com/40041022](https://skbug.com/40041022) behaviour where
 * the CPU backend treated A8 bitmaps as coverage rather than alpha. The
 * fix made CPU and GPU consistent (alpha-as-alpha) but altered the visual
 * for some Android apps ([b/231400686](https://issuetracker.google.com/231400686)).
 *
 * The expected output is the mandrill image with a round-rect border :
 * the white-filled-then-clear-roundrect A8 mask used as the source for an
 * sk-clear blend should erase the *border pixels* only, not the whole
 * mandrill. Without the Android workaround the mandrill is entirely
 * cleared (full coverage everywhere the mask alpha is non-zero).
 *
 * **STUB.INTRACTABLE** : the upstream behaviour the GM verifies is gated
 * by a compile-time `#define` that flips Skia's CPU A8-handling pipeline
 * to a non-default mode. `:kanvas-skia` has no equivalent toggle — our
 * A8 source path treats the bitmap as alpha (the post-fix behaviour), so
 * porting the body verbatim would render the *post*-workaround visual
 * (fully-erased mandrill), not the *workaround*-on visual the reference
 * documents. We port the body to keep the call sites compiled and the
 * test class is `@Disabled` with the STUB.INTRACTABLE classification ;
 * activating it would require a per-bitmap "legacy coverage" tag in the
 * device draw path. Also note that there is no `alpha_bitmap_is_coverage_ANDROID.png`
 * reference in `original-888/` — upstream's reference renders skip the
 * gated path, so even with a perfect port there is no fixture to compare
 * against.
 *
 * C++ original :
 * ```cpp
 * DEF_SIMPLE_GM(alpha_bitmap_is_coverage_ANDROID, canvas, 128, 128) {
 *   SkBitmap maskBitmap;
 *   maskBitmap.allocPixels(SkImageInfo::MakeA8(128, 128));
 *   {
 *     SkCanvas maskCanvas(maskBitmap);
 *     maskCanvas.clear(SK_ColorWHITE);
 *     SkPaint maskPaint;
 *     maskPaint.setAntiAlias(true);
 *     maskPaint.setColor(SK_ColorWHITE);
 *     maskPaint.setBlendMode(SkBlendMode::kClear);
 *     maskCanvas.drawRoundRect({0, 0, 128, 128}, 16, 16, maskPaint);
 *   }
 *   SkBitmap offscreenBitmap;
 *   offscreenBitmap.allocN32Pixels(128, 128);
 *   {
 *     SkCanvas offscreenCanvas(offscreenBitmap);
 *     offscreenCanvas.drawImage(ToolUtils::GetResourceAsImage("images/mandrill_128.png"), 0, 0);
 *     SkPaint clearPaint;
 *     clearPaint.setAntiAlias(true);
 *     clearPaint.setBlendMode(SkBlendMode::kClear);
 *     offscreenCanvas.drawImage(maskBitmap.asImage(), 0, 0, SkSamplingOptions{}, &clearPaint);
 *   }
 *   canvas->drawImage(offscreenBitmap.asImage(), 0, 0);
 * }
 * ```
 */
public class AlphaBitmapIsCoverageAndroidGM : GM() {

    override fun getName(): String = "alpha_bitmap_is_coverage_ANDROID"

    override fun getISize(): SkISize = SkISize.Make(128, 128)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // ── Mask bitmap : white A8 surface, kClear round-rect punches a hole ──
        val maskBitmap = SkBitmap.allocPixels(SkImageInfo.MakeA8(128, 128))
        run {
            val maskCanvas = SkCanvas(maskBitmap)
            maskCanvas.clear(SK_ColorWHITE)

            val maskPaint = SkPaint()
            maskPaint.isAntiAlias = true
            maskPaint.color = SK_ColorWHITE
            maskPaint.blendMode = SkBlendMode.kClear
            maskCanvas.drawRoundRect(
                SkRect.MakeXYWH(0f, 0f, 128f, 128f),
                16f,
                16f,
                maskPaint,
            )
        }

        // ── Offscreen : draw mandrill, then erase with the A8 mask ──
        val offscreenBitmap = SkBitmap.allocPixels(SkImageInfo.MakeN32Premul(128, 128))
        run {
            val offscreenCanvas = SkCanvas(offscreenBitmap)
            val mandrill = ToolUtils.GetResourceAsImage("images/mandrill_128.png")
            if (mandrill != null) {
                offscreenCanvas.drawImage(mandrill, 0f, 0f)
            }

            val clearPaint = SkPaint()
            clearPaint.isAntiAlias = true
            clearPaint.blendMode = SkBlendMode.kClear
            offscreenCanvas.drawImage(
                maskBitmap.asImage(),
                0f,
                0f,
                SkSamplingOptions.Default,
                clearPaint,
            )
        }

        c.drawImage(offscreenBitmap.asImage(), 0f, 0f)
    }
}
