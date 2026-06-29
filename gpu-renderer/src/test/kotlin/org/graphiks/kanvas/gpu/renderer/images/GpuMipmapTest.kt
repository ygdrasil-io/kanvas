package org.graphiks.kanvas.gpu.renderer.images

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

class GpuMipmapTest {

    @Test
    fun `mip level count is computed correctly from base dimensions`() {
        val planner = GPUImageMipmapPlanner()

        val square256 = planner.plan(256, 256, MipmapFilter.Box, computeAvailable = true, artifactKey = GPUImageUploadArtifactKey("test-image"))
        assertIs<GPUImageMipmapGenerationResult.Generated>(square256)
        assertEquals(9, square256.plan.levels)

        val square512 = planner.plan(512, 512, MipmapFilter.Box, computeAvailable = true, artifactKey = GPUImageUploadArtifactKey("test-image"))
        assertIs<GPUImageMipmapGenerationResult.Generated>(square512)
        assertEquals(10, square512.plan.levels)

        val singlePixel = planner.plan(1, 1, MipmapFilter.Box, computeAvailable = true, artifactKey = GPUImageUploadArtifactKey("test-image"))
        assertIs<GPUImageMipmapGenerationResult.Generated>(singlePixel)
        assertEquals(1, singlePixel.plan.levels)

        val nonPowerOfTwo = planner.plan(300, 200, MipmapFilter.Box, computeAvailable = true, artifactKey = GPUImageUploadArtifactKey("test-image"))
        assertIs<GPUImageMipmapGenerationResult.Generated>(nonPowerOfTwo)
        assertEquals(9, nonPowerOfTwo.plan.levels)
    }

    @Test
    fun `mip level budget is enforced and generation is refused when level count exceeds adapter limit`() {
        val planner = GPUImageMipmapPlanner(maxMipLevels = 4)

        val result = planner.plan(256, 256, MipmapFilter.Box, computeAvailable = true, artifactKey = GPUImageUploadArtifactKey("test-image"))

        assertIs<GPUImageMipmapGenerationResult.Refused>(result)
        assertEquals("unsupported.image.mipmap_budget_exceeded", result.code)
        assertTrue(result.message.contains("exceeds"))
    }

    @Test
    fun `compute unavailable falls back to blit path with computePlan null`() {
        val planner = GPUImageMipmapPlanner()

        val result = planner.plan(256, 256, MipmapFilter.Tent, computeAvailable = false, artifactKey = GPUImageUploadArtifactKey("test-image"))

        assertIs<GPUImageMipmapGenerationResult.Generated>(result)
        assertEquals(MipmapGenerationPath.Blit, result.plan.path)
        assertIs<GPUImageMipmapBlitPlan>(result.blitPlan)
        assertEquals(8, result.blitPlan!!.levels.size)
        assertEquals(null, result.computePlan)
    }

    @Test
    fun `neither compute nor blit available refuses with stable diagnostic`() {
        val planner = GPUImageMipmapPlanner()

        val result = planner.plan(
            256,
            256,
            MipmapFilter.Box,
            computeAvailable = false,
            artifactKey = GPUImageUploadArtifactKey("no-path-image"),
            blitAvailable = false,
        )

        assertIs<GPUImageMipmapGenerationResult.Refused>(result)
        assertEquals("unsupported.image.mipmap_no_generation_path", result.code)

        val diagnostic = result.toRefuseDiagnostic("mipmap-plan")
        assertTrue(diagnostic.terminal)
        assertEquals("mipmap-plan", diagnostic.stage)
    }

    @Test
    fun `compute path generates both blit and compute plans with correct dispatch sizes`() {
        val planner = GPUImageMipmapPlanner()

        val result = planner.plan(256, 256, MipmapFilter.Kaiser, computeAvailable = true, artifactKey = GPUImageUploadArtifactKey("test-image"))

        assertIs<GPUImageMipmapGenerationResult.Generated>(result)
        assertEquals(MipmapGenerationPath.Compute, result.plan.path)
        assertEquals(MipmapFilter.Kaiser, result.plan.filter)
        assertIs<GPUImageMipmapBlitPlan>(result.blitPlan)
        assertIs<GPUImageMipmapComputePlan>(result.computePlan)

        val blitPlan = result.blitPlan!!
        assertEquals(TextureId(0), blitPlan.sourceTexture)
        assertEquals(8, blitPlan.levels.size)
        assertEquals(TextureBlitOp(srcLevel = 0, dstLevel = 1, dstWidth = 128, dstHeight = 128), blitPlan.levels[0])
        assertEquals(TextureBlitOp(srcLevel = 7, dstLevel = 8, dstWidth = 1, dstHeight = 1), blitPlan.levels[7])

        val computePlan = result.computePlan!!
        assertEquals(WgslModuleId("mipmap-generation-v1"), computePlan.wgslModule)
        assertEquals(8, computePlan.dispatchSizes.size)
        assertEquals(WorkgroupSize(x = 8, y = 8), computePlan.dispatchSizes[0])
        assertEquals(WorkgroupSize(x = 1, y = 1), computePlan.dispatchSizes[7])
    }

