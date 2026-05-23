package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/convexpolyeffect.cpp::ConvexPolyEffect`.
 *
 * GM name: `convex_poly_effect`. This GM directly exercises the Ganesh-internal
 * `GrConvexPolyEffect` fragment processor. It builds a set of convex polygons
 * (triangle, reversed triangle, closed triangle, max-edge n-gon, scaled n-gon,
 * and a degenerate line) and for each path iterates over all `GrClipEdgeType`
 * values, calls `GrConvexPolyEffect::Make(nullptr, edgeType, path)`, and
 * draws a covering rectangle via `SurfaceDrawContext::addDrawOp` with the
 * resulting fragment processor as coverage.
 *
 * **Ganesh GrEffect — INTRACTABLE**
 *
 * This GM requires the following Ganesh-internal APIs that have no equivalent
 * in the kanvas-skia CPU / WebGPU pipeline:
 *
 *  - `GrConvexPolyEffect` (`src/gpu/ganesh/effects/GrConvexPolyEffect.h`) —
 *    a `GrFragmentProcessor` that clips a polygon using half-plane equations
 *    baked into the GPU shader. It carries `GrConvexPolyEffect::kMaxEdges`
 *    (8) limiting the n-gon size.
 *  - `GrClipEdgeType` / `kGrClipEdgeTypeCnt` (`include/private/gpu/ganesh/GrTypesPriv.h`) —
 *    enum driving fill / inverse-fill / AA / BW variants.
 *  - `GrFragmentProcessor` / `GrPaint::setCoverageFragmentProcessor` —
 *    GPU fragment processor graph.
 *  - `SurfaceDrawContext` / `TopDeviceSurfaceDrawContext` / `addDrawOp` —
 *    low-level Ganesh draw op submission, skipping the public SkCanvas API.
 *  - `GrPorterDuffXPFactory` — Ganesh transfer-function / xfer-processor.
 *  - `sk_gpu_test::test_ops::MakeRect` — internal GPU test-only rect op.
 *
 * None of these are surfaced through `include/core/`, the public Skia API, or
 * the kanvas-skia WebGPU backend. Implementing this GM would require a full
 * Ganesh GPU context; it is classified as **INTRACTABLE** for the CPU-raster
 * and WebGPU targets.
 *
 * TODO("STUB.GPU_CONVEX_POLY_EFFECT")
 *
 * See upstream `gm/convexpolyeffect.cpp` for the full C++ implementation.
 */
public class ConvexPolyEffectGM : GM() {

    init { setBGColor(0xFFFFFFFF.toInt()) }

    override fun getName(): String = "convex_poly_effect"
    override fun getISize(): SkISize = SkISize.Make(720, 550)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GPU_CONVEX_POLY_EFFECT")
    }
}
