package org.graphiks.kanvas.glyph.gpu

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class GPUTextArtifactRegistryTest {
    @Test
    fun `default registry dumps all target artifact descriptors in deterministic order`() {
        val registry = defaultTextGPUArtifactRegistry()

        val descriptorNames = registry.descriptors.map { it.artifactName }

        assertEquals(
            listOf(
                "GlyphAtlasArtifact",
                "SDFGlyphAtlasArtifact",
                "GlyphUploadPlan",
                "OutlineGlyphPlan",
                "ColorGlyphPlan",
                "BitmapGlyphPlan",
                "SVGGlyphPlan",
            ),
            descriptorNames,
        )

        val json = registry.toCanonicalJson()
        assertEquals(json, defaultTextGPUArtifactRegistry().toCanonicalJson())
        assertContains(json, """"schema":"org.graphiks.kanvas.glyph.gpu.TextGPUArtifactRegistry.v1"""")
        assertContains(json, """"artifactName":"GlyphAtlasArtifact"""")
        assertContains(json, """"supportedRoutes":["AtlasMaskSample"]""")
        assertContains(json, """"missingDiagnostic":"unsupported.text.artifact_unregistered"""")
        assertContains(json, """"productActivation":false""")

        listOf("SkFont", "SkTypeface", "SkTextBlob", "SkPaint", "fontBytes", "GPUHandle").forEach { token ->
            assertFalse(json.contains(token), "Registry dump leaked forbidden token $token: $json")
        }
    }

    @Test
    fun `unregistered artifact refusal emits handoff and renderer diagnostics`() {
        val refusal = defaultTextGPUArtifactRegistry().refuseUnregistered(
            typeName = "HostTypefaceGlyphBlob",
            artifactHash = "sha256:host-blob",
        )

        assertEquals("text.gpu.artifact-unregistered", refusal.handoffDiagnostic)
        assertEquals("unsupported.text.artifact_unregistered", refusal.rendererDiagnostic)
        assertEquals("HostTypefaceGlyphBlob", refusal.artifactName)
        assertEquals("sha256:host-blob", refusal.artifactHash)
        assertContains(refusal.toCanonicalJson(), """"claimPromotionAllowed":false""")
    }
}
