package core

import com.kanvas.core.Color
import com.kanvas.core.Paint
import device.BitmapDevice

/**
 * GlyphPainter est responsable du rendu effectif des glyphes sur un device.
 * Inspiré de SkGlyphRunPainter dans Skia.
 *
 * Cette classe gère différentes stratégies de rendu de texte :
 * - Rendu par masques (pour le texte normal)
 * - Rendu par paths (pour le texte transformé)
 * - Rendu par bitmaps (pour les glyphes pré-rendus)
 */
class GlyphPainter {
    
    /**
     * Dessine une séquence de glyphes sur un device.
     * 
     * @param device Le device sur lequel dessiner
     * @param glyphRun La séquence de glyphes à dessiner
     * @param paint Les propriétés de peinture
     */
    fun drawGlyphRun(device: BitmapDevice, glyphRun: GlyphRun, paint: Paint) {
        // Vérifier si nous avons besoin d'un rendu par path (transformations complexes)
        if (paint.hasComplexTransform() || glyphRun.font.hasComplexRendering()) {
            drawGlyphRunAsPaths(device, glyphRun, paint)
        } else {
            // Rendu normal par masques
            drawGlyphRunAsMasks(device, glyphRun, paint)
        }
    }
    
    /**
     * Dessine une séquence de glyphes en utilisant des masques.
     * C'est la méthode de rendu standard pour le texte non transformé.
     */
    private fun drawGlyphRunAsMasks(device: BitmapDevice, glyphRun: GlyphRun, paint: Paint) {
        val font = glyphRun.font
        val color = paint.color
        
        for (i in 0 until glyphRun.size()) {
            val (glyph, position) = glyphRun.getGlyphPosition(i)
            
            // Créer un masque pour ce glyphe
            val mask = createGlyphMask(glyph, font)
            
            // Appliquer le masque sur le device
            applyGlyphMask(device, mask, position.x.toInt(), position.y.toInt(), color)
        }
    }
    
    /**
     * Dessine une séquence de glyphes en utilisant des paths.
     * Utilisé pour le texte avec des transformations complexes.
     */
    private fun drawGlyphRunAsPaths(device: BitmapDevice, glyphRun: GlyphRun, paint: Paint) {
        // Pour l'instant, nous allons utiliser une approche simplifiée
        // Dans une implémentation complète, nous utiliserions les paths vectoriels des glyphes
        
        // Pour le moment, nous allons dessiner chaque glyphe comme un rectangle
        // Cela servira de placeholder jusqu'à l'implémentation complète
        for (i in 0 until glyphRun.size()) {
            val (glyph, position) = glyphRun.getGlyphPosition(i)
            
            // Créer un rectangle représentant le glyphe
            val glyphRect = SimpleRect(
                position.x,
                position.y - glyphRun.font.size,
                position.x + glyph.advanceX,
                position.y
            )
            
            // Dessiner le rectangle sur le device
            // Note: Cela est temporaire - à remplacer par le rendu de path réel
            device.drawRect(
                com.kanvas.core.Rect(
                    glyphRect.left,
                    glyphRect.top,
                    glyphRect.right,
                    glyphRect.bottom
                ),
                paint
            )
        }
    }
    
    /**
     * Crée un masque pour un glyphe donné.
     * 
     * @param glyph Le glyphe pour lequel créer le masque
     * @param font La police utilisée
     * @return Un tableau 2D représentant le masque du glyphe (true = pixel actif)
     */
    private fun createGlyphMask(glyph: Glyph, font: Font): Array<BooleanArray> {
        // Pour l'instant, nous créons un masque simple rectangulaire
        // Dans une implémentation complète, cela utiliserait les données réelles du glyphe
        
        val width = kotlin.math.max(1, glyph.width)
        val height = kotlin.math.max(1, font.size.toInt())
        
        val mask = Array(height) { BooleanArray(width) { false } }
        
        // Remplir le masque avec un rectangle simple
        // Cela représente la forme approximative du glyphe
        val fillWidth = kotlin.math.max(1, (width * 0.8).toInt()) // 80% de la largeur
        val fillHeight = kotlin.math.max(1, (height * 0.8).toInt()) // 80% de la hauteur
        
        val startX = (width - fillWidth) / 2
        val startY = (height - fillHeight) / 2
        
        for (y in startY until startY + fillHeight) {
            for (x in startX until startX + fillWidth) {
                if (y >= 0 && y < height && x >= 0 && x < width) {
                    mask[y][x] = true
                }
            }
        }
        
        return mask
    }
    
    /**
     * Applique un masque de glyphe sur un device à une position donnée.
     * 
     * @param device Le device sur lequel appliquer le masque
     * @param mask Le masque du glyphe (tableau 2D de booléens)
     * @param x La position X où appliquer le masque
     * @param y La position Y où appliquer le masque
     * @param color La couleur à utiliser pour le glyphe
     */
    private fun applyGlyphMask(device: BitmapDevice, mask: Array<BooleanArray>, x: Int, y: Int, color: Color) {
        val maskHeight = mask.size
        if (maskHeight == 0) return
        val maskWidth = mask[0].size
        
        // Parcourir le masque et appliquer les pixels actifs
        for (maskY in 0 until maskHeight) {
            val row = mask[maskY]
            for (maskX in 0 until maskWidth) {
                if (row[maskX]) {
                    val deviceX = x + maskX
                    val deviceY = y + maskY
                    
                    // Vérifier que nous sommes dans les limites du device
                    if (deviceX >= 0 && deviceX < device.width && deviceY >= 0 && deviceY < device.height) {
                        // Appliquer la couleur du glyphe
                        device.bitmap.setPixel(deviceX, deviceY, color)
                    }
                }
            }
        }
    }
    
    /**
     * Vérifie si une peinture a des transformations complexes nécessitant un rendu par path.
     */
    private fun Paint.hasComplexTransform(): Boolean {
        // Pour l'instant, nous considérons que toute transformation non-identité est complexe
        // Dans une implémentation complète, nous vérifierions les rotations, skews, etc.
        return false // Simplifié pour le moment
    }
    
    /**
     * Vérifie si une police nécessite un rendu complexe.
     */
    private fun Font.hasComplexRendering(): Boolean {
        // Pour l'instant, nous ne considérons pas de cas complexes
        return false
    }
    
    companion object {
        /**
         * Crée un GlyphPainter par défaut.
         */
        fun create(): GlyphPainter {
            return GlyphPainter()
        }
    }
}