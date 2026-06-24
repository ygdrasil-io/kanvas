package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GaussianBlurFilterTest {

    @Test
    fun `execute with default params produces accepted result`() {
        val filter = GaussianBlurFilter()
        val result = filter.execute(BlurFilterParams(radiusX = 4f, radiusY = 4f))
        assertTrue(result.accepted)
        assertEquals(2, result.passCount)
    }

    @Test
    fun `kernel radius maps to correct tap count`() {
        val filter = GaussianBlurFilter()
        val result = filter.execute(BlurFilterParams(radiusX = 3f, radiusY = 3f))
        assertEquals(7, result.kernelSize)
    }

    @Test
    fun `radius zero produces minimum kernel size of one tap`() {
        val filter = GaussianBlurFilter()
        val result = filter.execute(BlurFilterParams(radiusX = 0f, radiusY = 0f))
        assertEquals(1, result.kernelSize)
    }

    @Test
    fun `radius one produces three taps`() {
        val filter = GaussianBlurFilter()
        val result = filter.execute(BlurFilterParams(radiusX = 1f, radiusY = 1f))
        assertEquals(3, result.kernelSize)
    }

    @Test
    fun `small radius produces correct tap count`() {
        val filter = GaussianBlurFilter()
        val result = filter.execute(BlurFilterParams(radiusX = 0.5f, radiusY = 0.5f))
        assertEquals(1, result.kernelSize)
    }

    @Test
    fun `separable flag defaults to true`() {
        val params = BlurFilterParams(radiusX = 4f, radiusY = 4f)
        assertTrue(params.separable)
    }

    @Test
    fun `max pass count defaults to two`() {
        val filter = GaussianBlurFilter()
        val result = filter.execute(BlurFilterParams(radiusX = 4f, radiusY = 4f))
        assertEquals(2, result.passCount)
    }
}
