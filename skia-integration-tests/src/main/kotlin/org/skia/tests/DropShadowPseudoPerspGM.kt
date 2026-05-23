package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorLTGRAY
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkISize
import org.graphiks.math.SkM44
import org.graphiks.math.SkRect
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect

/**
 * Port of upstream Skia's `gm/dropshadowimagefilter.cpp` :
 * `DEF_SIMPLE_GM(dropshadow_pseudopersp, canvas, 155, 155)`.
 *
 * Exercises the drop-shadow image filter when the canvas matrix is a
 * 4×4 matrix that technically contains perspective (Z-component is
 * non-zero) but the projected coordinates have Z values far from 0.
 * When inversing device bounds with assumed Z=0 the layer bounds would
 * end up empty; this GM verifies that the layer mapping calculations
 * don't incorrectly discard such bounds.
 *
 * **Adaptation** : upstream uses `SkImageFilters::DropShadow` with an
 * `SkColor4f` ({0.14902, 0.215686, 0.329412, 0.666667}, colorSpace=nullptr).
 * We call the `DropShadow(SkColor4f)` overload which converts the float
 * colour to ARGB8888 via [SkColor4f.toSkColor].
 */
public class DropShadowPseudoPerspGM : GM() {

    override fun getName(): String = "dropshadow_pseudopersp"
    override fun getISize(): SkISize = SkISize.Make(155, 155)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        c.clear(SK_ColorLTGRAY)

        // First 4×4 concat — outer viewport / scale transform.
        c.concat(
            SkM44(
                0.5f, 0f,   0f, -75f,
                0f,   0.5f, 0f, -30f,
                0f,   0f,   1f,  0f,
                0f,   0f,   0f,  1f,
            )
        )

        // Second 4×4 concat — the "pseudo-perspective" matrix that has
        // non-zero Z impact but does not produce visible distortion.
        // This is the problematic matrix from the upstream bug report;
        // it places layer bounds computation under extreme stress.
        c.concat(
            SkM44(
                1360f,    0f,       275.4f,   294100f,
                   0f, 1360f,       489.6f,    98344f,
                   0f,    0f,        -0.51f,   -2180.67f,
                   0f,    0f,         0.51f,    2181.67f,
            )
        )

        val layerBounds = SkRect.MakeLTRB(42.5f, 42.5f, 457.5f, 457.5f)

        // Drop-shadow paint using an SkColor4f colour (RGBA floats).
        val shadowColor = SkColor4f(
            fR = 0.14902f,
            fG = 0.215686f,
            fB = 0.329412f,
            fA = 0.666667f,
        )
        val layerPaint = SkPaint().apply {
            imageFilter = SkImageFilters.DropShadow(
                dx = 30f, dy = 30f,
                sigmaX = 12f, sigmaY = 12f,
                color = shadowColor,
                input = null,
            )
        }
        c.saveLayer(layerBounds, layerPaint)

        // Rounded rectangle drawn inside the layer.
        val rrect = SkRRect.MakeRectXY(
            SkRect.MakeLTRB(-250f, -250f, 250f, 250f),
            45f, 45f,
        )

        // Third concat — position the rrect at (250, 250) with 0.83 scale.
        c.concat(
            SkM44(
                0.83f, 0f,   0f, 250f,
                0f,   0.83f, 0f, 250f,
                0f,   0f,    1f,   0f,
                0f,   0f,    0f,   1f,
            )
        )

        val rrectPaint = SkPaint().apply {
            color = SK_ColorWHITE
            isAntiAlias = true
        }
        c.drawRRect(rrect, rrectPaint)
        c.restore()

        // Draw the rrect outline (stroke) after restoring the layer —
        // same concat as the fill draw above.
        c.concat(
            SkM44(
                0.83f, 0f,   0f, 250f,
                0f,   0.83f, 0f, 250f,
                0f,   0f,    1f,   0f,
                0f,   0f,    0f,   1f,
            )
        )
        rrectPaint.color = SK_ColorBLACK
        rrectPaint.style = SkPaint.Style.kStroke_Style
        c.drawRRect(rrect, rrectPaint)
    }
}
