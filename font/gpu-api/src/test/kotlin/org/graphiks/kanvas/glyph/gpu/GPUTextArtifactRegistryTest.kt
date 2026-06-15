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
        assertEquals(listOf("AtlasMaskSample"), registry.descriptor("GlyphAtlasArtifact")?.supportedRoutes)
        descriptorNames
            .filterNot { descriptorName -> descriptorName == "GlyphAtlasArtifact" }
            .forEach { descriptorName ->
                assertEquals(emptyList(), registry.descriptor(descriptorName)?.supportedRoutes)
            }

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

    @Test
    fun `registry snapshots descriptor list fields at construction`() {
        val keyPreimageFields = mutableListOf("artifactID", "generation")
        val invalidationFacts = mutableListOf("generation")
        val supportedRoutes = mutableListOf("OriginalRoute")
        val registry = TextGPUArtifactRegistry(
            listOf(
                TextGPUArtifactDescriptor(
                    artifactName = "MutableArtifact",
                    descriptorVersion = 1,
                    ownerSubsystem = "test-text",
                    keyPreimageFields = keyPreimageFields,
                    lifetimeClass = "test-lifetime",
                    invalidationFacts = invalidationFacts,
                    memoryBudgetClass = "test-memory",
                    uploadBudgetClass = "test-upload",
                    supportedRoutes = supportedRoutes,
                    missingDiagnostic = "unsupported.text.artifact_unregistered",
                ),
            ),
        )
        val json = registry.toCanonicalJson()

        keyPreimageFields += "fontBytes"
        invalidationFacts += "staleGeneration"
        supportedRoutes += "InjectedRoute"

        assertEquals(json, registry.toCanonicalJson())
        assertEquals(listOf("artifactID", "generation"), registry.descriptor("MutableArtifact")?.keyPreimageFields)
        assertEquals(listOf("generation"), registry.descriptor("MutableArtifact")?.invalidationFacts)
        assertEquals(listOf("OriginalRoute"), registry.descriptor("MutableArtifact")?.supportedRoutes)

        listOf("fontBytes", "staleGeneration", "InjectedRoute").forEach { token ->
            assertFalse(registry.toCanonicalJson().contains(token), "Registry dump changed after source mutation: $token")
        }
    }
}
