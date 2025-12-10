package testing.skia

import com.kanvas.core.Canvas
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Rect
import com.kanvas.core.SK_Scalar1
import com.kanvas.core.Size
import com.kanvas.core.SkClipOp
import com.kanvas.core.SkColorSetRGB
import com.kanvas.core.SkIntToScalar
import testing.DrawResult
import testing.GM

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
        // Using SK_Scalar1 / 5 for precision like C++ version
        canvas.translate(SK_Scalar1 / 5, SK_Scalar1 / 5)
        canvas.translate(SkIntToScalar(50), 0f)
        drawRectTests(canvas)
        
        canvas.translate(SK_Scalar1 / 5, SK_Scalar1 / 5)
        canvas.translate(SkIntToScalar(50), 0f)
        drawRectTests(canvas)
        
        canvas.translate(SK_Scalar1 / 5, SK_Scalar1 / 5)
        canvas.translate(SkIntToScalar(50), 0f)
        drawRectTests(canvas)
        
        canvas.translate(SK_Scalar1 / 5, SK_Scalar1 / 5)
        canvas.translate(SkIntToScalar(50), 0f)
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
        // SkRect::MakeWH(10 * SK_Scalar1, 10 * SK_Scalar1)
        draw(canvas, Rect.makeWH(10f * SK_Scalar1, 10f * SK_Scalar1), x, y)
    }
    
    private fun drawColumn(canvas: Canvas, x: Int, y: Int) {
        // Test a tall, thin rectangle (1px wide, 10px tall)
        // SkRect::MakeWH(1 * SK_Scalar1, 10 * SK_Scalar1)
        draw(canvas, Rect.makeWH(1f * SK_Scalar1, 10f * SK_Scalar1), x, y)
    }
    
    private fun drawBar(canvas: Canvas, x: Int, y: Int) {
        // Test a short, wide rectangle (10px wide, 1px tall)
        // SkRect::MakeWH(10 * SK_Scalar1, 1 * SK_Scalar1)
        draw(canvas, Rect.makeWH(10f * SK_Scalar1, 1f * SK_Scalar1), x, y)
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
            color = SkColorSetRGB(0x0, 0xDD, 0x0) // Green border - SkColorSetRGB(0x0, 0xDD, 0x0)
            isAntiAlias = true
            style = PaintStyle.FILL
        }
        
        val backgroundPaint = Paint().apply {
            color = SkColorSetRGB(0xDD, 0x0, 0x0) // Red background - SkColorSetRGB(0xDD, 0x0, 0x0)
            isAntiAlias = true
            style = PaintStyle.FILL
        }
        
        val foregroundPaint = Paint().apply {
            color = SkColorSetRGB(0x0, 0x0, 0xDD) // Blue foreground - SkColorSetRGB(0x0, 0x0, 0xDD)
            isAntiAlias = true
            style = PaintStyle.FILL
        }
        
        canvas.save()
        canvas.translate(SkIntToScalar(x), SkIntToScalar(y)) // SkIntToScalar conversion
        
        // Draw green border (2px inset from target) - EXACT C++ logic
        target.inset(SkIntToScalar(-2), SkIntToScalar(-2)) // SkIntToScalar(-2)
        canvas.drawRect(target, borderPaint)
        target.inset(SkIntToScalar(2), SkIntToScalar(2)) // Reset target like C++
        
        // Draw red background (should be mostly clipped)
        canvas.drawRect(target, backgroundPaint)
        
        // Set clip to match the target rectangle with anti-aliasing
        // Use the Skia-aligned method: clipRect(rect, SkClipOp.INTERSECT, doAA)
        canvas.clipRect(target, SkClipOp.INTERSECT, true)
        
        // Draw blue foreground (4px inset from target) - EXACT C++ logic
        target.inset(SkIntToScalar(-4), SkIntToScalar(-4)) // SkIntToScalar(-4)
        canvas.drawRect(target, foregroundPaint)
        
        canvas.restore()
    }
}