package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GaussianBlurFilterTest {

    @Test
    fun `execute with default params produces accepted result`() {
        val filter = GaussianBlurFilter()
        val result = filter.execute(BlurFilterParams(sigmaX = 4f, sigmaY = 4f))
        assertTrue(result.accepted)
        assertEquals(2, result.passCount)
    }

    @Test
    fun `kernel sigma maps to correct tap count`() {
        val filter = GaussianBlurFilter()
        val result = filter.execute(BlurFilterParams(sigmaX = 3f, sigmaY = 3f))
        assertEquals(7, result.kernelSize, "Normal tier ceil(3.0)*2+1 ≈ 7")
    }

    @Test
    fun `sigma zero produces elision identity pass`() {
        val filter = GaussianBlurFilter()
        val result = filter.execute(BlurFilterParams(sigmaX = 0f, sigmaY = 0f))
        assertFalse(result.accepted)
        assertEquals(0, result.kernelSize)
    }

    @Test
    fun `sigma one produces three taps`() {
        val filter = GaussianBlurFilter()
        val result = filter.execute(BlurFilterParams(sigmaX = 1f, sigmaY = 1f))
        assertEquals(3, result.kernelSize)
    }

    @Test
    fun `small sigma produces one tap`() {
        val filter = GaussianBlurFilter()
        val result = filter.execute(BlurFilterParams(sigmaX = 0.5f, sigmaY = 0.5f))
        assertEquals(1, result.kernelSize)
    }

    @Test
    fun `separable flag defaults to true`() {
        val params = BlurFilterParams(sigmaX = 4f, sigmaY = 4f)
        assertTrue(params.separable)
    }

    @Test
    fun `max pass count defaults to two`() {
        val filter = GaussianBlurFilter()
        val result = filter.execute(BlurFilterParams(sigmaX = 4f, sigmaY = 4f))
        assertEquals(2, result.passCount)
    }
}
