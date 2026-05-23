package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * R-final.S — **STUB.GPU_RRECT_EFFECT** consumer GM. Port of Skia's
 * `gm/rrects.cpp` — `RRectGM(kEffect_Type)`.
 *
 * GM name: `rrect_effect`. This variant drives the Ganesh-internal
 * `GrRRectEffect` fragment processor directly through `SurfaceDrawContext`
 * and `GrClipEdgeType` — APIs that do not exist in our WebGPU / CPU-raster
 * pipeline. The body throws [TODO] tagged **STUB.GPU_RRECT_EFFECT** so the
 * compile contract holds; [RRectEffectTest] is `@Disabled`.
 *
 * See upstream `gm/rrects.cpp` (`kEffect_Type` branch in `onDraw`).
 */
public class RRectEffectGM : GM() {

    init { setBGColor(0xFFDDDDDD.toInt()) }

    override fun getName(): String = "rrect_effect"
    override fun getISize(): SkISize = SkISize.Make(kImageWidth, kImageHeight)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GPU_RRECT_EFFECT")
    }
}
