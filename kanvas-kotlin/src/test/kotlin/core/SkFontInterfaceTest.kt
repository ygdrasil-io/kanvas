package core

import com.kanvas.core.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import kotlin.math.abs

/**
 * Test suite for SkFontInterface implementation
 * Verifies that Kanvas Font implements all required Skia-compatible font methods
 */
class SkFontInterfaceTest {

    @Test
    fun `test SkFontInterface implementation`() {
        // Create a font and verify it implements the interface
        val font = Font()
        assertTrue(font is SkFontInterface, "Font should implement SkFontInterface")
    }

    @Test
    fun `test font properties`() {
        val typeface = Typeface.makeDefault()
        val font = Font(typeface, 24f, 1.5f, 0.2f, FontFlags.EMBOLDEN, FontEdging.ANTI_ALIAS, FontHinting.NORMAL)
        
        // Test property getters
        assertEquals(typeface, font.getTypeface())
        assertEquals(24f, font.getSize())
        assertEquals(1.5f, font.getScaleX())
        assertEquals(0.2f, font.getSkewX())
        assertEquals(FontEdging.ANTI_ALIAS, font.getEdging())
        assertEquals(FontHinting.NORMAL, font.getHinting())
        assertTrue(font.getFlags() and FontFlags.EMBOLDEN != 0)
    }

    @Test
    fun `test font creation`() {
        val typeface = Typeface.makeDefault()
        val font = Font(typeface, 16f)
        
        // Test makeWithSize
        val largerFont = font.makeWithSize(24f)
        assertEquals(24f, largerFont.getSize())
        assertEquals(font.getTypeface(), largerFont.getTypeface())
        
        // Test makeWithTypeface
        val newTypeface = Typeface.makeFromName("Arial", FontStyle.NORMAL)
        val newFont = font.makeWithTypeface(newTypeface)
        assertEquals(newTypeface, newFont.getTypeface())
        assertEquals(font.getSize(), newFont.getSize())
    }

    @Test
    fun `test text measurement`() {
        val font = Font(Typeface.makeDefault(), 16f)
        
        // Test measureText
        val width = font.measureText("Hello World")
        assertTrue(width > 0)
        
        // Test measureText with range
        val partialWidth = font.measureText("Hello World", 0, 5) // "Hello"
        assertTrue(partialWidth > 0)
        assertTrue(partialWidth < width)
        
        // Test getTextWidths
        val widths = font.getTextWidths("Hello")
        assertEquals(5, widths.size)
        assertTrue(widths.all { it > 0 })
        
        // Test getTextBounds
        val bounds = Rect()
        font.getTextBounds("Hello", bounds)
        assertTrue(bounds.width > 0)
        assertTrue(bounds.height > 0)
    }

    @Test
    fun `test glyph conversion`() {
        val font = Font(Typeface.makeDefault(), 16f)
        
        // Test unicharToGlyph
        val glyphA = font.unicharToGlyph('A')
        val glyphSpace = font.unicharToGlyph(' ')
        assertNotEquals(GlyphID.INVALID, glyphA)
        assertNotEquals(GlyphID.INVALID, glyphSpace)
        
        // Test textToGlyphs
        val glyphs = font.textToGlyphs("Hello")
        assertEquals(5, glyphs.size)
        assertTrue(glyphs.all { it != GlyphID.INVALID })
        
        // Test with range
        val partialGlyphs = font.textToGlyphs("Hello", 1, 3) // "el"
        assertEquals(2, partialGlyphs.size)
    }

    @Test
    fun `test font metrics`() {
        val font = Font(Typeface.makeDefault(), 16f)
        
        // Test getMetrics
        val metrics = font.getMetrics()
        assertTrue(metrics.ascent > 0)
        assertTrue(metrics.descent > 0)
        assertTrue(metrics.leading >= 0)
        
        // Test getSpacing
        val spacing = font.getSpacing()
        assertTrue(spacing >= 0)
        
        // Test getBounds
        val bounds = font.getBounds()
        assertTrue(bounds.width > 0)
        assertTrue(bounds.height > 0)
    }

    @Test
    fun `test text rendering properties`() {
        val font = Font(Typeface.makeDefault(), 16f)
        
        // Test hasSomeAntiAliasing
        assertTrue(font.hasSomeAntiAliasing())
        
        // Test anti-aliasing modes
        val noAAFont = Font(Typeface.makeDefault(), 16f, edging = FontEdging.ALIAS)
        assertFalse(noAAFont.hasSomeAntiAliasing())
        
        val subpixelFont = Font(Typeface.makeDefault(), 16f, edging = FontEdging.SUBPIXEL_ANTI_ALIAS)
        assertTrue(subpixelFont.hasSomeAntiAliasing())
        
        // Test flag manipulation
        val testFont = Font(Typeface.makeDefault(), 16f)
        testFont.setEmbolden(true)
        assertTrue(testFont.getEmbolden())
        
        testFont.setBaselineSnap(true)
        assertTrue(testFont.getBaselineSnap())
    }

    @Test
    fun `test text utilities`() {
        val font = Font(Typeface.makeDefault(), 16f)
        
        // Test countText
        assertEquals(5, font.countText("Hello"))
        assertEquals(0, font.countText(""))
        
        // Test getTextBlob
        val blob = font.getTextBlob("Hello")
        assertNotNull(blob)
        assertEquals("Hello", blob.getText())
        
        // Test getTextBlob with bounds
        val bounds = Rect(0f, 0f, 100f, 50f)
        val boundedBlob = font.getTextBlob("Hello", bounds)
        assertNotNull(boundedBlob)
        
        // Test getTextRun
        val run = font.getTextRun("Hello")
        assertNotNull(run)
        assertEquals("Hello", run.getText())
    }

