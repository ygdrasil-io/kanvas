package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/circulararcs.cpp::onebadarc` (DEF_SIMPLE_GM,
 * 100 × 100).
 *
 * Two semi-translucent red `kStroke_Style` arcs : a hand-built
 * quadratic + conic + line + close path (matching Skia's `arcTo`
 * decomposition), and an upstream `drawArc(useCenter=true)`. Used to
 * verify that the manual decomposition produces the same outline as
 * the canvas convenience.
 */
public class OneBadArcGM : GM() {

    override fun getName(): String = "onebadarc"
    override fun getISize(): SkISize = SkISize.Make(100, 100)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val path = SkPathBuilder()
            .moveTo(20f, 20f)
            .lineTo(34.1421f, 34.1421f)
            .conicTo(20f, 48.2843f, 5.85786f, 34.1421f, 0.707107f)
            .quadTo(5.85786f, 34.1421f, 5.85787f, 34.1421f)
            .lineTo(20f, 20f)
            .close()
            .detach()
        val p0 = SkPaint().apply {
            // Equivalent to setColor(SK_ColorRED) + setAlpha(100).
            color = SkColorSetARGB(
                100,
                SkColorGetR(SK_ColorRED),
                SkColorGetG(SK_ColorRED),
                SkColorGetB(SK_ColorRED),
            )
            strokeWidth = 15f
            style = SkPaint.Style.kStroke_Style
        }
        c.translate(20f, 0f)
        c.drawPath(path, p0)
        c.drawArc(SkRect.MakeLTRB(60f, 0f, 100f, 40f), 45f, 90f, useCenter = true, paint = p0)
    }
}
