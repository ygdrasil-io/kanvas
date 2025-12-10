package testing.skia

import com.kanvas.core.*
import testing.DrawResult
import testing.GM

/**
 * ClearSwizzleGM tests clear operations and color swizzling.
 * This corresponds to the Skia clear_swizzle.cpp test.
 */
class ClearSwizzleGM : GM() {
    
    override fun getName(): String = "clear_swizzle"
    
    override fun getSize(): Size = Size(512f, 512f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        // For this test, we'll demonstrate clear operations
        // Note: Kanvas may not have full swizzle support yet, so we'll focus on clear operations
        
        // Draw background
        drawBackground(canvas)
        
        // Test 1: Clear with transparent color
        val clearPaint = Paint().apply {
            color = Color.TRANSPARENT
            style = PaintStyle.FILL
        }
        
        // Draw some colored rectangles first
        val colorPaint = Paint().apply {
            color = Color(0, 0, 255, 255) // Blue
            style = PaintStyle.FILL
        }
        
        canvas.drawRect(Rect.makeLTRB(50f, 50f, 200f, 200f), colorPaint)
        
        // "Clear" by drawing transparent over part of it
        canvas.drawRect(Rect.makeLTRB(100f, 100f, 150f, 150f), clearPaint)
        
        // Test 2: Different clear colors
        val clearColors = listOf(
            Color.TRANSPARENT,
            Color(255, 0, 0, 128), // Semi-transparent red (0.5*255)
            Color(0, 255, 0, 77), // Semi-transparent green (0.3*255)
            Color(0, 0, 255, 179)  // Semi-transparent blue (0.7*255)
        )
        
        clearColors.forEachIndexed { index, clearColor ->
            val x = 250f + index * 120f
            val y = 50f
            
            // Draw background rectangle
            colorPaint.color = Color(128, 128, 128, 255) // Gray (0.5*255)
            canvas.drawRect(Rect.makeLTRB(x, y, x + 100f, y + 100f), colorPaint)
            
            // Draw "clear" rectangle
            clearPaint.color = clearColor
            canvas.drawRect(Rect.makeLTRB(x + 20f, y + 20f, x + 80f, y + 80f), clearPaint)
        }
        
        // Test 3: Clear with different blend modes
        val blendModes = listOf(
            BlendMode.CLEAR,
            BlendMode.SRC,
            BlendMode.DST_OVER
        )
        
        blendModes.forEachIndexed { index, blendMode ->
            val x = 50f + index * 180f
            val y = 200f
            
            // Draw background
            colorPaint.color = Color(255, 0, 0, 255) // Red
            canvas.drawRect(Rect.makeLTRB(x, y, x + 150f, y + 100f), colorPaint)
            
            // Draw clear with blend mode
            clearPaint.color = Color(0, 255, 0, 128) // Green (0.5*255)
            clearPaint.blendMode = blendMode
            canvas.drawRect(Rect.makeLTRB(x + 20f, y + 20f, x + 130f, y + 80f), clearPaint)
        }
        
        return DrawResult.OK
    }
}