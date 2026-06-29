package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GPUMorphologyTest {

    @Test
    fun `dilate with rect kernel produces accepted result`() {
        val filter = GPUMorphologyFilter()
        val result = filter.execute(
            MorphologyParams(
                mode = MorphologyMode.DILATE,
                kernel = MorphologyKernel.RECT,
                radiusX = 4f,
                radiusY = 4f,
            ),
        )
        assertTrue(result.accepted)
        assertEquals(1, result.passCount)
    }

    @Test
    fun `erode with rect kernel produces accepted result`() {
        val filter = GPUMorphologyFilter()
        val result = filter.execute(
            MorphologyParams(
                mode = MorphologyMode.ERODE,
                kernel = MorphologyKernel.RECT,
                radiusX = 3f,
                radiusY = 3f,
            ),
        )
        assertTrue(result.accepted)
    }

    @Test
    fun `dilate with circular kernel produces accepted result`() {
        val filter = GPUMorphologyFilter()
        val result = filter.execute(
            MorphologyParams(
                mode = MorphologyMode.DILATE,
                kernel = MorphologyKernel.CIRCLE,
                radiusX = 5f,
                radiusY = 5f,
            ),
        )
        assertTrue(result.accepted)
    }

    @Test
    fun `erode with circular kernel produces accepted result`() {
        val filter = GPUMorphologyFilter()
        val result = filter.execute(
            MorphologyParams(
                mode = MorphologyMode.ERODE,
                kernel = MorphologyKernel.CIRCLE,
                radiusX = 2f,
                radiusY = 4f,
            ),
        )
        assertTrue(result.accepted)
    }

    @Test
    fun `zero radius is valid and produces accepted result`() {
        val filter = GPUMorphologyFilter()
        val result = filter.execute(
            MorphologyParams(
                mode = MorphologyMode.DILATE,
                kernel = MorphologyKernel.RECT,
                radiusX = 0f,
                radiusY = 0f,
            ),
        )
        assertTrue(result.accepted)
    }

    @Test
    fun `negative radius produces refused result`() {
        val filter = GPUMorphologyFilter()
        val result = filter.execute(
            MorphologyParams(
                mode = MorphologyMode.DILATE,
                kernel = MorphologyKernel.RECT,
                radiusX = -1f,
                radiusY = 4f,
            ),
        )
        assertFalse(result.accepted)
    }

    @Test
    fun `ellipse kernel with valid radii produces accepted result`() {
        val filter = GPUMorphologyFilter()
        val result = filter.execute(
            MorphologyParams(
                mode = MorphologyMode.DILATE,
                kernel = MorphologyKernel.ELLIPSE,
                radiusX = 3f,
                radiusY = 2f,
            ),
        )
        assertTrue(result.accepted)
    }

    @Test
    fun `ellipse kernel with zero radii produces shape unsupported refusal`() {
        val filter = GPUMorphologyFilter()
        val result = filter.execute(
            MorphologyParams(
                mode = MorphologyMode.DILATE,
                kernel = MorphologyKernel.ELLIPSE,
                radiusX = 0f,
                radiusY = 0f,
            ),
        )
        assertFalse(result.accepted)
        assertTrue(result.diagnostics.any { it.code == "unsupported.filter.morphology_shape_unsupported" })
    }

    @Test
    fun `params roundtrip preserves mode kernel and radii`() {
        val params = MorphologyParams(
            mode = MorphologyMode.ERODE,
            kernel = MorphologyKernel.CIRCLE,
            radiusX = 7f,
            radiusY = 3f,
        )
        assertEquals(MorphologyMode.ERODE, params.mode)
        assertEquals(MorphologyKernel.CIRCLE, params.kernel)
        assertEquals(7f, params.radiusX)
        assertEquals(3f, params.radiusY)
    }

    @Test
    fun `ellipse kernel roundtrip preserves fields`() {
        val params = MorphologyParams(
            mode = MorphologyMode.DILATE,
            kernel = MorphologyKernel.ELLIPSE,
            radiusX = 4f,
            radiusY = 3f,
        )
        assertEquals(MorphologyKernel.ELLIPSE, params.kernel)
        assertEquals(4f, params.radiusX)
        assertEquals(3f, params.radiusY)
    }
}
