package org.graphiks.kanvas.text

import org.graphiks.kanvas.text.shaping.defaultSystemFontScanEvidenceBundle
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SystemFontScanTest {
    @Test
    fun `system font scan evidence bundle matches repo goldens`() {
        val bundle = defaultSystemFontScanEvidenceBundle()
        val root = projectRoot()

        assertEquals(
            Files.readString(root.resolve("reports/font/fixtures/expected/fallback/system-font-scan.json")).trimEnd(),
            bundle.systemFontScanJson.trimEnd(),
        )
        assertEquals(
            Files.readString(root.resolve("reports/font/fixtures/expected/fallback/system-font-scan-font-catalog-link.json")).trimEnd(),
            bundle.fontCatalogLinkJson.trimEnd(),
        )
        assertEquals(
            Files.readString(root.resolve("reports/font/fixtures/expected/fallback/system-font-scan-fallback-trace.json")).trimEnd(),
            bundle.fallbackTraceJson.trimEnd(),
        )
    }

    @Test
    fun `system font scan evidence stays host dependent and deterministic`() {
        val bundle = defaultSystemFontScanEvidenceBundle()

        assertContains(bundle.systemFontScanJson, """"dumpId": "system-font-scan"""")
        assertContains(bundle.systemFontScanJson, """"hostDependent": true""")
        assertContains(bundle.systemFontScanJson, """"diagnosticCode":"font.source.host-dependent"""")
        assertContains(bundle.systemFontScanJson, """"diagnosticCode":"font.source.unreadable"""")
        assertContains(bundle.systemFontScanJson, """"diagnosticCode":"font.outline-format.unsupported-wrapper"""")
        assertContains(bundle.systemFontScanJson, """"diagnosticCode":"font.catalog.duplicate-face"""")
        assertContains(bundle.systemFontScanJson, """"diagnosticCode":"font.required-table-missing"""")
        assertContains(bundle.fontCatalogLinkJson, """"linkedSystemScans"""")
        assertContains(bundle.fallbackTraceJson, """"selectedSourceKind":"SystemScannedFontSource"""")
        assertContains(bundle.fallbackTraceJson, """"selectedHostDependent":true""")
        assertFalse(bundle.systemFontScanJson.contains("HarfBuzz", ignoreCase = true))
        assertFalse(bundle.systemFontScanJson.contains("FreeType", ignoreCase = true))
        assertTrue(bundle.systemFontScanJson.indexOf("duplicate.ttf") < bundle.systemFontScanJson.indexOf("valid.ttf"))
    }

    @Test
    fun `host dependent system scan ticket is closed while broader fallback decisions stay explicit`() {
        val root = projectRoot()
        val ticket = Files.readString(
            root.resolve(".upstream/specs/pure-kotlin-text/tickets/M7-fallback-system-fonts/KFONT-M7-005-add-host-dependent-system-scan-diagnostics.md"),
        )
        val milestoneReadme = Files.readString(
            root.resolve(".upstream/specs/pure-kotlin-text/tickets/M7-fallback-system-fonts/README.md"),
        )
        val statusSummary = Files.readString(
            root.resolve(".upstream/specs/pure-kotlin-text/tickets/STATUS.md"),
        )
        val ticketReport = Files.readString(
            root.resolve("reports/pure-kotlin-text/2026-06-17-kfont-m7-005-host-dependent-system-scan.md"),
        )

        assertContains(ticket, """status: "done"""")
        assertContains(ticket, "host-dependent links should be folded")
        assertFalse(ticket.contains("Remaining gate before `done`"))
        assertContains(
            milestoneReadme,
            "| [KFONT-M7-005 - Add host-dependent system scan diagnostics](KFONT-M7-005-add-host-dependent-system-scan-diagnostics.md) | `done` |",
        )
        assertContains(statusSummary, "| M7 | 0 | 0 | 0 | 0 | 0 | 5 |")
        assertContains(ticketReport, "No ticket-local gate remains")
        assertContains(ticketReport, "reviewed product decision")
    }

    private fun projectRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (current.parent != null && !Files.isDirectory(current.resolve("reports/pure-kotlin-text"))) {
            current = current.parent
        }
        return current
    }
}
