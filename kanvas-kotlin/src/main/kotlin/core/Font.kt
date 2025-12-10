package core

/**
 * Classe principale pour la gestion des polices et du texte dans Kanvas.
 * Correspond à SkFont dans Skia.
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
}