package com.kanvas.core

import java.nio.ByteBuffer
import kotlin.math.abs
import core.*

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
    // Track anti-aliasing for each clip level
    private val clipAntiAliasStack: MutableList<Boolean> = mutableListOf(false)
    // Current paint properties
    private var currentPaint: Paint = Paint()
    /**
     * Saves the current canvas state (matrix and clip) onto a stack
     */
    fun save() {
        matrixStack.add(matrixStack.last().copy())
        clipStack.add(clipStack.last().copy())
        clipAntiAliasStack.add(clipAntiAliasStack.last())
    }
    /**
     * Restores the canvas state from the stack
     */
    fun restore() {
        if (matrixStack.size > 1) matrixStack.removeAt(matrixStack.size - 1)
        if (clipStack.size > 1) clipStack.removeAt(clipStack.size - 1)
        if (clipAntiAliasStack.size > 1) clipAntiAliasStack.removeAt(clipAntiAliasStack.size - 1)
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
        
        // Implement actual raster drawing with anti-aliasing info
        drawRectRaster(clippedRect, paint, clipAntiAliasStack.last())
    }
    /**
     * Internal method for raster rectangle drawing
     * @param rect The rectangle to draw
     * @param paint The paint to use
     * @param useClipAntiAlias Whether to apply clip anti-aliasing
     */
    private fun drawRectRaster(rect: Rect, paint: Paint, useClipAntiAlias: Boolean) {
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
                        
                        // Apply clip anti-aliasing if enabled
                        val finalColor = if (useClipAntiAlias) {
                            // Calculate distance from clip edges for anti-aliasing
                            val edgeDistanceX = minOf(
                                x - rect.left, 
                                rect.right - x
                            )
                            val edgeDistanceY = minOf(
                                y - rect.top,
                                rect.bottom - y
                            )
                            val edgeDistance = minOf(edgeDistanceX, edgeDistanceY)
                            
                            // If pixel is near the edge (within 0.5px), apply anti-aliasing
                            if (edgeDistance < 0.5f) {
                                // Calculate alpha based on distance from edge (0 = fully transparent, 1 = fully opaque)
                                val edgeAlpha = (edgeDistance / 0.5f).coerceIn(0f, 1f)
                                val antiAliasedColor = applyAlpha(newColor, (paint.alpha * edgeAlpha).toInt())
                                blendColors(antiAliasedColor, existingColor, paint.blendMode)
                            } else {
                                blendColors(newColor, existingColor, paint.blendMode)
                            }
                        } else {
                            blendColors(newColor, existingColor, paint.blendMode)
                        }
                        
                        bitmap.setPixel(x, y, finalColor)
                    }
                }
            }
            PaintStyle.STROKE -> {
                // Draw rectangle outline
                val halfStroke = paint.strokeWidth / 2
                
                // Top edge
                drawLineInternal(left.toFloat(), top.toFloat(), right.toFloat(), top.toFloat(), paint, useClipAntiAlias)
                // Bottom edge
                drawLineInternal(left.toFloat(), bottom.toFloat(), right.toFloat(), bottom.toFloat(), paint, useClipAntiAlias)
                // Left edge
                drawLineInternal(left.toFloat(), top.toFloat(), left.toFloat(), bottom.toFloat(), paint, useClipAntiAlias)
                // Right edge
                drawLineInternal(right.toFloat(), top.toFloat(), right.toFloat(), bottom.toFloat(), paint, useClipAntiAlias)
            }
            PaintStyle.FILL_AND_STROKE -> {
                // Fill first
                drawRectRaster(rect, paint.copy().apply { style = PaintStyle.FILL }, false)
                // Then stroke
                drawRectRaster(rect, paint.copy().apply { style = PaintStyle.STROKE }, false)
            }
        }
    }
    /**
     * Draws a line between two points
     */
    fun drawLine(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint = currentPaint) {
        // Check if we should use anti-aliasing
        val useAA = paint.isAntiAlias
        
        if (useAA) {
            drawLineAA(x1, y1, x2, y2, paint)
        } else {
            // Use the original Bresenham algorithm for non-AA lines
            drawLineInternal(x1, y1, x2, y2, paint, false)
        }
    }
    /**
     * Internal line drawing using Bresenham's algorithm
     * @param useClipAntiAlias Whether to apply clip anti-aliasing
     */
    private fun drawLineInternal(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint, useClipAntiAlias: Boolean = false) {
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
                
                val blendedColor = if (useClipAntiAlias) {
                    // For clip anti-aliasing, we need to check if this pixel is on the clip edge
                    // This is a simplified approach - in a full implementation, we would track
                    // the exact clip boundaries and calculate distance to edges
                    // For now, we'll apply a subtle anti-aliasing effect
                    val edgeAlpha = 0.8f // Slightly reduce alpha for anti-aliased effect
                    val antiAliasedColor = applyAlpha(newColor, (paint.alpha * edgeAlpha).toInt())
                    blendColors(antiAliasedColor, existingColor, paint.blendMode)
                } else {
                    blendColors(newColor, existingColor, paint.blendMode)
                }
                
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
     * Draws an anti-aliased line using Skia-like coverage approach
     */
    private fun drawLineAA(x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint) {
        // Get the bounding box of the line
        val minX = minOf(x1, x2).toInt() - 1
        val maxX = maxOf(x1, x2).toInt() + 1
        val minY = minOf(y1, y2).toInt() - 1
        val maxY = maxOf(y1, y2).toInt() + 1
        
        // For each pixel in the bounding box, compute coverage
        for (y in minY..maxY) {
            for (x in maxOf(0, minX)..minOf(bitmap.getWidth() - 1, maxX)) {
                if (x !in 0 until bitmap.getWidth() || y !in 0 until bitmap.getHeight()) {
                    continue
                }
                
                // Compute distance from pixel center to line
                val pixelCenterX = x + 0.5f
                val pixelCenterY = y + 0.5f
                val distance = computeDistanceToLine(pixelCenterX, pixelCenterY, x1, y1, x2, y2)
                
                // Get coverage from table (like Skia)
                val coverage = getCoverage(distance)
                
                if (coverage.toInt() == 0) continue // Skip if no coverage
                
                // Apply coverage to the color
                val srcColor = applyAlpha(paint.color, paint.alpha)
                val dstColor = bitmap.getPixel(x, y)
                
                // Blend using coverage (like Skia's SkAlphaMulQ)
                val blendedColor = blendWithCoverage(srcColor, dstColor, coverage)
                bitmap.setPixel(x, y, blendedColor)
            }
        }
    }
    /**
     * Blend two colors using coverage (like Skia's alpha blending)
     */
    private fun blendWithCoverage(src: Color, dst: Color, coverage: SkAlpha): Color {
        val invCoverage = (255 - coverage.toInt()).toFloat() / 255f
        val coverageRatio = coverage.toInt().toFloat() / 255f
        
        return Color(
            (src.red * coverageRatio + dst.red * invCoverage).toInt().coerceIn(0, 255),
            (src.green * coverageRatio + dst.green * invCoverage).toInt().coerceIn(0, 255),
            (src.blue * coverageRatio + dst.blue * invCoverage).toInt().coerceIn(0, 255),
            (src.alpha * coverageRatio + dst.alpha * invCoverage).toInt().coerceIn(0, 255)
        )
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
        drawRectRaster(bounds, paint, false)
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
                        drawLineInternal(currentPoint!!.x, currentPoint!!.y, endPoint.x, endPoint.y, paint, false)
                        currentPoint = endPoint
                    }
                    i++
                }
                PathVerb.CONIC -> {
                    if (currentPoint != null && i + 1 < path.points.size) {
                        val controlPoint = path.points[i]
                        val endPoint = path.points[i + 1]
                        val conicCount = path.verbs.take(i).count { it == PathVerb.CONIC }
                        val weight = path.conicWeights[conicCount]
                        
                        if (weight == 1.0f) {
                            // Weight of 1.0 is equivalent to quadratic
                            drawQuadraticCurve(currentPoint!!, controlPoint, endPoint, paint)
                        } else {
                            // Draw conic curve with the specified weight
                            drawConicCurve(currentPoint!!, controlPoint, endPoint, weight, paint)
                        }
                        currentPoint = endPoint
                        i += 2
                    }
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
            drawLineInternal(prev.x, prev.y, current.x, current.y, paint, false)
            prev = current
        }
    }
    /**
     * Draws a conic curve by approximating with line segments
     * @param weight The conic weight (1.0 = quadratic, other values create different curves)
     */
    private fun drawConicCurve(p0: Point, p1: Point, p2: Point, weight: Float, paint: Paint) {
        // For conic curves, we use rational quadratic Bezier curves
        // The formula is: P(t) = (1-t)^2 * P0 + 2*(1-t)*t*w*P1 + t^2 * P2
        //                     -------------------------------------------
        //                     (1-t)^2 + 2*(1-t)*t*w + t^2
        
        val segments = 8
        var prev = p0
        
        for (i in 1..segments) {
            val t = i.toFloat() / segments
            val oneMinusT = 1 - t
            
            // Calculate denominator
            val denominator = oneMinusT * oneMinusT + 2 * oneMinusT * t * weight + t * t
            
            if (denominator > 0) {
                // Calculate numerator components
                val term0 = oneMinusT * oneMinusT * p0.x
                val term1 = 2 * oneMinusT * t * weight * p1.x
                val term2 = t * t * p2.x
                val x = (term0 + term1 + term2) / denominator
                
                val term0y = oneMinusT * oneMinusT * p0.y
                val term1y = 2 * oneMinusT * t * weight * p1.y
                val term2y = t * t * p2.y
                val y = (term0y + term1y + term2y) / denominator
                
                val current = Point(x, y)
                drawLineInternal(prev.x, prev.y, current.x, current.y, paint, false)
                prev = current
            }
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
            drawLineInternal(prev.x, prev.y, current.x, current.y, paint, false)
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
     * @param rect The rectangle to clip to
     * @param antiAlias Whether to use anti-aliasing for the clip edges (default: false)
     */
    fun clipRect(rect: Rect, antiAlias: Boolean = false) {
        val currentClip = clipStack.last()
        
        if (antiAlias) {
            // Implement anti-aliased clipping similar to Skia
            // Anti-aliased clipping creates a smooth transition at the clip edges
            // We'll expand the clip slightly and store the anti-alias information
            
            // Expand the rectangle slightly for anti-aliasing (similar to Skia's approach)
            val expandedRect = rect.copy().apply {
                // Expand by 0.5 pixels in each direction for anti-aliasing
                inset(-0.5f, -0.5f)
            }
            
            val newClip = currentClip.intersect(expandedRect)
            clipStack[clipStack.size - 1] = newClip
            
            // Track that this clip level uses anti-aliasing
            clipAntiAliasStack[clipAntiAliasStack.size - 1] = true
        } else {
            // Standard aliased clipping
            val newClip = currentClip.intersect(rect)
            clipStack[clipStack.size - 1] = newClip
            // No anti-aliasing for this clip
            clipAntiAliasStack[clipAntiAliasStack.size - 1] = false
        }
    }
    /**
     * Gets the destination bitmap
     */
    fun getBitmap(): Bitmap = bitmap.copy()
    companion object {
        // Anti-aliasing coverage table (like Skia's gLineCoverage)
        private val COVERAGE_TABLE: ByteArray = ByteArray(256) {
            // Skia-like coverage function: smooth transition
            when {
                it < 64 -> (it * 4).toByte() // Linear ramp up
                it < 192 -> 255.toByte() // Full coverage
                else -> ((255 - it) * 4).toByte() // Linear ramp down
            }
        }
        
        /**
         * Get coverage value for a given distance (like Skia's LineCoverage)
         */
        fun getCoverage(distance: Float): SkAlpha {
            // Convert distance to 0-255 range
            val normalizedDist = (distance * 255f).coerceIn(0f, 255f)
            val index = normalizedDist.toInt().coerceIn(0, 255)
            return COVERAGE_TABLE[index].toUByte()
        }
        
        /**
         * Compute distance from point to line segment
         */
        fun computeDistanceToLine(x: Float, y: Float, x1: Float, y1: Float, x2: Float, y2: Float): Float {
            // Vector from point to line start
            val vx = x - x1
            val vy = y - y1
            
            // Line direction vector
            val dx = x2 - x1
            val dy = y2 - y1
            
            val lengthSquared = dx * dx + dy * dy
            if (lengthSquared == 0f) {
                return kotlin.math.sqrt(vx * vx + vy * vy) // Point to point distance
            }
            
            // Projection factor
            val t = (vx * dx + vy * dy) / lengthSquared
            
            // Clamp to segment
            val clampedT = t.coerceIn(0f, 1f)
            
            // Closest point on segment
            val closestX = x1 + clampedT * dx
            val closestY = y1 + clampedT * dy
            
            // Distance to closest point
            return kotlin.math.sqrt((x - closestX) * (x - closestX) + (y - closestY) * (y - closestY))
        }
        
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