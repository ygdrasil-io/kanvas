package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.skia.testing.TestUtils

/**
 * Tests for [FontationsGM] — ports of upstream's three `DEF_GM`
 * registrations in `gm/fontations.cpp`.
 *
 * All three methods are `@Disabled` because:
 *  1. [org.skia.foundation.SkTypeface_Fontations.MakeFromStream] throws
 *     `STUB.FONTATIONS` — the Fontations Rust crate is not wired in.
 *  2. The pure Kotlin OpenType backend exposes basic name-table data,
 *     but this GM specifically targets the upstream Fontations factory
 *     path and variable-axis behavior, which remains a native bridge stub.
 *
 * See `API_FINALIZATION_PLAN.md` § STUB.FONTATIONS.
 */
@Disabled("STUB.FONTATIONS: requires Fontations Rust crate via UniFFI/JNI — see API_FINALIZATION_PLAN.md")
class FontationsTest {

    @Test
    fun `typeface_fontations_roboto matches reference`() {
        val gm = FontationsGM.makeRoboto()
        TestUtils.runGmTest(gm)
    }

    @Test
    fun `typeface_fontations_distortable_light matches reference`() {
        val gm = FontationsGM.makeDistortableLight()
        TestUtils.runGmTest(gm)
    }

    @Test
    fun `typeface_fontations_distortable_bold matches reference`() {
        val gm = FontationsGM.makeDistortableBold()
        TestUtils.runGmTest(gm)
    }
}
