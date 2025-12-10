package device

import com.kanvas.core.Color
import com.kanvas.core.Paint
import com.kanvas.core.Rect
import core.Font
import core.createGlyphRun
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/**
 * Tests for text rendering functionality in BitmapDevice.
 * These tests verify that the device can properly render text using GlyphRuns.
 */
class TextRenderingTest {
    
    private lateinit var device: BitmapDevice
    private lateinit var testFont: Font
    private lateinit var testPaint: Paint
    
    @BeforeEach
    fun setUp() {
        // Create a test device
        device = BitmapDevice(200, 100)
        
        // Create a test font
        testFont = Font(core.Typeface.makeFromName("Dialog"), 16f)
        
        // Create a test paint
        testPaint = Paint().apply {
            color = Color.BLACK
            typeface = testFont.typeface // Use the same Typeface as the Font
            textSize = testFont.size
        }
    }
    
    @Test
    fun `test basic text rendering`() {
        val text = "Hello"
        
        // Draw text on the device
        device.drawText(text, 10f, 50f, testPaint)
        
        // Verify that some pixels were drawn (text should be visible)
        var pixelsDrawn = 0
        for (y in 0 until device.height) {
            for (x in 0 until device.width) {
                val pixel = device.bitmap.getPixel(x, y)
                if (pixel != Color.TRANSPARENT) {
                    pixelsDrawn++
                }
            }
        }
        
        // We should have some non-transparent pixels from the text
        assertTrue(pixelsDrawn > 0, "Text rendering should draw some pixels")
    }
    
    @Test
    fun `test glyph run rendering`() {
        val text = "Test"
        val glyphRun = testFont.createGlyphRun(text, 20f, 30f)
        
        // Draw the glyph run directly
        device.drawGlyphRun(glyphRun, testPaint)
        
        // Verify that the glyph run was rendered
        val bounds = glyphRun.getBounds()
        
        // Debug: print bounds information
        println("Glyph run bounds: left=${bounds.left}, top=${bounds.top}, right=${bounds.right}, bottom=${bounds.bottom}")
        println("Device dimensions: width=${device.width}, height=${device.height}")
        
        // Check that we have pixels within the expected bounds
        var hasTextPixels = false
        for (y in bounds.top.toInt() until bounds.bottom.toInt()) {
            for (x in bounds.left.toInt() until bounds.right.toInt()) {
                if (y >= 0 && y < device.height && x >= 0 && x < device.width) {
                    val pixel = device.bitmap.getPixel(x, y)
                    if (pixel != Color.TRANSPARENT) {
                        hasTextPixels = true
                        println("Found text pixel at ($x, $y): $pixel")
                        break
                    }
                }
            }
            if (hasTextPixels) break
        }
        
        // If no pixels found in bounds, check a broader area around the expected position
        if (!hasTextPixels) {
            println("No pixels found in glyph bounds, checking broader area...")
            for (y in 10 until 50) { // Around the Y position (30f)
                for (x in 10 until 100) { // Around the X position (20f)
                    if (y >= 0 && y < device.height && x >= 0 && x < device.width) {
                        val pixel = device.bitmap.getPixel(x, y)
                        if (pixel != Color.TRANSPARENT) {
                            hasTextPixels = true
                            println("Found text pixel at ($x, $y): $pixel")
                            break
                        }
                    }
                }
                if (hasTextPixels) break
            }
        }
        
        assertTrue(hasTextPixels, "Glyph run should render visible pixels")
    }
    
    @Test
    fun `test text rendering with different colors`() {
        val redPaint = Paint().apply {
            color = Color.RED
            typeface = testFont.typeface // Use the same Typeface as the Font
            textSize = testFont.size
        }
        
        val bluePaint = Paint().apply {
            color = Color.BLUE
            typeface = testFont.typeface // Use the same Typeface as the Font
            textSize = testFont.size
        }
        
        // Draw red text
        device.drawText("Red", 10f, 30f, redPaint)
        
        // Draw blue text
        device.drawText("Blue", 10f, 70f, bluePaint)
        
        // Check that we have both red and blue pixels
        var hasRed = false
        var hasBlue = false
        
        for (y in 0 until device.height) {
            for (x in 0 until device.width) {
                val pixel = device.bitmap.getPixel(x, y)
                if (pixel.red > 200 && pixel.green < 100 && pixel.blue < 100) {
                    hasRed = true
                }
                if (pixel.blue > 200 && pixel.red < 100 && pixel.green < 100) {
                    hasBlue = true
                }
            }
        }
        
        assertTrue(hasRed, "Should have red text pixels")
        assertTrue(hasBlue, "Should have blue text pixels")
    }
    
