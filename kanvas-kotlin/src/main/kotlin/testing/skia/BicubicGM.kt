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
 * Port of Skia's bicubic.cpp test
 * Tests bicubic filtering and different sampling options for image scaling
 */
class BicubicGM : GM() {
    override fun getName(): String = "bicubic"

    override fun getSize(): Size = Size(300f, 320f)

    override fun onDraw(canvas: Canvas): DrawResult {
        return try {
            canvas.clear(Color.BLACK)

            // Create different sampling options to test
            val samplings = listOf(
                SamplingOptions.nearest(),
                SamplingOptions.linear(),
                SamplingOptions.mitchell()
            )

            // Create a simple test image (7x7 with a vertical line)
            val testImage = createTestImage()

            // Scale canvas and draw images with different sampling options
            canvas.scale(40f, 8f)
            for (sampling in samplings) {
                canvas.drawImage(testImage, Rect(0f, 0f, testImage.getWidth().toFloat(), testImage.getHeight().toFloat()),
                    Rect(0f, 0f, testImage.getWidth().toFloat(), testImage.getHeight().toFloat()), Paint(), sampling)
                canvas.translate(0f, testImage.getHeight() + 1f)
            }

            // Note: Shader support with SamplingOptions is not yet implemented in Kanvas
            // The original Skia test also includes shader tests, but we'll focus on the
            // core image scaling functionality for now

            DrawResult.OK
        } catch (e: Exception) {
            println("BicubicGM failed: ${e.message}")
            DrawResult.FAIL
        }
    }

    /**
     * Create a simple 7x7 test image with a vertical white line
     * Similar to the original Skia test
     */
    private fun createTestImage(): Bitmap {
        val image = Bitmap(7, 7, BitmapConfig.ARGB_8888)
        
        // Fill with black
        for (y in 0 until 7) {
            for (x in 0 until 7) {
                image.setPixel(x, y, Color.BLACK)
            }
        }

        // Draw a vertical white line at x=3.5 (between pixels 3 and 4)
        // Since we're working with integer pixels, we'll approximate this
        for (y in 0 until 8) {  // Draw slightly taller to match Skia's line
            if (y < 7) {  // Stay within bounds
                image.setPixel(3, y, Color.WHITE)  // Main line
                image.setPixel(4, y, Color.WHITE)  // Make it slightly wider for visibility
            }
        }

        return image
    }
}