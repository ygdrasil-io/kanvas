package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GpuSeparableBlurTest {

    @Test
    fun `quality tier HIGH produces more taps than LOW for same radius`() {
        val highTaps = SeparableBlurQualityTier.HIGH.tapCount(4f)
        val lowTaps = SeparableBlurQualityTier.LOW.tapCount(4f)
        assertTrue(highTaps > lowTaps)
    }

    @Test
    fun `quality tier MEDIUM sigma scale is between HIGH and LOW`() {
        assertTrue(SeparableBlurQualityTier.HIGH.sigmaScale > SeparableBlurQualityTier.MEDIUM.sigmaScale)
        assertTrue(SeparableBlurQualityTier.MEDIUM.sigmaScale > SeparableBlurQualityTier.LOW.sigmaScale)
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
    fun `plan produces two passes for valid radius`() {
        val planner = GpuSeparableBlurPlanner()
        val plan = planner.plan(radiusX = 4f, radiusY = 4f, qualityTier = SeparableBlurQualityTier.HIGH)
        assertEquals(2, plan.passes.size)
    }

    @Test
    fun `plan with zero radius produces refusal diagnostic`() {
        val planner = GpuSeparableBlurPlanner()
        val plan = planner.plan(radiusX = 0f, radiusY = 0f, qualityTier = SeparableBlurQualityTier.HIGH)
        assertTrue(plan.diagnostics.any { it.terminal })
    }

    @Test
    fun `plan intermediate artifact has correct format class`() {
        val planner = GpuSeparableBlurPlanner()
        val plan = planner.plan(radiusX = 4f, radiusY = 4f, qualityTier = SeparableBlurQualityTier.HIGH)
        assertNotNull(plan.intermediateArtifact)
        assertEquals("rgba8", plan.intermediateArtifact.formatClass)
    }

    @Test
    fun `plan kernel size grows with radius`() {
        val planner = GpuSeparableBlurPlanner()
        val planSmall = planner.plan(radiusX = 2f, radiusY = 2f, qualityTier = SeparableBlurQualityTier.HIGH)
        val planLarge = planner.plan(radiusX = 8f, radiusY = 8f, qualityTier = SeparableBlurQualityTier.HIGH)
        assertTrue(planLarge.passes.first().kernelTaps > planSmall.passes.first().kernelTaps)
    }

    @Test
    fun `quality tier HIGH tap scaling factor is one`() {
        assertEquals(1.0f, SeparableBlurQualityTier.HIGH.tapScale)
    }

    @Test
    fun `quality tier names are ordered by quality`() {
        assertEquals(0, SeparableBlurQualityTier.HIGH.ordinal)
        assertEquals(1, SeparableBlurQualityTier.MEDIUM.ordinal)
        assertEquals(2, SeparableBlurQualityTier.LOW.ordinal)
    }
}
