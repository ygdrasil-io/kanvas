package skia

import com.kanvas.core.Bitmap
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import testing.skia.AaClipGM

/**
 * Kotlin Test version of AaClipGM
 * Tests anti-aliased clipping functionality
 */
class AaClipTest {
    
    @Test
    fun `AaClip test should run without errors`() {
        val gm = AaClipGM()
        assertNotNull(gm, "GM test should be created")
        
        val size = gm.getSize()
        assertTrue(size.width > 0 && size.height > 0, "GM test should have valid size")
        
        val result = TestUtils.runGmTest(gm, size.width.toInt(), size.height.toInt())
        assertNotNull(result, "Test should produce a bitmap result")
        
        println("✅ AaClip test executed successfully")
        println("   Size: ${size.width}x${size.height}")
        println("   Result: ${result.getWidth()}x${result.getHeight()} bitmap")
    }
    
    @Test
    fun `AaClip test should match reference image`() {
        val gm = AaClipGM()
        val size = gm.getSize()
        
        // Run the test
        val testResult = TestUtils.runGmTest(gm, size.width.toInt(), size.height.toInt())
        
        // Load reference image
        val referenceImage = TestUtils.loadReferenceImage("aaclip")
        
        if (referenceImage != null) {
            // Compare images
            val similarity = TestUtils.compareBitmaps(testResult, referenceImage)
            println("🔍 AaClip similarity with Skia reference: ${String.format("%.2f", similarity)}%")
            
            // Track similarity scores over time and fail if similarity drops significantly
            val scoreAcceptable = SimilarityTracker.updateScore("AaClipGM", similarity)

            // Save debug image if similarity is low
            if (similarity < 98.0) {
                TestUtils.saveDebugImage(testResult, "aaclip_test_output")
                println("⚠️  Low similarity - debug image saved")
            }

            assertTrue(scoreAcceptable, "Similarity score dropped significantly compared to previous best")
        } else {
            println("⚠️  No reference image found for aaclip")
            // Save the test output for future reference
            TestUtils.saveDebugImage(testResult, "aaclip_test_output")
        }
    }
}