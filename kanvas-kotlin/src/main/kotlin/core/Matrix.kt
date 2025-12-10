package com.kanvas.core

/**
 * 3x3 transformation matrix for 2D graphics
 * Inspired by Skia's SkMatrix
 */
data class Matrix(
    var scaleX: Float = 1f,
    var skewX: Float = 0f,
    var transX: Float = 0f,
    var skewY: Float = 0f,
    var scaleY: Float = 1f,
    var transY: Float = 0f,
    var persp0: Float = 0f,
    var persp1: Float = 0f,
    var persp2: Float = 1f
) {
    
    companion object {
        fun identity(): Matrix {
            return Matrix(
                scaleX = 1f, skewX = 0f, transX = 0f,
                skewY = 0f, scaleY = 1f, transY = 0f,
                persp0 = 0f, persp1 = 0f, persp2 = 1f
            )
        }
    }
    
    /**
     * Create a translation matrix
     */
    fun translate(dx: Float, dy: Float): Matrix {
        return Matrix(
            scaleX = scaleX, skewX = skewX, transX = transX + dx,
            skewY = skewY, scaleY = scaleY, transY = transY + dy,
            persp0 = persp0, persp1 = persp1, persp2 = persp2
        )
    }
    
    /**
     * Create a scale matrix
     */
    fun scale(sx: Float, sy: Float): Matrix {
        return Matrix(
            scaleX = scaleX * sx, skewX = skewX * sx, transX = transX,
            skewY = skewY * sy, scaleY = scaleY * sy, transY = transY,
            persp0 = persp0, persp1 = persp1, persp2 = persp2
        )
    }
    
    /**
     * Create a rotation matrix
     */
    fun rotate(degrees: Float, px: Float, py: Float): Matrix {
        val radians = Math.toRadians(degrees.toDouble()).toFloat()
        val cos = kotlin.math.cos(radians)
        val sin = kotlin.math.sin(radians)
        
        // Translate to origin, rotate, translate back
        val result = Matrix(
            scaleX = cos, skewX = -sin, transX = 0f,
            skewY = sin, scaleY = cos, transY = 0f,
            persp0 = 0f, persp1 = 0f, persp2 = 1f
        )
        
        // Apply translation for pivot point
        val translateMatrix = Matrix.identity().translate(px, py)
        val inverseTranslateMatrix = Matrix.identity().translate(-px, -py)
        
        return inverseTranslateMatrix.concat(translateMatrix.concat(this.concat(result)))
    }
    
    /**
     * Concatenate with another matrix (this * other)
     */
    fun concat(other: Matrix): Matrix {
        return Matrix(
            scaleX = scaleX * other.scaleX + skewX * other.skewY,
            skewX = scaleX * other.skewX + skewX * other.scaleY,
            transX = scaleX * other.transX + skewX * other.transY + transX,
            
            skewY = skewY * other.scaleX + scaleY * other.skewY,
            scaleY = skewY * other.skewX + scaleY * other.scaleY,
            transY = skewY * other.transX + scaleY * other.transY + transY,
            
            persp0 = persp0 * other.scaleX + persp1 * other.skewY + persp2 * other.transX,
            persp1 = persp0 * other.skewX + persp1 * other.scaleY + persp2 * other.transY,
            persp2 = persp0 * other.transX + persp1 * other.transY + persp2 * other.persp2
        )
    }
    
    /**
     * Transform a point
     */
    fun mapPoint(x: Float, y: Float): Pair<Float, Float> {
        return Pair(
            scaleX * x + skewX * y + transX,
            skewY * x + scaleY * y + transY
        )
    }
    
    /**
     * Transform a rectangle
     */
    fun mapRect(rect: Rect): Rect {
        val (x1, y1) = mapPoint(rect.left, rect.top)
        val (x2, y2) = mapPoint(rect.right, rect.top)
        val (x3, y3) = mapPoint(rect.right, rect.bottom)
        val (x4, y4) = mapPoint(rect.left, rect.bottom)
        
        val minX = kotlin.math.min(kotlin.math.min(x1, x2), kotlin.math.min(x3, x4))
        val maxX = kotlin.math.max(kotlin.math.max(x1, x2), kotlin.math.max(x3, x4))
        val minY = kotlin.math.min(kotlin.math.min(y1, y2), kotlin.math.min(y3, y4))
        val maxY = kotlin.math.max(kotlin.math.max(y1, y2), kotlin.math.max(y3, y4))
        
        return Rect(minX, minY, maxX, maxY)
    }
    
    /**
     * Check if this is an identity matrix
     */
    fun isIdentity(): Boolean {
        return scaleX == 1f && skewX == 0f && transX == 0f &&
               skewY == 0f && scaleY == 1f && transY == 0f &&
               persp0 == 0f && persp1 == 0f && persp2 == 1f
    }
    
    /**
     * Copy this matrix
     */
    fun copy(): Matrix {
        return Matrix(scaleX, skewX, transX, skewY, scaleY, transY, persp0, persp1, persp2)
    }
    
    override fun toString(): String {
        return "Matrix([${scaleX}, ${skewX}, ${transX}], [${skewY}, ${scaleY}, ${transY}], [${persp0}, ${persp1}, ${persp2}])"
    }
}