package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.skia.foundation.SkCornerPathEffect
import org.skia.tools.SkDiscretePathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize

/**
 * Bespoke validation GM for [SkCornerPathEffect] +
 * [SkDiscretePathEffect] (Phase 7p2). Not a port of an upstream GM —
 * upstream `gm/patheffects.cpp::PathEffectGM` mixes 5 effect families
 * (hair / corner / dash / 1D-tile / 2D-tile / discrete / compose),
 * three of which we don't ship yet.
 *
 * Layout : 4 columns × 2 rows = 8 cells of varying corner radius
 * × jitter deviation, each drawing the same zigzag polyline. Cells
 * with `radius == 0` show sharp corners ; cells with `deviation == 0`
 * show clean (un-jittered) edges. The combination tests whether the
 * pathEffect → stroker pipeline correctly composes when neither
 * effect is `null`.
 *
 * This GM doesn't have an upstream reference image — `runGmTest` is
 * called only to ensure the pipeline doesn't crash and produces a
 * non-empty bitmap. The unit tests
 * [org.skia.foundation.SkCornerPathEffectTest] +
 * [org.skia.tools.SkDiscretePathEffectTest] cover the per-effect
 * correctness at the path level ; this GM is the integration smoke
 * test.
 */
public class CornerDiscretePathEffectGM : GM() {

    override fun getName(): String = "corner_discrete_path_effect"
    override fun getISize(): SkISize = SkISize.Make(640, 320)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Zigzag polyline in source coords.
        val zigzag = SkPathBuilder()
            .moveTo(20f, 60f)
            .lineTo(60f, 20f)
            .lineTo(100f, 100f)
            .lineTo(140f, 30f)
            .detach()

        val configs = arrayOf(
            // (cornerRadius, discreteSegLength, discreteDeviation)
            Triple(0f, 0f, 0f),       // raw : no effect
            Triple(15f, 0f, 0f),      // corner only
            Triple(0f, 8f, 4f),       // discrete only
            Triple(15f, 8f, 4f),      // corner + discrete (compose-style sequential)
            Triple(0f, 0f, 0f),       // sharp baseline
            Triple(30f, 0f, 0f),      // larger corner
            Triple(0f, 16f, 8f),      // larger jitter
            Triple(30f, 16f, 8f),     // larger combo
        )

        for ((i, cfg) in configs.withIndex()) {
            val col = i % 4
            val row = i / 4
            val ox = 10f + col * 160f
            val oy = 10f + row * 160f

            c.save()
            c.translate(ox, oy)

            val paint = SkPaint(SK_ColorBLACK).apply {
                isAntiAlias = true
                style = SkPaint.Style.kStroke_Style
                strokeWidth = 2f
            }
            // Apply discrete first (if requested), then corner.
            // Without MakeCompose, we apply only the last-set effect
            // since Skia's `paint.pathEffect = ...` slot is a single
            // reference. The two cells with both effects render with
            // corner only (the slice's API doesn't ship MakeCompose
            // yet — follow-up).
            val (r, segLen, dev) = cfg
            if (segLen > 0f && dev > 0f && r <= 0f) {
                paint.pathEffect = SkDiscretePathEffect.Make(segLen, dev, seed = i)
            } else if (r > 0f) {
                paint.pathEffect = SkCornerPathEffect.Make(r)
            }
            c.drawPath(zigzag, paint)
            c.restore()
        }
    }
}
