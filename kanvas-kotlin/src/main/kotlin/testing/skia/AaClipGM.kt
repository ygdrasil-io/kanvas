package testing.skia

import com.kanvas.core.*
import testing.GM
import testing.DrawResult
import testing.Size

/**
 * Port of Skia's aaclip.cpp test
 * Tests anti-aliased clipping with various rectangle configurations.
 * 
 * This test draws a series of rectangles with different clipping scenarios
 * to verify that anti-aliased clipping works correctly. Each test case
 * should show a blue center surrounded by a 2px green border with no red visible.
 */
class AaClipGM : GM() {
    override fun getName(): String = "aaclip"
    
    override fun getSize(): Size = Size(240f, 120f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        // Initial pixel-boundary-aligned draw
        drawRectTests(canvas)
        
        // Repeat 4x with .2, .4, .6, .8 px offsets
        // These offsets test sub-pixel positioning
        canvas.translate(1.0f/5, 1.0f/5)
        canvas.translate(50f, 0f)
        drawRectTests(canvas)
        
        canvas.translate(1.0f/5, 1.0f/5)
        canvas.translate(50f, 0f)
        drawRectTests(canvas)
        
        canvas.translate(1.0f/5, 1.0f/5)
        canvas.translate(50f, 0f)
        drawRectTests(canvas)
        
        canvas.translate(1.0f/5, 1.0f/5)
        canvas.translate(50f, 0f)
        drawRectTests(canvas)
        
        return DrawResult.OK
    }
    
    /**
     * Draw the three test cases: square, column, and bar
     */
    private fun drawRectTests(canvas: Canvas) {
        drawSquare(canvas, 10, 10)
        drawColumn(canvas, 30, 10)
        drawBar(canvas, 10, 30)
    }
    
    private fun drawSquare(canvas: Canvas, x: Int, y: Int) {
        val target = Rect(0f, 0f, 10f, 10f)
        draw(canvas, target, x, y)
    }
    
    private fun drawColumn(canvas: Canvas, x: Int, y: Int) {
        // Test a tall, thin rectangle (1px wide, 10px tall)
        val target = Rect(0f, 0f, 1f, 10f)
        draw(canvas, target, x, y)
    }
    
    private fun drawBar(canvas: Canvas, x: Int, y: Int) {
        // Test a short, wide rectangle (10px wide, 1px tall)
        val target = Rect(0f, 0f, 10f, 1f)
        draw(canvas, target, x, y)
    }
    
    /**
     * Draw a 2px border around the target, then red behind the target;
     * set the clip to match the target, then draw the target in blue.
     * 
     * Expected result: blue center surrounded by green border, no red visible.
     * The red should be completely clipped by the target rectangle.
     */
    private fun draw(canvas: Canvas, target: Rect, x: Int, y: Int) {
        val borderPaint = Paint().apply {
            color = Color(0, 0xDD, 0, 255) // Green border
            isAntiAlias = true
            style = PaintStyle.FILL
        }
        
        val backgroundPaint = Paint().apply {
            color = Color(0xDD, 0, 0, 255) // Red background (should be clipped)
            isAntiAlias = true
            style = PaintStyle.FILL
        }
        
        val foregroundPaint = Paint().apply {
            color = Color(0, 0, 0xDD, 255) // Blue foreground (should be visible)
            isAntiAlias = true
            style = PaintStyle.FILL
        }
        
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        
        // Draw green border (2px inset from target)
        val borderRect = target.copy().apply {
            inset(-2f, -2f)
        }
        canvas.drawRect(borderRect, borderPaint)
        
        // Draw red background (should be mostly clipped)
        canvas.drawRect(target, backgroundPaint)
        
        // Set clip to match the target rectangle
        canvas.clipRect(target)
        
        // Draw blue foreground (4px inset from target) - this should be visible
        val foregroundRect = target.copy().apply {
            inset(-4f, -4f)
        }
        canvas.drawRect(foregroundRect, foregroundPaint)
        
        canvas.restore()
    }
}