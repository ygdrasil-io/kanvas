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
     * Utilise les métriques de l'AWT Font avec des calculs plus précis.
     */
    override fun getMetrics(): FontMetrics {
        val transform = AffineTransform()
        val fontRenderContext = FontRenderContext(transform, true, true)
        val awtMetrics = awtFont.getLineMetrics("ABC", fontRenderContext)
        
        // Calculer les métriques plus précisément
        val ascent = awtMetrics.ascent.toFloat()
        val descent = awtMetrics.descent.toFloat()
        val leading = awtMetrics.leading.toFloat()
        
        // Calculer la hauteur de la ligne
        val lineHeight = ascent + descent + leading
        
        // Calculer les largeurs de caractères plus précisément
        val avgCharWidth = calculateAverageCharWidth(fontRenderContext)
        val maxCharWidth = calculateMaxCharWidth(fontRenderContext)
        
        // Calculer la hauteur x (hauteur des lettres minuscules)
        val xHeight = calculateXHeight(fontRenderContext)
        
        // Calculer la hauteur des majuscules
        val capHeight = calculateCapHeight(fontRenderContext)
        
        // Calculer l'épaisseur et la position du soulignement
        val underlineThickness = lineHeight * 0.05f // 5% de la hauteur de ligne
        val underlinePosition = ascent * 0.8f // 80% de l'ascender
        
        return FontMetrics(
            top = -ascent, // Position supérieure de la ligne
            ascent = ascent,
            descent = descent,
            bottom = descent, // Position inférieure de la ligne
            leading = leading,
            avgCharWidth = avgCharWidth,
            maxCharWidth = maxCharWidth,
            xMin = 0f, // À calculer plus précisément dans une implémentation complète
            xMax = maxCharWidth, // Largeur maximale comme approximation
            xHeight = xHeight,
            capHeight = capHeight,
            underlineThickness = underlineThickness,
            underlinePosition = underlinePosition
        )
    }
    
    /**
     * Calcule la largeur moyenne des caractères.
     */
    private fun calculateAverageCharWidth(fontRenderContext: FontRenderContext): Float {
        // Utiliser une sélection de caractères représentatifs
        val sampleChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        var totalWidth = 0f
        var charCount = 0
        
        for (char in sampleChars) {
            val bounds = awtFont.getStringBounds(char.toString(), fontRenderContext)
            totalWidth += bounds.width.toFloat()
            charCount++
        }
        
        return if (charCount > 0) totalWidth / charCount else awtFont.size2D.toFloat() * 0.6f
    }
    
    /**
     * Calcule la largeur maximale des caractères.
     */
    private fun calculateMaxCharWidth(fontRenderContext: FontRenderContext): Float {
        // Tester plusieurs caractères larges
        val wideChars = "MW@#$%&*()[]{}<>"
        var maxWidth = 0f
        
        for (char in wideChars) {
            val bounds = awtFont.getStringBounds(char.toString(), fontRenderContext)
            val width = bounds.width.toFloat()
            if (width > maxWidth) {
                maxWidth = width
            }
        }
        
        return maxWidth
    }
    
    /**
     * Calcule la hauteur x (hauteur des lettres minuscules).
     */
    private fun calculateXHeight(fontRenderContext: FontRenderContext): Float {
        // Utiliser des lettres minuscules représentatives
        val xChars = "abcdefghijklmnopqrstuvwxyz"
        var totalHeight = 0f
        var charCount = 0
        
        for (char in xChars) {
            val bounds = awtFont.getStringBounds(char.toString(), fontRenderContext)
            totalHeight += bounds.height.toFloat()
            charCount++
        }
        
        return if (charCount > 0) totalHeight / charCount else awtFont.size2D.toFloat() * 0.5f
    }
    
    /**
     * Calcule la hauteur des majuscules.
     */
    private fun calculateCapHeight(fontRenderContext: FontRenderContext): Float {
        // Utiliser des lettres majuscules représentatives
        val capChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        var totalHeight = 0f
        var charCount = 0
        
        for (char in capChars) {
            val bounds = awtFont.getStringBounds(char.toString(), fontRenderContext)
            totalHeight += bounds.height.toFloat()
            charCount++
        }
        
        return if (charCount > 0) totalHeight / charCount else awtFont.size2D.toFloat() * 0.7f
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