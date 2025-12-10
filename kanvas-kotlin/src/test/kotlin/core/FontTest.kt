package core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FontTest {
    
    private lateinit var testFont: Font
    private lateinit var testTypeface: Typeface
    
    @BeforeEach
    fun setUp() {
        testTypeface = Typeface.makeFromFile("test.ttf") // Cela créera un FileTypeface ou EmptyTypeface
        testFont = Font(testTypeface, 16f)
    }
    
    @Test
    fun `test Font creation with default values`() {
        val defaultFont = Font()
        assertEquals(FontDefaults.DEFAULT_SIZE, defaultFont.size)
        assertEquals(FontDefaults.DEFAULT_SCALE_X, defaultFont.scaleX)
        assertEquals(FontDefaults.DEFAULT_SKEW_X, defaultFont.skewX)
        assertEquals(FontDefaults.DEFAULT_EDGING, defaultFont.edging)
        assertEquals(FontDefaults.DEFAULT_HINTING, defaultFont.hinting)
        assertEquals(FontDefaults.DEFAULT_FLAGS, defaultFont.flags)
        assertTrue(defaultFont.typeface is EmptyTypeface)
    }
    
    @Test
    fun `test Font creation with custom values`() {
        val customFont = Font(testTypeface, 24f, 1.5f, 0.2f)
        assertEquals(24f, customFont.size)
        assertEquals(1.5f, customFont.scaleX)
        assertEquals(0.2f, customFont.skewX)
        assertEquals(testTypeface, customFont.typeface)
    }
    
    @Test
    fun `test makeWithSize creates new Font with different size`() {
        val originalSize = testFont.size
        val newFont = testFont.makeWithSize(20f)
        
        assertEquals(20f, newFont.size)
        assertEquals(originalSize, testFont.size) // Original should be unchanged
        assertEquals(testFont.typeface, newFont.typeface)
        assertEquals(testFont.scaleX, newFont.scaleX)
        assertEquals(testFont.skewX, newFont.skewX)
    }
    
    @Test
    fun `test hasSomeAntiAliasing with different edging modes`() {
        // Test with ANTI_ALIAS
        val antiAliasFont = Font(typeface = testTypeface, edging = FontEdging.ANTI_ALIAS)
        assertTrue(antiAliasFont.hasSomeAntiAliasing())
        
        // Test with SUBPIXEL_ANTI_ALIAS
        val subpixelFont = Font(typeface = testTypeface, edging = FontEdging.SUBPIXEL_ANTI_ALIAS)
        assertTrue(subpixelFont.hasSomeAntiAliasing())
        
        // Test with ALIAS
        val aliasFont = Font(typeface = testTypeface, edging = FontEdging.ALIAS)
        assertFalse(aliasFont.hasSomeAntiAliasing())
    }
    
    @Test
    fun `test textToGlyphs conversion`() {
        val text = "Hello"
        val glyphIds = testFont.textToGlyphs(text)
        
        assertEquals(text.length, glyphIds.size)
        
        // Test individual character conversion
        val expectedGlyphIds = text.map { GlyphID(it.code) }
        assertEquals(expectedGlyphIds, glyphIds)
    }
    
    @Test
    fun `test unicharToGlyph conversion`() {
        val char = 'A'
        val glyphId = testFont.unicharToGlyph(char)
        assertEquals(GlyphID(char.code), glyphId)
    }
    
    @Test
    fun `test Font equality`() {
        val font1 = Font(testTypeface, 16f, 1f, 0f, FontDefaults.DEFAULT_FLAGS, 
                        FontEdging.ANTI_ALIAS, FontHinting.NORMAL)
        val font2 = Font(testTypeface, 16f, 1f, 0f, FontDefaults.DEFAULT_FLAGS,
                        FontEdging.ANTI_ALIAS, FontHinting.NORMAL)
        
        assertEquals(font1, font2)
        assertEquals(font1.hashCode(), font2.hashCode())
    }
    
    @Test
    fun `test Font inequality`() {
        val font1 = Font(testTypeface, 16f)
        val font2 = Font(testTypeface, 20f)
        
        assertNotEquals(font1, font2)
    }
    
    @Test
    fun `test Font flags manipulation`() {
        val font = Font()
        
        // Test initial state
        assertFalse(font.flags and FontFlags.FORCE_AUTO_HINTING != 0)
        
        // Test setting flag
        font.setForceAutoHinting(true)
        assertTrue(font.flags and FontFlags.FORCE_AUTO_HINTING != 0)
        
        // Test clearing flag
        font.setForceAutoHinting(false)
        assertFalse(font.flags and FontFlags.FORCE_AUTO_HINTING != 0)
    }
    
    @Test
    fun `test Font toString`() {
        val fontString = testFont.toString()
        assertTrue(fontString.contains("Font("))
        assertTrue(fontString.contains("size=${testFont.size}"))
        assertTrue(fontString.contains("typeface=${testFont.typeface}"))
    }
    
    @Test
    fun `test Typeface creation`() {
        // Test empty typeface
        val emptyTypeface = Typeface.makeEmpty()
        assertTrue(emptyTypeface is EmptyTypeface)
        
        // Test file typeface creation
        val fileTypeface = Typeface.makeFromFile("nonexistent.ttf")
        // Should return EmptyTypeface for non-existent file
        assertTrue(fileTypeface is EmptyTypeface)
    }
    
    @Test
    fun `test SystemTypeface creation`() {
        // Test creation with default system font
        val systemTypeface = Typeface.makeFromName("Dialog")
        assertTrue(systemTypeface is SystemTypeface)
        
        // Test with different styles
        val boldTypeface = Typeface.makeFromName("Dialog", java.awt.Font.BOLD)
        assertTrue(boldTypeface is SystemTypeface)
        
        val italicTypeface = Typeface.makeFromName("Dialog", java.awt.Font.ITALIC)
        assertTrue(italicTypeface is SystemTypeface)
    }
    
    @Test
    fun `test SystemTypeface metrics`() {
        val systemTypeface = Typeface.makeFromName("Dialog", java.awt.Font.PLAIN, 12f)
        
        if (systemTypeface is SystemTypeface) {
            val metrics = systemTypeface.getMetrics()
            
            // Basic checks - metrics should not be all zeros for a valid font
            assertTrue(metrics.ascent > 0, "Ascent should be positive")
            assertTrue(metrics.descent >= 0, "Descent should be non-negative")
            assertTrue(metrics.avgCharWidth > 0, "Average char width should be positive")
            
            // Cap height should be reasonable
            assertTrue(metrics.capHeight > 0, "Cap height should be positive")
        }
    }
    
    @Test
    fun `test available font families`() {
        val availableFonts = Typeface.getAvailableFontFamilies()
        
        // Should return some fonts (at least the default ones)
        assertTrue(availableFonts.isNotEmpty(), "Should have at least some system fonts available")
        
        // Common fonts that should be available on most systems
        val commonFonts = listOf("Dialog", "Monospaced", "Serif", "SansSerif")
        val foundCommonFonts = availableFonts.intersect(commonFonts.toSet())
        
        // At least one common font should be available
        assertTrue(foundCommonFonts.isNotEmpty(), "Should have at least one common font available")
    }
    
    @Test
    fun `test Font with SystemTypeface`() {
        val systemTypeface = Typeface.makeFromName("Dialog", java.awt.Font.BOLD, 18f)
        val font = Font(systemTypeface, 24f)
        
        assertEquals(systemTypeface, font.typeface)
        assertEquals(24f, font.size)
        
        // Test text to glyphs conversion
        val glyphIds = font.textToGlyphs("Test")
        assertEquals(4, glyphIds.size)
    }
    
    @Test
    fun `test FontMetrics creation`() {
        val metrics = FontMetrics()
        assertEquals(0f, metrics.ascent)
        assertEquals(0f, metrics.descent)
        assertEquals(0f, metrics.leading)
        
        val customMetrics = FontMetrics(
            top = 10f,
            ascent = 15f,
            descent = 5f,
            bottom = -5f,
            leading = 2f
        )
        assertEquals(10f, customMetrics.top)
        assertEquals(15f, customMetrics.ascent)
        assertEquals(5f, customMetrics.descent)
        assertEquals(-5f, customMetrics.bottom)
        assertEquals(2f, customMetrics.leading)
    }
}