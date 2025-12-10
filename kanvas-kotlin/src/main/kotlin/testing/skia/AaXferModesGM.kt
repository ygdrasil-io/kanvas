package testing.skia

import com.kanvas.core.*
import testing.DrawResult
import testing.GM

/**
 * AaXferModesGM tests anti-aliased transfer modes (blend modes) for drawing operations.
 * This corresponds to the Skia aaxfermodes.cpp test.
 */
class AaXferModesGM : GM() {
    
    override fun getName(): String = "aaxfermodes"
    
    override fun getSize(): Size = Size(640f, 480f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        // Draw background
        drawBackground(canvas)
        
        // Set up initial transform for the test
        canvas.translate(10f, 10f)
        
        // Test various blend modes with anti-aliased drawing
        val blendModes = listOf(
            BlendMode.CLEAR,
            BlendMode.SRC,
            BlendMode.DST,
            BlendMode.SRC_OVER,
            BlendMode.DST_OVER,
            BlendMode.SRC_IN,
            BlendMode.DST_IN,
            BlendMode.SRC_OUT,
            BlendMode.DST_OUT,
            BlendMode.SRC_ATOP,
            BlendMode.DST_ATOP,
            BlendMode.XOR,
            BlendMode.PLUS,
            BlendMode.MODULATE,
            BlendMode.SCREEN
        )
        
        val rectWidth = 100f
        val rectHeight = 80f
        val spacing = 120f
        
        // Create a red rectangle as the destination
        val dstPaint = Paint().apply {
            color = Color.RED
            style = PaintStyle.FILL
        }
        
        // Create a blue rectangle with anti-aliasing as the source
        val srcPaint = Paint().apply {
            color = Color.BLUE
            style = PaintStyle.FILL
            isAntiAlias = true
        }
        
        // Draw destination rectangles
        blendModes.forEachIndexed { index, _ ->
            val x = (index % 4) * spacing
            val y = (index / 4) * (rectHeight + 20f)
            canvas.drawRect(Rect(x, y, x + rectWidth, y + rectHeight), dstPaint)
        }
        
        // Draw source rectangles with different blend modes
        blendModes.forEachIndexed { index, blendMode ->
            val x = (index % 4) * spacing + 20f
            val y = (index / 4) * (rectHeight + 20f) + 10f
            
            srcPaint.blendMode = blendMode
            canvas.drawRect(Rect(x, y, x + rectWidth - 20f, y + rectHeight - 20f), srcPaint)
            
            // Draw blend mode name
            val textPaint = Paint().apply {
                color = Color.BLACK
                textSize = 12f
                isAntiAlias = true
            }
            canvas.drawText(blendMode.toString(), x, y - 5f, textPaint)
        }
        
        return DrawResult.OK
    }
}

// Helper extension to get blend mode name
private fun BlendMode.toString(): String {
    return when (this) {
        BlendMode.CLEAR -> "CLEAR"
        BlendMode.SRC -> "SRC"
        BlendMode.DST -> "DST"
        BlendMode.SRC_OVER -> "SRC_OVER"
        BlendMode.DST_OVER -> "DST_OVER"
        BlendMode.SRC_IN -> "SRC_IN"
        BlendMode.DST_IN -> "DST_IN"
        BlendMode.SRC_OUT -> "SRC_OUT"
        BlendMode.DST_OUT -> "DST_OUT"
        BlendMode.SRC_ATOP -> "SRC_ATOP"
        BlendMode.DST_ATOP -> "DST_ATOP"
        BlendMode.XOR -> "XOR"
        BlendMode.PLUS -> "PLUS"
        BlendMode.MODULATE -> "MODULATE"
        BlendMode.SCREEN -> "SCREEN"
        else -> "UNKNOWN"
    }
}