package testing

import testing.skia.AaClipGM
import testing.skia.AaRectModesGM
import testing.skia.AddArcGM

/**
 * Example demonstrating how to use the Kanvas GM test framework.
 * This shows the basic workflow for running GM tests.
 */
fun main() {
    println("🚀 Kanvas GM Test Framework Example")
    println("===================================\n")
    
    // Create a test runner
    val runner = TestRunner()
    runner.setOutputDir("gm_test_output")
    runner.setVerbose(true)
    
    // Register some example tests
    runner.register(BasicShapesGM())
    runner.register(simpleRectTest)
    runner.register(gradientTest)
    runner.register(ClippingTestGM())
    runner.register(TransformationTestGM())
    
    // Register Skia ported tests
    runner.register(AaClipGM())
    runner.register(AaRectModesGM())
    runner.register(AddArcGM())
    
    println("Registered ${runner.getTestList().size} tests:")
    runner.getTestList().forEach { testName ->
        println("  - $testName")
    }
    println()
    
    // Run all tests
    runner.runAll()
    
    println("\n🎉 Test run complete!")
    println("Check the 'gm_test_output' directory for PNG results.")
}