    @Test
    fun `test text operations`() {
        val font = Font(Typeface.makeDefault(), 16f)
        
        // Test breakText
        val breakPos = font.breakText("Hello World", 50f)
        assertTrue(breakPos > 0)
        assertTrue(breakPos <= "Hello World".length)
        
        // Test with narrow width
        val narrowBreak = font.breakText("Hello World", 10f)
        assertTrue(narrowBreak >= 0)
        assertTrue(narrowBreak < "Hello World".length)
        
        // Test getTextXPos
        val xpos = FloatArray(5)
        font.getTextXPos("Hello", 10f, 20f, xpos)
        assertEquals(10f, xpos[0], 1e-5f)
        for (i in 1 until 5) {
            assertTrue(xpos[i] > xpos[i-1])
        }
        
        // Test getTextYPos
        val ypos = FloatArray(5)
        font.getTextYPos("Hello", 10f, 20f, ypos)
        assertTrue(ypos.all { abs(it - 20f) < 1e-5f })
    }

    @Test
    fun `test font comparison`() {
        val font1 = Font(Typeface.makeDefault(), 16f)
        val font2 = Font(Typeface.makeDefault(), 16f)
        val font3 = Font(Typeface.makeDefault(), 24f)
        
        // Test equality
        assertEquals(font1, font2)
        assertNotEquals(font1, font3)
        
        // Test hashCode
        assertEquals(font1.hashCode(), font2.hashCode())
        assertNotEquals(font1.hashCode(), font3.hashCode())
        
        // Test toString
        assertTrue(font1.toString().contains("Font"))
        assertTrue(font1.toString().contains("size=16.0"))
    }

    @Test
    fun `test Skia compatibility methods`() {
        val font = Font(Typeface.makeDefault(), 16f)
        
        // Test setEdging
        font.setEdging(FontEdging.SUBPIXEL_ANTI_ALIAS)
        assertEquals(FontEdging.SUBPIXEL_ANTI_ALIAS, font.getEdging())
        
        // Test setHinting
        font.setHinting(FontHinting.FULL)
        assertEquals(FontHinting.FULL, font.getHinting())
        
        // Test setVariations
        val variations = listOf(FontVariation("wght", 400f))
        font.setVariations(variations)
        // Note: In current implementation, variations don't affect the typeface
        
        // Test subpixel positioning
        font.setSubpixelPositioning(true)
        assertTrue(font.getSubpixelPositioning())
        
        font.setSubpixelPositioning(false)
        assertFalse(font.getSubpixelPositioning())
        
        // Test other getters (placeholders)
        assertFalse(font.getLCDRenderText())
        assertFalse(font.getAutohinted())
        assertFalse(font.getDisableHinting())
        assertFalse(font.getVertical())
        assertFalse(font.getUsePathBoundsForDrawing())
    }

    @Test
    fun `test text path generation`() {
        val font = Font(Typeface.makeDefault(), 24f)
        
        // Test getTextPath
        val path = font.getTextPath("Hello", 10f, 20f)
        assertFalse(path.isEmpty())
        
        // Verify the path has the expected verbs
        val verbs = path.getVerbs()
        assertTrue(verbs.isNotEmpty())
        
        // Verify the path bounds
        val bounds = path.getBounds()
        assertTrue(bounds.width > 0)
        assertTrue(bounds.height > 0)
        
        // Test with empty string
        val emptyPath = font.getTextPath("", 10f, 20f)
        assertTrue(emptyPath.isEmpty())
    }

    @Test
    fun `test Skia compatibility patterns`() {
        val font = Font(Typeface.makeDefault(), 16f)
        
        // Test creating fonts with different configurations
        val boldFont = Font(Typeface.makeFromName("Arial", FontStyle.BOLD), 18f)
        assertEquals(FontStyle.BOLD, boldFont.getTypeface().getStyle())
        assertEquals(18f, boldFont.getSize())
        
        // Test text measurement with different fonts
        val text = "Hello World"
        val width1 = font.measureText(text)
        val width2 = boldFont.measureText(text)
        
        // Bold font should generally be wider
        assertTrue(width2 >= width1)
        
        // Test font with scaling
        val scaledFont = Font(Typeface.makeDefault(), 16f, scaleX = 2f)
        val scaledWidth = scaledFont.measureText(text)
        assertTrue(scaledWidth > width1)
        
        // Test font with skewing
        val skewedFont = Font(Typeface.makeDefault(), 16f, skewX = 0.5f)
        val skewedWidth = skewedFont.measureText(text)
        // Skewed text should have different width
        assertNotEquals(width1, skewedWidth, 1e-5f)
    }

    @Test
    fun `test advanced text operations`() {
        val font = Font(Typeface.makeDefault(), 16f)
        
        // Test text with special characters
        val specialText = "Hello\nWorld\tTest"
        val glyphs = font.textToGlyphs(specialText)
        assertEquals(specialText.length, glyphs.size)
        
        // Test text with emoji (if supported)
        val emojiText = "Hello 😀"
        val emojiGlyphs = font.textToGlyphs(emojiText)
        // Should have glyphs for all characters, even if emoji is represented as multiple glyphs
        assertTrue(emojiGlyphs.isNotEmpty())
        
        // Test long text
        val longText = "A".repeat(1000)
        val longWidth = font.measureText(longText)
        assertTrue(longWidth > 0)
        
        // Test text with mixed scripts (if supported)
        val mixedText = "Hello 世界"
        val mixedWidth = font.measureText(mixedText)
        assertTrue(mixedWidth > 0)
    }
}