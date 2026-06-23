package org.graphiks.kanvas.glyph.gpu

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs

class TextWgslValidationTest {
    @Test
    fun `default text wgsl reports match repo goldens`() {
        val root = projectRoot()

        assertEquals(
            Files.readString(root.resolve("reports/pure-kotlin-text/text-wgsl-reflection.json")).trimEnd(),
            defaultTextWgslReflectionReportJson().trimEnd(),
        )
        assertEquals(
            Files.readString(root.resolve("reports/pure-kotlin-text/text-wgsl-validation-report.json")).trimEnd(),
            defaultTextWgslValidationReportJson().trimEnd(),
        )
    }

    @Test
    fun `accepted A8 text wgsl validation links reflected bindings to kotlin plans`() {
        val accepted = assertIs<TextWgslValidationPlanningResult.Accepted>(
            planTextWgslValidation(defaultTextWgslValidationFixture()),
        )
        val report = accepted.report

        assertEquals("text.a8-mask", report.moduleId)
        assertEquals("A8TextMaskStep", report.renderStep)
        assertEquals("gpu-text-binding-a8-0", report.bindingPlanId)
        assertEquals("gpu-text-resource-a8-0", report.resourcePlanId)
        assertEquals("gpu-text-ordering-a8-0", report.orderingTokenId)
        assertEquals("fnv1a64:text-a8-layout", report.bindingLayoutHash)
        assertEquals("fnv1a64:text-a8-instance-layout", report.instanceLayoutHash)
        assertContains(report.kotlinPlanComparisons.map { comparison -> comparison.comparisonId }, "binding:glyphAtlas")
        assertContains(report.kotlinPlanComparisons.map { comparison -> comparison.comparisonId }, "binding:glyphSampler")
        assertContains(report.kotlinPlanComparisons.map { comparison -> comparison.comparisonId }, "binding:textParams")
        assertContains(report.kotlinPlanComparisons.map { comparison -> comparison.comparisonId }, "instance-layout:text.a8-mask.instance-layout.v1")
        assertEquals(emptyList(), report.diagnostics)
    }

    @Test
    fun `text wgsl validator refuses parser binding sdf and registration failures`() {
        val cases = listOf(
            defaultTextWgslValidationFixture().copy(parserSuccess = false) to
                "wgsl4k.validation.syntax_error",
            defaultTextWgslValidationFixture().copy(bindingLayoutMatches = false) to
                "unsupported.wgsl.binding_reflection_mismatch",
            defaultTextWgslValidationFixture().copy(sdfParamsRequired = true, sdfParamsAvailable = false) to
                "unsupported.text.sdf_params_missing",
            defaultTextWgslValidationFixture().copy(moduleRegistered = false) to
                "unsupported.wgsl.unregistered_module",
        )

        cases.forEach { (fixture, expectedDiagnostic) ->
            val refused = assertIs<TextWgslValidationPlanningResult.Refused>(
                planTextWgslValidation(fixture),
            )

            assertEquals(expectedDiagnostic, refused.diagnostic.code)
            assertEquals("text.a8-mask", refused.diagnostic.moduleId)
            assertEquals("A8TextMaskStep", refused.diagnostic.renderStep)
            assertEquals("not-promoted", refused.diagnostic.routePromotion)
        }
    }

    @Test
    fun `text wgsl reports snapshot caller supplied lists`() {
        val comparisons = mutableListOf(
            TextWgslKotlinPlanComparison(
                comparisonId = "binding:glyphAtlas",
                kotlinPlanRef = "gpu-text-binding-a8-0",
                reflectedRef = "group=2,binding=0",
                expectedKind = "sampledTexture",
                reflectedKind = "sampledTexture",
                status = "accepted",
            ),
        )
        val report = TextWgslValidationReport(
            moduleId = "text.a8-mask",
            sourceId = "text/a8_text_mask.wgsl",
            moduleHash = "sha256:text-a8-mask",
            wgsl4kSha = "72a35b58758f241756d984a84768ae77308730da",
            renderStep = "A8TextMaskStep",
            route = "AtlasMaskSample",
            subRunId = "atlas-page-generation-split.0",
            resourcePlanId = "gpu-text-resource-a8-0",
            bindingPlanId = "gpu-text-binding-a8-0",
            bindingLayoutHash = "fnv1a64:text-a8-layout",
            instanceLayoutHash = "fnv1a64:text-a8-instance-layout",
            orderingTokenId = "gpu-text-ordering-a8-0",
            kotlinPlanComparisons = comparisons,
            diagnostics = emptyList(),
            refusals = emptyList(),
        )

        comparisons += TextWgslKotlinPlanComparison(
            comparisonId = "binding:glyphSampler",
            kotlinPlanRef = "gpu-text-binding-a8-0",
            reflectedRef = "group=2,binding=1",
            expectedKind = "sampler",
            reflectedKind = "sampler",
            status = "accepted",
        )

        assertEquals(listOf("binding:glyphAtlas"), report.kotlinPlanComparisons.map { comparison -> comparison.comparisonId })
    }

    @Test
    fun `text wgsl reports are deterministic and non promotional`() {
        val json = listOf(
            defaultTextWgslReflectionReportJson(),
            defaultTextWgslValidationReportJson(),
        ).joinToString(separator = "\n")

        assertContains(json, """"ownerTickets":["KFONT-M11-009"]""")
        assertContains(json, """"classification":"GPU-gated"""")
        assertContains(json, """"routePromotion":"not-promoted"""")
        assertContains(json, """"productActivation":false""")
        assertContains(json, """"wgslLanguage":"WGSL"""")
        assertContains(json, """"moduleId":"text.a8-mask"""")
        assertContains(json, """"code":"unsupported.text.sdf_params_missing"""")
        listOf("SkSL", "SkFont", "SkTypeface", "SkTextBlob", "fontBytes", "GPUHandle").forEach { token ->
            assertFalse(json.contains(token), "Text WGSL reports leaked forbidden token $token: $json")
        }
    }

    private fun projectRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (current.parent != null && !Files.isDirectory(current.resolve("reports/pure-kotlin-text"))) {
            current = current.parent
        }
        return current
    }
}
