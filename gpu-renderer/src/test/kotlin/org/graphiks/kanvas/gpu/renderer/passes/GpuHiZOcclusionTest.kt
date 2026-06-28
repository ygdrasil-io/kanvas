package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

class GpuHiZOcclusionTest {

    private fun bounds(x: Int, y: Int, width: Int, height: Int): GpuTileBounds =
        GpuTileBounds(x = x, y = y, width = width, height = height)

    // -- Pyramid construction --

    @Test
    fun `pyramid levels build correctly from depth buffer`() {
        val depth = FloatArray(16) { i -> (i % 4) / 4.0f }
        val pyramid = buildHiZPyramid(depth, 4, 4, GPUDepthFormat.DEPTH32FLOAT)

        assertEquals(3, pyramid.levels.size)
        assertEquals(4, pyramid.baseWidth)
        assertEquals(4, pyramid.baseHeight)
        assertEquals(GPUDepthFormat.DEPTH32FLOAT, pyramid.sourceDepthFormat)

        assertEquals(0, pyramid.levels[0].index)
        assertEquals(4, pyramid.levels[0].width)
        assertEquals(4, pyramid.levels[0].height)

        assertEquals(1, pyramid.levels[1].index)
        assertEquals(2, pyramid.levels[1].width)
        assertEquals(2, pyramid.levels[1].height)

        assertEquals(2, pyramid.levels[2].index)
        assertEquals(1, pyramid.levels[2].width)
        assertEquals(1, pyramid.levels[2].height)
    }

    @Test
    fun `pyramid level stores max depth of 2x2 block from level below`() {
        val depth = floatArrayOf(
            0.1f, 0.3f, 0.5f, 0.7f,
            0.2f, 0.4f, 0.6f, 0.8f,
            0.9f, 0.1f, 0.3f, 0.5f,
            0.7f, 0.9f, 0.1f, 0.3f,
        )
        val pyramid = buildHiZPyramid(depth, 4, 4, GPUDepthFormat.DEPTH32FLOAT)

        val level1 = pyramid.levels[1]
        assertEquals(2, level1.width)
        assertEquals(2, level1.height)

        assertEquals(
            maxOf(0.1f, 0.3f, 0.2f, 0.4f),
            level1.depthData[0],
            1e-6f,
            "level1[0,0] must be max of block (0.1,0.3,0.2,0.4)",
        )
        assertEquals(
            maxOf(0.5f, 0.7f, 0.6f, 0.8f),
            level1.depthData[1],
            1e-6f,
            "level1[1,0] must be max of block (0.5,0.7,0.6,0.8)",
        )
        assertEquals(
            maxOf(0.9f, 0.1f, 0.7f, 0.9f),
            level1.depthData[2],
            1e-6f,
            "level1[0,1] must be max of block (0.9,0.1,0.7,0.9)",
        )
        assertEquals(
            maxOf(0.3f, 0.5f, 0.1f, 0.3f),
            level1.depthData[3],
            1e-6f,
            "level1[1,1] must be max of block (0.3,0.5,0.1,0.3)",
        )

        val level2 = pyramid.levels[2]
        assertEquals(1, level2.width)
        assertEquals(1, level2.height)
        assertEquals(
            maxOf(level1.depthData[0], level1.depthData[1], level1.depthData[2], level1.depthData[3]),
            level2.depthData[0],
            1e-6f,
            "top level must be global max depth",
        )
    }

    // -- Occlusion test: Occluded --

    @Test
    fun `draw behind full-coverage occluder detected as Occluded`() {
        val depth = FloatArray(64) { 0.3f }
        val pyramid = buildHiZPyramid(depth, 8, 8, GPUDepthFormat.DEPTH32FLOAT)

        val result = testHiZOcclusion(pyramid, bounds(0, 0, 8, 8), drawMinDepth = 0.8f)
        assertIs<GPUHiZOcclusionResult.Occluded>(result)
    }

    @Test
    fun `full-screen draw behind all geometry is Occluded`() {
        val depth = FloatArray(64) { 0.5f }
        val pyramid = buildHiZPyramid(depth, 8, 8, GPUDepthFormat.DEPTH32FLOAT)

        val result = testHiZOcclusion(pyramid, bounds(0, 0, 8, 8), drawMinDepth = 0.9f)
        assertIs<GPUHiZOcclusionResult.Occluded>(result)
    }

    // -- Occlusion test: Visible --

    @Test
    fun `draw in front of occluder is Visible`() {
        val depth = FloatArray(64) { 0.8f }
        val pyramid = buildHiZPyramid(depth, 8, 8, GPUDepthFormat.DEPTH32FLOAT)

        val result = testHiZOcclusion(pyramid, bounds(0, 0, 8, 8), drawMinDepth = 0.2f)
        assertIs<GPUHiZOcclusionResult.Visible>(result)
    }

    @Test
    fun `draw at same depth as pyramid region is Visible`() {
        val depth = FloatArray(64) { 0.5f }
        val pyramid = buildHiZPyramid(depth, 8, 8, GPUDepthFormat.DEPTH32FLOAT)

        val result = testHiZOcclusion(pyramid, bounds(0, 0, 8, 8), drawMinDepth = 0.5f)
        assertIs<GPUHiZOcclusionResult.Visible>(result)
    }

