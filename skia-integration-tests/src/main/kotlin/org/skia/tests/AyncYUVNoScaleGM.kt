package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.core.SkSurface.RescaleGamma
import org.skia.core.SkSurface.RescaleMode
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.graphiks.math.SkColorSetARGB

/**
 * Placeholder port of upstream Skia
 * `gm/asyncrescaleandread.cpp::AyncYUVNoScaleGM` (note typo in upstream
 * name: "Aync" not "Async").
 *
 * Original verifies async YUV[A] read-back without rescale, exercising
 * `SkSurface::asyncRescaleAndReadPixelsYUV420` at scale 1.0.
 *
 * TODO: missing API — `SkSurface.asyncRescaleAndReadPixelsYUV420`.
 * Flag-planting stub.
 */
public class AyncYUVNoScaleGM : GM() {
    override fun getName(): String = "async_yuv_no_scale"
    override fun getISize(): SkISize = SkISize.Make(400, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val surface = SkSurface.MakeRaster(
            SkImageInfo.Make(400, 300, SkColorType.kRGBA_8888, SkAlphaType.kUnpremul, SkColorSpace.MakeSRGB()),
        )
        surface.canvas.drawRect(SkRect.MakeWH(400f, 300f), SkPaint(0xFFFFFFFF.toInt()))
        surface.canvas.drawRect(SkRect.MakeXYWH(30f, 20f, 140f, 80f), SkPaint(0xFFFF0000.toInt()))
        surface.canvas.drawRect(SkRect.MakeXYWH(200f, 30f, 120f, 100f), SkPaint(0xFF00FF00.toInt()))
        surface.canvas.drawRect(SkRect.MakeXYWH(100f, 150f, 240f, 120f), SkPaint(0xFF0000FF.toInt()))

        surface.asyncRescaleAndReadPixelsYUV420(
            yuvColorSpace = SkSurface.SkYUVColorSpace.kJPEG_Full_YUV,
            dstColorSpace = SkColorSpace.MakeSRGB(),
            srcRect = SkIRect.MakeWH(400, 300),
            dstSize = SkISize.Make(400, 300),
            rescaleGamma = RescaleGamma.kSrc,
            rescaleMode = RescaleMode.kNearest,
        ) { result ->
            val r = result ?: return@asyncRescaleAndReadPixelsYUV420
            val y = r.data(0)
            var i = 0
            for (py in 0 until 300) {
                for (px in 0 until 400) {
                    val luma = y[i++].toInt() and 0xFF
                    c.drawRect(
                        SkRect.MakeXYWH(px.toFloat(), py.toFloat(), 1f, 1f),
                        SkPaint(SkColorSetARGB(255, luma, luma, luma)),
                    )
                }
            }
        }
    }
}
