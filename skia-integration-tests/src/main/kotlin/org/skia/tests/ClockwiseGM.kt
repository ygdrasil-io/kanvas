package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/clockwise.cpp::ClockwiseGM`.
 *
 * This is a GPU-backend specific test. It ensures that SkSL properly identifies
 * clockwise-winding triangles (`sk_Clockwise`), in terms of Skia device space,
 * in all backends and with all render target origins. Clockwise triangles are
 * drawn green and counter-clockwise red.
 *
 * GM name: `clockwise`. Canvas size: 300 × 200.
 *
 * The upstream implementation (`gm/clockwise.cpp`) builds two private Ganesh
 * types — `ClockwiseTestProcessor` (a `GrGeometryProcessor` that emits SkSL
 * reading `sk_Clockwise`) and `ClockwiseTestOp` (a `GrDrawOp`) — and then
 * exercises them via `SurfaceDrawContext::addDrawOp` on:
 *   1. The framebuffer directly (default surface origin).
 *   2. An off-screen top-left (`kTopLeft_GrSurfaceOrigin`) render target.
 *   3. An off-screen bottom-up (default `kBottomLeft_GrSurfaceOrigin`) render target.
 *
 * All of these APIs (`GrGeometryProcessor`, `GrDrawOp`, `SurfaceDrawContext`,
 * `GrRecordingContext`) are Ganesh-internal and have no equivalent in the
 * kanvas-skia raster / WebGPU backend.
 *
 * C++ original `onDraw` (abbreviated):
 * ```cpp
 * DrawResult ClockwiseGM::onDraw(GrRecordingContext* rContext, SkCanvas* canvas,
 *                                SkString* errorMsg) {
 *     auto sdc = skgpu::ganesh::TopDeviceSurfaceDrawContext(canvas);
 *     if (!sdc) {
 *         *errorMsg = kErrorMsg_DrawSkippedGpuOnly;
 *         return DrawResult::kSkip;
 *     }
 *     sdc->clear(SK_PMColor4fBLACK);
 *     sdc->addDrawOp(ClockwiseTestOp::Make(rContext, false, 0));
 *     sdc->addDrawOp(ClockwiseTestOp::Make(rContext, true, 100));
 *     // ... off-screen top-left and bottom-up render targets ...
 *     return DrawResult::kOk;
 * }
 * ```
 *
 * Tracked as STUB.GPU_CLOCKWISE.
 */
public class ClockwiseGM : GM() {

    override fun getName(): String = "clockwise"
    override fun getISize(): SkISize = SkISize.Make(300, 200)

    override fun onDraw(canvas: SkCanvas?) {
        // GPU-only GM — requires GrGeometryProcessor (ClockwiseTestProcessor),
        // GrDrawOp (ClockwiseTestOp), SurfaceDrawContext::addDrawOp, and
        // GrRecordingContext. None of these exist in the kanvas-skia raster /
        // WebGPU pipeline. The upstream GM returns kSkip on any non-Ganesh
        // context.
        TODO("STUB.GPU_CLOCKWISE")
    }
}
