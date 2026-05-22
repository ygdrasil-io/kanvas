package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * `@Disabled` — kanvas-skia has no Graphite renderer ; see
 * [GraphiteReplayGM] for the rationale. The GM is registered (so any
 * generic test sweep can find it) but the pixel-level reference
 * comparison is skipped because the upstream image is the Graphite
 * record/replay output and we would unconditionally fail it.
 */
class GraphiteReplayTest {

    @Test
    @Disabled("Requires Skia Graphite backend (record/replay) — not implemented in kanvas-skia.")
    fun `GraphiteReplayGM is unsupported on the kanvas-skia backend`() {
        // No-op : the GM exists but its onDraw is a black-frame stub.
    }
}
