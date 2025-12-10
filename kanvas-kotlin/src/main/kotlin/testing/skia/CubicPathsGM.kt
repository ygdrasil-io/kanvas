package testing.skia

import com.kanvas.core.*
import testing.GM
import testing.DrawResult

/**
 * CubicPaths GM test - Cubic path drawing
 * This test draws various cubic bezier curves to test path rendering
 * 
 * Note: This is a simplified implementation since Kanvas doesn't have full
 * cubic path support yet. We'll implement basic cubic curves.
 */
class CubicPathsGM : GM() {
    override fun getName(): String = "cubicpaths"
    override fun getSize(): Size = Size(512f, 512f)

    override fun onDraw(canvas: Canvas): DrawResult {
        // Draw a simple cubic curve
        val cubicPath = Path().apply {
            moveTo(50f, 200f)
            cubicTo(100f, 50f, 200f, 300f, 250f, 200f)
        }
        
        val cubicPaint = Paint().apply {
            color = Color.RED
            style = PaintStyle.STROKE
            strokeWidth = 3f
        }
        canvas.drawPath(cubicPath, cubicPaint)
        
        // Draw a more complex cubic path
        val complexPath = Path().apply {
            moveTo(50f, 300f)
            cubicTo(150f, 200f, 250f, 400f, 350f, 300f)
            cubicTo(400f, 250f, 450f, 350f, 450f, 300f)
        }
        
        val complexPaint = Paint().apply {
            color = Color.BLUE
            style = PaintStyle.STROKE
            strokeWidth = 3f
        }
        canvas.drawPath(complexPath, complexPaint)
        
        // Draw some text
        val textPaint = Paint().apply {
            color = Color.BLACK
            style = PaintStyle.FILL
        }
        canvas.drawText("Cubic Paths Test", 50f, 400f, textPaint)
        
        return DrawResult.OK
    }
}