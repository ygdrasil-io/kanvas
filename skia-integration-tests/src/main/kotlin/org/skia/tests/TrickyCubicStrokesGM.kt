package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.tools.SkRandom

/**
 * Port of Skia's `gm/trickycubicstrokes.cpp::trickycubicstrokes` (and
 * sister `trickycubicstrokes_roundcaps`, 1000 × 1000).
 *
 * 5 × 5 grid of 23 pathological cubic / quad / conic paths drawn
 * stroked at `strokeWidth = 30`. Each cell uses a `RectToRect`
 * transform from the path's stroke bbox to a 200-px cell (with
 * `kCenter_ScaleToFit`, or center-then-translate when the cell-fill
 * mode is `kCenter`). Stroke width is normalized by the matrix's
 * max-scale so the visible width stays 30 px on screen.
 *
 * Open / closed variants share the [drawTest] body and only differ by
 * `(cap, join)`.
 */
public open class TrickyCubicStrokesGM(
    private val gmName: String,
    private val cap: SkPaint.Cap,
    private val join: SkPaint.Join,
) : GM() {

    override fun getName(): String = gmName
    override fun getISize(): SkISize = SkISize.Make(K_TEST_WIDTH, K_TEST_HEIGHT)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorBLACK)
        val rand = SkRandom()
        val strokePaint = SkPaint().apply {
            isAntiAlias = true
            strokeWidth = K_STROKE_WIDTH
            style = SkPaint.Style.kStroke_Style
            strokeCap = cap
            strokeJoin = join
        }
        for ((i, tc) in kTrickyCubics.withIndex()) {
            val numPts = tc.numPts
            val originalPts = tc.points
            val scale = tc.scale
            val p = Array(numPts) {
                (originalPts[it].first * scale) to (originalPts[it].second * scale)
            }
            val w = originalPts[3].first  // weight stored in 4th X for conics

            val cellRect = SkRect.MakeXYWH(
                ((i % K_NUM_COLS) * K_CELL_SIZE).toFloat(),
                ((i / K_NUM_COLS) * K_CELL_SIZE).toFloat(),
                K_CELL_SIZE.toFloat(),
                K_CELL_SIZE.toFloat(),
            )

            val strokeBounds = if (numPts == 4) {
                calcTightCubicBounds(p)
            } else {
                // 3-pt path → treat as cubic via lerp for bounds.
                val asCubic = arrayOf(
                    p[0],
                    lerp(p[0], p[1], 2f / 3f),
                    lerp(p[1], p[2], 1f / 3f),
                    p[2],
                )
                calcTightCubicBounds(asCubic)
            }
            strokeBounds.outset(K_STROKE_WIDTH, K_STROKE_WIDTH)

            val matrix: SkMatrix = if (tc.fillMode == CellFillMode.kStretch) {
                SkMatrix.MakeRectToRect(strokeBounds, cellRect, SkMatrix.ScaleToFit.kCenter_ScaleToFit)
                    ?: SkMatrix.Identity
            } else {
                SkMatrix.MakeTrans(
                    cellRect.left + K_STROKE_WIDTH +
                        (cellRect.width() - strokeBounds.width()) / 2f,
                    cellRect.top + K_STROKE_WIDTH +
                        (cellRect.height() - strokeBounds.height()) / 2f,
                )
            }

            c.save()
            c.concat(matrix)
            strokePaint.strokeWidth = K_STROKE_WIDTH / matrix.getMaxScale().coerceAtLeast(1e-6f)
            strokePaint.color = rand.nextU() or 0xFF808080.toInt()
            val builder = SkPathBuilder()
            builder.moveTo(p[0].first, p[0].second)
            when {
                numPts == 4 -> builder.cubicTo(p[1].first, p[1].second, p[2].first, p[2].second, p[3].first, p[3].second)
                w == 1f -> builder.quadTo(p[1].first, p[1].second, p[2].first, p[2].second)
                else -> builder.conicTo(p[1].first, p[1].second, p[2].first, p[2].second, w)
            }
            c.drawPath(builder.detach(), strokePaint)
            c.restore()
        }
    }

    /** Recursive `SkChopCubicAt(.5)`-based AABB. Depth 5 is plenty
     *  for the tightness needed by the stroker outset. */
    private fun calcTightCubicBounds(p: Array<Pair<Float, Float>>, depth: Int = 5): SkRect {
        if (depth == 0) {
            val xs = p.map { it.first }
            val ys = p.map { it.second }
            return SkRect.MakeLTRB(xs.min(), ys.min(), xs.max(), ys.max())
        }
        val chopped = chopCubicAtHalf(p)
        val left = arrayOf(chopped[0], chopped[1], chopped[2], chopped[3])
        val right = arrayOf(chopped[3], chopped[4], chopped[5], chopped[6])
        val a = calcTightCubicBounds(left, depth - 1)
        val b = calcTightCubicBounds(right, depth - 1)
        a.join(b)
        return a
    }

    /** De Casteljau split at `t = 0.5`. Returns 7 points
     *  (`p0, m1, m2, midpoint, m4, m5, p3`). */
    private fun chopCubicAtHalf(p: Array<Pair<Float, Float>>): Array<Pair<Float, Float>> {
        val mid01 = midpoint(p[0], p[1])
        val mid12 = midpoint(p[1], p[2])
        val mid23 = midpoint(p[2], p[3])
        val mid012 = midpoint(mid01, mid12)
        val mid123 = midpoint(mid12, mid23)
        val mid = midpoint(mid012, mid123)
        return arrayOf(p[0], mid01, mid012, mid, mid123, mid23, p[3])
    }

    private fun midpoint(a: Pair<Float, Float>, b: Pair<Float, Float>): Pair<Float, Float> =
        ((a.first + b.first) * 0.5f) to ((a.second + b.second) * 0.5f)

    private fun lerp(a: Pair<Float, Float>, b: Pair<Float, Float>, t: Float): Pair<Float, Float> =
        ((b.first - a.first) * t + a.first) to ((b.second - a.second) * t + a.second)

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

        @JvmStatic
        protected val kTrickyCubics: Array<TrickyCubic> = arrayOf(
            TrickyCubic(arrayOf(122f to 737f, 348f to 553f, 403f to 761f, 400f to 760f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(244f to 520f, 244f to 518f, 1141f to 634f, 394f to 688f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(550f to 194f, 138f to 130f, 1035f to 246f, 288f to 300f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(226f to 733f, 556f to 779f, -43f to 471f, 348f to 683f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(268f to 204f, 492f to 304f, 352f to 23f, 433f to 412f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(172f to 480f, 396f to 580f, 256f to 299f, 338f to 677f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(731f to 340f, 318f to 252f, 1026f to -64f, 367f to 265f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(475f to 708f, 62f to 620f, 770f to 304f, 220f to 659f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(0f to 0f, 128f to 128f, 128f to 0f, 0f to 128f), 4, CellFillMode.kCenter),               // perfect cusp
            TrickyCubic(arrayOf(0f to 0.01f, 128f to 127.999f, 128f to 0.01f, 0f to 127.99f), 4, CellFillMode.kCenter),  // near-cusp
            TrickyCubic(arrayOf(0f to -0.01f, 128f to 128.001f, 128f to -0.01f, 0f to 128.001f), 4, CellFillMode.kCenter),
            TrickyCubic(arrayOf(0f to 0f, 0f to -10f, 0f to -10f, 0f to 10f), 4, CellFillMode.kCenter, 1.098283f),       // flat with 180
            TrickyCubic(arrayOf(10f to 0f, 0f to 0f, 20f to 0f, 10f to 0f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(39f to -39f, 40f to -40f, 40f to -40f, 0f to 0f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(39f to -39f, 40f to -40f, 37f to -39f, 0f to 0f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(40f to 40f, 0f to 0f, 200f to 200f, 0f to 0f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(0f to 0f, 0.01f to 0f, -0.01f to 0f, 0f to 0f), 4, CellFillMode.kCenter),                // circle
            TrickyCubic(arrayOf(400.75f to 100.05f, 400.75f to 100.05f, 100.05f to 300.95f, 100.05f to 300.95f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(0.5f to 0f, 0f to 0f, 20f to 0f, 10f to 0f), 4, CellFillMode.kStretch),
            TrickyCubic(arrayOf(10f to 0f, 0f to 0f, 10f to 0f, 10f to 0f), 4, CellFillMode.kStretch),
            // 3-pt entries: 4th point's X is reused as the conic weight `w`.
            // `w == 1` means quad ; otherwise conic.
            TrickyCubic(arrayOf(1f to 1f, 2f to 1f, 1f to 1f, 1f to 0f), 3, CellFillMode.kStretch),                     // flat quad with cusp
            TrickyCubic(arrayOf(1f to 1f, 100f to 1f, 25f to 1f, 0.3f to 0f), 3, CellFillMode.kStretch),               // flat conic with cusp
            TrickyCubic(arrayOf(1f to 1f, 100f to 1f, 25f to 1f, 1.5f to 0f), 3, CellFillMode.kStretch),
        )
    }
}

/** `trickycubicstrokes` — `kButt_Cap` + `kMiter_Join`. */
public class TrickyCubicStrokesButtMiterGM : TrickyCubicStrokesGM(
    gmName = "trickycubicstrokes",
    cap = SkPaint.Cap.kButt_Cap,
    join = SkPaint.Join.kMiter_Join,
)

/** `trickycubicstrokes_roundcaps` — `kRound_Cap` + `kRound_Join`. */
public class TrickyCubicStrokesRoundCapsGM : TrickyCubicStrokesGM(
    gmName = "trickycubicstrokes_roundcaps",
    cap = SkPaint.Cap.kRound_Cap,
    join = SkPaint.Join.kRound_Join,
)
