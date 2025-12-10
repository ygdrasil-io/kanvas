package testing.skia

import com.kanvas.core.*
import testing.GM
import testing.DrawResult

/**
 * Colorspace GM test - Color space handling
 * This test checks different color space conversions and handling
 * 
 * Note: This is a simplified implementation since Kanvas doesn't have full
 * color space support yet. We'll implement basic color space awareness.
 */
class ColorspaceGM : GM() {
    override fun getName(): String = "colorspace"
    override fun getSize(): Size = Size(512f, 512f)

    override fun onDraw(canvas: Canvas): DrawResult {
        // For now, implement a basic test that draws shapes with different
        // color representations to test color handling
        
        // Draw rectangles with different color intensities
        val colors = listOf(
            Color(255, 0, 0, 255),     // Red
            Color(0, 255, 0, 255),     // Green
            Color(0, 0, 255, 255),     // Blue
            Color(255, 255, 0, 255),   // Yellow
            Color(255, 0, 255, 255),   // Magenta
            Color(0, 255, 255, 255)    // Cyan
        )
        
        colors.forEachIndexed { index, color ->
            val paint = Paint().apply {
                this.color = color
                style = PaintStyle.FILL
            }
            val x = 50f + index * 70f
            canvas.drawRect(Rect(x, 50f, x + 60f, 150f), paint)
        }
        
        // Draw some grayscale rectangles
        val grays = listOf(0, 64, 128, 192, 255)
        grays.forEachIndexed { index, gray ->
            val paint = Paint().apply {
                color = Color(gray, gray, gray, 255)
                style = PaintStyle.FILL
            }
            val x = 50f + index * 70f
            canvas.drawRect(Rect(x, 200f, x + 60f, 300f), paint)
        }
        
        // Draw some text
        val textPaint = Paint().apply {
            color = Color.BLACK
            style = PaintStyle.FILL
        }
        canvas.drawText("Colorspace Test", 50f, 350f, textPaint)
        
        return DrawResult.OK
    }
}