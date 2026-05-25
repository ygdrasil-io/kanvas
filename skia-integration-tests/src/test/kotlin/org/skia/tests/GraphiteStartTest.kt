package org.skia.tests

import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test

/**
 * `@Disabled` — [GraphiteStartGM] is a Graphite-private GM with two
 * call sites that the raster CPU `:kanvas-skia` backend cannot honour
 * faithfully :
 *
 *  - **`STUB.GRAPHITE.writePixels`** : the bottom-left tile uses
 *    `SkCanvas::writePixels(SkBitmap, …)` gated on
 *    `#if defined(SK_GRAPHITE)`, loading
 *    `images/color_wheel.gif` (which isn't shipped under
 *    `skia-integration-tests/src/test/resources/images/` anyway — only the
 *    `.png` and `.jpg` siblings). Raster CPU upstream also produces a
 *    black cell for this tile.
 *  - **`STUB.GRAPHITE.gaussianCF`** : slot `[4]` of the upper-right
 *    swatch grid uses `SkColorFilterPriv::MakeGaussian()` which is
 *    private to Skia (`src/core/SkColorFilterPriv.h`). The Kotlin port
 *    renders the gradient un-filtered for that slot.
 *
 * See [GraphiteStartGM] KDoc for the full degradation list. The visual
 * diff against the upstream Graphite reference would be dominated by
 * these two divergences ; the surrounding tiles render through the
 * standard raster pipeline.
 */
class GraphiteStartTest {

    @Test
    @Disabled(
        "STUB.GRAPHITE: GraphiteStartGM is Graphite-private upstream — " +
            "writePixels (bottom-left, gated on SK_GRAPHITE) and " +
            "SkColorFilterPriv::MakeGaussian (swatch slot [4]) have no " +
            "kanvas-skia equivalent ; the rest of the GM renders through " +
            "the raster pipeline. See GraphiteStartGM KDoc.",
    )
    fun `GraphiteStartGM renders the 3x3 tile grid except the Graphite-only tiles`() {
        // No-op : the GM body is portable except for two tagged tiles ;
        // we leave the test @Disabled until Graphite (or the private
        // Gaussian colour filter) lands in :kanvas-skia.
    }
}
