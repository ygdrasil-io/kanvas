package org.graphiks.kanvas.surface.gpu

import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.Test
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.commands.GPUBounds
import org.graphiks.kanvas.gpu.renderer.filters.BLUR_PASS_COPY_WGSL
import org.graphiks.kanvas.gpu.renderer.filters.BlurAxis
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlanner
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurRequest
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedBlurStyle
import org.graphiks.wgsl.parser.Lowerer
import org.graphiks.wgsl.parser.parseWgslResult

class GPUMaskBlurWgslTest {
    @Test
    fun `positive sub-half sigma uses static blur WGSL`() {
        assertEquals(MASK_BLUR_HORIZONTAL_WGSL, blurWgsl(BlurAxis.HORIZONTAL))
        assertEquals(MASK_BLUR_VERTICAL_WGSL, blurWgsl(BlurAxis.VERTICAL))
        assertNotEquals(BLUR_PASS_COPY_WGSL, blurWgsl(BlurAxis.HORIZONTAL))
    }

    @Test
    fun `different sigma values keep the same blur module keys`() {
        val moduleKeys = listOf(0.1f, 0.5f, 2f, 48f).flatMap { sigma ->
            blurModuleKeysFor(MaskBlurPlanner.plan(request(sigma)))
        }

        assertEquals(2, moduleKeys.toSet().size)
    }

    @Test
    fun `static horizontal blur shader is parser backed and lowers`() {
        assertParsesAndLowers(MASK_BLUR_HORIZONTAL_WGSL)
    }

    @Test
    fun `static vertical blur shader is parser backed and lowers`() {
        assertParsesAndLowers(MASK_BLUR_VERTICAL_WGSL)
    }

    @Test
    fun `style shader samples two mask textures`() {
        assertTrue(MASK_BLUR_STYLE_WGSL.contains("@binding(1) var srcTexture"))
        assertTrue(MASK_BLUR_STYLE_WGSL.contains("@binding(3) var dstTexture"))
    }

    @Test
    fun `style shader contains solid outer and inner formulas`() {
        assertTrue(MASK_BLUR_STYLE_WGSL.contains("max(original, blurred)"))
        assertTrue(MASK_BLUR_STYLE_WGSL.contains("blurred * (1.0 - original)"))
        assertTrue(MASK_BLUR_STYLE_WGSL.contains("blurred * original"))
    }

    @Test
    fun `solid final composite modulates premultiplied color`() {
        assertTrue(MASK_BLUR_SOLID_COMPOSITE_WGSL.contains("u.color * coverage"))
    }

    @Test
    fun `style shader is parser backed and lowers`() {
        val parsed = parseWgslResult(MASK_BLUR_STYLE_WGSL)

        assertTrue(parsed.isSuccess)
        assertTrue(Lowerer().lower(parsed.translationUnit).entryPoints.isNotEmpty())
    }

    @Test
    fun `solid composite shader is parser backed and lowers`() {
        val parsed = parseWgslResult(MASK_BLUR_SOLID_COMPOSITE_WGSL)

        assertTrue(parsed.isSuccess)
        assertTrue(Lowerer().lower(parsed.translationUnit).entryPoints.isNotEmpty())
    }

    private fun request(sigma: Float): MaskBlurRequest = MaskBlurRequest(
        bounds = GPUBounds(10f, 10f, 30f, 30f),
        clipBounds = GPUBounds(0f, 0f, 64f, 64f),
        targetWidth = 64,
        targetHeight = 64,
        style = NormalizedBlurStyle.NORMAL,
        sigma = sigma,
        maxTextureDimension2D = 4096,
        maxIntermediateBytes = 67_108_864L,
    )

    private fun assertParsesAndLowers(wgsl: String) {
        val parsed = parseWgslResult(wgsl)

        assertTrue(parsed.isSuccess)
        assertTrue(Lowerer().lower(parsed.translationUnit).entryPoints.isNotEmpty())
    }
}
