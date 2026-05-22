package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * `@Disabled` — kanvas-skia has no Graphite renderer. See
 * [GraphiteStartGM] for the rationale.
 */
class GraphiteStartTest {

    @Test
    @Disabled("Requires Skia Graphite backend — not implemented in kanvas-skia.")
    fun `GraphiteStartGM is unsupported on the kanvas-skia backend`() {
        // No-op : the GM exists but its onDraw is a black-frame stub.
    }
}
