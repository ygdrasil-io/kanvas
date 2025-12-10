package core

import java.awt.GraphicsEnvironment
import java.awt.font.FontRenderContext
import java.awt.geom.AffineTransform
import java.awt.Font as AwtFont

/**
 * Implémentation de Typeface pour les polices système.
 * Utilise AWT pour accéder aux polices disponibles sur le système.
 * 
 * Cette classe fournit un pont entre les polices système et notre système de rendu.
 */
class SystemTypeface private constructor(
    val familyName: String,
    val style: Int,
    val size: Float
) : Typeface() {
    
    private val awtFont: AwtFont = AwtFont(familyName, style, size.toInt())
    
    companion object {
        /**
         * Crée une SystemTypeface à partir d'une famille de police et d'un style.
         * 
         * @param familyName Le nom de la famille de police (ex: "Arial", "Times New Roman")
         * @param style Le style de la police (utilise les constantes AWT: Font.PLAIN, Font.BOLD, etc.)
         * @param size La taille de la police
         * @return Une SystemTypeface ou EmptyTypeface si la police n'est pas disponible
         */
        fun create(familyName: String, style: Int = AwtFont.PLAIN, size: Float = 12f): Typeface {
            return try {
                // Vérifie si la police est disponible
                val availableFonts = GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames
                if (familyName in availableFonts) {
                    SystemTypeface(familyName, style, size)
                } else {
                    // Si la police n'est pas disponible, retourne une police par défaut
                    SystemTypeface("Dialog", style, size)
                }
            } catch (e: Exception) {
                EmptyTypeface
            }
        }
        
        /**
         * Crée une SystemTypeface à partir d'une AWT Font.
         */
        fun fromAwtFont(awtFont: AwtFont): Typeface {
            return SystemTypeface(awtFont.family, awtFont.style, awtFont.size.toFloat())
        }
        
        /**
         * Retourne la liste des polices système disponibles.
         */
        fun getAvailableFontFamilies(): List<String> {
            return try {
                GraphicsEnvironment.getLocalGraphicsEnvironment().availableFontFamilyNames.toList()
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
    
    /**
     * Convertit un caractère Unicode en ID de glyphe.
     * Cette implémentation utilise la police AWT pour obtenir le glyphe.
     */
    override fun unicharToGlyph(uni: Char): GlyphID {
        // Pour une implémentation réelle, nous aurions besoin d'accéder
        // aux tables de glyphes de la police, mais pour l'instant
        // nous retournons simplement le code du caractère
        return GlyphID(uni.code)
    }
    
    /**
     * Retourne les métriques de la police.
     * Utilise les métriques de l'AWT Font.
     */
    override fun getMetrics(): FontMetrics {
        val transform = AffineTransform()
        val fontRenderContext = FontRenderContext(transform, true, true)
        val awtMetrics = awtFont.getLineMetrics("ABC", fontRenderContext)
        
        return FontMetrics(
            top = 0f,
            ascent = awtMetrics.ascent.toFloat(),
            descent = awtMetrics.descent.toFloat(),
            bottom = 0f,
            leading = awtMetrics.leading.toFloat(),
            avgCharWidth = awtFont.getStringBounds("A", fontRenderContext).width.toFloat(),
            maxCharWidth = awtFont.getStringBounds("W", fontRenderContext).width.toFloat(),
            xMin = 0f,
            xMax = 0f,
            xHeight = awtMetrics.ascent.toFloat() * 0.5f, // Approximation
            capHeight = awtMetrics.ascent.toFloat(),
            underlineThickness = 1f,
            underlinePosition = awtMetrics.descent.toFloat()
        )
    }
    
    override fun toString(): String {
        val styleName = when (style) {
            AwtFont.PLAIN -> "PLAIN"
            AwtFont.BOLD -> "BOLD"
            AwtFont.ITALIC -> "ITALIC"
            AwtFont.BOLD + AwtFont.ITALIC -> "BOLD_ITALIC"
            else -> "UNKNOWN"
        }
        return "SystemTypeface(family='$familyName', style=$styleName, size=$size)"
    }
}