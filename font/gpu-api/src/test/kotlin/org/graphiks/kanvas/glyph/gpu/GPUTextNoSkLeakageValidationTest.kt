package org.graphiks.kanvas.glyph.gpu

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue

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
        assertContains(json, """"schema":"org.graphiks.kanvas.glyph.gpu.TextPayloadLeakReport.v1"""")
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
        assertContains(json, """"schema":"org.graphiks.kanvas.glyph.gpu.TextPayloadLeakReport.v1"""")
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
    fun `non Skia sk words and mask fields do not fail leakage validation`() {
        val report = validateGPUTextNoSkLeakage(
            payloadKind = "ValuePayload",
            fields = listOf(
                TextPayloadField("transform", "SkewTransformFacts", "sketch diagnostics"),
                TextPayloadField("clip.maskPath", "String", "maskPath"),
                TextPayloadField("material.maskFont", "String", "maskFont"),
            ),
        )

        assertEquals("pass", report.status)
        assertTrue(report.findings.isEmpty())
    }
}
