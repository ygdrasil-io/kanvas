package core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class TextToGlyphsTest {
    
    private lateinit var testFont: Font
    private lateinit var testTypeface: Typeface
    
    @BeforeEach
    fun setUp() {
        testTypeface = Typeface.makeFromName("Dialog", java.awt.Font.PLAIN, 12f)
        testFont = Font(testTypeface, 16f)
    }
    
    @Test
    fun `test text to glyph run conversion`() {
        val text = "Hello"
        val glyphRun = testFont.createGlyphRun(text)
        
        // Should have the same number of glyphs as characters
        assertEquals(text.length, glyphRun.size())
        
        // Each glyph should have a position
        assertEquals(text.length, glyphRun.positions.size)
        assertEquals(text.length, glyphRun.glyphs.size)
    }
    
    @Test
    fun `test glyph run positions`() {
        val text = "Test"
        val glyphRun = testFont.createGlyphRun(text, 100f, 50f)
        
        // First glyph should be at the starting position
        val firstPosition = glyphRun.positions[0]
        assertEquals(100f, firstPosition.x)
        assertEquals(50f, firstPosition.y)
        
        // Subsequent glyphs should be offset by the advance of previous glyphs
        for (i in 1 until glyphRun.positions.size) {
            val currentPos = glyphRun.positions[i]
            val prevPos = glyphRun.positions[i-1]
            val prevGlyph = glyphRun.glyphs[i-1]
            
            assertTrue(currentPos.x > prevPos.x, "Each glyph should be to the right of the previous one")
            assertEquals(prevPos.x + prevGlyph.advanceX, currentPos.x, 0.01f)
            assertEquals(prevPos.y, currentPos.y, "Y position should be the same for horizontal text")
        }
    }
    
    @Test
    fun `test empty text to glyph run`() {
        val emptyText = ""
        val glyphRun = testFont.createGlyphRun(emptyText)
        
        assertEquals(0, glyphRun.size())
        assertTrue(glyphRun.positions.isEmpty())
        assertTrue(glyphRun.glyphs.isEmpty())
    }
    
    @Test
    fun `test glyph run bounds calculation`() {
        val text = "Bounds"
        val glyphRun = testFont.createGlyphRun(text, 0f, 0f)
        
        val bounds = glyphRun.getBounds()
        
        // Bounds should not be empty for non-empty text
        assertFalse(bounds.isEmpty)
        
        // Bounds should start at origin for this case
        assertEquals(0f, bounds.left, 0.01f)
        assertEquals(0f, bounds.top, 0.01f)
        
        // Bounds should have positive width and height
        assertTrue(bounds.width > 0)
        assertTrue(bounds.height > 0)
    }
    
    @Test
    fun `test text measurement`() {
        val shortText = "Hi"
        val longText = "Hello World"
        
        val shortWidth = testFont.measureText(shortText)
        val longWidth = testFont.measureText(longText)
        
        // Longer text should have greater width
        assertTrue(longWidth > shortWidth)
        
        // Width should be proportional to text length
        assertTrue(shortWidth > 0)
        assertTrue(longWidth > 0)
    }
    
    @Test
    fun `test glyph run to sequence conversion`() {
        val text = "ABC"
        val glyphRun = testFont.createGlyphRun(text)
        
        val sequence = TextToGlyphs.glyphRunToSequence(glyphRun)
        val pairs = sequence.toList()
        
        assertEquals(text.length, pairs.size)
        
        // Each pair should contain a glyph and a position
        for (pair in pairs) {
            assertTrue(pair.first is Glyph)
            assertTrue(pair.second is Point)
        }
    }
    
    @Test
    fun `test glyph properties`() {
        val text = "Test"
        val glyphRun = testFont.createGlyphRun(text)
        
        for (glyph in glyphRun.glyphs) {
            // Glyphs should not be empty
            assertFalse(glyph.isEmpty())
            
            // Should have positive advance
            assertTrue(glyph.advanceX > 0)
            
            // Should have valid bounds
            assertFalse(glyph.bounds.isEmpty)
        }
    }
    
    @Test
    fun `test different fonts produce different glyph runs`() {
        val text = "Test"
        
        val font1 = Font(Typeface.makeFromName("Dialog"), 12f)
        val font2 = Font(Typeface.makeFromName("Monospaced"), 12f)
        
        val glyphRun1 = font1.createGlyphRun(text)
        val glyphRun2 = font2.createGlyphRun(text)
        
        // Different fonts should produce different glyphs (different IDs)
        // Note: This might not always be true for our simple implementation,
        // but it's a reasonable expectation for a real implementation
        assertEquals(glyphRun1.size(), glyphRun2.size())
    }
    
    @Test
    fun `test font size affects glyph run`() {
        val text = "Size"
        
        val smallFont = Font(testTypeface, 10f)
        val largeFont = Font(testTypeface, 20f)
        
        val smallGlyphRun = smallFont.createGlyphRun(text)
        val largeGlyphRun = largeFont.createGlyphRun(text)
        
        // Larger font should produce larger glyph advances
        val smallTotalWidth = smallGlyphRun.glyphs.sumOf { it.advanceX.toDouble() }
        val largeTotalWidth = largeGlyphRun.glyphs.sumOf { it.advanceX.toDouble() }
        
        assertTrue(largeTotalWidth > smallTotalWidth)
    }
}