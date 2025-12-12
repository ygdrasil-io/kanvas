package com.kanvas.core

/**
 * SkPathInterface defines the complete path interface compatible with Skia's SkPath
 * This interface ensures that Kanvas paths have all the methods available in Skia
 */
interface SkPathInterface {
    
    /**
     * Path Construction Methods
     */
    fun reset()
    fun moveTo(x: Float, y: Float)
    fun lineTo(x: Float, y: Float)
    fun quadTo(x1: Float, y1: Float, x2: Float, y2: Float)
    fun conicTo(x1: Float, y1: Float, x2: Float, y2: Float, weight: Float)
    fun cubicTo(x1: Float, y1: Float, x2: Float, y2: Float, x3: Float, y3: Float)
    fun close()
    
    /**
     * Relative Path Construction Methods
     */
    fun rQuadTo(dx1: Float, dy1: Float, dx2: Float, dy2: Float)
    fun rCubicTo(dx1: Float, dy1: Float, dx2: Float, dy2: Float, dx3: Float, dy3: Float)
    
    /**
     * Arc Methods
     */
    fun arcTo(oval: Rect, startAngle: Float, sweepAngle: Float, forceMoveTo: Boolean)
    fun addArc(oval: Rect, startAngle: Float, sweepAngle: Float)
    
    /**
     * Shape Addition Methods
     */
    fun addRect(rect: Rect, direction: PathDirection)
    fun addOval(oval: Rect, direction: PathDirection)
    fun addCircle(x: Float, y: Float, radius: Float, direction: PathDirection)
    
    /**
     * Path Properties
     */
    fun getFillType(): FillType
    fun setFillType(fillType: FillType)
    fun isEmpty(): Boolean
    
    /**
     * Path Analysis
     */
    fun getBounds(): Rect
    fun computeTightBounds(): Rect
    fun getLength(): Float
    
    /**
     * Path Operations
     */
    fun transform(matrix: Matrix): Path
    fun offset(dx: Float, dy: Float): Path
    fun asWinding(): Path
    
    /**
     * Path Contour Operations
     */
    fun getPointCount(): Int
    fun getPoints(): List<Point>
    fun getVerbs(): List<PathVerb>
}