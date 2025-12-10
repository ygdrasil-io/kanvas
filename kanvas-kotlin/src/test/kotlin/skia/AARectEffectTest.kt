package skia

import com.kanvas.core.CanvasFactory
import com.kanvas.examples.exportBitmapToPNG
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testing.DrawResult
import testing.skia.AARectEffectGM
import java.io.File
import kotlin.test.assertEquals

/**
 * Unit test for AARectEffectGM
 * Tests anti-aliased rectangle drawing with different edge conditions
 */
class AARectEffectTest {

    @TempDir
    lateinit var tempDir: File

    @Test
    fun `AARectEffectGM should draw successfully`() {
        val test = AARectEffectGM()
        val size = test.getSize()
        val canvas = CanvasFactory.createRaster(size.width.toInt(), size.height.toInt())

        val result = test.draw(canvas)

        assertEquals(DrawResult.OK, result, "AARectEffectGM should return OK")

        // Export for visual inspection
        val outputFile = File(tempDir, "aa_rect_effect_test_output.png")
        val bitmap = canvas.getBitmapCopy()
        exportBitmapToPNG(bitmap, outputFile.absolutePath)

        println("AARectEffectGM test output saved to: ${outputFile.absolutePath}")
    }
}