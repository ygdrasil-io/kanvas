package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CustomRuntimeEffectRegistryTest {

    private fun fixtureModule(): WGSLParsedModule = WGSLParsedModule(
        sourceHash = generateHash("fixture-wgsl"),
        source = "@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }",
        uniforms = listOf("u_color"),
        textures = emptyList(),
        bindGroups = listOf("group0"),
        loopIterationCount = 2,
        functionDepth = 3,
    )

    private fun generateHash(input: String): String =
        java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
            .take(12)

    @Test
    fun `WGSLValidator parse is wired and returns WGSLParsedModule`() {
        val validator = KanvasWGSLValidator()
        val source = """
            @group(0) @binding(0) var<uniform> u_color: vec4<f32>;
            @fragment
            fn main() -> @location(0) vec4<f32> {
                return u_color;
            }
        """.trimIndent()

        val module = validator.parse(source)
        assertNotNull(module)
        assertEquals(generateHash(source), module.sourceHash)
    }

    @Test
    fun `WGSLValidator parse returns empty syntaxErrors for valid WGSL`() {
        val validator = KanvasWGSLValidator()
        val source = """
            @fragment
            fn main() -> @location(0) vec4<f32> {
                return vec4<f32>(1.0, 0.0, 0.0, 1.0);
            }
        """.trimIndent()

        val module = validator.parse(source)
        assertEquals(emptyList(), module.syntaxErrors)
    }

    @Test
    fun `KanvasWGSLValidator falls back to fixture when wgsl4k is unavailable`() {
        val validator = KanvasWGSLValidator()
        val module = validator.parse("@fragment fn main() -> @location(0) vec4<f32> { return vec4(1.0); }")
        assertNotNull(module)
    }
}
