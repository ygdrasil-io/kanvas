package testing.skia

import com.kanvas.core.*
import core.*
import testing.GM
import testing.DrawResult
import testing.Size

/**
 * Port of Skia's alphagradients.cpp test
 * Tests alpha channel gradients and color blending.
 * 
 * This test creates various alpha gradients to verify proper blending
 * and transparency handling. Simplified version that demonstrates
 * the concept using color gradients with alpha channels.
 */
class AlphaGradientsGM : GM() {
    override fun getName(): String = "alphagradients"
    
    override fun getSize(): Size = Size(640f, 480f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        val size = getSize()
        
        // Set background
        val bgPaint = Paint().apply {
            color = Color(0xFF, 0xFF, 0xFF, 255) // White background
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(0f, 0f, size.width, size.height), bgPaint)
        
        // Test various alpha gradient combinations
        testAlphaGradients(canvas)
        
        // Add descriptive labels
        addLabels(canvas)
        
        return DrawResult.OK
    }
    
    private fun testAlphaGradients(canvas: Canvas) {
        // Define gradient test cases (simplified from original Skia test)
        val testCases = listOf(
            GradientTestCase(Color(255, 255, 255, 255), Color(255, 255, 255, 0), "White to Transparent"),
            GradientTestCase(Color(255, 255, 255, 255), Color(255, 0, 0, 0), "White to Red"),
            GradientTestCase(Color(255, 255, 255, 255), Color(255, 255, 255, 0), "White to Yellow"),
            GradientTestCase(Color(255, 0, 0, 255), Color(255, 0, 0, 0), "Red to Transparent"),
            GradientTestCase(Color(255, 0, 0, 255), Color(255, 0, 0, 0), "Red to Red (alpha)"),
            GradientTestCase(Color(255, 0, 0, 255), Color(255, 0, 0, 0), "Blue to Transparent"),
            GradientTestCase(Color(255, 0, 255, 255), Color(255, 0, 255, 0), "Green to Transparent"),
            GradientTestCase(Color(255, 255, 255, 255), Color(255, 0, 0, 136), "White to Semi-Blue"),
        )
        
        val size = getSize()
        var x = 20f
        var y = 20f
        
        testCases.forEach { testCase ->
            drawSimulatedGradient(canvas, Rect(x, y, x + 150f, y + 80f), testCase.color1, testCase.color2)
            
            // Add label
            val textPaint = Paint().apply {
                color = Color(0, 0, 0, 255)
                textSize = 12f
                style = PaintStyle.FILL
            }
            canvas.drawText(testCase.label, x, y - 5f, textPaint)
            
            x += 170f
            if (x > size.width - 180f) {
                x = 20f
                y += 100f
            }
        }
    }
    
    private fun drawSimulatedGradient(canvas: Canvas, rect: Rect, color1: Color, color2: Color) {
        // Simulate gradient by drawing multiple rectangles with interpolated colors
        val segmentHeight = rect.height / 10f
        
        for (i in 0..9) {
            val ratio = i.toFloat() / 9f
            val interpolatedColor = interpolateColors(color1, color2, ratio)
            
            val paint = Paint().apply {
                color = interpolatedColor
                style = PaintStyle.FILL
            }
            
            val segmentRect = Rect(
                rect.left,
                rect.top + i * segmentHeight,
                rect.right,
                rect.top + (i + 1) * segmentHeight
            )
            canvas.drawRect(segmentRect, paint)
        }
        
        // Draw outline
        val outlinePaint = Paint().apply {
            color = Color(0, 0, 0, 255)
            strokeWidth = 1f
            style = PaintStyle.STROKE
        }
        canvas.drawRect(rect, outlinePaint)
    }
    
    private fun interpolateColors(color1: Color, color2: Color, ratio: Float): Color {
        // Use SkFixed for high-precision color interpolation (like Skia)
        val skRatio = SkFixed.fromFloat(ratio)
        val skInvRatio = SkFixed.fromFloat(1f) - skRatio
        
        val r = SkFixedMul(SkFixed.fromInt(color1.red), skInvRatio) + 
                SkFixedMul(SkFixed.fromInt(color2.red), skRatio)
        val g = SkFixedMul(SkFixed.fromInt(color1.green), skInvRatio) + 
                SkFixedMul(SkFixed.fromInt(color2.green), skRatio)
        val b = SkFixedMul(SkFixed.fromInt(color1.blue), skInvRatio) + 
                SkFixedMul(SkFixed.fromInt(color2.blue), skRatio)
        val a = SkFixedMul(SkFixed.fromInt(color1.alpha), skInvRatio) + 
                SkFixedMul(SkFixed.fromInt(color2.alpha), skRatio)
        
        return Color(
            r.toInt().coerceIn(0, 255),
            g.toInt().coerceIn(0, 255),
            b.toInt().coerceIn(0, 255),
            a.toInt().coerceIn(0, 255)
        )
    }
    
    private fun addLabels(canvas: Canvas) {
        val titlePaint = Paint().apply {
            color = Color(0, 0, 0, 255)
            textSize = 18f
            style = PaintStyle.FILL
        }
        canvas.drawText("Alpha Gradient Tests", 20f, 18f, titlePaint)
        
        val infoPaint = Paint().apply {
            color = Color(0x88, 0, 0, 255)
            textSize = 12f
            style = PaintStyle.FILL
        }
        canvas.drawText("Simulated gradients showing alpha channel blending", 20f, 470f, infoPaint)
    }
    
    /**
     * Helper class for gradient test cases
     */
    private data class GradientTestCase(val color1: Color, val color2: Color, val label: String)
}