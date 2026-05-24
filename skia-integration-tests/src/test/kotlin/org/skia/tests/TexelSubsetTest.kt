package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Tests for the `texelsubset` GM family.
 *
 * All 8 GMs ported from `gm/texelsubset.cpp` are classified
 * **INTRACTABLE.GPU_ONLY**: they extend `GpuGM` and their entire `onDraw`
 * body is gated on `skgpu::ganesh::TopDeviceSurfaceDrawContext(canvas)`,
 * returning `kSkip` on any non-GPU canvas. The drawing pipeline uses
 * `GrTextureEffect::MakeSubset`, `GrTextureEffect::Make`,
 * `GrMakeCachedBitmapProxyView`, `GrSamplerState`, and
 * `sk_gpu_test::test_ops::MakeRect` — none of which exist in the
 * `:kanvas-skia` CPU-raster / WebGPU pipeline.
 *
 * All GM bodies call `TODO("STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET")`.
 *
 * Source: `gm/texelsubset.cpp`
 */
class TexelSubsetTest {

    @Disabled(
        "INTRACTABLE.GPU_ONLY: texel_subset_nearest_down is a GpuGM that exercises " +
            "GrTextureEffect::MakeSubset with Filter::kNearest / MipmapMode::kNone (downscale) " +
            "across all GrSamplerState::WrapMode combinations via " +
            "sk_gpu_test::test_ops::MakeRect -> SurfaceDrawContext::addDrawOp. " +
            "No GrRecordingContext is available in the CPU/WebGPU pipeline; " +
            "body calls TODO(\"STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET\"). " +
            "Upstream: gm/texelsubset.cpp.",
    )
    @Test
    fun `TexelSubsetNearestNoneDownGM is GPU-only INTRACTABLE`() {
        val gm = TexelSubsetNearestNoneDownGM()
        TestUtils.runGmTest(gm)
    }

    @Disabled(
        "INTRACTABLE.GPU_ONLY: texel_subset_nearest_up is a GpuGM that exercises " +
            "GrTextureEffect::MakeSubset with Filter::kNearest / MipmapMode::kNone (upscale) " +
            "across all GrSamplerState::WrapMode combinations via " +
            "sk_gpu_test::test_ops::MakeRect -> SurfaceDrawContext::addDrawOp. " +
            "No GrRecordingContext is available in the CPU/WebGPU pipeline; " +
            "body calls TODO(\"STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET\"). " +
            "Upstream: gm/texelsubset.cpp.",
    )
    @Test
    fun `TexelSubsetNearestNoneUpGM is GPU-only INTRACTABLE`() {
        val gm = TexelSubsetNearestNoneUpGM()
        TestUtils.runGmTest(gm)
    }

    @Disabled(
        "INTRACTABLE.GPU_ONLY: texel_subset_linear_down is a GpuGM that exercises " +
            "GrTextureEffect::MakeSubset with Filter::kLinear / MipmapMode::kNone (downscale) " +
            "across all GrSamplerState::WrapMode combinations via " +
            "sk_gpu_test::test_ops::MakeRect -> SurfaceDrawContext::addDrawOp. " +
            "No GrRecordingContext is available in the CPU/WebGPU pipeline; " +
            "body calls TODO(\"STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET\"). " +
            "Upstream: gm/texelsubset.cpp.",
    )
    @Test
    fun `TexelSubsetLinearNoneDownGM is GPU-only INTRACTABLE`() {
        val gm = TexelSubsetLinearNoneDownGM()
        TestUtils.runGmTest(gm)
    }

