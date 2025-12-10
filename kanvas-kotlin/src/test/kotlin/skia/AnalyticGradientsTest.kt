package skia

import com.kanvas.core.CanvasFactory
import com.kanvas.examples.exportBitmapToPNG
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testing.DrawResult
import testing.skia.AnalyticGradientsGM
import java.io.File
import kotlin.test.assertEquals

/**
 * Unit test for AnalyticGradientsGM
 * Tests analytic gradient calculations with various interpolation intervals
 */
class AnalyticGradientsTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `AnalyticGradientsGM should draw successfully`() {
        val test = AnalyticGradientsGM()
        val size = test.getSize()
        val canvas = CanvasFactory.createRaster(size.width.toInt(), size.height.toInt())

        val result = test.draw(canvas)

        assertEquals(DrawResult.OK, result, "AnalyticGradientsGM should return OK")

        // Export for visual inspection
        val outputFile = File(tempDir, "analytic_gradients_test_output.png")
        val bitmap = canvas.getBitmapCopy()
        exportBitmapToPNG(bitmap, outputFile.absolutePath)

        println("AnalyticGradientsGM test output saved to: ${outputFile.absolutePath}")
    }
}