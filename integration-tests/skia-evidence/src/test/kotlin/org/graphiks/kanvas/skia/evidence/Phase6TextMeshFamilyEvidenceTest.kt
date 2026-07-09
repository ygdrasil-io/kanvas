package org.graphiks.kanvas.skia.evidence

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class Phase6TextMeshFamilyEvidenceTest {
    @Test
    fun `build evidence filters text and mesh only`() {
        val evidence = Phase6TextMeshFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T12:00:00",
                rows = listOf(
                    row("bigtext", family = "TEXT"),
                    row("vertices", family = "MESH"),
                    row("srcmode", family = "COMPOSITE"),
                    row("linear_gradient", family = "GRADIENT"),
                ),
            ),
        )

        assertEquals(2, evidence.summary.totalRows)
        assertEquals(mapOf("MESH" to 1, "TEXT" to 1), evidence.summary.families)
        assertEquals(listOf("bigtext", "vertices"), evidence.rows.map { it.name })
        assertEquals("phase6-text-mesh-families-v1", evidence.schemaVersion)
    }

    @Test
    fun `classifies text subfamilies with stable reasons`() {
        val basic = classify("bigtext", "TEXT")
        val rsxform = classify("drawTextRSXform", "TEXT", similarity = null, isPassing = null, renderFailed = true)
        val perspective = classify("dftext_blob_persp", "TEXT", similarity = null, isPassing = null, renderFailed = true)
        val fontManager = classify("fontmgr_match", "TEXT")
        val emoji = classify("coloremoji", "TEXT", similarity = null, isPassing = null, noReference = true)
        val colorFont = classify("colrv1_gradient_stops_repeat", "TEXT")
        val shader = classify("chrome_gradtext2", "TEXT")
        val filter = classify("textfilter_image", "TEXT")
        val clip = classify("cliperror", "TEXT", similarity = null, isPassing = null, renderFailed = true)

        assertEquals("text-basic-latin", basic.subfamily)
        assertEquals("instrumented-existing", basic.classification)
        assertEquals("text-rsxform-gated", rsxform.subfamily)
        assertEquals("unsupported.text.rsxform", rsxform.fallbackReason)
        assertEquals("no-score", rsxform.classification)
        assertEquals("generated-render-missing", rsxform.noScoreCause)
        assertEquals("text-perspective-or-transform-gated", perspective.subfamily)
        assertEquals("unsupported.text.perspective", perspective.fallbackReason)
        assertEquals("no-score", perspective.classification)
        assertEquals("generated-render-missing", perspective.noScoreCause)
        assertEquals("text-font-manager-gated", fontManager.subfamily)
        assertEquals("unsupported.text.font_manager", fontManager.fallbackReason)
        assertEquals("expected-unsupported", fontManager.classification)
        assertEquals("text-emoji-gated", emoji.subfamily)
        assertEquals("unsupported.text.emoji", emoji.fallbackReason)
        assertEquals("text-color-font-gated", colorFont.subfamily)
        assertEquals("unsupported.text.color_font", colorFont.fallbackReason)
        assertEquals("text-shader-or-gradient-gated", shader.subfamily)
        assertEquals("unsupported.text.shader_or_gradient", shader.fallbackReason)
        assertEquals("text-filter-or-blur-gated", filter.subfamily)
        assertEquals("unsupported.text.filter_or_blur", filter.fallbackReason)
        assertEquals("text-clip-interaction-gated", clip.subfamily)
        assertEquals("unsupported.text.clip_interaction", clip.fallbackReason)
        assertEquals("no-score", clip.classification)
        assertEquals("generated-render-missing", clip.noScoreCause)
    }

    @Test
    fun `classifies mesh subfamilies with stable reasons`() {
        val vertices = classify("vertices", "MESH")
        val custom = classify("custommesh", "MESH")
        val customUniforms = classify("custommesh_cs_uniforms", "MESH", similarity = null, isPassing = null, renderFailed = true)
        val effects = classify("mesh_with_effects", "MESH", similarity = null, isPassing = null, renderFailed = true)
        val image = classify("mesh_with_image", "MESH", similarity = null, isPassing = null, renderFailed = true)
        val paintColor = classify("mesh_with_paint_color", "MESH", similarity = null, isPassing = null, renderFailed = true)
        val paintImage = classify("mesh_with_paint_image", "MESH", similarity = null, isPassing = null, renderFailed = true)
        val perspective = classify("vertices_perspective", "MESH", similarity = null, isPassing = null, renderFailed = true)
        val updates = classify("mesh_updates", "MESH")
        val zeroInit = classify("mesh_zero_init", "MESH", similarity = null, isPassing = null, renderFailed = true)
        val picture = classify("picture_mesh", "MESH")

        assertEquals("mesh-basic-vertices", vertices.subfamily)
        assertEquals("instrumented-existing", vertices.classification)
        assertEquals("mesh-custom-basic", custom.subfamily)
        assertEquals("instrumented-existing", custom.classification)
        assertEquals("mesh-custom-uniforms-gated", customUniforms.subfamily)
        assertEquals("unsupported.mesh.custom_uniforms", customUniforms.fallbackReason)
        assertEquals("no-score", customUniforms.classification)
        assertEquals("generated-render-missing", customUniforms.noScoreCause)
        assertEquals("mesh-effect-dependency-gated", effects.subfamily)
        assertEquals("unsupported.mesh.effect_dependency", effects.fallbackReason)
        assertEquals("no-score", effects.classification)
        assertEquals("generated-render-missing", effects.noScoreCause)
        assertEquals("mesh-image-dependency-gated", image.subfamily)
        assertEquals("unsupported.mesh.image_dependency", image.fallbackReason)
        assertEquals("no-score", image.classification)
        assertEquals("generated-render-missing", image.noScoreCause)
        assertEquals("mesh-paint-color-dependency-gated", paintColor.subfamily)
        assertEquals("unsupported.mesh.paint_color_dependency", paintColor.fallbackReason)
        assertEquals("no-score", paintColor.classification)
        assertEquals("generated-render-missing", paintColor.noScoreCause)
        assertEquals("mesh-paint-image-dependency-gated", paintImage.subfamily)
        assertEquals("unsupported.mesh.paint_image_dependency", paintImage.fallbackReason)
        assertEquals("no-score", paintImage.classification)
        assertEquals("generated-render-missing", paintImage.noScoreCause)
        assertEquals("mesh-perspective-gated", perspective.subfamily)
        assertEquals("unsupported.mesh.perspective", perspective.fallbackReason)
        assertEquals("no-score", perspective.classification)
        assertEquals("generated-render-missing", perspective.noScoreCause)
        assertEquals("mesh-update-or-dynamic-gated", updates.subfamily)
        assertEquals("unsupported.mesh.dynamic_updates", updates.fallbackReason)
        assertEquals("mesh-zero-init-gated", zeroInit.subfamily)
        assertEquals("unsupported.mesh.zero_init", zeroInit.fallbackReason)
        assertEquals("no-score", zeroInit.classification)
        assertEquals("generated-render-missing", zeroInit.noScoreCause)
        assertEquals("mesh-picture-dependency-gated", picture.subfamily)
        assertEquals("unsupported.mesh.picture_dependency", picture.fallbackReason)
    }

    @Test
    fun `separates no score from unexpected fail`() {
        val noScore = classify("coloremoji", "TEXT", similarity = null, isPassing = null, noReference = true)
        val fail = classify("plain_text_fail", "TEXT", similarity = 20.0, isPassing = false)

        assertEquals("no-score", noScore.classification)
        assertEquals("reference-missing", noScore.noScoreCause)
        assertEquals("unsupported.text.emoji", noScore.fallbackReason)
        assertEquals("unexpected-fail", fail.classification)
        assertEquals("none", fail.fallbackReason)
    }

    @Test
    fun `passing gated rows remain expected unsupported`() {
        val text = classify("fontmgr_match", "TEXT", similarity = 100.0, isPassing = true)
        val mesh = classify("mesh_updates", "MESH", similarity = 100.0, isPassing = true)

        assertEquals("expected-unsupported", text.classification)
        assertEquals("unsupported.text.font_manager", text.fallbackReason)
        assertEquals("expected-unsupported", mesh.classification)
        assertEquals("unsupported.mesh.dynamic_updates", mesh.fallbackReason)
    }

    @Test
    fun `build evidence computes family deltas from baseline`() {
        val evidence = Phase6TextMeshFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T12:00:00",
                rows = listOf(
                    row("bigtext", family = "TEXT"),
                    row("fontmgr_match", family = "TEXT"),
                    row("vertices", family = "MESH"),
                ),
            ),
        )

        assertEquals(77, evidence.summary.familyDeltas.getValue("TEXT").baselineCount)
        assertEquals(2, evidence.summary.familyDeltas.getValue("TEXT").currentCount)
        assertEquals(-75, evidence.summary.familyDeltas.getValue("TEXT").delta)
        assertEquals(16, evidence.summary.familyDeltas.getValue("MESH").baselineCount)
        assertEquals(1, evidence.summary.familyDeltas.getValue("MESH").currentCount)
        assertEquals(-15, evidence.summary.familyDeltas.getValue("MESH").delta)
    }

    @Test
    fun `duplicate rows receive stable row ids and surface them in csv and markdown`() {
        val evidence = Phase6TextMeshFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T12:00:00",
                rows = listOf(
                    row("vertices", family = "MESH"),
                    row("vertices", family = "MESH"),
                ),
            ),
        )

        assertEquals(listOf("vertices", "vertices#2"), evidence.rows.map { it.rowId })
        assertContains(evidence.toCsv(), "vertices#2,vertices,MESH")
        assertContains(evidence.toMarkdown(), "`vertices#2`")
    }

    @Test
    fun `evidence json includes diff stats no score cause and follow ups`() {
        val evidence = Phase6TextMeshFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T12:00:00",
                rows = listOf(
                    row("coloremoji", family = "TEXT", similarity = null, isPassing = null, noReference = true),
                    row("mesh_updates", family = "MESH", maxDiff = GmRgbaInt(1, 2, 3, 4), meanDiff = GmRgbaDouble(1.5, 2.5, 3.5, 4.5)),
                ),
            ),
        )

        val json = evidence.toJsonObject().toString()
        assertContains(json, "\"schemaVersion\":\"phase6-text-mesh-families-v1\"")
        assertContains(json, "\"noScoreCause\":\"reference-missing\"")
        assertContains(json, "\"maxDiff\":{\"r\":1,\"g\":2,\"b\":3,\"a\":4}")
        assertContains(json, "\"meanDiff\":{\"r\":1.5,\"g\":2.5,\"b\":3.5,\"a\":4.5}")
        assertContains(json, "\"followUpCandidates\"")
        assertContains(json, "\"unsupported.mesh.dynamic_updates\"")
    }

    @Test
    fun `writer creates json markdown and csv outputs and preserves validation section`() {
        val root = Files.createTempDirectory("phase6-text-mesh-evidence-test")
        val markdown = root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-text-mesh-families.md")
        Files.createDirectories(markdown.parent)
        Files.writeString(markdown, "# Existing\n")
        val evidence = Phase6TextMeshFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T12:00:00",
                rows = listOf(row("bigtext", family = "TEXT")),
            ),
        )
        val evidencePath = root.resolve("reports/gpu-renderer/phase-6-text-mesh-families/evidence.json")
        val csvPath = root.resolve("reports/gpu-renderer/phase-6-text-mesh-families/classification.csv")
        val sentinel = "SENTINEL BODY MUTATION"

        Phase6TextMeshFamilyEvidenceWriter.writeOutputs(root, evidence)

        assertContains(Files.readString(evidencePath), "phase6-text-mesh-families-v1")
        assertContains(Files.readString(csvPath), "rowId,name,family,subfamily,classification")
        assertContains(Files.readString(markdown), "No broad TEXT or MESH support is claimed from classification alone.")

        Files.writeString(
            markdown,
            Files.readString(markdown) +
                "\n$sentinel\n" +
                """

                ## Validation

                - `:integration-tests:skia-evidence:test` passed.
                - `generateGpuPhase6TextMeshFamiliesEvidence` regenerated evidence.
                """.trimIndent() +
                "\n",
        )

        Phase6TextMeshFamilyEvidenceWriter.writeOutputs(root, evidence)

        val regenerated = Files.readString(markdown)
        assertFalse(regenerated.contains(sentinel))
        assertContains(regenerated, "## Validation")
        assertContains(regenerated, "- `:integration-tests:skia-evidence:test` passed.")
        assertContains(regenerated, "- `generateGpuPhase6TextMeshFamiliesEvidence` regenerated evidence.")
    }

    @Test
    fun `non claims do not mention excluded support as complete`() {
        val evidence = Phase6TextMeshFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T12:00:00",
                rows = listOf(row("bigtext", family = "TEXT")),
            ),
        )

        val nonClaims = evidence.nonClaims.joinToString("\n")
        assertContains(nonClaims, "No broad TEXT or MESH support is claimed from classification alone.")
        assertContains(nonClaims, "shaping, font fallback, glyph atlas, glyph cache, color fonts, emoji, palettes, transformed text, text filters, and clip/text interactions remain outside this evidence wave unless row diagnostics prove a bounded route.")
        assertContains(nonClaims, "custom mesh, dynamic mesh updates, perspective mesh, picture mesh, image dependencies, paint-image dependencies, mesh effects, and arbitrary vertices remain outside this evidence wave unless row diagnostics prove a bounded route.")
        assertFalse(nonClaims.contains("complete support", ignoreCase = true))
    }

    private fun classify(
        name: String,
        family: String,
        similarity: Double? = 100.0,
        minSimilarity: Double? = 99.0,
        isPassing: Boolean? = true,
        noReference: Boolean = false,
        renderFailed: Boolean = false,
        sizeMismatch: Boolean = false,
        maxDiff: GmRgbaInt? = null,
        meanDiff: GmRgbaDouble? = null,
    ): Phase6TextMeshRowEvidence =
        Phase6TextMeshFamilyClassifier.classify(
            row(
                name = name,
                family = family,
                similarity = similarity,
                minSimilarity = minSimilarity,
                isPassing = isPassing,
                noReference = noReference,
                renderFailed = renderFailed,
                sizeMismatch = sizeMismatch,
                maxDiff = maxDiff,
                meanDiff = meanDiff,
            ),
        )

    private fun row(
        name: String,
        family: String,
        similarity: Double? = 100.0,
        minSimilarity: Double? = 99.0,
        isPassing: Boolean? = true,
        width: Int? = null,
        height: Int? = null,
        matchingPixels: Long? = null,
        totalPixels: Long? = null,
        maxDiff: GmRgbaInt? = null,
        meanDiff: GmRgbaDouble? = null,
        noReference: Boolean = false,
        renderFailed: Boolean = false,
        sizeMismatch: Boolean = false,
        hasDiff: Boolean = false,
    ): GmDashboardRow =
        GmDashboardRow(
            name = name,
            family = family,
            similarity = similarity,
            minSimilarity = minSimilarity,
            isPassing = isPassing,
            width = width,
            height = height,
            maxDiff = maxDiff,
            meanDiff = meanDiff,
            matchingPixels = matchingPixels,
            totalPixels = totalPixels,
            noReference = noReference,
            renderFailed = renderFailed,
            sizeMismatch = sizeMismatch,
            hasDiff = hasDiff,
        )
}
