package org.graphiks.kanvas.gpu.renderer.runtimeeffects

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class GpuEffectKindsTest {

    @Test
    fun `Blender ClipShader and Compute kinds are valid enum values`() {
        val kinds = GPURuntimeEffectKind.entries
        assertTrue(GPURuntimeEffectKind.Blender in kinds)
        assertTrue(GPURuntimeEffectKind.ClipShader in kinds)
        assertTrue(GPURuntimeEffectKind.Compute in kinds)
        assertTrue(GPURuntimeEffectKind.Material in kinds)
        assertEquals(4, kinds.size)
    }

    @Test
    fun `kind contract defines required capabilities for Blender kind`() {
        val contract = GPURuntimeEffectKindContract(
            kind = GPURuntimeEffectKind.Blender,
            entryPointSignature = "fn blend(src: float4, dst: float4) -> float4",
            routePlacement = GPURuntimeEffectRoutePlacement.MaterialBlender,
        )
        assertEquals(GPURuntimeEffectKind.Blender, contract.kind)
        assertNotNull(contract.entryPointSignature)
        assertTrue(contract.requiredCapabilities.contains("premultiplied_input"))
        assertTrue(contract.requiredCapabilities.contains("premultiplied_output"))
    }

    @Test
    fun `kind contract defines required capabilities for ClipShader kind`() {
        val contract = GPURuntimeEffectKindContract(
            kind = GPURuntimeEffectKind.ClipShader,
            entryPointSignature = "fn clipShader(coords: float2, uniformBlock: U) -> float",
            routePlacement = GPURuntimeEffectRoutePlacement.ClipShader,
        )
        assertTrue(contract.requiredCapabilities.contains("coverage_float_output"))
    }

    @Test
    fun `kind contract defines required capabilities for Compute kind`() {
        val contract = GPURuntimeEffectKindContract(
            kind = GPURuntimeEffectKind.Compute,
            entryPointSignature = "@compute @workgroup_size(...) fn computeMain(...)",
            routePlacement = GPURuntimeEffectRoutePlacement.FilterComputeNode,
        )
        assertTrue(contract.requiredCapabilities.contains("storage_buffer_io"))
        assertTrue(contract.requiredCapabilities.contains("compute_dispatch"))
    }

    @Test
    fun `kind validation accepts registered kind`() {
        val result = DefaultGPURuntimeEffectKindValidator.validate(
            kind = GPURuntimeEffectKind.Blender,
            acceptedKinds = setOf(GPURuntimeEffectKind.Blender, GPURuntimeEffectKind.Material),
        )
        assertTrue(result is GPURuntimeEffectKindResult.Accepted)
    }

    @Test
    fun `kind validation refuses unregistered kind with diagnostic code`() {
        val result = DefaultGPURuntimeEffectKindValidator.validate(
            kind = GPURuntimeEffectKind.ClipShader,
            acceptedKinds = setOf(GPURuntimeEffectKind.Blender),
        )
        assertEquals("unsupported.runtime_effect.kind_not_registered", (result as GPURuntimeEffectKindResult.Refused).diagnosticCode)
    }

    @Test
    fun `kind validation accepts all four known kinds`() {
        val acceptedKinds = setOf(
            GPURuntimeEffectKind.Material,
            GPURuntimeEffectKind.Blender,
            GPURuntimeEffectKind.ClipShader,
            GPURuntimeEffectKind.Compute,
        )
        for (kind in GPURuntimeEffectKind.entries) {
            assertTrue(DefaultGPURuntimeEffectKindValidator.validate(kind, acceptedKinds) is GPURuntimeEffectKindResult.Accepted)
        }
    }

    @Test
    fun `kind contract associates correct route placement with each kind`() {
        assertEquals(
            GPURuntimeEffectRoutePlacement.MaterialSource,
            GPURuntimeEffectKindContract(
                GPURuntimeEffectKind.Material,
                "fn main(coords: float2, uniformBlock: U) -> float4",
                GPURuntimeEffectRoutePlacement.MaterialSource,
            ).routePlacement,
        )
        assertEquals(
            GPURuntimeEffectRoutePlacement.MaterialBlender,
            GPURuntimeEffectKindContract(
                GPURuntimeEffectKind.Blender,
                "fn blend(src: float4, dst: float4) -> float4",
                GPURuntimeEffectRoutePlacement.MaterialBlender,
            ).routePlacement,
        )
        assertEquals(
            GPURuntimeEffectRoutePlacement.ClipShader,
            GPURuntimeEffectKindContract(
                GPURuntimeEffectKind.ClipShader,
                "fn clipShader(coords: float2, uniformBlock: U) -> float",
                GPURuntimeEffectRoutePlacement.ClipShader,
            ).routePlacement,
        )
        assertEquals(
            GPURuntimeEffectRoutePlacement.FilterComputeNode,
            GPURuntimeEffectKindContract(
                GPURuntimeEffectKind.Compute,
                "@compute @workgroup_size(...) fn computeMain(...)",
                GPURuntimeEffectRoutePlacement.FilterComputeNode,
            ).routePlacement,
        )
    }
}
