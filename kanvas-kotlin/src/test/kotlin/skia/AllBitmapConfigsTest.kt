package skia

import com.kanvas.core.CanvasFactory
import com.kanvas.examples.exportBitmapToPNG
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testing.DrawResult
import testing.skia.AllBitmapConfigsGM
import java.io.File
import kotlin.test.assertEquals

/**
 * Unit test for AllBitmapConfigsGM
 */
class AllBitmapConfigsTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `AllBitmapConfigsGM should draw successfully`() {
        val test = AllBitmapConfigsGM()
        val size = test.getSize()
        val canvas = CanvasFactory.createRaster(size.width.toInt(), size.height.toInt())

        val result = test.draw(canvas)

        assertEquals(DrawResult.OK, result, "AllBitmapConfigsGM should return OK")

        // Export for visual inspection
        val outputFile = File(tempDir, "all_bitmap_configs_test_output.png")
        val bitmap = canvas.getBitmapCopy()
        exportBitmapToPNG(bitmap, outputFile.absolutePath)

        println("AllBitmapConfigsGM test output saved to: ${outputFile.absolutePath}")
    }
}