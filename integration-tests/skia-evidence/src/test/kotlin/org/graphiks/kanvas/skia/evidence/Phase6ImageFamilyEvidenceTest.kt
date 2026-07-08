package org.graphiks.kanvas.skia.evidence

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class Phase6ImageFamilyEvidenceTest {
    @Test
    fun `classifies passed image rect as instrumented until route evidence exists`() {
        val classified = Phase6ImageFamilyClassifier.classify(row("DrawBitmapRect3"))

        assertEquals("instrumented-existing", classified.classification)
        assertEquals("simple-image-rect", classified.subfamily)
        assertEquals("none", classified.fallbackReason)
        assertContains(classified.nonClaim, "route/cache/batching evidence missing")
    }

    @Test
    fun `classifies codec rows as expected unsupported`() {
        val classified = Phase6ImageFamilyClassifier.classify(
            row("AnimatedGif", similarity = 37.5, isPassing = false),
        )

        assertEquals("expected-unsupported", classified.classification)
        assertEquals("animation-gated", classified.subfamily)
        assertEquals("dependency.image.codec.unregistered", classified.fallbackReason)
    }

    @Test
    fun `classifies yuv rows with stable conversion reason`() {
        val classified = Phase6ImageFamilyClassifier.classify(
            row("YUV", similarity = 22.0, isPassing = false),
        )

        assertEquals("expected-unsupported", classified.classification)
        assertEquals("yuv-gated", classified.subfamily)
        assertEquals("unsupported.color.yuv_conversion", classified.fallbackReason)
    }

    @Test
    fun `draw image rect filter row stays in sampling family instead of image filter gate`() {
        val classified = Phase6ImageFamilyClassifier.classify(
            row("DrawimagerectFilter", similarity = 29.67, isPassing = false),
        )

        assertEquals("unexpected-fail", classified.classification)
        assertEquals("strict-nearest-linear", classified.subfamily)
        assertEquals("none", classified.fallbackReason)
    }

    @Test
    fun `bitmap filter quality repeat row stays in sampler policy family instead of image filter gate`() {
        val classified = Phase6ImageFamilyClassifier.classify(
            row("BmpFilterQualityRepeat", similarity = 0.0, isPassing = false),
        )

        assertEquals("unexpected-fail", classified.classification)
        assertEquals("sampler-policy-candidate", classified.subfamily)
        assertEquals("none", classified.fallbackReason)
    }

    @Test
    fun `local matrix image shader filtering row stays in local matrix family instead of image filter gate`() {
        val classified = Phase6ImageFamilyClassifier.classify(
            row("LocalMatrixImageShaderFiltering", similarity = 0.0, isPassing = false),
        )

        assertEquals("unexpected-fail", classified.classification)
        assertEquals("local-matrix-affine", classified.subfamily)
        assertEquals("none", classified.fallbackReason)
    }

    @Test
    fun `classifies no score separately from unexpected fail`() {
        val noScore = Phase6ImageFamilyClassifier.classify(
            row("MissingReferenceImage", similarity = null, isPassing = null, noReference = true),
        )
        val fail = Phase6ImageFamilyClassifier.classify(
            row("PlainImageFail", similarity = 40.0, isPassing = false),
        )

        assertEquals("no-score", noScore.classification)
        assertEquals("reference-missing", noScore.noScoreCause)
        assertEquals("unexpected-fail", fail.classification)
    }

    @Test
    fun `build evidence filters only image family`() {
        val dashboard = GmDashboard(
            generatedAt = "2026-07-08T21:00:00",
            rows = listOf(
                row("DrawBitmapRect3"),
                row("aaclip", family = "CLIP"),
            ),
        )

        val evidence = Phase6ImageFamilyClassifier.buildEvidence(dashboard)

        assertEquals(1, evidence.summary.totalImageRows)
        assertEquals(1, evidence.summary.classifications["instrumented-existing"])
        assertEquals("DrawBitmapRect3", evidence.rows.single().name)
    }

    @Test
    fun `dashboard reader keeps path clip flags for shared evidence`() {
        val root = kotlin.io.path.createTempDirectory("phase6-shared-dashboard")
        val dashboard = root.resolve("gms.json")
        java.nio.file.Files.writeString(
            dashboard,
            """
            {
              "generatedAt": "2026-07-09T08:00:00",
              "gms": [
                {
                  "name": "complexclip_aa",
                  "family": "CLIP",
                  "similarity": null,
                  "minSimilarity": 99.0,
                  "isPassing": null,
                  "noReference": false,
                  "renderFailed": false,
                  "sizeMismatch": true,
                  "hasDiff": false
                }
              ]
            }
            """.trimIndent(),
        )

        val loaded = GmDashboardJsonReader.read(dashboard)

        assertEquals("2026-07-09T08:00:00", loaded.generatedAt)
        assertEquals("complexclip_aa", loaded.rows.single().name)
        assertEquals("CLIP", loaded.rows.single().family)
        assertEquals(true, loaded.rows.single().sizeMismatch)
    }

    @Test
    fun `duplicate image rows receive stable row ids and surface them in csv and markdown`() {
        val evidence = Phase6ImageFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-08T21:00:00",
                rows = listOf(
                    row("duplicate"),
                    row("aaclip", family = "CLIP"),
                    row("duplicate", similarity = 40.0, isPassing = false),
                ),
            ),
        )

        assertEquals(listOf("duplicate", "duplicate#2"), evidence.rows.map { it.rowId })
        assertEquals(listOf("duplicate", "duplicate"), evidence.rows.map { it.name })
        assertContains(evidence.toCsv(), "duplicate,duplicate,texture-cache-candidate,instrumented-existing")
        assertContains(evidence.toCsv(), "duplicate#2,duplicate,texture-cache-candidate,unexpected-fail")
        assertContains(evidence.toMarkdown(), "| `duplicate` | `duplicate` | `texture-cache-candidate` | `instrumented-existing` |")
        assertContains(evidence.toMarkdown(), "| `duplicate#2` | `duplicate` | `texture-cache-candidate` | `unexpected-fail` |")
    }

    @Test
    fun `writer creates json markdown and csv outputs`() {
        val evidence = Phase6ImageFamilyClassifier.buildEvidence(
            GmDashboard(
                generatedAt = "2026-07-08T21:00:00",
                rows = listOf(row("DrawBitmapRect3")),
            ),
        )

        val root = kotlin.io.path.createTempDirectory("phase6-image-evidence")
        Phase6ImageFamilyEvidenceWriter.writeOutputs(root, evidence)

        val evidencePath = root.resolve("reports/gpu-renderer/phase-6-image-family/evidence.json")
        val markdownPath = root.resolve("reports/gpu-renderer/2026-07-08-gpu-phase-6-image-family.md")
        val csvPath = root.resolve("reports/gpu-renderer/phase-6-image-family/classification.csv")

        assertEquals(true, java.nio.file.Files.isRegularFile(evidencePath))
        assertEquals(true, java.nio.file.Files.isRegularFile(markdownPath))
        assertEquals(true, java.nio.file.Files.isRegularFile(csvPath))
        assertContains(java.nio.file.Files.readString(evidencePath), "\"schemaVersion\": \"phase6-image-family-v1\"")
        assertContains(java.nio.file.Files.readString(evidencePath), "\"rowId\": \"DrawBitmapRect3\"")
        assertContains(java.nio.file.Files.readString(markdownPath), "No broad IMAGE support is claimed")
        assertContains(java.nio.file.Files.readString(csvPath), "DrawBitmapRect3,DrawBitmapRect3,simple-image-rect,instrumented-existing")
    }

    @Test
    fun `resource evidence is attached when present`() {
        val evidence = Phase6ImageFamilyClassifier.buildEvidence(
            dashboard = GmDashboard("2026-07-08T21:00:00", listOf(row("DrawBitmapRect3"))),
            resourceEvidence = ResourceEvidence(
                rowId = "phase6-image-repeated-texture-sampler",
                dumpLines = listOf("resource-provider.cache lane=texture-sampler result=create key=k subject=s"),
                nonClaims = listOf("no-broad-image-support"),
            ),
        )

        assertEquals("phase6-image-repeated-texture-sampler", evidence.resourceEvidence?.rowId)
        assertContains(evidence.toMarkdown(), "## Resource And Cache Evidence")
        assertContains(evidence.toMarkdown(), "resource-provider.cache lane=texture-sampler result=create")
    }

    @Test
    fun `resource evidence reader loads optional json file`() {
        val root = kotlin.io.path.createTempDirectory("phase6-resource-evidence")
        val evidencePath = root.resolve("reports/gpu-renderer/phase-6-image-family/resource-evidence.json")
        java.nio.file.Files.createDirectories(evidencePath.parent)
        java.nio.file.Files.writeString(
            evidencePath,
            """
            {
              "rowId": "phase6-image-repeated-texture-sampler",
              "dumpLines": ["resource-provider.cache lane=texture-sampler result=create key=k subject=s"],
              "nonClaims": ["no-broad-image-support"]
            }
            """.trimIndent(),
        )

        val loaded = ResourceEvidenceReader.readIfPresent(root)

        assertEquals("phase6-image-repeated-texture-sampler", loaded?.rowId)
        assertEquals(listOf("no-broad-image-support"), loaded?.nonClaims)
    }
}

private fun row(
    name: String,
    family: String = "IMAGE",
    similarity: Double? = 100.0,
    isPassing: Boolean? = true,
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
        noReference = noReference,
        renderFailed = renderFailed,
        sizeMismatch = sizeMismatch,
        hasDiff = hasDiff,
    )