    // -- Culling rate >= 40% --

    @Test
    fun `culling rate exceeds 40 percent for 50 plus draws with 50 percent occlusion`() {
        val depth = FloatArray(256) { 0.5f }
        for (y in 0 until 16) {
            for (x in 0 until 8) {
                depth[y * 16 + x] = 0.2f
            }
        }
        val pyramid = buildHiZPyramid(depth, 16, 16, GPUDepthFormat.DEPTH32FLOAT)

        val results = mutableListOf<GPUHiZOcclusionResult>()
        for (i in 0 until 60) {
            val isLeft = i < 30
            val bx = if (isLeft) 0 else 8
            val drawDepth = if (isLeft) 0.8f else 0.4f
            results.add(testHiZOcclusion(pyramid, bounds(bx, 0, 8, 16), drawMinDepth = drawDepth))
        }

        val cullingRate = computeHiZCullingRate(results)
        assertTrue(cullingRate >= 0.40f, "Expected culling rate >= 40%, got ${cullingRate * 100}%")

        val occludedCount = results.count { it is GPUHiZOcclusionResult.Occluded }
        assertTrue(occludedCount >= 24, "Expected >= 24 occluded draws, got $occludedCount")
    }

    // -- Zero false positives --

    @Test
    fun `zero false positives across diverse draw set`() {
        val depth = FloatArray(256) { 1.0f }
        for (y in 4 until 12) {
            for (x in 4 until 12) {
                depth[y * 16 + x] = 0.3f
            }
        }
        val pyramid = buildHiZPyramid(depth, 16, 16, GPUDepthFormat.DEPTH32FLOAT)

        val inFrontResults = (0 until 20).map {
            testHiZOcclusion(pyramid, bounds(0, 0, 16, 16), drawMinDepth = 0.1f)
        }

        val falsePositives = inFrontResults.count { it is GPUHiZOcclusionResult.Occluded }
        assertEquals(0, falsePositives, "Zero false positives required, got $falsePositives")
        assertTrue(inFrontResults.all { it is GPUHiZOcclusionResult.Visible })
    }

    @Test
    fun `no false positive when draw partially overlaps occluding region`() {
        val depth = FloatArray(64) { 0.5f }
        for (y in 0 until 4) {
            for (x in 0 until 4) {
                depth[y * 8 + x] = 0.2f
            }
        }
        val pyramid = buildHiZPyramid(depth, 8, 8, GPUDepthFormat.DEPTH32FLOAT)

        val result = testHiZOcclusion(pyramid, bounds(0, 0, 8, 8), drawMinDepth = 0.1f)
        assertIs<GPUHiZOcclusionResult.Visible>(result)
    }

    // -- Memory budget --

    @Test
    fun `memory budget enforced for pyramid storage`() {
        val depth = FloatArray(1024 * 1024) { 0.5f }
        val pyramid = buildHiZPyramid(depth, 1024, 1024, GPUDepthFormat.DEPTH32FLOAT)

        assertTrue(checkHiZPyramidMemoryBudget(pyramid, maxBudgetBytes = 10 * 1024 * 1024))
        assertFalse(checkHiZPyramidMemoryBudget(pyramid, maxBudgetBytes = 1 * 1024 * 1024))

        val totalBytes = pyramidTotalBytes(pyramid)
        assertTrue(totalBytes > 0)
    }

    @Test
    fun `pyramid total bytes computed correctly`() {
        val depth = FloatArray(16) { 0.5f }
        val pyramid = buildHiZPyramid(depth, 4, 4, GPUDepthFormat.DEPTH32FLOAT)

        val expectedBytes = 4 * 4 * 4L + 2 * 2 * 4L + 1 * 1 * 4L
        assertEquals(expectedBytes, pyramidTotalBytes(pyramid, bytesPerTexel = 4))
    }

    // -- Depth format refusal --

    @Test
    fun `depth16unorm format refused with stable diagnostic`() {
        val decision = validateHiZDepthFormat(GPUDepthFormat.DEPTH16UNORM, depthReadable = true)

        assertIs<GPUHiZOcclusionDecision.Refused>(decision)
        assertContains(decision.diagnostic.code, "depth_format_unsupported")
        assertTrue(decision.diagnostic.terminal)
    }

    @Test
    fun `depth not readable target refused with stable diagnostic`() {
        val decision = validateHiZDepthFormat(GPUDepthFormat.DEPTH32FLOAT, depthReadable = false)

        assertIs<GPUHiZOcclusionDecision.Refused>(decision)
        assertContains(decision.diagnostic.code, "depth_not_readable")
        assertTrue(decision.diagnostic.terminal)
    }

    @Test
    fun `depth32float format accepted when readable`() {
        val decision = validateHiZDepthFormat(GPUDepthFormat.DEPTH32FLOAT, depthReadable = true)
        assertIs<GPUHiZOcclusionDecision.Accepted>(decision)
    }

