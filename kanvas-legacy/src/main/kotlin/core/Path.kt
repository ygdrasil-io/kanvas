package com.kanvas.core

import kotlin.math.sqrt

/**
 * Extension function to convert Float to SkScalar
 */
private fun Float.toSkScalar(): SkScalar {
    return this
}

/**
 * Path represents a series of points, lines, and curves that can be drawn on a canvas.
 */
class Path {
    
    internal val points: MutableList<Point> = mutableListOf()
    internal val verbs: MutableList<PathVerb> = mutableListOf()
    internal val conicWeights: MutableList<Float> = mutableListOf()
    private var fillType: FillType = FillType.WINDING
    
    /**
     * Resets the path to empty
     */
    fun reset() {
        points.clear()
        verbs.clear()
        conicWeights.clear()
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
     * Also known as conicTo in Skia terminology
     */
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float) {
        points.add(Point(x1, y1))
        points.add(Point(x2, y2))
        verbs.add(PathVerb.QUAD)
    }
    
    /**
     * Draws a conic curve to the specified point
     * This is an alias for quadTo, using Skia's terminology
     * @param weight The conic weight (default 1.0, which makes it equivalent to quadTo)
     */
    fun conicTo(x1: Float, y1: Float, x2: Float, y2: Float, weight: Float = 1.0f) {
        if (verbs.isEmpty() || verbs.last() == PathVerb.CLOSE) {
            moveTo(0f, 0f)
        }
        
        if (weight == 1.0f) {
            // Weight of 1.0 makes it equivalent to a quadratic curve
            quadTo(x1, y1, x2, y2)
        } else if (weight.isFinite() && weight > 0) {
            // Store as a true conic with weight
            points.add(Point(x1, y1))
            points.add(Point(x2, y2))
            verbs.add(PathVerb.CONIC)
            conicWeights.add(weight)
        } else {
            // For invalid weights, approximate with lines (like Skia)
            lineTo(x1, y1)
            lineTo(x2, y2)
        }
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
     * Adds an arc to the path using SkScalar precision (inspired by Skia's implementation)
     * 
     * @param oval The bounding rectangle for the oval that the arc is part of
     * @param startAngle The starting angle of the arc in degrees
     * @param sweepAngle The sweep angle of the arc in degrees
     */
    fun addArc(oval: Rect, startAngle: Float, sweepAngle: Float) {
        // Convert to SkScalar for high precision (like Skia)
        val skStartAngle = startAngle.toSkScalar()
        val skSweepAngle = sweepAngle.toSkScalar()
        
        // Normalize angles like Skia does
        val normalizedStart = SkScalarMod(skStartAngle, 360.0f)
        val normalizedSweep = if (skSweepAngle < 0) {
            SkScalarMod(skSweepAngle, 360.0f)
        } else {
            skSweepAngle
        }
        
        // Handle special cases
        if (normalizedSweep == 0.0f) {
            return
        }
        
        // Use more segments for better precision (like Skia's kMaxSegments)
        val segments = SkScalarCeil(SkScalarAbs(normalizedSweep) / 4.0f).coerceAtLeast(1)
        val angleStep = normalizedSweep / segments.toFloat()
        
        val centerX = oval.centerX
        val centerY = oval.centerY
        val radiusX = oval.width / 2.0f
        val radiusY = oval.height / 2.0f
        
        for (i in 0..segments) {
            val angle = normalizedStart + i * angleStep
            val radians = SkScalarDegreesToRadians(angle)
            
            // Use SkScalar trigonometric functions for precision
            val x = centerX + radiusX * SkScalarCos(radians)
            val y = centerY + radiusY * SkScalarSin(radians)
            
            if (i == 0) {
                moveTo(x.toFloat(), y.toFloat())
            } else {
                // Use conicTo for better arc approximation (like Skia)
                // Calculate control point for the conic curve using proper geometry
                val prevAngle = normalizedStart + (i - 1) * angleStep
                val prevRadians = SkScalarDegreesToRadians(prevAngle)
                val prevX = centerX + radiusX * SkScalarCos(prevRadians)
                val prevY = centerY + radiusY * SkScalarSin(prevRadians)
                
                // Calculate the angle between the two points
                val angleDiff = angleStep
                val angleMid = prevAngle + angleDiff / 2.0f
                val midRadians = SkScalarDegreesToRadians(angleMid)
                
                // For circular arcs, the control point should be at a distance
                // that creates a smooth curve. We use a factor based on the angle.
                // This is similar to Skia's approach for approximating arcs with conics.
                val controlDistanceFactor = 1.0f / (1.0f + 0.25f * SkScalarAbs(angleDiff))
                
                // Calculate control point position
                val controlX = centerX + radiusX * SkScalarCos(midRadians) * controlDistanceFactor
                val controlY = centerY + radiusY * SkScalarSin(midRadians) * controlDistanceFactor
                
                conicTo(controlX, controlY, x, y)
            }
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
        
        // For conic curves, we need to approximate their bounds
        // since they can extend beyond the control points
        var pointIndex = 0
        for (i in verbs.indices) {
            when (verbs[i]) {
                PathVerb.CONIC -> {
                    if (pointIndex + 1 < points.size) {
                        val p0 = if (i > 0 && verbs[i-1] != PathVerb.MOVE && verbs[i-1] != PathVerb.CLOSE) {
                            points[pointIndex - 1]
                        } else {
                            // Find the actual start point by looking back through the verbs
                            var startPoint = Point(0f, 0f)
                            var j = i - 1
                            while (j >= 0) {
                                when (verbs[j]) {
                                    PathVerb.MOVE -> {
                                        startPoint = points[j]
                                        break
                                    }
                                    PathVerb.LINE, PathVerb.QUAD, PathVerb.CUBIC, PathVerb.CONIC -> {
                                        startPoint = points[j]
                                        break
                                    }
                                    else -> {
                                        // Keep looking back
                                    }
                                }
                                j--
                            }
                            startPoint
                        }
                        val p1 = points[pointIndex]
                        val p2 = points[pointIndex + 1]
                        val conicCount = verbs.take(i).count { it == PathVerb.CONIC }
                        val weight = conicWeights[conicCount]
                        
                        // Approximate conic bounds by including control points
                        // and estimating the curve's extent based on weight
                        minX = minX.coerceAtMost(p0.x.coerceAtMost(p1.x).coerceAtMost(p2.x))
                        minY = minY.coerceAtMost(p0.y.coerceAtMost(p1.y).coerceAtMost(p2.y))
                        maxX = maxX.coerceAtLeast(p0.x.coerceAtLeast(p1.x).coerceAtLeast(p2.x))
                        maxY = maxY.coerceAtLeast(p0.y.coerceAtLeast(p1.y).coerceAtLeast(p2.y))
                        
                        pointIndex += 2
                    }
                }
                PathVerb.QUAD, PathVerb.CUBIC -> {
                    // For quad and cubic, just include their points
                    pointIndex += if (verbs[i] == PathVerb.QUAD) 2 else 3
                }
                else -> {
                    if (verbs[i] != PathVerb.CLOSE) {
                        pointIndex++
                    }
                }
            }
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
        // Conic weights are not transformed as they are scalar values
    }
    
    /**
     * Creates a copy of this path
     */
    fun copy(): Path {
        val newPath = Path()
        newPath.points.addAll(this.points)
        newPath.verbs.addAll(this.verbs)
        newPath.conicWeights.addAll(this.conicWeights)
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
    CONIC,  // Draw a conic curve (with weight)
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
                PathVerb.CONIC -> {
                    if (lastPoint != null && i + 1 < path.points.size) {
                        // Approximate conic curve length
                        val p0 = lastPoint!!
                        val p1 = path.points[i]
                        val p2 = path.points[i + 1]
                        val conicCount = path.verbs.take(i).count { it == PathVerb.CONIC }
                        val weight = path.conicWeights[conicCount]
                        
                        // For conic curves, the length depends on the weight
                        // Weight = 1.0 is equivalent to quadratic, other weights create different curves
                        if (weight == 1.0f) {
                            // Quadratic approximation
                            length += distance(p0, p1) + distance(p1, p2)
                        } else {
                            // For other weights, use a simple approximation
                            // This could be improved with a proper conic length calculation
                            length += distance(p0, p1) + distance(p1, p2)
                        }
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