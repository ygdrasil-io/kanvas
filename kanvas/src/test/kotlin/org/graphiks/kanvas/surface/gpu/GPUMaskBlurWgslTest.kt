package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertTrue

class GPUMaskBlurWgslTest {
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
}
