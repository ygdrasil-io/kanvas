package org.skia.tests

import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint

/**
 * Focused RGBA CPU port of upstream Skia
 * `gm/asyncrescaleandread.cpp::AsyncRescaleAndReadAlphaTypeGM`.
 *
 * Original exercises the async pixel-read + rescale pipeline
 * (`SkSurface::asyncRescaleAndReadPixels`) across alpha types
 * (`kPremul`, `kUnpremul`). Designed to verify GPU readback round-trip
 * conformance.
 */
public class AsyncRescaleAndReadAlphaTypeGM : GM() {
    override fun getName(): String = "async_rescale_and_read_alpha_type"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val alphaTypes = listOf(SkAlphaType.kPremul, SkAlphaType.kUnpremul)
        for ((row, alphaType) in alphaTypes.withIndex()) {
            val surface = SkSurface.MakeRaster(
                SkImageInfo.Make(64, 64, SkColorType.kRGBA_8888, alphaType, SkColorSpace.MakeSRGB()),
            )
            surface.canvas.drawRect(SkRect.MakeWH(64f, 64f), SkPaint(0x8000AAFF.toInt()))
            surface.canvas.drawRect(SkRect.MakeXYWH(16f, 16f, 32f, 32f), SkPaint(0xFFFF5500.toInt()))

            val info = SkImageInfo.Make(256, 256, SkColorType.kRGBA_8888, alphaType, SkColorSpace.MakeSRGB())
            surface.asyncRescaleAndReadPixels(
                info = info,
                srcRect = SkIRect.MakeLTRB(0, 0, 64, 64),
                rescaleMode = SkSurface.RescaleMode.kRepeatedLinear,
            ) { result ->
                drawAsyncReadResult(c, result, info, 0f, row * 256f)
            }
        }
    }
}
