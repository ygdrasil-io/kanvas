package testing.skia

import com.kanvas.core.*
import testing.DrawResult
import testing.GM

/**
 * ConvexPathsGM tests drawing of convex paths.
 * This corresponds to the Skia convexpaths.cpp test.
 */
class ConvexPathsGM : GM() {
    
    override fun getName(): String = "convexpaths"
    
    override fun getSize(): Size = Size(512f, 512f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        // Draw background
        drawBackground(canvas)
        
        // Set up paint for paths
        val pathPaint = Paint().apply {
            color = Color.BLUE
            style = PaintStyle.FILL
            isAntiAlias = true
        }
        
        val strokePaint = Paint().apply {
            color = Color.RED
            style = PaintStyle.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        
        // Test 1: Simple convex polygon
        val polyPath = Path().apply {
            moveTo(100f, 100f)
            lineTo(200f, 100f)
            lineTo(150f, 150f)
            close()
        }
        
        canvas.drawPath(polyPath, pathPaint)
        canvas.drawPath(polyPath, strokePaint)
        
        // Test 2: Convex quadrilateral
        val quadPath = Path().apply {
            moveTo(50f, 200f)
            lineTo(150f, 200f)
            lineTo(150f, 300f)
            lineTo(50f, 250f)
            close()
        }
        
        canvas.drawPath(quadPath, pathPaint)
        canvas.drawPath(quadPath, strokePaint)
        
        // Test 3: Convex pentagon
        val pentagonPath = Path().apply {
            moveTo(250f, 100f)
            lineTo(350f, 100f)
            lineTo(375f, 150f)
            lineTo(325f, 200f)
            lineTo(225f, 150f)
            close()
        }
        
        canvas.drawPath(pentagonPath, pathPaint)
        canvas.drawPath(pentagonPath, strokePaint)
        
        // Test 4: Multiple convex shapes
        val shapes = listOf(
            listOf(Point(50f, 350f), Point(150f, 350f), Point(100f, 450f)),
            listOf(Point(200f, 350f), Point(300f, 350f), Point(300f, 450f), Point(200f, 400f)),
            listOf(Point(350f, 350f), Point(450f, 350f), Point(475f, 400f), Point(425f, 450f), Point(375f, 400f))
        )
        
        shapes.forEach { shape ->
            val shapePath = Path().apply {
                moveTo(shape[0].x, shape[0].y)
                for (i in 1 until shape.size) {
                    lineTo(shape[i].x, shape[i].y)
                }
                close()
            }
            canvas.drawPath(shapePath, pathPaint)
            canvas.drawPath(shapePath, strokePaint)
        }
        
        // Test 5: Convex paths with transformations
        canvas.save()
        canvas.translate(100f, 475f)
        
        // Scaled convex path
        canvas.save()
        canvas.scale(1.5f, 1.5f)
        val scaledPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(50f, 0f)
            lineTo(25f, 50f)
            close()
        }
        pathPaint.color = Color(0, 128, 255, 255) // Light blue (0.5*255)
        canvas.drawPath(scaledPath, pathPaint)
        canvas.drawPath(scaledPath, strokePaint)
        canvas.restore()
        
        // Rotated convex path
        canvas.save()
        canvas.translate(100f, 0f)
        canvas.rotate(30f, 1f, 1f)
        val rotatedPath = Path().apply {
            moveTo(0f, 0f)
            lineTo(40f, 0f)
            lineTo(40f, 40f)
            lineTo(0f, 20f)
            close()
        }
        pathPaint.color = Color(255, 128, 0, 255) // Orange (0.5*255)
        canvas.drawPath(rotatedPath, pathPaint)
        canvas.drawPath(rotatedPath, strokePaint)
        canvas.restore()
        
        canvas.restore()
        
        return DrawResult.OK
    }
}

data class Point(val x: Float, val y: Float)