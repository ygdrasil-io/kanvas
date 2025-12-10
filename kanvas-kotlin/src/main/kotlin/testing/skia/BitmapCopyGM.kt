package testing.skia

import com.kanvas.core.*
import testing.DrawResult
import testing.GM

/**
 * Port of Skia's bitmapcopy.cpp test
 * Tests copying bitmaps between different color configurations
 */
class BitmapCopyGM : GM() {
    override fun getName(): String = "bitmapcopy"
    override fun getSize(): Size = Size(540f, 330f)

    private val configs = listOf(
        BitmapConfig.RGB_565,
        BitmapConfig.ARGB_4444,
        BitmapConfig.ARGB_8888
    )

    override fun onDraw(canvas: Canvas): DrawResult {
        return try {
            // Set background color
            canvas.clear(Color(0xDD, 0xDD, 0xDD, 0xFF))

            // Create source bitmap with checkerboard pattern
            val src = createCheckerboardBitmap(40, 40)

            // Copy to different configurations
            val dstBitmaps = configs.map { config ->
                copyBitmapToConfig(src, config)
            }

            // Draw layout
            val horizMargin = 10f
            val vertMargin = 10f
            val bitmapSize = 40f
            val textHeight = 20f // Approximate text height
            
            val width = maxOf(bitmapSize, configs.maxOf { getConfigName(it).length * 8f }) // Approximate text width
            val horizOffset = width + horizMargin
            val vertOffset = maxOf(bitmapSize, textHeight) + vertMargin

            canvas.translate(20f, 20f)

            dstBitmaps.forEachIndexed { index, dstBitmap ->
                // Draw destination bitmap
                val bitmapX = (width - bitmapSize) / 2f
                val srcRect = Rect(0f, 0f, dstBitmap.getWidth().toFloat(), dstBitmap.getHeight().toFloat())
                val dstRect = Rect(bitmapX, textHeight + vertMargin, bitmapX + bitmapSize, textHeight + vertMargin + bitmapSize)
                canvas.drawImage(dstBitmap, srcRect, dstRect, Paint())

                // Move to next position
                canvas.translate(horizOffset, 0f)
            }

            DrawResult.OK
        } catch (e: Exception) {
            println("Error in BitmapCopyGM: ${e.message}")
            DrawResult.FAIL
        }
    }

    private fun createCheckerboardBitmap(width: Int, height: Int): Bitmap {
        val bitmap = Bitmap(width, height, BitmapConfig.ARGB_8888)
        
        // Create a simple checkerboard pattern
        for (y in 0 until height) {
            for (x in 0 until width) {
                val color = when {
                    x < width/2 && y < height/2 -> Color(0xFF, 0x00, 0x00, 0xFF) // Red
                    x >= width/2 && y < height/2 -> Color(0x00, 0xFF, 0x00, 0xFF) // Green
                    x < width/2 && y >= height/2 -> Color(0x00, 0x00, 0xFF, 0xFF) // Blue
                    else -> Color(0xFF, 0xFF, 0x00, 0xFF) // Yellow
                }
                bitmap.setPixel(x, y, color)
            }
        }
        return bitmap
    }

    private fun copyBitmapToConfig(src: Bitmap, targetConfig: BitmapConfig): Bitmap {
        // Create a new bitmap with the target configuration
        val dst = Bitmap(src.getWidth(), src.getHeight(), targetConfig)
        
        // Copy pixels - this is a simplified version
        // In a real implementation, we'd need proper color conversion
        for (y in 0 until src.getHeight()) {
            for (x in 0 until src.getWidth()) {
                val srcColor = src.getPixel(x, y)
                dst.setPixel(x, y, srcColor)
            }
        }
        
        return dst
    }

    private fun getConfigName(config: BitmapConfig): String {
        return when (config) {
            BitmapConfig.RGB_565 -> "565"
            BitmapConfig.ARGB_4444 -> "4444"
            BitmapConfig.ARGB_8888 -> "8888"
            BitmapConfig.ALPHA_8 -> "A8"
            BitmapConfig.RGBA_F16 -> "F16"
        }
    }
}