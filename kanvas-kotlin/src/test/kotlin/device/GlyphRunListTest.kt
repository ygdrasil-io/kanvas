package device

import com.kanvas.core.Color
import com.kanvas.core.Paint
import core.Font
import core.GlyphRunList
import core.Typeface
import core.createGlyphRun
import org.junit.jupiter.api.Test

/**
 * Simple tests for GlyphRunList functionality
 */
class GlyphRunListTest {

    /**
     * Create a test font for testing purposes
     */
    private fun createTestFont(): Font {
        val typeface = Typeface.makeFromName("Dialog", java.awt.Font.PLAIN, 12f)
        return Font(typeface, 16f)
    }

    @Test
    fun `test onDrawGlyphRunList with empty list`() {
        val device = BitmapDevice(100, 100)
        val emptyList = GlyphRunList()
        val paint = Paint()

        // This should not throw any exceptions
        device.onDrawGlyphRunList(emptyList, paint)
    }

    @Test
    fun `test onDrawGlyphRunList with single GlyphRun`() {
        val device = BitmapDevice(100, 100)
        val testFont = createTestFont()
        val text = "Test"
        val glyphRun = testFont.createGlyphRun(text, 10f, 20f)
        val glyphRunList = GlyphRunList(glyphRun)
        val paint = Paint().apply {
            color = Color.RED
        }

        // This should not throw any exceptions
        device.onDrawGlyphRunList(glyphRunList, paint)
    }

    @Test
    fun `test onDrawGlyphRunList with multiple GlyphRuns`() {
        val device = BitmapDevice(100, 100)
        val testFont = createTestFont()
        val text1 = "Hello"
        val text2 = "World"
        val glyphRun1 = testFont.createGlyphRun(text1, 10f, 20f)
        val glyphRun2 = testFont.createGlyphRun(text2, 30f, 40f)
        val glyphRunList = GlyphRunList(listOf(glyphRun1, glyphRun2))
        val paint = Paint().apply {
            color = Color.BLUE
        }

        // This should not throw any exceptions
        device.onDrawGlyphRunList(glyphRunList, paint)
    }

    @Test
    fun `test onDrawGlyphRunList with clipping`() {
        val device = BitmapDevice(100, 100)
        val testFont = createTestFont()
        val text = "Test"
        val glyphRun = testFont.createGlyphRun(text, 10f, 20f)
        val glyphRunList = GlyphRunList(glyphRun)
        val paint = Paint().apply {
            color = Color.GREEN
        }

        // Set a small clip region that should clip most of the text
        device.setClipBounds(com.kanvas.core.Rect(5f, 15f, 15f, 25f))

        // This should not throw any exceptions and should handle clipping properly
        device.onDrawGlyphRunList(glyphRunList, paint)
    }
}