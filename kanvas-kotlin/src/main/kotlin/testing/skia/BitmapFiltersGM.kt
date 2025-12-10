package testing.skia

import com.kanvas.core.Bitmap
import com.kanvas.core.BitmapConfig
import com.kanvas.core.Canvas
import com.kanvas.core.Color
import com.kanvas.core.Paint
import com.kanvas.core.Rect
import com.kanvas.core.SamplingOptions
import com.kanvas.core.Size
import testing.DrawResult
import testing.GM

/**
 * Port of Skia's bitmapfilters.cpp test
 * Tests different bitmap filtering modes across different color configurations.
 * 
 * This test creates a simple 2x2 bitmap with different colors and then draws it
 * scaled up using different sampling/filtering options to demonstrate how
 * different filtering modes affect the output quality.
 * 
 * Simplified from the original Skia test to focus on core filtering functionality
 * that's available in Kanvas.
 */
class BitmapFiltersGM : GM() {
    override fun getName(): String = "bitmapfilters"
    
    override fun getSize(): Size = Size(540f, 250f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        return try {
            // Set background color
            canvas.clear(Color(0xDD, 0xDD, 0xDD, 255)) // Light gray
            
            // Create test bitmaps with different color configurations
            val bitmap32 = createTestBitmap(BitmapConfig.ARGB_8888)
            val bitmap4444 = createTestBitmap(BitmapConfig.ARGB_4444)
            val bitmap565 = createTestBitmap(BitmapConfig.RGB_565)
            
            // Draw each bitmap configuration with different filtering modes
            var yPos = 10f
            
            drawBitmapRow(canvas, bitmap4444, "ARGB_4444", yPos)
            yPos += 80f
            
            drawBitmapRow(canvas, bitmap565, "RGB_565", yPos)
            yPos += 80f
            
            drawBitmapRow(canvas, bitmap32, "ARGB_8888", yPos)
            
            DrawResult.OK
        } catch (e: Exception) {
            println("BitmapFiltersGM failed: ${e.message}")
            e.printStackTrace()
            DrawResult.FAIL
        }
    }
    
    /**
     * Create a simple 2x2 test bitmap with different colors
     * Similar to the original Skia test's make_bm function
     */
    private fun createTestBitmap(config: BitmapConfig): Bitmap {
        val bitmap = Bitmap(2, 2, config)
        
        // Set the 4 pixels to different colors
        bitmap.setPixel(0, 0, Color(255, 0, 0, 255))     // Red
        bitmap.setPixel(1, 0, Color(0, 255, 0, 255))     // Green
        bitmap.setPixel(0, 1, Color(0, 0, 255, 255))     // Blue
        bitmap.setPixel(1, 1, Color(255, 255, 255, 255)) // White
        
        return bitmap
    }
    
    /**
     * Draw a row of bitmap filtering tests for a specific bitmap configuration
     */
    private fun drawBitmapRow(canvas: Canvas, bitmap: Bitmap, label: String, y: Float) {
        val paint = Paint().apply {
            isAntiAlias = true
        }
        
        // Draw label
        val labelPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            isAntiAlias = true
        }
        canvas.drawText(label, 10f, y + 20f, labelPaint)
        
        // Translate to drawing position
        canvas.save()
        canvas.translate(50f, y)
        
        // Scale up for visibility (similar to original test's scale factor)
        canvas.scale(32f, 32f)
        
        var xPos = 0f
        
        // Test 1: Nearest neighbor (default)
        xPos = drawBitmapTest(canvas, bitmap, xPos, SamplingOptions.nearest(), paint)
        
        // Test 2: Linear filtering
        xPos = drawBitmapTest(canvas, bitmap, xPos, SamplingOptions.linear(), paint)
        
        // Test 3: Linear filtering with dithering
        paint.isDither = true
        drawBitmapTest(canvas, bitmap, xPos, SamplingOptions.linear(), paint)
        
        canvas.restore()
    }
    
    /**
     * Draw a single bitmap test with specific sampling options
     */
    private fun drawBitmapTest(canvas: Canvas, bitmap: Bitmap, x: Float, sampling: SamplingOptions, paint: Paint): Float {
        val srcRect = Rect(0f, 0f, bitmap.getWidth().toFloat(), bitmap.getHeight().toFloat())
        val dstRect = Rect(x, 0f, x + bitmap.getWidth().toFloat(), bitmap.getHeight().toFloat())
        
        canvas.drawImage(bitmap, srcRect, dstRect, paint, sampling)
        
        // Return new x position (with some spacing)
        return x + bitmap.getWidth() * 5f / 4f
    }
}