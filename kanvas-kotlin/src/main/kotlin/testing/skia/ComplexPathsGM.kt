package testing.skia

import com.kanvas.core.Canvas
import com.kanvas.core.Color
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Path
import com.kanvas.core.PathDirection
import com.kanvas.core.Rect
import com.kanvas.core.Shaders
import com.kanvas.core.Size
import testing.DrawResult
import testing.GM
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * ComplexPathsGM - Intermediate Level 2 test
 * Tests complex path operations with transformations, gradients, and advanced drawing features
 * This test demonstrates Kanvas' capability to handle sophisticated path operations
 * that go beyond basic drawing but don't require GPU or 3D features.
 */
class ComplexPathsGM : GM() {
    override fun getName(): String = "complexpaths"

    override fun getSize(): Size = Size(400f, 400f)

    override fun onDraw(canvas: Canvas): DrawResult {
        return try {
            val size = getSize()
            // Set background to dark gray for better contrast
            val bgPaint = Paint().apply {
                color = Color(50, 50, 50, 255)
                style = PaintStyle.FILL
            }
            canvas.drawRect(Rect(0f, 0f, size.width, size.height), bgPaint)

            // Test 1: Complex star pattern with transformations
            drawStarPattern(canvas, 50f, 50f, 80f, 12)

            // Test 2: Interlocking circles with gradients
            drawInterlockingCircles(canvas, 200f, 100f, 60f)

            // Test 3: Advanced path with multiple segments and styles
            drawAdvancedPath(canvas, 50f, 200f)

            // Test 4: Transformations with complex shapes
            drawTransformedShapes(canvas, 200f, 250f)

            // Test 5: Gradient-filled complex path
            drawGradientPath(canvas, 100f, 300f)

            DrawResult.OK
        } catch (e: Exception) {
            println("Error in ComplexPathsGM: ${e.message}")
            DrawResult.FAIL
        }
    }

    private fun drawStarPattern(canvas: Canvas, centerX: Float, centerY: Float, radius: Float, points: Int) {
        val starPath = Path()
        val angleStep = 2 * PI / points

        // Create a star path
        for (i in 0 until points) {
            val angle = i * angleStep
            val x = centerX + radius * cos(angle).toFloat()
            val y = centerY + radius * sin(angle).toFloat()
            
            if (i == 0) {
                starPath.moveTo(x, y)
            } else {
                starPath.lineTo(x, y)
            }
        }
        starPath.close()

        // Draw with gradient
        val colors = listOf(
            Color(255, 0, 0, 255),
            Color(0, 255, 0, 255),
            Color(0, 0, 255, 255)
        )
        val positions = listOf(0f, 0.5f, 1f)
        val gradient = Shaders.makeRadialGradient(
            colors, positions, 
            com.kanvas.core.Point(centerX, centerY), radius
        )

        val paint = Paint().apply {
            shader = gradient
            style = PaintStyle.FILL
            isAntiAlias = true
        }

        canvas.drawPath(starPath, paint)

        // Add outline
        val outlinePaint = Paint().apply {
            color = Color(255, 255, 255, 200)
            style = PaintStyle.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        canvas.drawPath(starPath, outlinePaint)
    }

    private fun drawInterlockingCircles(canvas: Canvas, centerX: Float, centerY: Float, radius: Float) {
        val circleSpacing = radius * 0.8f
        
        // Create a complex path with multiple circles
        val path = Path()
        
        // Add 3 interlocking circles
        for (i in 0 until 3) {
            val angle = i * 2 * PI / 3
            val cx = centerX + circleSpacing * cos(angle).toFloat()
            val cy = centerY + circleSpacing * sin(angle).toFloat()
            
            path.addCircle(cx, cy, radius * 0.7f, PathDirection.CW)
        }

        // Apply complex path operations
        val paint = Paint().apply {
            color = Color(255, 100, 150, 200)
            style = PaintStyle.FILL
            isAntiAlias = true
        }

        canvas.drawPath(path, paint)
    }

    private fun drawAdvancedPath(canvas: Canvas, startX: Float, startY: Float) {
        val path = Path()
        
        // Start with a complex path combining lines, curves, and arcs
        path.moveTo(startX, startY)
        path.lineTo(startX + 50f, startY + 30f)
        path.cubicTo(
            startX + 70f, startY + 10f,  // control point 1
            startX + 90f, startY + 50f,  // control point 2
            startX + 110f, startY + 30f  // end point
        )
        path.quadTo(
            startX + 130f, startY + 60f,  // control point
            startX + 150f, startY + 40f   // end point
        )
        
        // Add an arc
        val rect = Rect(startX + 150f, startY + 20f, startX + 190f, startY + 60f)
        path.addArc(rect, 0f, 180f)
        
        path.close()

        // Draw with different styles
        val fillPaint = Paint().apply {
            color = Color(100, 200, 100, 180)
            style = PaintStyle.FILL
            isAntiAlias = true
        }
        
        val strokePaint = Paint().apply {
            color = Color(0, 150, 200, 255)
            style = PaintStyle.STROKE
            strokeWidth = 3f
            isAntiAlias = true
        }

        canvas.drawPath(path, fillPaint)
        canvas.drawPath(path, strokePaint)
    }

    private fun drawTransformedShapes(canvas: Canvas, centerX: Float, centerY: Float) {
        // Save current state
        canvas.save()
        
        // Apply transformations
        canvas.translate(centerX, centerY)
        canvas.rotate(15f, 0f, 0f)  // Rotate around origin after translation
        
        // Draw a complex shape
        val path = Path()
        path.moveTo(-30f, -20f)
        path.lineTo(30f, -20f)
        path.lineTo(30f, 20f)
        path.lineTo(-30f, 20f)
        path.close()
        
        // Add some decorative elements
        path.moveTo(-20f, -30f)
        path.lineTo(0f, -50f)
        path.lineTo(20f, -30f)
        
        val paint = Paint().apply {
            color = Color(200, 100, 200, 220)
            style = PaintStyle.FILL
            isAntiAlias = true
        }

        canvas.drawPath(path, paint)
        
        // Restore state
        canvas.restore()
    }

    private fun drawGradientPath(canvas: Canvas, startX: Float, startY: Float) {
        val path = Path()
        
        // Create a complex wave-like path
        val segments = 8
        val segmentWidth = 60f
        
        path.moveTo(startX, startY)
        for (i in 0..segments) {
            val x = startX + i * segmentWidth
            val y = startY + 30f * sin(i * PI / segments * 2).toFloat()
            path.lineTo(x, y)
        }
        
        // Close the path with a curve
        path.lineTo(startX + segments * segmentWidth, startY + 40f)
        path.lineTo(startX, startY + 40f)
        path.close()

        // Create a complex gradient
        val colors = listOf(
            Color(255, 0, 0, 255),
            Color(255, 255, 0, 255),
            Color(0, 255, 0, 255),
            Color(0, 255, 255, 255),
            Color(0, 0, 255, 255)
        )
        
        val positions = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        
        val gradient = Shaders.makeLinearGradient(
            colors, positions,
            com.kanvas.core.Point(startX, startY),
            com.kanvas.core.Point(startX + segments * segmentWidth, startY + 40f)
        )

        val paint = Paint().apply {
            shader = gradient
            style = PaintStyle.FILL
            isAntiAlias = true
        }

        canvas.drawPath(path, paint)
        
        // Add border
        val borderPaint = Paint().apply {
            color = Color(255, 255, 255, 200)
            style = PaintStyle.STROKE
            strokeWidth = 1.5f
            isAntiAlias = true
        }
        canvas.drawPath(path, borderPaint)
    }
}