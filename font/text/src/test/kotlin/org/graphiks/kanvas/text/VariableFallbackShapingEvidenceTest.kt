package org.graphiks.kanvas.text

import kotlin.test.Test
import kotlin.test.assertContains
import org.graphiks.kanvas.text.shaping.defaultFallbackShapedGlyphRunEvidenceJson

class VariableFallbackShapingEvidenceTest {
    @Test
    fun variableFallbackShapingDumpLinksVariableFallbackFixtures() {
        val actual = defaultFallbackShapedGlyphRunEvidenceJson()

        assertContains(actual, """"fixtureId":"fallback-axis-clamped"""")
        assertContains(actual, """"fixtureId":"fallback-axis-missing"""")
        assertContains(actual, """"fixtureId":"fallback-metrics-variation-missing"""")
        assertContains(actual, """"fixtureId":"fallback-variable-cff2"""")
    }
}
