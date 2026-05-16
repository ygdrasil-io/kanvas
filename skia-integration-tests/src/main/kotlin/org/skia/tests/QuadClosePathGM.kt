package org.skia.tests

import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/quadpaths.cpp::QuadClosePathGM` (1240 × 390).
 *
 * Closed quadratic-Bézier path (`moveTo(25, 10) ; quadTo(50, 20, 75, 10)
 * ; close()`) drawn into the [PathCapsFillsGridGM] matrix.
 */
public class QuadClosePathGM : PathCapsFillsGridGM(
    gmName = "quadclosepath",
    canvasSize = SkISize.Make(1240, 390),
    shape = SkPathBuilder()
        .moveTo(25f, 10f)
        .quadTo(50f, 20f, 75f, 10f)
        .close()
        .detach(),
    title = "Quad Closed Drawn Into Rectangle Clips With " +
            "Indicated Style, Fill and Linecaps, with stroke width 10",
)
