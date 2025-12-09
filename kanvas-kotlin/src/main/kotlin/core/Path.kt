package com.kanvas.core

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Path represents a series of points, lines, and curves that can be drawn on a canvas.
 */
class Path {
    
    internal val points: MutableList<Point> = mutableListOf()
    internal val verbs: MutableList<PathVerb> = mutableListOf()
    private var fillType: FillType = FillType.WINDING
    
    /**
     * Resets the path to empty
     */
    fun reset() {
        points.clear()
        verbs.clear()
    }
    
    /**
     * Moves to the specified point
     */
    fun moveTo(x: Float, y: Float) {
        points.add(Point(x, y))
        verbs.add(PathVerb.MOVE)
    }
    
    /**
     * Draws a line to the specified point
     */
    fun lineTo(x: Float, y: Float) {
        points.add(Point(x, y))
        verbs.add(PathVerb.LINE)
    }
    
    /**
     * Draws a quadratic curve to the specified point
     */
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) {
        points.add(Point(x1, y1))
        points.add(Point(x2, y2))
        verbs.add(PathVerb.QUAD)
    }
    
    /**
     * Draws a cubic curve to the specified point
     */
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float) {
        points.add(Point(x1, y1))
        points.add(Point(x2, y2))
        points.add(Point(x3, y3))
        verbs.add(PathVerb.CUBIC)
    }
    
    /**
     * Closes the current contour
     */
    fun close() {
        verbs.add(PathVerb.CLOSE)
    }
    
    /**
     * Adds a rectangle to the path
     */
    fun addRect(rect: Rect, direction: PathDirection = PathDirection.CW) {
        moveTo(rect.left, rect.top)
        if (direction == PathDirection.CW) {
            lineTo(rect.right, rect.top)
            lineTo(rect.right, rect.bottom)
            lineTo(rect.left, rect.bottom)
        } else {
            lineTo(rect.left, rect.bottom)
            lineTo(rect.right, rect.bottom)
            lineTo(rect.right, rect.top)
        }
        close()
    }
    
    /**
     * Adds a circle to the path
     */
    fun addCircle(x: Float, y: Float, radius: Float, direction: PathDirection = PathDirection.CW) {
        // Approximate circle with cubic curves
        val c = 0.551915024494f * radius
        
        moveTo(x + radius, y)
        cubicTo(x + radius, y - c, x + c, y - radius, x, y - radius)
        cubicTo(x - c, y - radius, x - radius, y - c, x - radius, y)
        cubicTo(x - radius, y + c, x - c, y + radius, x, y + radius)
        cubicTo(x + c, y + radius, x + radius, y + c, x + radius, y)
        close()
    }
    
    /**
     * Adds an oval to the path
     */
    fun addOval(oval: Rect, direction: PathDirection = PathDirection.CW) {
        val x = oval.centerX
        val y = oval.centerY
        val rx = oval.width / 2
        val ry = oval.height / 2
        val c = 0.551915024494f
        
        moveTo(x + rx, y)
        cubicTo(x + rx, y - c * ry, x + c * rx, y - ry, x, y - ry)
        cubicTo(x - c * rx, y - ry, x - rx, y - c * ry, x - rx, y)
        cubicTo(x - rx, y + c * ry, x - c * rx, y + ry, x, y + ry)
        cubicTo(x + c * rx, y + ry, x + rx, y + c * ry, x + rx, y)
        close()
    }
    
    /**
     * Adds an arc to the path (simplified implementation)
     * 
     * @param oval The bounding rectangle for the oval that the arc is part of
     * @param startAngle The starting angle of the arc in degrees
     * @param sweepAngle The sweep angle of the arc in degrees
     */
    fun addArc(oval: Rect, startAngle: Float, sweepAngle: Float) {
        // Convert angles to radians
        val startRad = Math.toRadians(startAngle.toDouble()).toFloat()
        val sweepRad = Math.toRadians(sweepAngle.toDouble()).toFloat()
        val endRad = startRad + sweepRad
        
        val centerX = oval.centerX
        val centerY = oval.centerY
        val radiusX = oval.width / 2
        val radiusY = oval.height / 2
        
        // Calculate start and end points
        val startX = centerX + radiusX * kotlin.math.cos(startRad.toDouble()).toFloat()
        val startY = centerY + radiusY * kotlin.math.sin(startRad.toDouble()).toFloat()
        
        val endX = centerX + radiusX * kotlin.math.cos(endRad.toDouble()).toFloat()
        val endY = centerY + radiusY * kotlin.math.sin(endRad.toDouble()).toFloat()
        
        // Move to start point
        moveTo(startX, startY)
        
        // Approximate the arc with cubic curves
        // This is a simplified approach - a full implementation would use multiple segments
        val segments = (kotlin.math.abs(sweepAngle) / 45f).toInt().coerceAtLeast(1)
        
        for (i in 1..segments) {
            val t = i.toFloat() / segments
            val angle = startRad + t * sweepRad
            
            // Control points for the cubic curve
            val control1Angle = startRad + (t - 0.333f) * sweepRad
            val control2Angle = startRad + (t + 0.333f) * sweepRad
            
            val c1x = centerX + radiusX * kotlin.math.cos(control1Angle.toDouble()).toFloat()
            val c1y = centerY + radiusY * kotlin.math.sin(control1Angle.toDouble()).toFloat()
            
            val c2x = centerX + radiusX * kotlin.math.cos(control2Angle.toDouble()).toFloat()
            val c2y = centerY + radiusY * kotlin.math.sin(control2Angle.toDouble()).toFloat()
            
            val endPx = centerX + radiusX * kotlin.math.cos(angle.toDouble()).toFloat()
            val endPy = centerY + radiusY * kotlin.math.sin(angle.toDouble()).toFloat()
            
            cubicTo(c1x, c1y, c2x, c2y, endPx, endPy)
        }
    }
    
    /**
     * Gets the fill type for this path
     */
    fun getFillType(): FillType = fillType
    
    /**
     * Sets the fill type for this path
     */
    fun setFillType(fillType: FillType) {
        this.fillType = fillType
    }
    
    /**
     * Checks if the path is empty
     */
    fun isEmpty(): Boolean = verbs.isEmpty()
    
    /**
     * Gets the bounds of the path
     */
    fun getBounds(): Rect {
        if (points.isEmpty()) return Rect(0f, 0f, 0f, 0f)
        
        var minX = points[0].x
        var minY = points[0].y
        var maxX = points[0].x
        var maxY = points[0].y
        
        for (point in points) {
            minX = minX.coerceAtMost(point.x)
            minY = minY.coerceAtMost(point.y)
            maxX = maxX.coerceAtLeast(point.x)
            maxY = maxY.coerceAtLeast(point.y)
        }
        
        return Rect(minX, minY, maxX, maxY)
    }
    
    /**
     * Transforms the path by the specified matrix
     */
    fun transform(matrix: Matrix) {
        for (i in points.indices) {
            val point = points[i]
            points[i] = Point(
                point.x * matrix.scaleX + point.y * matrix.skewX + matrix.transX,
                point.x * matrix.skewY + point.y * matrix.scaleY + matrix.transY
            )
        }
    }
    
    /**
     * Creates a copy of this path
     */
    fun copy(): Path {
        val newPath = Path()
        newPath.points.addAll(this.points)
        newPath.verbs.addAll(this.verbs)
        newPath.fillType = this.fillType
        return newPath
    }
    
    override fun toString(): String {
        return "Path(points=${points.size}, verbs=${verbs.size}, fillType=$fillType)"
    }
}

