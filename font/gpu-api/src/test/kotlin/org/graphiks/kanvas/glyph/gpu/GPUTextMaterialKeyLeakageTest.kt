package org.graphiks.kanvas.glyph.gpu

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class GPUTextMaterialKeyLeakageTest {
    @Test
    fun `material key leakage report matches repo golden`() {
        assertEquals(
            Files.readString(projectRoot().resolve("reports/pure-kotlin-text/text-material-key-leakage-report.json")).trimEnd(),
            defaultMaterialKeyLeakageReportJson().trimEnd(),
        )
    }

    @Test
    fun `glyph id variance does not change material identity`() {
        val cases = listOf(
            leakageCase(
                caseId = "glyph-id-variance-42",
                materialIdentifierValue = "material:text-black",
                forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
            ),
            leakageCase(
                caseId = "glyph-id-variance-99",
                materialIdentifierValue = "material:text-black",
                forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
            ),
            leakageCase(
                caseId = "glyph-id-variance-7",
                materialIdentifierValue = "material:text-black",
                forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
            ),
        )

        val report = validateGPUTextMaterialKeyLeakage(cases)
        assertEquals("all-clean", report.status)
        assertEquals(3, report.cleanCases)
        assertEquals(0, report.leakDetectedCases)
        assertTrue(report.leakageFindings.isEmpty())
    }

    @Test
    fun `atlas rect uv and coordinate variance does not change material identity`() {
        val cases = listOf(
            leakageCase(
                caseId = "atlas-rect-variance-topleft",
                materialIdentifierValue = "material:text-black",
                forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
            ),
            leakageCase(
                caseId = "atlas-rect-variance-bottomright",
                materialIdentifierValue = "material:text-black",
                forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
            ),
        )

        val report = validateGPUTextMaterialKeyLeakage(cases)
        assertEquals("all-clean", report.status)
        assertEquals(2, report.cleanCases)
        assertTrue(report.leakageFindings.isEmpty())
    }

    @Test
    fun `atlas generation variance does not change material identity`() {
        val cases = listOf(
            leakageCase(
                caseId = "generation-variance-1",
                materialIdentifierValue = "material:text-black",
                forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
            ),
            leakageCase(
                caseId = "generation-variance-42",
                materialIdentifierValue = "material:text-black",
                forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
            ),
        )

        val report = validateGPUTextMaterialKeyLeakage(cases)
        assertEquals("all-clean", report.status)
        assertEquals(2, report.cleanCases)
        assertTrue(report.leakageFindings.isEmpty())
    }

    @Test
    fun `upload token variance does not change material identity`() {
        val cases = listOf(
            leakageCase(
                caseId = "upload-token-variance-page-0",
                materialIdentifierValue = "material:text-black",
                forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
            ),
            leakageCase(
                caseId = "upload-token-variance-page-1",
                materialIdentifierValue = "material:text-black",
                forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
            ),
        )

        val report = validateGPUTextMaterialKeyLeakage(cases)
        assertEquals("all-clean", report.status)
        assertEquals(2, report.cleanCases)
        assertTrue(report.leakageFindings.isEmpty())
    }

    @Test
    fun `live texture handle variance does not change material identity`() {
        val cases = listOf(
            leakageCase(
                caseId = "live-handle-variance-texture-0",
                materialIdentifierValue = "material:text-black",
                forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
            ),
            leakageCase(
                caseId = "live-handle-variance-texture-1",
                materialIdentifierValue = "material:text-black",
                forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
            ),
        )

        val report = validateGPUTextMaterialKeyLeakage(cases)
        assertEquals("all-clean", report.status)
        assertEquals(2, report.cleanCases)
        assertTrue(report.leakageFindings.isEmpty())
    }

    @Test
    fun `legitimate material color change reflects different material identity`() {
        val cases = listOf(
            leakageCase(
                caseId = "color-text-black",
                materialIdentifierValue = "material:text-black",
                forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
            ),
            leakageCase(
                caseId = "color-text-red",
                materialIdentifierValue = "material:text-red",
                forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
            ),
        )

        assertEquals("material:text-black", cases[0].materialIdentifierValue)
        assertEquals("material:text-red", cases[1].materialIdentifierValue)
        assertFalse(cases[0].materialIdentifierValue == cases[1].materialIdentifierValue)

        val report = validateGPUTextMaterialKeyLeakage(cases)
        assertEquals("all-clean", report.status)
        assertTrue(report.leakageFindings.isEmpty())
    }

    @Test
    fun `negative fixture detects glyph id leak into material plan ref`() {
        val case = leakageCase(
            caseId = "negative-glyph-id-leak",
            materialIdentifierValue = "material:text-black:glyphId=42",
            forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
        )

        assertTrue(case.hasMaterialKeyLeak())
        assertEquals("leak-detected", case.actualLeakageStatus)

        val report = validateGPUTextMaterialKeyLeakage(listOf(case))
        assertEquals("leaks-detected", report.status)
        assertEquals(1, report.leakDetectedCases)
        assertEquals(0, report.cleanCases)
        assertEquals(1, report.leakageFindings.size)
        assertEquals("text.gpu.material-key-field-leaked:glyphId", report.leakageFindings[0].diagnostic)
    }

    @Test
    fun `negative fixture detects atlas rect leak into material plan ref`() {
        val case = leakageCase(
            caseId = "negative-atlas-rect-leak",
            materialIdentifierValue = "material:text-black:atlasRect=4,8,16,24",
            forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
        )

        assertTrue(case.hasMaterialKeyLeak())
        assertEquals("leak-detected", case.actualLeakageStatus)

        val report = validateGPUTextMaterialKeyLeakage(listOf(case))
        assertEquals("leaks-detected", report.status)
        assertEquals(1, report.leakDetectedCases)
    }

    @Test
    fun `negative fixture detects atlas generation leak into material plan ref`() {
        val case = leakageCase(
            caseId = "negative-generation-leak",
            materialIdentifierValue = "material:text-black:atlasGeneration=42",
            forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
        )

        assertTrue(case.hasMaterialKeyLeak())

        val report = validateGPUTextMaterialKeyLeakage(listOf(case))
        assertEquals("leaks-detected", report.status)
        assertEquals(1, report.leakageFindings.size)
    }

    @Test
    fun `negative fixture detects upload token leak into material plan ref`() {
        val case = leakageCase(
            caseId = "negative-upload-token-leak",
            materialIdentifierValue = "material:text-black:uploadToken=a8-page-0",
            forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
        )

        assertTrue(case.hasMaterialKeyLeak())

        val report = validateGPUTextMaterialKeyLeakage(listOf(case))
        assertEquals("leaks-detected", report.status)
    }

    @Test
    fun `negative fixture detects live handle leak into material plan ref`() {
        val case = leakageCase(
            caseId = "negative-live-handle-leak",
            materialIdentifierValue = "material:text-black:liveTextureHandle=wgpu-a8-page-0",
            forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle"),
        )

        assertTrue(case.hasMaterialKeyLeak())

        val report = validateGPUTextMaterialKeyLeakage(listOf(case))
        assertEquals("leaks-detected", report.status)
    }

    @Test
    fun `report is deterministic and non promotional`() {
        val json = defaultMaterialKeyLeakageReportJson()

        assertContains(json, """"ownerTickets":["KFONT-M11-010"]""")
        assertContains(json, """"classification":"GPU-gated"""")
        assertContains(json, """"routePromotion":"not-promoted"""")
        assertContains(json, """"productActivation":false""")
        assertContains(json, """"schema":"org.graphiks.kanvas.glyph.gpu.GPUTextMaterialKeyLeakageReport.v1"""")
        listOf("SkFont", "SkTypeface", "SkTextBlob", "fontBytes", "GPUHandle").forEach { token ->
            assertFalse(json.contains(token), "Report leaked forbidden token $token: $json")
        }
        assertEquals(json, defaultMaterialKeyLeakageReportJson())
    }

    @Test
    fun `binding plan fixture keeps atlas resource facts outside material identity`() {
        val binding = defaultGPUTextResourceContractEvidence().bindingPlan

        assertEquals("material:text-black", binding.materialPlanRef)
        assertContains(binding.materialKeyExcludedFields, "glyphId")
        assertContains(binding.materialKeyExcludedFields, "atlasRect")
        assertContains(binding.materialKeyExcludedFields, "atlasGeneration")
        assertContains(binding.materialKeyExcludedFields, "uploadToken")
        assertContains(binding.materialKeyExcludedFields, "liveTextureHandle")
        assertFalse(binding.materialPlanRef.contains("glyphId"))
        assertFalse(binding.materialPlanRef.contains("atlasRect"))
        assertFalse(binding.materialPlanRef.contains("upload"))
        assertFalse(binding.materialPlanRef.contains("generation"))
        assertFalse(binding.materialPlanRef.contains("textureHandle"))
    }

    @Test
    fun `report cases cover all required forbidden field categories`() {
        val cases = defaultMaterialKeyLeakageCases()

        val allForbiddenFields = cases.flatMap { c -> c.forbiddenFields }.distinct().sorted()
        assertContains(allForbiddenFields.joinToString(), "atlasGeneration")
        assertContains(allForbiddenFields.joinToString(), "atlasRect")
        assertContains(allForbiddenFields.joinToString(), "glyphId")
        assertContains(allForbiddenFields.joinToString(), "liveTextureHandle")
        assertContains(allForbiddenFields.joinToString(), "uploadToken")

        val cleanCases = cases.filter { c -> !c.hasMaterialKeyLeak() }
        val leakCases = cases.filter { c -> c.hasMaterialKeyLeak() }
        assertTrue(cleanCases.size > leakCases.size, "Most cases should be clean")
    }

    private fun projectRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (current.parent != null && !Files.isDirectory(current.resolve("reports/pure-kotlin-text"))) {
            current = current.parent
        }
        return current
    }

    private fun leakageCase(
        caseId: String,
        materialIdentifierValue: String,
        forbiddenFields: List<String>,
    ): GPUTextMaterialKeyLeakageCase {
        val cleanLabel = if (forbiddenFields.any { field ->
                materialIdentifierValue.normalizedMaterialKeyField().contains(field.normalizedMaterialKeyField())
            }
        ) "leak-detected" else "clean"

        return GPUTextMaterialKeyLeakageCase(
            caseId = caseId,
            scenarioLabel = "material-identity-${caseId}",
            materialIdentifierLabel = "materialPlanRef",
            materialIdentifierValue = materialIdentifierValue,
            forbiddenFields = forbiddenFields,
            expectedLeakageStatus = cleanLabel,
        )
    }
}

private fun String.normalizedMaterialKeyField(): String =
    filter { char -> char.isLetterOrDigit() }.lowercase()

fun defaultMaterialKeyLeakageCases(): List<GPUTextMaterialKeyLeakageCase> {
    val forbiddenFields = listOf("glyphId", "atlasRect", "atlasGeneration", "uploadToken", "liveTextureHandle")

    return listOf(
        GPUTextMaterialKeyLeakageCase(
            caseId = "glyph-id-baseline",
            scenarioLabel = "material-identity-variance",
            materialIdentifierLabel = "materialPlanRef",
            materialIdentifierValue = "material:text-black",
            forbiddenFields = forbiddenFields,
            expectedLeakageStatus = "clean",
        ),
        GPUTextMaterialKeyLeakageCase(
            caseId = "glyph-id-variant-42",
            scenarioLabel = "material-identity-variance",
            materialIdentifierLabel = "materialPlanRef",
            materialIdentifierValue = "material:text-black",
            forbiddenFields = forbiddenFields,
            expectedLeakageStatus = "clean",
        ),
        GPUTextMaterialKeyLeakageCase(
            caseId = "atlas-rect-variant",
            scenarioLabel = "material-identity-variance",
            materialIdentifierLabel = "materialPlanRef",
            materialIdentifierValue = "material:text-black",
            forbiddenFields = forbiddenFields,
            expectedLeakageStatus = "clean",
        ),
        GPUTextMaterialKeyLeakageCase(
            caseId = "generation-variant",
            scenarioLabel = "material-identity-variance",
            materialIdentifierLabel = "materialPlanRef",
            materialIdentifierValue = "material:text-black",
            forbiddenFields = forbiddenFields,
            expectedLeakageStatus = "clean",
        ),
        GPUTextMaterialKeyLeakageCase(
            caseId = "upload-token-variant",
            scenarioLabel = "material-identity-variance",
            materialIdentifierLabel = "materialPlanRef",
            materialIdentifierValue = "material:text-black",
            forbiddenFields = forbiddenFields,
            expectedLeakageStatus = "clean",
        ),
        GPUTextMaterialKeyLeakageCase(
            caseId = "live-handle-variant",
            scenarioLabel = "material-identity-variance",
            materialIdentifierLabel = "materialPlanRef",
            materialIdentifierValue = "material:text-black",
            forbiddenFields = forbiddenFields,
            expectedLeakageStatus = "clean",
        ),
        GPUTextMaterialKeyLeakageCase(
            caseId = "color-red-identity",
            scenarioLabel = "legitimate-material-change",
            materialIdentifierLabel = "materialPlanRef",
            materialIdentifierValue = "material:text-red",
            forbiddenFields = forbiddenFields,
            expectedLeakageStatus = "clean",
        ),
        GPUTextMaterialKeyLeakageCase(
            caseId = "negative-glyph-id-leak",
            scenarioLabel = "detected-leak",
            materialIdentifierLabel = "materialPlanRef",
            materialIdentifierValue = "material:text-black:glyphId=42",
            forbiddenFields = forbiddenFields,
            expectedLeakageStatus = "leak-detected",
        ),
        GPUTextMaterialKeyLeakageCase(
            caseId = "negative-atlas-rect-leak",
            scenarioLabel = "detected-leak",
            materialIdentifierLabel = "materialPlanRef",
            materialIdentifierValue = "material:text-black:atlasRect=4,8,16,24",
            forbiddenFields = forbiddenFields,
            expectedLeakageStatus = "leak-detected",
        ),
        GPUTextMaterialKeyLeakageCase(
            caseId = "negative-generation-leak",
            scenarioLabel = "detected-leak",
            materialIdentifierLabel = "materialPlanRef",
            materialIdentifierValue = "material:text-black:atlasGeneration=42",
            forbiddenFields = forbiddenFields,
            expectedLeakageStatus = "leak-detected",
        ),
        GPUTextMaterialKeyLeakageCase(
            caseId = "negative-upload-token-leak",
            scenarioLabel = "detected-leak",
            materialIdentifierLabel = "materialPlanRef",
            materialIdentifierValue = "material:text-black:uploadToken=a8-page-0",
            forbiddenFields = forbiddenFields,
            expectedLeakageStatus = "leak-detected",
        ),
        GPUTextMaterialKeyLeakageCase(
            caseId = "negative-live-handle-leak",
            scenarioLabel = "detected-leak",
            materialIdentifierLabel = "materialPlanRef",
            materialIdentifierValue = "material:text-black:liveTextureHandle=wgpu-a8-page-0",
            forbiddenFields = forbiddenFields,
            expectedLeakageStatus = "leak-detected",
        ),
    )
}

fun defaultMaterialKeyLeakageReportJson(): String {
    val cases = defaultMaterialKeyLeakageCases()
    val report = validateGPUTextMaterialKeyLeakage(cases)
    return report.toCanonicalJson()
}
