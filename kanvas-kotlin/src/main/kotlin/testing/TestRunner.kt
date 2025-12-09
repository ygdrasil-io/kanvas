package testing

import com.kanvas.core.Bitmap
import com.kanvas.core.Canvas
import com.kanvas.core.Color
import com.kanvas.core.Paint
import com.kanvas.core.Path
import com.kanvas.core.Rect
import com.kanvas.examples.exportBitmapToPNG
import testing.Size
import java.io.File

/**
 * TestRunner is responsible for executing Skia GM tests and managing their output.
 * Focused on running ported Skia tests to validate Kanvas implementation.
 */
class TestRunner {
    private val tests = mutableListOf<GM>()
    private var outputDir: String = "test_output"
    private var verbose: Boolean = false
    
    /**
     * Register a GM test with the runner
     */
    fun register(test: GM) {
        tests.add(test)
    }
    
    /**
     * Set the output directory for test results
     */
    fun setOutputDir(dir: String) {
        this.outputDir = dir
    }
    
    /**
     * Enable verbose output
     */
    fun setVerbose(verbose: Boolean) {
        this.verbose = verbose
    }
    
    /**
     * Run all registered tests
     */
    fun runAll() {
        // Create output directory if it doesn't exist
        File(outputDir).mkdirs()
        
        println("Running ${tests.size} Skia GM tests...")
        
        var passed = 0
        var failed = 0
        var skipped = 0
        
        tests.forEach { test ->
            if (verbose) {
                println("🧪 Running test: ${test.getName()}")
            }
            
            val result = runTest(test)
            
            when (result) {
                DrawResult.OK -> {
                    passed++
                    println("✅ ${test.getName()}: PASSED")
                }
                DrawResult.FAIL -> {
                    failed++
                    println("❌ ${test.getName()}: FAILED")
                }
                DrawResult.SKIP -> {
                    skipped++
                    println("⏭️  ${test.getName()}: SKIPPED")
                }
            }
        }
        
        println("\n📊 Test Results:")
        println("   Passed: $passed")
        println("   Failed: $failed")
        println("   Skipped: $skipped")
        println("   Total: ${tests.size}")
        
        if (failed > 0) {
            println("\n❌ Some tests failed!")
        } else {
            println("\n✅ All tests passed!")
        }
    }
    
    /**
     * Run a single test and return the result
     */
    private fun runTest(test: GM): DrawResult {
        try {
            val size = test.getSize()
            val canvas = Canvas.createRaster(size.width.toInt(), size.height.toInt())
            
            val result = test.draw(canvas)
            
            if (result == DrawResult.OK) {
                // Export the result as PNG
                val filename = "$outputDir/${test.getName()}.png"
                val bitmap = canvas.getBitmap()
                exportBitmapToPNG(bitmap, filename)
                if (verbose) {
                    println("   💾 Exported to: $filename")
                }
            }
            
            return result
            
        } catch (e: Exception) {
            println("   💥 Exception in ${test.getName()}: ${e.message}")
            return DrawResult.FAIL
        }
    }
    
    /**
     * Run tests matching a specific pattern
     */
    fun runMatching(pattern: String) {
        val matchingTests = tests.filter { it.getName().contains(pattern, ignoreCase = true) }
        if (matchingTests.isEmpty()) {
            println("❌ No tests matching pattern: $pattern")
            return
        }
        
        println("Running ${matchingTests.size} tests matching '$pattern'...")
        
        val originalTests = tests.toList()
        tests.clear()
        tests.addAll(matchingTests)
        runAll()
        tests.clear()
        tests.addAll(originalTests)
    }
    
    /**
     * Get the list of all registered tests
     */
    fun getTestList(): List<String> = tests.map { it.getName() }
}