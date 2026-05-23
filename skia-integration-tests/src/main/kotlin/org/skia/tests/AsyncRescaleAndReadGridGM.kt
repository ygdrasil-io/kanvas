package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia
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
 * via `SkSurface::asyncRescaleAndReadPixels` (RGBA) or
 * `SkSurface::asyncRescaleAndReadPixelsYUV420` /
 * `asyncRescaleAndReadPixelsYUVA420` (YUV/YUVA).
 *
 * This representative stub covers the `async_rescale_and_read_rose`
 * variant (surface, RGBA, 3×410×410 grid) and flags the entire family
 * as blocked on the async-readback API.
 *
 * TODO: missing API — `SkSurface.asyncRescaleAndReadPixels` and
 * `SkSurface.asyncRescaleAndReadPixelsYUV420` /
 * `asyncRescaleAndReadPixelsYUVA420`.  Flag-planting stub for all 8
 * variants.
 */
public class AsyncRescaleAndReadGridGM : GM() {
    override fun getName(): String = "async_rescale_and_read_rose"
    // 3 columns × 410 px wide, 2 rows × 410 px tall
    override fun getISize(): SkISize = SkISize.Make(3 * 410, 2 * 410)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO("STUB.ASYNC_RESCALE_READ") — requires SkSurface.asyncRescaleAndReadPixels.
    }
}
