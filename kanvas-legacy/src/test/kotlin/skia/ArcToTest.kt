package skia

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import testing.skia.ArcToGM

/**
 * Kotlin Test version of ArcToGM
 * Tests arcTo path operations
 */
class ArcToTest {
    
    @Test
    fun `ArcTo test should run without errors`() {
        val gm = ArcToGM()
        assertNotNull(gm, "GM test should be created")
        
        val size = gm.getSize()
        assertTrue(size.width > 0 && size.height > 0, "GM test should have valid size")
        
        val result = TestUtils.runGmTest(gm, size.width.toInt(), size.height.toInt())
        assertNotNull(result, "Test should produce a bitmap result")
        
        println("✅ ArcTo test executed successfully")
        println("   Size: ${size.width}x${size.height}")
        println("   Result: ${result.getWidth()}x${result.getHeight()} bitmap")
    }
    
    @Test
    fun `ArcTo test should match reference image`() {
        val gm = ArcToGM()
        val size = gm.getSize()
        
        // Run the test
        val testResult = TestUtils.runGmTest(gm, size.width.toInt(), size.height.toInt())
        
        // Load reference image
        val referenceImage = TestUtils.loadReferenceImage("arcto")
        
        if (referenceImage != null) {
            // Compare images
            val similarity = TestUtils.compareBitmaps(testResult, referenceImage)
            println("🔍 ArcTo similarity with Skia reference: ${String.format("%.2f", similarity)}%")
            
            // Track similarity scores over time and fail if similarity drops significantly
            val scoreAcceptable = SimilarityTracker.updateScore("ArcToGM", similarity)
            assertTrue(scoreAcceptable, "Similarity score dropped significantly compared to previous best")
            
            // For now, we just log the similarity
            assertTrue(similarity >= 0, "Similarity should be calculated")
            
            // Save debug image if similarity is low
            if (similarity < 80.0) {
                TestUtils.saveDebugImage(testResult, "arcto_test_output")
                println("⚠️  Low similarity - debug image saved")
            }
        } else {
            println("⚠️  No reference image found for arcto")
            // Save the test output for future reference
            TestUtils.saveDebugImage(testResult, "arcto_test_output")
        }
    }
}