package testing.skia

import com.kanvas.core.*
import testing.GM
import testing.DrawResult

/**
 * Bleed GM test - Color bleeding tests
 * This test checks for color bleeding at edges and boundaries
 * 
 * Note: This is a simplified implementation since Kanvas doesn't have full
 * color bleeding detection yet. We'll implement basic edge cases.
 */
class BleedGM : GM() {
    override fun getName(): String = "bleed"
    override fun getSize(): Size = Size(512f, 512f)

    override fun onDraw(canvas: Canvas): DrawResult {
        // For now, implement a basic test that draws shapes with different
        // colors and checks for proper edge handling
        
        // Draw a red rectangle
        val redPaint = Paint().apply {
            color = Color.RED
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(50f, 50f, 150f, 150f), redPaint)
        
        // Draw an overlapping blue rectangle
        val bluePaint = Paint().apply {
            color = Color.BLUE
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(100f, 100f, 200f, 200f), bluePaint)
        
        // Draw a green circle
        val greenPaint = Paint().apply {
            color = Color.GREEN
            style = PaintStyle.FILL
        }
        canvas.drawCircle(300f, 150f, 50f, greenPaint)
        
        // Draw some text
        val textPaint = Paint().apply {
            color = Color.BLACK
            style = PaintStyle.FILL
        }
        canvas.drawText("Bleed Test", 50f, 300f, textPaint)
        
        return DrawResult.OK
    }
}