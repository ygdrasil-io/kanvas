package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimpleBitmapRectSceneEvidenceTest {
    @Test
    fun `simple bitmap rect emits reference cpu gpu diff stats and route diagnostics`() {
        val evidence = SimpleBitmapRectSceneEvidence.capture(writeArtifacts = true)

        assertEquals("paint.bitmap-rect.nearest.fixture.v1", evidence.sceneId)
        assertEquals("bitmap-rect.nearest.fixture.simple.v1", evidence.scopeId)
        assertEquals("kanvas-fixture-checker-8x6-rgba8888-v1", evidence.fixtureId)
        assertEquals("nearest", evidence.sampler)
        assertEquals("kClamp", evidence.tileMode)
        assertEquals("kStrict", evidence.srcRectConstraint)
        assertEquals("kSrcOver", evidence.blendMode)
        assertEquals("cpu.paint.bitmap-rect.nearest.fixture", evidence.cpuRouteIdentifier)
        assertEquals("webgpu.image.bitmap-rect.nearest.fixture", evidence.webGpuRouteIdentifier)
        assertEquals("none", evidence.cpuFallbackReason)
        assertEquals("none", evidence.webGpuFallbackReason)
        assertTrue(evidence.cpuComparison.similarity >= evidence.cpuSimilarityThreshold)
        assertTrue(evidence.webGpuComparison.similarity >= evidence.webGpuSimilarityThreshold)
        assertTrue(evidence.cpuSampledPixels > 1_000)
        assertTrue(evidence.webGpuSampledPixels > 1_000)
        assertTrue(evidence.artifacts.referencePng.isFile)
        assertTrue(evidence.artifacts.cpuPng.isFile)
        assertTrue(evidence.artifacts.webGpuPng.isFile)
        assertTrue(evidence.artifacts.cpuDiffPng.isFile)
        assertTrue(evidence.artifacts.webGpuDiffPng.isFile)
        assertTrue(evidence.artifacts.routeCpuJson.isFile)
        assertTrue(evidence.artifacts.routeWebGpuJson.isFile)
        assertTrue(evidence.artifacts.statsJson.isFile)

        val routeGpu = evidence.artifacts.routeWebGpuJson.readText()
        assertTrue(routeGpu.contains("\"selectedRoute\": \"webgpu.image.bitmap-rect.nearest.fixture\""))
        assertTrue(routeGpu.contains("\"fallbackReason\": \"none\""))
        assertTrue(routeGpu.contains("\"sampler\": \"nearest\""))
        assertTrue(routeGpu.contains("\"tileMode\": \"kClamp\""))
        assertTrue(routeGpu.contains("\"srcRectConstraint\": \"kStrict\""))
        assertTrue(routeGpu.contains("\"fixtureId\": \"kanvas-fixture-checker-8x6-rgba8888-v1\""))
        assertTrue(routeGpu.contains("no-broad-image-claim"))
        assertTrue(routeGpu.contains("no-codec-decode-claim"))
        assertTrue(routeGpu.contains("no-arbitrary-texture-claim"))
        assertTrue(routeGpu.contains("no-mipmap-claim"))
        assertTrue(routeGpu.contains("no-tile-mode-claim"))
        assertTrue(routeGpu.contains("no-color-managed-decode-claim"))
        assertFalse(routeGpu.contains("\"supportClaim\": \"broad-image\""))

        val stats = evidence.artifacts.statsJson.readText()
        assertTrue(stats.contains("\"referenceArtifact\""))
        assertTrue(stats.contains("\"cpuArtifact\""))
        assertTrue(stats.contains("\"webGpuArtifact\""))
        assertTrue(stats.contains("\"globalThresholdChanged\": false"))
        assertTrue(stats.contains("\"fixtureBacked\": true"))
        assertTrue(stats.contains("\"colorSpacePolicy\": \"srgb-unmanaged-fixture-oracle\""))
        assertNotNull(evidence.webGpuAdapter)
    }
}
