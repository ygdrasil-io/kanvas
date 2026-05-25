package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.core.SkSurface.RescaleGamma
import org.skia.core.SkSurface.RescaleMode
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkImages
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPixmap
import org.skia.foundation.SkSamplingOptions
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
 * variant (surface, RGBA, 3×410×410 grid). The YUV / YUVA variants stay
 * dependency-gated and are not scheduled from this RGBA GM.
 */
public class AsyncRescaleAndReadGridGM : GM() {
    override fun getName(): String = "async_rescale_and_read_rose"

    // 3 columns × 410 px wide, 2 rows × 410 px tall
    override fun getISize(): SkISize = SkISize.Make(3 * 410, 2 * 410)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val surface = SkSurface.MakeRaster(
            SkImageInfo.Make(
                410, 410,
                SkColorType.kRGBA_8888,
                SkAlphaType.kUnpremul,
                SkColorSpace.MakeSRGB(),
            ),
        )
        drawSource(surface.canvas, 410f, 410f)

        // 3 × 2 grid : columns ∈ {kNearest, kRepeatedLinear, kRepeatedCubic},
        // rows ∈ {kSrc, kLinear}. The CPU fallback invokes callbacks
        // synchronously, so each cell is drawn during the loop.
        val modes = listOf(
            RescaleMode.kNearest,
            RescaleMode.kRepeatedLinear,
            RescaleMode.kRepeatedCubic,
        )
        for ((row, gamma) in listOf(RescaleGamma.kSrc, RescaleGamma.kLinear).withIndex()) {
            for ((col, mode) in modes.withIndex()) {
                val info = SkImageInfo.Make(
                    410, 410,
                    SkColorType.kRGBA_8888,
                    SkAlphaType.kUnpremul,
                    SkColorSpace.MakeSRGB(),
                )
                surface.asyncRescaleAndReadPixels(
                    info = info,
                    srcRect = SkIRect.MakeLTRB(0, 0, 410, 410),
                    rescaleGamma = gamma,
                    rescaleMode = mode,
                ) { result ->
                    drawAsyncReadResult(c, result, info, x = col * 410f, y = row * 410f)
                }
            }
        }
    }

    private fun drawSource(c: SkCanvas, w: Float, h: Float) {
        c.drawRect(SkRect.MakeWH(w, h), SkPaint(0xFFFFFFFF.toInt()))
        c.drawRect(SkRect.MakeXYWH(0f, 0f, w / 2f, h / 2f), SkPaint(0xFFFF5555.toInt()))
        c.drawRect(SkRect.MakeXYWH(w / 2f, 0f, w / 2f, h / 2f), SkPaint(0xFF55FF55.toInt()))
        c.drawRect(SkRect.MakeXYWH(0f, h / 2f, w / 2f, h / 2f), SkPaint(0xFF5555FF.toInt()))
        c.drawRect(SkRect.MakeXYWH(w / 2f, h / 2f, w / 2f, h / 2f), SkPaint(0xFFFFFF55.toInt()))
    }
}

internal fun drawAsyncReadResult(
    canvas: SkCanvas,
    result: SkSurface.AsyncReadResult?,
    info: SkImageInfo,
    x: Float,
    y: Float,
) {
    val actual = result ?: return
    val pixmap = SkPixmap(
        info,
        ByteBuffer.wrap(actual.data(0)).order(ByteOrder.LITTLE_ENDIAN),
        actual.rowBytes(0),
    )
    val image = SkImages.RasterFromPixmapCopy(pixmap) ?: return
    canvas.drawImage(image, x, y, SkSamplingOptions.Default)
}
