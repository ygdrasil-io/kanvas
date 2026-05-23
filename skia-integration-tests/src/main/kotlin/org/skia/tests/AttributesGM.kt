package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's `gm/attributes.cpp::AttributesGM`
 * (registered as `attributes`, 120 × 340).
 *
 * ## Upstream scope
 *
 * This is a **Ganesh-internal** GM that exercises explicit and implicit
 * vertex-attribute offsets and strides directly at the GPU driver level.
 * The test body is entirely built from private Ganesh backend types:
 *
 *  - `GrGeometryProcessor` / `GrGeometryProcessor::ProgramImpl` —
 *    custom vertex shader with three attribute layout modes (`kAuto`,
 *    `kManual`, `kWacky`).
 *  - `GrDrawOp` — custom draw operation that allocates a `GrGpuBuffer`
 *    vertex buffer, binds a pipeline, and issues a 4-vertex triangle-strip
 *    draw call directly via `GrOpFlushState`.
 *  - `GrRecordingContext` / `SurfaceDrawContext` — internal Ganesh
 *    recording and flush primitives not exposed through the public
 *    `SkCanvas` API.
 *  - GLSL shader emitters (`GrGLSLVertexGeoBuilder`,
 *    `GrGLSLFragmentShaderBuilder`) used to compile the attribute-passthrough
 *    shader at flush time.
 *
 * None of these symbols exist in `:kanvas-skia`, which is a raster-CPU
 * backend. The GM cannot be ported at the `SkCanvas` level because the
 * entire test _is_ the internal GPU attribute plumbing — there is no
 * canvas-level drawing surface to wrap.
 *
 * ## Port status — `STUB.GANESH_INTERNAL`
 *
 * The GM is classified `STUB.GANESH_INTERNAL`: the test's purpose is to
 * verify Ganesh-private attribute stride/offset handling, which has no
 * meaningful analogue in a raster implementation. The stub is a
 * flag-planting placeholder only.
 *
 * [AttributesTest] is `@Disabled` until a Ganesh or WebGPU backend
 * lands in `:kanvas-skia`.
 */
public class AttributesGM : GM() {

    override fun getName(): String = "attributes"

    // Upstream: `SkISize getISize() override { return {120, 340}; }`
    override fun getISize(): SkISize = SkISize.Make(120, 340)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO("STUB.GANESH_INTERNAL: AttributesGM requires GrGeometryProcessor, " +
        //      "GrDrawOp, GrGpuBuffer, GrRecordingContext and Ganesh GLSL emitters — " +
        //      "none of which are available in the raster-CPU :kanvas-skia backend. " +
        //      "Port when a GPU backend (Ganesh/WebGPU) is added.")
    }
}
