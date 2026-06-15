package org.graphiks.kanvas.glyph.gpu

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

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
        assertContains(json, """"descriptorCompactHash":"${registry.descriptors[0].descriptorCompactHash}"""")
        assertContains(json, """"supportedRoutes":["AtlasMaskSample"]""")
        assertContains(json, """"missingDiagnostic":"unsupported.text.artifact_unregistered"""")
        assertContains(json, """"productActivation":false""")

        listOf("SkFont", "SkTypeface", "SkTextBlob", "SkPaint", "fontBytes", "GPUHandle").forEach { token ->
            assertFalse(json.contains(token), "Registry dump leaked forbidden token $token: $json")
        }
    }

    @Test
    fun `default registry descriptors pass no Sk leakage report deterministically`() {
        val registry = defaultTextGPUArtifactRegistry()
        val report = registry.noSkLeakageReport()
        val json = report.toCanonicalJson()

        assertEquals("pass", report.status)
        assertTrue(report.findings.isEmpty())
        assertEquals(json, defaultTextGPUArtifactRegistry().noSkLeakageReport().toCanonicalJson())
        assertContains(json, """"payloadKind":"TextGPUArtifactRegistry"""")
        assertContains(json, """"fieldPath":"descriptors[0].artifactName"""")
        assertContains(json, """"fieldPath":"descriptors[0].descriptorCompactHash"""")
    }

    @Test
    fun `registry leakage report fails forbidden descriptor values`() {
        val descriptor = TextGPUArtifactDescriptor(
            artifactName = "SkFontArtifact",
            descriptorVersion = 1,
            ownerSubsystem = "pure-kotlin-text",
            keyPreimageFields = listOf("fontBytes"),
            lifetimeClass = "test-lifetime",
            invalidationFacts = listOf("generation"),
            memoryBudgetClass = "GPUTexture",
            uploadBudgetClass = "test-upload",
            supportedRoutes = listOf("DependencyGated"),
            missingDiagnostic = "unsupported.text.artifact_unregistered",
        )
        val report = TextGPUArtifactRegistry(listOf(descriptor)).noSkLeakageReport()

        assertEquals("fail", report.status)
        assertEquals(
            listOf(
                "descriptors[0].artifactName",
                "descriptors[0].keyPreimageFields[0]",
                "descriptors[0].memoryBudgetClass",
            ),
            report.findings.map { finding -> finding.fieldPath },
        )
        assertEquals(
            listOf(
                "unsupported.text.sk_type_leaked",
                "unsupported.text.sk_type_leaked",
                "unsupported.text.sk_type_leaked",
            ),
            report.findings.map { finding -> finding.rendererDiagnostic },
        )
    }

    @Test
    fun `registry json includes deterministic descriptor compact hashes`() {
        val registry = defaultTextGPUArtifactRegistry()
        val hashes = registry.descriptors.map { descriptor -> descriptor.descriptorCompactHash }
        val json = registry.toCanonicalJson()

        assertEquals(hashes, defaultTextGPUArtifactRegistry().descriptors.map { descriptor -> descriptor.descriptorCompactHash })
        assertEquals(hashes.size, hashes.toSet().size)
        hashes.forEach { hash ->
            assertTrue(hash.startsWith("fnv1a64:"))
            assertEquals("fnv1a64:".length + 16, hash.length)
        }
        assertEquals(
            registry.descriptors.size,
            Regex("\"descriptorCompactHash\":\"fnv1a64:[0-9a-f]{16}\"").findAll(json).count(),
        )
        registry.descriptors.forEach { descriptor ->
            assertContains(json, """"descriptorCompactHash":"${descriptor.descriptorCompactHash}"""")
        }
        assertEquals(json, defaultTextGPUArtifactRegistry().toCanonicalJson())
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
