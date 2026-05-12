package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import org.skia.math.SkRect
import kotlin.math.PI

/**
 * Port of Skia's `gm/dashcircle.cpp::DashCircleGM` (900 × 1200,
 * static frame ; we drop the animated `fRotation` and render
 * the at-rest pose, matching the GM-test convention).
 *
 * Two strokes per cell stacked at the same origin :
 *
 *  - A reference outline built by walking `arcTo` segments
 *    around an oval, with each on-interval becoming a separate
 *    sub-path. Stroked with a 1-px AA line in `0xFFbf3f7f`.
 *  - A `drawPath(circle)` with a [SkDashPathEffect] whose
 *    intervals correspond to the same `(on, off)` pattern,
 *    stroked at 10 px with black AA.
 *
 * Four dash patterns × three "wedge counts" `{6, 12, 36}` → 12
 * cells laid out in a 4-row × 3-column grid spaced by
 * `radius * 2 + 50 = 300` px.
 */
public class DashCircleGM : GM() {

    override fun getName(): String = "dashcircle"
    override fun getISize(): SkISize = SkISize.Make(900, 1200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val refPaint = SkPaint().apply {
            isAntiAlias = true
            color = 0xFFbf3f7f.toInt()
            setStroke(true)
            strokeWidth = 1f
        }

        val radius = 125f
        val oval = SkRect.MakeLTRB(-radius - 20f, -radius - 20f, radius + 20f, radius + 20f)
        val circle: SkPath = SkPath.Circle(0f, 0f, radius)
        val circumference = (radius * PI.toFloat() * 2f)

        val wedges = intArrayOf(6, 12, 36)
        c.translate(radius + 20f, radius + 20f)

        for (wedge in wedges) {
            val arcLength = 360f / wedge
            c.save()
            for (dashExample in dashExamples) {
                // Build the analytic reference path.
                val refBuilder = SkPathBuilder()
                var dashUnits = 0
                for (v in dashExample) dashUnits += v
                val unitLength = arcLength / dashUnits
                var angle = 0f
                for (i in 0 until wedge) {
                    var i2 = 0
                    while (i2 < dashExample.size) {
                        val span = dashExample[i2] * unitLength
                        refBuilder.moveTo(0f, 0f)
                        refBuilder.arcTo(oval, angle, span, false)
                        refBuilder.close()
                        angle += span + dashExample[i2 + 1] * unitLength
                        i2 += 2
                    }
                }
                c.save()
                c.drawPath(refBuilder.detach(), refPaint)
                c.restore()

                // The dashed stroke equivalent.
                val p = SkPaint().apply {
                    isAntiAlias = true
                    setStroke(true)
                    strokeWidth = 10f
                }
                val dashLength = circumference / wedge / dashUnits
                val intervals = FloatArray(dashExample.size)
                for (i2 in dashExample.indices) {
                    intervals[i2] = dashExample[i2] * dashLength
                }
                p.pathEffect = SkDashPathEffect.Make(intervals, 0f)
                c.save()
                c.drawPath(circle, p)
                c.restore()
                c.translate(0f, radius * 2 + 50f)
            }
            c.restore()
            c.translate(radius * 2 + 50f, 0f)
        }
    }

    public companion object {
        private val dashExamples: Array<IntArray> = arrayOf(
            intArrayOf(1, 1),
            intArrayOf(1, 3),
            intArrayOf(1, 1, 3, 3),
            intArrayOf(1, 3, 2, 4),
        )
    }
}
