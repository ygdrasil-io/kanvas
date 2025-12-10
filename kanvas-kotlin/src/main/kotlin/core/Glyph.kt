package core

/**
 * Classe représentant un point 2D avec des coordonnées flottantes.
 * Utilisée pour les positions des glyphes.
 */
data class Point(val x: Float, val y: Float) {
    companion object {
        val ZERO: Point = Point(0f, 0f)
    }
    
    fun translate(dx: Float, dy: Float): Point {
        return Point(x + dx, y + dy)
    }
    
    override fun toString(): String {
        return "Point($x, $y)"
    }
}

/**
 * Classe représentant un rectangle simple pour les limites des glyphes.
 * Version simplifiée pour éviter les dépendances.
 */
data class SimpleRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    companion object {
        val EMPTY: SimpleRect = SimpleRect(0f, 0f, 0f, 0f)
    }
    
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isEmpty: Boolean get() = left >= right || top >= bottom
    
    fun copy(): SimpleRect = SimpleRect(left, top, right, bottom)
    
    fun join(other: SimpleRect): SimpleRect {
        if (isEmpty) return other.copy()
        if (other.isEmpty) return copy()
        
        val newLeft = kotlin.math.min(left, other.left)
        val newTop = kotlin.math.min(top, other.top)
        val newRight = kotlin.math.max(right, other.right)
        val newBottom = kotlin.math.max(bottom, other.bottom)
        
        return SimpleRect(newLeft, newTop, newRight, newBottom)
    }
    
    override fun toString(): String {
        return "SimpleRect($left, $top, $right, $bottom)"
    }
}

/**
 * Classe représentant un glyphe dans Kanvas.
 * Correspond partiellement à SkGlyph dans Skia.
 * 
 * Un glyphe représente la forme visuelle d'un caractère et contient
 * des informations sur sa forme, sa position et ses métriques.
 * 
 * @property id L'ID unique du glyphe
 * @property width La largeur du glyphe
 * @property height La hauteur du glyphe
 * @property advanceX L'avance horizontale (espace occupé par le glyphe)
 * @property advanceY L'avance verticale
 * @property bounds Les limites du glyphe
 */
data class Glyph(
    val id: GlyphID,
    val width: Int = 0,
    val height: Int = 0,
    val advanceX: Float = 0f,
    val advanceY: Float = 0f,
    val bounds: SimpleRect = SimpleRect.EMPTY,
    // val path: Path? = null // Chemin vectoriel du glyphe (optionnel) - à implémenter plus tard
) {
    companion object {
        /**
         * Crée un glyphe vide
         */
        val EMPTY: Glyph = Glyph(GlyphID(0))
        
        /**
         * Crée un glyphe à partir d'un ID et d'une avance
         * Avec des bounds améliorés basés sur les métriques de police
         */
        fun createSimple(glyphId: GlyphID, advanceX: Float, fontMetrics: FontMetrics? = null): Glyph {
            val ascent = fontMetrics?.ascent ?: 0f
            val descent = fontMetrics?.descent ?: 1f
            return Glyph(
                id = glyphId,
                advanceX = advanceX,
                bounds = SimpleRect(0f, -ascent, advanceX, descent)
            )
        }
    }
    
    /**
     * Retourne le vecteur d'avance du glyphe
     */
    fun advanceVector(): Point {
        return Point(advanceX, advanceY)
    }
    
    /**
     * Vérifie si ce glyphe est vide
     */
    fun isEmpty(): Boolean {
        return width == 0 && height == 0 && advanceX == 0f && advanceY == 0f
    }
    
    override fun toString(): String {
        return "Glyph(id=$id, advanceX=$advanceX, bounds=$bounds)"
    }
}

