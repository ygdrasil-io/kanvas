package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimpleSaveLayerImageFilterSceneEvidenceTest {
    @Test
    fun `simple saveLayer color filter emits reference cpu gpu diff stats and route diagnostics`() {
        val evidence = SimpleSaveLayerImageFilterSceneEvidence.capture(writeArtifacts = true)

        assertEquals("save-layer.image-filter.color-filter-matrix.v1", evidence.sceneId)
        assertEquals("kan-007.save-layer.simple-color-filter.v1", evidence.scopeId)
        assertEquals("ColorFilter", evidence.imageFilterKind)
        assertEquals("Matrix", evidence.colorFilterKind)
        assertEquals(2_560, evidence.layerPixelCount)
        assertEquals("cpu.save-layer.image-filter.color-filter-matrix", evidence.cpuRouteIdentifier)
        assertEquals("webgpu.image-filter.color-filter.layer-composite", evidence.webGpuRouteIdentifier)
        assertEquals("none", evidence.cpuFallbackReason)
        assertEquals("none", evidence.webGpuFallbackReason)
        assertEquals("webgpu.image-filter.color-filter.layer-composite", evidence.routeDiagnostics.selectedRoute)
        assertEquals(null, evidence.routeDiagnostics.prepassRoute)
        assertEquals(null, evidence.routeDiagnostics.scratchOwner)
        assertEquals(null, evidence.routeDiagnostics.scratchLifetime)
        assertEquals(0, evidence.routeDiagnostics.materialiseStages)
        assertEquals(null, evidence.routeDiagnostics.fallbackReason)
        assertTrue(evidence.cpuComparison.similarity >= evidence.cpuSimilarityThreshold)
        assertTrue(evidence.webGpuComparison.similarity >= evidence.webGpuSimilarityThreshold)
        assertEquals(evidence.layerPixelCount, evidence.cpuNonBackgroundPixels)
        assertEquals(evidence.layerPixelCount, evidence.webGpuNonBackgroundPixels)
        assertTrue(evidence.artifacts.referencePng.isFile)
        assertTrue(evidence.artifacts.cpuPng.isFile)
        assertTrue(evidence.artifacts.webGpuPng.isFile)
        assertTrue(evidence.artifacts.cpuDiffPng.isFile)
        assertTrue(evidence.artifacts.webGpuDiffPng.isFile)
        assertTrue(evidence.artifacts.routeCpuJson.isFile)
        assertTrue(evidence.artifacts.routeWebGpuJson.isFile)
        assertTrue(evidence.artifacts.statsJson.isFile)

        val routeGpu = evidence.artifacts.routeWebGpuJson.readText()
        assertTrue(routeGpu.contains("\"selectedRoute\": \"webgpu.image-filter.color-filter.layer-composite\""))
        assertTrue(routeGpu.contains("\"drawKind\": \"SkCanvas.saveLayer\""))
        assertTrue(routeGpu.contains("\"imageFilterKind\": \"ColorFilter\""))
        assertTrue(routeGpu.contains("\"colorFilterKind\": \"Matrix\""))
        assertTrue(routeGpu.contains("\"prepassRoute\": null"))
        assertTrue(routeGpu.contains("\"materialiseStages\": 0"))
        assertTrue(routeGpu.contains("\"fallbackReason\": \"none\""))
        assertTrue(routeGpu.contains("\"supportScope\": \"bounded-saveLayer-colorFilter-matrix-input-null\""))
        assertTrue(routeGpu.contains("no-arbitrary-layer-stack-claim"))
        assertTrue(routeGpu.contains("no-multi-node-dag-claim"))
        assertTrue(routeGpu.contains("no-cpu-readback-fallback-claim"))
        assertFalse(routeGpu.contains("\"supportClaim\": \"broad-image-filter\""))

        val stats = evidence.artifacts.statsJson.readText()
        assertTrue(stats.contains("\"referenceArtifact\""))
        assertTrue(stats.contains("\"cpuArtifact\""))
        assertTrue(stats.contains("\"webGpuArtifact\""))
        assertTrue(stats.contains("\"routeWebGpuArtifact\""))
        assertTrue(stats.contains("\"globalThresholdChanged\": false"))
        assertTrue(stats.contains("\"layerPixelCount\": 2560"))
        assertNotNull(evidence.webGpuAdapter)
    }
}
