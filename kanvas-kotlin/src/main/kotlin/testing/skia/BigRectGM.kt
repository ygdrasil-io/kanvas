package testing.skia

import com.kanvas.core.*
import testing.GM
import testing.DrawResult
import testing.Size

/**
 * Port of Skia's bigrect.cpp test
 * Tests drawing of large rectangles and performance.
 * 
 * This test creates large rectangles to test rendering performance
 * and verify that Kanvas can handle large drawing operations correctly.
 */
class BigRectGM : GM() {
    override fun getName(): String = "bigrect"
    
    override fun getSize(): Size = Size(256f, 256f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        val size = getSize()
        
        // Set background
        val bgPaint = Paint().apply {
            color = Color(0xFF, 0xFF, 0xFF, 255) // White background
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(0f, 0f, size.width, size.height), bgPaint)
        
        // Draw large rectangles with different colors and styles
        drawLargeRectangles(canvas)
        
        // Add descriptive labels
        addLabels(canvas)
        
        return DrawResult.OK
    }
    
    private fun drawLargeRectangles(canvas: Canvas) {
        // Test 1: Large filled rectangle
        val fillPaint = Paint().apply {
            color = Color(0x88, 0xAA, 0xFF, 200) // Semi-transparent blue
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(20f, 20f, 236f, 128f), fillPaint)
        
        // Test 2: Large stroked rectangle
        val strokePaint = Paint().apply {
            color = Color(0xFF, 0, 0, 255) // Red
            strokeWidth = 4f
            style = PaintStyle.STROKE
        }
        canvas.drawRect(Rect(30f, 40f, 226f, 118f), strokePaint)
        
        // Test 3: Multiple overlapping rectangles
        val colors = listOf(
            Color(0xFF, 0x88, 0, 180),
            Color(0, 0xFF, 0x88, 180),
            Color(0x88, 0, 0xFF, 180)
        )
        
        for (i in colors.indices) {
            val paint = Paint().apply {
                color = colors[i]
                style = PaintStyle.FILL
            }
            val x = 40f + i * 20f
            val y = 150f + i * 10f
            canvas.drawRect(Rect(x, y, x + 180f, y + 60f), paint)
        }
        
        // Test 4: Rectangle with different blend modes (placeholder for when implemented)
        val blendPaint = Paint().apply {
            color = Color(0xFF, 0xFF, 0, 150) // Semi-transparent yellow
            style = PaintStyle.FILL
            // blendMode = BlendMode.SRC_OVER // Will be implemented later
        }
        canvas.drawRect(Rect(60f, 60f, 180f, 180f), blendPaint)
    }
    
    private fun addLabels(canvas: Canvas) {
        val titlePaint = Paint().apply {
            color = Color(0, 0, 0, 255)
            textSize = 16f
            style = PaintStyle.FILL
        }
        canvas.drawText("Large Rectangle Test", 20f, 18f, titlePaint)
        
        val infoPaint = Paint().apply {
            color = Color(0x88, 0, 0, 255)
            textSize = 12f
            style = PaintStyle.FILL
        }
        canvas.drawText("Testing large rectangle drawing and performance", 20f, 240f, infoPaint)
    }
}