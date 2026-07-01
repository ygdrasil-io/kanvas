package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/concavepaths.cpp` (`DEF_SIMPLE_GM(concavepaths, ...)`).
 *
 * 29 sub-tests of concave / self-intersecting / coincident-edge polygon
 * paths drawn with anti-aliasing and kFill_Style.
 * @see https://github.com/google/skia/blob/main/gm/concavepaths.cpp
 */
class ConcavePathsGm : SkiaGm {
    override val name = "concavepaths"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 93.0
    override val width = 500
    override val height = 600

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(color = Color.fromRGBA(0f, 0f, 0f, 1f), antiAlias = true, style = PaintStyle.FILL)
        testConcave(canvas, paint)
        testReverseConcave(canvas, paint)
        testBowtie(canvas, paint)
        testFakeBowtie(canvas, paint)
        testIntrudingVertex(canvas, paint)
        testFish(canvas, paint)
        testFastForward(canvas, paint)
        testHole(canvas, paint)
        testStar(canvas, paint)
        testTwist(canvas, paint)
        testInversionRepeatVertex(canvas, paint)
        testStairstep(canvas, paint)
        testStairstep2(canvas, paint)
        testOverlapping(canvas, paint)
        testPartners(canvas, paint)
        testWindingMergedToZero(canvas, paint)
        testMonotone1(canvas, paint)
        testMonotone2(canvas, paint)
        testMonotone3(canvas, paint)
        testMonotone4(canvas, paint)
        testMonotone5(canvas, paint)
        testDegenerate(canvas, paint)
        testCoincidentEdge(canvas, paint)
        testBowtieCoincidentTriangle(canvas, paint)
        testCollinearOuterBoundaryEdge(canvas, paint)
        testCoincidentEdges1(canvas, paint)
        testCoincidentEdges2(canvas, paint)
        testCoincidentEdges3(canvas, paint)
        testCoincidentEdges4(canvas, paint)
    }

    private fun drawPath(canvas: GmCanvas, paint: Paint, x: Float, y: Float, block: Path.() -> Unit) {
        canvas.save()
        canvas.translate(x, y)
        canvas.drawPath(Path { }.apply(block), paint)
        canvas.restore()
    }

    private fun testConcave(canvas: GmCanvas, paint: Paint) {
        canvas.save()
        canvas.drawPath(
            Path {
                moveTo(20f, 20f); lineTo(80f, 20f); lineTo(30f, 30f); lineTo(20f, 80f)
            },
            paint,
        )
        canvas.restore()
    }

    private fun testReverseConcave(canvas: GmCanvas, paint: Paint) = drawPath(canvas, paint, 100f, 0f) {
        moveTo(20f, 20f); lineTo(20f, 80f); lineTo(30f, 30f); lineTo(80f, 20f)
    }

    private fun testBowtie(canvas: GmCanvas, paint: Paint) = drawPath(canvas, paint, 200f, 0f) {
        moveTo(20f, 20f); lineTo(80f, 80f); lineTo(80f, 20f); lineTo(20f, 80f)
    }

    private fun testFakeBowtie(canvas: GmCanvas, paint: Paint) = drawPath(canvas, paint, 300f, 0f) {
        moveTo(20f, 20f); lineTo(50f, 40f); lineTo(80f, 20f)
        lineTo(80f, 80f); lineTo(50f, 60f); lineTo(20f, 80f)
    }

    private fun testIntrudingVertex(canvas: GmCanvas, paint: Paint) {
        canvas.save()
        canvas.translate(400f, 0f)
        canvas.drawPath(
            Path {
                moveTo(20f, 20f); lineTo(50f, 50f); lineTo(68f, 20f); lineTo(68f, 80f)
                lineTo(50f, 50f); lineTo(20f, 80f)
            },
            paint,
        )
        canvas.restore()
    }

    private fun testInversionRepeatVertex(canvas: GmCanvas, paint: Paint) {
        canvas.save()
        canvas.translate(400f, 100f)
        canvas.drawPath(
            Path {
                moveTo(80f, 50f); lineTo(40f, 80f); lineTo(60f, 20f); lineTo(20f, 20f)
                lineTo(39.99f, 80f); lineTo(80f, 50f)
            },
            paint,
        )
        canvas.restore()
    }

    private fun testFish(canvas: GmCanvas, paint: Paint) {
        canvas.save()
        canvas.translate(0f, 100f)
        canvas.drawPath(
            Path {
                moveTo(20f, 20f); lineTo(80f, 80f); lineTo(70f, 50f); lineTo(80f, 20f)
                lineTo(20f, 80f); lineTo(0f, 50f)
            },
            paint,
        )
        canvas.restore()
    }

    private fun testFastForward(canvas: GmCanvas, paint: Paint) {
        canvas.save()
        canvas.translate(100f, 100f)
        canvas.drawPath(
            Path {
                moveTo(20f, 20f); lineTo(60f, 50f); lineTo(20f, 80f)
                moveTo(40f, 20f); lineTo(40f, 80f); lineTo(80f, 50f)
            },
            paint,
        )
        canvas.restore()
    }

    private fun testHole(canvas: GmCanvas, paint: Paint) {
        canvas.save()
        canvas.translate(200f, 100f)
        canvas.drawPath(
            Path {
                moveTo(20f, 20f); lineTo(80f, 20f); lineTo(80f, 80f); lineTo(20f, 80f)
                moveTo(30f, 30f); lineTo(30f, 70f); lineTo(70f, 70f); lineTo(70f, 30f)
            },
            paint,
        )
        canvas.restore()
    }

    private fun testStar(canvas: GmCanvas, paint: Paint) {
        canvas.save()
        canvas.translate(300f, 100f)
        canvas.drawPath(
            Path {
                moveTo(30f, 20f); lineTo(50f, 80f); lineTo(70f, 20f); lineTo(20f, 57f); lineTo(80f, 57f)
            },
            paint,
        )
        canvas.restore()
    }

    private fun testTwist(canvas: GmCanvas, paint: Paint) {
        canvas.save()
        canvas.translate(420f, 220f)
        canvas.scale(10f, 10f)
        canvas.drawPath(
            Path {
                moveTo(0.5f, 6f)
                lineTo(5.8070392608642578125f, 6.4612660408020019531f)
                lineTo(-2.9186885356903076172f, 2.811046600341796875f)
                lineTo(0.49999994039535522461f, -1.4124038219451904297f)
            },
            paint,
        )
        canvas.restore()
    }

    private fun testStairstep(canvas: GmCanvas, paint: Paint) {
        canvas.save()
        canvas.translate(0f, 200f)
        canvas.drawPath(
            Path {
                moveTo(50f, 50f); lineTo(50f, 20f); lineTo(80f, 20f); lineTo(50f, 50f)
                lineTo(20f, 50f); lineTo(20f, 80f)
            },
            paint,
        )
        canvas.restore()
    }

    private fun testStairstep2(canvas: GmCanvas, paint: Paint) {
        canvas.save()
        canvas.translate(100f, 200f)
        canvas.drawPath(
            Path {
                moveTo(20f, 60f); lineTo(35f, 80f); lineTo(50f, 60f); lineTo(65f, 80f); lineTo(80f, 60f)
            },
            paint,
        )
        canvas.restore()
    }

    private fun testOverlapping(canvas: GmCanvas, paint: Paint) {
        canvas.save()
        canvas.translate(200f, 200f)
        canvas.drawPath(
            Path {
                moveTo(20f, 80f); lineTo(80f, 80f); lineTo(80f, 20f); lineTo(80f, 30f)
            },
            paint,
        )
        canvas.restore()
    }

    private fun testPartners(canvas: GmCanvas, paint: Paint) {
        canvas.save()
        canvas.translate(300f, 200f)
        canvas.drawPath(
            Path {
                moveTo(20f, 80f); lineTo(80f, 80f); lineTo(80f, 20f); lineTo(20f, 20f)
                moveTo(30f, 30f); lineTo(45f, 50f); lineTo(30f, 70f)
                moveTo(70f, 30f); lineTo(70f, 70f); lineTo(55f, 50f)
            },
            paint,
        )
        canvas.restore()
    }

    private fun testWindingMergedToZero(canvas: GmCanvas, paint: Paint) = drawPath(canvas, paint, 400f, 350f) {
        moveTo(20f, 80f)
        moveTo(70f, -0.000001f)
        lineTo(70f, 0.0f)
        lineTo(60f, -30.0f)
        lineTo(40f, 20.0f)
        moveTo(50f, 50.0f)
        lineTo(50f, -50.0f)
        lineTo(10f, 50.0f)
    }

    private fun testMonotone1(canvas: GmCanvas, paint: Paint) = drawPath(canvas, paint, 0f, 300f) {
        moveTo(20f, 20f)
        quadTo(20f, 50f, 80f, 50f)
        quadTo(20f, 50f, 20f, 80f)
    }

    private fun testMonotone2(canvas: GmCanvas, paint: Paint) = drawPath(canvas, paint, 100f, 300f) {
        moveTo(20f, 20f)
        lineTo(80f, 30f)
        quadTo(20f, 20f, 20f, 80f)
    }

    private fun testMonotone3(canvas: GmCanvas, paint: Paint) = drawPath(canvas, paint, 200f, 300f) {
        moveTo(20f, 80f)
        lineTo(80f, 70f)
        quadTo(20f, 80f, 20f, 20f)
    }

    private fun testMonotone4(canvas: GmCanvas, paint: Paint) = drawPath(canvas, paint, 300f, 300f) {
        moveTo(80f, 25f); lineTo(50f, 39f); lineTo(20f, 25f)
        lineTo(40f, 45f); lineTo(70f, 50f); lineTo(80f, 80f)
    }

    private fun testMonotone5(canvas: GmCanvas, paint: Paint) = drawPath(canvas, paint, 0f, 400f) {
        moveTo(50f, 20f); lineTo(80f, 80f); lineTo(50f, 50f); lineTo(20f, 80f)
    }

    private fun testDegenerate(canvas: GmCanvas, paint: Paint) = drawPath(canvas, paint, 100f, 400f) {
        moveTo(50f, 20f); lineTo(70f, 30f); lineTo(20f, 50f)
        moveTo(50f, 20f); lineTo(80f, 80f); lineTo(50f, 80f)
    }

    private fun testCoincidentEdge(canvas: GmCanvas, paint: Paint) = drawPath(canvas, paint, 200f, 400f) {
        moveTo(80f, 20f); lineTo(80f, 80f); lineTo(20f, 80f)
        moveTo(20f, 20f); lineTo(80f, 80f); lineTo(20f, 80f)
    }

    private fun testBowtieCoincidentTriangle(canvas: GmCanvas, paint: Paint) = drawPath(canvas, paint, 300f, 400f) {
        moveTo(20f, 20f); lineTo(80f, 80f); lineTo(80f, 20f); lineTo(20f, 80f)
        moveTo(50f, 50f); lineTo(80f, 20f); lineTo(80f, 80f)
    }

    private fun testCollinearOuterBoundaryEdge(canvas: GmCanvas, paint: Paint) = drawPath(canvas, paint, 400f, 400f) {
        moveTo(20f, 20f); lineTo(20f, 50f); lineTo(50f, 50f)
        moveTo(80f, 50f); lineTo(50f, 50f); lineTo(80f, 20f)
    }

    private fun testCoincidentEdges1(canvas: GmCanvas, paint: Paint) = drawPath(canvas, paint, 0f, 500f) {
        moveTo(20f, 20f); lineTo(80f, 80f); lineTo(20f, 80f)
        moveTo(20f, 20f); lineTo(50f, 50f); lineTo(20f, 50f)
    }

    private fun testCoincidentEdges2(canvas: GmCanvas, paint: Paint) = drawPath(canvas, paint, 100f, 500f) {
        moveTo(20f, 20f); lineTo(50f, 50f); lineTo(20f, 50f)
        moveTo(20f, 20f); lineTo(80f, 80f); lineTo(20f, 80f)
    }

    private fun testCoincidentEdges3(canvas: GmCanvas, paint: Paint) = drawPath(canvas, paint, 200f, 500f) {
        moveTo(20f, 80f); lineTo(20f, 50f); lineTo(50f, 50f)
        moveTo(20f, 80f); lineTo(20f, 20f); lineTo(80f, 20f)
    }

    private fun testCoincidentEdges4(canvas: GmCanvas, paint: Paint) = drawPath(canvas, paint, 300f, 500f) {
        moveTo(20f, 80f); lineTo(20f, 20f); lineTo(80f, 20f)
        moveTo(20f, 80f); lineTo(20f, 50f); lineTo(50f, 50f)
    }
}