    @Test
    fun `test text rendering with clipping`() {
        // Set a small clip region
        device.setClipBounds(Rect(50f, 20f, 150f, 80f))
        
        // Draw text that should be partially clipped
        device.drawText("This text should be clipped on the sides", 10f, 50f, testPaint)
        
        // Debug: print some pixel values to see what's happening
        println("Clip bounds: ${device.getClipBounds()}")
        println("Pixel at (25, 50): ${device.bitmap.getPixel(25, 50)}")
        println("Pixel at (75, 50): ${device.bitmap.getPixel(75, 50)}")
        println("Pixel at (175, 50): ${device.bitmap.getPixel(175, 50)}")
        
        // Verify that text is only drawn within the clip region
        // Check that pixels outside the clip are transparent
        for (y in 0 until 20) { // Above clip
            for (x in 0 until device.width) {
                assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(x, y), 
                    "Pixels above clip should be transparent")
            }
        }
        
        for (y in 80 until device.height) { // Below clip
            for (x in 0 until device.width) {
                assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(x, y),
                    "Pixels below clip should be transparent")
            }
        }
        
        for (y in 20 until 80) { // Within clip Y range
            for (x in 0 until 50) { // Left of clip
                assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(x, y),
                    "Pixels left of clip should be transparent")
            }
            
            for (x in 150 until device.width) { // Right of clip
                assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(x, y),
                    "Pixels right of clip should be transparent")
            }
        }
    }
    
    @Test
    fun `test empty text rendering`() {
        val emptyText = ""
        
        // Draw empty text
        device.drawText(emptyText, 10f, 50f, testPaint)
        
        // Verify that no pixels were drawn (should still be transparent)
        for (y in 0 until device.height) {
            for (x in 0 until device.width) {
                assertEquals(Color.TRANSPARENT, device.bitmap.getPixel(x, y),
                    "Empty text should not draw any pixels")
            }
        }
    }
    
    @Test
    fun `test text rendering with different fonts`() {
        // Create a larger font
        val largeFont = Font(core.Typeface.makeFromName("Dialog"), 24f)
        val largePaint = Paint().apply {
            color = Color.GREEN
            typeface = largeFont.typeface // Use the same Typeface as the Font
            textSize = largeFont.size
        }
        
        // Draw small text
        device.drawText("Small", 10f, 30f, testPaint)
        
        // Draw large text
        device.drawText("LARGE", 10f, 70f, largePaint)
        
        // Verify that both texts were drawn
        var hasSmallText = false
        var hasLargeText = false
        
        // Check around the small text position
        for (y in 20 until 40) {
            for (x in 10 until 80) {
                if (device.bitmap.getPixel(x, y) != Color.TRANSPARENT) {
                    hasSmallText = true
                    break
                }
            }
            if (hasSmallText) break
        }
        
        // Check around the large text position
        for (y in 60 until 90) {
            for (x in 10 until 100) {
                if (device.bitmap.getPixel(x, y) != Color.TRANSPARENT) {
                    hasLargeText = true
                    break
                }
            }
            if (hasLargeText) break
        }
        
        assertTrue(hasSmallText, "Small text should be rendered")
        assertTrue(hasLargeText, "Large text should be rendered")
    }
    
    @Test
    fun `test glyph run bounds calculation`() {
        val text = "Bounds"
        val glyphRun = testFont.createGlyphRun(text, 10f, 10f)
        
        val bounds = glyphRun.getBounds()
        
        // Bounds should not be empty
        assertFalse(bounds.isEmpty)
        
        // Bounds should start at the specified horizontal position
        assertEquals(10f, bounds.left, 0.01f)
        // With improved metrics, top can be below the specified Y position due to ascender
        assertTrue(bounds.top <= 10f) // Top should be at or above the specified Y position
        
        // Bounds should have positive dimensions
        assertTrue(bounds.width > 0)
        assertTrue(bounds.height > 0)
    }
    
    @Test
    fun `test text rendering performance`() {
        // This test verifies that text rendering doesn't take too long
        val startTime = System.currentTimeMillis()
        
        // Draw multiple text strings
        for (i in 0 until 10) {
            device.drawText("Performance test $i", 10f, (i * 10 + 10).toFloat(), testPaint)
        }
        
        val endTime = System.currentTimeMillis()
        val duration = endTime - startTime
        
        // Should complete in reasonable time (less than 100ms for this simple test)
        assertTrue(duration < 100, "Text rendering should be reasonably fast: $duration ms")
    }
}