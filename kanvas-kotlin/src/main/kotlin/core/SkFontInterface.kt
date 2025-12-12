package core

/**
 * SkFontInterface defines the complete font interface compatible with Skia's SkFont
 * This interface ensures that Kanvas fonts have all the methods available in Skia
 */
interface SkFontInterface {
    
    /**
     * Font Properties
     */
    fun getTypeface(): Typeface
    fun getSize(): Float
    fun getScaleX(): Float
    fun getSkewX(): Float
    fun getEdging(): FontEdging
    fun getHinting(): FontHinting
    fun getFlags(): Int
    
    /**
     * Font Creation
     */
    fun makeWithSize(newSize: Float): Font
    fun makeWithTypeface(newTypeface: Typeface): Font
    
    /**
     * Text Measurement
     */
    fun measureText(text: String): Float
    fun measureText(text: String, start: Int, end: Int): Float
    fun getTextWidths(text: String): FloatArray
    fun getTextBounds(text: String, bounds: Rect)
    fun getTextPath(text: String, x: Float, y: Float): Path
    
    /**
     * Glyph Conversion
     */
    fun unicharToGlyph(uni: Char): GlyphID
    fun unicharsToGlyphs(unis: List<Char>): List<GlyphID>
    fun textToGlyphs(text: String): List<GlyphID>
    fun textToGlyphs(text: String, start: Int, end: Int): List<GlyphID>
    
    /**
     * Font Metrics
     */
    fun getMetrics(): FontMetrics
    fun getSpacing(): Float
    fun getBounds(): Rect
    
    /**
     * Text Rendering
     */
    fun hasSomeAntiAliasing(): Boolean
    fun setForceAutoHinting(predicate: Boolean)
    fun setEmbeddedBitmaps(predicate: Boolean)
    fun setSubpixel(predicate: Boolean)
    fun setLinearMetrics(predicate: Boolean)
    fun setEmbolden(predicate: Boolean)
    fun setBaselineSnap(predicate: Boolean)
    fun setDisableHinting(predicate: Boolean)
    fun setVertical(predicate: Boolean)
    fun setUsePathBoundsForDrawing(predicate: Boolean)
    
    /**
     * Text Utilities
     */
    fun countText(text: String): Int
    fun getTextBlob(text: String): TextBlob
    fun getTextBlob(text: String, bounds: Rect): TextBlob
    fun getTextRun(text: String): TextRun
    fun getTextRun(text: String, bounds: Rect): TextRun
    
    /**
     * Advanced Text Operations
     */
    fun breakText(text: String, maxWidth: Float): Int
    fun getTextIntercepts(text: String, x: Float, y: Float, bounds: Rect, intercepts: FloatArray): Int
    fun getTextPos(text: String, x: Float, y: Float, pos: FloatArray)
    fun getTextXPos(text: String, x: Float, y: Float, xpos: FloatArray)
    fun getTextYPos(text: String, x: Float, y: Float, ypos: FloatArray)
    
    /**
     * Font Comparison
     */
    fun equals(other: Any?): Boolean
    fun hashCode(): Int
    
    /**
     * Skia-compatible Methods
     */
    fun setEdging(edging: FontEdging)
    fun setHinting(hinting: FontHinting)
    fun setVariations(variations: List<FontVariation>)
    fun getVariations(): List<FontVariation>
    fun setSubpixelPositioning(subpixel: Boolean)
    fun getSubpixelPositioning(): Boolean
    fun setLCDRenderText(lcdRenderText: Boolean)
    fun getLCDRenderText(): Boolean
    fun setAutohinted(autohinted: Boolean)
    fun getAutohinted(): Boolean
    fun setEmbolden(embolden: Boolean)
    fun getEmbolden(): Boolean
    fun setBaselineSnap(baselineSnap: Boolean)
    fun getBaselineSnap(): Boolean
    fun setForceAutoHinting(forceAutoHinting: Boolean)
    fun getForceAutoHinting(): Boolean
    fun setLinearMetrics(linearMetrics: Boolean)
    fun getLinearMetrics(): Boolean
    fun setEmbeddedBitmaps(embeddedBitmaps: Boolean)
    fun getEmbeddedBitmaps(): Boolean
    fun setDisableHinting(disableHinting: Boolean)
    fun getDisableHinting(): Boolean
    fun setVertical(vertical: Boolean)
    fun getVertical(): Boolean
    fun setUsePathBoundsForDrawing(usePathBoundsForDrawing: Boolean)
    fun getUsePathBoundsForDrawing(): Boolean
}