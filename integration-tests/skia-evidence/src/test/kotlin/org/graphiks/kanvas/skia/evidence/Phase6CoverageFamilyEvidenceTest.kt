package org.graphiks.kanvas.skia.evidence

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class Phase6CoverageFamilyEvidenceTest {
    @Test
    fun `classifies path fill simple as instrumented`() {
        val classified = Phase6CoverageFamilyClassifier.classify(row("cubicpath", family = "PATH"))

        assertEquals("instrumented-existing", classified.classification)
        assertEquals("path-fill-concave", classified.subfamily)
        assertEquals("none", classified.fallbackReason)
    }

    @Test
    fun `classifies stroke caps joins as instrumented when passing`() {
        val classified = Phase6CoverageFamilyClassifier.classify(row("strokedline_caps", family = "PATH"))

        assertEquals("instrumented-existing", classified.classification)
        assertEquals("path-stroke-caps-joins", classified.subfamily)
    }

    @Test
    fun `classifies failing dash rows as expected unsupported`() {
        val classified = Phase6CoverageFamilyClassifier.classify(
            row("dashing", family = "PATH", similarity = 40.0, isPassing = false),
        )

        assertEquals("expected-unsupported", classified.classification)
        assertEquals("path-dash-gated", classified.subfamily)
        assertEquals("unsupported.coverage.dash_pattern", classified.fallbackReason)
    }

    @Test
    fun `classifies failing path ops rows as expected unsupported`() {
        val classified = Phase6CoverageFamilyClassifier.classify(
            row("pathops_skbug_10155", family = "PATH", similarity = 10.0, isPassing = false),
        )

        assertEquals("expected-unsupported", classified.classification)
        assertEquals("path-ops-gated", classified.subfamily)
        assertEquals("unsupported.coverage.path_ops", classified.fallbackReason)
    }

    @Test
    fun `classifies large budget path rows with stable reason`() {
        val huge = Phase6CoverageFamilyClassifier.classify(
            row("path_huge_aa_manual", family = "PATH", similarity = null, isPassing = null),
        )
        val atlases = Phase6CoverageFamilyClassifier.classify(
            row("manypathatlases", family = "PATH", similarity = null, isPassing = null, noReference = true),
        )

        assertEquals("path-large-budget-gated", huge.subfamily)
        assertEquals("unsupported.coverage.verb_budget_exceeded", huge.fallbackReason)
        assertEquals("path-large-budget-gated", atlases.subfamily)
        assertEquals("unsupported.coverage.verb_budget_exceeded", atlases.fallbackReason)
    }

    @Test
    fun `classifies rect and rrect clips`() {
        val rect = Phase6CoverageFamilyClassifier.classify(row("windowrectangles", family = "CLIP"))
        val rrect = Phase6CoverageFamilyClassifier.classify(row("rrect_clip_aa", family = "CLIP"))

        assertEquals("clip-rect", rect.subfamily)
        assertEquals("clip-rrect", rrect.subfamily)
    }

    @Test
    fun `classifies nested bounded clip`() {
        val classified = Phase6CoverageFamilyClassifier.classify(row("clipdrawdraw", family = "CLIP"))

        assertEquals("instrumented-existing", classified.classification)
        assertEquals("clip-nested-bounded", classified.subfamily)
    }

    @Test
    fun `classifies inverse complex and perspective clips with stable reasons`() {
        val inverse = Phase6CoverageFamilyClassifier.classify(
            row("inverseclip", family = "CLIP", similarity = 5.0, isPassing = false),
        )
        val complex = Phase6CoverageFamilyClassifier.classify(
            row("complexclip_aa", family = "CLIP", similarity = 5.0, isPassing = false),
        )
        val perspective = Phase6CoverageFamilyClassifier.classify(
            row("perspective_clip", family = "CLIP", similarity = 5.0, isPassing = false),
        )

        assertEquals("clip-inverse-gated", inverse.subfamily)
        assertEquals("unsupported.coverage.inverse_clip", inverse.fallbackReason)
        assertEquals("clip-complex-gated", complex.subfamily)
        assertEquals("unsupported.coverage.complex_clip", complex.fallbackReason)
        assertEquals("clip-perspective-gated", perspective.subfamily)
        assertEquals("unsupported.coverage.perspective_clip", perspective.fallbackReason)
    }

    @Test
    fun `classifies no score separately from unexpected fail`() {
        val noScore = Phase6CoverageFamilyClassifier.classify(
            row("missing_path_reference", family = "PATH", similarity = null, isPassing = null, noReference = true),
        )
        val fail = Phase6CoverageFamilyClassifier.classify(
            row("plain_path_fail", family = "PATH", similarity = 20.0, isPassing = false),
        )

        assertEquals("no-score", noScore.classification)
        assertEquals("reference-missing", noScore.noScoreCause)
        assertEquals("unexpected-fail", fail.classification)
        assertEquals("none", fail.fallbackReason)
    }

    @Test
    fun `build evidence filters path and clip only`() {
        val evidence = Phase6CoverageFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T08:00:00",
                rows = listOf(
                    row("cubicpath", family = "PATH"),
                    row("aaclip", family = "CLIP"),
                    row("all_bitmap_configs", family = "IMAGE"),
                ),
            ),
        )

        assertEquals(2, evidence.summary.totalRows)
        assertEquals(mapOf("CLIP" to 1, "PATH" to 1), evidence.summary.families)
        assertEquals(listOf("cubicpath", "aaclip"), evidence.rows.map { it.name })
    }

    @Test
    fun `duplicate coverage rows receive stable row ids and surface them in csv and markdown`() {
        val evidence = Phase6CoverageFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T08:00:00",
                rows = listOf(
                    row("duplicate_path", family = "PATH"),
                    row("duplicate_path", family = "PATH", similarity = 20.0, isPassing = false),
                ),
            ),
        )

        assertEquals(listOf("duplicate_path", "duplicate_path#2"), evidence.rows.map { it.rowId })
        assertContains(evidence.toCsv(), "duplicate_path,duplicate_path,PATH,path-fill-simple,instrumented-existing")
        assertContains(evidence.toCsv(), "duplicate_path#2,duplicate_path,PATH,path-fill-simple,unexpected-fail")
        assertContains(evidence.toMarkdown(), "| `duplicate_path` | `duplicate_path` | `PATH` | `path-fill-simple` | `instrumented-existing` |")
        assertContains(evidence.toMarkdown(), "| `duplicate_path#2` | `duplicate_path` | `PATH` | `path-fill-simple` | `unexpected-fail` |")
    }

    @Test
    fun `markdown report includes separate path and clip deltas`() {
        val evidence = Phase6CoverageFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T08:00:00",
                rows = buildList {
                    repeat(94) { add(row("path_$it", family = "PATH")) }
                    repeat(47) { add(row("clip_$it", family = "CLIP")) }
                },
            ),
        )

        val markdown = evidence.toMarkdown()

        assertContains(markdown, "## Family Deltas")
        assertContains(markdown, "2026-07-08 local dashboard before #2010")
        assertContains(markdown, "| `PATH` | 58 | 94 | +36 |")
        assertContains(markdown, "| `CLIP` | 32 | 47 | +15 |")
    }

    @Test
    fun `coverage evidence json includes diff stats and family deltas`() {
        val evidence = Phase6CoverageFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T08:00:00",
                rows = listOf(
                    row(
                        "cubicpath",
                        family = "PATH",
                        hasDiff = true,
                        width = 320,
                        height = 240,
                        matchingPixels = 70000,
                        totalPixels = 76800,
                        maxDiff = GmRgbaInt(r = 12, g = 13, b = 14, a = 15),
                        meanDiff = GmRgbaDouble(r = 1.25, g = 2.5, b = 3.75, a = 4.0),
                    ),
                ),
            ),
        )

        val json = evidence.toJsonObject().toString()

        assertContains(json, "\"familyDeltas\":{\"CLIP\":{\"baselineSource\":\"2026-07-08 local dashboard before #2010\"")
        assertContains(json, "\"PATH\":{\"baselineSource\":\"2026-07-08 local dashboard before #2010\"")
        assertContains(json, "\"width\":320")
        assertContains(json, "\"height\":240")
        assertContains(json, "\"matchingPixels\":70000")
        assertContains(json, "\"totalPixels\":76800")
        assertContains(json, "\"maxDiff\":{\"r\":12,\"g\":13,\"b\":14,\"a\":15}")
        assertContains(json, "\"meanDiff\":{\"r\":1.25,\"g\":2.5,\"b\":3.75,\"a\":4.0}")
    }

    @Test
    fun `coverage writer creates json markdown and csv outputs`() {
        val evidence = Phase6CoverageFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T08:00:00",
                rows = listOf(row("cubicpath", family = "PATH"), row("aaclip", family = "CLIP")),
            ),
        )

        val root = kotlin.io.path.createTempDirectory("phase6-coverage-evidence")
        Phase6CoverageFamilyEvidenceWriter.writeOutputs(root, evidence)

        val evidencePath = root.resolve("reports/gpu-renderer/phase-6-coverage-families/evidence.json")
        val markdownPath = root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-coverage-families.md")
        val csvPath = root.resolve("reports/gpu-renderer/phase-6-coverage-families/classification.csv")

        assertEquals(true, java.nio.file.Files.isRegularFile(evidencePath))
        assertEquals(true, java.nio.file.Files.isRegularFile(markdownPath))
        assertEquals(true, java.nio.file.Files.isRegularFile(csvPath))
        assertContains(java.nio.file.Files.readString(evidencePath), "\"schemaVersion\": \"phase6-coverage-families-v1\"")
        assertContains(java.nio.file.Files.readString(evidencePath), "\"totalRows\": 2")
        assertContains(java.nio.file.Files.readString(markdownPath), "No broad Path AA support is claimed")
        assertContains(java.nio.file.Files.readString(markdownPath), "Coverage `unsupported.coverage.*` reason codes in this report are evidence refusal taxonomy only")
        assertContains(java.nio.file.Files.readString(csvPath), "cubicpath,cubicpath,PATH,path-fill-concave,instrumented-existing")
    }

    @Test
    fun `coverage writer preserves existing validation section when rewriting markdown`() {
        val evidence = Phase6CoverageFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-09T08:00:00",
                rows = listOf(row("cubicpath", family = "PATH"), row("aaclip", family = "CLIP")),
            ),
        )

        val root = kotlin.io.path.createTempDirectory("phase6-coverage-validation")
        val markdownPath = root.resolve("reports/gpu-renderer/2026-07-09-gpu-phase-6-coverage-families.md")
        Phase6CoverageFamilyEvidenceWriter.writeOutputs(root, evidence)
        java.nio.file.Files.writeString(
            markdownPath,
            java.nio.file.Files.readString(markdownPath) +
                """

                ## Validation

                - `:integration-tests:skia-evidence:test` passed.
                - `generateGpuPhase6CoverageFamiliesEvidence` regenerated evidence.
                """.trimIndent() +
                "\n",
        )

        Phase6CoverageFamilyEvidenceWriter.writeOutputs(root, evidence)

        val regenerated = java.nio.file.Files.readString(markdownPath)
        assertContains(regenerated, "## Validation")
        assertContains(regenerated, "- `:integration-tests:skia-evidence:test` passed.")
        assertContains(regenerated, "- `generateGpuPhase6CoverageFamiliesEvidence` regenerated evidence.")
    }
}

private fun row(
    name: String,
    family: String,
    similarity: Double? = 100.0,
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
        minSimilarity = 99.0,
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
