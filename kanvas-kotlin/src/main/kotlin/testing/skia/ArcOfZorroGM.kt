package testing.skia

import com.kanvas.core.*
import testing.GM
import testing.DrawResult
import com.kanvas.core.Size

/**
 * Port of Skia's arcofzorro.cpp test
 * Tests complex arc patterns and path operations.
 * 
 * This test creates intricate arc patterns that resemble the "Zorro" signature mark.
 * It tests arc drawing, path operations, and complex geometric transformations.
 */
class ArcOfZorroGM : GM() {
    override fun getName(): String = "arcofzorro"
    
    override fun getSize(): Size = Size(256f, 256f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        val size = getSize()
        
        // Set background
        val bgPaint = Paint().apply {
            color = Color(0xFF, 0xFF, 0xFF, 255) // White background
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(0f, 0f, size.width, size.height), bgPaint)
        
        // Draw the Zorro arc pattern
        drawZorroPattern(canvas)
        
        // Add descriptive labels
        addLabels(canvas)
        
        return DrawResult.OK
    }
    
    private fun drawZorroPattern(canvas: Canvas) {
        val paint = Paint().apply {
            color = Color(0, 0, 0, 255) // Black arcs
            strokeWidth = 2f
            style = PaintStyle.STROKE
            isAntiAlias = true
        }
        
        // Create a path for the Zorro pattern
        val path = Path()
        
        // Draw the main Zorro arcs - these create the signature "Z" pattern with arcs
        // The original Skia test uses complex arc combinations
        
        // First set of arcs - top part
        path.moveTo(50f, 50f)
        path.addArc(Rect(50f, 50f, 150f, 150f), 0f, 180f)
        
        // Second set of arcs - middle diagonal
        path.moveTo(150f, 150f)
        path.addArc(Rect(100f, 100f, 200f, 200f), 45f, 180f)
        
        // Third set of arcs - bottom part
        path.moveTo(50f, 150f)
        path.addArc(Rect(50f, 100f, 150f, 200f), 0f, -180f)
        
        // Draw additional decorative arcs
        for (i in 0..2) {
            val x = 70f + i * 40f
            val y = 70f + i * 40f
            path.moveTo(x, y)
            path.addArc(Rect(x, y, x + 60f, y + 60f), i * 30f, 120f)
        }
        
        // Draw the path
        canvas.drawPath(path, paint)
        
        // Add some filled arcs for variety
        val fillPaint = Paint().apply {
            color = Color(0x88, 0, 0, 200) // Semi-transparent red
            style = PaintStyle.FILL
            isAntiAlias = true
        }
        
        // Draw filled arcs in different positions
        for (i in 0..3) {
            val x = 180f + i * 15f
            val y = 50f + i * 30f
            val arcPath = Path()
            arcPath.moveTo(x, y)
            arcPath.addArc(Rect(x, y, x + 30f, y + 30f), 0f, 270f)
            arcPath.close()
            canvas.drawPath(arcPath, fillPaint)
        }
    }
    
    private fun addLabels(canvas: Canvas) {
        val titlePaint = Paint().apply {
            color = Color(0, 0, 0, 255)
            textSize = 16f
            style = PaintStyle.FILL
        }
        canvas.drawText("Arc of Zorro Pattern", 20f, 18f, titlePaint)
        
        val infoPaint = Paint().apply {
            color = Color(0x88, 0, 0, 255)
            textSize = 12f
            style = PaintStyle.FILL
        }
        canvas.drawText("Complex arc patterns and path operations", 20f, 240f, infoPaint)
    }
}