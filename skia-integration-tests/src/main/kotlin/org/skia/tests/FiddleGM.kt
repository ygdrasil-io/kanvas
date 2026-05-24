package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/fiddle.cpp::fiddle` (256 × 256).
 *
 * Upstream is a **deliberately empty stub** used by `fiddle.skia.org` —
 * contributors paste their fiddle source into the body when reproducing
 * a bug locally. The upstream draw is literally :
 *
 * ```cpp
 * static void draw(SkCanvas* canvas);
 * DEF_SIMPLE_GM(fiddle, canvas, 256, 256) { draw(canvas); }
 *
 * // Paste your fiddle.skia.org code over this stub.
 * void draw(SkCanvas*) {}
 * ```
 *
 * The faithful Kotlin port is therefore an empty `onDraw` ; the GM
 * renders only the background color (default `SK_ColorWHITE`) and acts
 * as a sanity check that the test harness can run a no-op draw end-to-end.
 *
 * The matching reference `original-888/fiddle.png` is a pure-white
 * 256 × 256 PNG ; we currently ratchet at 100 % similarity.
 */
public class FiddleGM : GM() {

    override fun getName(): String = "fiddle"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        // Intentionally empty — matches upstream's stub draw body verbatim.
        // Contributors paste fiddle.skia.org code here to reproduce bugs
        // locally ; in CI we keep the body empty so the GM functions as a
        // background-fill sanity check.
    }
}
