package testing

import com.kanvas.core.Color
import com.kanvas.core.Canvas

/**
 * Global test registry that automatically registers GM tests.
 * This provides a simple way to register tests without manual registration.
 */
object TestRegistry {
    private val tests = mutableListOf<GM>()
    
    /**
     * Register a GM test
     */
    fun register(test: GM) {
        tests.add(test)
    }
    
    /**
     * Get all registered tests
     */
    fun getAllTests(): List<GM> = tests.toList()
    
    /**
     * Clear all registered tests (useful for testing)
     */
    fun clear() {
        tests.clear()
    }
}

/**
 * Helper function to register a GM test globally
 */
fun registerGM(test: GM) {
    TestRegistry.register(test)
}

/**
 * Helper function to create and register a simple GM test
 */
fun registerSimpleGM(
    name: String,
    width: Int,
    height: Int,
    backgroundColor: Color = Color.WHITE,
    drawProc: (Canvas) -> Unit
) {
    TestRegistry.register(simpleGM(name, width, height, backgroundColor, drawProc))
}