    @Test
    fun `depth24plus format accepted when readable`() {
        val decision = validateHiZDepthFormat(GPUDepthFormat.DEPTH24PLUS, depthReadable = true)
        assertIs<GPUHiZOcclusionDecision.Accepted>(decision)
    }

    // -- Pyramid build plan --

    @Test
    fun `pyramid build plan computes max levels correctly`() {
        val plan = planHiZPyramidBuild(
            source = GPUHiZDepthSource.ZPREPASS,
            sourceWidth = 1024,
            sourceHeight = 768,
            maxLevels = 0,
            format = GPUDepthFormat.DEPTH32FLOAT,
        )

        assertEquals(GPUHiZDepthSource.ZPREPASS, plan.source)
        assertEquals(1024, plan.sourceWidth)
        assertEquals(768, plan.sourceHeight)
        assertEquals(GPUDepthFormat.DEPTH32FLOAT, plan.format)
        assertTrue(plan.maxLevels > 0, "maxLevels should be computed when 0 passed")
    }

    @Test
    fun `pyramid build plan respects explicit max levels cap`() {
        val plan = planHiZPyramidBuild(
            source = GPUHiZDepthSource.PREVIOUS_FRAME,
            sourceWidth = 1024,
            sourceHeight = 1024,
            maxLevels = 4,
            format = GPUDepthFormat.DEPTH32FLOAT,
        )

        assertEquals(4, plan.maxLevels)
        assertEquals(GPUHiZDepthSource.PREVIOUS_FRAME, plan.source)
    }

    // -- Uncertain result for empty/invalid pyramid --

    @Test
    fun `occlusion test returns Uncertain when pyramid has no levels`() {
        val pyramid = GPUHiZPyramid(
            levels = emptyList(),
            baseWidth = 0,
            baseHeight = 0,
            sourceDepthFormat = GPUDepthFormat.DEPTH32FLOAT,
        )

        val result = testHiZOcclusion(pyramid, bounds(0, 0, 4, 4), drawMinDepth = 0.5f)
        assertIs<GPUHiZOcclusionResult.Uncertain>(result)
    }

    // -- Dump lines --

    @Test
    fun `dump lines produce deterministic evidence without backend handles`() {
        val depth = FloatArray(16) { 0.5f }
        val pyramid = buildHiZPyramid(depth, 4, 4, GPUDepthFormat.DEPTH32FLOAT)

        val lines = pyramid.dumpLines()
        assertContains(lines.first(), "passes.hi-z-pyramid format=DEPTH32FLOAT base=4x4")
        assertContains(lines.first(), "levels=3")
        assertFalse(lines.joinToString("\n").contains("WGPU"))
        assertFalse(lines.any { line -> line.contains("backend") && line.contains("handle") })
    }

    @Test
    fun `occlusion result Refused carries diagnostic with correct fields`() {
        val diagnostic = RefuseDiagnostic(
            code = "unsupported.occlusion.depth_format_unsupported",
            message = "Depth16Unorm is not supported for Hi-Z occlusion",
            stage = "occlusion.format",
            terminal = true,
        )
        val result = GPUHiZOcclusionDecision.Refused(diagnostic)

        assertIs<GPUHiZOcclusionDecision.Refused>(result)
        assertEquals("unsupported.occlusion.depth_format_unsupported", result.diagnostic.code)
        assertEquals("occlusion.format", result.diagnostic.stage)
        assertTrue(result.diagnostic.terminal)
    }

    @Test
    fun `occlusion result Accepted carries pyramid`() {
        val depth = FloatArray(16) { 0.5f }
        val pyramid = buildHiZPyramid(depth, 4, 4, GPUDepthFormat.DEPTH32FLOAT)
        val result = GPUHiZOcclusionDecision.Accepted(pyramid)

        assertIs<GPUHiZOcclusionDecision.Accepted>(result)
        assertEquals(pyramid, result.pyramid)
        assertEquals(3, result.pyramid.levels.size)
    }

    // -- Culling rate edge cases --

    @Test
    fun `culling rate zero when no draws occluded`() {
        val results = listOf(
            GPUHiZOcclusionResult.Visible,
            GPUHiZOcclusionResult.Visible,
            GPUHiZOcclusionResult.Visible,
        )
        assertEquals(0.0f, computeHiZCullingRate(results))
    }

    @Test
    fun `culling rate handles empty results`() {
        assertEquals(0.0f, computeHiZCullingRate(emptyList()))
    }

    // -- Pyramid negative test: single-texel base produces single-level pyramid --

    @Test
    fun `single-texel base produces single-level pyramid`() {
        val depth = FloatArray(1) { 0.5f }
        val pyramid = buildHiZPyramid(depth, 1, 1, GPUDepthFormat.DEPTH32FLOAT)

        assertEquals(1, pyramid.levels.size)
        assertEquals(1, pyramid.levels[0].width)
        assertEquals(1, pyramid.levels[0].height)
    }

    // -- Validation helpers --

    private fun assertIllegalArgument(expectedMessageFragment: String, block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected IllegalArgumentException with message containing: $expectedMessageFragment")
        } catch (e: IllegalArgumentException) {
            assertContains(e.message ?: "", expectedMessageFragment)
        }
    }
}
