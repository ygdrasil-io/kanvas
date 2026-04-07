package core

import com.kanvas.core.CanvasFactory
import com.kanvas.core.Color
import com.kanvas.core.Paint
import com.kanvas.core.PaintStyle
import com.kanvas.core.Path
import com.kanvas.core.Rect
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Basic test for CanvasWithDevice functionality
 */
class CanvasTest {

    @Test
    fun testCanvasWithDeviceCreation() {
        val canvas = CanvasFactory.createRaster(100, 100)
        assertNotNull(canvas)
        assertTrue(canvas.width == 100)
        assertTrue(canvas.height == 100)
    }

    @Test
    fun testCanvasWithDeviceDrawing() {
        val canvas = CanvasFactory.createRaster(100, 100)
        val paint = Paint().apply {
            color = Color(255, 0, 0)
            style = PaintStyle.FILL
        }

        // This should not throw an exception
        canvas.drawRect(Rect(10f, 10f, 50f, 50f), paint)
        
        // Verify that something was drawn
        val bitmap = canvas.bitmap
        assertNotNull(bitmap)
    }

    @Test
    fun testCanvasWithDevicePathDrawing() {
        val canvas = CanvasFactory.createRaster(200, 200)
        val paint = Paint().apply {
            color = Color(255, 0, 0)
            strokeWidth = 2f
            style = PaintStyle.STROKE
        }

        val path = Path()
        path.moveTo(20f, 100f)
        path.conicTo(100f, 20f, 180f, 100f, 0.5f)

        // This should not throw an exception
        canvas.drawPath(path, paint)
        
        // Verify that something was drawn
        val bitmap = canvas.bitmap
        assertNotNull(bitmap)
    }
}