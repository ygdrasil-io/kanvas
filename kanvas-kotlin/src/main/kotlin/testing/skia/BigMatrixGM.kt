package testing.skia

import com.kanvas.core.Bitmap
import com.kanvas.core.BitmapConfig
import com.kanvas.core.Canvas
import com.kanvas.core.Color
import com.kanvas.core.Matrix
import com.kanvas.core.Paint
import com.kanvas.core.Rect
import com.kanvas.core.Shaders
import com.kanvas.core.Size
import com.kanvas.core.TileMode
import testing.DrawResult
import testing.GM

/**
 * Port of Skia's bigmatrix.cpp test
 * Tests large matrix transformations and operations
 */
class BigMatrixGM : GM() {
    
    override fun getName(): String = "bigmatrix"
    
    override fun getSize(): Size = Size(50f, 50f)
    
    override fun onDraw(canvas: Canvas): DrawResult {
        return try {
            // Set background color (light teal)
            canvas.clear(Color(0x66, 0xAA, 0x99, 0xFF))
            
            // Create a large transformation matrix
            // Apply transformations: rotate 33 degrees around origin, scale by 3000, translate by (6000, -5000)
            val m = Matrix.identity()
                .rotate(33f, 0f, 0f)
                .scale(3000f, 3000f)
                .translate(6000f, -5000f)
            
            // Apply the matrix to the canvas
            canvas.concat(m)
            
            // Create paint for drawing
            val paint = Paint().apply {
                color = Color(0xFF, 0x00, 0x00, 0xFF)  // Red
                isAntiAlias = true
            }
            
            // Note: Matrix inversion is not implemented in Kanvas for 2D matrices
            // The original test checks invertibility but doesn't use the result
            // Calculate a small size for drawing elements
            val small = 1f / 500f
            
            // Draw first circle at transformed position (10, 10)
            val (x1, y1) = m.mapPoint(10f, 10f)
            canvas.drawCircle(x1, y1, small, paint)
            
            // Draw rectangle at transformed position (30, 10)
            val (x2, y2) = m.mapPoint(30f, 10f)
            val rect = Rect(x2 - small, y2 - small, x2 + small, y2 + small)
            canvas.drawRect(rect, paint)
            
            // Create a 2x2 bitmap for shader
            val bmp = Bitmap(2, 2, BitmapConfig.ARGB_8888)
            
            // Set bitmap pixels (similar to original test)
            bmp.setPixel(0, 0, Color(0xFF, 0xFF, 0x00, 0x00))  // Red
            bmp.setPixel(1, 0, Color(0xFF, 0x00, 0xFF, 0x00))  // Green
            bmp.setPixel(0, 1, Color(0x80, 0x00, 0x00, 0x00))  // Transparent black
            bmp.setPixel(1, 1, Color(0xFF, 0x00, 0x00, 0xFF))  // Blue
            
            // Draw bitmap shader at transformed position (30, 30)
            val (x3, y3) = m.mapPoint(30f, 30f)
            
            val shaderPaint = Paint().apply {
                shader = Shaders.makeBitmapShader(bmp, TileMode.REPEAT, TileMode.REPEAT)
                isAntiAlias = false
            }
            
            val shaderRect = Rect(x3 - small, y3 - small, x3 + small, y3 + small)
            canvas.drawRect(shaderRect, shaderPaint)
            
            DrawResult.OK
        } catch (e: Exception) {
            println("Error in BigMatrixGM: ${e.message}")
            DrawResult.FAIL
        }
    }
}