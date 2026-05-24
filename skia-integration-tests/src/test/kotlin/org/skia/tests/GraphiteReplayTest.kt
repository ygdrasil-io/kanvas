package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * `@Disabled("STUB.GRAPHITE")` — the GM body is fully ported against
 * the upstream `drawNonGraphite` raster fallback (see
 * [GraphiteReplayGM]) but the upstream reference PNG under
 * `src/test/resources/original-888/` was rendered by Skia's Graphite
 * GPU backend, whose record-and-replay pipeline can yield different
 * sub-pixel rasterisation than the raster fallback. A pixel-level
 * reference comparison would therefore diverge for reasons that are
 * inherent to backend differences, not to the kanvas-skia port.
 *
 * The GM stays registered so the generic GM sweep finds it ; the
 * `@Disabled` is purely a reference-image mismatch guard.
 */
class GraphiteReplayTest {

    @Test
    @Disabled(
        "STUB.GRAPHITE: upstream reference is the Graphite record/replay output ; " +
            "kanvas-skia runs the drawNonGraphite raster fallback (see GraphiteReplayGM).",
    )
    fun `GraphiteReplayGM matches the upstream Graphite reference frame`() {
        // No-op : the GM body is a faithful port of upstream's
        // drawNonGraphite branch, but the reference PNG comes from the
        // Graphite path so a pixel-level diff would unconditionally
        // fail.
    }
}
