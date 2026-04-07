package testing.skia

import com.kanvas.core.*
import testing.GM
import testing.DrawResult

/**
 * ConcavePaths GM test - Concave path drawing
 * This test draws various concave shapes to test path rendering
 * 
 * Note: This is a simplified implementation since Kanvas doesn't have full
 * concave path support yet. We'll implement basic concave shapes.
 */
class ConcavePathsGM : GM() {
    override fun getName(): String = "concavepaths"
    override fun getSize(): Size = Size(512f, 512f)

    override fun onDraw(canvas: Canvas): DrawResult {
        // Draw a simple concave shape (star-like)
        val starPath = Path().apply {
            moveTo(100f, 50f)
            lineTo(150f, 200f)
            lineTo(50f, 100f)
            lineTo(200f, 100f)
            lineTo(100f, 200f)
            close()
        }
        
        val starPaint = Paint().apply {
            color = Color.RED
            style = PaintStyle.FILL
        }
        canvas.drawPath(starPath, starPaint)
        
        // Draw a more complex concave shape
        val complexPath = Path().apply {
            moveTo(250f, 50f)
            lineTo(350f, 50f)
            lineTo(300f, 150f)
            lineTo(400f, 100f)
            lineTo(350f, 200f)
            lineTo(250f, 200f)
            lineTo(300f, 100f)
            close()
        }
        
        val complexPaint = Paint().apply {
            color = Color.BLUE
            style = PaintStyle.FILL
        }
        canvas.drawPath(complexPath, complexPaint)
        
        // Draw some text
        val textPaint = Paint().apply {
            color = Color.BLACK
            style = PaintStyle.FILL
        }
        canvas.drawText("Concave Paths Test", 50f, 300f, textPaint)
        
        return DrawResult.OK
    }
}