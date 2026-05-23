package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's
 * [`gm/fpcoordinateoverride.cpp`](https://github.com/google/skia/blob/main/gm/fpcoordinateoverride.cpp)
 * — registered as `DEF_SIMPLE_GPU_GM_BG(fpcoordinateoverride, …, 512, 512, …)`.
 *
 * ## What the GM does
 *
 * The GM exercises Ganesh's `GrFragmentProcessor` coordinate-override
 * feature. A custom `SampleCoordEffect` fragment processor wraps an
 * image `GrFragmentProcessor` (`GrTextureEffect`) and samples it twice
 * using explicit `sk_FragCoord` coordinates — once forward
 * (`float2(sk_FragCoord.x, sk_FragCoord.y)`) and once with a
 * vertically-mirrored Y (`float2(sk_FragCoord.x, 512-sk_FragCoord.y)`)
 * — then blends the two samples 50/50 and fills the entire 512 × 512
 * surface via `SurfaceFillContext::fillWithFP`.
 *
 * ## Why INTRACTABLE
 *
 * Every step of the rendering pipeline is deeply Ganesh-internal:
 *
 *  - **`skgpu::ganesh::TopDeviceSurfaceFillContext`** — obtains a
 *    Ganesh-private `SurfaceFillContext` from the canvas's backing
 *    device. This type lives in `src/gpu/ganesh/SurfaceFillContext.h`,
 *    far outside the public Skia API surface.
 *
 *  - **`GrFragmentProcessor` / `ProgramImpl`** — the `SampleCoordEffect`
 *    subclass emits raw GLSL via `GrGLSLFPFragmentBuilder::codeAppendf`
 *    and uses `invokeChild(0, args, "float2(…)")` (the explicit-sample-
 *    coordinate override). Both `GrFragmentProcessor` and
 *    `GrGLSLFPFragmentBuilder` are Ganesh GPU-pipeline types with no
 *    equivalent in `:kanvas-skia`'s CPU/WebGPU path.
 *
 *  - **`GrTextureEffect`** — creates a GPU texture-based fragment
 *    processor from a `GrSurfaceProxyView`. Requires a live
 *    `GrDirectContext`.
 *
 *  - **`GrMippedBitmap` / `GrMakeCachedBitmapProxyView`** — Ganesh-
 *    private helpers that upload a CPU `SkBitmap` into a GPU texture.
 *
 *  - **`sfc->fillWithFP(fp)`** — issues a GPU draw that fills the
 *    entire surface with the fragment-processor pipeline. No equivalent
 *    in the CPU raster or WebGPU pipeline.
 *
 * There is no way to emulate `GrFragmentProcessor` coordinate overrides
 * on the CPU raster backend. The closest CPU analogue would be a pair
 * of `SkShader::makeWithLocalMatrix` + `SkShaders::Blend`, but that
 * does not reproduce the sub-pixel GLSL coordinate logic.
 *
 * TODO: STUB.GR_FRAGMENT_PROCESSOR — fpcoordinateoverride requires
 * GrFragmentProcessor / SurfaceFillContext (Ganesh GPU-only);
 * not available in the CPU/WebGPU pipeline.
 */
public class FpCoordinateOverrideGM : GM() {

    init {
        // Upstream background: ToolUtils::color_to_565(0xFF66AA99)
        // Approximated as the nearest ARGB8888 representation.
        setBGColor(0xFF669999.toInt())
    }

    override fun getName(): String = "fpcoordinateoverride"

    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GR_FRAGMENT_PROCESSOR: fpcoordinateoverride requires GrFragmentProcessor + SurfaceFillContext (Ganesh GPU-only)")
    }
}
