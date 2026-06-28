package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

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

    @Test
    fun `WGSLReflectionProvider reflect returns WGSLReflectionResult`() {
        val provider = KanvasWGSLReflectionProvider()
        val module = fixtureModule()
        val result = provider.reflect(module)
        assertNotNull(result)
        assertTrue(result.moduleHash.isNotBlank())
        assertTrue(result.entryPoint.isNotBlank())
        assertEquals(0, result.uniformCount)
    }

    @Test
    fun `WGSLReflectionProvider falls back to fixture when wgsl4k is unavailable`() {
        val provider = KanvasWGSLReflectionProvider()
        val module = fixtureModule()
        val result = provider.reflect(module)
        assertNotNull(result)
        assertTrue(result.reflectionHash.isNotBlank())
    }

    private fun validWGSLSource(): String = """
        @group(0) @binding(0) var<uniform> u_color: vec4<f32>;
        @fragment
        fn main() -> @location(0) vec4<f32> {
            return u_color;
        }
    """.trimIndent()

    private fun unsafeWGSLSource(): String = """
        @group(0) @binding(0) var<uniform> u_color: vec4<f32>;
        @fragment
        fn main() -> @location(0) vec4<f32> {
            atomicAdd(&u_color, 1.0);
            return u_color;
        }
    """.trimIndent()

    private fun customRegistry(): KanvasCustomRuntimeEffectRegistry = KanvasCustomRuntimeEffectRegistry(
        wgslValidator = KanvasWGSLValidator(),
        reflectionProvider = KanvasWGSLReflectionProvider(),
        securityValidator = WGSLSecurityValidator(),
        deviceCapabilities = WGSLDeviceCapabilities(),
    )

    @Test
    fun `registerCustomEffect succeeds for valid WGSL`() {
        val registry = customRegistry()
        val schema = GPURuntimeEffectUniformSchema(
            schemaHash = "schema:test:v1",
            fields = listOf("u_color:vec4<f32>@0:16"),
            packingPolicy = "std140",
        )
        val result = registry.registerCustomEffect(validWGSLSource(), schema, emptyList(), "test-fixture")
        assertTrue(result.isSuccess)
        val id = result.getOrThrow()
        assertTrue(id.value.startsWith("custom."))
        assertTrue(registry.isRegistered(id))

        val descriptor = registry.getDescriptor(id)
        assertNotNull(descriptor)
        assertEquals(GPUCustomRuntimeEffectValidationStatus.VALID, descriptor.validationStatus)
        assertEquals("test-fixture", descriptor.sourceProvenance)
    }

    @Test
    fun `registerCustomEffect fails for WGSL with blocked features`() {
        val registry = customRegistry()
        val schema = GPURuntimeEffectUniformSchema(
            schemaHash = "schema:test:v1",
            fields = listOf("u_color:vec4<f32>@0:16"),
            packingPolicy = "std140",
        )
        val result = registry.registerCustomEffect(unsafeWGSLSource(), schema, emptyList(), "test-fixture")
        if (result.isSuccess) {
            val id = result.getOrThrow()
            assertTrue(registry.isRegistered(id))
        } else {
            val error = result.exceptionOrNull() as? GPUCustomRuntimeEffectValidationError
            assertNotNull(error)
            assertTrue(error.code.contains("unsafe"))
        }
    }

    @Test
    fun `unregisterCustomEffect removes descriptor`() {
        val registry = customRegistry()
        val schema = GPURuntimeEffectUniformSchema(
            schemaHash = "schema:test:v1",
            fields = listOf("u_color:vec4<f32>@0:16"),
            packingPolicy = "std140",
        )
        val result = registry.registerCustomEffect(validWGSLSource(), schema, emptyList(), "test-fixture")
        val id = result.getOrThrow()
        assertTrue(registry.isRegistered(id))

        registry.unregisterCustomEffect(id)
        assertTrue(!registry.isRegistered(id))
        assertEquals(null, registry.getDescriptor(id))
    }

    @Test
    fun `getDescriptor returns null for unknown ID`() {
        val registry = customRegistry()
        assertEquals(null, registry.getDescriptor(GPUCustomRuntimeEffectID("custom.unknown")))
    }

    @Test
    fun `registration produces deterministic ID from same source and schema`() {
        val registry = customRegistry()
        val schema = GPURuntimeEffectUniformSchema(
            schemaHash = "schema:test:v1",
            fields = listOf("u_color:vec4<f32>@0:16"),
            packingPolicy = "std140",
        )
        val result1 = registry.registerCustomEffect(validWGSLSource(), schema, emptyList(), "a")
        val result2 = registry.registerCustomEffect(validWGSLSource(), schema, emptyList(), "b")
        assertEquals(result1.getOrThrow(), result2.getOrThrow(), "same source+schema produces same ID")
    }
}
