package testing.skia

import com.kanvas.core.Canvas
import com.kanvas.core.Color
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Rect
import com.kanvas.core.Size
import com.kanvas.core.SkFixed
import com.kanvas.core.SkFixedMul
import testing.DrawResult
import testing.GM

/**
 * Port of Skia's gradient.cpp test
 * Tests basic gradient drawing functionality.
 * 
 * This test creates various gradient patterns to test Kanvas gradient capabilities.
 * Since Kanvas doesn't have native gradient support yet, we simulate gradients
 * using multiple rectangles with interpolated colors.
 */
class GradientGM : GM() {
    override fun getName(): String = "gradient"
    
    override fun getSize(): Size = Size(256f, 256f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        val size = getSize()
        
        // Set background
        val bgPaint = Paint().apply {
            color = Color(0xFF, 0xFF, 0xFF, 255) // White background
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(0f, 0f, size.width, size.height), bgPaint)
        
        // Draw various gradient simulations
        drawSimulatedGradients(canvas)
        
        // Add descriptive labels
        addLabels(canvas)
        
        return DrawResult.OK
    }
    
    private fun drawSimulatedGradients(canvas: Canvas) {
        // Gradient 1: Horizontal gradient (blue to red)
        drawHorizontalGradient(canvas, Rect(20f, 20f, 200f, 60f), 
                              Color(0, 0, 255, 255), Color(255, 0, 0, 255))
        
        // Gradient 2: Vertical gradient (green to purple)
        drawVerticalGradient(canvas, Rect(20f, 80f, 200f, 140f),
                            Color(0, 255, 0, 255), Color(128, 0, 128, 255))
        
        // Gradient 3: Diagonal gradient simulation
        drawDiagonalGradient(canvas, Rect(20f, 160f, 140f, 220f),
                             Color(255, 255, 0, 255), Color(0, 255, 255, 255))
        
        // Gradient 4: Radial gradient simulation
        drawRadialGradient(canvas, Rect(160f, 160f, 240f, 240f),
                           Color(255, 0, 0, 255), Color(255, 255, 255, 255))
    }
    
    private fun drawHorizontalGradient(canvas: Canvas, rect: Rect, color1: Color, color2: Color) {
        val segmentWidth = rect.width / 20f
        
        for (i in 0..19) {
            val ratio = i.toFloat() / 19f
            val interpolatedColor = interpolateColors(color1, color2, ratio)
            
            val paint = Paint().apply {
                color = interpolatedColor
                style = PaintStyle.FILL
            }
            
            val segmentRect = Rect(
                rect.left + i * segmentWidth,
                rect.top,
                rect.left + (i + 1) * segmentWidth,
                rect.bottom
            )
            canvas.drawRect(segmentRect, paint)
        }
    }
    
    private fun drawVerticalGradient(canvas: Canvas, rect: Rect, color1: Color, color2: Color) {
        val segmentHeight = rect.height / 20f
        
        for (i in 0..19) {
            val ratio = i.toFloat() / 19f
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
    }
    
    private fun drawDiagonalGradient(canvas: Canvas, rect: Rect, color1: Color, color2: Color) {
        // Simplified diagonal gradient using horizontal segments with varying heights
        val segmentWidth = rect.width / 15f
        
        for (i in 0..14) {
            val ratio = i.toFloat() / 14f
            val interpolatedColor = interpolateColors(color1, color2, ratio)
            
            val paint = Paint().apply {
                color = interpolatedColor
                style = PaintStyle.FILL
            }
            
            val segmentHeight = rect.height * (0.3f + ratio * 0.7f)
            val segmentRect = Rect(
                rect.left + i * segmentWidth,
                rect.top + (rect.height - segmentHeight) / 2,
                rect.left + (i + 1) * segmentWidth,
                rect.top + (rect.height + segmentHeight) / 2
            )
            canvas.drawRect(segmentRect, paint)
        }
    }
    
    private fun drawRadialGradient(canvas: Canvas, rect: Rect, color1: Color, color2: Color) {
        // Simplified radial gradient using concentric rectangles
        val centerX = (rect.left + rect.right) / 2
        val centerY = (rect.top + rect.bottom) / 2
        val maxRadius = minOf(rect.width, rect.height) / 2
        
        for (i in 0..9) {
            val ratio = i.toFloat() / 9f
            val interpolatedColor = interpolateColors(color1, color2, ratio)
            
            val paint = Paint().apply {
                color = interpolatedColor
                style = PaintStyle.FILL
            }
            
            val currentRadius = maxRadius * ratio
            val innerRect = Rect(
                centerX - currentRadius,
                centerY - currentRadius,
                centerX + currentRadius,
                centerY + currentRadius
            )
            
            // Draw ring (outer minus inner)
            if (i > 0) {
                val outerRadius = maxRadius * (i + 1).toFloat() / 9f
                val outerRect = Rect(
                    centerX - outerRadius,
                    centerY - outerRadius,
                    centerX + outerRadius,
                    centerY + outerRadius
                )
                
                // Draw the ring by drawing outer and then inner (simplified)
                canvas.drawRect(outerRect, paint)
            } else {
                canvas.drawRect(innerRect, paint)
            }
        }
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
            textSize = 16f
            style = PaintStyle.FILL
        }
        canvas.drawText("Gradient Test", 20f, 18f, titlePaint)
        
        val infoPaint = Paint().apply {
            color = Color(0x88, 0, 0, 255)
            textSize = 12f
            style = PaintStyle.FILL
        }
        canvas.drawText("Simulated gradients using color interpolation", 20f, 240f, infoPaint)
    }
}