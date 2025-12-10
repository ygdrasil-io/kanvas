package testing.skia

import com.kanvas.core.*
import kotlin.math.sqrt
import kotlin.math.min
import testing.DrawResult
import testing.GM

/**
 * Port of Skia's all_bitmap_configs.cpp test
 * Tests various bitmap configurations and copying between them
 * Simplified version focusing on core functionality available in Kanvas
 */
class AllBitmapConfigsGM : GM() {
    override fun getName(): String = "all_bitmap_configs"
    override fun getSize(): Size = Size(512f, 512f)

    // Supported configurations in Kanvas
    private val configs = listOf(
        BitmapConfig.RGB_565,
        BitmapConfig.ARGB_4444,
        BitmapConfig.ARGB_8888,
        BitmapConfig.RGBA_F16
    )

    override fun onDraw(canvas: Canvas): DrawResult {
        return try {
            // Set background with checkerboard pattern
            drawCheckerboardBackground(canvas)

            // Create a source bitmap with a test pattern
            val sourceBitmap = createTestBitmap(128, 128)

            // Test each configuration
            val spacing = 140f
            var x = 20f
            var y = 20f

            // Draw original source
            drawBitmapWithLabel(canvas, sourceBitmap, "Source (ARGB_8888)", x, y)
            y += spacing

            // Draw copies in different configurations
            configs.forEach { config ->
                val copiedBitmap = copyBitmapToConfig(sourceBitmap, config)
                val configName = getConfigName(config)
                drawBitmapWithLabel(canvas, copiedBitmap, "Copied: $configName", x, y)
                y += spacing
            }

            DrawResult.OK
        } catch (e: Exception) {
            println("Error in AllBitmapConfigsGM: ${e.message}")
            DrawResult.FAIL
        }
    }

    private fun drawCheckerboardBackground(canvas: Canvas) {
        val checkerSize = 20f
        val paint = Paint().apply {
            style = PaintStyle.FILL
        }

        for (y in 0 until getSize().height.toInt() step checkerSize.toInt()) {
            for (x in 0 until getSize().width.toInt() step checkerSize.toInt()) {
                paint.color = if ((x / checkerSize.toInt() + y / checkerSize.toInt()) % 2 == 0) {
                    Color(0xEE, 0xEE, 0xEE, 0xFF) // Light gray
                } else {
                    Color(0xCC, 0xCC, 0xCC, 0xFF) // Medium gray
                }
                canvas.drawRect(Rect(x.toFloat(), y.toFloat(), x + checkerSize, y + checkerSize), paint)
            }
        }
    }

    private fun createTestBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap(width, height, BitmapConfig.ARGB_8888)
        
        // Create a gradient/circle test pattern
        val centerX = width / 2
        val centerY = height / 2
        val radius = min(width, height) / 2.0
        
        for (y in 0 until height) {
            for (x in 0 until width) {
                // Calculate distance from center
                val dx = x - centerX
                val dy = y - centerY
                val distance = sqrt(dx.toDouble() * dx.toDouble() + dy.toDouble() * dy.toDouble()).toFloat()
                
                // Create a radial gradient effect
                val intensity = (distance / radius.toFloat()).coerceAtMost(1.0f)
                
                val color = when {
                    distance < radius * 0.3f -> Color(0xFF, 0x00, 0x00, 0xFF) // Red center
                    distance < radius * 0.6f -> Color(
                        (0xFF * (1 - intensity)).toInt(), 
                        (0xFF * intensity).toInt(), 
                        0x00, 0xFF
                    ) // Yellow gradient
                    else -> Color(0x00, 0x00, 0xFF, 0xFF) // Blue outer
                }
                
                bitmap.setPixel(x, y, color)
            }
        }
        
        return bitmap
    }

    private fun copyBitmapToConfig(src: Bitmap, targetConfig: BitmapConfig): Bitmap {
        val dst = Bitmap(src.getWidth(), src.getHeight(), targetConfig)
        
        // Simple pixel copy - in real implementation would need proper color conversion
        for (y in 0 until src.getHeight()) {
            for (x in 0 until src.getWidth()) {
                val srcColor = src.getPixel(x, y)
                dst.setPixel(x, y, srcColor)
            }
        }
        
        return dst
    }

    private fun drawBitmapWithLabel(canvas: Canvas, bitmap: Bitmap, label: String, x: Float, y: Float) {
        // Draw the bitmap
        val srcRect = Rect(0f, 0f, bitmap.getWidth().toFloat(), bitmap.getHeight().toFloat())
        val dstRect = Rect(x, y + 20f, x + 128f, y + 148f) // Leave space for label
        canvas.drawImage(bitmap, srcRect, dstRect, Paint())
        
        // Draw label (simplified - Kanvas doesn't have full text support)
        // For now, we'll just draw a colored rectangle as a placeholder
        val labelPaint = Paint().apply {
            color = Color(0x00, 0x00, 0x00, 0xFF)
            style = PaintStyle.FILL
        }
        canvas.drawRect(Rect(x, y, x + 128f, y + 20f), labelPaint)
        
        // Draw label text would go here if text support was available
        // canvas.drawText(label, x + 5, y + 15, Paint().apply { color = Color.WHITE })
    }

    private fun getConfigName(config: BitmapConfig): String {
        return when (config) {
            BitmapConfig.RGB_565 -> "RGB_565"
            BitmapConfig.ARGB_4444 -> "ARGB_4444"
            BitmapConfig.ARGB_8888 -> "ARGB_8888"
            BitmapConfig.ALPHA_8 -> "ALPHA_8"
            BitmapConfig.RGBA_F16 -> "RGBA_F16"
        }
    }
}