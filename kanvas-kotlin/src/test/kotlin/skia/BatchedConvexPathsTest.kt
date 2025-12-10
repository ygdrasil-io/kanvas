package skia

import com.kanvas.core.CanvasFactory
import com.kanvas.examples.exportBitmapToPNG
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testing.DrawResult
import testing.skia.BatchedConvexPathsGM
import java.io.File
import kotlin.test.assertEquals

class BatchedConvexPathsTest {
    
    @TempDir
    lateinit var tempDir: File
    
    @Test
    fun `BatchedConvexPathsGM should draw successfully`() {
        val test = BatchedConvexPathsGM()
        val size = test.getSize()
        val canvas = CanvasFactory.createRaster(size.width.toInt(), size.height.toInt())
        
        val result = test.draw(canvas)
        
        assertEquals(DrawResult.OK, result, "BatchedConvexPathsGM should return OK")
        
        // Export for visual inspection
        val outputFile = File(tempDir, "batchedconvexpaths_test_output.png")
        val bitmap = canvas.getBitmapCopy()
        exportBitmapToPNG(bitmap, outputFile.absolutePath)
        
        println("BatchedConvexPathsGM test output saved to: ${outputFile.absolutePath}")
    }
}