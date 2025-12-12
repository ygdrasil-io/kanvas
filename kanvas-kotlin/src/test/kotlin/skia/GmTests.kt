package skia

import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import testing.TestRegistry
import testing.registerAllTests

/**
 * Kotest-based GM tests for Skia functionality
 * This file combines all GM tests into a single Kotest specification
 */
class GmTests : FreeSpec({

    "Skia GM Tests " - {
        registerAllTests()
        TestRegistry.getAllTests()
            .forEach { test ->
                "${test.getName()}" - {

                    val size = test.getSize()

                    "should run without errors" {

                        val result = TestUtils.runGmTest(test, size.width.toInt(), size.height.toInt())
                        result shouldNotBe null

                        size.width shouldBe result.getWidth()
                        size.height shouldBe result.getHeight()

                        println("✅ ${test.getName()} executed successfully")
                        println("   Size: ${size.width}x${size.height}")
                        println("   Result: ${result.getWidth()}x${result.getHeight()} bitmap")
                    }

                    "should match reference image" {

                        val testResult = TestUtils.runGmTest(test, size.width.toInt(), size.height.toInt())

                        // Load reference image
                        val referenceName = test.getName()

                        val referenceImage = TestUtils.loadReferenceImage(referenceName)

                        if (referenceImage != null) {
                            // Compare images
                            val similarity = TestUtils.compareBitmaps(testResult, referenceImage)
                            println("🔍 ${test.getName()}similarity with Skia reference: ${String.format("%.2f", similarity)}%")

                            // Track similarity scores over time
                            val scoreAcceptable = SimilarityTracker.updateScore(test.getName(), similarity)
                            scoreAcceptable shouldBe true

                            // Save debug image if similarity is low
                            if (similarity < 80.0) {
                                TestUtils.saveDebugImage(testResult, "${referenceName}_test_output")
                                println("⚠️  Low similarity - debug image saved")
                            }
                        } else {
                            println("⚠️  No reference image found for $referenceName")
                            // Save the test output for future reference
                            TestUtils.saveDebugImage(testResult, "${referenceName}_test_output")
                        }
                    }
                }
            }
    }

})