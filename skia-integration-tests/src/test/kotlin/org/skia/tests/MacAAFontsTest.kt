package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

class MacAAFontsTest {
    /**
     * `MacAAFontsGM` is a CoreText/CoreGraphics-only test that has no
     * kanvas-skia equivalent (see [MacAAFontsGM] kdoc). The placeholder
     * implementation renders an empty canvas ; comparing it against the
     * upstream `macaatest.png` reference is meaningless, so the test is
     * permanently disabled.
     */
    @Disabled("Not portable : depends on CoreText / CoreGraphics (Mac-only API surface).")
    @Test
    fun `MacAAFontsGM is unsupported on kanvas-skia (CoreText-only)`() {
        // Intentionally empty.
    }
}
