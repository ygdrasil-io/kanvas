package skia

import com.kanvas.core.CanvasFactory
import com.kanvas.examples.exportBitmapToPNG
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testing.DrawResult
import testing.skia.AttributesGM
import java.io.File
import kotlin.test.assertEquals

/**
 * Unit test for AttributesGM
 */
class AttributesTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `AttributesGM should draw successfully`() {
        val test = AttributesGM()
        val size = test.getSize()
        val canvas = CanvasFactory.createRaster(size.width.toInt(), size.height.toInt())

        val result = test.draw(canvas)

        assertEquals(DrawResult.OK, result, "AttributesGM should return OK")

        // Export for visual inspection
        val outputFile = File(tempDir, "attributes_test_output.png")
        val bitmap = canvas.getBitmapCopy()
        exportBitmapToPNG(bitmap, outputFile.absolutePath)

        println("AttributesGM test output saved to: ${outputFile.absolutePath}")
    }
}