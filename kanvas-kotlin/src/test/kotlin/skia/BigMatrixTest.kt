package skia

import com.kanvas.core.CanvasFactory
import com.kanvas.examples.exportBitmapToPNG
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testing.DrawResult
import testing.skia.BigMatrixGM
import java.io.File
import kotlin.test.assertEquals

class BigMatrixTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `BigMatrixGM should draw successfully`() {
        val test = BigMatrixGM()
        val size = test.getSize()
        val canvas = CanvasFactory.createRaster(size.width.toInt(), size.height.toInt())

        val result = test.draw(canvas)

        assertEquals(DrawResult.OK, result, "BigMatrixGM should return OK")

        // Export for visual inspection
        val outputFile = File(tempDir, "bigmatrix_test_output.png")
        val bitmap = canvas.getBitmapCopy()
        exportBitmapToPNG(bitmap, outputFile.absolutePath)

        println("BigMatrixGM test output saved to: ${outputFile.absolutePath}")
    }
}