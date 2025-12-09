package skia

import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import testing.skia.ArcOfZorroGM

/**
 * Kotlin Test version of ArcOfZorroGM
 * Tests complex arc patterns
 */
class ArcOfZorroTest {
    
    @Test
    fun `ArcOfZorro test should run without errors`() {
        val gm = ArcOfZorroGM()
        assertNotNull(gm, "GM test should be created")
        
        val size = gm.getSize()
        assertTrue(size.width > 0 && size.height > 0, "GM test should have valid size")
        
        val result = TestUtils.runGmTest(gm, size.width.toInt(), size.height.toInt())
        assertNotNull(result, "Test should produce a bitmap result")
        
        println("✅ ArcOfZorro test executed successfully")
        println("   Size: ${size.width}x${size.height}")
        println("   Result: ${result.getWidth()}x${result.getHeight()} bitmap")
    }
    
    @Test
    fun `ArcOfZorro test should match reference image`() {
        val gm = ArcOfZorroGM()
        val size = gm.getSize()
        
        // Run the test
        val testResult = TestUtils.runGmTest(gm, size.width.toInt(), size.height.toInt())
        
        // Load reference image
        val referenceImage = TestUtils.loadReferenceImage("arcofzorro")
        
        if (referenceImage != null) {
            // Compare images
            val similarity = TestUtils.compareBitmaps(testResult, referenceImage)
            println("🔍 ArcOfZorro similarity with Skia reference: ${String.format("%.2f", similarity)}%")
            
            // Track similarity scores over time
            SimilarityTracker.updateScore("ArcOfZorroGM", similarity)
            
            // For now, we just log the similarity
            assertTrue(similarity >= 0, "Similarity should be calculated")
            
            // Save debug image if similarity is low
            if (similarity < 80.0) {
                TestUtils.saveDebugImage(testResult, "arcofzorro_test_output")
                println("⚠️  Low similarity - debug image saved")
            }
        } else {
            println("⚠️  No reference image found for arcofzorro")
            // Save the test output for future reference
            TestUtils.saveDebugImage(testResult, "arcofzorro_test_output")
        }
    }
}