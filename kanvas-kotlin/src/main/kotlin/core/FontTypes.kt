package core

/**
 * Enumération des différents types d'edging (anti-aliasing) pour le texte.
 * Correspond à SkFont::Edging dans Skia.
 */
enum class FontEdging {
    /**
     * Pas d'anti-aliasing - rendu en aliasing pur
     */
    ALIAS,
    
    /**
     * Anti-aliasing standard
     */
    ANTI_ALIAS,
    
    /**
     * Anti-aliasing sous-pixel pour un rendu LCD de haute qualité
     */
    SUBPIXEL_ANTI_ALIAS
}

/**
 * Enumération des différents types de hinting pour le texte.
 * Correspond à SkFontHinting dans Skia.
 */
enum class FontHinting {
    /**
     * Pas de hinting
     */
    NONE,
    
    /**
     * Hinting léger
     */
    SLIGHT,
    
    /**
     * Hinting normal
     */
    NORMAL,
    
    /**
     * Hinting complet
     */
    FULL
}

/**
 * Constantes pour les flags privés de SkFont.
 * Ces flags contrôlent divers aspects du rendu de texte.
 */
object FontFlags {
    const val FORCE_AUTO_HINTING: Int = 1 shl 0
    const val EMBEDDED_BITMAPS: Int = 1 shl 1
    const val SUBPIXEL: Int = 1 shl 2
    const val LINEAR_METRICS: Int = 1 shl 3
    const val EMBOLDEN: Int = 1 shl 4
    const val BASELINE_SNAP: Int = 1 shl 5
}

/**
 * Constantes par défaut pour SkFont
 */
object FontDefaults {
    const val DEFAULT_SIZE: Float = 12f
    const val DEFAULT_SCALE_X: Float = 1f
    const val DEFAULT_SKEW_X: Float = 0f
    val DEFAULT_EDGING: FontEdging = FontEdging.ANTI_ALIAS
    val DEFAULT_HINTING: FontHinting = FontHinting.NORMAL
    const val DEFAULT_FLAGS: Int = FontFlags.BASELINE_SNAP
}