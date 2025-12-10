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
            
            // Créer des glyphes simples avec des avances approximatives
            // Pour une implémentation réelle, nous aurions besoin des métriques réelles
            // des glyphes à partir de la police
            val glyphs = glyphIds.map { glyphId ->
                // Utiliser une avance approximative pour l'instant
                // Dans une implémentation complète, cela viendrait des métriques de la police
                val advanceX = font.size * 0.6f // Approximation: 60% de la taille de la police
                Glyph.createSimple(glyphId, advanceX)
            }
            
            // Calculer les positions
            val positions = calculateGlyphPositions(glyphs, startX, startY)
            
            return GlyphRun(font, positions, glyphs)
        }
        
        /**
         * Calcule les positions des glyphes pour un rendu horizontal simple.
         * 
         * @param glyphs La liste des glyphes
         * @param startX La position X de départ
         * @param startY La position Y de départ (ligne de base)
         * @return La liste des positions calculées
         */
        private fun calculateGlyphPositions(glyphs: List<Glyph>, startX: Float, startY: Float): List<Point> {
            val positions = mutableListOf<Point>()
            var currentX = startX
            val currentY = startY
            
            for (glyph in glyphs) {
                positions.add(Point(currentX, currentY))
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