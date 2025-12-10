package skia

import com.kanvas.core.CanvasFactory
import com.kanvas.examples.exportBitmapToPNG
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testing.DrawResult
import testing.skia.BeziersGM
import java.io.File
import kotlin.test.assertEquals

class BeziersTest {
    
    @TempDir
    lateinit var tempDir: File
    
    @Test
    fun `BeziersGM should draw successfully`() {
        val test = BeziersGM()
        val size = test.getSize()
        val canvas = CanvasFactory.createRaster(size.width.toInt(), size.height.toInt())
        
        val result = test.draw(canvas)
        
        assertEquals(DrawResult.OK, result, "BeziersGM should return OK")
        
        // Export for visual inspection
        val outputFile = File(tempDir, "beziers_test_output.png")
        val bitmap = canvas.getBitmapCopy()
        exportBitmapToPNG(bitmap, outputFile.absolutePath)
        
        println("BeziersGM test output saved to: ${outputFile.absolutePath}")
    }
}