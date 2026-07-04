package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Rect
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * Port of Skia's `gm/trickycubicstrokes.cpp`.
 * 5×5 grid of 23 pathological cubic/quad/conic paths drawn stroked at width 30.
 * @see https://github.com/google/skia/blob/main/gm/trickycubicstrokes.cpp
 */
open class TrickyCubicStrokesGm(
    private val gmName: String,
    private val cap: StrokeCap,
    private val join: StrokeJoin,
) : SkiaGm {
    override val name = gmName
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = K_TEST_WIDTH
    override val height = K_TEST_HEIGHT

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f, 1f)
        val rand = Random(0)

        for ((i, tc) in kTrickyCubics.withIndex()) {
            val numPts = tc.numPts
            val originalPts = tc.points
            val scale = tc.scale
            val p = Array(numPts) {
                Pair(originalPts[it].first * scale, originalPts[it].second * scale)
            }
            val w = originalPts[3].first

            val cellLeft = ((i % K_NUM_COLS) * K_CELL_SIZE).toFloat()
            val cellTop = ((i / K_NUM_COLS) * K_CELL_SIZE).toFloat()
            val cellRect = Rect(cellLeft, cellTop, cellLeft + K_CELL_SIZE, cellTop + K_CELL_SIZE)

            val strokeBounds = if (numPts == 4) {
                calcTightCubicBounds(p)
            } else {
                val asCubic = arrayOf(
                    p[0],
                    lerp(p[0], p[1], 2f / 3f),
                    lerp(p[1], p[2], 1f / 3f),
                    p[2],
                )
                calcTightCubicBounds(asCubic)
            }
            val sb = Rect(
                strokeBounds.left - K_STROKE_WIDTH,
                strokeBounds.top - K_STROKE_WIDTH,
                strokeBounds.right + K_STROKE_WIDTH,
                strokeBounds.bottom + K_STROKE_WIDTH,
            )

            val matrix = if (tc.fillMode == CellFillMode.kStretch) {
                makeRectToRect(sb, cellRect, ScaleToFit.kCenter)
            } else {
            val tx = cellRect.left + K_STROKE_WIDTH +
                (cellRect.width - (strokeBounds.right - strokeBounds.left) - 2f * K_STROKE_WIDTH) / 2f
            val ty = cellRect.top + K_STROKE_WIDTH +
                (cellRect.height - (strokeBounds.bottom - strokeBounds.top) - 2f * K_STROKE_WIDTH) / 2f
                Matrix33.translate(tx, ty)
            }

            val maxScale = max(
                abs(matrix.scaleX), abs(matrix.scaleY),
            ).coerceAtLeast(1e-6f)

            canvas.save()
            canvas.concat(matrix)

            val rgba = rand.nextInt() or -0x807F80
            val color = Color.fromRGBA(
                ((rgba shr 16) and 0xFF).toFloat() / 255f,
                ((rgba shr 8) and 0xFF).toFloat() / 255f,
                (rgba and 0xFF).toFloat() / 255f,
            )
            val strokePaint = Paint(
                antiAlias = true,
                strokeWidth = K_STROKE_WIDTH / maxScale,
                style = PaintStyle.STROKE,
                strokeCap = cap,
                strokeJoin = join,
                color = color,
            )

            val path = Path { moveTo(p[0].first, p[0].second) }
            when {
                numPts == 4 -> path.cubicTo(p[1].first, p[1].second, p[2].first, p[2].second, p[3].first, p[3].second)
                else -> path.quadTo(p[1].first, p[1].second, p[2].first, p[2].second)
            }
            canvas.drawPath(path, strokePaint)
            canvas.restore()
        }
    }

    private fun calcTightCubicBounds(p: Array<Pair<Float, Float>>, depth: Int = 5): Rect {
        if (depth <= 0) {
            val xs = p.map { it.first }
            val ys = p.map { it.second }
            return Rect(xs.min(), ys.min(), xs.max(), ys.max())
        }
        val chopped = chopCubicAtHalf(p)
        val left = arrayOf(chopped[0], chopped[1], chopped[2], chopped[3])
        val right = arrayOf(chopped[3], chopped[4], chopped[5], chopped[6])
        val a = calcTightCubicBounds(left, depth - 1)
        val b = calcTightCubicBounds(right, depth - 1)
        return Rect(
            min(a.left, b.left), min(a.top, b.top),
            max(a.right, b.right), max(a.bottom, b.bottom),
        )
    }

    private fun chopCubicAtHalf(p: Array<Pair<Float, Float>>): Array<Pair<Float, Float>> {
        val mid01 = midpoint(p[0], p[1])
        val mid12 = midpoint(p[1], p[2])
        val mid23 = midpoint(p[2], p[3])
        val mid012 = midpoint(mid01, mid12)
        val mid123 = midpoint(mid12, mid23)
        val mid = midpoint(mid012, mid123)
        return arrayOf(p[0], mid01, mid012, mid, mid123, mid23, p[3])
    }

    private fun midpoint(a: Pair<Float, Float>, b: Pair<Float, Float>) =
        Pair((a.first + b.first) * 0.5f, (a.second + b.second) * 0.5f)

    private fun lerp(a: Pair<Float, Float>, b: Pair<Float, Float>, t: Float) =
        Pair((b.first - a.first) * t + a.first, (b.second - a.second) * t + a.second)

    private fun makeRectToRect(src: Rect, dst: Rect, fit: ScaleToFit): Matrix33 {
        val sx = dst.width / src.width
        val sy = dst.height / src.height
        val s = when (fit) {
            ScaleToFit.kCenter -> min(sx, sy)
            ScaleToFit.kStart -> min(sx, sy)
            ScaleToFit.kEnd -> min(sx, sy)
            ScaleToFit.kFill -> sy /* doesn't matter, just use sy */
        }
        val tx = dst.left + (dst.width - src.width * s) / 2f - src.left * s
        val ty = dst.top + (dst.height - src.height * s) / 2f - src.top * s
        return Matrix33.makeAll(s, 0f, tx, 0f, s, ty)
    }

    private enum class ScaleToFit { kCenter, kStart, kEnd, kFill }
    protected enum class CellFillMode { kStretch, kCenter }

    protected data class TrickyCubic(
        val points: Array<Pair<Float, Float>>,
        val numPts: Int,
        val fillMode: CellFillMode,
        val scale: Float = 1f,
    )

    protected companion object {
        const val K_STROKE_WIDTH: Float = 30f
        const val K_CELL_SIZE: Int = 200
        const val K_NUM_COLS: Int = 5
        const val K_NUM_ROWS: Int = 5
        const val K_TEST_WIDTH: Int = K_NUM_COLS * K_CELL_SIZE
        const val K_TEST_HEIGHT: Int = K_NUM_ROWS * K_CELL_SIZE

        val kTrickyCubics: Array<TrickyCubic> = arrayOf(
            TrickyCubic(arrayOf(122f to 737f, 348f to 553f, 403f to 761f, 400f to 760f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(244f to 520f, 244f to 518f, 1141f to 634f, 394f to 688f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(550f to 194f, 138f to 130f, 1035f to 246f, 288f to 300f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(226f to 733f, 556f to 779f, -43f to 471f, 348f to 683f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(268f to 204f, 492f to 304f, 352f to 23f, 433f to 412f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(172f to 480f, 396f to 580f, 256f to 299f, 338f to 677f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(731f to 340f, 318f to 252f, 1026f to -64f, 367f to 265f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(475f to 708f, 62f to 620f, 770f to 304f, 220f to 659f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(0f to 0f, 128f to 128f, 128f to 0f, 0f to 128f), 4, CellFillMode.kCenter),
            TrickyCubic(arrayOf(0f to 0.01f, 128f to 127.999f, 128f to 0.01f, 0f to 127.99f), 4, CellFillMode.kCenter),
            TrickyCubic(arrayOf(0f to -0.01f, 128f to 128.001f, 128f to -0.01f, 0f to 128.001f), 4, CellFillMode.kCenter),
            TrickyCubic(arrayOf(0f to 0f, 0f to -10f, 0f to -10f, 0f to 10f), 4, CellFillMode.kCenter, 1.098283f),
            TrickyCubic(arrayOf(10f to 0f, 0f to 0f, 20f to 0f, 10f to 0f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(39f to -39f, 40f to -40f, 40f to -40f, 0f to 0f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(39f to -39f, 40f to -40f, 37f to -39f, 0f to 0f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(40f to 40f, 0f to 0f, 200f to 200f, 0f to 0f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(0f to 0f, 0.01f to 0f, -0.01f to 0f, 0f to 0f), 4, CellFillMode.kCenter),
            TrickyCubic(arrayOf(400.75f to 100.05f, 400.75f to 100.05f, 100.05f to 300.95f, 100.05f to 300.95f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(0.5f to 0f, 0f to 0f, 20f to 0f, 10f to 0f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(10f to 0f, 0f to 0f, 10f to 0f, 10f to 0f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(1f to 1f, 2f to 1f, 1f to 1f, 1f to 0f), 3, CellFillMode.kStretch),
            TrickyCubic(arrayOf(1f to 1f, 100f to 1f, 25f to 1f, 0.3f to 0f), 3, CellFillMode.kStretch),
            TrickyCubic(arrayOf(1f to 1f, 100f to 1f, 25f to 1f, 1.5f to 0f), 3, CellFillMode.kStretch),
        )
    }
}

class TrickyCubicStrokesButtMiterGm : TrickyCubicStrokesGm(
    gmName = "trickycubicstrokes",
    cap = StrokeCap.BUTT,
    join = StrokeJoin.MITER,
)

class TrickyCubicStrokesRoundCapsGm : TrickyCubicStrokesGm(
    gmName = "trickycubicstrokes_roundcaps",
    cap = StrokeCap.ROUND,
    join = StrokeJoin.ROUND,
)
