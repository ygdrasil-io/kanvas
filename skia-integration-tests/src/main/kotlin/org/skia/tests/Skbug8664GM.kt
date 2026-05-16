package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/skbug_8664.cpp::skbug_8664`
 * (`DEF_SIMPLE_GM(skbug_8664, canvas, 830, 550)`).
 *
 * Repro for Adreno 330 mipmap-generation × scissor interference
 * (skbug.com/8664) : an image is drawn at four progressively smaller
 * scales (1, 0.5, 0.25, 0.125) under medium sampling
 * (`kLinear`/`kLinear`), each followed by a half-transparent overlay
 * rect that is rotated *inside* a non-rotated clip — the clip cannot
 * be folded into the draw bounds, so the GPU must use the scissor
 * test. The bug was that mipmap regeneration mid-draw stomped the
 * scissor state.
 *
 * On the CPU raster sink there is no scissor and no mipmap path —
 * we simply downsample under bilinear filtering — so the GM lands
 * as a static regression check for the image-draw + clip + rotate
 * + drawRect sequence rather than the bug it was authored for.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(skbug_8664, canvas, 830, 550) {
 *     const struct {
 *         SkScalar    fSx, fSy, fTx, fTy;
 *     } xforms[] = {
 *         { 1, 1, 0, 0 },
 *         { 0.5f, 0.5f, 530, 0 },
 *         { 0.25f, 0.25f, 530, 275 },
 *         { 0.125f, 0.125f, 530, 420 },
 *     };
 *     SkSamplingOptions sampling(SkFilterMode::kLinear, SkMipmapMode::kLinear);
 *     sk_sp<SkImage> image(ToolUtils::GetResourceAsImage("images/mandrill_512.png"));
 *     SkPaint overlayPaint;
 *     overlayPaint.setColor(0x80FFFFFF);
 *     canvas->clear(0xFF888888);
 *     canvas->translate(20, 20);
 *     for (const auto& xform : xforms) {
 *         canvas->save();
 *         canvas->translate(xform.fTx, xform.fTy);
 *         canvas->scale(xform.fSx, xform.fSy);
 *         canvas->drawImage(image, 0, 0, sampling, nullptr);
 *         SkRect inner = SkRect::MakeLTRB(32.f, 32.f, 480.f, 480.f);
 *         SkRect outer = inner.makeOutset(16.f, 16.f);
 *         canvas->save();
 *         canvas->clipRect(inner);
 *         canvas->rotate(20.f);
 *         canvas->drawRect(outer, overlayPaint);
 *         canvas->restore();
 *         canvas->restore();
 *     }
 * }
 * ```
 */
public class Skbug8664GM : GM() {

    private data class Xform(val sx: Float, val sy: Float, val tx: Float, val ty: Float)

    override fun getName(): String = "skbug_8664"
    override fun getISize(): SkISize = SkISize.Make(830, 550)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val xforms = listOf(
            Xform(1f, 1f, 0f, 0f),
            Xform(0.5f, 0.5f, 530f, 0f),
            Xform(0.25f, 0.25f, 530f, 275f),
            Xform(0.125f, 0.125f, 530f, 420f),
        )

        // Must be at least medium to require mipmaps when we downscale the image
        val sampling = SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kLinear)

        val image = ToolUtils.GetResourceAsImage("images/mandrill_512.png") ?: return

        val overlayPaint = SkPaint().apply { color = 0x80FFFFFF.toInt() }

        // Make the overlay visible even when the downscaled images fail to render
        c.clear(0xFF888888.toInt())

        c.translate(20f, 20f)
        for (xform in xforms) {
            c.save()
            c.translate(xform.tx, xform.ty)
            c.scale(xform.sx, xform.sy)

            // Draw an image, possibly down sampled.
            c.drawImage(image, 0f, 0f, sampling, null)

            // Draw an overlay that requires the scissor test for its clipping.
            val inner = SkRect.MakeLTRB(32f, 32f, 480f, 480f)
            val outer = inner.makeOutset(16f, 16f)

            // Clip to smaller rectangle
            c.save()
            c.clipRect(inner)
            // Then apply a rotation and draw a larger rectangle to ensure the clip cannot be dropped
            c.rotate(20f)
            c.drawRect(outer, overlayPaint)
            c.restore()

            c.restore()
        }
    }
}
