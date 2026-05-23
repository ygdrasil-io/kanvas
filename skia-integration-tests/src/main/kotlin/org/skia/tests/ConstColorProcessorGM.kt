package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of `gm/constcolorprocessor.cpp::ColorProcessor` with
 * `TestMode::kConstColor` (`DEF_GM` name `const_color_processor`).
 *
 * The GM exercises `GrFragmentProcessor::MakeColor` layered on top of
 * various solid-colour and gradient base fragment processors, then
 * renders the FP trees directly via Ganesh's `SurfaceDrawContext`
 * and `sk_gpu_test::test_ops::MakeRect`.
 *
 * **Ganesh-only.** The entire draw path — `GrFragmentProcessor`,
 * `GrFPArgs`, `SurfaceDrawContext`, `GrRecordingContext`, and
 * `sk_gpu_test::TestOps` — is Ganesh-internal and has no raster
 * equivalent in kanvas-skia. The project decision
 * ([MIGRATION_PLAN_GPU_WEBGPU.md](../../../../../../../../MIGRATION_PLAN_GPU_WEBGPU.md))
 * explicitly excludes Ganesh; the WebGPU backend will address GPU GMs
 * in a separate phase.
 *
 * The body calls `TODO("STUB.GANESH_GPU")` to make the dependency
 * explicit at runtime. [ConstColorProcessorTest] is `@Disabled`.
 *
 * Upstream reference: `gm/constcolorprocessor.cpp`,
 * `ColorProcessor::TestMode::kConstColor`, size 820 × 500.
 */
public class ConstColorProcessorGM : GM() {

    override fun getName(): String = "const_color_processor"
    override fun getISize(): SkISize = SkISize.Make(820, 500)

    override fun onDraw(canvas: SkCanvas?) {
        // Touch the stubbed dispatch — throws STUB.GANESH_GPU at runtime.
        TODO("STUB.GANESH_GPU: GrFragmentProcessor::MakeColor + SurfaceDrawContext are Ganesh-internal; no raster equivalent — see MIGRATION_PLAN_GPU_WEBGPU.md")
    }
}
