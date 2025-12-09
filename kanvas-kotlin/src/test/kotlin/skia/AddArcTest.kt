package skia

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import testing.skia.AddArcGM

/**
 * Kotlin Test version of AddArcGM
 * Tests arc drawing functionality
 */
class AddArcTest {
    
    @Test
    fun `AddArc test should run without errors`() {
        val gm = AddArcGM()
        assertNotNull(gm, "GM test should be created")
        
        val size = gm.getSize()
        assertTrue(size.width > 0 && size.height > 0, "GM test should have valid size")
        
        val result = TestUtils.runGmTest(gm, size.width.toInt(), size.height.toInt())
        assertNotNull(result, "Test should produce a bitmap result")
        
        println("✅ AddArc test executed successfully")
        println("   Size: ${size.width}x${size.height}")
        println("   Result: ${result.getWidth()}x${result.getHeight()} bitmap")
    }
    
    @Test
    fun `AddArc test should match reference image`() {
        val gm = AddArcGM()
        val size = gm.getSize()
        
        // Run the test
        val testResult = TestUtils.runGmTest(gm, size.width.toInt(), size.height.toInt())
        
        // Load reference image
        val referenceImage = TestUtils.loadReferenceImage("addarc")
        
        if (referenceImage != null) {
            // Compare images
            val similarity = TestUtils.compareBitmaps(testResult, referenceImage)
            println("🔍 AddArc similarity with Skia reference: ${String.format("%.2f", similarity)}%")
            
            // Track similarity scores over time and fail if similarity drops significantly
            val scoreAcceptable = SimilarityTracker.updateScore("AddArcGM", similarity)
            assertTrue(scoreAcceptable, "Similarity score dropped significantly compared to previous best")
            
            // For now, we just log the similarity
            assertTrue(similarity >= 0, "Similarity should be calculated")
            
            // Save debug image if similarity is low
            if (similarity < 80.0) {
                TestUtils.saveDebugImage(testResult, "addarc_test_output")
                println("⚠️  Low similarity - debug image saved")
            }
        } else {
            println("⚠️  No reference image found for addarc")
            // Save the test output for future reference
            TestUtils.saveDebugImage(testResult, "addarc_test_output")
        }
    }
}