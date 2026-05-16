package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.graphiks.math.SkISize

/**
 * Port of Skia's `gm/concavepaths.cpp` (`DEF_SIMPLE_GM(concavepaths, ...)`).
 *
 * 29 sub-tests of concave / self-intersecting / coincident-edge polygon
 * paths drawn with `paint.setAntiAlias(true)` and `kFill_Style`. The fill
 * rule is `kWinding` by default; a handful of sub-tests pass it explicitly.
 *
 * Three monotone-with-quadratic sub-tests exercise [SkPathBuilder.quadTo].
 * In Phase 3b the verb is preserved as `kQuad` and adaptively flattened
 * to a 0.25-pixel chord error inside `SkBitmapDevice.buildEdges`.
 */
public class ConcavePathsGM : GM() {
    override fun getName(): String = "concavepaths"
    override fun getISize(): SkISize = SkISize.Make(500, 600)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kFill_Style
        }
        testConcave(c, paint)
        testReverseConcave(c, paint)
        testBowtie(c, paint)
        testFakeBowtie(c, paint)
        testIntrudingVertex(c, paint)
        testFish(c, paint)
        testFastForward(c, paint)
        testHole(c, paint)
        testStar(c, paint)
        testTwist(c, paint)
        testInversionRepeatVertex(c, paint)
        testStairstep(c, paint)
        testStairstep2(c, paint)
        testOverlapping(c, paint)
        testPartners(c, paint)
        testWindingMergedToZero(c, paint)
        testMonotone1(c, paint)
        testMonotone2(c, paint)
        testMonotone3(c, paint)
        testMonotone4(c, paint)
        testMonotone5(c, paint)
        testDegenerate(c, paint)
        testCoincidentEdge(c, paint)
        testBowtieCoincidentTriangle(c, paint)
        testCollinearOuterBoundaryEdge(c, paint)
        testCoincidentEdges1(c, paint)
        testCoincidentEdges2(c, paint)
        testCoincidentEdges3(c, paint)
        testCoincidentEdges4(c, paint)
    }

    // -- Helpers -----------------------------------------------------------

    private fun pts(vararg coords: Float): Array<Pair<Float, Float>> {
        require(coords.size % 2 == 0)
        return Array(coords.size / 2) { i -> coords[i * 2] to coords[i * 2 + 1] }
    }

    private fun draw(c: SkCanvas, p: SkPaint, x: Float, y: Float, build: SkPathBuilder.() -> Unit) {
        c.save(); c.translate(x, y)
        c.drawPath(SkPathBuilder().apply(build).detach(), p)
        c.restore()
    }

    // -- Sub-tests (one-to-one with the .cpp) ----------------------------

    private fun testConcave(c: SkCanvas, p: SkPaint) {
        c.translate(0f, 0f)
        c.drawPath(SkPath.Polygon(pts(20f,20f, 80f,20f, 30f,30f, 20f,80f), false), p)
    }

    private fun testReverseConcave(c: SkCanvas, p: SkPaint) = draw(c, p, 100f, 0f) {
        moveTo(20f, 20f); lineTo(20f, 80f); lineTo(30f, 30f); lineTo(80f, 20f)
    }

    private fun testBowtie(c: SkCanvas, p: SkPaint) = draw(c, p, 200f, 0f) {
        moveTo(20f, 20f); lineTo(80f, 80f); lineTo(80f, 20f); lineTo(20f, 80f)
    }

    private fun testFakeBowtie(c: SkCanvas, p: SkPaint) = draw(c, p, 300f, 0f) {
        moveTo(20f, 20f); lineTo(50f, 40f); lineTo(80f, 20f)
        lineTo(80f, 80f); lineTo(50f, 60f); lineTo(20f, 80f)
    }

    private fun testIntrudingVertex(c: SkCanvas, p: SkPaint) {
        c.save(); c.translate(400f, 0f)
        c.drawPath(
            SkPath.Polygon(
                pts(20f,20f, 50f,50f, 68f,20f, 68f,80f, 50f,50f, 20f,80f),
                isClosed = false,
                fillType = SkPathFillType.kWinding,
                isVolatile = true,
            ), p,
        )
        c.restore()
    }

    private fun testInversionRepeatVertex(c: SkCanvas, p: SkPaint) {
        c.save(); c.translate(400f, 100f)
        c.drawPath(
            SkPath.Polygon(
                pts(80f,50f, 40f,80f, 60f,20f, 20f,20f, 39.99f,80f, 80f,50f),
                isClosed = false,
                fillType = SkPathFillType.kWinding,
                isVolatile = true,
            ), p,
        )
        c.restore()
    }

    private fun testFish(c: SkCanvas, p: SkPaint) {
        c.save(); c.translate(0f, 100f)
        c.drawPath(
            SkPath.Polygon(
                pts(20f,20f, 80f,80f, 70f,50f, 80f,20f, 20f,80f, 0f,50f),
                isClosed = false,
                fillType = SkPathFillType.kWinding,
                isVolatile = true,
            ), p,
        )
        c.restore()
    }

    private fun testFastForward(c: SkCanvas, p: SkPaint) = draw(c, p, 100f, 100f) {
        addPolygon(pts(20f,20f, 60f,50f, 20f,80f), false)
        addPolygon(pts(40f,20f, 40f,80f, 80f,50f), false)
    }

    private fun testHole(c: SkCanvas, p: SkPaint) = draw(c, p, 200f, 100f) {
        addPolygon(pts(20f,20f, 80f,20f, 80f,80f, 20f,80f), false)
        addPolygon(pts(30f,30f, 30f,70f, 70f,70f, 70f,30f), false)
    }

    private fun testStar(c: SkCanvas, p: SkPaint) {
        c.save(); c.translate(300f, 100f)
        c.drawPath(SkPath.Polygon(pts(30f,20f, 50f,80f, 70f,20f, 20f,57f, 80f,57f), false), p)
        c.restore()
    }

    private fun testTwist(c: SkCanvas, p: SkPaint) {
        c.save(); c.translate(420f, 220f); c.scale(10f, 10f)
        c.drawPath(
            SkPath.Polygon(
                pts(
                    0.5f, 6f,
                    5.8070392608642578125f, 6.4612660408020019531f,
                    -2.9186885356903076172f, 2.811046600341796875f,
                    0.49999994039535522461f, -1.4124038219451904297f,
                ),
                isClosed = false,
            ), p,
        )
        c.restore()
    }

    private fun testStairstep(c: SkCanvas, p: SkPaint) {
        c.save(); c.translate(0f, 200f)
        c.drawPath(
            SkPath.Polygon(
                pts(50f,50f, 50f,20f, 80f,20f, 50f,50f, 20f,50f, 20f,80f),
                isClosed = false,
            ), p,
        )
        c.restore()
    }

    private fun testStairstep2(c: SkCanvas, p: SkPaint) {
        c.save(); c.translate(100f, 200f)
        c.drawPath(
            SkPath.Polygon(pts(20f,60f, 35f,80f, 50f,60f, 65f,80f, 80f,60f), false), p,
        )
        c.restore()
    }

    private fun testOverlapping(c: SkCanvas, p: SkPaint) {
        c.save(); c.translate(200f, 200f)
        c.drawPath(SkPath.Polygon(pts(20f,80f, 80f,80f, 80f,20f, 80f,30f), false), p)
        c.restore()
    }

    private fun testPartners(c: SkCanvas, p: SkPaint) = draw(c, p, 300f, 200f) {
        addPolygon(pts(20f,80f, 80f,80f, 80f,20f, 20f,20f), false)
        addPolygon(pts(30f,30f, 45f,50f, 30f,70f), false)
        addPolygon(pts(70f,30f, 70f,70f, 55f,50f), false)
    }

    private fun testWindingMergedToZero(c: SkCanvas, p: SkPaint) = draw(c, p, 400f, 350f) {
        moveTo(20f, 80f)
        moveTo(70f, -0.000001f)
        lineTo(70f, 0.0f)
        lineTo(60f, -30.0f)
        lineTo(40f, 20.0f)
        moveTo(50f, 50.0f)
        lineTo(50f, -50.0f)
        lineTo(10f, 50.0f)
    }

    private fun testMonotone1(c: SkCanvas, p: SkPaint) = draw(c, p, 0f, 300f) {
        moveTo(20f, 20f)
        quadTo(20f, 50f, 80f, 50f)
        quadTo(20f, 50f, 20f, 80f)
    }

    private fun testMonotone2(c: SkCanvas, p: SkPaint) = draw(c, p, 100f, 300f) {
        moveTo(20f, 20f)
        lineTo(80f, 30f)
        quadTo(20f, 20f, 20f, 80f)
    }

    private fun testMonotone3(c: SkCanvas, p: SkPaint) = draw(c, p, 200f, 300f) {
        moveTo(20f, 80f)
        lineTo(80f, 70f)
        quadTo(20f, 80f, 20f, 20f)
    }

    private fun testMonotone4(c: SkCanvas, p: SkPaint) = draw(c, p, 300f, 300f) {
        moveTo(80f, 25f); lineTo(50f, 39f); lineTo(20f, 25f)
        lineTo(40f, 45f); lineTo(70f, 50f); lineTo(80f, 80f)
    }

    private fun testMonotone5(c: SkCanvas, p: SkPaint) = draw(c, p, 0f, 400f) {
        moveTo(50f, 20f); lineTo(80f, 80f); lineTo(50f, 50f); lineTo(20f, 80f)
    }

    private fun testDegenerate(c: SkCanvas, p: SkPaint) = draw(c, p, 100f, 400f) {
        moveTo(50f, 20f); lineTo(70f, 30f); lineTo(20f, 50f)
        moveTo(50f, 20f); lineTo(80f, 80f); lineTo(50f, 80f)
    }

    private fun testCoincidentEdge(c: SkCanvas, p: SkPaint) = draw(c, p, 200f, 400f) {
        moveTo(80f, 20f); lineTo(80f, 80f); lineTo(20f, 80f)
        moveTo(20f, 20f); lineTo(80f, 80f); lineTo(20f, 80f)
    }

    private fun testBowtieCoincidentTriangle(c: SkCanvas, p: SkPaint) = draw(c, p, 300f, 400f) {
        moveTo(20f, 20f); lineTo(80f, 80f); lineTo(80f, 20f); lineTo(20f, 80f)
        moveTo(50f, 50f); lineTo(80f, 20f); lineTo(80f, 80f)
    }

    private fun testCollinearOuterBoundaryEdge(c: SkCanvas, p: SkPaint) = draw(c, p, 400f, 400f) {
        moveTo(20f, 20f); lineTo(20f, 50f); lineTo(50f, 50f)
        moveTo(80f, 50f); lineTo(50f, 50f); lineTo(80f, 20f)
    }

    private fun testCoincidentEdges1(c: SkCanvas, p: SkPaint) = draw(c, p, 0f, 500f) {
        moveTo(20f, 20f); lineTo(80f, 80f); lineTo(20f, 80f)
        moveTo(20f, 20f); lineTo(50f, 50f); lineTo(20f, 50f)
    }

    private fun testCoincidentEdges2(c: SkCanvas, p: SkPaint) = draw(c, p, 100f, 500f) {
        moveTo(20f, 20f); lineTo(50f, 50f); lineTo(20f, 50f)
        moveTo(20f, 20f); lineTo(80f, 80f); lineTo(20f, 80f)
    }

    private fun testCoincidentEdges3(c: SkCanvas, p: SkPaint) = draw(c, p, 200f, 500f) {
        moveTo(20f, 80f); lineTo(20f, 50f); lineTo(50f, 50f)
        moveTo(20f, 80f); lineTo(20f, 20f); lineTo(80f, 20f)
    }

    private fun testCoincidentEdges4(c: SkCanvas, p: SkPaint) = draw(c, p, 300f, 500f) {
        moveTo(20f, 80f); lineTo(20f, 20f); lineTo(80f, 20f)
        moveTo(20f, 80f); lineTo(20f, 50f); lineTo(50f, 50f)
    }
}
