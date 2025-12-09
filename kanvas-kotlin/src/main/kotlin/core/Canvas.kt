package com.kanvas.core

import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * Kanvas is the main drawing surface that provides an interface for drawing operations.
 * It maintains a stack of transformations and clip regions.
 */
class Canvas(private val width: Int, private val height: Int) {
    
    // Destination bitmap for raster rendering
    private val bitmap: Bitmap = Bitmap.create(width, height, BitmapConfig.ARGB_8888)
    
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
        
        // Implement actual raster drawing
        drawRectRaster(clippedRect, paint)
    }
    
    /**
     * Internal method for raster rectangle drawing
     */
    private fun drawRectRaster(rect: Rect, paint: Paint) {
        val left = rect.left.toInt().coerceAtLeast(0)
        val top = rect.top.toInt().coerceAtLeast(0)
        val right = rect.right.toInt().coerceAtMost(bitmap.getWidth())
        val bottom = rect.bottom.toInt().coerceAtMost(bitmap.getHeight())
        
        when (paint.style) {
            PaintStyle.FILL -> {
                // Fill the rectangle
                for (y in top until bottom) {
                    for (x in left until right) {
                        // Apply alpha blending
                        val existingColor = bitmap.getPixel(x, y)
                        val newColor = applyAlpha(paint.color, paint.alpha)
                        val blendedColor = blendColors(newColor, existingColor, paint.blendMode)
                        bitmap.setPixel(x, y, blendedColor)
                    }
                }
            }
            PaintStyle.STROKE -> {
                // Draw rectangle outline
                val halfStroke = paint.strokeWidth / 2
                
                // Top edge
                drawLine(left.toFloat(), top.toFloat(), right.toFloat(), top.toFloat(), paint)
                // Bottom edge
                drawLine(left.toFloat(), bottom.toFloat(), right.toFloat(), bottom.toFloat(), paint)
                // Left edge
                drawLine(left.toFloat(), top.toFloat(), left.toFloat(), bottom.toFloat(), paint)
                // Right edge
                drawLine(right.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint)
            }
            PaintStyle.FILL_AND_STROKE -> {
                // Fill first
                drawRectRaster(rect, paint.copy().apply { style = PaintStyle.FILL })
                // Then stroke
                drawRectRaster(rect, paint.copy().apply { style = PaintStyle.STROKE })
            }
        }
    }
    
    /**
     * Draws a line between two points
     */
    private fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        // Bresenham's line algorithm
        val x0 = x1.toInt()
        val y0 = y1.toInt()
        val x1i = x2.toInt()
        val y1i = y2.toInt()
        
        val dx = abs(x1i - x0)
        val dy = abs(y1i - y0)
        val sx = if (x0 < x1i) 1 else -1
        val sy = if (y0 < y1i) 1 else -1
        
        var err = dx - dy
        
        var x = x0
        var y = y0
        
        while (true) {
            // Draw pixel
            if (x in 0 until bitmap.getWidth() && y in 0 until bitmap.getHeight()) {
                val existingColor = bitmap.getPixel(x, y)
                val newColor = applyAlpha(paint.color, paint.alpha)
                val blendedColor = blendColors(newColor, existingColor, paint.blendMode)
                bitmap.setPixel(x, y, blendedColor)
            }
            
            if (x == x1i && y == y1i) break
            
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
    }
    
    /**
     * Applies alpha to a color
     */
    private fun applyAlpha(color: Color, alpha: Int): Color {
        val resultAlpha = (color.alpha * alpha) / 255
        return Color(color.red, color.green, color.blue, resultAlpha)
    }
    
    /**
     * Blends two colors using the specified blend mode
     */
    private fun blendColors(src: Color, dst: Color, mode: BlendMode): Color {
        return BitmapUtils.blendColors(src, dst, mode)
    }
    
    /**
     * Draws a path with the current paint
     */
    fun drawPath(path: Path, paint: Paint = currentPaint) {
        if (path.isEmpty()) return
        
        // Apply current transformation and clip
        val transformedPath = path.copy()
        transformedPath.transform(matrixStack.last())
        
        // Get the bounds and clip
        val bounds = transformedPath.getBounds()
        val clippedBounds = bounds.intersect(clipStack.last())
        
        if (clippedBounds.isEmpty) return
        
        when (paint.style) {
            PaintStyle.FILL -> {
                // Rasterize the path using scanline algorithm
                rasterizePathFill(transformedPath, paint)
            }
            PaintStyle.STROKE -> {
                // Rasterize the path outline
                rasterizePathStroke(transformedPath, paint)
            }
            PaintStyle.FILL_AND_STROKE -> {
                // Fill first
                drawPath(transformedPath, paint.copy().apply { style = PaintStyle.FILL })
                // Then stroke
                drawPath(transformedPath, paint.copy().apply { style = PaintStyle.STROKE })
            }
        }
    }
    
    /**
     * Rasterizes a path fill using scanline algorithm
     */
    private fun rasterizePathFill(path: Path, paint: Paint) {
        // Simple implementation: use bounding box for now
        // TODO: Implement proper scanline algorithm for complex paths
        val bounds = path.getBounds()
        drawRectRaster(bounds, paint)
    }
    
    /**
     * Rasterizes a path stroke
     */
    private fun rasterizePathStroke(path: Path, paint: Paint) {
        // Simple implementation: draw lines between points
        var currentPoint: Point? = null
        var i = 0
        
        while (i < path.verbs.size) {
            when (path.verbs[i]) {
                PathVerb.MOVE -> {
                    currentPoint = path.points[i]
                    i++
                }
                PathVerb.LINE -> {
                    if (currentPoint != null) {
                        val endPoint = path.points[i]
                        drawLine(currentPoint!!.x, currentPoint!!.y, endPoint.x, endPoint.y, paint)
                        currentPoint = endPoint
                    }
                    i++
                }
                PathVerb.QUAD -> {
                    if (currentPoint != null && i + 1 < path.points.size) {
                        val controlPoint = path.points[i]
                        val endPoint = path.points[i + 1]
                        // Approximate quadratic curve with lines
                        drawQuadraticCurve(currentPoint!!, controlPoint, endPoint, paint)
                        currentPoint = endPoint
                        i += 2
                    }
                }
                PathVerb.CUBIC -> {
                    if (currentPoint != null && i + 2 < path.points.size) {
                        val controlPoint1 = path.points[i]
                        val controlPoint2 = path.points[i + 1]
                        val endPoint = path.points[i + 2]
                        // Approximate cubic curve with lines
                        drawCubicCurve(currentPoint!!, controlPoint1, controlPoint2, endPoint, paint)
                        currentPoint = endPoint
                        i += 3
                    }
                }
                PathVerb.CLOSE -> {
                    // Close the path by connecting back to the first point
                    // For simplicity, we'll skip this in stroke rendering
                    i++
                }
            }
        }
    }
    
    /**
     * Draws a quadratic curve by approximating with line segments
     */
    private fun drawQuadraticCurve(p0: Point, p1: Point, p2: Point, paint: Paint) {
        // Improved approximation with 8 line segments for better precision
        // This matches Skia's approach for rendering conic curves
        val segments = 8
        var prev = p0
        
        for (i in 1..segments) {
            val t = i.toFloat() / segments
            // Quadratic Bezier curve formula: B(t) = (1-t)^2*P0 + 2(1-t)t*P1 + t^2*P2
            val x = (1 - t) * (1 - t) * p0.x + 2 * (1 - t) * t * p1.x + t * t * p2.x
            val y = (1 - t) * (1 - t) * p0.y + 2 * (1 - t) * t * p1.y + t * t * p2.y
            val current = Point(x, y)
            drawLine(prev.x, prev.y, current.x, current.y, paint)
            prev = current
        }
    }
    
    /**
     * Draws a cubic curve by approximating with line segments
     */
    private fun drawCubicCurve(p0: Point, p1: Point, p2: Point, p3: Point, paint: Paint) {
        // Simple approximation with 8 line segments
        val segments = 8
        var prev = p0
        
        for (i in 1..segments) {
            val t = i.toFloat() / segments
            val x = (1 - t) * (1 - t) * (1 - t) * p0.x + 
                    3 * (1 - t) * (1 - t) * t * p1.x + 
                    3 * (1 - t) * t * t * p2.x + 
                    t * t * t * p3.x
            val y = (1 - t) * (1 - t) * (1 - t) * p0.y + 
                    3 * (1 - t) * (1 - t) * t * p1.y + 
                    3 * (1 - t) * t * t * p2.y + 
                    t * t * t * p3.y
            val current = Point(x, y)
            drawLine(prev.x, prev.y, current.x, current.y, paint)
            prev = current
        }
    }
    
    /**
     * Draws text at the specified position
     */
    fun drawText(text: String, x: Float, y: Float, paint: Paint = currentPaint) {
        if (text.isEmpty()) return
        
        // Simple text rendering - draw each character as a small rectangle for now
        // TODO: Implement proper font rendering
        val charWidth = paint.textSize * 0.6f
        val charHeight = paint.textSize
        
        for ((i, char) in text.withIndex()) {
            val charX = x + i * charWidth
            val charY = y - charHeight // Text baseline is at the bottom
            
            // Draw character as a filled rectangle (simplified)
            val charRect = Rect(charX, charY, charX + charWidth, charY + charHeight)
            drawRect(charRect, paint)
        }
    }
    
    /**
     * Clears the canvas with the specified color
     */
    fun clear(color: Color) {
        bitmap.eraseColor(color)
    }
    
    /**
     * Gets the current transformation matrix
     */
    fun getMatrix(): Matrix = matrixStack.last().copy()
    
    /**
     * Gets the current clip bounds
     */
    fun getClipBounds(): Rect = clipStack.last().copy()
    
    /**
     * Sets the current clip to the intersection of the current clip and the specified rectangle
     */
    fun clipRect(rect: Rect) {
        val currentClip = clipStack.last()
        val newClip = currentClip.intersect(rect)
        clipStack[clipStack.size - 1] = newClip
    }
    
    /**
     * Gets the destination bitmap
     */
    fun getBitmap(): Bitmap = bitmap.copy()
    
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
        // Transform the four corners of the rectangle
        val topLeft = transformPoint(rect.left, rect.top)
        val topRight = transformPoint(rect.right, rect.top)
        val bottomLeft = transformPoint(rect.left, rect.bottom)
        val bottomRight = transformPoint(rect.right, rect.bottom)
        
        // Find the bounding box of the transformed rectangle
        val minX = minOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x)
        val maxX = maxOf(topLeft.x, topRight.x, bottomLeft.x, bottomRight.x)
        val minY = minOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)
        val maxY = maxOf(topLeft.y, topRight.y, bottomLeft.y, bottomRight.y)
        
        return Rect(minX, minY, maxX, maxY)
    }
    
    /**
     * Transforms a single point using this matrix
     */
    private fun transformPoint(x: Float, y: Float): Point {
        val newX = x * scaleX + y * skewX + transX
        val newY = x * skewY + y * scaleY + transY
        return Point(newX, newY)
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