    @Test
    fun `nearest sampled no-mipmap single-level texture is not regressed by mipmap planner`() {
        val planner = GPUImageMipmapPlanner()

        val result = planner.plan(64, 64, MipmapFilter.Box, computeAvailable = true, artifactKey = GPUImageUploadArtifactKey("test-image"))

        assertIs<GPUImageMipmapGenerationResult.Generated>(result)
        assertEquals(7, result.plan.levels)

        val blitPlan = result.blitPlan!!
        for (op in blitPlan.levels) {
            assertTrue(op.dstWidth >= 1)
            assertTrue(op.dstHeight >= 1)
            assertTrue(op.dstWidth <= 64)
            assertTrue(op.dstHeight <= 64)
        }
    }

    @Test
    fun `cache plan is keyed deterministically by upload artifact, filter, and format`() {
        val planner = GPUImageMipmapPlanner()

        val result = planner.plan(
            128,
            128,
            MipmapFilter.Box,
            computeAvailable = true,
            artifactKey = GPUImageUploadArtifactKey("cache-image-a"),
            format = "RGBA8Unorm",
        )
        assertIs<GPUImageMipmapGenerationResult.Generated>(result)

        val cachePlan = result.cachePlan()
        assertIs<GPUImageMipmapCachePlan>(cachePlan)
        assertFalse(cachePlan.key.value.isBlank())
        assertFalse(cachePlan.artifact.value.isBlank())
        assertTrue(cachePlan.key.value.contains("cache-image-a"))
        assertTrue(cachePlan.key.value.contains("box"))
        assertTrue(cachePlan.key.value.contains("rgba8unorm"))

        val again = planner.plan(
            128,
            128,
            MipmapFilter.Box,
            computeAvailable = true,
            artifactKey = GPUImageUploadArtifactKey("cache-image-a"),
            format = "RGBA8Unorm",
        )
        assertIs<GPUImageMipmapGenerationResult.Generated>(again)
        assertEquals(cachePlan.key, again.cachePlan().key)
    }

    @Test
    fun `cache key differs for different upload artifacts with identical dimensions filter and format`() {
        val planner = GPUImageMipmapPlanner()

        val artifactA = planner.plan(
            256,
            256,
            MipmapFilter.Box,
            computeAvailable = true,
            artifactKey = GPUImageUploadArtifactKey("image-a"),
            format = "RGBA8Unorm",
        )
        val artifactB = planner.plan(
            256,
            256,
            MipmapFilter.Box,
            computeAvailable = true,
            artifactKey = GPUImageUploadArtifactKey("image-b"),
            format = "RGBA8Unorm",
        )
        assertIs<GPUImageMipmapGenerationResult.Generated>(artifactA)
        assertIs<GPUImageMipmapGenerationResult.Generated>(artifactB)

        assertTrue(artifactA.cachePlan().key != artifactB.cachePlan().key)
    }

    @Test
    fun `refused mipmap generation returns terminal RefuseDiagnostic`() {
        val planner = GPUImageMipmapPlanner(maxMipLevels = 3)

        val result = planner.plan(4096, 4096, MipmapFilter.Box, computeAvailable = true, artifactKey = GPUImageUploadArtifactKey("test-image"))
        assertIs<GPUImageMipmapGenerationResult.Refused>(result)

        val diagnostic = result.toRefuseDiagnostic("mipmap-plan")
        assertEquals("unsupported.image.mipmap_budget_exceeded", diagnostic.code)
        assertTrue(diagnostic.terminal)
        assertEquals("mipmap-plan", diagnostic.stage)
        assertTrue(diagnostic.message.contains("4096"))
    }

    @Test
    fun `mip level count for rectangular non-square textures uses max dimension`() {
        val planner = GPUImageMipmapPlanner()

        val tall = planner.plan(64, 512, MipmapFilter.Box, computeAvailable = true, artifactKey = GPUImageUploadArtifactKey("test-image"))
        assertIs<GPUImageMipmapGenerationResult.Generated>(tall)
        assertEquals(10, tall.plan.levels)

        val wide = planner.plan(512, 64, MipmapFilter.Box, computeAvailable = true, artifactKey = GPUImageUploadArtifactKey("test-image"))
        assertIs<GPUImageMipmapGenerationResult.Generated>(wide)
        assertEquals(10, wide.plan.levels)
    }

    @Test
    fun `mipmap plan dump provides stable evidence lines for reporting`() {
        val planner = GPUImageMipmapPlanner()

        val result = planner.plan(256, 256, MipmapFilter.Box, computeAvailable = true, artifactKey = GPUImageUploadArtifactKey("test-image"))
        assertIs<GPUImageMipmapGenerationResult.Generated>(result)

        val dump = result.dumpLines()
        assertTrue(dump.isNotEmpty())
        assertTrue(dump.any { it.startsWith("mipmap:generation") })
        assertTrue(dump.any { it.startsWith("mipmap:plan") })
        assertTrue(dump.any { it.startsWith("mipmap:blit") })
        assertTrue(dump.any { it.startsWith("mipmap:compute") })
        assertTrue(dump.any { it.startsWith("mipmap:blit-level") })
        assertTrue(dump.any { it.startsWith("mipmap:cache") })
        val evidenceLines = dump.filter { !it.startsWith("nonclaim:") }
        assertTrue(evidenceLines.none { it.contains("handle", ignoreCase = true) })
        assertTrue(evidenceLines.none { it.contains("pointer", ignoreCase = true) })
        assertTrue(dump.any { it.startsWith("nonclaim:") })
    }
}
