package org.skia.tests

import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/quadpaths.cpp::QuadPathGM` (1240 × 390).
 *
 * Open quadratic-Bézier path (`moveTo(25, 10) ; quadTo(50, 20, 75, 10)`)
 * drawn into the [PathCapsFillsGridGM] matrix : 3 cap/join combos × 4
 * fill rules (incl. `kInverse*`) × 3 paint styles.
 */
public class QuadPathGM : PathCapsFillsGridGM(
    gmName = "quadpath",
    canvasSize = SkISize.Make(1240, 390),
    shape = SkPathBuilder()
        .moveTo(25f, 10f)
        .quadTo(50f, 20f, 75f, 10f)
        .detach(),
    title = "Quad Drawn Into Rectangle Clips With " +
            "Indicated Style, Fill and Linecaps, with stroke width 10",
)
