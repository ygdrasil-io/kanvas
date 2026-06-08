package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimpleLinearGradientSceneEvidenceTest {
    @Test
    fun `simple linear gradient emits reference cpu gpu diff stats and route diagnostics`() {
        val evidence = SimpleLinearGradientSceneEvidence.capture(writeArtifacts = true)

        assertEquals("paint.linear-gradient.rect.v1", evidence.sceneId)
        assertEquals("linear-gradient.clamp.rect.simple.v1", evidence.scopeId)
        assertEquals("kClamp", evidence.tileMode)
        assertEquals("cpu.paint.linear-gradient.rect.clamp", evidence.cpuRouteIdentifier)
        assertEquals("webgpu.generated.linear-gradient.rect", evidence.webGpuRouteIdentifier)
        assertEquals("none", evidence.cpuFallbackReason)
        assertEquals("none", evidence.webGpuFallbackReason)
        assertTrue(evidence.generatedWgslValidated)
        assertTrue(evidence.cpuComparison.similarity >= evidence.cpuSimilarityThreshold)
        assertTrue(evidence.webGpuComparison.similarity >= evidence.webGpuSimilarityThreshold)
        assertTrue(evidence.cpuNonWhitePixels > 1_000)
        assertTrue(evidence.webGpuNonWhitePixels > 1_000)
        assertTrue(evidence.artifacts.referencePng.isFile)
        assertTrue(evidence.artifacts.cpuPng.isFile)
        assertTrue(evidence.artifacts.webGpuPng.isFile)
        assertTrue(evidence.artifacts.cpuDiffPng.isFile)
        assertTrue(evidence.artifacts.webGpuDiffPng.isFile)
        assertTrue(evidence.artifacts.routeCpuJson.isFile)
        assertTrue(evidence.artifacts.routeWebGpuJson.isFile)
        assertTrue(evidence.artifacts.statsJson.isFile)

        val routeGpu = evidence.artifacts.routeWebGpuJson.readText()
        assertTrue(routeGpu.contains("\"selectedRoute\": \"webgpu.generated.linear-gradient.rect\""))
        assertTrue(routeGpu.contains("\"fallbackReason\": \"none\""))
        assertTrue(routeGpu.contains("\"tileMode\": \"kClamp\""))
        assertTrue(routeGpu.contains("\"generatedWgslValidated\": true"))
        assertTrue(routeGpu.contains("no-wide-gamut-color-management-claim"))
        assertTrue(routeGpu.contains("no-all-tile-modes-claim"))
        assertTrue(routeGpu.contains("no-gradient-mesh-claim"))
        assertTrue(routeGpu.contains("no-advanced-color-space-claim"))
        assertTrue(routeGpu.contains("no-broad-gradient-family-claim"))
        assertFalse(routeGpu.contains("\"supportClaim\": \"broad-gradient\""))

        val stats = evidence.artifacts.statsJson.readText()
        assertTrue(stats.contains("\"referenceArtifact\""))
        assertTrue(stats.contains("\"cpuArtifact\""))
        assertTrue(stats.contains("\"webGpuArtifact\""))
        assertTrue(stats.contains("\"globalThresholdChanged\": false"))
        assertTrue(stats.contains("\"colorSpacePolicy\": \"srgb-unmanaged-test-oracle\""))
        assertNotNull(evidence.webGpuAdapter)
    }
}
