package skia

import device.BitmapDevice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testing.DrawResult
import testing.skia.BitmapFiltersGM
import java.nio.file.Path

/**
 * Unit test for BitmapFiltersGM
 */
class BitmapFiltersTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun testBitmapFiltersGM() {
        val gm = BitmapFiltersGM()
        
        // Verify GM properties
        assertEquals("bitmapfilters", gm.getName())
        assertEquals(com.kanvas.core.Size(540f, 250f), gm.getSize())
        
        // Test execution
        val result = gm.onDraw(com.kanvas.core.Canvas(BitmapDevice(540, 250)))
        assertEquals(DrawResult.OK, result)
        
        println("BitmapFiltersGM test passed")
    }
}