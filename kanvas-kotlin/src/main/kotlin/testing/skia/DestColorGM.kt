package testing.skia

import com.kanvas.core.*
import testing.GM
import testing.DrawResult

/**
 * DestColor GM test - Destination color operations
 * This test checks destination color blending and operations
 * 
 * Note: This is a simplified implementation since Kanvas doesn't have full
 * destination color support yet. We'll implement basic destination-aware drawing.
 */
class DestColorGM : GM() {
    override fun getName(): String = "destcolor"
    override fun getSize(): Size = Size(512f, 512f)

    override fun onDraw(canvas: Canvas): DrawResult {
        // Draw a background
        val bgPaint = Paint().apply {
            color = Color(200, 200, 200, 255) // Light gray
            style = PaintStyle.FILL
        }
        val size = getSize()
        canvas.drawRect(Rect(0f, 0f, size.width, size.height), bgPaint)
        
        // Draw some colored rectangles to test destination blending
        val colors = listOf(
            Color(255, 0, 0, 128),     // Semi-transparent red
            Color(0, 255, 0, 128),     // Semi-transparent green
            Color(0, 0, 255, 128)      // Semi-transparent blue
        )
        
        colors.forEachIndexed { index, color ->
            val paint = Paint().apply {
                this.color = color
                style = PaintStyle.FILL
            }
            val x = 50f + index * 100f
            canvas.drawRect(Rect(x, 50f, x + 80f, 150f), paint)
        }
        
        // Draw some overlapping shapes
        val redPaint = Paint().apply {
            color = Color(255, 0, 0, 128)
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(50f, 200f, 150f, 300f), redPaint)
        
        val bluePaint = Paint().apply {
            color = Color(0, 0, 255, 128)
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(100f, 250f, 200f, 350f), bluePaint)
        
        // Draw some text
        val textPaint = Paint().apply {
            color = Color.BLACK
            style = PaintStyle.FILL
        }
        canvas.drawText("Destination Color Test", 50f, 400f, textPaint)
        
        return DrawResult.OK
    }
}