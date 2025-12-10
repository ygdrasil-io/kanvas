package testing.skia

import com.kanvas.core.Bitmap
import com.kanvas.core.BitmapConfig
import com.kanvas.core.Canvas
import com.kanvas.core.Color
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Rect
import com.kanvas.core.Size
import testing.DrawResult
import testing.GM

/**
 * Port of Skia's arithmode.cpp test
 * Tests arithmetic blend modes and color mixing
 * Simplified version focusing on core functionality available in Kanvas
 */
class ArithmodeGM : GM() {
    override fun getName(): String = "arithmode"
    override fun getSize(): Size = Size(640f, 400f)

    override fun onDraw(canvas: Canvas): DrawResult {
        return try {
            // Set background
            canvas.clear(Color(0xF0, 0xF0, 0xF0, 0xFF))

            // Create source and destination patterns
            val srcBitmap = createSourcePattern(100, 32)
            val dstBitmap = createDestinationPattern(100, 32)

            // Test different blend modes
            val blendModes = listOf(
                com.kanvas.core.BlendMode.SRC_OVER,
                com.kanvas.core.BlendMode.MULTIPLY,
                com.kanvas.core.BlendMode.SCREEN,
                com.kanvas.core.BlendMode.OVERLAY,
                com.kanvas.core.BlendMode.DARKEN,
                com.kanvas.core.BlendMode.LIGHTEN
            )

            val spacing = 120f
            var x = 20f
            var y = 20f

            // Draw header
            drawBitmapWithLabel(canvas, srcBitmap, "Source", x, y)
            x += spacing
            drawBitmapWithLabel(canvas, dstBitmap, "Destination", x, y)
            x += spacing
            
            // Test each blend mode
            blendModes.forEach { blendMode ->
                // Draw blended result
                val blendedBitmap = blendBitmaps(srcBitmap, dstBitmap, blendMode)
                drawBitmapWithLabel(canvas, blendedBitmap, blendMode.toString(), x, y)
                x += spacing
            }

            // Second row: More blend modes
            y += 50f
            x = 20f
            
            val blendModes2 = listOf(
                com.kanvas.core.BlendMode.PLUS,
                com.kanvas.core.BlendMode.SRC_IN,
                com.kanvas.core.BlendMode.DST_IN,
                com.kanvas.core.BlendMode.SRC_OUT,
                com.kanvas.core.BlendMode.DST_OUT
            )

            blendModes2.forEach { blendMode ->
                // Draw blended result
                val blendedBitmap = blendBitmaps(srcBitmap, dstBitmap, blendMode)
                drawBitmapWithLabel(canvas, blendedBitmap, blendMode.toString(), x, y)
                x += spacing
            }

            DrawResult.OK
        } catch (e: Exception) {
            println("Error in ArithmodeGM: ${e.message}")
            DrawResult.FAIL
        }
    }

    private fun createSourcePattern(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap(width, height, BitmapConfig.ARGB_8888)
        
        // Create horizontal gradient: transparent -> green -> cyan -> red -> magenta -> white
        for (y in 0 until height) {
            for (x in 0 until width) {
                val ratio = x.toFloat() / width
                val color = when {
                    ratio < 0.17f -> Color(0x00, 0xFF, 0x00, (ratio / 0.17f * 0xFF).toInt()) // Transparent to green
                    ratio < 0.33f -> Color(0x00, 0xFF, ((ratio - 0.17f) / 0.16f * 0xFF).toInt(), 0xFF) // Green to cyan
                    ratio < 0.50f -> Color(((ratio - 0.33f) / 0.17f * 0xFF).toInt(), 0xFF, 0xFF, 0xFF) // Cyan to red
                    ratio < 0.67f -> Color(0xFF, ((ratio - 0.50f) / 0.17f * 0xFF).toInt(), 0xFF, 0xFF) // Red to magenta
                    ratio < 0.83f -> Color(0xFF, 0xFF, ((ratio - 0.67f) / 0.16f * 0xFF).toInt(), 0xFF) // Magenta to white
                    else -> Color(0xFF, 0xFF, 0xFF, 0xFF) // White
                }
                bitmap.setPixel(x, y, color)
            }
        }
        
        return bitmap
    }

