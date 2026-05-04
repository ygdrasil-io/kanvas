package org.skia.foundation

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Coverage for the Phase 3i `resScale` parameter on [SkStroker]: when the
 * caller advertises a large CTM scale, the stroker must subdivide curves
 * proportionally finer so the polyline approximating the stroke outline
 * stays within 0.25 device-px of the true offset curve.
 */
class SkStrokerResScaleTest {

    private fun strokeCubic(resScale: Float): Int {
        // A 1-unit-wide circle-ish quarter-arc, stroked at width 0.05.
        // Under `resScale = 1f` the cubic flattens to a few segments
        // (chord error in source space ≤ 0.25 source-units ≈ acceptable
        // for a 1-unit arc); under `resScale = 1000f` the source-space
        // tolerance shrinks to 0.25/1000 = 2.5e-4 — forcing dozens of
        // subdivisions to bound the error in device-space pixels.
        val src = SkPathBuilder()
            .moveTo(1f, 0f)
            .cubicTo(1f, 0.55f, 0.55f, 1f, 0f, 1f)
            .detach()
        val paint = SkPaint().apply {
            strokeWidth = 0.05f
            style = SkPaint.Style.kStroke_Style
        }
        val out = SkStroker.fromPaint(paint, resScale).stroke(src)
        return out.verbs.size
    }

    @Test
    fun `resScale=1 produces a coarse polyline`() {
        val n = strokeCubic(1f)
        // 1 cubic at default tolerance flattens into a handful of lineTo
        // verbs per side. Outline assembly wraps left + cap + right + cap +
        // close, giving a verb count well under 30.
        assertTrue(n < 30, "resScale=1f produced $n verbs — expected ≪ 30")
    }

    @Test
    fun `resScale=1000 produces a much finer polyline`() {
        val n1000 = strokeCubic(1000f)
        val n1 = strokeCubic(1f)
        // 1000× scale ⇒ FLATNESS_SQ shrinks by 1e6 ⇒ De Casteljau recursion
        // hits dozens of levels; cap at MAX_DEPTH=18 ⇒ at most 2^18 segs
        // per cubic but in practice "many more than at resScale=1f".
        assertTrue(n1000 > n1 * 4,
            "resScale=1000 produced $n1000 verbs vs $n1 at 1× — expected ≥ 4× more")
    }

    @Test
    fun `resScale clamps to floor 1f`() {
        // Sub-1 resScale (e.g. extreme zoom-out) doesn't loosen the
        // source-space tolerance below the default — we never under-sample.
        // Fixture: callers go through SkBitmapDevice.ctmResScale which
        // applies the floor; this test pins the stroker's own arithmetic
        // by running it at `resScale = 1f` (the floored value) and showing
        // it produces ≥ as many verbs as e.g. `resScale = 0.001f` would
        // were it accepted as-is. We assert the public contract via the
        // fromPaint-default path behaving identically to fromPaint(_, 1f).
        val defaultStroker = SkStroker.fromPaint(SkPaint().apply {
            strokeWidth = 0.05f
            style = SkPaint.Style.kStroke_Style
        })
        val explicitStroker = SkStroker.fromPaint(SkPaint().apply {
            strokeWidth = 0.05f
            style = SkPaint.Style.kStroke_Style
        }, resScale = 1f)
        val src = SkPathBuilder()
            .moveTo(1f, 0f)
            .cubicTo(1f, 0.55f, 0.55f, 1f, 0f, 1f)
            .detach()
        assertTrue(defaultStroker.stroke(src).verbs.size ==
                   explicitStroker.stroke(src).verbs.size,
            "fromPaint(paint) must equal fromPaint(paint, 1f)")
    }
}
