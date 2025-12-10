package core

import java.io.File

/**
 * Classe de base pour les polices (typefaces) dans Kanvas.
 * Correspond à SkTypeface dans Skia.
 * 
 * Une Typeface représente une famille de police avec un style spécifique.
 */
sealed class Typeface {
    
    /**
     * Crée une Typeface vide (par défaut).
     * Correspond à SkTypeface::MakeEmpty() dans Skia.
     */
    companion object {
        fun makeEmpty(): Typeface {
            return EmptyTypeface
        }
        
        /**
         * Crée une Typeface à partir d'un fichier de police.
         * Cette méthode est une implémentation de base et devrait être
         * remplacée par des implémentations spécifiques à la plateforme.
         */
        fun makeFromFile(filePath: String): Typeface {
            return try {
                FileTypeface(File(filePath))
            } catch (e: Exception) {
                EmptyTypeface
            }
        }
        
        /**
         * Crée une Typeface à partir d'une police système.
         * Utilise l'implémentation SystemTypeface pour accéder aux polices système.
         * 
         * @param familyName Le nom de la famille de police (ex: "Arial", "Times New Roman")
         * @param style Le style de la police (utilise les constantes AWT: Font.PLAIN, Font.BOLD, etc.)
         * @param size La taille de la police
         */
        fun makeFromName(familyName: String, style: Int = java.awt.Font.PLAIN, size: Float = 12f): Typeface {
            return SystemTypeface.create(familyName, style, size)
        }
        
        /**
         * Crée une Typeface à partir d'une AWT Font.
         * Utile pour l'interopérabilité avec les bibliothèques Java existantes.
         */
        fun fromAwtFont(awtFont: java.awt.Font): Typeface {
            return SystemTypeface.fromAwtFont(awtFont)
        }
        
        /**
         * Retourne la liste des polices système disponibles.
         */
        fun getAvailableFontFamilies(): List<String> {
            return SystemTypeface.getAvailableFontFamilies()
        }
    }
    
    /**
     * Convertit un caractère Unicode en ID de glyphe.
     * Cette méthode devrait être implémentée par les sous-classes.
     */
    open fun unicharToGlyph(uni: Char): GlyphID {
        // Implémentation de base - retourne simplement le code du caractère
        return GlyphID(uni.code)
    }
    
    /**
     * Convertit une séquence de caractères Unicode en IDs de glyphes.
     * Cette méthode devrait être implémentée par les sous-classes.
     */
    open fun unicharsToGlyphs(unis: List<Char>): List<GlyphID> {
        return unis.map { unicharToGlyph(it) }
    }
    
    /**
     * Convertit du texte en IDs de glyphes.
     * Cette méthode devrait être implémentée par les sous-classes.
     */
    open fun textToGlyphs(text: String): List<GlyphID> {
        return text.map { unicharToGlyph(it) }
    }
    
    /**
     * Retourne les métriques de la police.
     * Cette méthode devrait être implémentée par les sous-classes.
     */
    open fun getMetrics(): FontMetrics {
        return FontMetrics() // Retourne des métriques par défaut
    }
}

/**
 * Typeface vide - utilisée comme valeur par défaut.
 */
object EmptyTypeface : Typeface() {
    override fun toString(): String {
        return "EmptyTypeface"
    }
}

/**
 * Typeface basée sur un fichier.
 * Cette implémentation est basique et devrait être améliorée
 * pour charger réellement les données de la police.
 */
class FileTypeface(val file: File) : Typeface() {
    override fun toString(): String {
        return "FileTypeface(${file.name})"
    }
}

/**
 * ID de glyphe - correspond à SkGlyphID dans Skia.
 * Utilisé pour identifier de manière unique un glyphe dans une police.
 */
@JvmInline
value class GlyphID(val value: Int) {
    override fun toString(): String {
        return "GlyphID($value)"
    }
}

/**
 * Métriques de police - correspond à SkFontMetrics dans Skia.
 * Contient les informations de mesure pour une police.
 */
data class FontMetrics(
    val top: Float = 0f,
    val ascent: Float = 0f,
    val descent: Float = 0f,
    val bottom: Float = 0f,
    val leading: Float = 0f,
    val avgCharWidth: Float = 0f,
    val maxCharWidth: Float = 0f,
    val xMin: Float = 0f,
    val xMax: Float = 0f,
    val xHeight: Float = 0f,
    val capHeight: Float = 0f,
    val underlineThickness: Float = 0f,
    val underlinePosition: Float = 0f
) {
    companion object {
        val EMPTY: FontMetrics = FontMetrics()
    }
}