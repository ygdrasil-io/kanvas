package org.skia.tests

import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorTRANSPARENT
import org.graphiks.math.SkISize
import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint

/**
 * Port of Skia's
 * [`gm/aaclip.cpp::cgimage`](https://github.com/google/skia/blob/main/gm/aaclip.cpp)
 * (800 × 250).
 *
 * Upstream is a **Mac-only** GM (`#ifdef SK_BUILD_FOR_MAC`) that
 * exercises Skia's CoreGraphics interop helpers
 * ([`SkCreateCGImageRefWithColorspace`](https://github.com/google/skia/blob/main/include/utils/mac/SkCGUtils.h),
 * `SkCreateBitmapFromCGImage`, `SkMakeImageFromCGImage`) by :
 *
 *  1. Allocating a `100 × 100` [SkBitmap] at one of 7 [SkColorType] /
 *     [SkAlphaType] combinations (565/Opaque, 8888/{Premul, Unpremul,
 *     Opaque} ×{RGBA, BGRA}).
 *  2. Erasing it to `kGreen` (opaque configs) or `0` (transparent),
 *     wrapping it with a direct-raster canvas, and drawing a single
 *     anti-aliased blue circle at `(50, 50, r = 49)`.
 *  3. Round-tripping the bitmap through `CGImageRef` :
 *     * `SkCreateCGImageRefWithColorspace(bm, nullptr)` → `CGImageRef`.
 *     * `SkCreateBitmapFromCGImage(&bm2, image)` → fresh `SkBitmap`.
 *     * `SkMakeImageFromCGImage(image)` → fresh `SkImage`.
 *  4. Stamping all three (`bm`, `bm2`, `cgImage`) into a vertical
 *     `(10, 10) – (10, 120) – (10, 240)` column, then translating
 *     `info.width + 10` (`= 110`) to the right for the next config.
 *
 * Expected output : a `7 × 3` grid of identical "blue disc on green /
 * transparent square" tiles — the CG round-trip is conceptually
 * lossless for every config Skia compiles in.
 *
 * ## Port mapping
 *
 * `:kanvas-skia` has **no CoreGraphics interop** — the
 * `SkCGUtils.h` API set is platform-specific to upstream's Skia build
 * and not exposed through our [org.skia.foundation.SkImages] surface.
 * Re-implementing the round-trip natively would require a JNI bridge
 * to `CoreGraphics.framework` (`CGImageCreate`,
 * `CGBitmapContextCreateImage`, …) ; both directions of the
 * conversion happen to be **identity** for the 6 colour-type /
 * alpha-type combinations that compile on macOS, so we model the
 * round-trip semantically as a **bitmap copy** — the same blue disc is
 * drawn into the bitmap once and the second / third rows replay the
 * same content from copies of that buffer.
 *
 * ## Backend constraints
 *
 * The raster surface ([SkSurface.MakeRasterDirect]) infers `kUnpremul`
 * for `kRGBA_8888` and `kPremul` for `kRGBA_F16Norm` ; it does **not**
 * vend a raster device for `kRGB_565` / `kBGRA_8888`. To still produce
 * the 7-cell row layout, every cell drives the draw through an
 * `kRGBA_8888 / kPremul` working bitmap and then converts (per-pixel
 * via [SkBitmap.setPixel]) to the requested storage type when the
 * upstream config specifies one of the unsupported backends. This
 * keeps the visual output identical for the supported configs and a
 * "best-effort with the same disc geometry" for the unsupported ones —
 * good enough for the cross-test similarity bucket, sufficient to
 * exercise the [SkSurface.MakeRasterDirect] + circle-AA path that the
 * GM was originally validating on the CPU side.
 *
 * ## Test bucket
 *
 * Classified `STUB.MISSING_API` — the upstream test's *point* is the
 * CGImage interop, which we cannot model. The raster output here
 * shows the right disc geometry per cell but doesn't carry the colour
 * type round-trip semantics, so the produced image will diverge
 * heavily from `cgimage.png`. Test is kept ACTIVE behind a very
 * permissive similarity floor so the ratchet can track regressions
 * in the raster circle-AA path.
 */
public class CgimageGM : GM() {

    override fun getName(): String = "cgimage"

    override fun getISize(): SkISize = SkISize.Make(800, 250)

    /**
     * Mirrors upstream's anonymous `rec[]` table — the seven
     * `(SkColorType, SkAlphaType)` configurations the GM iterates over.
     */
    private data class Rec(val colorType: SkColorType, val alphaType: SkAlphaType)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val recs = listOf(
            Rec(SkColorType.kRGB_565, SkAlphaType.kOpaque),

            Rec(SkColorType.kRGBA_8888, SkAlphaType.kPremul),
            Rec(SkColorType.kRGBA_8888, SkAlphaType.kUnpremul),
            Rec(SkColorType.kRGBA_8888, SkAlphaType.kOpaque),

            Rec(SkColorType.kBGRA_8888, SkAlphaType.kPremul),
            Rec(SkColorType.kBGRA_8888, SkAlphaType.kUnpremul),
            Rec(SkColorType.kBGRA_8888, SkAlphaType.kOpaque),
        )

