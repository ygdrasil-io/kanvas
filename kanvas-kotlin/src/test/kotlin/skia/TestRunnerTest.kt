package skia

import org.junit.jupiter.api.Test
import testing.TestRunner
import testing.registerAllTests

class TestRunnerTest {
    
    @Test
    fun `TestRunner should execute all registered tests`() {
        val runner = TestRunner()
        runner.setOutputDir("build/test-output")
        runner.setVerbose(true)
        
        // Register all tests
        registerAllTests()
        
        // Run all tests
        runner.runAll()
    }
}