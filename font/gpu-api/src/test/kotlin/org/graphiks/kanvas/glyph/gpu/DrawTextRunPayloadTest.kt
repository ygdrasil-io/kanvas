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
        assertContains(json, """"artifactType":"GlyphAtlasArtifact"""")
        assertContains(json, """"artifactKeyHash":"sha256:a8-atlas"""")
        assertContains(json, """"invalidationFacts":["generation","contentFingerprint","atlasCapacity"]""")
        assertContains(json, """"diagnostics":["text.gpu.upload-plan-ready"]""")
        assertContains(json, """"artifactKeyHashes":["sha256:a8-atlas"]""")
        assertContains(json, """"atlasGenerations":[3]""")
        assertContains(json, """"uploadDependencies":[{"id":"550e8400-e29b-41d4-a716-446655441110","label":"upload-a8-page-0"}]""")
        assertContains(json, """"diagnostics":[]""")
        assertContains(json, """"routePromotion":"not-promoted"""")
        assertContains(json, """"productActivation":false""")
        assertEquals(
            Uuid.parse("550e8400-e29b-41d4-a716-446655441110"),
            payload.uploadDependencies.single().id.value,
        )

        val leakReport = payload.noSkLeakageReport()
        assertEquals("pass", leakReport.status)
        assertContains(leakReport.toCanonicalJson(), """"payloadKind":"DrawTextRunPayload"""")
        assertContains(leakReport.toCanonicalJson(), """"payloadHash":"fnv1a64:""")
        assertContains(leakReport.toCanonicalJson(), """"fieldPath":"artifacts[0].sourceLabel"""")
        assertContains(leakReport.toCanonicalJson(), """"fieldPath":"artifacts[0].artifactType","typeName":"String","value":"GlyphAtlasArtifact"""")
        assertContains(leakReport.toCanonicalJson(), """"fieldPath":"artifacts[0].artifactKeyHash","typeName":"String","value":"sha256:a8-atlas"""")
        assertContains(leakReport.toCanonicalJson(), """"fieldPath":"artifacts[0].invalidationFacts[2]","typeName":"String","value":"atlasCapacity"""")
        assertContains(leakReport.toCanonicalJson(), """"fieldPath":"artifacts[0].diagnostics[0]","typeName":"String","value":"text.gpu.upload-plan-ready"""")
        assertContains(leakReport.toCanonicalJson(), """"fieldPath":"uploadDependencies","typeName":"List<GPUTextUploadDependencyRef>"""")
        assertContains(leakReport.toCanonicalJson(), """"fieldPath":"uploadDependencies[0].id","typeName":"GPUTextUploadDependencyID","value":"550e8400-e29b-41d4-a716-446655441110"""")
        assertContains(leakReport.toCanonicalJson(), """"fieldPath":"uploadDependencies[0].label","typeName":"String","value":"upload-a8-page-0"""")
        assertContains(leakReport.toCanonicalJson(), """"fieldPath":"diagnostics","typeName":"List<GPUTextRouteDiagnosticRef>"""")
        listOf("SkFont", "SkTypeface", "SkTextBlob", "SkPaint", "fontBytes", "GPUHandle").forEach { token ->
            assertFalse(json.contains(token), "Payload dump leaked forbidden token $token: $json")
        }
    }

    @Test
    fun `draw text run leakage report scans UUID refs and label code message facts`() {
        val payload = fixtureDrawTextRunPayload(
            uploadDependencies = listOf(
                fixtureUploadDependencyRef(
                    uuid = "550e8400-e29b-41d4-a716-446655441120",
                    label = "upload-before-sample:a8-page-0",
                ),
            ),
            diagnostics = listOf(
                fixtureRouteDiagnosticRef(
                    uuid = "550e8400-e29b-41d4-a716-446655441121",
                    code = "text.gpu.upload-plan-missing",
                    message = "Upload plan was not registered for the fixture route.",
                ),
            ),
        )

        val report = payload.noSkLeakageReport()
        val reportJson = report.toCanonicalJson()

        assertEquals("pass", report.status)
        assertEquals(Uuid.parse("550e8400-e29b-41d4-a716-446655441120"), payload.uploadDependencies.single().id.value)
        assertEquals(Uuid.parse("550e8400-e29b-41d4-a716-446655441121"), payload.diagnostics.single().id.value)
        assertContains(payload.toCanonicalJson(), """"uploadDependencies":[{"id":"550e8400-e29b-41d4-a716-446655441120","label":"upload-before-sample:a8-page-0"}]""")
        assertContains(payload.toCanonicalJson(), """"diagnostics":[{"id":"550e8400-e29b-41d4-a716-446655441121","code":"text.gpu.upload-plan-missing","message":"Upload plan was not registered for the fixture route."}]""")
        assertContains(
            reportJson,
            """"fieldPath":"uploadDependencies[0].id","typeName":"GPUTextUploadDependencyID","value":"550e8400-e29b-41d4-a716-446655441120"""",
        )
        assertContains(
            reportJson,
            """"fieldPath":"uploadDependencies[0].label","typeName":"String","value":"upload-before-sample:a8-page-0"""",
        )
        assertContains(
            reportJson,
            """"fieldPath":"diagnostics[0].code","typeName":"String","value":"text.gpu.upload-plan-missing"""",
        )
        assertContains(
            reportJson,
            """"fieldPath":"diagnostics[0].message","typeName":"String","value":"Upload plan was not registered for the fixture route."""",
        )
    }

    @Test
    fun `draw text run leakage report rejects forbidden payload values`() {
        val payload = fixtureDrawTextRunPayload(
            artifactKeyHashes = listOf("fontBytes:sha256:bad"),
            uploadDependencies = listOf(fixtureUploadDependencyRef(label = "GPUHandle:webgpu-texture-7")),
            diagnostics = listOf(
                fixtureRouteDiagnosticRef(message = "SkFont(opaque-stringified-wrapper)"),
                fixtureRouteDiagnosticRef(
                    uuid = "550e8400-e29b-41d4-a716-446655441131",
                    message = "NativeFontHandle(platform-font-ref)",
                ),
            ),
        )

        val report = payload.noSkLeakageReport()

        assertEquals("fail", report.status)
        assertEquals(
            listOf(
                "artifactKeyHashes[0]",
                "uploadDependencies[0].label",
                "diagnostics[0].message",
                "diagnostics[1].message",
            ),
            report.findings.map { finding -> finding.fieldPath },
        )
        assertEquals(
            listOf("String", "String", "String", "String"),
            report.findings.map { finding -> finding.typeName },
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
    fun `draw text run leakage report rejects generic SkPath diagnostics`() {
        val payload = fixtureDrawTextRunPayload(
            diagnostics = listOf(fixtureRouteDiagnosticRef(message = "SkPath(...)")),
        )

        val report = payload.noSkLeakageReport()

        assertEquals("fail", report.status)
        assertEquals(listOf("diagnostics[0].message"), report.findings.map { finding -> finding.fieldPath })
        assertEquals(
            listOf("unsupported.text.sk_type_leaked"),
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
                    diagnostics = listOf("text.gpu.CPU-rendered-texture-forbidden"),
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
            diagnostics = listOf(fixtureRouteDiagnosticRef(message = "payload_nondumpable: NonDumpableDrawTextRun")),
        )

        val report = payload.noSkLeakageReport()

        assertEquals("fail", report.status)
        assertEquals(listOf("diagnostics[0].message"), report.findings.map { finding -> finding.fieldPath })
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
                diagnostics = listOf("text.gpu.upload-plan-ready"),
            ),
        )
        val artifactKeyHashes = mutableListOf("sha256:a8-atlas")
        val atlasGenerations = mutableListOf(GPUTextArtifactGeneration(3))
        val uploadDependencies = mutableListOf(fixtureUploadDependencyRef(label = "upload-a8-page-0"))
        val diagnostics = mutableListOf(
            fixtureRouteDiagnosticRef(
                code = "text.gpu.fixture",
                message = "quote\"slash\\line\nwrapped",
            ),
        )
        val payload = fixtureDrawTextRunPayload(
            glyphRuns = glyphRuns,
            artifacts = artifacts,
            artifactKeyHashes = artifactKeyHashes,
            atlasGenerations = atlasGenerations,
            uploadDependencies = uploadDependencies,
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
        uploadDependencies += fixtureUploadDependencyRef(
            uuid = "550e8400-e29b-41d4-a716-446655441199",
            label = "GPUHandle",
        )
        diagnostics += fixtureRouteDiagnosticRef(
            uuid = "550e8400-e29b-41d4-a716-446655441198",
            message = "SkFont",
        )

        assertEquals(json, payload.toCanonicalJson())
        assertContains(json, """"glyphIDs":[42,43]""")
        assertContains(json, """"advances":[8.0,9.0]""")
        assertContains(json, """"offsets":[0.0,1.0]""")
        assertContains(json, """"diagnostics":[{"id":"550e8400-e29b-41d4-a716-446655441130","code":"text.gpu.fixture","message":"quote\"slash\\line\nwrapped"}]""")
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

    @Test
    fun `draw text run upload and diagnostic refs use UUID IDs and reject blank facts`() {
        val uploadID = GPUTextUploadDependencyID(Uuid.parse("550e8400-e29b-41d4-a716-446655441110"))
        val diagnosticID = GPUTextRouteDiagnosticID(Uuid.parse("550e8400-e29b-41d4-a716-446655441111"))

        assertEquals(Uuid.parse("550e8400-e29b-41d4-a716-446655441110"), uploadID.value)
        assertEquals(Uuid.parse("550e8400-e29b-41d4-a716-446655441111"), diagnosticID.value)
        assertFailsWith<IllegalArgumentException> {
            GPUTextUploadDependencyRef(uploadID, " ")
        }
        assertFailsWith<IllegalArgumentException> {
            GPUTextRouteDiagnosticRef(diagnosticID, "", "message")
        }
        assertFailsWith<IllegalArgumentException> {
            GPUTextRouteDiagnosticRef(diagnosticID, "text.gpu.fixture", "")
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
                diagnostics = listOf("text.gpu.upload-plan-ready"),
            ),
        ),
        artifactKeyHashes: List<String> = listOf("sha256:a8-atlas"),
        atlasGenerations: List<GPUTextArtifactGeneration> = listOf(GPUTextArtifactGeneration(3)),
        uploadDependencies: List<GPUTextUploadDependencyRef> = listOf(fixtureUploadDependencyRef()),
        diagnostics: List<GPUTextRouteDiagnosticRef> = emptyList(),
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
            uploadDependencies = uploadDependencies,
            diagnostics = diagnostics,
            provenance = TextEvidenceProvenance("fixture", "KFONT-M11-003"),
            routePromotion = routePromotion,
            productActivation = productActivation,
        )

    private fun fixtureUploadDependencyRef(
        uuid: String = "550e8400-e29b-41d4-a716-446655441110",
        label: String = "upload-a8-page-0",
    ): GPUTextUploadDependencyRef = GPUTextUploadDependencyRef(
        id = GPUTextUploadDependencyID(Uuid.parse(uuid)),
        label = label,
    )

    private fun fixtureRouteDiagnosticRef(
        uuid: String = "550e8400-e29b-41d4-a716-446655441130",
        code: String = "text.gpu.fixture",
        message: String = "Fixture route diagnostic.",
    ): GPUTextRouteDiagnosticRef = GPUTextRouteDiagnosticRef(
        id = GPUTextRouteDiagnosticID(Uuid.parse(uuid)),
        code = code,
        message = message,
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
            "\"artifactType\":\"GlyphAtlasArtifact\"," +
            "\"artifactID\":\"550e8400-e29b-41d4-a716-446655441102\"," +
            "\"generation\":3," +
            "\"contentFingerprint\":\"sha256:a8-atlas\"," +
            "\"artifactKeyHash\":\"sha256:a8-atlas\"," +
            "\"invalidationFacts\":[\"generation\",\"contentFingerprint\",\"atlasCapacity\"]," +
            "\"diagnostics\":[\"text.gpu.upload-plan-ready\"]," +
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
            "\"uploadDependencies\":[{\"id\":\"550e8400-e29b-41d4-a716-446655441110\",\"label\":\"upload-a8-page-0\"}]," +
            "\"diagnostics\":[]," +
            "\"provenance\":{\"source\":\"fixture\",\"ticket\":\"KFONT-M11-003\"}," +
            "\"routePromotion\":\"not-promoted\"," +
            "\"productActivation\":false" +
            "}"
}
