package testing.skia

import com.kanvas.core.*
import testing.GM
import testing.DrawResult

/**
 * Dashing GM test - Dashed line drawing
 * This test draws various dashed lines to test stroke dashing
 * 
 * Note: This is a simplified implementation since Kanvas doesn't have full
 * dashing support yet. We'll implement basic dashed line simulation.
 */
class DashingGM : GM() {
    override fun getName(): String = "dashing"
    override fun getSize(): Size = Size(512f, 512f)

    override fun onDraw(canvas: Canvas): DrawResult {
        // For now, simulate dashed lines by drawing short line segments
        
        // Draw horizontal dashed line
        val dashPaint = Paint().apply {
            color = Color.RED
            style = PaintStyle.STROKE
            strokeWidth = 3f
        }
        
        for (i in 0..20) {
            val x1 = 50f + i * 15f
            val x2 = x1 + 10f
            canvas.drawLine(x1, 100f, x2, 100f, dashPaint)
        }
        
        // Draw vertical dashed line
        for (i in 0..20) {
            val y1 = 150f + i * 15f
            val y2 = y1 + 10f
            canvas.drawLine(100f, y1, 100f, y2, dashPaint)
        }
        
        // Draw diagonal dashed line
        for (i in 0..15) {
            val x1 = 200f + i * 15f
            val y1 = 200f + i * 15f
            val x2 = x1 + 10f
            val y2 = y1 + 10f
            canvas.drawLine(x1, y1, x2, y2, dashPaint)
        }
        
        // Draw some text
        val textPaint = Paint().apply {
            color = Color.BLACK
            style = PaintStyle.FILL
        }
        canvas.drawText("Dashing Test", 50f, 350f, textPaint)
        
        return DrawResult.OK
    }
}