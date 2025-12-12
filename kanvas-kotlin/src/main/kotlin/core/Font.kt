package core

/**
 * Classe principale pour la gestion des polices et du texte dans Kanvas.
 * Correspond à SkFont dans Skia et implémente SkFontInterface.
 * 
 * Cette classe gère les propriétés de rendu du texte telles que la taille,
 * l'échelle, l'inclinaison, l'anti-aliasing et le hinting.
 * 
 * @property typeface La police utilisée pour le rendu
 * @property size La taille du texte en points
 * @property scaleX Le facteur d'échelle horizontal
 * @property skewX Le facteur d'inclinaison horizontal
 * @property flags Les flags de rendu (voir FontFlags)
 * @property edging Le type d'anti-aliasing (voir FontEdging)
 * @property hinting Le type de hinting (voir FontHinting)
 */
data class Font(
    var typeface: Typeface = Typeface.makeEmpty(),
    var size: Float = FontDefaults.DEFAULT_SIZE,
    var scaleX: Float = FontDefaults.DEFAULT_SCALE_X,
    var skewX: Float = FontDefaults.DEFAULT_SKEW_X,
    var flags: Int = FontDefaults.DEFAULT_FLAGS,
    var edging: FontEdging = FontDefaults.DEFAULT_EDGING,
    var hinting: FontHinting = FontDefaults.DEFAULT_HINTING
) {
    
    /**
     * Constructeurs secondaires pour une création plus simple
     */
    constructor(typeface: Typeface, size: Float) : this(typeface, size, 1f, 0f)
    constructor(typeface: Typeface) : this(typeface, FontDefaults.DEFAULT_SIZE)
    constructor() : this(Typeface.makeEmpty())
    
    /**
     * Crée une nouvelle Font avec une taille différente
     */
    fun makeWithSize(newSize: Float): Font {
        return Font(typeface, newSize, scaleX, skewX, flags, edging, hinting)
    }
    
    /**
     * Vérifie si cette Font a un certain niveau d'anti-aliasing
     */
    fun hasSomeAntiAliasing(): Boolean {
        return edging == FontEdging.ANTI_ALIAS || edging == FontEdging.SUBPIXEL_ANTI_ALIAS
    }
    
    /**
     * Convertit un caractère Unicode en ID de glyphe
     */
    fun unicharToGlyph(uni: Char): GlyphID {
        return typeface.unicharToGlyph(uni)
    }
    
    /**
     * Convertit une séquence de caractères Unicode en IDs de glyphes
     */
    fun unicharsToGlyphs(unis: List<Char>): List<GlyphID> {
        return typeface.unicharsToGlyphs(unis)
    }
    
    /**
     * Convertit du texte en IDs de glyphes
     */
    fun textToGlyphs(text: String): List<GlyphID> {
        return typeface.textToGlyphs(text)
    }
    
    /**
     * Retourne les métriques de la police
     */
    fun getMetrics(): FontMetrics {
        return typeface.getMetrics()
    }
    
    // Méthodes de configuration des flags
    
    fun setForceAutoHinting(predicate: Boolean) {
        flags = setClearMask(flags, predicate, FontFlags.FORCE_AUTO_HINTING)
    }
    
    fun setEmbeddedBitmaps(predicate: Boolean) {
        flags = setClearMask(flags, predicate, FontFlags.EMBEDDED_BITMAPS)
    }
    
    fun setSubpixel(predicate: Boolean) {
        flags = setClearMask(flags, predicate, FontFlags.SUBPIXEL)
    }
    
    fun setLinearMetrics(predicate: Boolean) {
        flags = setClearMask(flags, predicate, FontFlags.LINEAR_METRICS)
    }
    
    fun setEmbolden(predicate: Boolean) {
        flags = setClearMask(flags, predicate, FontFlags.EMBOLDEN)
    }
    
    fun setBaselineSnap(predicate: Boolean) {
        flags = setClearMask(flags, predicate, FontFlags.BASELINE_SNAP)
    }
    
    /**
     * Fonction utilitaire pour définir/effacer des bits dans un masque
     */
    private fun setClearMask(bits: Int, cond: Boolean, mask: Int): Int {
        return if (cond) bits or mask else bits and mask.inv()
    }
    
    /**
     * Retourne une représentation textuelle de cette Font
     */
    override fun toString(): String {
        return "Font(typeface=$typeface, size=$size, scaleX=$scaleX, skewX=$skewX, " +
               "edging=$edging, hinting=$hinting, flags=0x${flags.toString(16)})"
    }
    
    /**
     * Compare cette Font avec une autre pour l'égalité
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Font) return false
        
        return typeface == other.typeface &&
               size == other.size &&
               scaleX == other.scaleX &&
               skewX == other.skewX &&
               flags == other.flags &&
               edging == other.edging &&
               hinting == other.hinting
    }
    
    /**
     * Calcule le hash code pour cette Font
     */
    override fun hashCode(): Int {
        var result = typeface.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + scaleX.hashCode()
        result = 31 * result + skewX.hashCode()
        result = 31 * result + flags
        result = 31 * result + edging.hashCode()
        result = 31 * result + hinting.hashCode()
        return result
    }
    
    /**
     * Creates a new Font with the specified typeface
     */
    fun makeWithTypeface(newTypeface: Typeface): Font {
        return Font(newTypeface, size, scaleX, skewX, flags, edging, hinting)
    }
    
    /**
     * Measures the width of the specified text
     */
    fun measureText(text: String): Float {
        return measureText(text, 0, text.length)
    }
    
    /**
     * Measures the width of the specified text range
     */
    fun measureText(text: String, start: Int, end: Int): Float {
        val glyphs = textToGlyphs(text.substring(start, end))
        var width = 0f
        var lastGlyph: GlyphID? = null
        
        for (glyph in glyphs) {
            val advance = typeface.getGlyphWidth(glyph, lastGlyph)
            width += advance * scaleX
            lastGlyph = glyph
        }
        
        return width
    }
    
    /**
     * Gets the widths of each character in the text
     */
    fun getTextWidths(text: String): FloatArray {
        val widths = FloatArray(text.length)
        var lastGlyph: GlyphID? = null
        
        for (i in text.indices) {
            val glyph = unicharToGlyph(text[i])
            val advance = typeface.getGlyphWidth(glyph, lastGlyph)
            widths[i] = advance * scaleX
            lastGlyph = glyph
        }
        
        return widths
    }
    
    /**
     * Gets the bounds of the specified text
     */
    fun getTextBounds(text: String, bounds: Rect) {
        val metrics = getMetrics()
        val width = measureText(text)
        
        bounds.set(
            0f,
            -metrics.ascent,
            width,
            metrics.descent
        )
    }
    
    /**
     * Gets the path for the specified text
     */
    fun getTextPath(text: String, x: Float, y: Float): Path {
        val path = Path()
        val glyphs = textToGlyphs(text)
        var currentX = x
        var currentY = y
        var lastGlyph: GlyphID? = null
        
        for (glyph in glyphs) {
            val glyphPath = typeface.getGlyphPath(glyph)
            if (glyphPath != null) {
                val transformedPath = glyphPath.copy()
                transformedPath.offset(currentX, currentY)
                if (path.isEmpty()) {
                    path.moveTo(transformedPath.getPoints()[0].x, transformedPath.getPoints()[0].y)
                }
                // Add the glyph path to the main path
                for (verbIndex in transformedPath.getVerbs().indices) {
                    when (transformedPath.getVerbs()[verbIndex]) {
                        PathVerb.MOVE -> {
                            val point = transformedPath.getPoints()[verbIndex]
                            path.moveTo(point.x, point.y)
                        }
                        PathVerb.LINE -> {
                            val point = transformedPath.getPoints()[verbIndex]
                            path.lineTo(point.x, point.y)
                        }
                        PathVerb.QUAD -> {
                            if (verbIndex + 1 < transformedPath.getPoints().size) {
                                val p1 = transformedPath.getPoints()[verbIndex]
                                val p2 = transformedPath.getPoints()[verbIndex + 1]
                                path.quadTo(p1.x, p1.y, p2.x, p2.y)
                            }
                        }
                        PathVerb.CUBIC -> {
                            if (verbIndex + 2 < transformedPath.getPoints().size) {
                                val p1 = transformedPath.getPoints()[verbIndex]
                                val p2 = transformedPath.getPoints()[verbIndex + 1]
                                val p3 = transformedPath.getPoints()[verbIndex + 2]
                                path.cubicTo(p1.x, p1.y, p2.x, p2.y, p3.x, p3.y)
                            }
                        }
                        PathVerb.CLOSE -> {
                            path.close()
                        }
                        PathVerb.CONIC -> {
                            if (verbIndex + 1 < transformedPath.getPoints().size) {
                                val p1 = transformedPath.getPoints()[verbIndex]
                                val p2 = transformedPath.getPoints()[verbIndex + 1]
                                val conicCount = transformedPath.getVerbs().take(verbIndex).count { it == PathVerb.CONIC }
                                val weight = typeface.getConicWeight(conicCount)
                                path.conicTo(p1.x, p1.y, p2.x, p2.y, weight)
                            }
                        }
                    }
                }
            }
            
            // Update position for next glyph
            val advance = typeface.getGlyphWidth(glyph, lastGlyph)
            currentX += advance * scaleX
            lastGlyph = glyph
        }
        
        return path
    }
    
    /**
     * Gets the spacing for this font
     */
    fun getSpacing(): Float {
        return typeface.getMetrics().spacing
    }
    
    /**
     * Gets the bounds for this font
     */
    fun getBounds(): Rect {
        val metrics = getMetrics()
        return Rect(
            0f,
            -metrics.ascent,
            typeface.getMetrics().maxWidth,
            metrics.descent
        )
    }
    
    /**
     * Counts the number of characters in the text
     */
    fun countText(text: String): Int {
        return text.length
    }
    
    /**
     * Creates a text blob from the specified text
     */
    fun getTextBlob(text: String): TextBlob {
        return TextBlob(this, text)
    }
    
    /**
     * Creates a text blob from the specified text with bounds
     */
    fun getTextBlob(text: String, bounds: Rect): TextBlob {
        return TextBlob(this, text, bounds)
    }
    
    /**
     * Creates a text run from the specified text
     */
    fun getTextRun(text: String): TextRun {
        return TextRun(this, text)
    }
    
    /**
     * Creates a text run from the specified text with bounds
     */
    fun getTextRun(text: String, bounds: Rect): TextRun {
        return TextRun(this, text, bounds)
    }
    
    /**
     * Breaks the text to fit within the specified width
     */
    fun breakText(text: String, maxWidth: Float): Int {
        var width = 0f
        var lastBreak = 0
        var lastGlyph: GlyphID? = null
        
        for (i in text.indices) {
            val glyph = unicharToGlyph(text[i])
            val advance = typeface.getGlyphWidth(glyph, lastGlyph)
            
            if (width + advance * scaleX > maxWidth) {
                return lastBreak
            }
            
            width += advance * scaleX
            lastGlyph = glyph
            lastBreak = i + 1
        }
        
        return text.length
    }
    
    /**
     * Gets the text intercepts (not fully implemented)
     */
    fun getTextIntercepts(text: String, x: Float, y: Float, bounds: Rect, intercepts: FloatArray): Int {
        // Placeholder implementation
        return 0
    }
    
    /**
     * Gets the text positions (not fully implemented)
     */
    fun getTextPos(text: String, x: Float, y: Float, pos: FloatArray) {
        // Placeholder implementation
    }
    
    /**
     * Gets the text X positions
     */
    fun getTextXPos(text: String, x: Float, y: Float, xpos: FloatArray) {
        val widths = getTextWidths(text)
        var currentX = x
        
        for (i in widths.indices) {
            xpos[i] = currentX
            currentX += widths[i]
        }
    }
    
    /**
     * Gets the text Y positions (not fully implemented)
     */
    fun getTextYPos(text: String, x: Float, y: Float, ypos: FloatArray) {
        // Placeholder implementation - all Y positions are the same for now
        ypos.fill(y)
    }
    
    /**
     * Sets the edging mode
     */
    fun setEdging(edging: FontEdging) {
        this.edging = edging
    }
    
    /**
     * Sets the hinting mode
     */
    fun setHinting(hinting: FontHinting) {
        this.hinting = hinting
    }
    
    /**
     * Sets font variations (placeholder)
     */
    fun setVariations(variations: List<FontVariation>) {
        // Placeholder - in a real implementation, this would affect the typeface
    }
    
    /**
     * Gets font variations (placeholder)
     */
    fun getVariations(): List<FontVariation> {
        return emptyList() // Placeholder
    }
    
    /**
     * Sets subpixel positioning
     */
    fun setSubpixelPositioning(subpixel: Boolean) {
        setSubpixel(subpixel)
    }
    
    /**
     * Gets subpixel positioning
     */
    fun getSubpixelPositioning(): Boolean {
        return (flags and FontFlags.SUBPIXEL) != 0
    }
    
    /**
     * Sets LCD render text (placeholder)
     */
    fun setLCDRenderText(lcdRenderText: Boolean) {
        // Placeholder
    }
    
    /**
     * Gets LCD render text (placeholder)
     */
    fun getLCDRenderText(): Boolean {
        return false // Placeholder
    }
    
    /**
     * Sets autohinted (placeholder)
     */
    fun setAutohinted(autohinted: Boolean) {
        setForceAutoHinting(autohinted)
    }
    
    /**
     * Gets autohinted (placeholder)
     */
    fun getAutohinted(): Boolean {
        return getForceAutoHinting()
    }
    
    /**
     * Gets embolden
     */
    fun getEmbolden(): Boolean {
        return (flags and FontFlags.EMBOLDEN) != 0
    }
    
    /**
     * Gets baseline snap
     */
    fun getBaselineSnap(): Boolean {
        return (flags and FontFlags.BASELINE_SNAP) != 0
    }
    
    /**
     * Gets disable hinting
     */
    fun getDisableHinting(): Boolean {
        return false // Placeholder
    }
    
    /**
     * Gets vertical
     */
    fun getVertical(): Boolean {
        return false // Placeholder
    }
    
    /**
     * Gets use path bounds for drawing
     */
    fun getUsePathBoundsForDrawing(): Boolean {
        return false // Placeholder
    }
}