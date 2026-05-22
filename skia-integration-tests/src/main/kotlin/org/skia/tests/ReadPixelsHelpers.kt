package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorSpacePrimaries
import org.skia.foundation.SkColorSpaceTransferFn
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkImages
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Shared helpers for the three GMs ported from
 * [`gm/readpixels.cpp`](https://github.com/google/skia/blob/main/gm/readpixels.cpp) :
 * [ReadPixelsGM], [ReadPixelsCodecGM] and [ReadPixelsPictureGM].
 *
 * All three exercise [SkImage.readPixels] across a Cartesian product of
 * destination `(SkColorType, SkAlphaType, SkColorSpace)` tuples, then
 * re-draw the readback bitmap tagged as sRGB so the caller can eyeball
 * the round-trip fidelity.
 *
 * ## Port notes vs upstream
 *
 *  - **`make_wide_gamut` / `make_small_gamut`** : the upstream parametric
 *    transfer function uses `g = 1.8` (gamma 1.8) on custom primaries.
 *    We mirror that via [SkColorSpace.MakeRGB] taking an explicit
 *    [SkColorSpaceTransferFn] / [SkColorSpacePrimaries] pair.
 *  - **`draw_image`** : the C++ allocates an [SkData] via
 *    `MakeUninitialized`, writes via `readPixels(... writable_data())`,
 *    then materialises an sRGB-tagged image via `RasterFromData`. The
 *    Kotlin port substitutes a heap [ByteBuffer] (kanvas-skia's [SkData]
 *    is immutable), then funnels the buffer back through
 *    [SkImages.RasterFromData].
 *  - **F16 destination** : upstream's `kRGBA_F16_SkColorType` row exists
 *    for the GPU path. `:kanvas-skia`'s raster [SkImage.readPixels]
 *    cannot write F16 (no pixmap writer) and
 *    [SkImages.RasterFromData] does not yet accept F16 either. We treat
 *    F16-dst rows as a no-op (matches the upstream fallback when
 *    `readPixels` returns false : `memset(data, 0, ...)` followed by an
 *    all-zero draw — a one-byte alpha-channel blue square). Tracked as
 *    a follow-up.
 *  - **GPU context** : upstream uploads the source to a texture when a
 *    `GrDirectContext` is available, then calls `image->readPixels(ctx,
 *    ...)`. The raster facade has no [GrDirectContext], so we always
 *    take the host-memory path.
 */
internal object ReadPixelsHelpers {

    public const val kWidth: Int = 64
    public const val kHeight: Int = 64

    /**
     * Mirrors `make_parametric_transfer_fn(primaries)` (upstream lines
     * 81-86) — a parametric `g = 1.8` transfer-function colour space on
     * [primaries].
     */
    public fun makeParametricTransferFn(primaries: SkColorSpacePrimaries): SkColorSpace {
        val tf = SkColorSpaceTransferFn(g = 1.8f, a = 1f, b = 0f, c = 0f, d = 0f, e = 0f, f = 0f)
        return SkColorSpace.MakeRGB(tf, primaries)
            ?: error("ReadPixelsHelpers: makeParametricTransferFn returned null for $primaries")
    }

    /**
     * Mirrors upstream's `make_wide_gamut()` — ProPhoto primaries
     * (`0.7347/0.2653, 0.1596/0.8404, 0.0366/0.0001`) with a `D50`-ish
     * white point and the shared `g = 1.8` TF.
     */
    public fun makeWideGamut(): SkColorSpace = makeParametricTransferFn(
        SkColorSpacePrimaries(
            fRX = 0.7347f, fRY = 0.2653f,
            fGX = 0.1596f, fGY = 0.8404f,
            fBX = 0.0366f, fBY = 0.0001f,
            fWX = 0.34567f, fWY = 0.35850f,
        ),
    )

    /**
     * Mirrors upstream's `make_small_gamut()` — narrow-gamut primaries
     * with a D65 white point and the shared `g = 1.8` TF.
     */
    public fun makeSmallGamut(): SkColorSpace = makeParametricTransferFn(
        SkColorSpacePrimaries(
            fRX = 0.50f, fRY = 0.33f,
            fGX = 0.30f, fGY = 0.50f,
            fBX = 0.25f, fBY = 0.16f,
            fWX = 0.3127f, fWY = 0.3290f,
        ),
    )

    /**
     * Mirrors upstream's `draw_image(dContext, canvas, image, dstColorType,
     * dstAlphaType, dstColorSpace, hint)` (lines 115-130).
     *
     *  1. Allocate a host buffer sized for `image.width × image.height ×
     *     bytesPerPixel(dstColorType)`.
     *  2. Call `image.readPixels(dstInfo, ...)`. On failure, zero the
     *     buffer — matches the upstream `memset(data->writable_data(),
     *     0, ...)` fallback.
     *  3. Re-tag the readback as sRGB and draw it at `(0, 0)`.
     *
     * Caching-hint is plumbed through but currently ignored — kanvas-skia
     * has no codec-image pixel cache to bypass.
     */
    public fun drawImage(
        canvas: SkCanvas,
        image: SkImage,
        dstColorType: SkColorType,
        dstAlphaType: SkAlphaType,
        dstColorSpace: SkColorSpace,
    ) {
        val bpp = bytesPerPixelOrNull(dstColorType)
        if (bpp == null) {
            // F16 (or any other unsupported readback) — upstream's
            // failure path : zero buffer + draw nothing (the source
            // image still progresses through the column translate).
            return
        }
        val rowBytes = image.width * bpp
        val totalBytes = rowBytes * image.height
        val buffer: ByteBuffer = ByteBuffer.allocate(totalBytes).order(ByteOrder.LITTLE_ENDIAN)

        val dstInfo = SkImageInfo.Make(
            width = image.width,
            height = image.height,
            colorType = dstColorType,
            alphaType = dstAlphaType,
            colorSpace = dstColorSpace,
        )

        val ok = image.readPixels(dstInfo, buffer, rowBytes, 0, 0)
        if (!ok) {
            // Mirror `memset(data->writable_data(), 0, ...)`. allocate()
            // already returned a zero-filled buffer, so nothing to do —
            // but we still draw an all-zero square so the cell is
            // visually distinct (matches upstream).
            for (i in 0 until totalBytes) buffer.put(i, 0)
        }

        // Re-tag as sRGB and rasterise as an SkImage so canvas can blit
        // it. RasterFromData only supports 8888 / BGRA / Alpha8 — for
        // unsupported colour types we silently no-op (matches the
        // failure path).
        val srgb = SkColorSpace.makeSRGB()
        val srgbInfo = dstInfo.makeColorSpace(srgb)
        val raw = SkImages.RasterFromData(srgbInfo, buffer, rowBytes) ?: return
        canvas.drawImage(raw, 0f, 0f)
    }

    /**
     * Bytes per pixel for colour types that [SkImages.RasterFromData] /
     * [SkImage.readPixels] can round-trip. Returns `null` for everything
     * else — F16, 565, 4444, Gray8, … . The caller (see [drawImage])
     * treats `null` as "skip this cell".
     */
    private fun bytesPerPixelOrNull(ct: SkColorType): Int? = when (ct) {
        SkColorType.kRGBA_8888, SkColorType.kBGRA_8888 -> 4
        SkColorType.kAlpha_8 -> 1
        else -> null
    }
}
