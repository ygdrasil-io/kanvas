package skia

import com.kanvas.core.CanvasFactory
import com.kanvas.examples.exportBitmapToPNG
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testing.DrawResult
import testing.skia.ComplexPathsGM
import java.io.File
import kotlin.test.assertEquals

class ComplexPathsTest {
    
    @TempDir
    lateinit var tempDir: File
    
    @Test
    fun `ComplexPathsGM should draw successfully`() {
        val test = ComplexPathsGM()
        val size = test.getSize()
        val canvas = CanvasFactory.createRaster(size.width.toInt(), size.height.toInt())
        
        val result = test.draw(canvas)
        
        assertEquals(DrawResult.OK, result, "ComplexPathsGM should return OK")
        
        // Export for visual inspection
        val outputFile = File(tempDir, "complexpaths_test_output.png")
        val bitmap = canvas.getBitmapCopy()
        exportBitmapToPNG(bitmap, outputFile.absolutePath)
        
        println("ComplexPathsGM test output saved to: ${outputFile.absolutePath}")
    }
}