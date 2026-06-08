package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

class SimpleLatinLineSceneEvidenceTest {
    @Test
    fun `simple latin line emits reference cpu gpu diff stats and route diagnostics`() {
        val evidence = SimpleLatinLineSceneEvidence.capture(writeArtifacts = true)

        assertEquals("text.simple-latin.line.v1", evidence.sceneId)
        assertEquals("text.simple-latin.liberation-sans-regular.v1", evidence.scopeId)
        assertEquals("Liberation Sans", evidence.fontFamily)
        assertEquals(SimpleLatinLineSceneEvidence.LineText, evidence.text)
        assertEquals("font.glyph.outline-path", evidence.glyphRoute)
        assertEquals("webgpu.text.outline-path.simple-latin", evidence.webGpuRouteIdentifier)
        assertEquals("webgpu.text.glyph-atlas.simple-latin", evidence.atlas.routeIdentifier)
        assertEquals(12_928, evidence.atlas.uploadByteCount)
        assertEquals("none", evidence.cpuFallbackReason)
        assertEquals("none", evidence.webGpuFallbackReason)
        assertEquals(TestUtils.TEXTUAL_GM_TOLERANCE, evidence.tolerance)
        assertTrue(evidence.cpuComparison.similarity >= evidence.cpuSimilarityThreshold)
        assertTrue(evidence.webGpuComparison.similarity >= evidence.webGpuSimilarityThreshold)
        assertTrue(evidence.cpuNonWhitePixels > 300)
        assertTrue(evidence.webGpuNonWhitePixels > 300)
        assertTrue(evidence.artifacts.referencePng.isFile)
        assertTrue(evidence.artifacts.cpuPng.isFile)
        assertTrue(evidence.artifacts.webGpuPng.isFile)
        assertTrue(evidence.artifacts.cpuDiffPng.isFile)
        assertTrue(evidence.artifacts.webGpuDiffPng.isFile)
        assertTrue(evidence.artifacts.routeCpuJson.isFile)
        assertTrue(evidence.artifacts.routeWebGpuJson.isFile)
        assertTrue(evidence.artifacts.statsJson.isFile)
        assertTrue(evidence.artifacts.atlasJson.isFile)

        val routeGpu = evidence.artifacts.routeWebGpuJson.readText()
        assertTrue(routeGpu.contains("\"selectedRoute\": \"webgpu.text.outline-path.simple-latin\""))
        assertTrue(routeGpu.contains("\"fallbackReason\": \"none\""))
        assertTrue(routeGpu.contains("\"atlasRouteIdentifier\": \"webgpu.text.glyph-atlas.simple-latin\""))
        assertTrue(routeGpu.contains("no-shaping-claim"))
        assertTrue(routeGpu.contains("no-fallback-font-claim"))
        assertTrue(routeGpu.contains("no-emoji-or-color-font-claim"))
        assertTrue(routeGpu.contains("no-sdf-or-lcd-claim"))
        assertFalse(routeGpu.contains("\"supportClaim\": \"broad-text\""))

        val stats = evidence.artifacts.statsJson.readText()
        assertTrue(stats.contains("\"referenceArtifact\""))
        assertTrue(stats.contains("\"cpuArtifact\""))
        assertTrue(stats.contains("\"webGpuArtifact\""))
        assertTrue(stats.contains("\"globalThresholdChanged\": false"))
        assertTrue(stats.contains("\"shapingMode\": \"simple-codepoint-order\""))
        assertNotNull(evidence.webGpuAdapter)
    }
}
