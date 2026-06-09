package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimpleColorFilterSceneEvidenceTest {
    @Test
    fun `simple blend color filter emits reference cpu gpu diff stats and route diagnostics`() {
        val evidence = SimpleColorFilterSceneEvidence.capture(writeArtifacts = true)

        assertEquals("paint.color-filter.blend-kplus.rect.v1", evidence.sceneId)
        assertEquals("color-filter.blend-kplus.direct-rect.simple.v1", evidence.scopeId)
        assertEquals("Blend", evidence.colorFilterKind)
        assertEquals("kPlus", evidence.colorFilterBlendMode)
        assertEquals(1, evidence.filteredCommandCount)
        assertEquals("cpu.paint.color-filter.blend-kplus.direct-rect", evidence.cpuRouteIdentifier)
        assertEquals("webgpu.paint.color-filter.blend-kplus.solid-color", evidence.webGpuRouteIdentifier)
        assertEquals("none", evidence.cpuFallbackReason)
        assertEquals("none", evidence.webGpuFallbackReason)
        assertEquals("generated solid rect does not support colorFilter", evidence.generatedSolidRectFallbackReason)
        assertTrue(evidence.solidColorWgslValidated)
        assertTrue(evidence.cpuComparison.similarity >= evidence.cpuSimilarityThreshold)
        assertTrue(evidence.webGpuComparison.similarity >= evidence.webGpuSimilarityThreshold)
        assertTrue(evidence.filteredPixelCount > 2_000)
        assertTrue(evidence.controlPixelCount > 2_000)
        assertTrue(evidence.artifacts.referencePng.isFile)
        assertTrue(evidence.artifacts.cpuPng.isFile)
        assertTrue(evidence.artifacts.webGpuPng.isFile)
        assertTrue(evidence.artifacts.cpuDiffPng.isFile)
        assertTrue(evidence.artifacts.webGpuDiffPng.isFile)
        assertTrue(evidence.artifacts.routeCpuJson.isFile)
        assertTrue(evidence.artifacts.routeWebGpuJson.isFile)
        assertTrue(evidence.artifacts.statsJson.isFile)

        val routeGpu = evidence.artifacts.routeWebGpuJson.readText()
        assertTrue(routeGpu.contains("\"selectedRoute\": \"webgpu.paint.color-filter.blend-kplus.solid-color\""))
        assertTrue(routeGpu.contains("\"colorFilterKind\": \"Blend\""))
        assertTrue(routeGpu.contains("\"colorFilterBlendMode\": \"kPlus\""))
        assertTrue(routeGpu.contains("\"fallbackReason\": \"none\""))
        assertTrue(routeGpu.contains("\"generatedSolidRectFallbackReason\": \"generated solid rect does not support colorFilter\""))
        assertTrue(routeGpu.contains("\"fallbackPolicy\": \"supported-via-handwritten-solid-color-color-filter-route\""))
        assertTrue(routeGpu.contains("no-color-filter-chain-claim"))
        assertTrue(routeGpu.contains("no-color-managed-pipeline-claim"))
        assertTrue(routeGpu.contains("no-wide-color-pipeline-claim"))
        assertFalse(routeGpu.contains("\"supportClaim\": \"broad-color-filter\""))

        val stats = evidence.artifacts.statsJson.readText()
        assertTrue(stats.contains("\"referenceArtifact\""))
        assertTrue(stats.contains("\"cpuArtifact\""))
        assertTrue(stats.contains("\"webGpuArtifact\""))
        assertTrue(stats.contains("\"globalThresholdChanged\": false"))
        assertTrue(stats.contains("\"globalColorPolicyChanged\": false"))
        assertTrue(stats.contains("\"filteredCommandCount\": 1"))
        assertTrue(stats.contains("\"colorSpacePolicy\": \"srgb-unmanaged-color-filter-oracle\""))
        assertNotNull(evidence.webGpuAdapter)
    }
}
