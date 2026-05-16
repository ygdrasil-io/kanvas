package testing.skia

import com.kanvas.core.Canvas
import com.kanvas.core.Color
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Rect
import com.kanvas.core.Size
import testing.DrawResult
import testing.GM

/**
 * BitmapRectGM tests bitmap rectangle drawing operations.
 * This corresponds to the Skia bitmaprect.cpp test.
 * Simplified version since Kanvas doesn't have full bitmap/shader support yet.
 */
class BitmapRectGM : GM() {
    
    override fun getName(): String = "bitmaprect"
    
    override fun getSize(): Size = Size(512f, 512f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        // Draw background
        drawBackground(canvas)
        
        // Since Kanvas doesn't have full Bitmap/Shader support yet,
        // we'll simulate bitmap-like drawing with patterns
        
        // Test 1: Simple rectangle with pattern (simulating bitmap)
        val patternPaint = Paint().apply {
            color = Color(51, 102, 204, 255) // Blue pattern (0.2*255, 0.4*255, 0.8*255)
            style = PaintStyle.FILL
            isAntiAlias = true
        }
        
        canvas.drawRect(Rect.makeLTRB(50f, 50f, 200f, 200f), patternPaint)
        
        // Test 2: "Scaled" rectangle (simulating scaled bitmap)
        canvas.save()
        canvas.translate(250f, 50f)
        canvas.scale(2f, 2f)
        patternPaint.color = Color(102, 153, 255, 255) // Lighter blue (0.4*255, 0.6*255, 1*255)
        canvas.drawRect(Rect.makeLTRB(0f, 0f, 75f, 75f), patternPaint)
        canvas.restore()
        
        // Test 3: "Rotated" rectangle (simulating rotated bitmap)
        canvas.save()
        canvas.translate(400f, 200f)
        canvas.rotate(45f, 1f, 1f)
        patternPaint.color = Color(26, 77, 179, 255) // Darker blue (0.1*255, 0.3*255, 0.7*255)
        canvas.drawRect(Rect.makeLTRB(-50f, -50f, 50f, 50f), patternPaint)
        canvas.restore()
        
        // Test 4: Different "blend modes" with rectangles
        val blendModes = listOf(
            com.kanvas.core.BlendMode.SRC_OVER,
            com.kanvas.core.BlendMode.MULTIPLY,
            com.kanvas.core.BlendMode.SCREEN,
            com.kanvas.core.BlendMode.OVERLAY
        )
        
        blendModes.forEachIndexed { index, blendMode ->
            val x = 50f + index * 120f
            val y = 250f
            
            // Draw background for blend mode test
            val bgPaint = Paint().apply {
                color = Color(128, 128, 128, 255) // Gray background (0.5*255)
                style = PaintStyle.FILL
            }
            canvas.drawRect(Rect.makeLTRB(x, y, x + 100f, y + 100f), bgPaint)
            
            // Draw rectangle with blend mode
            val blendPaint = Paint().apply {
                color = Color(0, 0, 255, 179) // Semi-transparent blue (0.7*255)
                this.blendMode = blendMode
                isAntiAlias = true
            }
            canvas.drawRect(Rect.makeLTRB(x + 10f, y + 10f, x + 90f, y + 90f), blendPaint)
        }
        
        return DrawResult.OK
    }
}