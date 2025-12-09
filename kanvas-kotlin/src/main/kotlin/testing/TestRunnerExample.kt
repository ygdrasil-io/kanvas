package testing

import testing.skia.AaClipGM
import testing.skia.AaRectModesGM
import testing.skia.AddArcGM
import testing.skia.AlphaImageGM
import testing.skia.AlphaGradientsGM
import testing.skia.ArcOfZorroGM
import testing.skia.ArcToGM
import testing.skia.BigRectGM
import testing.skia.GradientGM

/**
 * Kanvas Skia GM Test Runner
 * Focused on running Skia ported tests and validating Kanvas implementation
 */
fun main() {
    println("🎨 Kanvas Skia GM Tests")
    println("=======================\n")
    
    // Create a test runner focused on Skia tests
    val runner = TestRunner()
    runner.setOutputDir("gm_test_output")
    runner.setVerbose(true)
    
    // Register Skia ported tests
    println("📋 Registering Skia GM tests:")
    runner.register(AaClipGM())
    println("  ✓ AaClipGM - Anti-aliased clipping test")
    
    runner.register(AaRectModesGM())
    println("  ✓ AaRectModesGM - Anti-aliased rectangle modes test")
    
    runner.register(AddArcGM())
    println("  ✓ AddArcGM - Arc drawing test")
    
    runner.register(AlphaImageGM())
    println("  ✓ AlphaImageGM - Alpha channel image test")
    
    runner.register(AlphaGradientsGM())
    println("  ✓ AlphaGradientsGM - Alpha gradients test")
    
    runner.register(ArcOfZorroGM())
    println("  ✓ ArcOfZorroGM - Complex arc patterns test")
    
    runner.register(ArcToGM())
    println("  ✓ ArcToGM - ArcTo path operations test")
    
    runner.register(BigRectGM())
    println("  ✓ BigRectGM - Large rectangle performance test")
    
    runner.register(GradientGM())
    println("  ✓ GradientGM - Gradient drawing test")
    
    println("\n📊 Total tests registered: ${runner.getTestList().size}")
    println()
    
    // Run all Skia tests
    println("🚀 Running Skia GM tests...\n")
    runner.runAll()
    
    println("\n🎉 Skia GM test run complete!")
    println("📁 Results saved in 'gm_test_output/' directory")
    println("💡 Compare with Skia reference images in 'int-result/8888/gm/'")
}