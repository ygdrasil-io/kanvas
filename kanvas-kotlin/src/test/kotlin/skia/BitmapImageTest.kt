package skia

import device.BitmapDevice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import testing.DrawResult
import testing.skia.BitmapImageGM

/**
 * Unit test for BitmapImageGM
 */
class BitmapImageTest {

    @Test
    fun testBitmapImageGM() {
        val gm = BitmapImageGM()
        
        // Verify GM properties
        assertEquals("bitmapimage", gm.getName())
        assertEquals(com.kanvas.core.Size(1024f, 1024f), gm.getSize())
        
        // Test execution - this will use the fallback pattern since no image loading in main code
        val result = gm.onDraw(com.kanvas.core.Canvas(BitmapDevice(1024, 1024)))
        assertEquals(DrawResult.OK, result)
        
        println("BitmapImageGM test passed")
    }
}