package core

import com.kanvas.core.Size

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