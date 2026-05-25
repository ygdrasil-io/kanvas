package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

@Disabled(
    "STUB.FIXTURE / GM_REFERENCES: pure Kotlin COLR v1 rendering is covered " +
        "by synthetic unit fixtures, but this upstream GM still requires " +
        "(a) `fonts/test_glyphs-glyf_colr_1.ttf` + its " +
        "`_variable` sibling under `kanvas-legacy/src/test/resources/fonts/` " +
        "(not shipped), (b) accepted reference images, and (c) follow-up " +
        "coverage for COLR ItemVariationStore deltas. See #1020.",
)
class ColrV1Test {

    @Test
    fun `ColrV1GM matches reference`() {
        val gm = ColrV1GM()
        TestUtils.runGmTest(gm)
    }
}
