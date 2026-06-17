package org.graphiks.kanvas.text

import java.nio.file.Files
import java.nio.file.Path
import org.graphiks.kanvas.text.shaping.defaultFallbackShapedGlyphRunEvidenceJson
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class FallbackShapingEvidenceTest {
    @Test
    fun `fallback shaped glyph run dump matches repo golden`() {
        val actual = defaultFallbackShapedGlyphRunEvidenceJson()
        val expected = Files.readString(projectRoot().resolve("reports/font/fixtures/expected/shaping/fallback-shaped-glyph-run.json"))

        assertEquals(expected.trimEnd(), actual.trimEnd())
        assertContains(actual, """"dumpId": "fallback-shaped-glyph-run"""")
        assertContains(actual, """"fixtureId":"fallback-family-unavailable"""")
        assertContains(actual, """"decisionTraceRef":{"dumpId":"fallback-decision-trace","fixtureId":"fallback-script-arabic"}""")
        assertContains(actual, """"resolvedRunsRef":{"dumpId":"resolved-font-runs","fixtureId":"fallback-emoji-preference"}""")
        assertContains(actual, """"fixtureAssetRef":{"dumpId":"fallback-fixture","fixtureId":"fallback-missing-glyph"}""")
    }

    @Test
    fun `fallback shaped glyph run dump includes variable fallback fixtures`() {
        val actual = defaultFallbackShapedGlyphRunEvidenceJson()

        assertContains(actual, """"fixtureId":"fallback-axis-clamped"""")
        assertContains(actual, """"fixtureId":"fallback-axis-missing"""")
        assertContains(actual, """"fixtureId":"fallback-metrics-variation-missing"""")
        assertContains(actual, """"fixtureId":"fallback-named-instance"""")
        assertContains(actual, """"fixtureId":"fallback-multi-axis"""")
        assertContains(actual, """"fixtureId":"fallback-variable-cff2"""")
        assertContains(actual, """"decisionTraceRef":{"dumpId":"fallback-decision-trace","fixtureId":"fallback-variable-cff2"}""")
        assertContains(actual, """"resolvedRunsRef":{"dumpId":"resolved-font-runs","fixtureId":"fallback-axis-clamped"}""")
    }

    private fun projectRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (current.parent != null && !Files.isDirectory(current.resolve("reports/pure-kotlin-text"))) {
            current = current.parent
        }
        return current
    }
}
