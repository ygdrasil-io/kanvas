package core

/**
 * Classe utilitaire pour convertir du texte en glyphes et créer des GlyphRun.
 * Correspond partiellement à la fonctionnalité de SkFont et SkTextBlob dans Skia.
 * 
 * Cette classe fournit des méthodes pour convertir du texte en séquences de glyphes
 * avec leurs positions appropriées.
 */
class TextToGlyphs {
    
    companion object {
        /**
         * Convertit du texte en une séquence de glyphes avec des positions de base.
         * 
         * @param text Le texte à convertir
         * @param font La police à utiliser
         * @param startX La position X de départ
         * @param startY La position Y de départ (ligne de base)
         * @return Un GlyphRun contenant les glyphes et leurs positions
         */
        fun textToGlyphRun(text: String, font: Font, startX: Float = 0f, startY: Float = 0f): GlyphRun {
            // Convertir le texte en IDs de glyphes
            val glyphIds = font.textToGlyphs(text)
            
            // Créer des glyphes avec des avances plus précises basées sur les métriques de la police
            val glyphs = glyphIds.mapIndexed { index, glyphId ->
                // Utiliser les métriques de la police pour calculer une avance plus précise
                // Pour une implémentation complète, nous utiliserions les avances spécifiques à chaque glyphe
                val metrics = font.getMetrics()
                
                // Calculer l'avance en fonction du type de caractère
                val char = text.getOrNull(index) ?: ' '
                val advanceX = when (char) {
                    ' ', '\t', '\n', '\r' -> font.size * 0.3f // Espaces et caractères de contrôle
                    'i', 'j', 'l', 'I', '!', '.', ',', ';', ':' -> font.size * 0.25f // Caractères étroits
                    'm', 'w', 'M', 'W', '@', '#', '$', '%' -> font.size * 0.8f // Caractères larges
                    else -> metrics.avgCharWidth // Largeur moyenne par défaut
                }
                
                // Calculer la hauteur du glyphe en fonction des métriques
                val glyphHeight = metrics.ascent + metrics.descent
                
                Glyph.createSimple(glyphId, advanceX, font.getMetrics())
            }
            
            // Calculer les positions avec les métriques de la police
            val positions = calculateGlyphPositions(glyphs, startX, startY, font)
            
            return GlyphRun(font, positions, glyphs)
        }
        
        /**
         * Calcule les positions des glyphes pour un rendu horizontal simple.
         * Prend en compte les métriques verticales de la police pour un positionnement précis.
         * 
         * @param glyphs La liste des glyphes
         * @param startX La position X de départ
         * @param startY La position Y de départ (ligne de base)
         * @param font La police utilisée pour obtenir les métriques verticales
         * @return La liste des positions calculées
         */
        private fun calculateGlyphPositions(glyphs: List<Glyph>, startX: Float, startY: Float, font: Font): List<Point> {
            val positions = mutableListOf<Point>()
            var currentX = startX
            
            // Utiliser les métriques de la police pour le positionnement vertical
            val metrics = font.getMetrics()
            // La ligne de base est à startY, mais nous devons ajuster pour l'ascender
            val baselineY = startY
            
            for (glyph in glyphs) {
                // Positionner chaque glyphe sur la ligne de base
                positions.add(Point(currentX, baselineY))
                currentX += glyph.advanceX
            }
            
            return positions
        }
        
        /**
         * Mesure la largeur du texte en utilisant les métriques de la police.
         * 
         * @param text Le texte à mesurer
         * @param font La police à utiliser
         * @return La largeur totale du texte
         */
        fun measureTextWidth(text: String, font: Font): Float {
            val glyphIds = font.textToGlyphs(text)
            
            // Pour une implémentation réelle, nous utiliserions les avances réelles des glyphes
            // Pour l'instant, nous utilisons une approximation
            return glyphIds.size * font.size * 0.6f
        }
        
        /**
         * Crée un GlyphRun simple pour le rendu de texte de base.
         * 
         * @param text Le texte
         * @param font La police
         * @param position La position de départ
         * @return Un GlyphRun prêt pour le rendu
         */
        fun createSimpleGlyphRun(text: String, font: Font, position: Point = Point.ZERO): GlyphRun {
            return textToGlyphRun(text, font, position.x, position.y)
        }
        
        /**
         * Convertit un GlyphRun en une séquence de paires (glyphe, position).
         * Utile pour le rendu.
         * 
         * @param glyphRun Le GlyphRun à convertir
         * @return Une séquence de paires (glyphe, position)
         */
        fun glyphRunToSequence(glyphRun: GlyphRun): Sequence<Pair<Glyph, Point>> {
            return glyphRun.glyphs.asSequence().zip(glyphRun.positions.asSequence())
        }
    }
}

/**
 * Extension pour ajouter des méthodes de conversion de texte directement sur Font.
 */
fun Font.createGlyphRun(text: String, x: Float = 0f, y: Float = 0f): GlyphRun {
    return TextToGlyphs.textToGlyphRun(text, this, x, y)
}

/**
 * Extension pour mesurer la largeur du texte.
 */
fun Font.measureText(text: String): Float {
    return TextToGlyphs.measureTextWidth(text, this)
}