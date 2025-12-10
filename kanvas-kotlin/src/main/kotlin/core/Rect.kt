package com.kanvas.core

/**
 * Represents a rectangular region, similar to Skia's SkRect
 */
data class Rect(var left: Float, var top: Float, var right: Float, var bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = left + width / 2
    val centerY: Float get() = top + height / 2
    val isEmpty: Boolean get() = left >= right || top >= bottom

    fun copy(): Rect = Rect(left, top, right, bottom)

    /**
     * Insets the rectangle by the specified amounts
     * @param dx horizontal inset (positive values make the rect smaller)
     * @param dy vertical inset (positive values make the rect smaller)
     */
    fun inset(dx: Float, dy: Float) {
        left += dx
        top += dy
        right -= dx
        bottom -= dy
    }

    /**
     * Returns a new rectangle that is the intersection of this rectangle and another
     */
    fun intersect(other: Rect): Rect {
        val newLeft = kotlin.math.max(left, other.left)
        val newTop = kotlin.math.max(top, other.top)
        val newRight = kotlin.math.min(right, other.right)
        val newBottom = kotlin.math.min(bottom, other.bottom)

        return Rect(newLeft, newTop, newRight, newBottom)
    }

    /**
     * Returns a sorted version of this rectangle where left <= right and top <= bottom
     */
    fun makeSorted(): Rect {
        val sortedLeft = kotlin.math.min(left, right)
        val sortedRight = kotlin.math.max(left, right)
        val sortedTop = kotlin.math.min(top, bottom)
        val sortedBottom = kotlin.math.max(top, bottom)

        return Rect(sortedLeft, sortedTop, sortedRight, sortedBottom)
    }

    /**
     * Returns true if this rectangle intersects with another rectangle
     */
    fun intersects(other: Rect): Boolean {
        return left < other.right && right > other.left &&
                top < other.bottom && bottom > other.top
    }

    /**
     * Returns true if this rectangle contains another rectangle
     */
    fun contains(other: Rect): Boolean {
        return left <= other.left && right >= other.right &&
                top <= other.top && bottom >= other.bottom
    }

    /**
     * Returns true if this rectangle contains the specified point
     */
    fun contains(x: Float, y: Float): Boolean {
        return x >= left && x < right && y >= top && y < bottom
    }

    /**
     * Offsets the rectangle by the specified amounts
     */
    fun offset(dx: Float, dy: Float) {
        left += dx
        top += dy
        right += dx
        bottom += dy
    }

    /**
     * Sets this rectangle to be empty (left >= right or top >= bottom)
     */
    fun setEmpty() {
        left = 0f
        top = 0f
        right = 0f
        bottom = 0f
    }
    
    /**
     * Intersects this rectangle with another rectangle, modifying this rectangle in place.
     * Returns true if the intersection is non-empty, false otherwise.
     * Similar to SkRect::intersect(const SkRect& r)
     */
    fun intersectInPlace(other: Rect): Boolean {
        val newLeft = kotlin.math.max(left, other.left)
        val newTop = kotlin.math.max(top, other.top)
        val newRight = kotlin.math.min(right, other.right)
        val newBottom = kotlin.math.min(bottom, other.bottom)
        
        if (newLeft >= newRight || newTop >= newBottom) {
            return false
        }
        
        left = newLeft
        top = newTop
        right = newRight
        bottom = newBottom
        return true
    }
    
    /**
     * Expands this rectangle to include another rectangle.
     * Similar to SkRect::join(const SkRect& r)
     */
    fun join(other: Rect) {
        if (other.isEmpty) return
        if (isEmpty) {
            left = other.left
            top = other.top
            right = other.right
            bottom = other.bottom
        } else {
            left = kotlin.math.min(left, other.left)
            top = kotlin.math.min(top, other.top)
            right = kotlin.math.max(right, other.right)
            bottom = kotlin.math.max(bottom, other.bottom)
        }
    }
    
    /**
     * Expands this rectangle to include a point.
     * Similar to SkRectPriv::GrowToInclude(SkRect* r, const SkPoint& pt)
     */
    fun growToInclude(x: Float, y: Float) {
        if (isEmpty) {
            // If rectangle is empty, set it to a small rectangle around the point
            // to ensure it's not empty
            left = x
            top = y
            right = x + 1f
            bottom = y + 1f
        } else {
            left = kotlin.math.min(x, left)
            right = kotlin.math.max(x, right)
            top = kotlin.math.min(y, top)
            bottom = kotlin.math.max(y, bottom)
        }
    }
    
    /**
     * Sets this rectangle to the bounds of a collection of points.
     * Similar to SkRect::setBoundsCheck()
     */
    fun setBounds(points: List<Point>) {
        if (points.isEmpty()) {
            setEmpty()
            return
        }
        
        var minX = points[0].x
        var minY = points[0].y
        var maxX = points[0].x
        var maxY = points[0].y
        
        for (point in points) {
            minX = kotlin.math.min(minX, point.x)
            minY = kotlin.math.min(minY, point.y)
            maxX = kotlin.math.max(maxX, point.x)
            maxY = kotlin.math.max(maxY, point.y)
        }
        
        left = minX
        top = minY
        right = maxX
        bottom = maxY
    }
    
    /**
     * Subtracts another rectangle from this rectangle.
     * Returns the largest rectangle contained in the difference, or null if no valid rectangle remains.
     * Similar to SkRectPriv::Subtract(const SkRect& a, const SkRect& b, SkRect* out)
     */
    fun subtract(other: Rect): Rect? {
        if (!intersects(other)) {
            return copy() // No intersection, return this rectangle
        }
        
        // Try different subtraction strategies
        val candidates = mutableListOf<Rect>()
        
        // Left strip
        if (left < other.left) {
            candidates.add(Rect(left, top, other.left, bottom))
        }
        
        // Right strip
        if (right > other.right) {
            candidates.add(Rect(other.right, top, right, bottom))
        }
        
        // Top strip
        if (top < other.top) {
            candidates.add(Rect(kotlin.math.max(left, other.left), top, kotlin.math.min(right, other.right), other.top))
        }
        
        // Bottom strip
        if (bottom > other.bottom) {
            candidates.add(Rect(kotlin.math.max(left, other.left), other.bottom, kotlin.math.min(right, other.right), bottom))
        }
        
        // Return the largest candidate by area
        return candidates.maxByOrNull { it.width * it.height }
    }

    override fun toString(): String = "Rect($left, $top, $right, $bottom)"

    companion object {
        /**
         * Creates an empty rectangle
         */
        fun makeEmpty(): Rect = Rect(0f, 0f, 0f, 0f)

        /**
         * Creates a rectangle from left, top, right, bottom coordinates
         */
        fun makeLTRB(left: Float, top: Float, right: Float, bottom: Float): Rect = Rect(left, top, right, bottom)

        /**
         * Creates a rectangle from x, y, width, height
         */
        fun makeXYWH(x: Float, y: Float, width: Float, height: Float): Rect = Rect(x, y, x + width, y + height)

        /**
         * Creates a rectangle from width and height, positioned at (0, 0)
         */
        fun makeWH(width: Float, height: Float): Rect = Rect(0f, 0f, width, height)

        /**
         * Creates a rectangle from a size
         */
        fun makeSize(size: Size): Rect = Rect(0f, 0f, size.width, size.height)
    }
}