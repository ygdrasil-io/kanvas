package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/fiddle.cpp::fiddle` (256 × 256).
 *
 * Empty placeholder GM — the upstream draw body is `void draw(SkCanvas*) {}`,
 * left blank so contributors can paste fiddle.skia.org code in. Renders
 * the bg colour (default white) and nothing else; useful as a sanity
 * check that the test harness can run a no-op draw end-to-end.
 */
public class FiddleGM : GM() {

    override fun getName(): String = "fiddle"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        // Intentionally empty — matches upstream's stub draw body.
    }
}
