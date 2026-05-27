package org.skia.effects.runtime

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsSimple

class SkRuntimeEffectDescriptorRegistryTest {
    @AfterEach
    fun cleanup() {
        SkRuntimeEffectDispatch.clearForTest()
    }

    @Test
    fun `support matrix entries are sorted by stable id then canonical hash`() {
        SkRuntimeEffectDescriptorRegistry.register("half4 main(vec2 p) { return vec4(0); }", descriptor("runtime.z"))
        SkRuntimeEffectDescriptorRegistry.register("half4 main(vec2 p) { return vec4(1); }", descriptor("runtime.a"))

        val entries = SkRuntimeEffectDescriptorRegistry.supportMatrixEntries()

        assertEquals(listOf("runtime.a", "runtime.z"), entries.map { it.descriptor.stableId })
    }

    @Test
    fun `support matrix contains SimpleRT CPU and GPU status`() {
        SkBuiltinShaderEffectsSimple.registerAll()

        val entry = SkRuntimeEffectDescriptorRegistry.supportMatrixEntries().single()

        assertEquals(3617365546103039931L, entry.canonicalHash)
        assertEquals("runtime.simple_rt", entry.descriptor.stableId)
        assertEquals("supported:kotlin/simple_rt", entry.cpuSupport)
        assertEquals("supported:wgsl/runtime_simple_rt", entry.gpuSupport)
        assertEquals(
            "Runtime effect descriptor not registered: ${entry.canonicalHash}",
            entry.missingDiagnostic,
        )
    }

    @Test
    fun `markdown export is deterministic and includes fallback fields`() {
        SkBuiltinShaderEffectsSimple.registerAll()

        val first = SkRuntimeEffectDescriptorRegistry.exportSupportMatrixMarkdown()
        val second = SkRuntimeEffectDescriptorRegistry.exportSupportMatrixMarkdown()

        assertEquals(first, second)
        assertTrue(first.contains("| runtime.simple_rt |"))
        assertTrue(first.contains("gColor:kFloat4"))
        assertTrue(first.contains("supported:kotlin/simple_rt"))
        assertTrue(first.contains("supported:wgsl/runtime_simple_rt"))
        assertTrue(first.contains("Runtime effect descriptor not registered:"))
    }

    @Test
    fun `blank WGSL implementation id is exported as unsupported`() {
        SkRuntimeEffectDescriptorRegistry.register(
            "half4 main(vec2 p) { return vec4(0); }",
            descriptor("runtime.blank-wgsl", wgslImplementationId = " "),
        )

        val entry = SkRuntimeEffectDescriptorRegistry.supportMatrixEntries().single()

        assertEquals("unsupported: WGSL implementation id missing", entry.gpuSupport)
        assertTrue(
            SkRuntimeEffectDescriptorRegistry.exportSupportMatrixMarkdown()
                .contains("unsupported: WGSL implementation id missing"),
        )
    }

    private fun descriptor(stableId: String): SkRuntimeEffectDescriptor =
        SkRuntimeEffectDescriptor(
            stableId = stableId,
            kind = SkRuntimeEffect.Kind.kShader,
            uniforms = emptyList(),
            children = emptyList(),
            flags = 0,
            cpuImplementationId = "kotlin/test",
            wgslImplementationId = null,
        )

    private fun descriptor(
        stableId: String,
        wgslImplementationId: String?,
    ): SkRuntimeEffectDescriptor =
        descriptor(stableId).copy(wgslImplementationId = wgslImplementationId)
}
