package org.skia.effects.runtime.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.foundation.SkColor4f
import kotlin.math.abs

/**
 * D2.4.d unit-tests : verifies that the 4 specialised SkSL
 * programs from [SkBuiltinSpecialisedEffects] resolve and
 * evaluate per upstream semantics.
 */
class SkBuiltinSpecialisedEffectsTest {

    @BeforeEach
    fun ensureRegistered() {
        SkBuiltinSpecialisedEffects.registerAll()
    }

    @Test
    fun `every specialised hash resolves`() {
        // destcolor invert blender
        assertNotNull(SkRuntimeEffect.MakeForBlender(
            SkBuiltinSpecialisedEffects.INVERT_BLENDER_SKSL).effect)
        // image_dither stretch_colors_blender
        assertNotNull(SkRuntimeEffect.MakeForBlender(
            SkBuiltinSpecialisedEffects.STRETCH_COLORS_BLENDER_SKSL).effect)
        // kawase_blur_rt blur shader
        assertNotNull(SkRuntimeEffect.MakeForShader(
            SkBuiltinSpecialisedEffects.KAWASE_BLUR_SHADER_SKSL).effect)
        // kawase_blur_rt mix shader
        assertNotNull(SkRuntimeEffect.MakeForShader(
            SkBuiltinSpecialisedEffects.KAWASE_MIX_SHADER_SKSL).effect)
    }

    @Test
    fun `invert blender returns 1 minus dst rgb`() {
        val out = SkBuiltinSpecialisedEffects.InvertBlenderImpl.shade(
            coords = null,
            srcColor = SkColor4f(1f, 1f, 1f, 1f),  // ignored
            dstColor = SkColor4f(0.2f, 0.4f, 0.6f, 0.5f),
            uniforms = java.nio.ByteBuffer.allocate(0),
            children = emptyArray(),
        )
        assertNearly(0.8f, out.fR)
        assertNearly(0.6f, out.fG)
        assertNearly(0.4f, out.fB)
        // Alpha forced to 1 by .rgb1 swizzle.
        assertEquals(1f, out.fA, 0.001f)
    }

    @Test
    fun `stretch blender maps dst from 0_25 to 1_0 to 0 to 12`() {
        val out = SkBuiltinSpecialisedEffects.StretchColorsBlenderImpl.shade(
            coords = null,
            srcColor = SkColor4f(1f, 1f, 1f, 1f),
            dstColor = SkColor4f(0.25f, 0.3125f, 0.5f, 0.0f),
            uniforms = java.nio.ByteBuffer.allocate(0),
            children = emptyArray(),
        )
        // (0.25 - 0.25) * 16 = 0
        assertNearly(0f, out.fR)
        // (0.3125 - 0.25) * 16 = 1
        assertNearly(1f, out.fG)
        // (0.5 - 0.25) * 16 = 4
        assertNearly(4f, out.fB)
        assertEquals(1f, out.fA, 0.001f)
    }

    private fun assertNearly(expected: Float, actual: Float, tol: Float = 1e-3f) {
        org.junit.jupiter.api.Assertions.assertTrue(
            abs(expected - actual) < tol,
            "expected=$expected actual=$actual diff=${abs(expected - actual)}",
        )
    }
}
