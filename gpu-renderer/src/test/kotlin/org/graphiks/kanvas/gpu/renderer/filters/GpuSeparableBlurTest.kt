package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GpuSeparableBlurTest {

    @Test
    fun `quality tier HIGH produces more taps than NORMAL for same sigma`() {
        val highTaps = SeparableBlurQualityTier.HIGH.tapCount(4f)
        val normalTaps = SeparableBlurQualityTier.NORMAL.tapCount(4f)
        assertTrue(highTaps > normalTaps)
    }

    @Test
    fun `quality tier FAST produces fixed five taps`() {
        assertEquals(5, SeparableBlurQualityTier.FAST.tapCount(2f))
        assertEquals(5, SeparableBlurQualityTier.FAST.tapCount(10f))
        assertEquals(5, SeparableBlurQualityTier.FAST.tapCount(50f))
    }

    @Test
    fun `kernel cache returns identical kernel for identical parameters`() {
        val cache = GaussianKernelCache()
        val k1 = cache.getOrCompute(sigma = 1.5f, taps = 5)
        val k2 = cache.getOrCompute(sigma = 1.5f, taps = 5)
        assertEquals(k1.weights.toList(), k2.weights.toList())
        assertEquals(k1.offset, k2.offset)
        assertTrue(k1 === k2, "Cached kernel should be the same reference")
    }

    @Test
    fun `kernel cache returns different kernels for different sigmas`() {
        val cache = GaussianKernelCache()
        val k1 = cache.getOrCompute(sigma = 1.0f, taps = 5)
        val k2 = cache.getOrCompute(sigma = 2.0f, taps = 5)
        assertNotEquals(k1.weights.toList(), k2.weights.toList())
    }

    @Test
    fun `kernel weights sum to approximately one`() {
        val cache = GaussianKernelCache()
        val kernel = cache.getOrCompute(sigma = 1.5f, taps = 7)
        val sum = kernel.weights.sum()
        assertTrue(sum in 0.99f..1.01f, "Kernel weights should sum to ~1.0, got $sum")
    }

    @Test
    fun `plan produces two passes for valid sigma`() {
        val planner = GpuSeparableBlurPlanner()
        val plan = planner.plan(sigmaX = 4f, sigmaY = 4f, qualityTier = SeparableBlurQualityTier.HIGH)
        assertEquals(2, plan.passes.size)
    }

    @Test
    fun `plan with zero sigma produces elision not refusal`() {
        val planner = GpuSeparableBlurPlanner()
        val plan = planner.plan(sigmaX = 0f, sigmaY = 0f, qualityTier = SeparableBlurQualityTier.NORMAL)
        assertTrue(plan.diagnostics.none { it.terminal })
        assertTrue(plan.diagnostics.any { it.code == "elision.identity_pass" })
    }

    @Test
    fun `plan with negative sigma produces stable refusal`() {
        val planner = GpuSeparableBlurPlanner()
        val plan = planner.plan(sigmaX = -1f, sigmaY = 0f, qualityTier = SeparableBlurQualityTier.NORMAL)
        assertTrue(plan.diagnostics.any { it.terminal })
        assertTrue(plan.diagnostics.any { it.code == "unsupported.filter.blur_sigma_range" })
    }

    @Test
    fun `plan intermediate artifact has correct format class`() {
        val planner = GpuSeparableBlurPlanner()
        val plan = planner.plan(sigmaX = 4f, sigmaY = 4f, qualityTier = SeparableBlurQualityTier.HIGH)
        assertNotNull(plan.intermediateArtifact)
        assertEquals("rgba8", plan.intermediateArtifact.formatClass)
    }

    @Test
    fun `plan kernel size grows with sigma`() {
        val planner = GpuSeparableBlurPlanner()
        val planSmall = planner.plan(sigmaX = 2f, sigmaY = 2f, qualityTier = SeparableBlurQualityTier.HIGH)
        val planLarge = planner.plan(sigmaX = 8f, sigmaY = 8f, qualityTier = SeparableBlurQualityTier.HIGH)
        assertTrue(planLarge.passes.first().kernelTaps > planSmall.passes.first().kernelTaps)
    }

    @Test
    fun `quality tier HIGH sigma=10 tap count matches spec ceil sigma 3 2 plus 1`() {
        val taps = SeparableBlurQualityTier.HIGH.tapCount(10f)
        assertEquals(31, taps, "ceil(10)*3*2+1=31")
    }

    @Test
    fun `quality tier names are ordered by quality`() {
        assertEquals(0, SeparableBlurQualityTier.FAST.ordinal)
        assertEquals(1, SeparableBlurQualityTier.NORMAL.ordinal)
        assertEquals(2, SeparableBlurQualityTier.HIGH.ordinal)
    }
}
