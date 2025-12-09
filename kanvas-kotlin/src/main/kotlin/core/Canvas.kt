package com.kanvas.core

import java.nio.ByteBuffer

/**
 * Kanvas is the main drawing surface that provides an interface for drawing operations.
 * It maintains a stack of transformations and clip regions.
 */
class Canvas(private val width: Int, private val height: Int) {
    
    // Current transformation matrix stack
    private val matrixStack: MutableList<Matrix> = mutableListOf(Matrix.identity())
    
    // Current clip region stack
    private val clipStack: MutableList<Rect> = mutableListOf(Rect(0f, 0f, width.toFloat(), height.toFloat()))
    
    // Current paint properties
    private var currentPaint: Paint = Paint()
    
    /**
     * Saves the current canvas state (matrix and clip) onto a stack
     */
    fun save() {
        matrixStack.add(matrixStack.last().copy())
        clipStack.add(clipStack.last().copy())
    }
    
    /**
     * Restores the canvas state from the stack
     */
    fun restore() {
        if (matrixStack.size > 1) matrixStack.removeAt(matrixStack.size - 1)
        if (clipStack.size > 1) clipStack.removeAt(clipStack.size - 1)
    }
    
    /**
     * Translates the canvas by the specified amounts
     */
    fun translate(dx: Float, dy: Float) {
        val currentMatrix = matrixStack.last()
        currentMatrix.postTranslate(dx, dy)
    }
    
    /**
     * Scales the canvas by the specified amounts
     */
    fun scale(sx: Float, sy: Float) {
        val currentMatrix = matrixStack.last()
        currentMatrix.postScale(sx, sy)
    }
    
    /**
     * Rotates the canvas by the specified degrees
     */
    fun rotate(degrees: Float) {
        val currentMatrix = matrixStack.last()
        currentMatrix.postRotate(degrees)
    }
    
    /**
     * Sets the current paint to use for drawing operations
     */
    fun setPaint(paint: Paint) {
        this.currentPaint = paint
    }
    
    /**
     * Draws a rectangle with the current paint
     */
    fun drawRect(rect: Rect, paint: Paint = currentPaint) {
        // Apply current transformation and clip
        val transformedRect = matrixStack.last().mapRect(rect)
        val clippedRect = transformedRect.intersect(clipStack.last())
        
        if (clippedRect.isEmpty) return
        
        // TODO: Implement actual drawing logic
        println("Drawing rect: $clippedRect with paint: $paint")
    }
    
    /**
     * Draws a path with the current paint
     */
    fun drawPath(path: Path, paint: Paint = currentPaint) {
        // TODO: Implement path drawing
        println("Drawing path: $path with paint: $paint")
    }
    
    /**
     * Draws text at the specified position
     */
    fun drawText(text: String, x: Float, y: Float, paint: Paint = currentPaint) {
        // TODO: Implement text drawing
        println("Drawing text: '$text' at ($x, $y) with paint: $paint")
    }
    
    /**
     * Clears the canvas with the specified color
     */
    fun clear(color: Color) {
        // TODO: Implement clear operation
        println("Clearing canvas with color: $color")
    }
    
    /**
     * Gets the current transformation matrix
     */
    fun getMatrix(): Matrix = matrixStack.last().copy()
    
    /**
     * Gets the current clip bounds
     */
    fun getClipBounds(): Rect = clipStack.last().copy()
    
    companion object {
        /**
         * Creates a new raster canvas
         */
        fun createRaster(width: Int, height: Int): Canvas {
            return Canvas(width, height)
        }
    }
}

/**
 * Represents a 3x3 transformation matrix
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
    fun copy(): Matrix = Matrix(scaleX, skewX, transX, skewY, scaleY, transY, persp0, persp1, persp2)
    
    fun postTranslate(dx: Float, dy: Float) {
        transX += dx
        transY += dy
    }
    
    fun postScale(sx: Float, sy: Float) {
        scaleX *= sx
        scaleY *= sy
    }
    
    fun postRotate(degrees: Float) {
        val radians = Math.toRadians(degrees.toDouble()).toFloat()
        val cos = kotlin.math.cos(radians)
        val sin = kotlin.math.sin(radians)
        
        val newScaleX = scaleX * cos + skewY * sin
        val newSkewY = -scaleX * sin + skewY * cos
        val newSkewX = skewX * cos + scaleY * sin
        val newScaleY = -skewX * sin + scaleY * cos
        
        scaleX = newScaleX
        skewY = newSkewY
        skewX = newSkewX
        scaleY = newScaleY
    }
    
    fun mapRect(rect: Rect): Rect {
        // TODO: Implement proper rectangle transformation
        return rect.copy()
    }
    
    companion object {
        fun identity(): Matrix = Matrix()
    }
}

/**
 * Represents a rectangular region
 */
data class Rect(var left: Float, var top: Float, var right: Float, var bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val isEmpty: Boolean get() = left >= right || top >= bottom
    
    fun copy(): Rect = Rect(left, top, right, bottom)
    
    fun intersect(other: Rect): Rect {
        val newLeft = kotlin.math.max(left, other.left)
        val newTop = kotlin.math.max(top, other.top)
        val newRight = kotlin.math.min(right, other.right)
        val newBottom = kotlin.math.min(bottom, other.bottom)
        
        return Rect(newLeft, newTop, newRight, newBottom)
    }
    
    override fun toString(): String = "Rect($left, $top, $right, $bottom)"
}

/**
 * Represents a color in RGBA format
 */
data class Color(val red: Int, val green: Int, val blue: Int, val alpha: Int = 255) {
    companion object {
        val TRANSPARENT = Color(0, 0, 0, 0)
        val BLACK = Color(0, 0, 0)
        val WHITE = Color(255, 255, 255)
        val RED = Color(255, 0, 0)
        val GREEN = Color(0, 255, 0)
        val BLUE = Color(0, 0, 255)
    }
}