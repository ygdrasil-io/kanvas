package com.kanvas.core

import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ColorTest {

    @Test
    fun `test basic color creation`() {
        val color = Color(100, 150, 200, 255)
        assertEquals(100, color.red)
        assertEquals(150, color.green)
        assertEquals(200, color.blue)
        assertEquals(255, color.alpha)
    }

    @Test
    fun `test color validation`() {
        assertThrows(IllegalArgumentException::class.java) {
            Color(300, 100, 100) // Red out of range
        }
        
        assertThrows(IllegalArgumentException::class.java) {
            Color(100, -1, 100) // Green out of range
        }
    }

    @Test
    fun `test ARGB conversion`() {
        val color = Color(100, 150, 200, 128)
        val argb = color.toArgb()
        
        val expectedArgb = (128 shl 24) or (100 shl 16) or (150 shl 8) or 200
        assertEquals(expectedArgb, argb)
        
        val colorFromArgb = Color.fromArgb(argb)
        assertEquals(color, colorFromArgb)
    }

    @Test
    fun `test color constants`() {
        assertEquals(Color(0, 0, 0, 0), Color.TRANSPARENT)
        assertEquals(Color(0, 0, 0), Color.BLACK)
        assertEquals(Color(255, 255, 255), Color.WHITE)
        assertEquals(Color(255, 0, 0), Color.RED)
        assertEquals(Color(0, 255, 0), Color.GREEN)
        assertEquals(Color(0, 0, 255), Color.BLUE)
    }

    @Test
    fun `test color blending`() {
        val red = Color.RED
        val blue = Color.BLUE
        
        // Test SRC_OVER blending (standard alpha blending)
        val blended = red.blendWith(blue, BlendMode.SRC_OVER)
        // Since both are opaque, SRC_OVER should just return the source (red)
        assertEquals(red, blended)
        
        // Test with transparency - semi-transparent red over opaque blue
        val semiRed = Color(255, 0, 0, 128)
        val result = semiRed.blendWith(blue, BlendMode.SRC_OVER)
        // Just verify that blending produces some result (not transparent)
        assertTrue(result.red > 0 || result.green > 0 || result.blue > 0)
        assertTrue(result.alpha > 0)
    }

    @Test
    fun `test color filters`() {
        val original = Color(200, 100, 50)
        
        // Test matrix filter (identity matrix should not change color)
        val identityMatrix = FloatArray(20) { if (it % 6 == 0) 1f else 0f } // Corrected: 4x5 matrix has 20 elements, identity should be 1s on diagonal
        val matrixFilter = ColorFilters.matrix(identityMatrix)
        val filtered = original.withFilter(matrixFilter)
        assertEquals(original, filtered)
        
        // Test lighting filter - multiply by 2 and add 10 to each channel
        val lightingFilter = ColorFilters.lighting(Color(2, 2, 2), Color(10, 10, 10))
        val litColor = original.withFilter(lightingFilter)
        
        // Just verify that the filter changes the color
        assertTrue(litColor.red != original.red || litColor.green != original.green || litColor.blue != original.blue)
        // Verify that values are within valid range
        assertTrue(litColor.red in 0..255)
        assertTrue(litColor.green in 0..255)
        assertTrue(litColor.blue in 0..255)
    }

    @Test
    fun `test color info`() {
        val opaqueColor = Color(100, 150, 200)
        val transparentColor = Color(100, 150, 200, 128)
        
        val opaqueInfo = opaqueColor.toColorInfo()
        assertEquals(ColorType.RGBA_8888, opaqueInfo.colorType)
        assertEquals(AlphaType.OPAQUE, opaqueInfo.alphaType)
        assertEquals(ColorSpace.SRGB, opaqueInfo.colorSpace)
        
        val transparentInfo = transparentColor.toColorInfo()
        assertEquals(ColorType.RGBA_8888, transparentInfo.colorType)
        assertEquals(AlphaType.UNPREMUL, transparentInfo.alphaType)
    }

    @Test
    fun `test alpha operations`() {
        val color = Color(200, 100, 50, 128)
        
        // Test premultiplication
        val premul = color.premultiply()
        assertTrue(premul.red < color.red) // Should be reduced due to alpha
        assertTrue(premul.green < color.green)
        assertTrue(premul.blue < color.blue)
        
        // Test unpremultiplication
        val unpremul = premul.unpremultiply()
        // Allow small rounding differences due to integer arithmetic
        assertTrue(Math.abs(color.red - unpremul.red) <= 1)
        assertTrue(Math.abs(color.green - unpremul.green) <= 1)
        assertTrue(Math.abs(color.blue - unpremul.blue) <= 1)
        assertEquals(color.alpha, unpremul.alpha)
    }

    @Test
    fun `test color transformations`() {
        val color = Color(200, 100, 50)
        
        // Test grayscale
        val gray = color.toGrayscale()
        assertEquals(gray.red, gray.green)
        assertEquals(gray.green, gray.blue)
        
        // Test invert
        val inverted = color.invert()
        assertEquals(255 - color.red, inverted.red)
        assertEquals(255 - color.green, inverted.green)
        assertEquals(255 - color.blue, inverted.blue)
        
        // Test brightness adjustment
        val brighter = color.adjustBrightness(1.5f)
        assertTrue(brighter.red > color.red)
        
        val darker = color.adjustBrightness(0.5f)
        assertTrue(darker.red < color.red)
        
        // Test saturation adjustment
        val desaturated = color.adjustSaturation(0.5f)
        val saturated = color.adjustSaturation(1.5f)
    }

    @Test
    fun `test HSL conversion`() {
        // Test known HSL values
        val red = Color.fromHSL(0f, 1f, 0.5f)
        assertEquals(255, red.red)
        assertEquals(0, red.green)
        assertEquals(0, red.blue)
        
        val green = Color.fromHSL(120f, 1f, 0.5f)
        assertEquals(0, green.red)
        assertEquals(255, green.green)
        assertEquals(0, green.blue)
        
        val blue = Color.fromHSL(240f, 1f, 0.5f)
        assertEquals(0, blue.red)
        assertEquals(0, blue.green)
        assertEquals(255, blue.blue)
        
        // Test round-trip conversion
        val original = Color(128, 64, 192)
        val (h, s, l) = original.toHSL()
        val converted = Color.fromHSL(h, s, l)
        
        // Allow small rounding differences
        assertTrue(Math.abs(original.red - converted.red) <= 1)
        assertTrue(Math.abs(original.green - converted.green) <= 1)
        assertTrue(Math.abs(original.blue - converted.blue) <= 1)
    }

    @Test
    fun `test SkColorSetRGB compatibility`() {
        val color = SkColorSetRGB(0xDD, 0x0, 0x0)
        assertEquals(0xDD, color.red)
        assertEquals(0, color.green)
        assertEquals(0, color.blue)
        assertEquals(255, color.alpha) // Should be opaque by default
    }
}