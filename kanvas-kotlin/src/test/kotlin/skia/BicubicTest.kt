package skia

import device.BitmapDevice
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testing.DrawResult
import testing.skia.BicubicGM
import java.nio.file.Path

/**
 * Unit test for BicubicGM
 */
class BicubicTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun testBicubicGM() {
        val gm = BicubicGM()
        
        // Verify basic properties
        assertEquals("bicubic", gm.getName())
        assertEquals(com.kanvas.core.Size(300f, 320f), gm.getSize())

        // Test execution
        val result = gm.onDraw(com.kanvas.core.Canvas(BitmapDevice(300, 320)))
        assertEquals(DrawResult.OK, result)
    }
}