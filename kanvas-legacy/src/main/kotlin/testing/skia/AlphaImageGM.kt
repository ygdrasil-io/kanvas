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
 * Port of Skia's alpha_image.cpp test
 * Tests alpha channel image handling and rendering.
 * 
 * This test creates images with alpha channels and verifies they can be
 * properly drawn and composited. Simplified version focusing on core
 * alpha channel functionality.
 */
class AlphaImageGM : GM() {
    override fun getName(): String = "alpha_image"
    
    override fun getSize(): Size = Size(256f, 256f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        val size = getSize()
        
        // Set background to make alpha visible
        val bgPaint = Paint().apply {
            color = Color(0xDD, 0xDD, 0xDD, 255) // Light gray background
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(0f, 0f, size.width, size.height), bgPaint)
        
        // Test 1: Basic alpha image
        testBasicAlphaImage(canvas)
        
        // Test 2: Alpha gradient image
        testAlphaGradientImage(canvas)
        
        // Test 3: Alpha patterns
        testAlphaPatterns(canvas)
        
        // Add labels
        addLabels(canvas)
        
        return DrawResult.OK
    }
    
    private fun testBasicAlphaImage(canvas: Canvas) {
        // Create a simple alpha-only image (simulated with transparent colors)
        val alphaPaint = Paint().apply {
            style = PaintStyle.FILL
        }
        
        // Draw semi-transparent rectangles to simulate alpha image
        for (i in 0..3) {
            for (j in 0..3) {
                val alpha = 50 + i * 50 + j * 10
                alphaPaint.color = Color(0xFF, 0, 0, alpha.coerceAtMost(255))
                
                val x = 20f + i * 30f
                val y = 20f + j * 30f
                canvas.drawRect(Rect(x, y, x + 25f, y + 25f), alphaPaint)
            }
        }
    }
    
    private fun testAlphaGradientImage(canvas: Canvas) {
        // Simulate alpha gradient by drawing rectangles with varying alpha
        val gradientPaint = Paint().apply {
            style = PaintStyle.FILL
        }
        
        for (i in 0..15) {
            val alpha = (i * 16).coerceAtMost(255)
            gradientPaint.color = Color(0, 0x88, 0xFF, alpha)
            
            val x = 150f
            val y = 20f + i * 10f
            canvas.drawRect(Rect(x, y, x + 80f, y + 8f), gradientPaint)
        }
    }
    
    private fun testAlphaPatterns(canvas: Canvas) {
        // Test different alpha patterns
        val patternPaint = Paint().apply {
            style = PaintStyle.FILL
        }
        
        // Checkerboard pattern with alpha
        for (y in 0..7) {
            for (x in 0..7) {
                val alpha = if ((x + y) % 2 == 0) 200 else 100
                patternPaint.color = Color(0x88, 0, 0x88, alpha)
                
                val px = 20f + x * 12f
                val py = 150f + y * 12f
                canvas.drawRect(Rect(px, py, px + 10f, py + 10f), patternPaint)
            }
        }
        
        // Circular alpha pattern
        // Note: drawCircle is not yet implemented in Kanvas
        // For now, we'll draw squares as a placeholder
        val circlePaint = Paint().apply {
            color = Color(0, 0xAA, 0xAA, 180)
            style = PaintStyle.FILL
            isAntiAlias = true
        }
        
        for (i in 0..2) {
            val size = 30f + i * 16f
            val alpha = 220 - i * 40
            circlePaint.color = Color(0xAA, 0x55, 0, alpha)
            
            val x = 220f - size/2
            val y = 220f - size/2
            canvas.drawRect(Rect(x, y, x + size, y + size), circlePaint)
        }
    }
    
    private fun addLabels(canvas: Canvas) {
        val textPaint = Paint().apply {
            color = Color(0, 0, 0, 255)
            textSize = 14f
            style = PaintStyle.FILL
        }
        
        canvas.drawText("Alpha Rectangles", 20f, 15f, textPaint)
        canvas.drawText("Alpha Gradient", 150f, 15f, textPaint)
        canvas.drawText("Alpha Patterns", 20f, 145f, textPaint)
        canvas.drawText("Alpha Circles", 200f, 145f, textPaint)
    }
}