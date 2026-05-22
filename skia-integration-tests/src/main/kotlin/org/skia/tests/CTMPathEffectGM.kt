package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Placeholder port of upstream Skia
 * `gm/ctmpatheffect.cpp::CTMPathEffectGM`.
 *
 * Original verifies that a `SkPathEffect` honours the current
 * `SkCanvas` CTM (concat-transform-matrix) when synthesising the
 * effect's pattern — typically dash + Discrete + 1D path effects under
 * `rotate/scale` transforms.
 *
 * TODO: missing API — `SkPath1DPathEffect` / `SkDiscretePathEffect`
 * filter() with CTM awareness. Flag-planting stub.
 */
public class CTMPathEffectGM : GM() {
    override fun getName(): String = "ctmpatheffect"
    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        // TODO: missing API — CTM-aware SkPath1DPathEffect / SkDiscretePathEffect.
    }
}
