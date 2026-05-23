package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Disabled stub test for the GPU-only `clear_swizzle` GM from
 * `gm/clear_swizzle.cpp` (`DEF_SIMPLE_GPU_GM_CAN_FAIL`).
 *
 * The GM exercises swizzle-aware GPU clears through Ganesh-internal
 * `SurfaceFillContext` APIs (`skgpu::Swizzle::Concat`, `makeSFC`,
 * `sfc->clear`, `sfc->blitTexture`). All rendering paths require a
 * live Ganesh GPU context and use private Ganesh headers not present
 * in the `:kanvas-skia` CPU raster backend.
 *
 * [ClearSwizzleGM.onDraw] calls `TODO("STUB.CLEAR_SWIZZLE_GPU: …")` that
 * throws [NotImplementedError] if accidentally invoked, preventing
 * silent empty-canvas passes.
 */
@Disabled(
    "STUB.CLEAR_SWIZZLE_GPU: clear_swizzle is DEF_SIMPLE_GPU_GM_CAN_FAIL — " +
        "requires skgpu::Swizzle, GrRecordingContextPriv::makeSFC, and " +
        "SurfaceFillContext::clear which are Ganesh GPU-only internals; " +
        "no raster equivalent exists in kanvas-skia.",
)
class ClearSwizzleTest {

    @Test
    fun `ClearSwizzleGM stub`() {
        TestUtils.runGmTest(ClearSwizzleGM())
    }
}
