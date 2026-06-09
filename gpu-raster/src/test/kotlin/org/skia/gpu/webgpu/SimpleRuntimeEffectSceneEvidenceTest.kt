package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimpleRuntimeEffectSceneEvidenceTest {
    @Test
    fun `simple rt emits bounded descriptor cpu gpu diff stats and route diagnostics`() {
        val evidence = SimpleRuntimeEffectSceneEvidence.capture(writeArtifacts = true)

        assertEquals("runtime.simple_rt.descriptor.rect.v1", evidence.sceneId)
        assertEquals("runtime-effect.simple-rt.registered-descriptor.rect.v1", evidence.scopeId)
        assertEquals("runtime.simple_rt", evidence.runtimeEffectStableId)
        assertEquals("kotlin/simple_rt", evidence.cpuImplementationId)
        assertEquals("wgsl/runtime_simple_rt", evidence.wgslImplementationId)
        assertEquals("cpu.runtime-effect.descriptor.simple_rt", evidence.cpuRouteIdentifier)
        assertEquals("webgpu.runtime-effect.descriptor.simple_rt", evidence.webGpuRouteIdentifier)
        assertEquals("none", evidence.cpuFallbackReason)
        assertEquals("none", evidence.webGpuFallbackReason)
        assertEquals("gColor", evidence.uniformName)
        assertEquals(0, evidence.uniformOffset)
        assertEquals(16, evidence.uniformBytes)
        assertTrue(evidence.runtimeWgslValidated)
        assertTrue(evidence.cpuComparison.similarity >= evidence.cpuSimilarityThreshold)
        assertTrue(evidence.webGpuComparison.similarity >= evidence.webGpuSimilarityThreshold)
        assertTrue(evidence.nonBackgroundPixels > 3_000)
        assertTrue(evidence.artifacts.referencePng.isFile)
        assertTrue(evidence.artifacts.cpuPng.isFile)
        assertTrue(evidence.artifacts.webGpuPng.isFile)
        assertTrue(evidence.artifacts.cpuDiffPng.isFile)
        assertTrue(evidence.artifacts.webGpuDiffPng.isFile)
        assertTrue(evidence.artifacts.routeCpuJson.isFile)
        assertTrue(evidence.artifacts.routeWebGpuJson.isFile)
        assertTrue(evidence.artifacts.statsJson.isFile)

        val routeGpu = evidence.artifacts.routeWebGpuJson.readText()
        assertTrue(routeGpu.contains("\"selectedRoute\": \"webgpu.runtime-effect.descriptor.simple_rt\""))
        assertTrue(routeGpu.contains("\"fallbackReason\": \"none\""))
        assertTrue(routeGpu.contains("\"runtimeEffectStableId\": \"runtime.simple_rt\""))
        assertTrue(routeGpu.contains("\"wgslImplementationId\": \"wgsl/runtime_simple_rt\""))
        assertTrue(routeGpu.contains("\"uniformLayout\": {"))
        assertTrue(routeGpu.contains("\"gColor\": 0"))
        assertTrue(routeGpu.contains("\"runtimeWgslValidated\": true"))
        assertTrue(routeGpu.contains("runtime-effect.wgsl-descriptor-missing"))
        assertTrue(routeGpu.contains("runtime-effect.arbitrary-sksl-unsupported"))
        assertTrue(routeGpu.contains("no-dynamic-sksl-compilation-claim"))
        assertTrue(routeGpu.contains("no-broad-runtime-effect-claim"))

        val stats = evidence.artifacts.statsJson.readText()
        assertTrue(stats.contains("\"referenceArtifact\""))
        assertTrue(stats.contains("\"cpuArtifact\""))
        assertTrue(stats.contains("\"webGpuArtifact\""))
        assertTrue(stats.contains("\"globalThresholdChanged\": false"))
        assertTrue(stats.contains("\"globalColorPolicyChanged\": false"))
        assertTrue(stats.contains("\"descriptorBacked\": true"))
        assertTrue(stats.contains("\"parserReflected\": true"))
        assertTrue(stats.contains("\"similarityThreshold\": 99.95"))
        assertNotNull(evidence.webGpuAdapter)
    }
}
