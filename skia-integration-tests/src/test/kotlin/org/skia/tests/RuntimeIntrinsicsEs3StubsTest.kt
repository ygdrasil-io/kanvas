package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Disabled test stubs for the two GPU-only `runtime_intrinsics_*_es3`
 * GMs from `gm/runtimeintrinsics.cpp` that require a Ganesh GPU
 * context with SkSL 300 (`#version 300`) support.
 *
 * Upstream registers these with `DEF_SIMPLE_GPU_GM_CAN_FAIL` (guarded
 * by `SK_GANESH`) and skips at runtime when
 * `shaderCaps()->supportedSkSLVerion() < SkSL::Version::k300`.
 * `:kanvas-skia` is a CPU raster renderer with no GPU context, so
 * both GMs are permanently unsatisfiable in this environment.
 *
 * Affected GMs:
 *  - `runtime_intrinsics_trig_es3`   — sinh/cosh/tanh/asinh/acosh/atanh
 *    (3-column × 2-row grid, `#version 300` SkSL)
 *  - `runtime_intrinsics_common_es3` — floatBitsToInt/floatBitsToUint/
 *    intBitsToFloat/uintBitsToFloat/trunc/round/roundEven/min(int)/
 *    max(int)/clamp(int)/mix(bool) (6-column × 5-row grid, `#version 300`)
 *
 * Each GM's `onDraw` contains a `TODO("STUB.SKSL_ES3_GPU: …")` that
 * throws [NotImplementedError] if accidentally invoked, preventing
 * silent empty-canvas passes.
 */
@Disabled("STUB.SKSL_ES3_GPU: runtime_intrinsics_*_es3 GMs require Ganesh GPU context with SkSL 300 support")
class RuntimeIntrinsicsEs3StubsTest {

    @Test
    fun `runtime_intrinsics_trig_es3 GM stub`() {
        TestUtils.runGmTest(RuntimeIntrinsicsTrigEs3GM())
    }

    @Test
    fun `runtime_intrinsics_common_es3 GM stub`() {
        TestUtils.runGmTest(RuntimeIntrinsicsCommonEs3GM())
    }
}
