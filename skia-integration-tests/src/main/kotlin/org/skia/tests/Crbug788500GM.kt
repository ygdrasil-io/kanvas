package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/crbug_788500.cpp` (`DEF_SIMPLE_GM(crbug_788500, ...)`).
 *
 * Reduced-from-Chromium AA fill that mixes a leading `moveTo(0, 0)` with
 * a second `moveTo` immediately followed by a cubic. The path uses the
 * `kEvenOdd` fill rule, exercises the path subsystem's `kCubic` flatten
 * (Phase 3b adaptive De Casteljau).
 *
 * C++ original:
 * ```cpp
 * DEF_SIMPLE_GM(crbug_788500, canvas, 300, 300) {
 *     SkPathBuilder path;
 *     path.setFillType(SkPathFillType::kEvenOdd);
 *     path.moveTo(0, 0);
 *     path.moveTo(245.5f, 98.5f);
 *     path.cubicTo(245.5f, 98.5f, 242, 78, 260, 75);
 *
 *     SkPaint paint;
 *     paint.setAntiAlias(true);
 *     canvas->drawPath(path.detach(), paint);
 * }
 * ```
 */
public class Crbug788500GM : GM() {
    override fun getName(): String = "crbug_788500"
    override fun getISize(): SkISize = SkISize.Make(300, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val path = SkPathBuilder()
            .setFillType(SkPathFillType.kEvenOdd)
            .moveTo(0f, 0f)
            .moveTo(245.5f, 98.5f)
            .cubicTo(245.5f, 98.5f, 242f, 78f, 260f, 75f)
            .detach()
        val paint = SkPaint().apply { isAntiAlias = true }
        c.drawPath(path, paint)
    }
}
