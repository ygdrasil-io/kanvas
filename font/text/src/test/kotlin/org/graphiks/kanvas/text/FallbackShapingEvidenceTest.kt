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

    private fun projectRoot(): Path {
        var current = Path.of("").toAbsolutePath().normalize()
        while (current.parent != null && !Files.isDirectory(current.resolve("reports/pure-kotlin-text"))) {
            current = current.parent
        }
        return current
    }
}
