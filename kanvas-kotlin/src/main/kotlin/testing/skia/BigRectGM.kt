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
 * Port of Skia's bigrect.cpp test
 * Tests drawing of large rectangles with various sizes and paint configurations.
 * 
 * This test systematically tests different combinations of paint styles, stroke widths,
 * and anti-aliasing settings with rectangles of varying sizes, including very large sizes
 * that test the limits of the rendering system.
 */
class BigRectGM : GM() {
    override fun getName(): String = "bigrect"
    
    override fun getSize(): Size = Size(325f, 125f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        // Test with sizes:
        //   - reasonable size (for comparison),
        //   - outside the range of int32, and
        //   - outside the range of SkFixed.
        val sizes = listOf(100f, 5e10f, 1e6f)

        for (i in 0 until 8) {
            for (j in 0 until 3) {
                canvas.save()
                // Replicate exact translation from C++: SkIntToScalar(i*40+5)
                canvas.translate((i * 40 + 5).toFloat(), (j * 40 + 5).toFloat())

                val paint = Paint().apply {
                    color = Color(0, 0, 0xFF, 255) // Blue - exactly SK_ColorBLUE
                    // These are the three parameters that affect the behavior of drawing
                    // Replicate exact bitwise logic from C++
                    if (i and 1 != 0) {
                        style = PaintStyle.FILL // SkPaint::kFill_Style
                    } else {
                        style = PaintStyle.STROKE // SkPaint::kStroke_Style
                    }
                    if (i and 2 != 0) {
                        strokeWidth = 1f // paint.setStrokeWidth(1)
                    } else {
                        strokeWidth = 0f // paint.setStrokeWidth(0)
                    }
                    if (i and 4 != 0) {
                        isAntiAlias = true // paint.setAntiAlias(true)
                    } else {
                        isAntiAlias = false // paint.setAntiAlias(false)
                    }
                }

                drawBigRect(canvas, sizes[j], paint)
                canvas.restore()
            }
        }
        
        return DrawResult.OK
    }
    
    private fun drawBigRect(canvas: Canvas, big: Float, rectPaint: Paint) {
        // Looks like this:
        // +--+-+----+-+----+
        // |  | |    | |    |
        // |--+-+----+-+----+
        // |--+-+----+-+----+
        // |  | |    | |    |
        // |  | |    +-+    |
        // +--+-+--+     +--+
        // +--+-+--+     +--+
        // |  | |    +-+    |
        // |  | |    | |    |
        // +--+-+----+-+----+

        // Exact replication of C++ clipRect: {0, 0, 35, 35}
        canvas.clipRect(Rect(0f, 0f, 35f, 35f))

        // Align to pixel boundaries - exact replication of C++ translate(0.5, 0.5)
        canvas.translate(0.5f, 0.5f)

        // Replicate exact SkRect::MakeLTRB calls from C++
        val horiz = Rect(-big, 5f, big, 10f) // SkRect::MakeLTRB(-big, 5, big, 10)
        canvas.drawRect(horiz, rectPaint)

        val vert = Rect(5f, -big, 10f, big) // SkRect::MakeLTRB(5, -big, 10, big)
        canvas.drawRect(vert, rectPaint)

        val fromLeft = Rect(-big, 20f, 17f, 25f) // SkRect::MakeLTRB(-big, 20, 17, 25)
        canvas.drawRect(fromLeft, rectPaint)

        val fromTop = Rect(20f, -big, 25f, 17f) // SkRect::MakeLTRB(20, -big, 25, 17)
        canvas.drawRect(fromTop, rectPaint)

        val fromRight = Rect(28f, 20f, big, 25f) // SkRect::MakeLTRB(28, 20, big, 25)
        canvas.drawRect(fromRight, rectPaint)

        val fromBottom = Rect(20f, 28f, 25f, big) // SkRect::MakeLTRB(20, 28, 25, big)
        canvas.drawRect(fromBottom, rectPaint)

        val leftBorder = Rect(-2f, -1f, 0f, 35f) // SkRect::MakeLTRB(-2, -1, 0, 35)
        canvas.drawRect(leftBorder, rectPaint)

        val topBorder = Rect(-1f, -2f, 35f, 0f) // SkRect::MakeLTRB(-1, -2, 35, 0)
        canvas.drawRect(topBorder, rectPaint)

        val rightBorder = Rect(34f, -1f, 36f, 35f) // SkRect::MakeLTRB(34, -1, 36, 35)
        canvas.drawRect(rightBorder, rectPaint)

        val bottomBorder = Rect(-1f, 34f, 35f, 36f) // SkRect::MakeLTRB(-1, 34, 35, 36)
        canvas.drawRect(bottomBorder, rectPaint)

        // Replicate exact outOfBoundsPaint from C++
        val outOfBoundsPaint = Paint().apply {
            color = Color(0xFF, 0, 0, 255) // SK_ColorRED
            style = PaintStyle.STROKE // SkPaint::kStroke_Style
            strokeWidth = 0f // setStrokeWidth(0)
        }

        val outOfBounds = Rect(-1f, -1f, 35f, 35f) // SkRect::MakeLTRB(-1, -1, 35, 35)
        canvas.drawRect(outOfBounds, outOfBoundsPaint)
    }
}