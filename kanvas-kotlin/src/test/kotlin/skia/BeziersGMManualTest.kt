package skia

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import testing.TestRunner
import testing.skia.BeziersGM
import java.io.File

class BeziersGMManualTest {
    
    @TempDir
    lateinit var tempDir: File
    
    @Test
    fun `BeziersGM should execute via TestRunner`() {
        val runner = TestRunner()
        runner.setOutputDir(tempDir.absolutePath)
        runner.setVerbose(true)
        
        // Register only BeziersGM for this test
        runner.register(BeziersGM())
        
        // Run the test
        runner.runAll()
        
        // Check that the output file was created
        val outputFile = File(tempDir, "beziers.png")
        assert(outputFile.exists()) { "BeziersGM output file should exist" }
        
        println("BeziersGM executed successfully via TestRunner")
        println("Output file: ${outputFile.absolutePath}")
    }
}