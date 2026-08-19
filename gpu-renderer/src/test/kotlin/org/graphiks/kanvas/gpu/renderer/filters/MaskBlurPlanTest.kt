package org.graphiks.kanvas.gpu.renderer.filters

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.clips.GPUBounds

class MaskBlurPlanTest {

    @Test
    fun `zero sigma is identity`() {
        assertEquals(MaskBlurPlan.Identity, plan(sigma = 0f))
    }

    @Test
    fun `positive sub-half sigma uses a normalized three-tap kernel`() {
        val result = assertIs<MaskBlurPlan.Ready>(plan(sigma = 0.1f))

        assertEquals(0.5f, result.normalizedSigma)
        assertEquals(2, result.halo)
        assertEquals(3, blurKernelUniform(result).tapCount)
    }

    @Test
    fun `negative and non-finite sigma refuse stably`() {
        listOf(-0.1f, Float.NaN, Float.POSITIVE_INFINITY).forEach { sigma ->
            assertEquals(
                "unsupported.mask-filter.blur.sigma",
                assertIs<MaskBlurPlan.Refused>(plan(sigma = sigma)).code,
            )
        }
    }

    @Test
    fun `small blur keeps native scale and halo`() {
        val result = assertIs<MaskBlurPlan.Ready>(plan(sigma = 2f))

        assertEquals(1f, result.scale)
        assertEquals(2f, result.effectiveSigma)
        assertEquals(GPUBounds(4f, 4f, 36f, 36f), result.deviceBounds)
    }

    @Test
    fun `large blur reduces until direct sigma limit`() {
        val result = assertIs<MaskBlurPlan.Ready>(plan(sigma = 48f))

        assertEquals(0.25f, result.scale)
        assertEquals(12f, result.effectiveSigma)
    }

    @Test
    fun `large API value clamps to Skia compatible sigma`() {
        val result = assertIs<MaskBlurPlan.Ready>(plan(sigma = 200f))

        assertEquals(135f, result.normalizedSigma)
        assertTrue(result.diagnostics.any { it.code == "mask-filter.blur.sigma-clamped" })
    }

    @Test
    fun `impossible aggregate texture budget refuses`() {
        val result = plan(12f, GPUBounds(0f, 0f, 4096f, 4096f), budgetBytes = 8L)

        assertEquals(
            "unsupported.mask-filter.blur.intermediate-budget",
            assertIs<MaskBlurPlan.Refused>(result).code,
        )
    }

    private fun plan(
        sigma: Float,
        bounds: GPUBounds = GPUBounds(10f, 10f, 30f, 30f),
        budgetBytes: Long = 67_108_864L,
    ): MaskBlurPlan = MaskBlurPlanner.plan(
        MaskBlurRequest(
            bounds = bounds,
            clipBounds = GPUBounds(0f, 0f, 4096f, 4096f),
            targetWidth = 4096,
            targetHeight = 4096,
            style = NormalizedBlurStyle.NORMAL,
            sigma = sigma,
            maxTextureDimension2D = 4096,
            maxIntermediateBytes = budgetBytes,
        ),
    )
}
