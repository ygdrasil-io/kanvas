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

    private fun projectRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (current.parent != null && !Files.isDirectory(current.resolve("reports/pure-kotlin-text"))) {
            current = current.parent
        }
        return current
    }
}
