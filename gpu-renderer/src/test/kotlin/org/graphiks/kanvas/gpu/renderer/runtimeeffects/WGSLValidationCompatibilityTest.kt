package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

@Suppress("DEPRECATION")
class WGSLValidationCompatibilityTest {
    @Test
    fun `legacy runtimeeffects WGSL names retain the generic service behavior`() {
        val validator: WGSLValidator = KanvasWGSLValidator()
        val parsed: WGSLParsedModule = validator.parse(
            "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }",
        )
        val reflectionProvider: WGSLReflectionProvider = KanvasWGSLReflectionProvider()
        val reflection: WGSLReflectionResult = reflectionProvider.reflect(parsed)

        assertTrue(parsed.syntaxErrors.isEmpty())
        assertEquals("main", reflection.entryPoint)
        assertTrue(reflection.reflectionHash.isNotBlank())
    }
}
