package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSurfaces
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/bitmaprect.cpp` (`DrawBitmapRect2`).
 *
 * Draws a 64×64 image (red background + white-to-blue linear gradient circle)
 * four times — one for each source rect in [src] — mapping each into the same
 * destination rect `dstR`. The two variants differ in how the source rect is
 * passed to [SkCanvas.drawImageRect]:
 *
 *  - **[Variant.FLOAT]** (`bitmaprect_s`) — passes `srcR` as an [SkRect]
 *    (`float` coordinates). Mirrors `!fUseIRect` in C++.
 *  - **[Variant.INT]** (`bitmaprect_i`) — passes `SkRect.Make(src[i])` built
 *    from the original [SkIRect]. Mirrors `fUseIRect` in C++.
 *
 * C++ original (`gm/bitmaprect.cpp`, `DrawBitmapRect2`):
 * ```cpp
 * SkString getName() const override {
 *     SkString str;
 *     str.printf("bitmaprect_%s", fUseIRect ? "i" : "s");
 *     return str;
 * }
 * SkISize getISize() override { return SkISize::Make(640, 480); }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     canvas->drawColor(0xFFCCCCCC);
 *     const SkIRect src[] = { {0,0,32,32}, {0,0,80,80}, {32,32,96,96}, {-32,-32,32,32} };
 *     SkPaint paint;
 *     paint.setStyle(SkPaint::kStroke_Style);
 *     auto sampling = SkSamplingOptions();
 *     auto image = make_image(canvas);
 *     SkRect dstR = { 0, 200, 128, 380 };
 *     canvas->translate(16, 40);
 *     for (size_t i = 0; i < std::size(src); i++) {
 *         SkRect srcR;
 *         srcR.set(src[i]);
 *         canvas->drawImage(image, 0, 0, sampling, &paint);
 *         if (!fUseIRect) {
 *             canvas->drawImageRect(image.get(), srcR, dstR, sampling, &paint,
 *                                   SkCanvas::kStrict_SrcRectConstraint);
 *         } else {
 *             canvas->drawImageRect(image.get(), SkRect::Make(src[i]), dstR, sampling, &paint,
 *                                   SkCanvas::kStrict_SrcRectConstraint);
 *         }
 *         canvas->drawRect(dstR, paint);
 *         canvas->drawRect(srcR, paint);
 *         canvas->translate(160, 0);
 *     }
 * }
 * ```
 */
public class DrawBitmapRect2GM(
    private val variant: Variant,
) : GM() {

    public enum class Variant(internal val suffix: String) {
        /** `bitmaprect_s` — float src rect (`!fUseIRect`). */
        FLOAT("s"),
        /** `bitmaprect_i` — IRect-derived src rect (`fUseIRect`). */
        INT("i"),
    }

    override fun getName(): String = "bitmaprect_${variant.suffix}"
    override fun getISize(): SkISize = SkISize.Make(640, 480)

    private var image: SkImage? = null

    override fun onOnceBeforeDraw() {
        image = makeImage()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.drawColor(0xFFCCCCCC.toInt())

        val srcs = arrayOf(
            SkIRect.MakeLTRB(0, 0, 32, 32),
            SkIRect.MakeLTRB(0, 0, 80, 80),
            SkIRect.MakeLTRB(32, 32, 96, 96),
            SkIRect.MakeLTRB(-32, -32, 32, 32),
        )

        val paint = SkPaint().apply { style = SkPaint.Style.kStroke_Style }
        val sampling = SkSamplingOptions.Default
        val img = image ?: return
        val dstR = SkRect.MakeLTRB(0f, 200f, 128f, 380f)

        c.translate(16f, 40f)
        for (src in srcs) {
            val srcR = SkRect.Make(src)

            c.drawImage(img, 0f, 0f, sampling, paint)
            when (variant) {
                Variant.FLOAT -> c.drawImageRect(
                    img, srcR, dstR, sampling, paint, SrcRectConstraint.kStrict,
                )
                Variant.INT -> c.drawImageRect(
                    img, SkRect.Make(src), dstR, sampling, paint, SrcRectConstraint.kStrict,
                )
            }
            c.drawRect(dstR, paint)
            c.drawRect(srcR, paint)
            c.translate(160f, 0f)
        }
    }

    private companion object {
        /**
         * Build the 64×64 reference image. Mirrors the `make_image()` helper in
         * `bitmaprect.cpp`: red background, white-to-blue linear gradient circle
         * at radius 32.
         *
         * Upstream uses `ToolUtils::MakeTextureImage` for the final snapshot;
         * in the raster back-end that is a no-op, so we just use `makeImageSnapshot`.
         */
        fun makeImage(): SkImage {
            val surface = SkSurfaces.Raster(SkImageInfo.MakeN32Premul(64, 64))
                ?: error("Could not create 64x64 raster surface")
            val tmp = surface.canvas

            tmp.drawColor(SK_ColorRED)

            val pts = arrayOf(SkPoint.Make(0f, 0f), SkPoint.Make(64f, 64f))
            val colors = intArrayOf(SK_ColorWHITE, SK_ColorBLUE)
            val shader = SkLinearGradient.Make(pts[0], pts[1], colors, null, SkTileMode.kClamp)

            val paint = SkPaint().apply {
                isAntiAlias = true
                this.shader = shader
            }
            tmp.drawCircle(32f, 32f, 32f, paint)

            return surface.makeImageSnapshot()
        }
    }
}
