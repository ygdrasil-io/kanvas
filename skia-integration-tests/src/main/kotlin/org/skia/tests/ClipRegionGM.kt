package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SkRegion
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/clipdrawdraw.cpp::DEF_SIMPLE_GM(clip_region, …, 256, 256)`.
 *
 * Two-quadrant clip-region smoke test :
 *
 *  - Top-left — `clipRegion({10, 10, 100, 100})` then `drawColor(RED)` ;
 *    the entire 90 × 90 sub-rect should be solid red.
 *  - Center — `saveLayer({30, 30, 80, 80}, null)` then `clipRegion`
 *    over the same outer rect then `drawColor(BLUE)`. Only the
 *    intersection of `(30..80, 30..80)` and the region (`(10..100,
 *    10..100)`) — i.e. `(30..80, 30..80)` — should be solid blue,
 *    everything else unchanged.
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(clip_region, canvas, 256, 256) {
 *     SkRegion rgn({ 10, 10, 100, 100 });
 *
 *     canvas->save();
 *     canvas->clipRegion(rgn);
 *     canvas->drawColor(SK_ColorRED);
 *     canvas->restore();
 *
 *     SkRect bounds = { 30, 30, 80, 80 };
 *     canvas->saveLayer(&bounds, nullptr);
 *     canvas->clipRegion(rgn);
 *     canvas->drawColor(SK_ColorBLUE);
 *     canvas->restore();
 * }
 * ```
 */
public class ClipRegionGM : GM() {
    override fun getName(): String = "clip_region"
    override fun getISize(): SkISize = SkISize.Make(256, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val rgn = SkRegion(SkIRect(10, 10, 100, 100))

        c.save()
        c.clipRegion(rgn)
        c.drawColor(SK_ColorRED)
        c.restore()

        val bounds = SkRect.MakeLTRB(30f, 30f, 80f, 80f)
        c.saveLayer(bounds, null)
        c.clipRegion(rgn)
        c.drawColor(SK_ColorBLUE)
        c.restore()
    }
}
