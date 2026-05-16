package org.skia.tests

import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/cubicpaths.cpp::CubicClosePathGM` (1240 × 390).
 *
 * Closed cubic path (`moveTo(25, 10) ; cubicTo(40, 20, 60, 20, 75, 10)
 * ; close()`) drawn into the [PathCapsFillsGridGM] matrix : 3 cap/join
 * combos × 4 fill rules (incl. `kInverse*`) × 3 paint styles (fill /
 * stroke / strokeAndFill).
 */
public class CubicClosePathGM : PathCapsFillsGridGM(
    gmName = "cubicclosepath",
    canvasSize = SkISize.Make(1240, 390),
    shape = SkPathBuilder()
        .moveTo(25f, 10f)
        .cubicTo(40f, 20f, 60f, 20f, 75f, 10f)
        .close()
        .detach(),
    title = "Cubic Closed Drawn Into Rectangle Clips With " +
            "Indicated Style, Fill and Linecaps, with stroke width 10",
)
