package org.skia.tests

import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint

/**
 * Port of Skia's `gm/cubicpaths.cpp::CubicPathShaderGM` (1240 × 390).
 *
 * Open cubic path (`moveTo(25, 10) ; cubicTo(40, 20, 60, 20, 75, 10)`)
 * drawn into the [PathCapsFillsGridGM] matrix, but each cell uses a
 * **3-stop linear gradient** as the paint shader instead of a solid
 * colour. Stops are sampled along the diagonal `(0, 0) → (50, 50)` :
 * `0x80F00080` (translucent magenta) → `0xF0F08000` (near-opaque amber)
 * → `0x800080F0` (translucent blue), at `t ∈ {0, 0.5, 1}`.
 */
public class CubicPathShaderGM : PathCapsFillsGridGM(
    gmName = "cubicpath_shader",
    canvasSize = SkISize.Make(1240, 390),
    shape = SkPathBuilder()
        .moveTo(25f, 10f)
        .cubicTo(40f, 20f, 60f, 20f, 75f, 10f)
        .detach(),
    title = "Cubic Drawn Into Rectangle Clips With " +
            "Indicated Style, Fill and Linecaps, with stroke width 10",
    paintShader = SkLinearGradient.Make(
        SkPoint.Make(0f, 0f),
        SkPoint.Make(50f, 50f),
        intArrayOf(0x80F00080.toInt(), 0xF0F08000.toInt(), 0x800080F0.toInt()),
        floatArrayOf(0f, 0.5f, 1f),
        SkTileMode.kClamp,
    ),
)
