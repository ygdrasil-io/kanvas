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
 * Tests drawing big rectangles with clipping
 */
class BigRectGM : GM() {
    override fun getName(): String = "bigrect"

    override fun getSize(): Size = Size(325f, 125f)

    override fun onDraw(canvas: Canvas): DrawResult {
        return try {
            // Test with sizes:
            //   - reasonable size (for comparison),
            //   - outside the range of int32, and
            //   - outside the range of SkFixed.
            val sizes = arrayOf(100f, 5e10f, 1e6f)

            for (i in 0 until 8) {
                for (j in 0 until 3) {
                    canvas.save()
                    canvas.translate((i * 40 + 5).toFloat(), (j * 40 + 5).toFloat())

                    val paint = Paint().apply {
                        color = Color(0, 0, 255, 255) // SK_ColorBLUE
                        // These are the three parameters that affect the behavior of skcpu::Draw::drawRect.
                        when {
                            i and 1 != 0 -> style = PaintStyle.FILL
                            else -> style = PaintStyle.STROKE
                        }
                        when {
                            i and 2 != 0 -> strokeWidth = 1f
                            else -> strokeWidth = 0f
                        }
                        when {
                            i and 4 != 0 -> isAntiAlias = true
                            else -> isAntiAlias = false
                        }
                    }

                    drawBigRect(canvas, sizes[j], paint)
                    canvas.restore()
                }
            }

            DrawResult.OK
        } catch (e: Exception) {
            println("Error in BigRectGM: ${e.message}")
            DrawResult.FAIL
        }
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

        canvas.clipRect(Rect(0f, 0f, 35f, 35f))

        // Align to pixel boundaries.
        canvas.translate(0.5f, 0.5f)

        val horiz = Rect(-big, 5f, big, 10f)
        canvas.drawRect(horiz, rectPaint)

        val vert = Rect(5f, -big, 10f, big)
        canvas.drawRect(vert, rectPaint)

        val fromLeft = Rect(-big, 20f, 17f, 25f)
        canvas.drawRect(fromLeft, rectPaint)

        val fromTop = Rect(20f, -big, 25f, 17f)
        canvas.drawRect(fromTop, rectPaint)

        val fromRight = Rect(28f, 20f, big, 25f)
        canvas.drawRect(fromRight, rectPaint)

        val fromBottom = Rect(20f, 28f, 25f, big)
        canvas.drawRect(fromBottom, rectPaint)

        val leftBorder = Rect(-2f, -1f, 0f, 35f)
        canvas.drawRect(leftBorder, rectPaint)

        val topBorder = Rect(-1f, -2f, 35f, 0f)
        canvas.drawRect(topBorder, rectPaint)

        val rightBorder = Rect(34f, -1f, 36f, 35f)
        canvas.drawRect(rightBorder, rectPaint)

        val bottomBorder = Rect(-1f, 34f, 35f, 36f)
        canvas.drawRect(bottomBorder, rectPaint)

        val outOfBoundsPaint = Paint().apply {
            color = Color(255, 0, 0, 255) // SK_ColorRED
            style = PaintStyle.STROKE
            strokeWidth = 0f
        }

        val outOfBounds = Rect(-1f, -1f, 35f, 35f)
        canvas.drawRect(outOfBounds, outOfBoundsPaint)
    }
}