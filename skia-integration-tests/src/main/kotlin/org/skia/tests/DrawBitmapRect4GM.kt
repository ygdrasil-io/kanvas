package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/bitmaprect.cpp` (`DrawBitmapRect4`).
 *
 * Builds a 4096×4096 bitmap (white 10-px border on a semi-transparent red
 * interior) and blits two source rects into the canvas using XOR blending
 * at 50 % alpha. This tests that the GPU back-end can correctly tile a
 * very large texture without introducing stripes from imprecise destination
 * tile placement.
 *
 * The two variants differ in how the source rect is passed:
 *  - **[Variant.FLOAT]** (`bigbitmaprect_s`) — passes `srcR` as a float
 *    [SkRect]. Mirrors `!fUseIRect` in C++.
 *  - **[Variant.INT]** (`bigbitmaprect_i`) — passes `SkRect.Make(srcR.roundOut())`
 *    built from the rounded-out IRect. Mirrors `fUseIRect` in C++.
 *
 * C++ original (`gm/bitmaprect.cpp`, `DrawBitmapRect4`, non-Android only):
 * ```cpp
 * SkString getName() const override {
 *     SkString str;
 *     str.printf("bigbitmaprect_%s", fUseIRect ? "i" : "s");
 *     return str;
 * }
 * SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     SkPaint paint;
 *     paint.setAlpha(128);
 *     paint.setBlendMode(SkBlendMode::kXor);
 *     SkSamplingOptions sampling;
 *     SkRect srcR1 = { 0.0f, 0.0f, 4096.0f, 2040.0f };
 *     SkRect dstR1 = { 10.1f, 10.1f, 629.9f, 400.9f };
 *     SkRect srcR2 = { 4085.0f, 10.0f, 4087.0f, 12.0f };
 *     SkRect dstR2 = { 10, 410, 30, 430 };
 *     if (!fUseIRect) {
 *         canvas->drawImageRect(fBigImage, srcR1, dstR1, sampling, &paint, kStrict);
 *         canvas->drawImageRect(fBigImage, srcR2, dstR2, sampling, &paint, kStrict);
 *     } else {
 *         canvas->drawImageRect(fBigImage, SkRect::Make(srcR1.roundOut()), dstR1, sampling, &paint, kStrict);
 *         canvas->drawImageRect(fBigImage, SkRect::Make(srcR2.roundOut()), dstR2, sampling, &paint, kStrict);
 *     }
 * }
 * ```
 *
 * Note: upstream guards these GMs with `#ifndef SK_BUILD_FOR_ANDROID`. We
 * port them unconditionally — the large bitmap is built on demand in
 * [onDraw] to defer the allocation until actually needed.
 */
public class DrawBitmapRect4GM(
    private val variant: Variant,
) : GM() {

    public enum class Variant(internal val suffix: String) {
        /** `bigbitmaprect_s` — float src rects (`!fUseIRect`). */
        FLOAT("s"),
        /** `bigbitmaprect_i` — IRect-derived src rects (`fUseIRect`). */
        INT("i"),
    }

    init {
        setBGColor(SkColorSetARGB(0x88, 0x44, 0x44, 0x44))
    }

    override fun getName(): String = "bigbitmaprect_${variant.suffix}"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    private var bigImage: SkImage? = null

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        if (bigImage == null) {
            bigImage = makeBigBitmap()
        }
        val img = bigImage ?: return

        val paint = SkPaint().apply {
            alpha = 128
            blendMode = SkBlendMode.kXor
        }
        val sampling = SkSamplingOptions.Default

        val srcR1 = SkRect.MakeLTRB(0.0f, 0.0f, 4096.0f, 2040.0f)
        val dstR1 = SkRect.MakeLTRB(10.1f, 10.1f, 629.9f, 400.9f)

        val srcR2 = SkRect.MakeLTRB(4085.0f, 10.0f, 4087.0f, 12.0f)
        val dstR2 = SkRect.MakeLTRB(10f, 410f, 30f, 430f)

        when (variant) {
            Variant.FLOAT -> {
                c.drawImageRect(img, srcR1, dstR1, sampling, paint, SrcRectConstraint.kStrict)
                c.drawImageRect(img, srcR2, dstR2, sampling, paint, SrcRectConstraint.kStrict)
            }
            Variant.INT -> {
                c.drawImageRect(img, SkRect.Make(srcR1.roundOut()), dstR1, sampling, paint,
                    SrcRectConstraint.kStrict)
                c.drawImageRect(img, SkRect.Make(srcR2.roundOut()), dstR2, sampling, paint,
                    SrcRectConstraint.kStrict)
            }
        }
    }

    private companion object {
        private const val gXSize = 4096
        private const val gYSize = 4096
        private const val gBorderWidth = 10

        /**
         * Build the 4096×4096 test image. The border (10px wide) is set to
         * semi-transparent white (`0x88FFFFFF` pre-multiplied) while the interior
         * is semi-transparent red (`0x88FF0000` pre-multiplied). Uses
         * [SkColorSetARGB] to approximate `SkPreMultiplyColor` — both store the
         * colour in premultiplied ARGB order.
         */
        fun makeBigBitmap(): SkImage {
            val borderColor = SkColorSetARGB(0x88, 0xFF, 0xFF, 0xFF)
            val interiorColor = SkColorSetARGB(0x88, 0xFF, 0x00, 0x00)
            val bitmap = SkBitmap(gXSize, gYSize)
            for (y in 0 until gYSize) {
                for (x in 0 until gXSize) {
                    val isBorder = x <= gBorderWidth || x >= gXSize - gBorderWidth ||
                        y <= gBorderWidth || y >= gYSize - gBorderWidth
                    bitmap.setPixel(x, y, if (isBorder) borderColor else interiorColor)
                }
            }
            return bitmap.asImage()
        }
    }
}
