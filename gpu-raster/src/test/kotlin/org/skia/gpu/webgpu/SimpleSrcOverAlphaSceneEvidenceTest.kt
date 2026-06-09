package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SimpleSrcOverAlphaSceneEvidenceTest {
    @Test
    fun `simple src-over partial alpha emits reference cpu gpu diff stats and route diagnostics`() {
        val evidence = SimpleSrcOverAlphaSceneEvidence.capture(writeArtifacts = true)

        assertEquals("paint.src-over-alpha.rect-stack.v1", evidence.sceneId)
        assertEquals("src-over.partial-alpha.rect-stack.simple.v1", evidence.scopeId)
        assertEquals("kSrcOver", evidence.blendMode)
        assertEquals("partial", evidence.alphaPolicy)
        assertEquals(2, evidence.partialAlphaCommandCount)
        assertEquals("cpu.paint.src-over.partial-alpha.rect-stack", evidence.cpuRouteIdentifier)
        assertEquals("webgpu.blend.src-over.partial-alpha.fixed-function", evidence.webGpuRouteIdentifier)
        assertEquals("none", evidence.cpuFallbackReason)
        assertEquals("none", evidence.webGpuFallbackReason)
        assertEquals("blend.unsupported-mode.requires-explicit-allowlist", evidence.unsupportedBlendReason)
        assertTrue(evidence.generatedSolidRectWgslValidated)
        assertTrue(evidence.cpuComparison.similarity >= evidence.cpuSimilarityThreshold)
        assertTrue(evidence.webGpuComparison.similarity >= evidence.webGpuSimilarityThreshold)
        assertTrue(evidence.overlapPixelCount > 1_000)
        assertTrue(evidence.cpuNonBackgroundPixels > 1_000)
        assertTrue(evidence.webGpuNonBackgroundPixels > 1_000)
        assertTrue(evidence.artifacts.referencePng.isFile)
        assertTrue(evidence.artifacts.cpuPng.isFile)
        assertTrue(evidence.artifacts.webGpuPng.isFile)
        assertTrue(evidence.artifacts.cpuDiffPng.isFile)
        assertTrue(evidence.artifacts.webGpuDiffPng.isFile)
        assertTrue(evidence.artifacts.routeCpuJson.isFile)
        assertTrue(evidence.artifacts.routeWebGpuJson.isFile)
        assertTrue(evidence.artifacts.statsJson.isFile)

        val routeGpu = evidence.artifacts.routeWebGpuJson.readText()
        assertTrue(routeGpu.contains("\"selectedRoute\": \"webgpu.blend.src-over.partial-alpha.fixed-function\""))
        assertTrue(routeGpu.contains("\"blendMode\": \"kSrcOver\""))
        assertTrue(routeGpu.contains("\"blendPlan\": \"FixedFunction\""))
        assertTrue(routeGpu.contains("\"alphaPolicy\": \"partial\""))
        assertTrue(routeGpu.contains("\"fallbackReason\": \"none\""))
        assertTrue(routeGpu.contains("\"unsupportedBlendReason\": \"blend.unsupported-mode.requires-explicit-allowlist\""))
        assertFalse(routeGpu.contains("shaderFamily=linearGradient"))
        assertTrue(routeGpu.contains("no-arbitrary-blend-mode-claim"))
        assertTrue(routeGpu.contains("no-saveLayer-blend-composition-claim"))
        assertTrue(routeGpu.contains("no-shader-destination-read-claim"))
        assertTrue(routeGpu.contains("no-wide-color-pipeline-claim"))
        assertFalse(routeGpu.contains("\"supportClaim\": \"broad-blend\""))

        val stats = evidence.artifacts.statsJson.readText()
        assertTrue(stats.contains("\"referenceArtifact\""))
        assertTrue(stats.contains("\"cpuArtifact\""))
        assertTrue(stats.contains("\"webGpuArtifact\""))
        assertTrue(stats.contains("\"globalThresholdChanged\": false"))
        assertTrue(stats.contains("\"globalBlendPolicyChanged\": false"))
        assertTrue(stats.contains("\"partialAlphaCommandCount\": 2"))
        assertTrue(stats.contains("\"colorSpacePolicy\": \"srgb-unmanaged-src-over-oracle\""))
        assertNotNull(evidence.webGpuAdapter)
    }
}
