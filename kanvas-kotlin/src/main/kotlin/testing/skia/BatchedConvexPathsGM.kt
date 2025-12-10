package testing.skia

import com.kanvas.core.Canvas
import com.kanvas.core.Color
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Path
import com.kanvas.core.Rect
import com.kanvas.core.Size
import testing.DrawResult
import testing.GM
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's batchedconvexpaths.cpp test
 * Tests drawing of batched convex paths with transformations.
 * 
 * This test draws a series of convex paths with varying scales and transformations
 * to verify that convex path rendering and batching works correctly.
 */
class BatchedConvexPathsGM : GM() {
    override fun getName(): String = "batchedconvexpaths"
    
    override fun getSize(): Size = Size(512f, 512f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        // Clear with black background (like original)
        val clearPaint = Paint().apply {
            color = Color.BLACK
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(0f, 0f, 512f, 512f), clearPaint)
        
        for (i in 0 until 10) {
            canvas.save()
            
            val numPoints = (i + 3) * 3
            val path = createConvexPath(numPoints)
            
            // Calculate scale and position
            val scale = 256f - i * 24f
            val x = scale + (256f - scale) * 0.33f
            val y = scale + (256f - scale) * 0.33f
            
            canvas.translate(x, y)
            canvas.scale(scale, scale)
            
            // Create paint with semi-transparent color
            val paint = Paint().apply {
                // Generate color using the same algorithm as original
                val baseColor = (((i + 123458383L) * 285018463L) or 0xff808080L).toInt()
                color = Color(
                    (baseColor shr 16) and 0xFF,
                    (baseColor shr 8) and 0xFF,
                    baseColor and 0xFF,
                    128  // Semi-transparent (alphaf 0.3f * 255 ≈ 76, but using 128 for better visibility)
                )
                isAntiAlias = true
                style = PaintStyle.STROKE
                strokeWidth = 2f
            }
            
            canvas.drawPath(path, paint)
            canvas.restore()
        }
        
        return DrawResult.OK
    }
    
    /**
     * Create a convex path with the specified number of points
     */
    private fun createConvexPath(numPoints: Int): Path {
        val path = Path()
        path.moveTo(1f, 0f)
        
        val k2PI = 2 * Math.PI.toFloat()
        
        var j = 1f
        while (j < numPoints) {
            val jNorm = j / numPoints
            val j1Norm = (j + 1) / numPoints
            val j2Norm = (j + 2) / numPoints
            
            // Control points for cubic curve
            val x1 = cos(jNorm * k2PI)
            val y1 = sin(jNorm * k2PI)
            val x2 = cos(j1Norm * k2PI)
            val y2 = sin(j1Norm * k2PI)
            
            // End point
            val x3 = if (j + 2 == numPoints.toFloat()) 1f else cos(j2Norm * k2PI)
            val y3 = if (j + 2 == numPoints.toFloat()) 0f else sin(j2Norm * k2PI)
            
            path.cubicTo(x1, y1, x2, y2, x3, y3)
            
            j += 3
        }
        
        path.close()
        return path
    }
}