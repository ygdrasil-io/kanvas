package org.graphiks.kanvas.font

import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FontTelemetrySchemaTest {
    @Test
    fun `font telemetry schema and fixture dumps match repo goldens`() {
        val root = projectRoot()
        val expectedSchema = Files.readString(root.resolve("reports/pure-kotlin-text/font-telemetry-schema.json"))
        val expectedFixture = Files.readString(root.resolve("reports/pure-kotlin-text/font-telemetry-schema-fixture.json"))
        val bundle = FontTelemetryEvidenceWriter.writeBundle()

        assertEquals(expectedSchema.trimEnd(), bundle.schemaJson.trimEnd())
        assertEquals(expectedFixture.trimEnd(), bundle.fixtureJson.trimEnd())
    }

    @Test
    fun `font telemetry schema covers every required domain and stable refusal diagnostics`() {
        val bundle = FontTelemetryEvidenceWriter.writeBundle()

        assertContains(bundle.schemaJson, """"schemaId": "font-telemetry-schema"""")
        assertContains(bundle.schemaJson, """"domain": "parser"""")
        assertContains(bundle.schemaJson, """"domain": "scaler"""")
        assertContains(bundle.schemaJson, """"domain": "shaping"""")
        assertContains(bundle.schemaJson, """"domain": "paragraph"""")
        assertContains(bundle.schemaJson, """"domain": "glyph-artifact"""")
        assertContains(bundle.schemaJson, """"domain": "gpu-text-handoff"""")
        assertContains(bundle.schemaJson, """"font.telemetry.schema-domain-missing"""")
        assertContains(bundle.schemaJson, """"font.telemetry.dimension-missing"""")
        assertContains(bundle.schemaJson, """"font.telemetry.single-run-budget-refused"""")
        assertFalse(bundle.schemaJson.contains("HarfBuzz", ignoreCase = true))
        assertFalse(bundle.schemaJson.contains("FreeType", ignoreCase = true))
    }

    @Test
    fun `telemetry fixture keeps GPU adapter facts conditional and repeated samples advisory only`() {
        val bundle = FontTelemetryEvidenceWriter.writeBundle()

        assertContains(bundle.fixtureJson, """"fixtureId": "telemetry-parser-repeat"""")
        assertContains(bundle.fixtureJson, """"fixtureId": "telemetry-gpu-handoff-repeat"""")
        assertContains(bundle.fixtureJson, """"measurementPhase": "steady-state"""")
        assertContains(bundle.fixtureJson, """"sampleCount": 5""")
        assertContains(bundle.fixtureJson, """"median": 1200000""")
        assertContains(bundle.fixtureJson, """"p90": 1600000""")
        assertContains(bundle.fixtureJson, """"max": 1800000""")
        assertContains(bundle.fixtureJson, """"gpuAdapter":"wgpu-nvidia-rtx-3070"""")
        assertContains(bundle.fixtureJson, """"diagnosticCode": "font.telemetry.single-run-budget-refused"""")
        assertContains(bundle.fixtureJson, """"diagnosticCode": "font.telemetry.dimension-missing"""")
        assertTrue(bundle.fixtureJson.indexOf("telemetry-parser-repeat") < bundle.fixtureJson.indexOf("telemetry-gpu-handoff-repeat"))
    }

    @Test
    fun `font telemetry PM bundle evidence stays advisory and domain-complete`() {
        val root = projectRoot()
        val advisoryJson = Files.readString(root.resolve("reports/pure-kotlin-text/font-telemetry-pm-bundle.json"))
        val advisoryMarkdown = Files.readString(
            root.resolve("reports/pure-kotlin-text/2026-06-17-kfont-m12-001-telemetry-pm-bundle.md"),
        )
        val buildGradle = Files.readString(root.resolve("build.gradle.kts"))

        assertContains(advisoryJson, """"ownerTickets": ["KFONT-M12-001"]""")
        assertContains(advisoryJson, """"surfaceId": "font-telemetry-schema"""")
        assertContains(advisoryJson, """"classification": "tracked-gap"""")
        assertContains(advisoryJson, """"claimPromotionAllowed": false""")
        assertContains(advisoryJson, """"pmBundleTask": "pipelinePmBundle"""")
        assertContains(advisoryJson, """"warningMode": "advisory"""")
        assertContains(advisoryJson, """"domain": "parser"""")
        assertContains(advisoryJson, """"domain": "gpu-text-handoff"""")
        assertContains(advisoryJson, """"bundlePaths": [""")
        assertContains(advisoryJson, """"reports/pure-kotlin-text/parser-metrics.json"""")
        assertContains(advisoryJson, """"reports/pure-kotlin-text/scaler-metrics.json"""")
        assertContains(advisoryMarkdown, "pipelinePmBundle")
        assertContains(advisoryMarkdown, "parser-metrics.json")
        assertContains(advisoryMarkdown, "scaler-metrics.json")
        assertContains(advisoryMarkdown, "tracked-gap")
        assertContains(advisoryMarkdown, "warning-only")
        assertContains(advisoryMarkdown, "KFONT-M12-005")
        assertContains(advisoryMarkdown, "KFONT-M12-003")
        assertContains(buildGradle, "\"reports/pure-kotlin-text/parser-metrics.json\"")
        assertContains(buildGradle, "\"reports/pure-kotlin-text/scaler-metrics.json\"")
        assertFalse(advisoryMarkdown.contains("remains open before `done`"))
    }

    @Test
    fun `font telemetry schema ticket is closed while downstream telemetry slices stay explicit`() {
        val root = projectRoot()
        val ticket = Files.readString(
            root.resolve(".upstream/specs/pure-kotlin-text/tickets/M12-performance-telemetry/KFONT-M12-001-define-font-telemetry-schema.md"),
        )
        val milestoneReadme = Files.readString(
            root.resolve(".upstream/specs/pure-kotlin-text/tickets/M12-performance-telemetry/README.md"),
        )
        val statusSummary = Files.readString(
            root.resolve(".upstream/specs/pure-kotlin-text/tickets/STATUS.md"),
        )
        val schemaReport = Files.readString(
            root.resolve("reports/pure-kotlin-text/2026-06-16-kfont-m12-001-font-telemetry-schema.md"),
        )

        assertContains(ticket, """status: "done"""")
        assertContains(ticket, "KFONT-M12-005")
        assertContains(ticket, "KFONT-M12-003")
        assertFalse(ticket.contains("producer-side subsystem wiring remains open before `done`"))
        assertContains(
            milestoneReadme,
            "| [KFONT-M12-001 - Define font telemetry schema](KFONT-M12-001-define-font-telemetry-schema.md) | `done` |",
        )
        assertContains(statusSummary, "| M12 | 3 | 0 | 0 | 0 | 0 | 2 |")
        assertContains(schemaReport, "No schema-local gate remains")
        assertContains(schemaReport, "KFONT-M12-005")
        assertContains(schemaReport, "KFONT-M12-003")
    }

    @Test
    fun `parser and scaler telemetry dumps close KFONT-M12-002 without promoting performance claims`() {
        val root = projectRoot()
        val expectedParser = Files.readString(root.resolve("reports/pure-kotlin-text/parser-metrics.json"))
        val expectedScaler = Files.readString(root.resolve("reports/pure-kotlin-text/scaler-metrics.json"))
        val dashboard = Files.readString(root.resolve("reports/pure-kotlin-text/font-claim-dashboard.json"))
        val ticket = Files.readString(
            root.resolve(".upstream/specs/pure-kotlin-text/tickets/M12-performance-telemetry/KFONT-M12-002-add-parser-and-scaler-metrics.md"),
        )
        val milestoneReadme = Files.readString(
            root.resolve(".upstream/specs/pure-kotlin-text/tickets/M12-performance-telemetry/README.md"),
        )
        val statusSummary = Files.readString(
            root.resolve(".upstream/specs/pure-kotlin-text/tickets/STATUS.md"),
        )
        val report = Files.readString(
            root.resolve("reports/pure-kotlin-text/2026-06-17-kfont-m12-002-parser-scaler-metrics.md"),
        )

        assertEquals(expectedParser.trimEnd(), FontTelemetryEvidenceWriter.writeParserMetricsJson().trimEnd())
        assertEquals(expectedScaler.trimEnd(), FontTelemetryEvidenceWriter.writeScalerMetricsJson().trimEnd())
        assertContains(dashboard, """"surfaceId": "font-parser-metrics"""")
        assertContains(dashboard, """"label": "Font parser metrics"""")
        assertContains(dashboard, """"surfaceId": "font-scaler-metrics"""")
        assertContains(dashboard, """"label": "Font scaler metrics"""")
        assertContains(dashboard, "KFONT-M12-003, KFONT-M12-004, and KFONT-M12-005 own shaping/paragraph/glyph/GPU producer emission")
        assertFalse(dashboard.contains("KFONT-M12-002, KFONT-M12-003, KFONT-M12-004, and KFONT-M12-005 own parser/scaler/shaping/paragraph/glyph/GPU producer emission"))
        assertContains(ticket, """status: "done"""")
        assertContains(ticket, "font.parser.parse.time")
        assertContains(ticket, "font.scaler.outline.time")
        assertContains(
            milestoneReadme,
            "| [KFONT-M12-002 - Add parser and scaler metrics](KFONT-M12-002-add-parser-and-scaler-metrics.md) | `done` |",
        )
        assertContains(statusSummary, "| M12 | 3 | 0 | 0 | 0 | 0 | 2 |")
        assertContains(report, "No ticket-local gate remains")
        assertContains(report, "font.parser.parse.time")
        assertContains(report, "font.scaler.outline.time")
        assertContains(expectedParser, """"fixtureId": "font-source-sfnt-malformed-directory-diagnostic"""")
        assertContains(report, "malformed-directory")
        assertContains(report, "pipelinePerformanceTrendWarnings")
        assertContains(report, "no-performance-release-gate-claim")
    }

    @Test
    fun `shaping and paragraph telemetry dumps close KFONT-M12-003 without promoting performance claims`() {
        val root = projectRoot()
        val expectedShaping = Files.readString(root.resolve("reports/pure-kotlin-text/shaping-metrics.json"))
        val expectedParagraph = Files.readString(root.resolve("reports/pure-kotlin-text/paragraph-metrics.json"))
        val ticket = Files.readString(
            root.resolve(".upstream/specs/pure-kotlin-text/tickets/M12-performance-telemetry/KFONT-M12-003-add-shaping-and-paragraph-metrics.md"),
        )
        val milestoneReadme = Files.readString(
            root.resolve(".upstream/specs/pure-kotlin-text/tickets/M12-performance-telemetry/README.md"),
        )
        val statusSummary = Files.readString(
            root.resolve(".upstream/specs/pure-kotlin-text/tickets/STATUS.md"),
        )
        val report = Files.readString(
            root.resolve("reports/pure-kotlin-text/2026-06-19-kfont-m12-003-shaping-paragraph-metrics.md"),
        )

        assertEquals(expectedShaping.trimEnd(), FontTelemetryEvidenceWriter.writeShapingMetricsJson().trimEnd())
        assertEquals(expectedParagraph.trimEnd(), FontTelemetryEvidenceWriter.writeParagraphMetricsJson().trimEnd())
        assertContains(expectedShaping, """"dumpId": "shaping-metrics"""")
        assertContains(expectedShaping, """"fixtureId": "telemetry-shaping-repeat"""")
        assertContains(expectedShaping, """"text.shaping.emoji-sequence-unsupported"""")
        assertContains(expectedParagraph, """"dumpId": "paragraph-metrics"""")
        assertContains(expectedParagraph, """"fixtureId": "paragraph-shaping-requests"""")
        assertContains(expectedParagraph, """"text.paragraph.placeholder-ellipsis-conflict"""")
        assertContains(ticket, """status: "done"""")
        assertContains(
            milestoneReadme,
            "| [KFONT-M12-003 - Add shaping and paragraph metrics](KFONT-M12-003-add-shaping-and-paragraph-metrics.md) | `done` |",
        )
        assertContains(statusSummary, "| M12 | 3 | 0 | 0 | 0 | 0 | 2 |")
        assertContains(report, "No ticket-local gate remains")
        assertContains(report, "tracked-gap")
        assertFalse(report.contains("claim promotion", ignoreCase = true))
    }

    private fun projectRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (current.parent != null && !Files.isDirectory(current.resolve("reports/pure-kotlin-text"))) {
            current = current.parent
        }
        return current
    }
}
