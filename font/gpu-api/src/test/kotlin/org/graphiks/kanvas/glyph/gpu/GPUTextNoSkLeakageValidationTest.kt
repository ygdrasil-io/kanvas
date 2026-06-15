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
}