        for (rec in recs) {
            val info = SkImageInfo.Make(100, 100, rec.colorType, rec.alphaType)
            testImage(c, info)
            c.translate(info.width.toFloat() + 10f, 0f)
        }
    }

    /**
     * Mirrors upstream's `test_image(SkCanvas* canvas, const SkImageInfo&)`.
     *
     * Builds the disc bitmap once, stamps three copies (original,
     * "CGImage round-trip bitmap", "CGImage-backed SkImage") into a
     * vertical column at `(10, 10) - (10, 120) - (10, 230)`. The
     * round-trip is modelled as a copy of the storage backing the
     * original — see class KDoc.
     */
    private fun testImage(canvas: SkCanvas, info: SkImageInfo) {
        // Step 1 — allocate the destination bitmap. Storage type is
        // whatever the caller specified (565 / RGBA / BGRA, any alpha
        // type). The actual draw happens through a kRGBA_8888 working
        // bitmap when the requested type can't host a raster surface.
        val bm = SkBitmap.allocPixels(info)
        if (info.isOpaque()) {
            bm.eraseColor(SK_ColorGREEN)
        } else {
            bm.eraseColor(SK_ColorTRANSPARENT)
        }

        // Step 2 — draw the blue circle into the bitmap. If the
        // requested colour type cannot back a raster surface, draw
        // through a working kRGBA_8888 bitmap, then copy the result
        // back via per-pixel `setPixel`.
        val paint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorBLUE
        }
        if (canBackRasterSurface(info.colorType)) {
            val surface = SkSurface.MakeRasterDirect(bm)
            surface.canvas.drawCircle(50f, 50f, 49f, paint)
        } else {
            // 565 / BGRA path : render through an RGBA_8888 working
            // bitmap, then transcribe pixels through [SkBitmap.setPixel]
            // (which honours the destination colour-type's premul /
            // quantise rules — mirrors the `copy_to` helper used in
            // AllBitmapConfigsGM).
            val workInfo = SkImageInfo.Make(
                info.width, info.height,
                SkColorType.kRGBA_8888,
                if (info.isOpaque()) SkAlphaType.kUnpremul else SkAlphaType.kPremul,
            )
            val work = SkBitmap.allocPixels(workInfo)
            if (info.isOpaque()) {
                work.eraseColor(SK_ColorGREEN)
            } else {
                work.eraseColor(SK_ColorTRANSPARENT)
            }
            val workSurface = SkSurface.MakeRasterDirect(work)
            workSurface.canvas.drawCircle(50f, 50f, 49f, paint)
            for (y in 0 until info.height) {
                for (x in 0 until info.width) {
                    bm.setPixel(x, y, work.getPixel(x, y))
                }
            }
        }

        // Step 3 — stamp the three rows. The two "round-trip" rows are
        // copies of `bm` (CG conversion is conceptually identity for
        // the configs that compile in upstream).
        canvas.drawImage(bm.asImage(), 10f, 10f)

        val bm2 = copyBitmap(bm, info)
        canvas.drawImage(bm2.asImage(), 10f, 120f)

        // Upstream stacks the third row at `10 + 120 + bm2.height() + 10
        // = 240`. With `bm2.height() == 100` that's exactly `230` from
        // the second row's origin, matching the GM's 250-tall canvas.
        canvas.drawImage(bm2.asImage(), 10f, 120f + bm2.height + 10f)
    }

    /**
     * `kRGBA_8888` and `kRGBA_F16Norm` are the two colour types
     * [SkSurface.MakeRasterDirect] knows how to host a [org.skia.core.SkCanvas]
     * over (see its private `inferAlphaType`). Anything else (`kRGB_565`,
     * `kBGRA_8888`, `kAlpha_8`, `kGray_8`, `kARGB_4444`) collapses
     * `inferAlphaType` to `kUnknown` and the surface refuses to draw.
     */
    private fun canBackRasterSurface(ct: SkColorType): Boolean = when (ct) {
        SkColorType.kRGBA_8888, SkColorType.kRGBA_F16Norm -> true
        else -> false
    }

    /**
     * Models the CGImage round-trip
     * (`SkCreateCGImageRefWithColorspace` → `SkCreateBitmapFromCGImage`)
     * as a bitmap-to-bitmap copy preserving [info]. Pixel-by-pixel so
     * the destination's premul / quantise rules apply — same trick as
     * `copy_to` in [AllBitmapConfigsGM].
     */
    private fun copyBitmap(src: SkBitmap, info: SkImageInfo): SkBitmap {
        val dst = SkBitmap.allocPixels(info)
        for (y in 0 until info.height) {
            for (x in 0 until info.width) {
                dst.setPixel(x, y, src.getPixel(x, y))
            }
        }
        return dst
    }
}
