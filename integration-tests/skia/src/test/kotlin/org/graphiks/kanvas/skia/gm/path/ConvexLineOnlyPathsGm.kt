package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import kotlin.math.cos
import kotlin.math.sin

private class ConvexLineOnlyPathsRenderer(private val doStrokeAndFill: Boolean) {
    private val kStrokeWidth = 10
    private val kNumPaths = 20
    private val kMaxPathHeight = 100
    private val kGmWidth = 512
    private val kCellSize = 120

    private fun buildPath(pts: Array<Pair<Float, Float>>, dir: Int): Path {
        val path = Path {
            if (dir == 0) {
                moveTo(pts[0].first, pts[0].second)
                for (i in 1 until pts.size) lineTo(pts[i].first, pts[i].second)
            } else {
                moveTo(pts[pts.size - 1].first, pts[pts.size - 1].second)
                for (i in pts.size - 2 downTo 0) lineTo(pts[i].first, pts[i].second)
            }
            close()
        }
        return path
    }

    private fun computeBounds(pts: Array<Pair<Float, Float>>): Rect {
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        for ((x, y) in pts) {
            minX = minOf(minX, x); minY = minOf(minY, y)
            maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
        }
        return Rect.fromLTRB(minX, minY, maxX, maxY)
    }

    private val gPoints: Array<Array<Pair<Float, Float>>> = arrayOf(
        arrayOf(-1.5f to -50f, 1.5f to -50f, 1.5f to 50f, -1.5f to 50f),
        arrayOf(-50f to -49f, -49f to -50f, 50f to 49f, 49f to 50f),
        arrayOf(-10f to -50f, 10f to -50f, 50f to 50f, -50f to 50f),
        arrayOf(-50f to -50f, 0f to -50f, 50f to 50f, 0f to 50f),
        arrayOf(-6f to -50f, 4f to -50f, 5f to -25f, 6f to 0f, 5f to 25f, 4f to 50f, -4f to 50f),
        arrayOf(-0.025f to -0.025f, 0.025f to -0.025f, 0.025f to 0.025f, -0.025f to 0.025f),
        arrayOf(-20f to -13f, -20f to -13.05f, 20f to -13f, 20f to 27f),
        arrayOf(-10f to -50f, 10f to -50f, 10f to -25f, 10f to 0f, 10f to 25f, 10f to 50f, -10f to 50f),
        arrayOf(50f to 50f, 0f to 50f, -15.45f to 47.55f, -29.39f to 40.45f, -40.45f to 29.39f, -47.55f to 15.45f, -50f to 0f, -47.55f to -15.45f, -40.45f to -29.39f, -29.39f to -40.45f, -15.45f to -47.55f, 0f to -50f, 50f to -50f),
        arrayOf(4.39f to 40.45f, -9.55f to 47.55f, -25f to 50f, -40.45f to 47.55f, -54.39f to 40.45f, -65.45f to 29.39f, -72.55f to 15.45f, -75f to 0f, -72.55f to -15.45f, -65.45f to -29.39f, -54.39f to -40.45f, -40.45f to -47.55f, -25f to -50f, -9.55f to -47.55f, 4.39f to -40.45f, 75f to 0f),
        arrayOf(-10f to -50f, 10f to -50f, 50f to 31f, 40f to 50f, -40f to 50f, -50f to 31f),
    )

    private val gPointBounds: List<Rect> = gPoints.map { computeBounds(it) }

    private fun createNgon(n: Int, width: Float, height: Float): Array<Pair<Float, Float>> {
        val angleStep = 360f / n
        var angle = if (n % 2 == 1) angleStep / 2f else 0f
        return Array(n) {
            val rad = (angle * kotlin.math.PI.toFloat() / 180f)
            val pt = (-sin(rad) * width) to (cos(rad) * height)
            angle += angleStep
            pt
        }
    }

    private fun getPoints(index: Int): Array<Pair<Float, Float>> {
        if (index < gPoints.size) return gPoints[index]
        val width: Float
        val height: Float = kMaxPathHeight / 2f
        val numPts: Int
        when (index - gPoints.size) {
            0 -> { numPts = 3; width = kMaxPathHeight / 2f }
            1 -> { numPts = 4; width = kMaxPathHeight / 2f }
            2 -> { numPts = 5; width = kMaxPathHeight / 2f }
            3 -> { numPts = 5; width = kMaxPathHeight / 5f }
            4 -> { numPts = 6; width = kMaxPathHeight / 2f }
            5 -> { numPts = 8; width = kMaxPathHeight / 2f }
            6 -> { numPts = 8; width = kMaxPathHeight / 5f }
            7 -> { numPts = 20; width = kMaxPathHeight / 2f }
            8 -> { numPts = 100; width = kMaxPathHeight / 2f }
            else -> { numPts = 3; width = kMaxPathHeight / 2f }
        }
        return createNgon(numPts, width, height)
    }

    private fun computeNgonBounds(n: Int, width: Float, height: Float): Rect {
        val angleStep = 360f / n
        var angle = if (n % 2 == 1) angleStep / 2f else 0f
        var minX = Float.MAX_VALUE; var minY = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE; var maxY = -Float.MAX_VALUE
        repeat(n) {
            val rad = (angle * kotlin.math.PI.toFloat() / 180f)
            val x = -sin(rad) * width
            val y = cos(rad) * height
            minX = minOf(minX, x); minY = minOf(minY, y)
            maxX = maxOf(maxX, x); maxY = maxOf(maxY, y)
            angle += angleStep
        }
        return Rect.fromLTRB(minX, minY, maxX, maxY)
    }

