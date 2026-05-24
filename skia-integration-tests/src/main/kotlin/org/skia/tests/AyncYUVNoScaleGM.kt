package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

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
        TODO("STUB.ASYNC_RESCALE_READ_YUV: SkSurface.asyncRescaleAndReadPixelsYUV420 not yet implemented")
    }
}
