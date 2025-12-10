package testing.skia

import com.kanvas.core.*
import testing.DrawResult
import testing.GM

/**
 * CircleSizesGM tests circle drawing with different sizes and anti-aliasing.
 * This corresponds to the Skia circle_sizes.cpp test.
 */
class CircleSizesGM : GM() {
    
    override fun getName(): String = "circle_sizes"
    
    override fun getSize(): Size = Size(512f, 512f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        // Draw background
        drawBackground(canvas)
        
        // Set up paint for circles
        val circlePaint = Paint().apply {
            color = Color(0, 0, 255, 255) // Blue
            style = PaintStyle.FILL
            isAntiAlias = true
        }
        
        val strokePaint = Paint().apply {
            color = Color(255, 0, 0, 255) // Red
            style = PaintStyle.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        
        // Test circles of different sizes
        val sizes = listOf(4f, 8f, 16f, 32f, 64f, 128f)
        val spacing = 150f
        
        sizes.forEachIndexed { index, size ->
            val x = 50f + (index % 3) * spacing
            val y = 50f + (index / 3) * spacing
            
            // Draw filled circle
            canvas.drawCircle(x, y, size / 2, circlePaint)
            
            // Draw circle outline
            canvas.drawCircle(x, y, size / 2, strokePaint)
        }
        
        // Test circles with different colors
        val colors = listOf(
            Color(255, 0, 0, 255), // Red
            Color(0, 255, 0, 255), // Green
            Color(0, 0, 255, 255), // Blue
            Color(255, 255, 0, 255), // Yellow
            Color(0, 255, 255, 255), // Cyan
            Color(255, 0, 255, 255)  // Magenta
        )
        
        colors.forEachIndexed { index, color ->
            val x = 50f + (index % 3) * spacing
            val y = 250f + (index / 3) * spacing
            
            circlePaint.color = color
            canvas.drawCircle(x, y, 40f, circlePaint)
        }
        
        return DrawResult.OK
    }
}