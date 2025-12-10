package testing.skia

import com.kanvas.core.*
import testing.GM
import testing.DrawResult
import com.kanvas.core.Point

/**
 * FillRectGradient GM test - Gradient-filled rectangles
 * This test draws rectangles filled with various gradients
 * 
 * Note: This is a simplified implementation since Kanvas doesn't have full
 * gradient rectangle support yet. We'll implement basic gradient-filled rectangles.
 */
class FillRectGradientGM : GM() {
    override fun getName(): String = "fillrect_gradient"
    override fun getSize(): Size = Size(512f, 512f)

    override fun onDraw(canvas: Canvas): DrawResult {
        // Draw rectangles with linear gradients
        val linearColors = listOf(Color.RED, Color.BLUE)
        val linearShader = Shaders.makeLinearGradient(
            linearColors,
            null,
            Point(50f, 50f),
            Point(150f, 150f)
        )
        
        canvas.setShader(linearShader)
        val linearPaint = Paint().apply {
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(50f, 50f, 150f, 150f), linearPaint)
        
        // Draw rectangles with radial gradients
        val radialColors = listOf(Color.GREEN, Color.YELLOW)
        val radialShader = Shaders.makeRadialGradient(
            radialColors,
            null,
            Point(300f, 100f),
            50f
        )
        
        canvas.setShader(radialShader)
        val radialPaint = Paint().apply {
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(250f, 50f, 350f, 150f), radialPaint)
        
        // Draw some text
        val textPaint = Paint().apply {
            color = Color.BLACK
            style = PaintStyle.FILL
        }
        canvas.drawText("Gradient Filled Rectangles Test", 50f, 250f, textPaint)
        
        return DrawResult.OK
    }
}