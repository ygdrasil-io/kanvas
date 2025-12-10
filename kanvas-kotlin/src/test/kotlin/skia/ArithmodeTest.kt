package skia

import com.kanvas.core.CanvasFactory
import com.kanvas.examples.exportBitmapToPNG
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testing.DrawResult
import testing.skia.ArithmodeGM
import java.io.File
import kotlin.test.assertEquals

/**
 * Unit test for ArithmodeGM
 */
class ArithmodeTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `ArithmodeGM should draw successfully`() {
        val test = ArithmodeGM()
        val size = test.getSize()
        val canvas = CanvasFactory.createRaster(size.width.toInt(), size.height.toInt())

        val result = test.draw(canvas)

        assertEquals(DrawResult.OK, result, "ArithmodeGM should return OK")

        // Export for visual inspection
        val outputFile = File(tempDir, "arithmode_test_output.png")
        val bitmap = canvas.getBitmapCopy()
        exportBitmapToPNG(bitmap, outputFile.absolutePath)

        println("ArithmodeGM test output saved to: ${outputFile.absolutePath}")
    }
}