    @Disabled(
        "INTRACTABLE.GPU_ONLY: texel_subset_linear_up is a GpuGM that exercises " +
            "GrTextureEffect::MakeSubset with Filter::kLinear / MipmapMode::kNone (upscale) " +
            "across all GrSamplerState::WrapMode combinations via " +
            "sk_gpu_test::test_ops::MakeRect -> SurfaceDrawContext::addDrawOp. " +
            "No GrRecordingContext is available in the CPU/WebGPU pipeline; " +
            "body calls TODO(\"STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET\"). " +
            "Upstream: gm/texelsubset.cpp.",
    )
    @Test
    fun `TexelSubsetLinearNoneUpGM is GPU-only INTRACTABLE`() {
        val gm = TexelSubsetLinearNoneUpGM()
        TestUtils.runGmTest(gm)
    }

    @Disabled(
        "INTRACTABLE.GPU_ONLY: texel_subset_nearest_mipmap_nearest_down is a GpuGM that exercises " +
            "GrTextureEffect::MakeSubset with Filter::kNearest / MipmapMode::kNearest (downscale) " +
            "across all GrSamplerState::WrapMode combinations via " +
            "sk_gpu_test::test_ops::MakeRect -> SurfaceDrawContext::addDrawOp. " +
            "No GrRecordingContext is available in the CPU/WebGPU pipeline; " +
            "body calls TODO(\"STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET\"). " +
            "Upstream: gm/texelsubset.cpp.",
    )
    @Test
    fun `TexelSubsetNearestMipNearestDownGM is GPU-only INTRACTABLE`() {
        val gm = TexelSubsetNearestMipNearestDownGM()
        TestUtils.runGmTest(gm)
    }

    @Disabled(
        "INTRACTABLE.GPU_ONLY: texel_subset_linear_mipmap_nearest_down is a GpuGM that exercises " +
            "GrTextureEffect::MakeSubset with Filter::kLinear / MipmapMode::kNearest (downscale) " +
            "across all GrSamplerState::WrapMode combinations via " +
            "sk_gpu_test::test_ops::MakeRect -> SurfaceDrawContext::addDrawOp. " +
            "No GrRecordingContext is available in the CPU/WebGPU pipeline; " +
            "body calls TODO(\"STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET\"). " +
            "Upstream: gm/texelsubset.cpp.",
    )
    @Test
    fun `TexelSubsetLinearMipNearestDownGM is GPU-only INTRACTABLE`() {
        val gm = TexelSubsetLinearMipNearestDownGM()
        TestUtils.runGmTest(gm)
    }

    @Disabled(
        "INTRACTABLE.GPU_ONLY: texel_subset_nearest_mipmap_linear_down is a GpuGM that exercises " +
            "GrTextureEffect::MakeSubset with Filter::kNearest / MipmapMode::kLinear (downscale) " +
            "across all GrSamplerState::WrapMode combinations via " +
            "sk_gpu_test::test_ops::MakeRect -> SurfaceDrawContext::addDrawOp. " +
            "No GrRecordingContext is available in the CPU/WebGPU pipeline; " +
            "body calls TODO(\"STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET\"). " +
            "Upstream: gm/texelsubset.cpp.",
    )
    @Test
    fun `TexelSubsetNearestMipLinearDownGM is GPU-only INTRACTABLE`() {
        val gm = TexelSubsetNearestMipLinearDownGM()
        TestUtils.runGmTest(gm)
    }

    @Disabled(
        "INTRACTABLE.GPU_ONLY: texel_subset_linear_mipmap_linear_down is a GpuGM that exercises " +
            "GrTextureEffect::MakeSubset with Filter::kLinear / MipmapMode::kLinear (downscale) " +
            "across all GrSamplerState::WrapMode combinations via " +
            "sk_gpu_test::test_ops::MakeRect -> SurfaceDrawContext::addDrawOp. " +
            "No GrRecordingContext is available in the CPU/WebGPU pipeline; " +
            "body calls TODO(\"STUB.GR_TEXTURE_EFFECT_MAKE_SUBSET\"). " +
            "Upstream: gm/texelsubset.cpp.",
    )
    @Test
    fun `TexelSubsetLinearMipLinearDownGM is GPU-only INTRACTABLE`() {
        val gm = TexelSubsetLinearMipLinearDownGM()
        TestUtils.runGmTest(gm)
    }
}
