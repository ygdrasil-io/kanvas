package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkPathFillType
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/convex_all_line_paths.cpp::ConvexLineOnlyPathsGM`
 * (`convex-lineonly-paths` / `convex-lineonly-paths-stroke-and-fill`,
 * 512 × 512).
 *
 * 20 convex line-only polygons (11 hand-coded + 9 procedural n-gons),
 * each drawn 7 times at progressively-shrinking scales `{1, 0.75, 0.5,
 * 0.25, 0.1, 0.01, 0.001}` alternating CW/CCW direction. Plus 3 crbug
 * repros at the end.
 *
 * The stroke-and-fill variant rotates through 3 stroke joins
 * (`kRound_Join`, `kBevel_Join`, `kMiter_Join`) per scale to expose
 * any join-specific seam.
 */
public open class ConvexLineOnlyPathsGM(
    private val doStrokeAndFill: Boolean,
) : GM() {

    init { setBGColor(SK_ColorWHITE) }

    override fun getName(): String =
        if (doStrokeAndFill) "convex-lineonly-paths-stroke-and-fill" else "convex-lineonly-paths"
    override fun getISize(): SkISize = SkISize.Make(K_GM_WIDTH, K_GM_HEIGHT)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val offset = floatArrayOf(0f, K_MAX_PATH_HEIGHT / 2f)
        if (doStrokeAndFill) {
            offset[0] += K_STROKE_WIDTH / 2f
            offset[1] += K_STROKE_WIDTH / 2f
        }

        for (i in 0 until K_NUM_PATHS) {
            drawPath(c, i, offset)
        }

        // crbug 472723 repro.
        val p = SkPaint().apply {
            isAntiAlias = true
            if (doStrokeAndFill) {
                style = SkPaint.Style.kStrokeAndFill_Style
                strokeJoin = SkPaint.Join.kMiter_Join
                strokeWidth = K_STROKE_WIDTH.toFloat()
            }
        }
        val p1 = SkPath.Polygon(
            arrayOf(
                60.8522949f to 364.671021f,
                59.4380493f to 364.671021f,
                385.414276f to 690.647217f,
                386.121399f to 689.940125f,
            ),
            isClosed = false,
        )
        c.save()
        c.translate(356f, 50f)
        c.drawPath(p1, p)
        c.restore()

        // crbug 869172 repro — only draws something in the stroke-and-fill variant.
        val p2 = SkPath.Polygon(
            arrayOf(
                10f to 0f, 38f to 0f, 66f to 0f, 94f to 0f, 122f to 0f, 150f to 0f,
                150f to 0f, 122f to 0f, 94f to 0f, 66f to 0f, 38f to 0f, 10f to 0f,
            ),
            isClosed = true,
        )
        c.save()
        c.translate(0f, 500f)
        c.drawPath(p2, p)
        c.restore()

        // crbug 856137 repro. Path with `kEvenOdd` fill type drawn under a
        // 3 × 3 affine.
        val p3 = SkPath.Polygon(
            arrayOf(
                1184.96f to 982.557f,
                1183.71f to 982.865f,
                1180.99f to 982.734f,
                1178.5f to 981.541f,
                1176.35f to 979.367f,
                1178.94f to 938.854f,
                1181.35f to 936.038f,
                1183.96f to 934.117f,
                1186.67f to 933.195f,
                1189.36f to 933.342f,
                1191.58f to 934.38f,
            ),
            isClosed = true,
            fillType = SkPathFillType.kEvenOdd,
        )
        c.save()
        c.concat(SkMatrix.MakeAll(
            0.0893210843f, 0f, 79.1197586f,
            0f, 0.0893210843f, 300f,
            0f, 0f, 1f,
        ))
        c.drawPath(p3, p)
        c.restore()
    }

    private fun drawPath(canvas: SkCanvas, index: Int, offset: FloatArray) {
        val anchorPath = getPath(index, SkPathDirection.kCW)
        val anchorBounds = anchorPath.computeBounds()
        if (offset[0] + anchorBounds.width() > K_GM_WIDTH) {
            offset[0] = 0f
            offset[1] += K_MAX_PATH_HEIGHT
            if (doStrokeAndFill) {
                offset[0] += K_STROKE_WIDTH / 2f
                offset[1] += K_STROKE_WIDTH / 2f
            }
        }
        val centerX = offset[0] + 0.5f * anchorBounds.width()
        val centerY = offset[1]
        offset[0] += anchorBounds.width()
        if (doStrokeAndFill) offset[0] += K_STROKE_WIDTH

        val colors = intArrayOf(SK_ColorBLACK, SK_ColorWHITE)
        val dirs = arrayOf(SkPathDirection.kCW, SkPathDirection.kCCW)
        val scales = floatArrayOf(1f, 0.75f, 0.5f, 0.25f, 0.1f, 0.01f, 0.001f)
        val joins = arrayOf(
            SkPaint.Join.kRound_Join,
            SkPaint.Join.kBevel_Join,
            SkPaint.Join.kMiter_Join,
        )

        val paint = SkPaint().apply { isAntiAlias = true }
        for (i in scales.indices) {
            val path = getPath(index, dirs[i % 2])
            if (doStrokeAndFill) {
                paint.style = SkPaint.Style.kStrokeAndFill_Style
                paint.strokeJoin = joins[i % 3]
                paint.strokeWidth = K_STROKE_WIDTH.toFloat()
            }
            canvas.save()
            canvas.translate(centerX, centerY)
            canvas.scale(scales[i], scales[i])
            paint.color = colors[i % 2]
            canvas.drawPath(path, paint)
            canvas.restore()
        }
    }

    private fun getPath(index: Int, dir: SkPathDirection): SkPath {
        val pts: Array<Pair<Float, Float>> = if (index < gPoints.size) {
            gPoints[index]
        } else {
            val width: Float
            val height: Float = K_MAX_PATH_HEIGHT / 2f
            val numPts: Int
            when (index - gPoints.size) {
                0 -> { numPts = 3; width = K_MAX_PATH_HEIGHT / 2f }
                1 -> { numPts = 4; width = K_MAX_PATH_HEIGHT / 2f }
                2 -> { numPts = 5; width = K_MAX_PATH_HEIGHT / 2f }
                3 -> { numPts = 5; width = K_MAX_PATH_HEIGHT / 5f }      // squashed pentagon
                4 -> { numPts = 6; width = K_MAX_PATH_HEIGHT / 2f }
                5 -> { numPts = 8; width = K_MAX_PATH_HEIGHT / 2f }
                6 -> { numPts = 8; width = K_MAX_PATH_HEIGHT / 5f }      // squashed octogon
                7 -> { numPts = 20; width = K_MAX_PATH_HEIGHT / 2f }
                8 -> { numPts = 100; width = K_MAX_PATH_HEIGHT / 2f }
                else -> { numPts = 3; width = K_MAX_PATH_HEIGHT / 2f }
            }
            createNgon(numPts, width, height)
        }

        val builder = SkPathBuilder()
        if (dir == SkPathDirection.kCW) {
            builder.moveTo(pts[0].first, pts[0].second)
            for (i in 1 until pts.size) builder.lineTo(pts[i].first, pts[i].second)
        } else {
            builder.moveTo(pts[pts.size - 1].first, pts[pts.size - 1].second)
            for (i in pts.size - 2 downTo 0) builder.lineTo(pts[i].first, pts[i].second)
        }
        builder.close()
        return builder.detach()
    }

    private fun createNgon(n: Int, width: Float, height: Float): Array<Pair<Float, Float>> {
        val angleStep = 360f / n
        var angle = if (n % 2 == 1) angleStep / 2f else 0f
        return Array(n) {
            val rad = (angle * Math.PI / 180f).toFloat()
            val pt = (-sin(rad) * width) to (cos(rad) * height)
            angle += angleStep
            pt
        }
    }

    private companion object {
        const val K_STROKE_WIDTH: Int = 10
        const val K_NUM_PATHS: Int = 20
        const val K_MAX_PATH_HEIGHT: Int = 100
        const val K_GM_WIDTH: Int = 512
        const val K_GM_HEIGHT: Int = 512

        // Hand-coded convex polygons. Indices match upstream's gPoints0..10.
        val gPoints: Array<Array<Pair<Float, Float>>> = arrayOf(
            // 0 — narrow rect
            arrayOf(-1.5f to -50f, 1.5f to -50f, 1.5f to 50f, -1.5f to 50f),
            // 1 — narrow rect on an angle
            arrayOf(-50f to -49f, -49f to -50f, 50f to 49f, 49f to 50f),
            // 2 — trapezoid (narrow top, wide bottom)
            arrayOf(-10f to -50f, 10f to -50f, 50f to 50f, -50f to 50f),
            // 3 — wide skewed rect
            arrayOf(-50f to -50f, 0f to -50f, 50f to 50f, 0f to 50f),
            // 4 — thin rect with colinear-ish lines
            arrayOf(
                -6f to -50f, 4f to -50f,
                5f to -25f,
                6f to 0f,
                5f to 25f,
                4f to 50f, -4f to 50f,
            ),
            // 5 — degenerate (~50 µm)
            arrayOf(-0.025f to -0.025f, 0.025f to -0.025f, 0.025f to 0.025f, -0.025f to 0.025f),
            // 6 — triangle whose first point should fuse with last
            arrayOf(-20f to -13f, -20f to -13.05f, 20f to -13f, 20f to 27f),
            // 7 — thin rect with colinear lines
            arrayOf(
                -10f to -50f, 10f to -50f,
                10f to -25f, 10f to 0f, 10f to 25f,
                10f to 50f, -10f to 50f,
            ),
            // 8 — capped teardrop
            arrayOf(
                50f to 50f, 0f to 50f,
                -15.45f to 47.55f, -29.39f to 40.45f,
                -40.45f to 29.39f, -47.55f to 15.45f,
                -50f to 0f,
                -47.55f to -15.45f, -40.45f to -29.39f,
                -29.39f to -40.45f, -15.45f to -47.55f,
                0f to -50f, 50f to -50f,
            ),
            // 9 — teardrop
            arrayOf(
                4.39f to 40.45f, -9.55f to 47.55f, -25f to 50f,
                -40.45f to 47.55f, -54.39f to 40.45f, -65.45f to 29.39f,
                -72.55f to 15.45f, -75f to 0f, -72.55f to -15.45f,
                -65.45f to -29.39f, -54.39f to -40.45f, -40.45f to -47.55f,
                -25f to -50f, -9.55f to -47.55f, 4.39f to -40.45f,
                75f to 0f,
            ),
            // 10 — clipped triangle
            arrayOf(
                -10f to -50f, 10f to -50f,
                50f to 31f, 40f to 50f,
                -40f to 50f, -50f to 31f,
            ),
        )
    }
}

/** `convex-lineonly-paths` — fill style only. */
public class ConvexLineOnlyPathsFillGM : ConvexLineOnlyPathsGM(doStrokeAndFill = false)

/** `convex-lineonly-paths-stroke-and-fill` — strokeAndFill with rotating joins. */
public class ConvexLineOnlyPathsStrokeAndFillGM : ConvexLineOnlyPathsGM(doStrokeAndFill = true)
