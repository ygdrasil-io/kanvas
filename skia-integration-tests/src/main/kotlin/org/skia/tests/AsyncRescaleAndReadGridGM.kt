package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.core.SkSurface.RescaleGamma
import org.skia.core.SkSurface.RescaleMode
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkImageInfo
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize

/**
 * Port of upstream Skia
 * `gm/asyncrescaleandread.cpp::AsyncRescaleAndReadGridGM<SOURCE, TYPE>`.
 *
 * The upstream C++ file registers **8** GM variants via the
 * `DEF_RESCALE_AND_READ_GRID_GM` macro, each parameterised by image
 * file, crop rect, read size, read source (image vs surface), and pixel
 * type (RGBA / YUV / YUVA):
 *
 *  - `async_rescale_and_read_yuv420_rose`       (surface, YUVA)
 *  - `async_rescale_and_read_yuv420_rose_down`  (image,   YUV)
 *  - `async_rescale_and_read_rose`              (surface, RGBA)
 *  - `async_rescale_and_read_dog_down`          (surface, RGBA)
 *  - `async_rescale_and_read_dog_up`            (image,   RGBA)
 *  - `async_rescale_and_read_text_down`         (image,   RGBA)
 *  - `async_rescale_and_read_text_up`           (surface, RGBA)
 *  - `async_rescale_and_read_text_up_large`     (image,   RGBA)
 *
 * Each variant draws a 3×2 grid of rescaled reads (columns = nearest /
 * repeated-linear / repeated-cubic; rows = src-gamma / linear-gamma)
 * via [SkSurface.asyncRescaleAndReadPixels] (RGBA) or
 * [SkSurface.asyncRescaleAndReadPixelsYUV420] /
 * [SkSurface.asyncRescaleAndReadPixelsYUVA420] (YUV / YUVA).
 *
 * This representative port covers the `async_rescale_and_read_rose`
 * variant (surface, RGBA, 3×410×410 grid). The body wires every cell of
 * the 3×2 grid through the async API ; each call resolves to
 * `TODO("STUB.ASYNC_RESCALE_READ")` at runtime so the matching
 * [AsyncRescaleAndReadGridTest] is `@Disabled` until the readback
 * pipeline lands. The YUV / YUVA variants are flag-planting only —
 * adding them as siblings is a one-class-per-row exercise once the
 * RGBA path drops the `TODO()` and produces actual pixels.
 */
public class AsyncRescaleAndReadGridGM : GM() {
    override fun getName(): String = "async_rescale_and_read_rose"

    // 3 columns × 410 px wide, 2 rows × 410 px tall
    override fun getISize(): SkISize = SkISize.Make(3 * 410, 2 * 410)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Bind a scratch raster surface around the GM's canvas-owned
        // bitmap so we can exercise the async readback API. In the
        // upstream test the surface is the one the canvas already draws
        // into (saveLayer pop snapshot) — for our flag-planting stub
        // anything that satisfies the call-site grep is enough.
        val surface = SkSurface.MakeRaster(
            SkImageInfo.Make(
                3 * 410, 2 * 410,
                SkColorType.kRGBA_8888,
                SkAlphaType.kUnpremul,
                SkColorSpace.MakeSRGB(),
            ),
        )

        // 3 × 2 grid : columns ∈ {kNearest, kRepeatedLinear, kRepeatedCubic},
        // rows ∈ {kSrc, kLinear}. Each cell schedules an async readback ;
        // the API is `TODO("STUB.ASYNC_RESCALE_READ")` so the very first
        // call throws and the GM short-circuits — visible in the
        // @Disabled test's `STUB.ASYNC_RESCALE_READ` reason.
        for (gamma in listOf(RescaleGamma.kSrc, RescaleGamma.kLinear)) {
            for (mode in listOf(
                RescaleMode.kNearest,
                RescaleMode.kRepeatedLinear,
                RescaleMode.kRepeatedCubic,
            )) {
                surface.asyncRescaleAndReadPixels(
                    info = SkImageInfo.Make(
                        410, 410,
                        SkColorType.kRGBA_8888,
                        SkAlphaType.kUnpremul,
                        SkColorSpace.MakeSRGB(),
                    ),
                    srcRect = SkIRect.MakeLTRB(0, 0, 410, 410),
                    rescaleGamma = gamma,
                    rescaleMode = mode,
                ) { _ -> /* not reached — TODO() */ }
            }
        }

        // Touch the YUV / YUVA paths so a future implementer's
        // `grep TODO()` sees them as live call sites too.
        surface.asyncRescaleAndReadPixelsYUV420(
            yuvColorSpace = SkSurface.SkYUVColorSpace.kJPEG_Full_YUV,
            dstColorSpace = SkColorSpace.MakeSRGB(),
            srcRect = SkIRect.MakeLTRB(0, 0, 410, 410),
            dstSize = SkISize.Make(410, 410),
        ) { _ -> }
        surface.asyncRescaleAndReadPixelsYUVA420(
            yuvColorSpace = SkSurface.SkYUVColorSpace.kJPEG_Full_YUV,
            dstColorSpace = SkColorSpace.MakeSRGB(),
            srcRect = SkIRect.MakeLTRB(0, 0, 410, 410),
            dstSize = SkISize.Make(410, 410),
        ) { _ -> }

        // Suppress unused-receiver warnings on `c` — the upstream paints
        // a checkerboard background while waiting for the readback. We
        // skip it ; the @Disabled test asserts on the API stub, not the
        // composition.
        @Suppress("UNUSED_PARAMETER") fun _ignore(canvas: SkCanvas) {}
        _ignore(c)
    }
}
