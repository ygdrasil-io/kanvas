package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * R-final.S — **STUB.GPU_RRECT_EFFECT** consumer GM. Port of Skia's
 * `gm/rrects.cpp` — `RRectGM(kEffect_Type)`.
 *
 * GM name: `rrect_effect`. This variant drives the Ganesh-internal
 * `GrRRectEffect` fragment processor directly through APIs that do not
 * exist in our WebGPU / CPU-raster pipeline. The body throws [TODO] tagged
 * **STUB.GPU_RRECT_EFFECT** so the compile contract holds.
 */
/**
 * Port of Skia's `gm/rrects.cpp` (RRectGM with effect type).
 * Stub for Ganesh-internal GrRRectEffect fragment processor.
 * @see https://github.com/google/skia/blob/main/gm/rrects.cpp
 */
class RRectEffectGm : SkiaGm {
    override val name = "rrect_effect"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        TODO("STUB.GPU_RRECT_EFFECT")
    }
}