    private val nGonBounds: List<Rect> = (0 until (kNumPaths - gPoints.size)).map { i ->
        val height: Float = kMaxPathHeight / 2f
        val width: Float
        val n: Int
        when (i) {
            0 -> { n = 3; width = kMaxPathHeight / 2f }
            1 -> { n = 4; width = kMaxPathHeight / 2f }
            2 -> { n = 5; width = kMaxPathHeight / 2f }
            3 -> { n = 5; width = kMaxPathHeight / 5f }
            4 -> { n = 6; width = kMaxPathHeight / 2f }
            5 -> { n = 8; width = kMaxPathHeight / 2f }
            6 -> { n = 8; width = kMaxPathHeight / 5f }
            7 -> { n = 20; width = kMaxPathHeight / 2f }
            else -> { n = 3; width = kMaxPathHeight / 2f }
        }
        computeNgonBounds(n, width, height)
    }

    private fun getPathBounds(index: Int): Rect {
        if (index < gPointBounds.size) return gPointBounds[index]
        return nGonBounds[index - gPointBounds.size]
    }

    fun render(canvas: GmCanvas) {
        canvas.drawRect(
            Rect.fromLTRB(0f, 0f, kGmWidth.toFloat(), kGmWidth.toFloat()),
            Paint(color = Color.WHITE),
        )

        val offset = floatArrayOf(0f, kMaxPathHeight / 2f)
        if (doStrokeAndFill) {
            offset[0] += kStrokeWidth / 2f
            offset[1] += kStrokeWidth / 2f
        }

        for (i in 0 until kNumPaths) {
            drawPath(canvas, i, offset)
        }

        val p = if (doStrokeAndFill) {
            Paint(antiAlias = true, style = PaintStyle.STROKE_AND_FILL, strokeJoin = StrokeJoin.MITER, strokeWidth = kStrokeWidth.toFloat())
        } else {
            Paint(antiAlias = true)
        }

        val p1 = Path {
            moveTo(60.8522949f, 364.671021f)
            lineTo(59.4380493f, 364.671021f)
            lineTo(385.414276f, 690.647217f)
            lineTo(386.121399f, 689.940125f)
        }
        canvas.save()
        canvas.translate(356f, 50f)
        canvas.drawPath(p1, p)
        canvas.restore()

        val p2 = Path {
            moveTo(10f, 0f); lineTo(38f, 0f); lineTo(66f, 0f); lineTo(94f, 0f)
            lineTo(122f, 0f); lineTo(150f, 0f); lineTo(150f, 0f); lineTo(122f, 0f)
            lineTo(94f, 0f); lineTo(66f, 0f); lineTo(38f, 0f); lineTo(10f, 0f)
            close()
        }
        canvas.save()
        canvas.translate(0f, 500f)
        canvas.drawPath(p2, p)
        canvas.restore()

        val p3 = Path {
            moveTo(1184.96f, 982.557f); lineTo(1183.71f, 982.865f)
            lineTo(1180.99f, 982.734f); lineTo(1178.5f, 981.541f)
            lineTo(1176.35f, 979.367f); lineTo(1178.94f, 938.854f)
            lineTo(1181.35f, 936.038f); lineTo(1183.96f, 934.117f)
            lineTo(1186.67f, 933.195f); lineTo(1189.36f, 933.342f)
            lineTo(1191.58f, 934.38f)
            close()
        }.also { it.fillType = FillType.EVEN_ODD }
        canvas.save()
        canvas.concat(Matrix33.translate(79.1197586f, 300f) * Matrix33.scale(0.0893210843f, 0.0893210843f))
        canvas.drawPath(p3, p)
        canvas.restore()
    }

    private fun drawPath(canvas: GmCanvas, index: Int, offset: FloatArray) {
        val anchorBounds = getPathBounds(index)
        if (offset[0] + anchorBounds.width > kGmWidth) {
            offset[0] = 0f
            offset[1] += kMaxPathHeight
            if (doStrokeAndFill) {
                offset[0] += kStrokeWidth / 2f
                offset[1] += kStrokeWidth / 2f
            }
        }
        val centerX = offset[0] + 0.5f * anchorBounds.width
        val centerY = offset[1]
        offset[0] += anchorBounds.width
        if (doStrokeAndFill) offset[0] += kStrokeWidth

        val colors = arrayOf(Color.BLACK, Color.WHITE)
        val dirs = intArrayOf(0, 1)
        val scales = floatArrayOf(1f, 0.75f, 0.5f, 0.25f, 0.1f, 0.01f, 0.001f)
        val joins = arrayOf(StrokeJoin.ROUND, StrokeJoin.BEVEL, StrokeJoin.MITER)

        val paint = Paint(antiAlias = true)
        var p = paint
        for (i in scales.indices) {
            val path = buildPath(getPoints(index), dirs[i % 2])
            if (doStrokeAndFill) {
                p = paint.copy(
                    style = PaintStyle.STROKE_AND_FILL,
                    strokeJoin = joins[i % 3],
                    strokeWidth = kStrokeWidth.toFloat(),
                )
            }
            canvas.save()
            canvas.translate(centerX, centerY)
            canvas.scale(scales[i], scales[i])
            p = p.copy(color = colors[i % 2])
            canvas.drawPath(path, p)
            canvas.restore()
        }
    }
}

class ConvexLineOnlyPathsGm : SkiaGm {
    override val name = "convex_lineonly_paths"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    private val renderer = ConvexLineOnlyPathsRenderer(false)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        renderer.render(canvas)
    }
}

class ConvexLineOnlyPathsStrokeAndFillGm : SkiaGm {
    override val name = "convex_lineonly_paths_stroke_and_fill"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    private val renderer = ConvexLineOnlyPathsRenderer(true)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        renderer.render(canvas)
    }
}
