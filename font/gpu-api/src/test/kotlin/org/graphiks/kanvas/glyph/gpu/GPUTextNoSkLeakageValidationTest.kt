package org.graphiks.kanvas.glyph.gpu

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

class GPUTextNoSkLeakageValidationTest {
    @Test
    fun `value object payload passes leakage validation`() {
        val report = validateGPUTextNoSkLeakage(
            payloadKind = "TextGPUArtifactBundle",
            fields = listOf(
                TextPayloadField("artifactID", "GPUTextArtifactID"),
                TextPayloadField("generation", "GPUTextArtifactGeneration"),
                TextPayloadField("contentFingerprint", "sha256:a8-atlas"),
            ),
        )

        val json = report.toCanonicalJson()

        assertTrue(report.findings.isEmpty())
        assertEquals("pass", report.status)
        assertTrue(report.payloadHash.startsWith("fnv1a64:"))
        assertEquals("fnv1a64:".length + 16, report.payloadHash.length)
        assertContains(json, """"schema":"org.graphiks.kanvas.glyph.gpu.TextPayloadLeakReport.v1"""")
        assertContains(json, """"payloadHash":"${report.payloadHash}"""")
        assertContains(json, """"status":"pass"""")
        assertContains(json, """"payloadKind":"TextGPUArtifactBundle"""")
        assertContains(json, """"findings":[]""")
        assertEquals(json, report.toCanonicalJson())
    }

    @Test
    fun `forbidden text payload fields emit stable diagnostics`() {
        val report = validateGPUTextNoSkLeakage(
            payloadKind = "DrawTextRunPayload",
            fields = listOf(
                TextPayloadField("font", "SkFont"),
                TextPayloadField("paint", "SkPaint"),
                TextPayloadField("texture", "CPURenderedTextTexture"),
            ),
        )

        assertEquals("fail", report.status)
        assertEquals(
            listOf(
                "unsupported.text.sk_type_leaked",
                "unsupported.text.sk_type_leaked",
                "unsupported.text.cpu_rendered_texture_forbidden",
            ),
            report.findings.map { it.rendererDiagnostic },
        )
        assertEquals(
            listOf(
                "text.gpu.sk-type-leaked",
                "text.gpu.sk-type-leaked",
                "text.gpu.CPU-rendered-texture-forbidden",
            ),
            report.findings.map { it.handoffDiagnostic },
        )
        assertContains(report.toCanonicalJson(), """"handoffDiagnostic":"text.gpu.sk-type-leaked"""")
        assertContains(report.toCanonicalJson(), """"fieldPath":"texture"""")
    }

    @Test
    fun `qualified sk types and forbidden handle payload markers fail validation`() {
        val report = validateGPUTextNoSkLeakage(
            payloadKind = "GPUTextRunPlan",
            fields = listOf(
                TextPayloadField("typeface", "org.skia.foundation.SkTypeface"),
                TextPayloadField("fontPayload", "FontBytes"),
                TextPayloadField("nativeHandle", "NativeFontHandle"),
                TextPayloadField("textureHandle", "GPUHandle"),
            ),
        )

        assertEquals("fail", report.status)
        assertEquals(
            listOf(
                "typeface",
                "fontPayload",
                "nativeHandle",
                "textureHandle",
            ),
            report.findings.map { it.fieldPath },
        )
        assertTrue(report.findings.all { finding ->
            finding.handoffDiagnostic == "text.gpu.sk-type-leaked" &&
                finding.rendererDiagnostic == "unsupported.text.sk_type_leaked"
        })
    }

    @Test
    fun `raw GPU handle values fail leakage validation`() {
        val rawHandleTokens = listOf(
            "GPUTexture",
            "GPUBuffer",
            "GPUDevice",
            "WGPUTexture",
            "TextureView",
            "BindGroup",
            "Sampler",
            "CommandEncoder",
            "RenderPassEncoder",
            "GPUQueue",
            "Queue",
            "RenderPipeline",
            "Pipeline",
            "PlatformFontHandle",
        )
        val report = validateGPUTextNoSkLeakage(
            payloadKind = "GPUTextRunPlan",
            fields = rawHandleTokens.mapIndexed { index, token ->
                TextPayloadField("diagnostics[$index]", "String", token)
            },
        )

        assertEquals("fail", report.status)
        assertEquals(rawHandleTokens.size, report.findings.size)
        assertEquals(
            List(rawHandleTokens.size) { "unsupported.text.sk_type_leaked" },
            report.findings.map { finding -> finding.rendererDiagnostic },
        )
        assertEquals(
            rawHandleTokens.indices.map { index -> "diagnostics[$index]" },
            report.findings.map { finding -> finding.fieldPath },
        )
    }

    @Test
    fun `domain GPU text wrapper type names do not fail leakage validation`() {
        val report = validateGPUTextNoSkLeakage(
            payloadKind = "GPUTextRunPlan",
            fields = listOf(
                TextPayloadField("artifactID", "GPUTextArtifactID", "3f235f9f-a223-4d16-9f85-cb6a092d229f"),
                TextPayloadField("generation", "GPUTextArtifactGeneration", "42"),
                TextPayloadField("layoutResultID", "GPUTextLayoutResultID", "b8461787-f45c-4d66-874e-8b48abb20da2"),
            ),
        )

        assertEquals("pass", report.status)
        assertTrue(report.findings.isEmpty())
    }

    @Test
    fun `report snapshots caller fields and keeps canonical json deterministic`() {
        val fields = mutableListOf(
            TextPayloadField("paint", "SkPaint"),
            TextPayloadField("texture", "CPURenderedTextTexture"),
        )
        val report = validateGPUTextNoSkLeakage(
            payloadKind = "DrawTextRunPayload",
            fields = fields,
        )
        val fieldsSnapshot = report.fields
        val findingsSnapshot = report.findings
        val json = report.toCanonicalJson()

        fields[0] = TextPayloadField("artifactID", "GPUTextArtifactID")
        fields += TextPayloadField("typeface", "SkTypeface")

        assertEquals(fieldsSnapshot, report.fields)
        assertEquals(findingsSnapshot, report.findings)
        assertEquals(json, report.toCanonicalJson())
        assertEquals(report.payloadHash, validateGPUTextNoSkLeakage("DrawTextRunPayload", fieldsSnapshot).payloadHash)
        assertTrue(report.payloadHash != validateGPUTextNoSkLeakage(
            payloadKind = "DrawTextRunPayload",
            fields = listOf(TextPayloadField("paint", "SkPaint", "changed")),
        ).payloadHash)
        assertContains(json, """"schema":"org.graphiks.kanvas.glyph.gpu.TextPayloadLeakReport.v1"""")
        assertContains(json, """"payloadHash":"${report.payloadHash}"""")
        assertContains(json, """"fieldPath":"paint"""")
        assertTrue(!report.toCanonicalJson().contains("SkTypeface"))
    }

    @Test
    fun `canonical json fixture preserves scan order and escapes special characters`() {
        val escapedPath = "z\"quote\\slash\nline"
        val report = validateGPUTextNoSkLeakage(
            payloadKind = "EscapedPayload",
            fields = listOf(
                TextPayloadField(escapedPath, "SkFont"),
                TextPayloadField("aTexture", "CPURenderedTextTexture"),
            ),
        )

        assertEquals(listOf(escapedPath, "aTexture"), report.findings.map { finding -> finding.fieldPath })
        assertEquals(
            "{" +
                "\"schema\":\"org.graphiks.kanvas.glyph.gpu.TextPayloadLeakReport.v1\"," +
                "\"payloadKind\":\"EscapedPayload\"," +
                "\"payloadHash\":\"fnv1a64:f0186fcbce3b9c51\"," +
                "\"status\":\"fail\"," +
                "\"fields\":[" +
                "{\"fieldPath\":\"z\\\"quote\\\\slash\\nline\",\"typeName\":\"SkFont\"}," +
                "{\"fieldPath\":\"aTexture\",\"typeName\":\"CPURenderedTextTexture\"}" +
                "]," +
                "\"findings\":[" +
                "{" +
                "\"payloadKind\":\"EscapedPayload\"," +
                "\"fieldPath\":\"z\\\"quote\\\\slash\\nline\"," +
                "\"typeName\":\"SkFont\"," +
                "\"forbiddenKind\":\"sk-type-or-handle\"," +
                "\"handoffDiagnostic\":\"text.gpu.sk-type-leaked\"," +
                "\"rendererDiagnostic\":\"unsupported.text.sk_type_leaked\"" +
                "}," +
                "{" +
                "\"payloadKind\":\"EscapedPayload\"," +
                "\"fieldPath\":\"aTexture\"," +
                "\"typeName\":\"CPURenderedTextTexture\"," +
                "\"forbiddenKind\":\"cpu-rendered-texture\"," +
                "\"handoffDiagnostic\":\"text.gpu.CPU-rendered-texture-forbidden\"," +
                "\"rendererDiagnostic\":\"unsupported.text.cpu_rendered_texture_forbidden\"" +
                "}" +
                "]" +
                "}",
            report.toCanonicalJson(),
        )
    }

    @Test
    fun `canonical json fixture preserves value evidence order and escaping`() {
        val report = validateGPUTextNoSkLeakage(
            payloadKind = "ValuePayload",
            fields = listOf(
                TextPayloadField("diagnostics[0]", "String", "SkTypeface(\"quote\\slash\nline\")"),
                TextPayloadField("material.materialKey", "String", "payload_nondumpable"),
                TextPayloadField("artifacts[0].sourceLabel", "String", "CPURenderedTextTexture(full-text)"),
            ),
        )

        assertEquals(
            listOf(
                "unsupported.text.sk_type_leaked",
                "unsupported.text.payload_nondumpable",
                "unsupported.text.cpu_rendered_texture_forbidden",
            ),
            report.findings.map { finding -> finding.rendererDiagnostic },
        )
        assertEquals(
            "{" +
                "\"schema\":\"org.graphiks.kanvas.glyph.gpu.TextPayloadLeakReport.v1\"," +
                "\"payloadKind\":\"ValuePayload\"," +
                "\"payloadHash\":\"fnv1a64:0b1d71deb1245e45\"," +
                "\"status\":\"fail\"," +
                "\"fields\":[" +
                "{" +
                "\"fieldPath\":\"diagnostics[0]\"," +
                "\"typeName\":\"String\"," +
                "\"value\":\"SkTypeface(\\\"quote\\\\slash\\nline\\\")\"" +
                "}," +
                "{" +
                "\"fieldPath\":\"material.materialKey\"," +
                "\"typeName\":\"String\"," +
                "\"value\":\"payload_nondumpable\"" +
                "}," +
                "{" +
                "\"fieldPath\":\"artifacts[0].sourceLabel\"," +
                "\"typeName\":\"String\"," +
                "\"value\":\"CPURenderedTextTexture(full-text)\"" +
                "}" +
                "]," +
                "\"findings\":[" +
                "{" +
                "\"payloadKind\":\"ValuePayload\"," +
                "\"fieldPath\":\"diagnostics[0]\"," +
                "\"typeName\":\"String\"," +
                "\"forbiddenKind\":\"sk-type-or-handle\"," +
                "\"handoffDiagnostic\":\"text.gpu.sk-type-leaked\"," +
                "\"rendererDiagnostic\":\"unsupported.text.sk_type_leaked\"" +
                "}," +
                "{" +
                "\"payloadKind\":\"ValuePayload\"," +
                "\"fieldPath\":\"material.materialKey\"," +
                "\"typeName\":\"String\"," +
                "\"forbiddenKind\":\"nondumpable-payload\"," +
                "\"handoffDiagnostic\":\"text.gpu.payload-nondumpable\"," +
                "\"rendererDiagnostic\":\"unsupported.text.payload_nondumpable\"" +
                "}," +
                "{" +
                "\"payloadKind\":\"ValuePayload\"," +
                "\"fieldPath\":\"artifacts[0].sourceLabel\"," +
                "\"typeName\":\"String\"," +
                "\"forbiddenKind\":\"cpu-rendered-texture\"," +
                "\"handoffDiagnostic\":\"text.gpu.CPU-rendered-texture-forbidden\"," +
                "\"rendererDiagnostic\":\"unsupported.text.cpu_rendered_texture_forbidden\"" +
                "}" +
                "]" +
                "}",
            report.toCanonicalJson(),
        )
    }

    @Test
    fun `value-level generic SkPath marker emits stable diagnostics`() {
        val report = validateGPUTextNoSkLeakage(
            payloadKind = "ValuePayload",
            fields = listOf(
                TextPayloadField("diagnostics[0]", "String", "SkPath(...)"),
            ),
        )

        assertEquals("fail", report.status)
        assertEquals(listOf("diagnostics[0]"), report.findings.map { finding -> finding.fieldPath })
        assertEquals(
            listOf("unsupported.text.sk_type_leaked"),
            report.findings.map { finding -> finding.rendererDiagnostic },
        )
    }

    @Test
    fun `separator encoded Sk markers emit stable diagnostics`() {
        val fields = listOf(
            TextPayloadField("labels[0]", "String", "sk-font"),
            TextPayloadField("labels[1]", "String", "sk_typeface"),
            TextPayloadField("labels[2]", "String", "sk_text_blob"),
            TextPayloadField("labels[3]", "String", "org.skia.foundation.sk_text_blob"),
            TextPayloadField("labels[4]", "String", "sk-shaper"),
            TextPayloadField("labels[5]", "String", "sk_paint"),
        )

        val report = validateGPUTextNoSkLeakage(
            payloadKind = "ValuePayload",
            fields = fields,
        )

        assertEquals("fail", report.status)
        assertEquals(fields.map { field -> field.fieldPath }, report.findings.map { finding -> finding.fieldPath })
        assertEquals(
            List(fields.size) { "unsupported.text.sk_type_leaked" },
            report.findings.map { finding -> finding.rendererDiagnostic },
        )
    }

    @Test
    fun `non Skia sk words and mask fields do not fail leakage validation`() {
        val report = validateGPUTextNoSkLeakage(
            payloadKind = "ValuePayload",
            fields = listOf(
                TextPayloadField("transform", "SkewTransformFacts", "sketch diagnostics"),
                TextPayloadField("clip.maskPath", "String", "maskPath"),
                TextPayloadField("material.maskFont", "String", "maskFont"),
                TextPayloadField("routeLabel", "String", "wgsl-pipeline-target"),
            ),
        )

        assertEquals("pass", report.status)
        assertTrue(report.findings.isEmpty())
    }

    @Test
    fun `real text gpu artifact bundle passes no Sk leakage validation`() {
        val report = fixtureTextGPUArtifactBundle().noSkLeakageReport()
        val json = report.toCanonicalJson()

        assertEquals("pass", report.status)
        assertTrue(report.findings.isEmpty())
        assertContains(json, """"payloadKind":"TextGPUArtifactBundle"""")
        assertContains(json, """"fieldPath":"artifactKey.artifactID","typeName":"GPUTextArtifactID","value":"550e8400-e29b-41d4-a716-446655444000"""")
        assertContains(json, """"fieldPath":"uploadPlans[0].ranges[0].label","typeName":"String","value":"glyph-page-0"""")
        assertContains(json, """"fieldPath":"artifactReferences[0].artifactType","typeName":"String","value":"GlyphAtlasArtifact"""")
        assertContains(json, """"fieldPath":"artifactReferences[0].artifactKeyHash","typeName":"String","value":"bundle-atlas-a8"""")
        assertContains(json, """"fieldPath":"artifactReferences[0].invalidationFacts[2]","typeName":"String","value":"atlasCapacity"""")
        assertContains(json, """"fieldPath":"diagnostics.diagnostics[0].message","typeName":"String","value":"Glyph route is fixture-clean."""")
    }

    @Test
    fun `real text gpu artifact bundle rejects forbidden diagnostic and reference values`() {
        val atlasKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655444100",
            generation = 1,
            contentFingerprint = "bundle-atlas-a8",
        )
        val bundle = fixtureTextGPUArtifactBundle(
            artifactKey = atlasKey,
            atlases = listOf(
                GlyphAtlasArtifact(
                    artifactKey = atlasKey,
                    width = 64,
                    height = 64,
                    format = "r8",
                ),
            ),
            diagnostics = GPUTextRouteDiagnostics(
                diagnostics = listOf(
                    GPUTextArtifactDiagnostic(
                        code = GPUTextArtifactDiagnosticCode.MISSING_GLYPH,
                        message = "SkFont leaked through a diagnostic wrapper.",
                        artifactKey = atlasKey,
                    ),
                ),
                refusalRequired = true,
            ),
        )

        val report = bundle.noSkLeakageReport()

        assertEquals("fail", report.status)
        assertEquals(
            listOf(
                "diagnostics.diagnostics[0].message",
                "artifactReferences[0].diagnostics[0]",
            ),
            report.findings.map { finding -> finding.fieldPath },
        )
        assertEquals(
            listOf("unsupported.text.sk_type_leaked", "unsupported.text.sk_type_leaked"),
            report.findings.map { finding -> finding.rendererDiagnostic },
        )
    }

    @Test
    fun `future GPUTextSubRunPlan fixture scans generic field list without production type`() {
        val passReport = validateGPUTextNoSkLeakage(
            payloadKind = "FutureGPUTextSubRunPlanFixture",
            fields = futureSubRunPlanFixtureFields(
                artifactType = "GlyphAtlasArtifact",
                artifactKeyHash = "sha256:a8-atlas",
                diagnostic = "text.gpu.upload-plan-ready",
            ),
        )
        val failReport = validateGPUTextNoSkLeakage(
            payloadKind = "FutureGPUTextSubRunPlanFixture",
            fields = futureSubRunPlanFixtureFields(
                artifactType = "SkTextBlobArtifact",
                artifactKeyHash = "GPUTexture:raw",
                diagnostic = "BindGroup leaked",
            ),
        )

        assertEquals("pass", passReport.status)
        assertTrue(passReport.findings.isEmpty())
        assertEquals("fail", failReport.status)
        assertEquals(
            listOf(
                "futureSubRunPlan.artifactReferences[0].artifactType",
                "futureSubRunPlan.artifactReferences[0].artifactKeyHash",
                "futureSubRunPlan.diagnostics[0]",
            ),
            failReport.findings.map { finding -> finding.fieldPath },
        )
    }

    private fun futureSubRunPlanFixtureFields(
        artifactType: String,
        artifactKeyHash: String,
        diagnostic: String,
    ): List<TextPayloadField> = listOf(
        TextPayloadField("futureSubRunPlan.subRunID", "String", "subrun-0"),
        TextPayloadField("futureSubRunPlan.glyphRange", "String", "0..2"),
        TextPayloadField("futureSubRunPlan.artifactReferences", "List<GPUTextArtifactReference>"),
        TextPayloadField(
            "futureSubRunPlan.artifactReferences[0].artifactName",
            "String",
            "GlyphAtlasArtifact",
        ),
        TextPayloadField(
            "futureSubRunPlan.artifactReferences[0].artifactType",
            "String",
            artifactType,
        ),
        TextPayloadField(
            "futureSubRunPlan.artifactReferences[0].artifactKeyHash",
            "String",
            artifactKeyHash,
        ),
        TextPayloadField(
            "futureSubRunPlan.artifactReferences[0].invalidationFacts[0]",
            "String",
            "generation",
        ),
        TextPayloadField("futureSubRunPlan.diagnostics[0]", "String", diagnostic),
    )

    private fun fixtureTextGPUArtifactBundle(
        artifactKey: GPUTextArtifactKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655444000",
            generation = 0,
            contentFingerprint = "bundle-root",
        ),
        atlases: List<GlyphAtlasArtifact> = listOf(
            GlyphAtlasArtifact(
                artifactKey = fixtureArtifactKey(
                    uuid = "550e8400-e29b-41d4-a716-446655444001",
                    generation = 1,
                    contentFingerprint = "bundle-atlas-a8",
                ),
                width = 64,
                height = 64,
                format = "r8",
            ),
        ),
        diagnostics: GPUTextRouteDiagnostics = GPUTextRouteDiagnostics(
            diagnostics = listOf(
                GPUTextArtifactDiagnostic(
                    code = GPUTextArtifactDiagnosticCode.MISSING_GLYPH,
                    message = "Glyph route is fixture-clean.",
                ),
            ),
            refusalRequired = false,
        ),
    ): TextGPUArtifactBundle {
        val uploadKey = fixtureArtifactKey(
            uuid = "550e8400-e29b-41d4-a716-446655444002",
            generation = 2,
            contentFingerprint = "bundle-upload",
        )
        val uploadPlan = GPUTextUploadPlan(
            artifactKey = uploadKey,
            ranges = listOf(GPUTextUploadRange(offset = 0, size = 16, label = "glyph-page-0")),
            byteSize = 16,
        )
        return TextGPUArtifactBundle(
            artifactKey = artifactKey,
            uploadPlans = listOf(uploadPlan),
            glyphUploadPlans = listOf(
                GlyphUploadPlan(
                    artifactKey = uploadKey,
                    uploadPlan = uploadPlan,
                    glyphIDs = listOf(7U, 8U),
                ),
            ),
            outlineGlyphPlans = listOf(
                OutlineGlyphPlan(
                    artifactKey = fixtureArtifactKey(
                        uuid = "550e8400-e29b-41d4-a716-446655444003",
                        generation = 3,
                        contentFingerprint = "bundle-outline",
                    ),
                    glyphIDs = listOf(7U),
                    windingRule = "non-zero",
                ),
            ),
            colorGlyphPlans = emptyList(),
            bitmapGlyphPlans = emptyList(),
            svgGlyphPlans = emptyList(),
            atlases = atlases,
            sdfAtlases = emptyList(),
            diagnostics = diagnostics,
        )
    }

    private fun fixtureArtifactKey(
        uuid: String,
        generation: Int,
        contentFingerprint: String,
    ): GPUTextArtifactKey = GPUTextArtifactKey(
        artifactID = GPUTextArtifactID(Uuid.parse(uuid)),
        generation = GPUTextArtifactGeneration(generation),
        contentFingerprint = contentFingerprint,
    )
}
