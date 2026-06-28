package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GpuDropShadowTest {

    private fun blackColor() = GPUColor(r = 0f, g = 0f, b = 0f, a = 1f)

    @Test
    fun `drop shadow composite mode with offset and sigma produces accepted result`() {
        val filter = GpuDropShadowFilter()
        val params = GPUDropShadowPlan(
            offsetDx = 5f,
            offsetDy = 5f,
            sigmaX = 2f,
            sigmaY = 2f,
            shadowColor = blackColor(),
            mode = GPUDropShadowMode.Composite,
            tileMode = GPUTileMode.Clamp,
        )
        val result = filter.plan(params)
        assertTrue(result is GPUDropShadowResult.Accepted)
        val accepted = result as GPUDropShadowResult.Accepted
        assertNotNull(accepted.blurPlan, "Blur plan should be present when sigma > 0")
    }

    @Test
    fun `drop shadow ShadowOnly mode is accepted with correct mode`() {
        val filter = GpuDropShadowFilter()
        val params = GPUDropShadowPlan(
            offsetDx = 5f,
            offsetDy = 5f,
            sigmaX = 2f,
            sigmaY = 2f,
            shadowColor = GPUColor(r = 0f, g = 0f, b = 0f, a = 0.5f),
            mode = GPUDropShadowMode.ShadowOnly,
            tileMode = GPUTileMode.Clamp,
        )
        val result = filter.plan(params)
        assertTrue(result is GPUDropShadowResult.Accepted)
        val accepted = result as GPUDropShadowResult.Accepted
        assertEquals(GPUDropShadowMode.ShadowOnly, accepted.plan.mode)
        assertNotNull(accepted.blurPlan)
    }

    @Test
    fun `drop shadow zero sigma produces accepted with null blur plan identity pass`() {
        val filter = GpuDropShadowFilter()
        val params = GPUDropShadowPlan(
            offsetDx = 5f,
            offsetDy = 5f,
            sigmaX = 0f,
            sigmaY = 0f,
            shadowColor = blackColor(),
            mode = GPUDropShadowMode.ShadowOnly,
            tileMode = GPUTileMode.Clamp,
        )
        val result = filter.plan(params)
        assertTrue(result is GPUDropShadowResult.Accepted)
        val accepted = result as GPUDropShadowResult.Accepted
        assertNull(accepted.blurPlan, "Blur plan should be null for zero sigma identity pass")
    }

    @Test
    fun `drop shadow offset zero zero Composite mode preserves zero offset`() {
        val filter = GpuDropShadowFilter()
        val params = GPUDropShadowPlan(
            offsetDx = 0f,
            offsetDy = 0f,
            sigmaX = 2f,
            sigmaY = 2f,
            shadowColor = blackColor(),
            mode = GPUDropShadowMode.Composite,
            tileMode = GPUTileMode.Clamp,
        )
        val result = filter.plan(params)
        assertTrue(result is GPUDropShadowResult.Accepted)
        val accepted = result as GPUDropShadowResult.Accepted
        assertEquals(0f, accepted.plan.offsetDx)
        assertEquals(0f, accepted.plan.offsetDy)
    }

    @Test
    fun `drop shadow negative offset with sigma 0 is accepted`() {
        val filter = GpuDropShadowFilter()
        val params = GPUDropShadowPlan(
            offsetDx = -3f,
            offsetDy = 2f,
            sigmaX = 0f,
            sigmaY = 0f,
            shadowColor = GPUColor(r = 1f, g = 0f, b = 0f, a = 0.5f),
            mode = GPUDropShadowMode.Composite,
            tileMode = GPUTileMode.Clamp,
        )
        val result = filter.plan(params)
        assertTrue(result is GPUDropShadowResult.Accepted)
        val accepted = result as GPUDropShadowResult.Accepted
        assertEquals(-3f, accepted.plan.offsetDx)
        assertEquals(2f, accepted.plan.offsetDy)
        assertNull(accepted.blurPlan)
    }

    @Test
    fun `drop shadow accepted result has non-terminal accepted diagnostic`() {
        val filter = GpuDropShadowFilter()
        val params = GPUDropShadowPlan(
            offsetDx = 10f,
            offsetDy = 5f,
            sigmaX = 4f,
            sigmaY = 4f,
            shadowColor = blackColor(),
            mode = GPUDropShadowMode.Composite,
            tileMode = GPUTileMode.Repeat,
        )
        val result = filter.plan(params)
        assertTrue(result is GPUDropShadowResult.Accepted)
        val accepted = result as GPUDropShadowResult.Accepted
        val diagnostic = accepted.diagnostics.single()
        assertEquals("accepted.filter.drop_shadow", diagnostic.code)
        assertFalse(diagnostic.terminal)
    }

    @Test
    fun `drop shadow result type has Refused variant with terminal diagnostic`() {
        val refused = GPUDropShadowResult.Refused(
            plan = GPUDropShadowPlan(
                offsetDx = 0f,
                offsetDy = 0f,
                sigmaX = 1f,
                sigmaY = 1f,
                shadowColor = blackColor(),
                mode = GPUDropShadowMode.Composite,
                tileMode = GPUTileMode.Clamp,
            ),
            diagnostics = listOf(
                GPUFilterDiagnostic(
                    code = "unsupported.filter.drop_shadow_blur_unavailable",
                    message = "Drop shadow blur pass unavailable.",
                    terminal = true,
                ),
            ),
        )
        assertTrue(refused is GPUDropShadowResult.Refused)
        assertEquals("unsupported.filter.drop_shadow_blur_unavailable", refused.diagnostics.single().code)
        assertTrue(refused.diagnostics.single().terminal)
        assertNull(refused.blurPlan)
    }

    @Test
    fun `drop shadow plan roundtrip preserves all fields`() {
        val shadowColor = GPUColor(r = 0.1f, g = 0.2f, b = 0.3f, a = 0.8f)
        val params = GPUDropShadowPlan(
            offsetDx = 7f,
            offsetDy = -4f,
            sigmaX = 3.5f,
            sigmaY = 1.5f,
            shadowColor = shadowColor,
            mode = GPUDropShadowMode.ShadowOnly,
            tileMode = GPUTileMode.Mirror,
        )
        assertEquals(7f, params.offsetDx)
        assertEquals(-4f, params.offsetDy)
        assertEquals(3.5f, params.sigmaX)
        assertEquals(1.5f, params.sigmaY)
        assertEquals(shadowColor, params.shadowColor)
        assertEquals(GPUDropShadowMode.ShadowOnly, params.mode)
        assertEquals(GPUTileMode.Mirror, params.tileMode)
    }

    @Test
    fun `drop shadow accepted has blur plan with correct pass count`() {
        val filter = GpuDropShadowFilter()
        val params = GPUDropShadowPlan(
            offsetDx = 3f,
            offsetDy = 3f,
            sigmaX = 3f,
            sigmaY = 3f,
            shadowColor = blackColor(),
            mode = GPUDropShadowMode.Composite,
            tileMode = GPUTileMode.Clamp,
        )
        val result = filter.plan(params)
        assertTrue(result is GPUDropShadowResult.Accepted)
        val accepted = result as GPUDropShadowResult.Accepted
        assertNotNull(accepted.blurPlan)
        assertEquals(2, accepted.blurPlan!!.blur.passes.size)
    }
}
