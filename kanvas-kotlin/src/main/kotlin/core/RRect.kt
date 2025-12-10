package com.kanvas.core

/**
 * Rounded Rectangle class, similar to Skia's SkRRect
 * Represents a rectangle with rounded corners
 */
data class RRect(
    val rect: Rect,
    val rx: Float = 0f,  // x-radius for all corners
    val ry: Float = 0f   // y-radius for all corners
) {
    constructor(left: Float, top: Float, right: Float, bottom: Float, rx: Float, ry: Float) : 
            this(Rect(left, top, right, bottom), rx, ry)
    
    constructor(rect: Rect, radius: Float) : this(rect, radius, radius)
    
    /**
     * Check if this RRect is empty
     */
    val isEmpty: Boolean
        get() = rect.isEmpty
    
    /**
     * Get the width of this RRect
     */
    val width: Float
        get() = rect.width
    
    /**
     * Get the height of this RRect
     */
    val height: Float
        get() = rect.height
    
    /**
     * Get the bounds of this RRect (same as the underlying rect)
     */
    fun getBounds(): Rect = rect.copy()
    
    /**
     * Check if this RRect contains a point
     */
    fun contains(x: Float, y: Float): Boolean {
        return rect.contains(x, y)
    }
    
    /**
     * Check if this RRect intersects with another RRect
     */
    fun intersects(other: RRect): Boolean {
        return rect.intersects(other.rect)
    }
    
    /**
     * Create an RRect from a rect with uniform corner radii
     */
    companion object {
        fun makeRect(rect: Rect): RRect = RRect(rect, 0f, 0f)
        fun makeOval(rect: Rect): RRect = RRect(rect, rect.width / 2, rect.height / 2)
        fun makeEmpty(): RRect = RRect(Rect(0f, 0f, 0f, 0f), 0f, 0f)
    }
    
    /**
     * Convert to string representation
     */
    override fun toString(): String {
        return "RRect(rect=$rect, rx=$rx, ry=$ry)"
    }
}