/**
 * RSXForm représente une transformation de mise à l'échelle et de rotation pour un glyphe.
 * Correspond à SkRSXform dans Skia.
 * 
 * RSXForm = Rotated Scale Transform (transformation d'échelle et de rotation)
 * C'est une combinaison de mise à l'échelle et de rotation sans cisaillement.
 * 
 * @property scaleX Facteur d'échelle horizontal
 * @property scaleY Facteur d'échelle vertical
 * @property rotation Rotation en radians
 * @property tx Translation horizontale
 * @property ty Translation verticale
 */
data class RSXForm(
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val rotation: Float = 0f,  // en radians
    val tx: Float = 0f,
    val ty: Float = 0f
) {
    companion object {
        val IDENTITY: RSXForm = RSXForm(1f, 1f, 0f, 0f)
        
        /**
         * Crée un RSXForm à partir d'une mise à l'échelle uniforme et d'une rotation
         */
        fun fromScaleRotation(scale: Float, rotation: Float): RSXForm {
            return RSXForm(scale, scale, rotation, 0f, 0f)
        }
    }
    
    /**
     * Vérifie si ce RSXForm est une identité (pas de transformation)
     */
    fun isIdentity(): Boolean {
        return scaleX == 1f && scaleY == 1f && rotation == 0f && tx == 0f && ty == 0f
    }
}

/**
 * Classe représentant une séquence de glyphes avec leurs positions.
 * Correspond partiellement à SkGlyphRun dans Skia.
 * 
 * @property font La police utilisée pour ces glyphes
 * @property positions Les positions des glyphes
 * @property glyphs Les glyphes eux-mêmes
 * @property rsxforms Les transformations RSXForm pour chaque glyphe (optionnel)
 */
data class GlyphRun(
    val font: Font,
    val positions: List<Point>,
    val glyphs: List<Glyph>,
    val rsxforms: List<RSXForm> = emptyList()
) {
    init {
        require(positions.size == glyphs.size) {
            "Positions and glyphs must have the same size"
        }
        require(rsxforms.isEmpty() || rsxforms.size == glyphs.size) {
            "RSXForms must either be empty or have the same size as glyphs"
        }
    }
    
    /**
     * Retourne le nombre de glyphes dans cette séquence
     */
    fun size(): Int = glyphs.size
    
    /**
     * Retourne la paire (glyphe, position) à l'index donné
     */
    fun getGlyphPosition(index: Int): Pair<Glyph, Point> {
        return Pair(glyphs[index], positions[index])
    }
    
    /**
     * Retourne la paire (glyphe, position, rsxform) à l'index donné
     */
    fun getGlyphPositionRSXForm(index: Int): Triple<Glyph, Point, RSXForm> {
        return Triple(glyphs[index], positions[index], 
                     if (rsxforms.isNotEmpty()) rsxforms[index] else RSXForm.IDENTITY)
    }
    
    /**
     * Vérifie si ce GlyphRun contient des RSXForms (transformations non-identité)
     * Correspond à la méthode hasRSXForm() dans Skia
     */
    fun hasRSXForm(): Boolean {
        if (rsxforms.isEmpty()) return false
        
        for (rsxform in rsxforms) {
            if (!rsxform.isIdentity()) {
                return true
            }
        }
        return false
    }
    
    /**
     * Calcule les limites globales de cette séquence de glyphes
     */
    fun getBounds(): SimpleRect {
        if (glyphs.isEmpty()) return SimpleRect.EMPTY
        
        var bounds = glyphs[0].bounds.copy()
        bounds = translateRect(bounds, positions[0])
        
        for (i in 1 until glyphs.size) {
            val glyphBounds = glyphs[i].bounds.copy()
            val translatedBounds = translateRect(glyphBounds, positions[i])
            bounds = bounds.join(translatedBounds)
        }
        
        return bounds
    }
    
    /**
     * Fonction utilitaire pour translater un SimpleRect par un Point
     */
    private fun translateRect(rect: SimpleRect, point: Point): SimpleRect {
        return SimpleRect(
            rect.left + point.x,
            rect.top + point.y,
            rect.right + point.x,
            rect.bottom + point.y
        )
    }
}