    private fun createDestinationPattern(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap(width, height, BitmapConfig.ARGB_8888)
        
        // Create diagonal gradient: blue -> yellow -> black -> green -> gray
        for (y in 0 until height) {
            for (x in 0 until width) {
                val ratio = (x + y).toFloat() / (width + height)
                val color = when {
                    ratio < 0.25f -> Color(
                        0x00, 
                        0x00, 
                        0xFF, 
                        0xFF
                    ) // Blue
                    ratio < 0.50f -> Color(
                        ((ratio - 0.25f) / 0.25f * 0xFF).toInt(),
                        ((ratio - 0.25f) / 0.25f * 0xFF).toInt(),
                        0xFF,
                        0xFF
                    ) // Blue to yellow
                    ratio < 0.75f -> Color(
                        0x00,
                        0x00,
                        (0xFF - (ratio - 0.50f) / 0.25f * 0xFF).toInt(),
                        0xFF
                    ) // Yellow to black
                    ratio < 0.90f -> Color(
                        0x00,
                        ((ratio - 0.75f) / 0.15f * 0xFF).toInt(),
                        0x00,
                        0xFF
                    ) // Black to green
                    else -> Color(
                        ((ratio - 0.90f) / 0.10f * 0x80).toInt(),
                        ((ratio - 0.90f) / 0.10f * 0x80).toInt(),
                        ((ratio - 0.90f) / 0.10f * 0x80).toInt(),
                        0xFF
                    ) // Green to gray
                }
                bitmap.setPixel(x, y, color)
            }
        }
        
        return bitmap
    }

    private fun blendBitmaps(src: Bitmap, dst: Bitmap, blendMode: com.kanvas.core.BlendMode): Bitmap {
        // Create a new bitmap for the result
        val result = Bitmap(src.getWidth(), src.getHeight(), BitmapConfig.ARGB_8888)
        
        // Simple blending - in real implementation would use proper blending math
        for (y in 0 until src.getHeight()) {
            for (x in 0 until src.getWidth()) {
                val srcColor = src.getPixel(x, y)
                val dstColor = dst.getPixel(x, y)
                
                // Apply blend mode
                val blendedColor = when (blendMode) {
                    com.kanvas.core.BlendMode.SRC_OVER -> blendSrcOver(srcColor, dstColor)
                    com.kanvas.core.BlendMode.MULTIPLY -> blendMultiply(srcColor, dstColor)
                    com.kanvas.core.BlendMode.SCREEN -> blendScreen(srcColor, dstColor)
                    com.kanvas.core.BlendMode.OVERLAY -> blendOverlay(srcColor, dstColor)
                    com.kanvas.core.BlendMode.DARKEN -> blendDarken(srcColor, dstColor)
                    com.kanvas.core.BlendMode.LIGHTEN -> blendLighten(srcColor, dstColor)
                    com.kanvas.core.BlendMode.PLUS -> blendAdd(srcColor, dstColor)
                    com.kanvas.core.BlendMode.SRC_IN -> blendSrcIn(srcColor, dstColor)
                    com.kanvas.core.BlendMode.DST_IN -> blendDstIn(srcColor, dstColor)
                    com.kanvas.core.BlendMode.SRC_OUT -> blendSrcOut(srcColor, dstColor)
                    com.kanvas.core.BlendMode.DST_OUT -> blendDstOut(srcColor, dstColor)
                    else -> srcColor // Default to source
                }
                
                result.setPixel(x, y, blendedColor)
            }
        }
        
        return result
    }

    private fun drawBitmapWithLabel(canvas: Canvas, bitmap: Bitmap, label: String, x: Float, y: Float) {
        // Draw the bitmap
        val srcRect = Rect(0f, 0f, bitmap.getWidth().toFloat(), bitmap.getHeight().toFloat())
        val dstRect = Rect(x, y + 20f, x + bitmap.getWidth().toFloat(), y + bitmap.getHeight().toFloat() + 20f)
        canvas.drawImage(bitmap, srcRect, dstRect, Paint())

        // Draw label (simplified - Kanvas doesn't have full text support)
        val labelPaint = Paint().apply {
            color = Color(0x00, 0x00, 0x00, 0xFF)
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(x, y, x + bitmap.getWidth().toFloat(), y + 20f), labelPaint)
        
        // Draw label text would go here if text support was available
        // canvas.drawText(label, x + 5, y + 15, Paint().apply { color = Color.WHITE })
    }

    // Basic blend mode implementations
    private fun blendSrcOver(src: Color, dst: Color): Color {
        val srcA = src.alpha / 255.0f
        val invSrcA = 1.0f - srcA
        
        val r = (src.red * srcA + dst.red * invSrcA).toInt().coerceIn(0, 255)
        val g = (src.green * srcA + dst.green * invSrcA).toInt().coerceIn(0, 255)
        val b = (src.blue * srcA + dst.blue * invSrcA).toInt().coerceIn(0, 255)
        val a = (src.alpha + dst.alpha - src.alpha * dst.alpha / 255).coerceIn(0, 255)
        
        return Color(r, g, b, a)
    }

