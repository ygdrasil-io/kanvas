package testing

import testing.skia.AaClipGM
import testing.skia.AaRectModesGM
import testing.skia.AddArcGM

/**
 * Example demonstrating how to use the TestValidator to validate GM tests
 */
fun main() {
    println("🧪 Kanvas Test Validator Example")
    println("================================\n")
    
    // Create a list of tests to validate
    val testsToValidate = listOf(
        AaClipGM(),
        AaRectModesGM(),
        AddArcGM(),
        BasicShapesGM(),
        simpleRectTest,
        gradientTest,
        ClippingTestGM(),
        TransformationTestGM()
    )
    
    println("Validating ${testsToValidate.size} tests...\n")
    
    // Run validation on all tests
    val validationResults = TestValidator.validateMultipleTests(testsToValidate)
    
    // Analyze and report results
    var passedCount = 0
    var failedCount = 0
    
    validationResults.forEach { result ->
        if (result.success) {
            passedCount++
            println("✅ ${result.testName}: ${result.message}")
        } else {
            failedCount++
            println("❌ ${result.testName}: ${result.message}")
            result.exception?.printStackTrace()
        }
    }
    
    println("\n📊 Validation Results:")
    println("   Passed: $passedCount")
    println("   Failed: $failedCount")
    println("   Total: ${validationResults.size}")
    
    if (failedCount > 0) {
        println("\n❌ Some tests failed validation!")
    } else {
        println("\n✅ All tests passed validation!")
    }
    
    // Example of comparing with reference images (if available)
    println("\n🔍 Reference Image Comparison Example:")
    
    val aaclipResult = validationResults.find { it.testName == "aaclip" }
    if (aaclipResult != null && aaclipResult.success) {
        // Check if we have a reference image (this would be from Skia's output)
        val referencePath = "int-result/8888/gm/aaclip.png"
        
        // For now, we'll skip the file check since we're simplifying
        // In a real implementation, you would check if the file exists
        println("   ℹ️  Visual comparison example (not fully implemented)")
        println("   To enable visual comparison, implement Bitmap.fromFile() and uncomment the comparison code")
        println("   Reference would be loaded from: $referencePath")
    }
    
    println("\n🎉 Test validation complete!")
}