package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.math.SkColorSetRGB
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.math.SkScalar
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/sharedcorners.cpp::SharedCornersGM`
 * (`sharedcorners`, 720 × 740).
 *
 * Stress-tests analytic AA at shared corners of triangle meshes
 * (adjacent rects, obtuse angles, right angles, and random-radial
 * acute angles). Each mesh is rasterised four times :
 *  - wireframe (stroke);
 *  - solid fill at three sub-pixel jitter offsets.
 *
 * The mesh is then rotated 45 ° and `-(45 + 69.38111)` ° to spray
 * the corner intersections across different sub-pixel grid offsets,
 * exercising different rasterisation paths.
 *
 * Background is `color_to_565(0xFF1A65D7)`. Foreground is opaque white.
 */
public class SharedCornersGM : GM() {

    private val fFillPaint = SkPaint()
    private val fWireFramePaint = SkPaint()

    init {
        setBGColor(ToolUtils.colorTo565(SkColorSetRGB(0x1A, 0x65, 0xD7)))
    }

    override fun getName(): String = "sharedcorners"

    override fun getISize(): SkISize {
        val numRows = 3 * 2
        val numCols = (1 + kJitters.size) * 2
        return SkISize.Make(
            numCols * (kBoxSize + kPadSize) + kPadSize,
            numRows * (kBoxSize + kPadSize) + kPadSize,
        )
    }

    override fun onOnceBeforeDraw() {
        fFillPaint.color = SK_ColorWHITE
        fFillPaint.isAntiAlias = true

        fWireFramePaint.color = SK_ColorWHITE
        fWireFramePaint.isAntiAlias = true
        fWireFramePaint.style = SkPaint.Style.kStroke_Style
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(kPadSize.toFloat(), kPadSize.toFloat())
        c.save()

        // Adjacent rects.
        drawTriangleBoxes(
            c,
            listOf(
                SkPoint(0f,  0f), SkPoint(40f,  0f), SkPoint(80f,  0f), SkPoint(120f,  0f),
                SkPoint(0f, 20f), SkPoint(40f, 20f), SkPoint(80f, 20f), SkPoint(120f, 20f),
                                   SkPoint(40f, 40f), SkPoint(80f, 40f),
                                   SkPoint(40f, 60f), SkPoint(80f, 60f),
            ),
            listOf(
                intArrayOf(0, 1, 4), intArrayOf(1, 5, 4),
                intArrayOf(5, 1, 6), intArrayOf(1, 2, 6),
                intArrayOf(2, 3, 6), intArrayOf(3, 7, 6),
                intArrayOf(8, 5, 9), intArrayOf(5, 6, 9),
                intArrayOf(10, 8, 11), intArrayOf(8, 9, 11),
            ),
        )

        // Obtuse angles.
        drawTriangleBoxes(
            c,
            listOf(
                SkPoint( 0f, 0f), SkPoint(10f, 0f), SkPoint(20f, 0f),
                SkPoint( 0f, 2f),                    SkPoint(20f, 2f),
                                    SkPoint(10f, 4f),
                SkPoint( 0f, 6f),                    SkPoint(20f, 6f),
                SkPoint( 0f, 8f), SkPoint(10f, 8f), SkPoint(20f, 8f),
            ),
            listOf(
                intArrayOf(3, 1, 4), intArrayOf(4, 5, 3), intArrayOf(6, 5, 7), intArrayOf(7, 9, 6),
                intArrayOf(0, 1, 3), intArrayOf(1, 2, 4),
                intArrayOf(3, 5, 6), intArrayOf(5, 4, 7),
                intArrayOf(6, 9, 8), intArrayOf(9, 7, 10),
            ),
        )

        c.restore()
        c.translate(((kBoxSize + kPadSize) * 4).toFloat(), 0f)

        // Right angles.
        drawTriangleBoxes(
            c,
            listOf(
                SkPoint(0f, 0f), SkPoint(-1f, 0f), SkPoint(0f, -1f), SkPoint(1f, 0f), SkPoint(0f, 1f),
            ),
            listOf(
                intArrayOf(0, 1, 2), intArrayOf(0, 2, 3), intArrayOf(0, 3, 4), intArrayOf(0, 4, 1),
            ),
        )

        // Acute angles (random-radial spokes).
        val rand = SkRandom()
        val pts = mutableListOf<SkPoint>()
        val indices = mutableListOf<IntArray>()
        var theta = 0f
        pts.add(SkPoint(0f, 0f))
        while (theta < 2f * PI.toFloat()) {
            pts.add(SkPoint(cos(theta), sin(theta)))
            if (pts.size > 2) {
                indices.add(intArrayOf(0, pts.size - 2, pts.size - 1))
            }
            theta += rand.nextRangeF(0f, (PI / 3.0).toFloat())
        }
        indices.add(intArrayOf(0, pts.size - 1, 1))
        drawTriangleBoxes(c, pts, indices)
    }

    private fun drawTriangleBoxes(
        canvas: SkCanvas,
        points: List<SkPoint>,
        triangles: List<IntArray>,
    ) {
        val builder = SkPathBuilder(SkPathFillType.kEvenOdd)
        for (tri in triangles) {
            builder.moveTo(points[tri[0]].fX, points[tri[0]].fY)
            builder.lineTo(points[tri[1]].fX, points[tri[1]].fY)
            builder.lineTo(points[tri[2]].fX, points[tri[2]].fY)
            builder.close()
        }
        // Bounds are computed from the verb stream — equivalent to
        // SkPathBuilder::computeBounds() upstream (no kanvas-skia
        // builder-side mirror today, so we snapshot first).
        val bounds = builder.snapshot().computeBounds()
        val scale: SkScalar = kBoxSize / maxOf(bounds.height(), bounds.width())
        builder.transform(SkMatrix.MakeScale(scale, scale))
        var path: SkPath = builder.detach()

        drawRow(canvas, path)
        canvas.translate(0f, (kBoxSize + kPadSize).toFloat())

        val rot1 = SkMatrix.MakeRotate(45f, path.computeBounds().centerX(), path.computeBounds().centerY())
        path = path.makeTransform(rot1)
        drawRow(canvas, path)
        canvas.translate(0f, (kBoxSize + kPadSize).toFloat())

        val rot2 = SkMatrix.MakeRotate(-45f - 69.38111f, path.computeBounds().centerX(), path.computeBounds().centerY())
        path = path.makeTransform(rot2)
        drawRow(canvas, path)
        canvas.translate(0f, (kBoxSize + kPadSize).toFloat())
    }

    private fun drawRow(canvas: SkCanvas, path: SkPath) {
        val saveCount = canvas.getSaveCount()
        canvas.save()
        val bounds = path.computeBounds()
        canvas.translate(
            (kBoxSize - bounds.width()) / 2f - bounds.left,
            (kBoxSize - bounds.height()) / 2f - bounds.top,
        )

        canvas.drawPath(path, fWireFramePaint)
        canvas.translate((kBoxSize + kPadSize).toFloat(), 0f)

        for (jitter in kJitters) {
            val sub = canvas.getSaveCount()
            canvas.save()
            canvas.translate(jitter.fX, jitter.fY)
            canvas.drawPath(path, fFillPaint)
            canvas.restoreToCount(sub)
            canvas.translate((kBoxSize + kPadSize).toFloat(), 0f)
        }
        canvas.restoreToCount(saveCount)
    }

    private companion object {
        const val kPadSize: Int = 20
        const val kBoxSize: Int = 100
        val kJitters: Array<SkPoint> = arrayOf(
            SkPoint(0f, 0f),
            SkPoint(0.5f, 0.5f),
            SkPoint(2f / 3f, 1f / 3f),
        )
    }
}
