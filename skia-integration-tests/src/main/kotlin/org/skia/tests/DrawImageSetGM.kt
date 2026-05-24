package org.skia.tests

import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.core.QuadAAFlags
import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.core.withSave
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import kotlin.math.sqrt

/**
 * Port of Skia's
 * [`gm/drawimageset.cpp::DrawImageSetGM`](https://github.com/google/skia/blob/main/gm/drawimageset.cpp#L109-L208).
 *
 * Exercises [SkCanvas.experimental_DrawEdgeAAImageSet] — the batched
 * `ImageSetEntry[]` API. Lays out a `4 × 3` tiled image grid (each tile is
 * a 1-pixel-overlap subset of a single backing image built by
 * [makeImageTiles]) and replays it under four CTMs :
 *  1. **Rotation** — 30°, translated by `d/3` where `d = ‖(M·tileW, N·tileH)‖`.
 *  2. **Perspective** — `setPolyToPoly` mapping the source quad into a
 *     non-axis-aligned dst quad.
 *  3. **Skew + rotation + scale** — `setRotate(-60)` → `postSkew(0.5, -1.15)`
 *     → `postScale(0.6, 1.05)`.
 *  4. **Perspective + mirror in x** — `setPolyToPoly` with a flipped /
 *     halved dst quad.
 *
 * For each of [SkFilterMode.kNearest] and [SkFilterMode.kLinear], the GM
 * paints red interior tile-boundary guide-lines through the active CTM,
 * draws the batched image set, then draws a single-entry batch in two
 * additional positions :
 *  - The same `srcRect`-inset entry under [SkBlendMode.kExclusion].
 *  - A second copy beside it composing in
 *    [SkColorFilters.LinearToSRGBGamma].
 *
 * The C++ original lives at the link above.
 *
 * Body fully ported against the raster
 * [SkCanvas.experimental_DrawEdgeAAImageSet] fallback.
 */
public class DrawImageSetGM : GM() {

    init { setBGColor(0xFFCCCCCC.toInt()) }

    private var fSet: Array<SkCanvas.ImageSetEntry>? = null

    override fun getName(): String = "draw_image_set"
    override fun getISize(): SkISize = SkISize.Make(1000, 725)

