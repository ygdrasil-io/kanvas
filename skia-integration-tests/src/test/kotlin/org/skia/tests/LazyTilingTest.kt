package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Tests for `gm/lazytiling.cpp` — `lazytiling_tl` and `lazytiling_bl`.
 *
 * Both GMs extend `GpuGM` and rely exclusively on Ganesh-internal APIs
 * (`GrProxyProvider::MakeFullyLazyProxy`, `GrTextureEffect::MakeSubset`,
 * `skgpu::ganesh::TopDeviceSurfaceDrawContext`). The upstream implementation
 * returns `DrawResult::kSkip` on any non-GPU context and there are no
 * reference PNGs in `src/test/resources/original-888/` for either variant.
 *
 * Disabled as `STUB.LAZY_TILING_GPU` until the `:kanvas-skia` GPU/WebGPU
 * backend is available (see MIGRATION_PLAN_GPU_WEBGPU.md).
 */
@Disabled(
    "STUB.LAZY_TILING_GPU: lazytiling_tl / lazytiling_bl are GpuGM-only — require " +
        "GrProxyProvider::MakeFullyLazyProxy, GrTextureEffect::MakeSubset, and " +
        "skgpu::ganesh::TopDeviceSurfaceDrawContext. No raster equivalent and no " +
        "reference PNGs exist for these GM names."
)
class LazyTilingTest {

    @Test
    fun `LazyTilingTlGM placeholder`() {
        TestUtils.runGmTest(LazyTilingTlGM())
    }

    @Test
    fun `LazyTilingBlGM placeholder`() {
        TestUtils.runGmTest(LazyTilingBlGM())
    }
}
