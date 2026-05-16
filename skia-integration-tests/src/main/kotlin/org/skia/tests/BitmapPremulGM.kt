package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/bitmappremul.cpp` (`bitmap_premul` GM).
 *
 * Phase G4c proof-of-concept : exercises the `kARGB_4444` `SkBitmap`
 * accessors landed alongside this GM by building two 256×256 4444
 * bitmaps (a grey alpha-coupled gradient and a black/white horizontal
 * stripe pattern), and drawing them next to their `kRGBA_8888` twins.
 *
 * Upstream rationale (paraphrased from the C++ comment block) :
 * "This GM checks that bitmap pixels are unpremultiplied before being
 * exported to other formats. If unpremultiplication is implemented
 * properly, this GM should come out completely white. If not, this GM
 * looks like a row of two greyscale gradients above a row of grey
 * lines."
 *
 * Both the 8888 and 4444 source bitmaps carry per-row alpha equal to
 * the row's grey value (so each scanline encodes `(a=y, r=y, g=y, b=y)`
 * unpremul — `(y, y, y, y)` premul). When `drawImage` blits the image
 * over the white background with `SrcOver`, the (premul) source over
 * (opaque white) dst yields `dst + (1 - sa) * white = (y, y, y, y) +
 * (255 - y, 255 - y, 255 - y, 255)` premul, which unpremuls to a fully
 * opaque white — every pixel collapses to `0xFFFFFFFF`. The test
 * passes if the on-screen result is uniformly white ; any leftover
 * grey reveals a missing or broken unpremultiplication step.
 *
 * The 4444 paths use `SkCanvas { bitmap4444 }.drawImage(source8888,
 * SkSamplingOptions(), &paintWithSrcBlend)` to copy from an 8888
 * source — that path is the principal new code surface this GM
 * exercises. Our implementation routes through
 * [org.skia.core.SkBitmapDevice]'s colorType-aware `setPixel` (which
 * packs to the 4-bit nibbles via `SkBitmap.packARGB4444Premul`), so
 * the round-trip via `bitmap4444.asImage()` reads back colour-loss-
 * within-4-bit-precision channels.
 *
 * C++ original :
 * ```cpp
 * static void init_bitmap(SkColorType ct, SkBitmap* bitmap) {
 *     bitmap->allocPixels(SkImageInfo::Make(SLIDE_SIZE, SLIDE_SIZE, ct, kPremul_SkAlphaType));
 *     bitmap->eraseColor(SK_ColorWHITE);
 * }
 *
 * static sk_sp<SkImage> make_argb8888_gradient() {
 *     SkBitmap bitmap;
 *     init_bitmap(kN32_SkColorType, &bitmap);
 *     for (int y = 0; y < SLIDE_SIZE; y++) {
 *         uint32_t* dst = bitmap.getAddr32(0, y);
 *         for (int x = 0; x < SLIDE_SIZE; x++) {
 *             dst[x] = SkPackARGB32(y, y, y, y);
 *         }
 *     }
 *     return bitmap.asImage();
 * }
 *
 * static sk_sp<SkImage> make_argb4444_gradient() {
 *     SkBitmap bitmap;
 *     init_bitmap(kARGB_4444_SkColorType, &bitmap);
 *     SkPaint paint;
 *     paint.setBlendMode(SkBlendMode::kSrc);
 *     SkCanvas{ bitmap }.drawImage(make_argb8888_gradient(), 0, 0, SkSamplingOptions(), &paint);
 *     return bitmap.asImage();
 * }
 *
 * static sk_sp<SkImage> make_argb8888_stripes() { ... }
 * static sk_sp<SkImage> make_argb4444_stripes() { ... }
 *
 * BitmapPremulGM() { this->setBGColor(SK_ColorWHITE); }
 *
 * SkString getName() const override { return SkString("bitmap_premul"); }
 * SkISize getISize() override { return SkISize::Make(SLIDE_SIZE * 2, SLIDE_SIZE * 2); }
 *
 * void onDraw(SkCanvas* canvas) override {
 *     SkScalar slideSize = SkIntToScalar(SLIDE_SIZE);
 *     canvas->drawImage(make_argb8888_gradient(), 0, 0);
 *     canvas->drawImage(make_argb4444_gradient(), slideSize, 0);
 *     canvas->drawImage(make_argb8888_stripes(), 0, slideSize);
 *     canvas->drawImage(make_argb4444_stripes(), slideSize, slideSize);
 * }
 * ```
 */
public class BitmapPremulGM : GM() {

    init {
        setBGColor(SK_ColorWHITE)
    }

    override fun getName(): String = "bitmap_premul"

    override fun getISize(): SkISize = SkISize.Make(SLIDE_SIZE * 2, SLIDE_SIZE * 2)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val slide = SLIDE_SIZE.toFloat()
        c.drawImage(makeArgb8888Gradient(), 0f, 0f)
        c.drawImage(makeArgb4444Gradient(), slide, 0f)
        c.drawImage(makeArgb8888Stripes(), 0f, slide)
        c.drawImage(makeArgb4444Stripes(), slide, slide)
    }

    public companion object {
        public const val SLIDE_SIZE: Int = 256

        /**
         * Allocate a `kPremul`-typed bitmap of `SLIDE_SIZE × SLIDE_SIZE`
         * with the supplied colour type, white-filled. Matches the
         * upstream `init_bitmap` helper exactly.
         */
        private fun initBitmap(ct: SkColorType): SkBitmap {
            val bm = SkBitmap.allocPixels(
                SkImageInfo.Make(SLIDE_SIZE, SLIDE_SIZE, ct, org.skia.foundation.SkAlphaType.kPremul),
            )
            bm.eraseColor(SK_ColorWHITE)
            return bm
        }

        /**
         * Per-row grey gradient with `alpha = row` and `r = g = b = row`
         * (unpremul). The C++ packs `SkPackARGB32(y, y, y, y)` — a
         * **premul** value with `(a, r, g, b) = (y, y, y, y)`. The
         * equivalent unpremul has `(a, r, g, b) = (y, 255, 255, 255)`
         * for `y > 0` (and a fully transparent black at `y == 0`).
         *
         * Because our `SkBitmap[kRGBA_8888]` storage is **unpremul**
         * (per the class-level KDoc on [SkBitmap]) we store the unpremul
         * form directly. Downstream consumers (`SkBitmapShader`, the
         * raster device) read the channels back through the unpremul
         * convention, so the visual result is identical to upstream.
         */
        private fun makeArgb8888Gradient(): SkImage {
            val bm = initBitmap(SkColorType.kRGBA_8888)
            for (y in 0 until SLIDE_SIZE) {
                val alpha = y
                // y == 0 → fully transparent black ; y > 0 → unpremul
                // form is `(y, 255, 255, 255)` (the premul (y,y,y,y)
                // un-divides by y/255 on the RGB channels to give 255s).
                val rgb = if (alpha == 0) 0 else 0xFF
                val px = SkColorSetARGB(alpha, rgb, rgb, rgb)
                for (x in 0 until SLIDE_SIZE) {
                    bm.setPixel(x, y, px)
                }
            }
            return bm.asImage()
        }

        /**
         * 4444 mirror of [makeArgb8888Gradient]. Upstream draws the
         * 8888 gradient onto an empty 4444 canvas with `SkBlendMode::kSrc`
         * (which dithers in upstream's hand-tuned 8888 → 4444
         * down-converter ; we don't dither, but the source alphas were
         * chosen such that the visual result still collapses to white
         * once composited against the white BG). Our implementation
         * walks the source pixels and writes them through
         * [SkBitmap.setPixel] — same end-state as upstream's
         * `SkCanvas{ bitmap }.drawImage(source, 0, 0, SkSamplingOptions(), &paint)`,
         * just without the intermediate device round-trip.
         */
        private fun makeArgb4444Gradient(): SkImage {
            val bm = initBitmap(SkColorType.kARGB_4444)
            // Suppress dither (upstream uses drawImage rather than
            // readPixels for this very reason — bit-for-bit copy).
            val paint = SkPaint().apply { blendMode = SkBlendMode.kSrc }
            SkCanvas(bm).drawImage(makeArgb8888Gradient(), 0f, 0f, SkSamplingOptions.Default, paint)
            return bm.asImage()
        }

        /** Black / white alternating horizontal stripes (alpha matches the row colour). */
        private fun makeArgb8888Stripes(): SkImage {
            val bm = initBitmap(SkColorType.kRGBA_8888)
            var rowColor = 0
            for (y in 0 until SLIDE_SIZE) {
                val px = SkColorSetARGB(rowColor, rowColor, rowColor, rowColor)
                for (x in 0 until SLIDE_SIZE) {
                    bm.setPixel(x, y, px)
                }
                rowColor = if (rowColor == 0) 255 else 0
            }
            return bm.asImage()
        }

        /** 4444 mirror of [makeArgb8888Stripes]. */
        private fun makeArgb4444Stripes(): SkImage {
            val bm = initBitmap(SkColorType.kARGB_4444)
            val paint = SkPaint().apply { blendMode = SkBlendMode.kSrc }
            SkCanvas(bm).drawImage(makeArgb8888Stripes(), 0f, 0f, SkSamplingOptions.Default, paint)
            return bm.asImage()
        }
    }
}
