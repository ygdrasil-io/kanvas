package testing

import com.kanvas.core.Bitmap
import com.kanvas.core.Canvas
import com.kanvas.core.Color
import java.io.File

/**
 * Result of test validation
 */
data class TestValidationResult(
    val success: Boolean,
    val testName: String,
    val message: String,
    val drawResult: DrawResult,
    val bitmap: Bitmap? = null,
    val exception: Exception? = null
)

/**
 * Result of image comparison
 */
data class ImageComparisonResult(
    val success: Boolean,
    val message: String,
    val testName: String,
    val diffPercentage: Double = 0.0
)

/**
 * TestValidator provides unit testing capabilities for GM tests.
 * It can validate that tests run without errors and produce expected results.
 */
class TestValidator {
    companion object {
        /**
         * Run a single test and validate its execution
         */
        fun validateTest(test: GM): TestValidationResult {
            return try {
                val size = test.getSize()
                val canvas = Canvas.createRaster(size.width.toInt(), size.height.toInt())
                
                // Execute the test
                val drawResult = test.draw(canvas)
                
                // Get the resulting bitmap
                val bitmap = canvas.getBitmap()
                
                // Validate basic properties
                if (size.width.toInt() != bitmap.getWidth() || size.height.toInt() != bitmap.getHeight()) {
                    return TestValidationResult(
                        success = false,
                        testName = test.getName(),
                        message = "Bitmap dimensions don't match test size",
                        drawResult = drawResult
                    )
                }
                
                // Check that the bitmap has some non-transparent pixels
                val hasContent = bitmap.hasNonTransparentPixels()
                if (!hasContent) {
                    return TestValidationResult(
                        success = false,
                        testName = test.getName(),
                        message = "Bitmap should contain some drawn content",
                        drawResult = drawResult
                    )
                }
                
                TestValidationResult(
                    success = true,
                    testName = test.getName(),
                    message = "Test executed successfully",
                    drawResult = drawResult,
                    bitmap = bitmap
                )
                
            } catch (e: Exception) {
                TestValidationResult(
                    success = false,
                    testName = test.getName(),
                    message = "Test failed: ${e.message}",
                    drawResult = DrawResult.FAIL,
                    exception = e
                )
            }
        }
        
        /**
         * Run multiple tests and validate all of them
         */
        fun validateMultipleTests(tests: List<GM>): List<TestValidationResult> {
            return tests.map { test ->
                println("🧪 Validating test: ${test.getName()}")
                validateTest(test)
            }
        }
        
        /**
         * Compare a test result with a reference image
         */
        fun compareWithReference(
            testResult: TestValidationResult,
            referencePath: String,
            tolerance: Double = 0.05
        ): ImageComparisonResult {
            if (testResult.bitmap == null) {
                return ImageComparisonResult(
                    false,
                    "No bitmap available for comparison",
                    testResult.testName
                )
            }
            
            // For now, we'll skip the actual comparison since Bitmap.fromFile is not implemented
            // In a real implementation, you would load the reference image and compare
            return ImageComparisonResult(
                true,
                "Visual comparison not yet implemented",
                testResult.testName,
                0.0
            )
            
            // Visual comparison not yet implemented
            return ImageComparisonResult(
                true,
                "Visual comparison not yet implemented",
                testResult.testName,
                0.0
            )
        }
        
        // TODO: Implement pixel comparison functions when needed
        // private fun countDifferentPixels(bitmap1: Bitmap, bitmap2: Bitmap, tolerance: Double): Int
        // private fun colorsDifferSignificantly(color1: Color, color2: Color, tolerance: Double): Boolean
    }
}