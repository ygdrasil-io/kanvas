package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize
import org.skia.tests.RuntimeIntrinsicsPlotHelper.columnsToWidth
import org.skia.tests.RuntimeIntrinsicsPlotHelper.rowsToHeight

/**
 * Stub port of Skia's
 * [`gm/runtimeintrinsics.cpp::DEF_SIMPLE_GPU_GM_CAN_FAIL(runtime_intrinsics_trig_es3)`](https://github.com/google/skia/blob/main/gm/runtimeintrinsics.cpp).
 *
 * Upstream lays out a 3-column × 2-row grid of ES3-only hyperbolic
 * trig intrinsics (sinh / cosh / tanh / asinh / acosh / atanh) using
 * `#version 300` SkSL. The GM is guarded by `SK_GANESH` and skips
 * at runtime if the GPU context does not support SkSL 300
 * (`shaderCaps()->supportedSkSLVerion() < SkSL::Version::k300`).
 *
 * **Why stubbed** : `:kanvas-skia` is a CPU raster renderer — there
 * is no GPU context, no Ganesh, and no SkSL 300 support. The
 * `#version 300` path in [org.skia.effects.runtime.effects.SkBuiltinShaderEffectsIntrinsicsTrig.makeUnarySksl1d]
 * is wired but the GPU capability check cannot be satisfied.
 *
 * TODO: STUB.SKSL_ES3_GPU — runtime_intrinsics_trig_es3 requires a
 * Ganesh/GPU context with SkSL 300 support; not available in the CPU
 * raster path.
 */
public class RuntimeIntrinsicsTrigEs3GM : GM() {

    override fun getName(): String = "runtime_intrinsics_trig_es3"
    override fun getISize(): SkISize = SkISize.Make(
        columnsToWidth(3),
        rowsToHeight(2),
    )

    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.SKSL_ES3_GPU: runtime_intrinsics_trig_es3 requires Ganesh GPU context with SkSL 300 support")
    }
}
