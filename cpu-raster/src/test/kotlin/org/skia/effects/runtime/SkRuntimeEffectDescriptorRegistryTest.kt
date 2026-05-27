package org.skia.effects.runtime

import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
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

        assertEquals(listOf("runtime.a", "runtime.z"), entries.map { it.stableId })
    }

    @Test
    fun `support matrix contains SimpleRT CPU and GPU status`() {
        SkBuiltinShaderEffectsSimple.registerAll()

        val entry = SkRuntimeEffectDescriptorRegistry.supportMatrixEntries()
            .single { it.stableId == "runtime.simple_rt" }

        assertEquals(3617365546103039931L, entry.canonicalHash)
        assertEquals("runtime.simple_rt", entry.stableId)
        assertEquals("descriptor-backed", entry.descriptorStatus)
        assertEquals("supported:kotlin/simple_rt", entry.cpuSupport)
        assertEquals("supported:wgsl/runtime_simple_rt", entry.gpuSupport)
        assertEquals("none", entry.missingReason)
    }

    @Test
    fun `support matrix reports dispatch-only builtins as missing descriptors`() {
        SkBuiltinShaderEffectsSimple.registerAll()

        val entries = SkRuntimeEffectDescriptorRegistry.supportMatrixEntries()

        assertEquals(
            listOf("runtime.linear_gradient_rt", "runtime.simple_rt", "runtime.spiral_rt"),
            entries.map { it.stableId },
        )
        val linearGradient = entries.single { it.stableId == "runtime.linear_gradient_rt" }
        assertEquals("dispatch-only; missing descriptor", linearGradient.descriptorStatus)
        assertEquals("supported:kotlin/linear_gradient_rt", linearGradient.cpuSupport)
        assertEquals("unsupported: WGSL implementation id missing", linearGradient.gpuSupport)
        assertEquals(
            "Runtime effect descriptor missing for dispatch-only effect: ${linearGradient.canonicalHash}",
            linearGradient.missingReason,
        )

        val spiral = entries.single { it.stableId == "runtime.spiral_rt" }
        assertEquals("dispatch-only; missing descriptor", spiral.descriptorStatus)
        assertEquals("supported:kotlin/spiral_rt", spiral.cpuSupport)
        assertTrue(spiral.uniforms.map { it.name }.containsAll(listOf("rad_scale", "in_center", "in_colors0", "in_colors1")))
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
        assertTrue(first.contains("descriptor-backed"))
        assertTrue(first.contains("dispatch-only; missing descriptor"))
        assertTrue(first.contains("Runtime effect descriptor missing for dispatch-only effect:"))
    }

    @Test
    fun `CPU-only descriptor is valid and exports GPU unsupported`() {
        SkRuntimeEffectDescriptorRegistry.register(
            "half4 main(vec2 p) { return vec4(0); }",
            descriptor("runtime.cpu_only"),
        )

        val entry = SkRuntimeEffectDescriptorRegistry.supportMatrixEntries().single()

        assertEquals("unsupported: WGSL implementation id missing", entry.gpuSupport)
        assertTrue(
            SkRuntimeEffectDescriptorRegistry.exportSupportMatrixMarkdown()
                .contains("unsupported: WGSL implementation id missing"),
        )
    }

    @Test
    fun `blank WGSL implementation id fails registration`() {
        val source = "half4 main(vec2 p) { return vec4(0); }"
        val error = assertThrows(IllegalStateException::class.java) {
            SkRuntimeEffectDescriptorRegistry.register(
                source,
                descriptor("runtime.blank_wgsl", wgslImplementationId = " "),
            )
        }
        assertEquals(
            "Invalid runtime effect descriptor WGSL implementation id: " +
                "canonicalHash=${SkRuntimeEffectDispatch.canonicalHash(source)} " +
                "stableId=runtime.blank_wgsl wgslImplementationId= ",
            error.message,
        )
    }

    @Test
    fun `invalid stable id fails registration`() {
        val source = "half4 main(vec2 p) { return vec4(0); }"
        val error = assertThrows(IllegalStateException::class.java) {
            SkRuntimeEffectDescriptorRegistry.register(source, descriptor("Runtime.Bad"))
        }
        assertEquals(
            "Invalid runtime effect descriptor stableId: " +
                "canonicalHash=${SkRuntimeEffectDispatch.canonicalHash(source)} stableId=Runtime.Bad",
            error.message,
        )
    }

    @Test
    fun `missing CPU implementation id fails registration`() {
        val source = "half4 main(vec2 p) { return vec4(0); }"
        val error = assertThrows(IllegalStateException::class.java) {
            SkRuntimeEffectDescriptorRegistry.register(
                source,
                descriptor("runtime.missing_cpu").copy(cpuImplementationId = ""),
            )
        }
        assertEquals(
            "Invalid runtime effect descriptor CPU implementation id: " +
                "canonicalHash=${SkRuntimeEffectDispatch.canonicalHash(source)} " +
                "stableId=runtime.missing_cpu cpuImplementationId=",
            error.message,
        )
    }

    @Test
    fun `unknown WGSL implementation id fails without parser evidence`() {
        val source = "half4 main(vec2 p) { return vec4(0); }"
        val error = assertThrows(IllegalStateException::class.java) {
            SkRuntimeEffectDescriptorRegistry.register(
                source,
                descriptor("runtime.unknown_wgsl", wgslImplementationId = "wgsl/unknown_rt"),
            )
        }
        assertEquals(
            "Runtime effect descriptor WGSL evidence missing: " +
                "canonicalHash=${SkRuntimeEffectDispatch.canonicalHash(source)} " +
                "stableId=runtime.unknown_wgsl wgslImplementationId=wgsl/unknown_rt",
            error.message,
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
