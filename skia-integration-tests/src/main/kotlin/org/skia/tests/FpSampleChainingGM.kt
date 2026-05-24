package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of upstream Skia `gm/fp_sample_chaining.cpp`
 * (`DEF_SIMPLE_GPU_GM_CAN_FAIL(fp_sample_chaining, …, 232, 306)`).
 *
 * This GM exercises **Ganesh GrFragmentProcessor sample-chaining** —
 * it wires custom `GrFragmentProcessor` subclasses (`UniformMatrixEffect`,
 * `ExplicitCoordEffect`) together in chains and validates that the GPU
 * shader compiler produces correct coordinate-transform code for each
 * combination:
 *
 *  - Row 1: identity, `kUniform` only, `kExplicit` only.
 *  - Row 2: `kUniform+kUniform`, `kExplicit+kExplicit`.
 *  - Row 3: `kUniform+kExplicit`, `kExplicit+kUniform`.
 *  - Row 4: `kDevice+kUniform`, `kExplicit+kUniform+kDevice`,
 *            `kDevice+kExplicit+kUniform+kDevice`.
 *
 * **Missing APIs** — the entire draw path relies on Ganesh-internal APIs
 * that have no CPU-raster equivalent:
 *  - `GrFragmentProcessor` + `GrFragmentProcessor::DeviceSpace`
 *  - `GrMatrixEffect::Make` / `GrTextureEffect::Make`
 *  - `GrPaint::setColorFragmentProcessor`
 *  - `SurfaceDrawContext::drawRect` (Ganesh internal)
 *  - `GrMakeCachedBitmapProxyView`
 *
 * Calling [onDraw] throws `STUB.GR_FRAGMENT_PROCESSOR`. The matching
 * [FpSampleChainingTest] is `@Disabled`.
 *
 * See `API_FINALIZATION_PLAN.md` § STUB.GR_FRAGMENT_PROCESSOR.
 */
public class FpSampleChainingGM : GM() {

    override fun getName(): String = "fp_sample_chaining"
    override fun getISize(): SkISize = SkISize.Make(232, 306)

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.GR_FRAGMENT_PROCESSOR: fp_sample_chaining requires Ganesh GrFragmentProcessor / GrPaint / SurfaceDrawContext — no CPU-raster equivalent")
    }
}
