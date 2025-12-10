package core

import com.kanvas.core.Bitmap
import com.kanvas.core.CanvasFactory
import com.kanvas.core.Color
import com.kanvas.core.LinearGradientShader
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Point
import com.kanvas.core.Rect
import com.kanvas.core.Shaders
import com.kanvas.core.TileMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Tests for Shader functionality inspired by Skia
 */
class ShaderTest {

    @Test
    fun testColorShader() {
        val color = Color(255, 0, 0, 255) // Red
        val shader = Shaders.makeColorShader(color)
        
        // Should return the same color everywhere
        assertEquals(color, shader.shade(0f, 0f))
        assertEquals(color, shader.shade(100f, 200f))
        assertEquals(color, shader.shade(-50f, -100f))
    }

    @Test
    fun testLinearGradientShader() {
        val colors = listOf(Color.RED, Color.BLUE)
        val start = Point(0f, 0f)
        val end = Point(100f, 0f)
        val shader = Shaders.makeLinearGradient(colors, null, start, end)
        
        // Test at start point - should be red
        val startColor = shader.shade(0f, 0f)
        assertEquals(Color.RED, startColor)
        
        // Test at end point - should be blue
        val endColor = shader.shade(100f, 0f)
        assertEquals(Color.BLUE, endColor)
        
        // Test in middle - should be purple (mix of red and blue)
        val middleColor = shader.shade(50f, 0f)
        assertTrue(middleColor.red in 127..129) // Around 128
        assertTrue(middleColor.blue in 127..129) // Around 128
        assertEquals(0, middleColor.green) // Should be 0 for pure mix
    }

    @Test
    fun testLinearGradientWithPositions() {
        val colors = listOf(Color.RED, Color.GREEN, Color.BLUE)
        val positions = listOf(0f, 0.5f, 1f)
        val start = Point(0f, 0f)
        val end = Point(100f, 0f)
        val shader = Shaders.makeLinearGradient(colors, positions, start, end)
        
        // Test at 25% position (t=0.25) - with positions [0, 0.5, 1], this is 50% through first segment (red->green)
        val quarterColor = shader.shade(25f, 0f)
        // At 50% through red->green segment, we should have equal red and green (128, 128, 0)
        assertTrue(quarterColor.red == 128 && quarterColor.green == 128, "At 25% position (50% through first segment), should be equal red and green")
        assertTrue(quarterColor.blue == 0, "At 25%, blue should be 0")
        
        // Test at 75% position (t=0.75) - with positions [0, 0.5, 1], this is 50% through second segment (green->blue)
        val threeQuarterColor = shader.shade(75f, 0f)
        // At 50% through green->blue segment, we should have equal green and blue (0, 128, 128)
        assertTrue(threeQuarterColor.green == 128 && threeQuarterColor.blue == 128, "At 75% position (50% through second segment), should be equal green and blue")
        assertTrue(threeQuarterColor.red == 0, "At 75%, red should be 0")
        
        // Test at 10% position (t=0.1) - should be mostly red
        val tenPercentColor = shader.shade(10f, 0f)
        assertTrue(tenPercentColor.red > tenPercentColor.green, "At 10%, red should be stronger than green")
        assertTrue(tenPercentColor.blue == 0, "At 10%, blue should be 0")
    }

    @Test
    fun testRadialGradientShader() {
        val colors = listOf(Color.WHITE, Color.BLACK)
        val center = Point(50f, 50f)
        val radius = 50f
        val shader = Shaders.makeRadialGradient(colors, null, center, radius)
        
        // Test at center - should be white
        val centerColor = shader.shade(50f, 50f)
        assertEquals(Color.WHITE, centerColor)
        
        // Test at edge - should be black
        val edgeColor = shader.shade(100f, 50f)
        assertEquals(Color.BLACK, edgeColor)
        
        // Test outside radius - should be clamped to black
        val outsideColor = shader.shade(150f, 50f)
        assertEquals(Color.BLACK, outsideColor)
    }