    private fun blendMultiply(src: Color, dst: Color): Color {
        val r = (src.red * dst.red / 255).coerceIn(0, 255)
        val g = (src.green * dst.green / 255).coerceIn(0, 255)
        val b = (src.blue * dst.blue / 255).coerceIn(0, 255)
        val a = (src.alpha * dst.alpha / 255).coerceIn(0, 255)
        
        return Color(r, g, b, a)
    }

    private fun blendScreen(src: Color, dst: Color): Color {
        val r = (255 - (255 - src.red) * (255 - dst.red) / 255).coerceIn(0, 255)
        val g = (255 - (255 - src.green) * (255 - dst.green) / 255).coerceIn(0, 255)
        val b = (255 - (255 - src.blue) * (255 - dst.blue) / 255).coerceIn(0, 255)
        val a = (255 - (255 - src.alpha) * (255 - dst.alpha) / 255).coerceIn(0, 255)
        
        return Color(r, g, b, a)
    }

    private fun blendOverlay(src: Color, dst: Color): Color {
        fun overlayComponent(s: Int, d: Int): Int {
            return if (d < 128) (2 * s * d / 255).coerceIn(0, 255)
                   else (255 - 2 * (255 - s) * (255 - d) / 255).coerceIn(0, 255)
        }
        
        val r = overlayComponent(src.red, dst.red)
        val g = overlayComponent(src.green, dst.green)
        val b = overlayComponent(src.blue, dst.blue)
        val a = overlayComponent(src.alpha, dst.alpha)
        
        return Color(r, g, b, a)
    }

    private fun blendDarken(src: Color, dst: Color): Color {
        val r = minOf(src.red, dst.red)
        val g = minOf(src.green, dst.green)
        val b = minOf(src.blue, dst.blue)
        val a = minOf(src.alpha, dst.alpha)
        
        return Color(r, g, b, a)
    }

    private fun blendLighten(src: Color, dst: Color): Color {
        val r = maxOf(src.red, dst.red)
        val g = maxOf(src.green, dst.green)
        val b = maxOf(src.blue, dst.blue)
        val a = maxOf(src.alpha, dst.alpha)
        
        return Color(r, g, b, a)
    }

    private fun blendAdd(src: Color, dst: Color): Color {
        val r = minOf(src.red + dst.red, 255)
        val g = minOf(src.green + dst.green, 255)
        val b = minOf(src.blue + dst.blue, 255)
        val a = minOf(src.alpha + dst.alpha, 255)
        
        return Color(r, g, b, a)
    }

    private fun blendSrcIn(src: Color, dst: Color): Color {
        val dstA = dst.alpha / 255.0f
        val r = (src.red * dstA).toInt().coerceIn(0, 255)
        val g = (src.green * dstA).toInt().coerceIn(0, 255)
        val b = (src.blue * dstA).toInt().coerceIn(0, 255)
        val a = (src.alpha * dstA).toInt().coerceIn(0, 255)
        
        return Color(r, g, b, a)
    }

    private fun blendDstIn(src: Color, dst: Color): Color {
        val srcA = src.alpha / 255.0f
        val r = (dst.red * srcA).toInt().coerceIn(0, 255)
        val g = (dst.green * srcA).toInt().coerceIn(0, 255)
        val b = (dst.blue * srcA).toInt().coerceIn(0, 255)
        val a = (dst.alpha * srcA).toInt().coerceIn(0, 255)
        
        return Color(r, g, b, a)
    }

    private fun blendSrcOut(src: Color, dst: Color): Color {
        val invDstA = 1.0f - (dst.alpha / 255.0f)
        val r = (src.red * invDstA).toInt().coerceIn(0, 255)
        val g = (src.green * invDstA).toInt().coerceIn(0, 255)
        val b = (src.blue * invDstA).toInt().coerceIn(0, 255)
        val a = (src.alpha * invDstA).toInt().coerceIn(0, 255)
        
        return Color(r, g, b, a)
    }

    private fun blendDstOut(src: Color, dst: Color): Color {
        val invSrcA = 1.0f - (src.alpha / 255.0f)
        val r = (dst.red * invSrcA).toInt().coerceIn(0, 255)
        val g = (dst.green * invSrcA).toInt().coerceIn(0, 255)
        val b = (dst.blue * invSrcA).toInt().coerceIn(0, 255)
        val a = (dst.alpha * invSrcA).toInt().coerceIn(0, 255)
        
        return Color(r, g, b, a)
    }
}