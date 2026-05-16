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
import kotlin.math.PI

/**
 * Port of Skia's addarc.cpp test
 * Tests arc drawing functionality using Path.addArc.
 * 
 * This test verifies that arcs can be correctly added to paths
 * and drawn with proper anti-aliasing.
 */
class AddArcGM : GM() {
    override fun getName(): String = "addarc"
    
    override fun getSize(): Size = Size(400f, 300f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        val size = getSize()
        
        // Set white background
        val bgPaint = Paint().apply {
            color = Color(0xFF, 0xFF, 0xFF, 255)
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(0f, 0f, size.width, size.height), bgPaint)
        
        // Test basic arcs
        testBasicArcs(canvas)
        
        // Test arc combinations
        testArcCombinations(canvas)
        
        // Test filled arcs
        testFilledArcs(canvas)
        
        return DrawResult.OK
    }
    
    private fun testBasicArcs(canvas: Canvas) {
        val strokePaint = Paint().apply {
            color = Color(0, 0, 0xFF, 255) // Blue
            strokeWidth = 3f
            style = PaintStyle.STROKE
            isAntiAlias = true
        }
        
        // Draw a simple arc
        val path1 = Path().apply {
            addArc(Rect(50f, 50f, 150f, 150f), 0f, 90f)
        }
        canvas.drawPath(path1, strokePaint)
        
        // Draw a larger arc
        val path2 = Path().apply {
            addArc(Rect(200f, 50f, 300f, 150f), 45f, 180f)
        }
        canvas.drawPath(path2, strokePaint)
        
        // Draw a full circle using arc
        val path3 = Path().apply {
            addArc(Rect(50f, 200f, 150f, 300f), 0f, 360f)
        }
        canvas.drawPath(path3, strokePaint)
        
        // Add labels
        val textPaint = Paint().apply {
            color = Color(0, 0, 0, 255)
            textSize = 14f
            style = PaintStyle.FILL
        }
        canvas.drawText("90° arc", 60f, 165f, textPaint)
        canvas.drawText("180° arc", 210f, 165f, textPaint)
        canvas.drawText("360° arc (circle)", 40f, 315f, textPaint)
    }
    
    private fun testArcCombinations(canvas: Canvas) {
        val strokePaint = Paint().apply {
            color = Color(0xFF, 0, 0, 255) // Red
            strokeWidth = 2f
            style = PaintStyle.STROKE
            isAntiAlias = true
        }
        
        // Create a path with multiple arcs
        val complexPath = Path().apply {
            moveTo(200f, 200f)
            addArc(Rect(200f, 200f, 250f, 250f), 0f, 90f)
            lineTo(250f, 250f)
            addArc(Rect(250f, 200f, 300f, 250f), 90f, 180f)
            close()
        }
        canvas.drawPath(complexPath, strokePaint)
        
        // Draw a decorative arc pattern
        val decorativePaint = Paint().apply {
            color = Color(0, 0x88, 0, 255) // Dark green
            strokeWidth = 4f
            style = PaintStyle.STROKE
            isAntiAlias = true
        }
        
        val decorativePath = Path().apply {
            for (i in 0..3) {
                val angle = i * 90f
                addArc(Rect(320f, 220f, 380f, 280f), angle, 60f)
            }
        }
        canvas.drawPath(decorativePath, decorativePaint)
        
        val textPaint = Paint().apply {
            color = Color(0, 0, 0, 255)
            textSize = 12f
            style = PaintStyle.FILL
        }
        canvas.drawText("Complex arc path", 200f, 310f, textPaint)
        canvas.drawText("Decorative arcs", 320f, 310f, textPaint)
    }
    
    private fun testFilledArcs(canvas: Canvas) {
        val fillPaint = Paint().apply {
            color = Color(0x88, 0, 0xFF, 180) // Purple with transparency
            style = PaintStyle.FILL
            isAntiAlias = true
        }
        
        // Draw filled arcs
        val filledPath = Path().apply {
            addArc(Rect(50f, 200f, 150f, 300f), 0f, 180f)
            lineTo(100f, 250f)
            close()
        }
        canvas.drawPath(filledPath, fillPaint)
        
        // Draw another filled arc
        val filledPath2 = Path().apply {
            addArc(Rect(150f, 200f, 250f, 300f), 45f, 270f)
            lineTo(200f, 250f)
            close()
        }
        canvas.drawPath(filledPath2, fillPaint)
        
        val textPaint = Paint().apply {
            color = Color(0, 0, 0, 255)
            textSize = 12f
            style = PaintStyle.FILL
        }
        canvas.drawText("Filled arcs", 60f, 315f, textPaint)
    }
    
    /**
     * Helper function to convert degrees to radians
     */
    private fun degreesToRadians(degrees: Float): Float {
        return degrees * (PI.toFloat() / 180f)
    }
}