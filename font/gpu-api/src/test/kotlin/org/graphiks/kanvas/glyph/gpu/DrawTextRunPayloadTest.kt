package org.graphiks.kanvas.glyph.gpu

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.uuid.Uuid

class DrawTextRunPayloadTest {
    @Test
    fun `draw text run payload is deterministic and passes no Sk leakage`() {
        val payload = fixtureDrawTextRunPayload()

        val json = payload.toCanonicalJson()

        assertEquals(json, fixtureDrawTextRunPayload().toCanonicalJson())
        assertEquals(fixtureDrawTextRunPayloadJson(), json)
        assertContains(json, """"schema":"org.graphiks.kanvas.glyph.gpu.DrawTextRunPayload.v1"""")
        assertContains(json, """"commandId":"draw-text-001"""")
        assertContains(json, """"layoutResultID":"550e8400-e29b-41d4-a716-446655441100"""")
        assertContains(json, """"glyphRunID":"550e8400-e29b-41d4-a716-446655441101"""")
        assertContains(json, """"glyphIDs":[42,43]""")
        assertContains(json, """"artifactName":"GlyphAtlasArtifact"""")
        assertContains(json, """"artifactKeyHashes":["sha256:a8-atlas"]""")
        assertContains(json, """"atlasGenerations":[3]""")
        assertContains(json, """"uploadDependencyIds":["upload-a8-page-0"]""")
        assertContains(json, """"routePromotion":"not-promoted"""")
        assertContains(json, """"productActivation":false""")

        val leakReport = payload.noSkLeakageReport()
        assertEquals("pass", leakReport.status)
        assertContains(leakReport.toCanonicalJson(), """"payloadKind":"DrawTextRunPayload"""")
        assertContains(leakReport.toCanonicalJson(), """"fieldPath":"artifacts[0].sourceLabel"""")
        listOf("SkFont", "SkTypeface", "SkTextBlob", "SkPaint", "fontBytes", "GPUHandle").forEach { token ->
            assertFalse(json.contains(token), "Payload dump leaked forbidden token $token: $json")
        }
    }

    @Test
    fun `draw text run leakage report rejects forbidden payload values`() {
        val payload = fixtureDrawTextRunPayload(
            artifactKeyHashes = listOf("fontBytes:sha256:bad"),
            uploadDependencyIds = listOf("GPUHandle:webgpu-texture-7"),
            diagnostics = listOf(
                "SkFont(opaque-stringified-wrapper)",
                "NativeFontHandle(platform-font-ref)",
            ),
        )

        val report = payload.noSkLeakageReport()

        assertEquals("fail", report.status)
        assertEquals(
            listOf("artifactKeyHashes[0]", "uploadDependencyIds[0]", "diagnostics[0]", "diagnostics[1]"),
            report.findings.map { finding -> finding.fieldPath },
        )
        assertEquals(
            listOf(
                "unsupported.text.sk_type_leaked",
                "unsupported.text.sk_type_leaked",
                "unsupported.text.sk_type_leaked",
                "unsupported.text.sk_type_leaked",
            ),
            report.findings.map { finding -> finding.rendererDiagnostic },
        )
    }

    @Test
    fun `draw text run leakage report rejects CPU rendered texture payload values`() {
        val payload = fixtureDrawTextRunPayload(
            artifacts = listOf(
                GPUTextArtifactReference(
                    artifactName = "GlyphAtlasArtifact",
                    artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655441102")),
                    generation = GPUTextArtifactGeneration(3),
                    contentFingerprint = "sha256:a8-atlas",
                    sourceLabel = "CPURenderedTextTexture(full-text-fallback)",
                ),
            ),
        )

        val report = payload.noSkLeakageReport()

        assertEquals("fail", report.status)
        assertEquals(listOf("artifacts[0].sourceLabel"), report.findings.map { finding -> finding.fieldPath })
        assertEquals(
            listOf("unsupported.text.cpu_rendered_texture_forbidden"),
            report.findings.map { finding -> finding.rendererDiagnostic },
        )
    }

    @Test
    fun `draw text run leakage report rejects nondumpable payload markers`() {
        val payload = fixtureDrawTextRunPayload(
            diagnostics = listOf("payload_nondumpable: NonDumpableDrawTextRun"),
        )

        val report = payload.noSkLeakageReport()

        assertEquals("fail", report.status)
        assertEquals(listOf("diagnostics[0]"), report.findings.map { finding -> finding.fieldPath })
        assertEquals(
            listOf("text.gpu.payload-nondumpable"),
            report.findings.map { finding -> finding.handoffDiagnostic },
        )
        assertEquals(
            listOf("unsupported.text.payload_nondumpable"),
            report.findings.map { finding -> finding.rendererDiagnostic },
        )
    }

    @Test
    fun `draw text run payload snapshots caller lists and nested glyph descriptors`() {
        val glyphIDs = mutableListOf(42, 43)
        val advances = mutableListOf(8.0f, 9.0f)
        val offsets = mutableListOf(0.0f, 1.0f)
        val glyphRuns = mutableListOf(
            GPUGlyphRunDescriptor(
                runID = GPUGlyphRunID(Uuid.parse("550e8400-e29b-41d4-a716-446655441101")),
                layoutResultID = GPUTextLayoutResultID(Uuid.parse("550e8400-e29b-41d4-a716-446655441100")),
                glyphIDs = glyphIDs,
                advances = advances,
                offsets = offsets,
                textRangeStart = 0,
                textRangeEnd = 2,
                script = "Latn",
                bidiLevel = 0,
            ),
        )
        val artifacts = mutableListOf(
            GPUTextArtifactReference(
                artifactName = "GlyphAtlasArtifact",
                artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655441102")),
                generation = GPUTextArtifactGeneration(3),
                contentFingerprint = "sha256:a8-atlas",
                sourceLabel = "fixture.atlas",
            ),
        )
        val artifactKeyHashes = mutableListOf("sha256:a8-atlas")
        val atlasGenerations = mutableListOf(GPUTextArtifactGeneration(3))
        val uploadDependencyIds = mutableListOf("upload-a8-page-0")
        val diagnostics = mutableListOf("text.gpu.fixture\"quote\\slash\nline")
        val payload = fixtureDrawTextRunPayload(
            glyphRuns = glyphRuns,
            artifacts = artifacts,
            artifactKeyHashes = artifactKeyHashes,
            atlasGenerations = atlasGenerations,
            uploadDependencyIds = uploadDependencyIds,
            diagnostics = diagnostics,
        )
        val json = payload.toCanonicalJson()

        glyphIDs += 99
        advances[0] = 99.0f
        offsets += 99.0f
        glyphRuns[0] = glyphRuns[0].copy(glyphIDs = listOf(7), script = "Injected")
        artifacts[0] = artifacts[0].copy(artifactName = "InjectedArtifact", contentFingerprint = "fontBytes")
        artifactKeyHashes += "fontBytes"
        atlasGenerations[0] = GPUTextArtifactGeneration(99)
        uploadDependencyIds += "GPUHandle"
        diagnostics += "SkFont"

        assertEquals(json, payload.toCanonicalJson())
        assertContains(json, """"glyphIDs":[42,43]""")
        assertContains(json, """"advances":[8.0,9.0]""")
        assertContains(json, """"offsets":[0.0,1.0]""")
        assertContains(json, """"diagnostics":["text.gpu.fixture\"quote\\slash\nline"]""")
        listOf("Injected", "InjectedArtifact", "fontBytes", "GPUHandle", "SkFont").forEach { token ->
            assertFalse(payload.toCanonicalJson().contains(token), "Payload dump changed after source mutation: $token")
        }
    }

    @Test
    fun `draw text run payload rejects route promotion and product activation claims`() {
        assertFailsWith<IllegalArgumentException> {
            fixtureDrawTextRunPayload(routePromotion = "promoted")
        }
        assertFailsWith<IllegalArgumentException> {
            fixtureDrawTextRunPayload(productActivation = true)
        }
    }

    private fun fixtureDrawTextRunPayload(
        glyphRuns: List<GPUGlyphRunDescriptor> = listOf(
            GPUGlyphRunDescriptor(
                runID = GPUGlyphRunID(Uuid.parse("550e8400-e29b-41d4-a716-446655441101")),
                layoutResultID = GPUTextLayoutResultID(Uuid.parse("550e8400-e29b-41d4-a716-446655441100")),
                glyphIDs = listOf(42, 43),
                advances = listOf(8.0f, 9.0f),
                textRangeStart = 0,
                textRangeEnd = 2,
                script = "Latn",
                bidiLevel = 0,
            ),
        ),
        artifacts: List<GPUTextArtifactReference> = listOf(
            GPUTextArtifactReference(
                artifactName = "GlyphAtlasArtifact",
                artifactID = GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655441102")),
                generation = GPUTextArtifactGeneration(3),
                contentFingerprint = "sha256:a8-atlas",
                sourceLabel = "fixture.atlas",
            ),
        ),
        artifactKeyHashes: List<String> = listOf("sha256:a8-atlas"),
        atlasGenerations: List<GPUTextArtifactGeneration> = listOf(GPUTextArtifactGeneration(3)),
        uploadDependencyIds: List<String> = listOf("upload-a8-page-0"),
        diagnostics: List<String> = emptyList(),
        routePromotion: String = "not-promoted",
        productActivation: Boolean = false,
    ): DrawTextRunPayload =
        DrawTextRunPayload(
            commandId = "draw-text-001",
            layoutResultID = GPUTextLayoutResultID(Uuid.parse("550e8400-e29b-41d4-a716-446655441100")),
            glyphRunID = GPUGlyphRunID(Uuid.parse("550e8400-e29b-41d4-a716-446655441101")),
            glyphRuns = glyphRuns,
            artifacts = artifacts,
            transform = TextTransformFacts("axis-aligned", "matrix:identity"),
            clip = TextClipFacts("rect", "0,0 64x32"),
            layer = TextLayerFacts("root", "none"),
            material = TextMaterialDescriptor("solid", "material:text-black"),
            blendColor = TextBlendColorFacts("src-over", "srgb-premul"),
            artifactKeyHashes = artifactKeyHashes,
            atlasGenerations = atlasGenerations,
            uploadDependencyIds = uploadDependencyIds,
            diagnostics = diagnostics,
            provenance = TextEvidenceProvenance("fixture", "KFONT-M11-003"),
            routePromotion = routePromotion,
            productActivation = productActivation,
        )

    private fun fixtureDrawTextRunPayloadJson(): String =
        "{" +
            "\"schema\":\"org.graphiks.kanvas.glyph.gpu.DrawTextRunPayload.v1\"," +
            "\"commandId\":\"draw-text-001\"," +
            "\"layoutResultID\":\"550e8400-e29b-41d4-a716-446655441100\"," +
            "\"glyphRunID\":\"550e8400-e29b-41d4-a716-446655441101\"," +
            "\"glyphRuns\":[" +
            "{" +
            "\"runID\":\"550e8400-e29b-41d4-a716-446655441101\"," +
            "\"layoutResultID\":\"550e8400-e29b-41d4-a716-446655441100\"," +
            "\"typefaceID\":null," +
            "\"glyphIDs\":[42,43]," +
            "\"advances\":[8.0,9.0]," +
            "\"offsets\":[]," +
            "\"textRangeStart\":0," +
            "\"textRangeEnd\":2," +
            "\"script\":\"Latn\"," +
            "\"bidiLevel\":0" +
            "}" +
            "]," +
            "\"artifacts\":[" +
            "{" +
            "\"artifactName\":\"GlyphAtlasArtifact\"," +
            "\"artifactID\":\"550e8400-e29b-41d4-a716-446655441102\"," +
            "\"generation\":3," +
            "\"contentFingerprint\":\"sha256:a8-atlas\"," +
            "\"sourceLabel\":\"fixture.atlas\"" +
            "}" +
            "]," +
            "\"transform\":{\"transformClass\":\"axis-aligned\",\"matrixLabel\":\"matrix:identity\"}," +
            "\"clip\":{\"clipKind\":\"rect\",\"boundsLabel\":\"0,0 64x32\"}," +
            "\"layer\":{\"layerKind\":\"root\",\"layerLabel\":\"none\"}," +
            "\"material\":{\"materialKind\":\"solid\",\"materialKey\":\"material:text-black\"}," +
            "\"blendColor\":{\"blendMode\":\"src-over\",\"colorSpace\":\"srgb-premul\"}," +
            "\"artifactKeyHashes\":[\"sha256:a8-atlas\"]," +
            "\"atlasGenerations\":[3]," +
            "\"uploadDependencyIds\":[\"upload-a8-page-0\"]," +
            "\"diagnostics\":[]," +
            "\"provenance\":{\"source\":\"fixture\",\"ticket\":\"KFONT-M11-003\"}," +
            "\"routePromotion\":\"not-promoted\"," +
            "\"productActivation\":false" +
            "}"
}
