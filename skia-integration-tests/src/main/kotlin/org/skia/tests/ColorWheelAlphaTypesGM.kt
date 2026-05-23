package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * `gm/colorwheel.cpp::DEF_SIMPLE_GM(colorwheel_alphatypes, canvas, 256, 128)`.
 *
 * Compares premul-then-filter vs filter-then-premul (unpremul) image draws.
 * The upstream GM loads `images/color_wheel.png` twice — once hinting
 * `kPremul_SkAlphaType` and once `kUnpremul_SkAlphaType` — then blows up
 * a tiny 8×8 edge crop (at pixel 12, 102) to a 128×128 destination. The
 * premul version looks correct (smooth transition to the white background),
 * while the unpremul version exhibits a dark fringe because the implicit
 * black of transparent pixels is mixed in during filtering.
 *
 * **Fidelity gap** : `SkImages::DeferredFromEncodedData(data, alphaType)` with
 * an explicit alpha-type override is not surfaced in `:kanvas-skia` —
 * [ToolUtils.GetResourceAsImage] uses the codec's natural alpha type for both
 * slots. Both cells therefore render identically (both premul-filtered), so
 * the dark-fringe diagnostic in the right cell is absent. The canvas structure
 * (white background, 2-cell side-by-side at 128×128 each) is preserved.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(colorwheel_alphatypes, canvas, 256, 128) {
 *     canvas->clear(SK_ColorWHITE);
 *
 *     sk_sp<SkData> imgData = GetResourceAsData("images/color_wheel.png");
 *
 *     auto pmImg  = ToolUtils::MakeTextureImage(
 *             canvas, SkImages::DeferredFromEncodedData(imgData, kPremul_SkAlphaType));
 *     auto upmImg = ToolUtils::MakeTextureImage(
 *             canvas, SkImages::DeferredFromEncodedData(imgData, kUnpremul_SkAlphaType));
 *
 *     SkSamplingOptions linear{SkFilterMode::kLinear};
 *
 *     SkRect srcRect = SkRect::MakeXYWH(12, 102, 8, 8);
 *     SkRect dstRect = SkRect::MakeLTRB(0, 0, 128, 128);
 *
 *     canvas->drawImageRect(pmImg,  srcRect, dstRect,
 *                           linear, nullptr, SkCanvas::kFast_SrcRectConstraint);
 *     canvas->drawImageRect(upmImg, srcRect, dstRect.makeOffset(128, 0),
 *                           linear, nullptr, SkCanvas::kFast_SrcRectConstraint);
 * }
 * ```
 */
public class ColorWheelAlphaTypesGM : GM() {

    override fun getName(): String = "colorwheel_alphatypes"
    override fun getISize(): SkISize = SkISize.Make(256, 128)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorWHITE)

        // Both images are decoded with the codec's natural alpha type —
        // :kanvas-skia does not surface a per-decode alpha-type override.
        val pmImg  = ToolUtils.GetResourceAsImage("images/color_wheel.png") ?: return
        val upmImg = ToolUtils.GetResourceAsImage("images/color_wheel.png") ?: return

        val linear = SkSamplingOptions.linear()

        // Tiny 8×8 section at the transparent edge of the wheel, blown up
        // to 128×128 so filter-tap bleed is clearly visible.
        val srcRect = SkRect.MakeXYWH(12f, 102f, 8f, 8f)
        val dstRect = SkRect.MakeLTRB(0f, 0f, 128f, 128f)

        // Left cell — upstream: premul image (filter-then-premul looks correct).
        c.drawImageRect(pmImg,  srcRect, dstRect, linear, null, SrcRectConstraint.kFast)
        // Right cell — upstream: unpremul image (filter-in-unpremul → dark fringe).
        c.drawImageRect(upmImg, srcRect, dstRect.makeOffset(128f, 0f), linear, null, SrcRectConstraint.kFast)
    }
}
