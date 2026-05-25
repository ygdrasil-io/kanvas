package org.skia.tests

import org.graphiks.math.SkIRect
import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Focused RGBA CPU port of upstream Skia
 * `gm/asyncrescaleandread.cpp::AsyncRescaleAndReadNoBleedGM`.
 *
 * Original verifies that the async rescale+readback pipeline does not
 * bleed pixels across rescale tile boundaries.
 */
public class AsyncRescaleAndReadNoBleedGM : GM() {
    override fun getName(): String = "async_rescale_and_read_no_bleed"
    override fun getISize(): SkISize = SkISize.Make(120, 60)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val surface = SkSurface.MakeRaster(
            SkImageInfo.Make(12, 6, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul, SkColorSpace.MakeSRGB()),
        )
        surface.canvas.drawRect(SkRect.MakeWH(12f, 6f), SkPaint(0xFFFF0000.toInt()))
        surface.canvas.drawRect(SkRect.MakeXYWH(6f, 0f, 6f, 6f), SkPaint(0xFF0000FF.toInt()))

        val info = SkImageInfo.Make(120, 60, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul, SkColorSpace.MakeSRGB())
        surface.asyncRescaleAndReadPixels(
            info = info,
            srcRect = SkIRect.MakeLTRB(0, 0, 12, 6),
            rescaleMode = SkSurface.RescaleMode.kRepeatedLinear,
        ) { result ->
            drawAsyncReadResult(c, result, info, 0f, 0f)
        }
    }
}
