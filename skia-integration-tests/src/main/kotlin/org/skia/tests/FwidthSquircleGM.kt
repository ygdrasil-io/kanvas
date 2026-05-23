package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of Skia's
 * [`gm/fwidth_squircle.cpp`](https://github.com/google/skia/blob/main/gm/fwidth_squircle.cpp).
 *
 * ## What the upstream GM does
 *
 * `DEF_SIMPLE_GPU_GM_CAN_FAIL(fwidth_squircle, rContext, canvas, errorMsg, 200, 200)`
 *
 * Verifies that the GLSL `fwidth()` screen-space derivative built-in works
 * correctly on Ganesh GPU configs by rendering an anti-aliased squircle.
 *
 * The entire rendering pipeline is Ganesh-internal:
 *
 * 1. **`FwidthSquircleTestProcessor`** — a custom `GrGeometryProcessor` that
 *    emits a vertex attribute `bboxcoord` (float2) and a view-matrix uniform.
 *    The vertex shader applies a tiny rotation (`cos(.05)/sin(.05)` matrix)
 *    and maps the quad to clip space.  The fragment shader computes the
 *    squircle function:
 *    ```glsl
 *    float fn = pow(abs(x), golden_ratio*pi) + pow(abs(y), golden_ratio*pi) - 1;
 *    float fnwidth = fwidth(fn);         // ← key: screen-space derivative
 *    half coverage = clamp(.5 - fn/fnwidth, 0, 1);
 *    ```
 *    and outputs a fixed colour `half4(.51, .42, .71, 1) * .89` at that
 *    coverage.
 *
 * 2. **`FwidthSquircleTestOp`** — a custom `GrDrawOp` that allocates a
 *    4-vertex strip (`{-1,-1}, {+1,-1}, {-1,+1}, {+1,+1}`), creates a
 *    `GrProgramInfo` with `kTriangleStrip`, and records the draw call
 *    directly onto the `SurfaceDrawContext`.
 *
 * 3. The GM guard: if `shaderCaps.fShaderDerivativeSupport` is false the GM
 *    returns `kSkip`.
 *
 * ## Why this is INTRACTABLE in kanvas-skia
 *
 * The entire draw path depends on Ganesh-private APIs:
 * - `GrGeometryProcessor` / `GrGeometryProcessor::ProgramImpl`
 * - `GrDrawOp` / `GrOpFlushState` / `GrProgramInfo`
 * - `GrGLSLVertexGeoBuilder` / `GrGLSLFragmentShaderBuilder`
 * - `GrGLSLVarying` / `GrGLSLUniformHandler`
 * - `SurfaceDrawContext::addDrawOp`
 * - `sk_gpu_test::CreateProgramInfo`
 *
 * `:kanvas-skia` is a raster-only backend; none of these GPU pipeline types
 * exist.  The upstream GM explicitly returns `kSkip` when called from a
 * non-GPU context (it checks for a valid `SurfaceDrawContext`).
 *
 * Tracked as **STUB.GANESH_FWIDTH**.
 */
public class FwidthSquircleGM : GM() {

    override fun getName(): String = "fwidth_squircle"

    override fun getISize(): SkISize = SkISize.Make(200, 200)

    override fun onDraw(canvas: SkCanvas?) {
        // Ganesh-only GM: requires GrGeometryProcessor, GrDrawOp, GrGLSL*,
        // SurfaceDrawContext, and the GLSL fwidth() derivative built-in.
        // The upstream GM returns kSkip on any non-GPU / non-Ganesh context.
        // No raster equivalent exists in kanvas-skia.
        TODO("STUB.GANESH_FWIDTH")
    }
}