/**
 * Represents a 2D point
 */
data class Point(val x: Float, val y: Float)

/**
 * Path verb types
 */
enum class PathVerb {
    MOVE,   // Move to a point
    LINE,   // Draw a line
    QUAD,   // Draw a quadratic curve
    CUBIC,  // Draw a cubic curve
    CLOSE   // Close the current contour
}

/**
 * Fill type for path rendering
 */
enum class FillType {
    WINDING,      // Use winding number rule
    EVEN_ODD,     // Use even-odd rule
    INVERSE_WINDING,  // Inverse winding number rule
    INVERSE_EVEN_ODD  // Inverse even-odd rule
}

/**
 * Direction for path construction
 */
enum class PathDirection {
    CW,   // Clockwise
    CCW  // Counter-clockwise
}

/**
 * Utility functions for path operations
 */
object PathUtils {
    
    /**
     * Computes the distance between two points
     */
    fun distance(p1: Point, p2: Point): Float {
        return sqrt((p2.x - p1.x) * (p2.x - p1.x) + (p2.y - p1.y) * (p2.y - p1.y))
    }
    
    /**
     * Computes the length of a path
     */
    fun computeLength(path: Path): Float {
        var length = 0f
        var lastPoint: Point? = null
        
        for (i in path.verbs.indices) {
            when (path.verbs[i]) {
                PathVerb.MOVE -> {
                    lastPoint = path.points[i]
                }
                PathVerb.LINE -> {
                    if (lastPoint != null) {
                        length += distance(lastPoint!!, path.points[i])
                        lastPoint = path.points[i]
                    }
                }
                PathVerb.QUAD -> {
                    if (lastPoint != null && i + 1 < path.points.size) {
                        // Approximate quadratic curve length
                        val p0 = lastPoint!!
                        val p1 = path.points[i]
                        val p2 = path.points[i + 1]
                        
                        // Simple approximation - could be more accurate
                        length += distance(p0, p1) + distance(p1, p2)
                        lastPoint = p2
                    }
                }
                PathVerb.CUBIC -> {
                    if (lastPoint != null && i + 2 < path.points.size) {
                        // Approximate cubic curve length
                        val p0 = lastPoint!!
                        val p1 = path.points[i]
                        val p2 = path.points[i + 1]
                        val p3 = path.points[i + 2]
                        
                        // Simple approximation - could be more accurate
                        length += distance(p0, p1) + distance(p1, p2) + distance(p2, p3)
                        lastPoint = p3
                    }
                }
                PathVerb.CLOSE -> {
                    // Close connects back to the first point of the contour
                    // For simplicity, we'll skip this in length calculation
                }
            }
        }
        
        return length
    }
}