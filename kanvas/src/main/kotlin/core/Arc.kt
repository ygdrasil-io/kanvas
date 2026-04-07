package com.kanvas.core

/**
 * Arc class, similar to Skia's SkArc
 * Represents an elliptical arc defined by an oval and angles
 */
data class Arc(
    val oval: Rect,      // The bounding rectangle of the oval
    val startAngle: Float, // Starting angle in degrees
    val sweepAngle: Float, // Sweep angle in degrees
    val useCenter: Boolean = false // Whether to connect to center
) {
    /**
     * Check if this arc is valid (non-zero sweep angle)
     */
    val isValid: Boolean
        get() = sweepAngle != 0f && oval.width > 0 && oval.height > 0
    
    /**
     * Get the bounds of this arc (same as the oval bounds)
     */
    fun getBounds(): Rect = oval.copy()
    
    /**
     * Check if this arc contains a point (simplified - checks if point is in oval)
     */
    fun contains(x: Float, y: Float): Boolean {
        return oval.contains(x, y)
    }
    
    /**
     * Create an Arc from center, radius, and angles
     */
    companion object {
        fun make(cx: Float, cy: Float, radiusX: Float, radiusY: Float, 
                 startAngle: Float, sweepAngle: Float, useCenter: Boolean = false): Arc {
            val left = cx - radiusX
            val top = cy - radiusY
            val right = cx + radiusX
            val bottom = cy + radiusY
            return Arc(Rect(left, top, right, bottom), startAngle, sweepAngle, useCenter)
        }
        
        fun makeEmpty(): Arc = Arc(Rect(0f, 0f, 0f, 0f), 0f, 0f)
    }
    
    /**
     * Convert to string representation
     */
    override fun toString(): String {
        return "Arc(oval=$oval, startAngle=$startAngle, sweepAngle=$sweepAngle, useCenter=$useCenter)"
    }
}