package skia

import com.kanvas.core.CanvasFactory
import com.kanvas.examples.exportBitmapToPNG
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testing.DrawResult
import testing.skia.BitmapCopyGM
import java.io.File
import kotlin.test.assertEquals

/**
 * Unit test for BitmapCopyGM
 */
class BitmapCopyTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `BitmapCopyGM should draw successfully`() {
        val test = BitmapCopyGM()
        val size = test.getSize()
        val canvas = CanvasFactory.createRaster(size.width.toInt(), size.height.toInt())

        val result = test.draw(canvas)

        assertEquals(DrawResult.OK, result, "BitmapCopyGM should return OK")

        // Export for visual inspection
        val outputFile = File(tempDir, "bitmapcopy_test_output.png")
        val bitmap = canvas.getBitmapCopy()
        exportBitmapToPNG(bitmap, outputFile.absolutePath)

        println("BitmapCopyGM test output saved to: ${outputFile.absolutePath}")
    }
}