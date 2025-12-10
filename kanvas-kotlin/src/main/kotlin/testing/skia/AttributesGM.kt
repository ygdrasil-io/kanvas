package testing.skia

import com.kanvas.core.Canvas
import com.kanvas.core.Color
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Path
import com.kanvas.core.Rect
import com.kanvas.core.Size
import com.kanvas.core.StrokeCap
import com.kanvas.core.StrokeJoin
import testing.DrawResult
import testing.GM

/**
 * Port of Skia's attributes.cpp test
 * Tests path attributes and drawing properties
 * Simplified version focusing on core functionality available in Kanvas
 * Note: Original test is GPU-specific with custom vertex attributes, this version tests
 * path drawing attributes like stroke width, join styles, and cap styles
 */
class AttributesGM : GM() {
    override fun getName(): String = "attributes"
    override fun getSize(): Size = Size(400f, 600f)

    override fun onDraw(canvas: Canvas): DrawResult {
        return try {
            // Set background
            canvas.clear(Color(0xF5, 0xF5, 0xF5, 0xFF))

            // Test different path attributes
            testStrokeAttributes(canvas)
            testJoinStyles(canvas)
            testCapStyles(canvas)
            testMiterLimits(canvas)

            DrawResult.OK
        } catch (e: Exception) {
            println("Error in AttributesGM: ${e.message}")
            DrawResult.FAIL
        }
    }

    private fun testStrokeAttributes(canvas: Canvas) {
        val title = "Stroke Attributes"
        drawSectionTitle(canvas, title, 20f, 20f)

        var y = 50f
        val spacing = 60f

        // Test different stroke widths
        val strokeWidths = listOf(1f, 2f, 5f, 10f, 20f)
        strokeWidths.forEachIndexed { index, width ->
            val paint = Paint().apply {
                color = Color(0xFF, 0x00, 0x00, 0xFF)
                style = PaintStyle.STROKE
                strokeWidth = width
                isAntiAlias = true
            }

            // Draw line
            canvas.drawLine(30f, y + 30f, 370f, y + 30f, paint)

            // Draw label (simplified)
            drawTextLabel(canvas, "Width: ${width}px", 30f, y)
            y += spacing
        }
    }

    private fun testJoinStyles(canvas: Canvas) {
        val title = "Join Styles"
        drawSectionTitle(canvas, title, 20f, 200f)

        var y = 230f
        val spacing = 80f

        // Test different join styles
        val joinStyles = listOf(
            StrokeJoin.MITER,
            StrokeJoin.ROUND,
            StrokeJoin.BEVEL
        )

        joinStyles.forEachIndexed { index, join ->
            val paint = Paint().apply {
                color = Color(0x00, 0x00, 0xFF, 0xFF)
                style = PaintStyle.STROKE
                strokeWidth = 15f
                strokeJoin = join
                isAntiAlias = true
            }

            // Create a path with sharp corners to show join style
            val path = Path().apply {
                moveTo(50f, y + 40f)
                lineTo(150f, y + 10f)
                lineTo(250f, y + 40f)
                lineTo(350f, y + 10f)
            }

            canvas.drawPath(path, paint)

            // Draw label
            drawTextLabel(canvas, "Join: ${join}", 30f, y)
            y += spacing
        }
    }

    private fun testCapStyles(canvas: Canvas) {
        val title = "Cap Styles"
        drawSectionTitle(canvas, title, 20f, 380f)

        var y = 410f
        val spacing = 60f

        // Test different cap styles
        val capStyles = listOf(
            StrokeCap.BUTT,
            StrokeCap.ROUND,
            StrokeCap.SQUARE
        )

        capStyles.forEachIndexed { index, cap ->
            val paint = Paint().apply {
                color = Color(0x00, 0xFF, 0x00, 0xFF)
                style = PaintStyle.STROKE
                strokeWidth = 20f
                strokeCap = cap
                isAntiAlias = true
            }

            // Draw horizontal line to show cap style
            canvas.drawLine(50f, y + 30f, 350f, y + 30f, paint)

            // Draw label
            drawTextLabel(canvas, "Cap: ${cap}", 30f, y)
            y += spacing
        }
    }

    private fun testMiterLimits(canvas: Canvas) {
        val title = "Miter Limits"
        drawSectionTitle(canvas, title, 20f, 520f)

        var y = 550f
        val spacing = 80f

        // Test different miter limits
        val miterLimits = listOf(1f, 2f, 4f, 8f)

        miterLimits.forEachIndexed { index, limit ->
            val paint = Paint().apply {
                color = Color(0xFF, 0x00, 0xFF, 0xFF)
                style = PaintStyle.STROKE
                strokeWidth = 15f
                strokeJoin = StrokeJoin.MITER
                strokeMiter = limit
                isAntiAlias = true
            }

            // Create a path with sharp corners to show miter limit effect
            val path = Path().apply {
                moveTo(100f, y + 40f)
                lineTo(200f, y + 10f)
                lineTo(300f, y + 40f)
            }

            canvas.drawPath(path, paint)

            // Draw label
            drawTextLabel(canvas, "Miter: ${limit}", 30f, y)
            y += spacing
        }
    }

    private fun drawSectionTitle(canvas: Canvas, title: String, x: Float, y: Float) {
        // Draw title background
        val titlePaint = Paint().apply {
            color = Color(0x33, 0x33, 0x33, 0xFF)
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(x, y, x + 360f, y + 30f), titlePaint)

        // Draw title text (simplified - Kanvas doesn't have full text support)
        // For now, we'll just draw a colored rectangle as a placeholder
        val textPaint = Paint().apply {
            color = Color(0xFF, 0xFF, 0xFF, 0xFF)
            style = PaintStyle.FILL
        }
        // canvas.drawText(title, x + 10, y + 20, textPaint)
    }

    private fun drawTextLabel(canvas: Canvas, text: String, x: Float, y: Float) {
        // Draw label background
        val labelPaint = Paint().apply {
            color = Color(0xEE, 0xEE, 0xEE, 0xFF)
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(x, y, x + 150f, y + 25f), labelPaint)

        // Draw label text (simplified - Kanvas doesn't have full text support)
        // canvas.drawText(text, x + 5, y + 18, Paint().apply { color = Color.BLACK })
    }
}