    @Test
    fun testBitmapShader() {
        // Create a simple 2x2 bitmap
        val bitmap = Bitmap.create(2, 2)
        bitmap.setPixel(0, 0, Color.RED)
        bitmap.setPixel(1, 0, Color.GREEN)
        bitmap.setPixel(0, 1, Color.BLUE)
        bitmap.setPixel(1, 1, Color.WHITE)
        
        val shader = Shaders.makeBitmapShader(bitmap)
        
        // Test pixel access
        assertEquals(Color.RED, shader.shade(0f, 0f))
        assertEquals(Color.GREEN, shader.shade(1f, 0f))
        assertEquals(Color.BLUE, shader.shade(0f, 1f))
        assertEquals(Color.WHITE, shader.shade(1f, 1f))
        
        // Test repeat mode
        assertEquals(Color.RED, shader.shade(2f, 0f)) // Should repeat
        assertEquals(Color.RED, shader.shade(4f, 2f)) // Should repeat
    }

    @Test
    fun testShaderTileModes() {
        val colors = listOf(Color.RED, Color.BLUE)
        val start = Point(0f, 0f)
        val end = Point(1f, 0f)
        
        // Test CLAMP mode (default)
        val clampShader = Shaders.makeLinearGradient(colors, null, start, end, TileMode.CLAMP)
        assertEquals(Color.BLUE, clampShader.shade(2f, 0f)) // Clamped to end
        
        // Test REPEAT mode
        val repeatShader = Shaders.makeLinearGradient(colors, null, start, end, TileMode.REPEAT)
        assertEquals(Color.RED, repeatShader.shade(1f, 0f)) // Should repeat
        
        // Test MIRROR mode
        val mirrorShader = Shaders.makeLinearGradient(colors, null, start, end, TileMode.MIRROR)
        assertEquals(Color.BLUE, mirrorShader.shade(1f, 0f)) // Should mirror
    }

    @Test
    fun testShaderIntegrationWithCanvas() {
        val canvas = CanvasFactory.createRaster(100, 100)
        
        // Create a gradient shader
        val colors = listOf(Color.RED, Color.BLUE)
        val shader = Shaders.makeLinearGradient(
            colors, 
            null, 
            Point(0f, 0f), 
            Point(100f, 100f)
        )
        
        // Set the shader on the canvas
        canvas.setShader(shader)
        
        // Verify it's set
        assertNotNull(canvas.getShader())
        assertTrue(canvas.getShader() is LinearGradientShader)
        
        // Draw something with the shader
        val paint = Paint().apply {
            style = PaintStyle.FILL
        }
        
        // This should use the shader for filling
        canvas.drawRect(Rect(10f, 10f, 50f, 50f), paint)
        
        // Verify something was drawn
        val bitmap = canvas.bitmap
        var hasNonTransparent = false
        for (y in 0 until bitmap.getHeight()) {
            for (x in 0 until bitmap.getWidth()) {
                if (bitmap.getPixel(x, y).alpha > 0) {
                    hasNonTransparent = true
                    break
                }
            }
            if (hasNonTransparent) break
        }
        assertTrue(hasNonTransparent, "Shader should have drawn something")
    }

    @Test
    fun testColorLerp() {
        val red = Color(255, 0, 0)
        val blue = Color(0, 0, 255)
        
        // Test lerp at 0% - should be red
        val lerp0 = red.lerp(blue, 0f)
        assertEquals(red, lerp0)
        
        // Test lerp at 100% - should be blue
        val lerp1 = red.lerp(blue, 1f)
        assertEquals(blue, lerp1)
        
        // Test lerp at 50% - should be purple
        val lerp05 = red.lerp(blue, 0.5f)
        assertEquals(128, lerp05.red)
        assertEquals(0, lerp05.green)
        assertEquals(128, lerp05.blue)
        
        // Test lerp at 25%
        val lerp025 = red.lerp(blue, 0.25f)
        assertEquals(191, lerp025.red)
        assertEquals(0, lerp025.green)
        assertEquals(64, lerp025.blue)
    }
}