package org.skia.tests

import org.graphiks.math.SK_ColorLTGRAY
import org.graphiks.math.SkColor
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkIRect
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.core.QuadAAFlags
import org.skia.core.SkCanvas
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSurfaces
import org.skia.foundation.SkTileMode

/**
 * Shared port of upstream's `make_image_tiles` helper
 * ([gm/drawimageset.cpp](https://github.com/google/skia/blob/main/gm/drawimageset.cpp#L37-L105)) :
 *
 * ```cpp
 * static void make_image_tiles(int tileW, int tileH, int m, int n,
 *                              SkSpan<const SkColor4f> colors,
 *                              SkCanvas::ImageSetEntry set[],
 *                              const SkColor bgColor = SK_ColorLTGRAY);
 * ```
 *
 * Renders an `m × n` grid where every tile is a 1-pixel-overlapping subset
 * of a single backing image. The backing image is filled with `bgColor`
 * then has two crossed stripe-gradient patterns stroked across it (a
 * top-left → bottom-right gradient using `colors[0..1]`, and a
 * bottom-left → top-right one using `colors[2..3]` under
 * [SkBlendMode.kMultiply]). The resulting [SkCanvas.ImageSetEntry] array is
 * populated in row-major order with :
 *
 *  - `image`    : the tile-sized subset of the backing image (1-pixel
 *                 overlap on every interior edge, for filter continuity).
 *  - `srcRect`  : `(1, 1, tileW + 1, tileH + 1)` for interior tiles, or
 *                 `(0, 0, tileW, tileH)` when the tile sits on the grid
 *                 boundary on that axis (no overlap to skip).
 *  - `dstRect`  : `(x * tileW, y * tileH, w, h)` — tiles butt up edge-to-
 *                 edge in destination space, no overlap.
 *  - `aaFlags`  : AA only on grid-boundary edges (so interior seams stay
 *                 hard-edged when the renderer pixel-snaps them).
 *  - `alpha`    : `1f` (set per-entry by callers needing alpha variations).
 *
 * [colors] must contain exactly 4 entries (the upstream `SK_ASSERT`).
 */
internal fun makeImageTiles(
    tileW: Int,
    tileH: Int,
    m: Int,
    n: Int,
    colors: Array<SkColor4f>,
    bgColor: SkColor = SK_ColorLTGRAY,
): Array<SkCanvas.ImageSetEntry> {
    require(colors.size == 4) {
        "makeImageTiles: colors must contain exactly 4 entries, got ${colors.size}"
    }

    val w = tileW * m
    val h = tileH * n
    val info = SkImageInfo.Make(w, h, SkColorType.kRGBA_8888, SkAlphaType.kPremul)
    val surf = SkSurfaces.Raster(info)
        ?: error("makeImageTiles: SkSurfaces.Raster returned null for ${w}x$h")
    val surfCanvas = surf.canvas
    surfCanvas.clear(bgColor)

    val stripeW = 10f
    val stripeSpacing = 30f
    val wF = w.toFloat()
    val hF = h.toFloat()

    // First gradient — colours[0..1] across (0,0) → (w,h), stroked as
    // a diagonal stripe family from "left of canvas" sliding to the right.
    val grad1 = SkLinearGradient.Make(
        SkPoint(0f, 0f), SkPoint(wF, hF),
        intArrayOf(colors[0].toSkColor(), colors[1].toSkColor()),
        null,
        SkTileMode.kClamp,
    )
    val paint = SkPaint().apply {
        shader = grad1
        isAntiAlias = true
        style = SkPaint.Style.kStroke_Style
        strokeWidth = stripeW
    }
    var stripeStartX = -wF - stripeW
    var stripeEndX = stripeW
    while (stripeStartX <= wF) {
        // pair-of-points line (kLines mode)
        val pts = arrayOf(
            SkPoint(stripeStartX, -stripeW),
            SkPoint(stripeEndX, hF + stripeW),
        )
        surfCanvas.drawPoints(SkCanvas.PointMode.kLines, pts, paint)
        stripeStartX += stripeSpacing
        stripeEndX += stripeSpacing
    }

    // Second gradient — colours[2..3] across (0,h) → (w,0), stroked as a
    // diagonal stripe family in the opposite direction, multiplied over.
    val grad2 = SkLinearGradient.Make(
        SkPoint(0f, hF),
        SkPoint(wF, 0f),
        intArrayOf(colors[2].toSkColor(), colors[3].toSkColor()),
        null,
        SkTileMode.kClamp,
    )
    paint.shader = grad2
    paint.blendMode = SkBlendMode.kMultiply
    stripeStartX = -wF - stripeW
    stripeEndX = stripeW
    while (stripeStartX <= wF) {
        val pts = arrayOf(
            SkPoint(stripeStartX, hF + stripeW),
            SkPoint(stripeEndX, -stripeW),
        )
        surfCanvas.drawPoints(SkCanvas.PointMode.kLines, pts, paint)
        stripeStartX += stripeSpacing
        stripeEndX += stripeSpacing
    }

    val fullImage: SkImage = surf.makeImageSnapshot()

    val out = ArrayList<SkCanvas.ImageSetEntry>(m * n)
    for (y in 0 until n) {
        for (x in 0 until m) {
            // 1-pixel overlap on every interior edge so filter sampling
            // doesn't fall off into the surrounding canvas.
            var subL = x * tileW - 1
            var subT = y * tileH - 1
            var subR = (x + 1) * tileW + 1
            var subB = (y + 1) * tileH + 1
            var aaFlags = QuadAAFlags.kNone_QuadAAFlags
            if (x == 0) { subL = 0; aaFlags = aaFlags or QuadAAFlags.kLeft_QuadAAFlag }
            if (x == m - 1) { subR = w; aaFlags = aaFlags or QuadAAFlags.kRight_QuadAAFlag }
            if (y == 0) { subT = 0; aaFlags = aaFlags or QuadAAFlags.kTop_QuadAAFlag }
            if (y == n - 1) { subB = h; aaFlags = aaFlags or QuadAAFlags.kBottom_QuadAAFlag }

            val subset = SkIRect.MakeLTRB(subL, subT, subR, subB)
            val tileImage = fullImage.makeSubset(subset)
                ?: error(
                    "makeImageTiles: makeSubset returned null for tile " +
                        "($x, $y) subset=$subset",
                )

            val srcL = if (x == 0) 0f else 1f
            val srcT = if (y == 0) 0f else 1f
            val srcRect = SkRect.MakeXYWH(srcL, srcT, tileW.toFloat(), tileH.toFloat())
            val dstRect = SkRect.MakeXYWH(
                (x * tileW).toFloat(), (y * tileH).toFloat(),
                tileW.toFloat(), tileH.toFloat(),
            )

            out += SkCanvas.ImageSetEntry(
                image = tileImage,
                srcRect = srcRect,
                dstRect = dstRect,
                aaFlags = aaFlags,
                alpha = 1f,
            )
        }
    }
    return out.toTypedArray()
}