    override fun onOnceBeforeDraw() {
        val kColors = arrayOf(
            SkColor4f.kCyan, SkColor4f.kBlack,
            SkColor4f.kMagenta, SkColor4f.kBlack,
        )
        fSet = makeImageTiles(kTileW, kTileH, kM, kN, kColors)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val set = fSet ?: return

        // Hypot of (M*tileW, N*tileH) — the diagonal of the image grid,
        // used as the inter-CTM stride. Matches upstream's `d`.
        val mw = (kM * kTileW).toFloat()
        val nh = (kN * kTileH).toFloat()
        val d = sqrt(mw * mw + nh * nh)

        val matrices = arrayOfNulls<SkMatrix>(4)

        // 0 — rotation.
        matrices[0] = SkMatrix.MakeRotate(30f).postTranslate(d / 3f, 0f)

        // 1 — perspective via PolyToPoly (rect → tilted quad).
        val srcQuad = rectToQuad(SkRect.MakeWH(mw, nh))
        val dstQuad1 = arrayOf(
            SkPoint(0f, 0f),
            SkPoint(mw + 10f, -5f),
            SkPoint(mw - 28f, nh + 40f),
            SkPoint(45f, nh - 25f),
        )
        matrices[1] = (SkMatrix.MakePolyToPoly(srcQuad, dstQuad1)
            ?: error("DrawImageSetGM: PolyToPoly returned null for perspective matrix"))
            .postTranslate(d, 50f)

        // 2 — skew (rotate -60° → postSkew(0.5, -1.15) → postScale(0.6, 1.05)
        // → postTranslate(d, 2.6 d)).
        matrices[2] = SkMatrix.MakeRotate(-60f)
            .postConcat(SkMatrix.MakeSkew(0.5f, -1.15f))
            .postScale(0.6f, 1.05f)
            .postTranslate(d, 2.6f * d)

        // 3 — perspective + mirror in x (dstQuad with x flipped).
        val dstQuad2 = arrayOf(
            // upstream indices : dst[1] = top-left, dst[0] = top-right,
            // dst[3] = bottom-mid (right side, half-down),
            // dst[2] = bottom-mid (left side, half-down minus 0.1*tileH).
            // We reproduce the exact element-by-element assignment :
            SkPoint(5f / 4f * mw, 0f),                              // dst[0]
            SkPoint(-0.25f * mw, 0f),                                // dst[1]
            SkPoint(1f / 3f * mw, 0.5f * nh - 0.1f * kTileH),        // dst[2]
            SkPoint(2f / 3f * mw, 0.5f * nh),                        // dst[3]
        )
        matrices[3] = (SkMatrix.MakePolyToPoly(srcQuad, dstQuad2)
            ?: error("DrawImageSetGM: PolyToPoly returned null for mirror matrix"))
            .postTranslate(100f, d)

        for (fm in arrayOf(SkFilterMode.kNearest, SkFilterMode.kLinear)) {
            val setPaint = SkPaint().apply { blendMode = SkBlendMode.kSrcOver }
            val sampling = SkSamplingOptions(fm)

            for (m in matrices.indices) {
                val mat = matrices[m] ?: continue
                // Red lines at interior tile boundaries, drawn through the
                // active CTM but in *device* space (the lines come from the
                // pre-mapped src points → out-of-canvas extension by
                // `kLineOutset` along the normalised direction).
                val kLineOutset = 10f
                val gridPaint = SkPaint().apply {
                    isAntiAlias = true
                    color = SK_ColorRED
                    style = SkPaint.Style.kStroke_Style
                    strokeWidth = 0f
                }
                // Vertical interior grid lines (mapped through CTM in
                // *device space* — upstream draws them with
                // `pts[1] - v, pts[0] + v` where `v` is the direction
                // `pts[0] → pts[1]` rescaled to `len + kLineOutset`,
                // i.e. each end is extended outward by `kLineOutset`).
                for (x in 1 until kM) {
                    val pts = arrayOf(
                        SkPoint(x * kTileW.toFloat(), 0f),
                        SkPoint(x * kTileW.toFloat(), nh),
                    )
                    mat.mapPoints(pts, pts, 2)
                    drawExtendedGridLine(c, pts[0], pts[1], kLineOutset, gridPaint)
                }
                // Horizontal interior grid lines.
                for (y in 1 until kN) {
                    val pts = arrayOf(
                        SkPoint(0f, y * kTileH.toFloat()),
                        SkPoint(mw, y * kTileH.toFloat()),
                    )
                    mat.mapPoints(pts, pts, 2)
                    drawExtendedGridLine(c, pts[0], pts[1], kLineOutset, gridPaint)
                }

                c.withSave {
                    concat(mat)
                    experimental_DrawEdgeAAImageSet(
                        set, kM * kN, null, null, sampling, setPaint,
                        SrcRectConstraint.kFast,
                    )
                }
            }

            // Single-entry batch with exotic blend mode + mixed AA flags
            // + alpha + sub-rect inset of the first image. Then a second
            // copy with LinearToSRGBGamma colour-filter on top.
            val entry = SkCanvas.ImageSetEntry(
                image = set[0].image,
                srcRect = SkRect.MakeWH(kTileW.toFloat(), kTileH.toFloat())
                    .makeInset(kTileW / 4f, kTileH / 4f),
                dstRect = SkRect.MakeWH(1.5f * kTileW, 1.5f * kTileH)
                    .makeOffset(d / 4f, 2f * d),
                alpha = 0.7f,
                aaFlags = QuadAAFlags.kLeft_QuadAAFlag or QuadAAFlags.kTop_QuadAAFlag,
            )
            c.withSave {
                rotate(3f)
                val exclusionPaint = SkPaint().apply {
                    blendMode = SkBlendMode.kExclusion
                }
                experimental_DrawEdgeAAImageSet(
                    arrayOf(entry), 1, null, null, sampling, exclusionPaint,
                    SrcRectConstraint.kFast,
                )
                translate(entry.dstRect.width() + 8f, 0f)
                val cfPaint = SkPaint().apply {
                    blendMode = SkBlendMode.kExclusion
                    colorFilter = SkColorFilters.LinearToSRGBGamma()
                }
                experimental_DrawEdgeAAImageSet(
                    arrayOf(entry), 1, null, null, sampling, cfPaint,
                    SrcRectConstraint.kFast,
                )
            }
            c.translate(2f * d, 0f)
        }
    }

    /**
     * Equivalent to `SkRect::toQuad` — four corners (TL, TR, BR, BL)
     * in upstream's `SkRect::toQuad` order.
     */
    private fun rectToQuad(r: SkRect): Array<SkPoint> = arrayOf(
        SkPoint(r.left, r.top),
        SkPoint(r.right, r.top),
        SkPoint(r.right, r.bottom),
        SkPoint(r.left, r.bottom),
    )

    /**
     * Mirrors upstream's
     * ```cpp
     * SkVector v = pts[1] - pts[0];
     * v.setLength(v.length() + kLineOutset);
     * canvas->drawLine(pts[1] - v, pts[0] + v, paint);
     * ```
     * — paints the segment `(p0, p1)` extended outward by [outset] on each
     * end so the red guide-lines visibly cross the tile-grid boundary.
     */
    private fun drawExtendedGridLine(
        canvas: SkCanvas, p0: SkPoint, p1: SkPoint, outset: Float, paint: SkPaint,
    ) {
        val dx = p1.fX - p0.fX
        val dy = p1.fY - p0.fY
        val len = sqrt(dx * dx + dy * dy)
        if (len <= 0f) return
        val target = len + outset
        val nx = dx * target / len
        val ny = dy * target / len
        canvas.drawLine(
            p1.fX - nx, p1.fY - ny,
            p0.fX + nx, p0.fY + ny,
            paint,
        )
    }

    private companion object {
        const val kM = 4
        const val kN = 3
        const val kTileW = 30
        const val kTileH = 60
    }
}
