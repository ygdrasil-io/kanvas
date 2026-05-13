package org.skia.effects

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkColor4f
import kotlin.math.abs

/**
 * Exercises [SkHighContrastFilter] : verify the config validation, the
 * three transform steps (grayscale, invert, contrast), and that alpha is
 * always preserved.
 */
class SkHighContrastFilterTest {

    private fun near(a: Float, b: Float, eps: Float = 1e-3f): Boolean = abs(a - b) <= eps

    @Test
    fun `Make returns null for invalid contrast`() {
        assertNull(SkHighContrastFilter.Make(SkHighContrastConfig(contrast = 1.5f)))
        assertNull(SkHighContrastFilter.Make(SkHighContrastConfig(contrast = -2f)))
    }

    @Test
    fun `default config is a near-identity filter`() {
        val cf = SkHighContrastFilter.Make(SkHighContrastConfig())
        assertNotNull(cf)
        val out = cf!!.filterColor4f(SkColor4f(0.25f, 0.5f, 0.75f, 0.8f))
        assertTrue(near(out.fR, 0.25f), "R=${out.fR}")
        assertTrue(near(out.fG, 0.5f), "G=${out.fG}")
        assertTrue(near(out.fB, 0.75f), "B=${out.fB}")
        assertEquals(0.8f, out.fA, "alpha preserved")
    }

    @Test
    fun `grayscale collapses RGB via BT-709 luma`() {
        val cf = SkHighContrastFilter.Make(SkHighContrastConfig(grayscale = true))!!
        val out = cf.filterColor4f(SkColor4f(1f, 0f, 0f, 1f))
        // BT.709 luma of pure red = 0.2126.
        assertTrue(near(out.fR, 0.2126f), "R=${out.fR}")
        assertTrue(near(out.fG, 0.2126f), "G=${out.fG}")
        assertTrue(near(out.fB, 0.2126f), "B=${out.fB}")
    }

    @Test
    fun `kInvertBrightness flips RGB`() {
        val cf = SkHighContrastFilter.Make(
            SkHighContrastConfig(invertStyle = SkHighContrastConfig.InvertStyle.kInvertBrightness),
        )!!
        val out = cf.filterColor4f(SkColor4f(0.2f, 0.5f, 0.9f, 1f))
        assertTrue(near(out.fR, 0.8f), "R=${out.fR}")
        assertTrue(near(out.fG, 0.5f), "G=${out.fG}")
        assertTrue(near(out.fB, 0.1f), "B=${out.fB}")
    }

    @Test
    fun `kInvertLightness preserves grayscale ramp inversion`() {
        val cf = SkHighContrastFilter.Make(
            SkHighContrastConfig(invertStyle = SkHighContrastConfig.InvertStyle.kInvertLightness),
        )!!
        // For pure gray, L = gray, so the inversion flips it to 1 - L.
        val out = cf.filterColor4f(SkColor4f(0.2f, 0.2f, 0.2f, 1f))
        assertTrue(near(out.fR, 0.8f, 1e-2f), "R=${out.fR}")
        assertTrue(near(out.fG, 0.8f, 1e-2f), "G=${out.fG}")
        assertTrue(near(out.fB, 0.8f, 1e-2f), "B=${out.fB}")
    }

    @Test
    fun `positive contrast amplifies distance from 0_5`() {
        val cf = SkHighContrastFilter.Make(SkHighContrastConfig(contrast = 0.5f))!!
        // contrast=0.5 → slope=(1+0.5)/(1-0.5)=3 ; (0.6-0.5)*3+0.5 = 0.8.
        val out = cf.filterColor4f(SkColor4f(0.6f, 0.6f, 0.6f, 1f))
        assertTrue(near(out.fR, 0.8f), "R=${out.fR}")
    }

    @Test
    fun `negative contrast pulls toward 0_5`() {
        val cf = SkHighContrastFilter.Make(SkHighContrastConfig(contrast = -0.5f))!!
        // slope=(1-0.5)/(1+0.5)=1/3 ; (0.8-0.5)/3+0.5 = 0.6.
        val out = cf.filterColor4f(SkColor4f(0.8f, 0.8f, 0.8f, 1f))
        assertTrue(near(out.fR, 0.6f, 1e-3f), "R=${out.fR}")
    }

    @Test
    fun `alpha is unchanged across configurations`() {
        val cf = SkHighContrastFilter.Make(
            SkHighContrastConfig(
                grayscale = true,
                invertStyle = SkHighContrastConfig.InvertStyle.kInvertBrightness,
                contrast = 0.3f,
            ),
        )!!
        val out = cf.filterColor4f(SkColor4f(0.5f, 0.5f, 0.5f, 0.42f))
        assertEquals(0.42f, out.fA)
        assertEquals(true, cf.isAlphaUnchanged())
    }
}
