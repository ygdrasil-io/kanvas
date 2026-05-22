package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Stub port of Skia's `gm/trimpatheffect.cpp::TrimGM`
 * (`trimpatheffect`, 1400 x 1000).
 *
 * Upstream chains [`SkTrimPathEffect`](https://github.com/google/skia/blob/main/src/effects/SkTrimPathEffect.cpp)
 * onto a stroked paint to render a moving "trim" window over three
 * Bezier / line / V-shaped paths. The path effect rebuilds the path
 * to clip it to a `[start, stop]` parametric window and is animated
 * via `onAnimate` (`fOffset` advances 1 every 2 seconds).
 *
 * `:kanvas-skia` doesn't currently ship `SkTrimPathEffect` (no Kotlin
 * implementation exists in `kanvas-skia/src/main/kotlin/org/skia/foundation`
 * yet, see [archives/MIGRATION_PLAN.md] -- path effects beyond corner /
 * dash / discrete are out of scope for the raster port). The GM is
 * kept as a no-op so the class compiles and downstream test harnesses
 * can still reference it via [getName] for dashboard rollup ; the
 * matching acceptance test is `@Ignore`d for now.
 */
public class TrimGM : GM() {

    override fun getName(): String = "trimpatheffect"

    override fun getISize(): SkISize = SkISize.Make(1400, 1000)

    override fun onDraw(canvas: SkCanvas?) {
        // No-op : SkTrimPathEffect is not implemented. See class KDoc.
    }
}
