package org.skia.tests

import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalarAbs
import org.graphiks.math.SkScalarNearlyZero
import org.graphiks.math.SkVector
import org.skia.core.QuadAAFlags
import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.tools.ToolUtils

/**
 * Partial port of Skia's
 * [`gm/compositor_quads.cpp`](https://github.com/google/skia/blob/main/gm/compositor_quads.cpp)
 * — Chromium-style batched-quad GM exercising the per-edge AA quad
 * draw surface (`SkCanvas.experimental_DrawEdgeAAQuad`) under several
 * BSP clipping configurations and a matrix of CTM transforms.
 *
 * ## Upstream scope (5 `DEF_GM` variants)
 *
 * ```cpp
 * DEF_GM(return new CompositorGM("debug",  make_debug_renderers);)    // SolidColor + AA / no-AA overrides
 * DEF_GM(return new CompositorGM("color",  make_solid_color_renderers);) // single SolidColor renderer
 * DEF_GM(return new CompositorGM("shader", make_shader_renderers);)   // TextureSetRenderer + SkShader
 * DEF_GM(return new CompositorGM("image",  make_image_renderers);)    // TextureSet + YUVTextureSet
 * DEF_GM(return new CompositorGM("filter", make_filtered_renderers);) // TextureSet + alpha / cf / if / mf
 * ```
 *
 * Each variant shares the same grid : `kMatrixCount=5` transforms
 * (columns) × N renderers (rows), drawing a `kColCount=3` × `kRowCount=4`
 * tile-grid clipped against three BSP lines formed by
 * `kClipP1 / kClipP2 / kClipP3`. Each tile is split against the 3 lines
 * and each leaf sub-quad is sent through the renderer's
 * `experimental_DrawEdgeAAQuad` / `experimental_DrawEdgeAAImageSet`
 * call.
 *
 * ## Port status (this file → `compositor_quads_color`)
 *
 * **Bucket : MISSING_API (subset port).** The `image` variant is already
 * partially covered by [CompositorQuadsImageGM] (the YUV-roundtrip
 * fixture half). This file ports the **`color` variant only** — the
 * single-`SolidColorRenderer` minimum that exercises the full
 * BSP-clip-tile + per-edge AA + per-quad CTM pipeline against the live
 * [SkCanvas.experimental_DrawEdgeAAQuad] surface in `:kanvas-skia`.
 *
 * What's **ported**, end-to-end :
 *   * `clipping_line_segment` / `intersect_line_segments` / `clipTile`
 *     BSP recursion (lines 78-417 of upstream cpp) — Kotlin verbatim,
 *     including the degenerate-pentagon split branches.
 *   * `drawTile` for the [SolidColorRenderer] family — solid green
 *     `(0.2, 0.8, 0.3, 1)` quads with [QuadAAFlags] from [maskToFlags].
 *   * `draw_tile_boundaries` / `draw_clipping_boundaries` — red & green
 *     stroke overlays mapped through the per-cell CTM.
 *   * Banner text via [SkFont] + [SkCanvas.drawString].
 *   * Transforms : **4 of 5** — Identity, Translate+Scale, Rotate,
 *     Skew. The `Perspective` column relies on `SkMatrix::setPolyToPoly`,
 *     which is not yet ported to `:math` (Phase R-perspective TODO).
 *     The matrix is replaced with [SkMatrix.I] so the layout stays
 *     identical and pixel diffs are localised to the rightmost column.
 *
 * What's **commented out / stubbed** :
 *   * The `debug` / `shader` / `image` / `filter` `DEF_GM` variants —
 *     each would need its own GM subclass (`CompositorQuadsDebugGM`,
 *     `…ShaderGM`, …). The `image` half is already partially live in
 *     [CompositorQuadsImageGM]. Adding the rest would require porting
 *     `SkCanvas.experimental_DrawEdgeAAImageSet` (`MISSING_API`,
 *     `TODO("STUB.EDGE_AA_IMAGE_SET")`), the upstream `LazyYUVImage`
 *     helper end-to-end, and the per-quad shader / colour-filter /
 *     image-filter / mask-filter knobs.
 *   * `SkLineClipper::IntersectLine` for the boundary visualiser — we
 *     simulate it inline by drawing the outset line segment directly
 *     (the upstream version clips against the tile-grid bbox so the
 *     line ends just outside the grid ; pixel diff is bounded to the
 *     four corners of the green overlay).
 *   * The "Draws = N" footer text (we still count to mirror the upstream
 *     trace but only the renderer banner is drawn, so the layout offset
 *     stays right).
 *
 * Future work : promote to full coverage as the missing APIs land —
 * `setPolyToPoly` on `SkMatrix` for the perspective transform, and
 * `experimental_DrawEdgeAAImageSet` on `SkCanvas` for the texture
 * variants.
 */
public class CompositorGM : GM() {

    override fun getName(): String = "compositor_quads_color"

    override fun getISize(): SkISize {
        // Match upstream's getISize() with one renderer row (color variant)
        // and 5 transform columns:
        //   width  = round(kCellWidth  × 5 + 175)
        //   height = round(kCellHeight × 1 + 75)
        val width = (kCellWidth * kMatrixCount + 175f).toInt()
        val height = (kCellHeight * 1 + 75f).toInt()
        return SkISize.Make(width, height)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val matrices = configureMatrices()
        val matrixNames = listOf("Identity", "T+S", "Rotate", "Skew", "Perspective")

        // Banner font shared across all text draws (upstream uses
        // `SkFont(DefaultPortableTypeface(), 12)`).
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), 12f)

        // Single renderer row : SolidColorRenderer with green.
        val renderer = SolidColorRenderer(
            color = SkColor4f(0.2f, 0.8f, 0.3f, 1f),
        )

        c.save()
        c.translate(kOffset + kBannerWidth, kOffset)
        for (i in matrices.indices) {
            c.save()
            // Column header — matrix name.
            c.drawString(matrixNames[i], 0f, 0f, font, SkPaint())
            c.translate(0f, kGap)

            // Single renderer row.
            c.save()
            drawTileBoundaries(c, matrices[i])
            drawClippingBoundaries(c, matrices[i])
            c.concat(matrices[i])
            renderer.drawTiles(c)
            c.restore()

            c.translate(0f, kGap + kRowCount * kTileHeight)
            c.restore()
            c.translate(kGap + kColCount * kTileWidth, 0f)
        }
        c.restore()

        // Row header banner (renderer name, no draw-count footer).
        c.save()
        c.translate(kOffset, kGap + 0.5f * kRowCount * kTileHeight)
        renderer.drawBanner(c, font)
        c.restore()
    }

    // ── Matrix grid ───────────────────────────────────────────────────

    /**
     * Mirrors `CompositorGM::configureMatrices` (cpp lines 509-545).
     * 4 of 5 ported verbatim; perspective stub'd to identity until
     * `SkMatrix.setPolyToPoly` lands (see class doc).
     */
    private fun configureMatrices(): List<SkMatrix> {
        val identity = SkMatrix.I()

        // Translate+Scale : setTranslate(5.5, 20.25); postScale(.9, .7)
        val ts = SkMatrix.MakeTrans(5.5f, 20.25f).postScale(0.9f, 0.7f)

        // Rotate : setRotate(20); preTranslate(15, -20)
        val rotate = SkMatrix.MakeRotate(20f).preTranslate(15f, -20f)

        // Skew : setSkew(.5, .25); preTranslate(-30, 0)
        val skew = SkMatrix.MakeSkew(0.5f, 0.25f).preTranslate(-30f, 0f)

        // Perspective : upstream uses setPolyToPoly(src, dst) — not yet
        // available in :math. Stub with identity to preserve layout.
        // TODO: switch to SkMatrix.setPolyToPoly once R-perspective lands.
        val perspectiveStub = SkMatrix.I()

        return listOf(identity, ts, rotate, skew, perspectiveStub)
    }

    // ── BSP tile-grid clip / draw (cpp lines 205-417) ─────────────────

    private inner class SolidColorRenderer(private val color: SkColor4f) {

        fun drawTiles(canvas: SkCanvas): Int {
            // All three BSP lines as a 6-point array.
            val lines = Array(6) { SkPoint() }
            clippingLineSegment(kClipP1, kClipP2, lines, 0)
            clippingLineSegment(kClipP2, kClipP3, lines, 2)
            clippingLineSegment(kClipP3, kClipP1, lines, 4)

            var tileID = 0
            var drawCount = 0
            for (i in 0 until kRowCount) {
                for (j in 0 until kColCount) {
                    val tile = SkRect.MakeXYWH(
                        j * kTileWidth, i * kTileHeight,
                        kTileWidth, kTileHeight,
                    )
                    val edgeAA = booleanArrayOf(
                        i == 0,                 // Top
                        j == kColCount - 1,     // Right
                        i == kRowCount - 1,     // Bottom
                        j == 0,                 // Left
                    )
                    val quadCount = intArrayOf(0)
                    drawCount += clipTile(canvas, tileID, tile, null, edgeAA, lines, 0, 3, quadCount)
                    tileID++
                }
            }
            return drawCount
        }

        fun drawTile(
            canvas: SkCanvas,
            rect: SkRect,
            clip: Array<SkPoint>?,
            edgeAA: BooleanArray,
            tileID: Int,
            quadID: Int,
        ): Int {
            canvas.experimental_DrawEdgeAAQuad(
                rect, clip, maskToFlags(edgeAA), color.toSkColor(), SkBlendMode.kSrcOver,
            )
            return 1
        }

        fun drawBanner(canvas: SkCanvas, font: SkFont) {
            canvas.drawString("Solid Color", 0f, 0f, font, SkPaint())
        }

        private fun maskToFlags(edgeAA: BooleanArray): Int {
            var flags = 0
            if (edgeAA[0]) flags = flags or QuadAAFlags.kTop_QuadAAFlag
            if (edgeAA[1]) flags = flags or QuadAAFlags.kRight_QuadAAFlag
            if (edgeAA[2]) flags = flags or QuadAAFlags.kBottom_QuadAAFlag
            if (edgeAA[3]) flags = flags or QuadAAFlags.kLeft_QuadAAFlag
            return flags
        }

        /**
         * Iso-aligned port of `ClipTileRenderer::clipTile` (cpp lines
         * 265-417). Recursively splits the tile against the 3 BSP lines
         * and invokes [drawTile] on each leaf sub-quad. The split logic
         * is verbatim with the upstream cpp — see the C++ comments for
         * the geometric rationale.
         */
        private fun clipTile(
            canvas: SkCanvas,
            tileID: Int,
            baseRect: SkRect,
            quad: Array<SkPoint>?,
            edgeAA: BooleanArray,
            lines: Array<SkPoint>,
            lineOffset: Int,
            lineCount: Int,
            quadCount: IntArray,
        ): Int {
            if (lineCount == 0) {
                val draws = drawTile(canvas, baseRect, quad, edgeAA, tileID, quadCount[0])
                quadCount[0] += 1
                return draws
            }

            // Indices into `points` array (cpp lines 275-280).
            val kTL = 0; val kTR = 1; val kBR = 2; val kBL = 3
            val kS0 = 4; val kS1 = 5

            val points = Array(6) { SkPoint() }
            if (quad != null) {
                for (i in 0 until 4) points[i] = SkPoint(quad[i].fX, quad[i].fY)
            } else {
                // baseRect.copyToQuad(points) — TL, TR, BR, BL.
                points[0] = SkPoint(baseRect.left, baseRect.top)
                points[1] = SkPoint(baseRect.right, baseRect.top)
                points[2] = SkPoint(baseRect.right, baseRect.bottom)
                points[3] = SkPoint(baseRect.left, baseRect.bottom)
            }

            val splitIndices = IntArray(2)
            var intersectionCount = 0
            for (i in 0 until 4) {
                val intersect = SkPoint()
                val nextI = if (i == 3) 0 else i + 1
                val l0 = lines[lineOffset]
                val l1 = lines[lineOffset + 1]
                if (intersectLineSegments(points[i], points[nextI], l0, l1, intersect)) {
                    var duplicate = false
                    for (j in 0 until intersectionCount) {
                        if (SkScalarNearlyZero((intersect - points[kS0 + j]).length())) {
                            duplicate = true; break
                        }
                    }
                    if (!duplicate) {
                        points[kS0 + intersectionCount] = intersect
                        splitIndices[intersectionCount] = i
                        intersectionCount++
                    }
                }
            }

            if (intersectionCount < 2) {
                return clipTile(
                    canvas, tileID, baseRect, quad, edgeAA, lines,
                    lineOffset + 2, lineCount - 1, quadCount,
                )
            }

            check(intersectionCount == 2)

            // List of (sub-quad as 4 indices into `points`) — match cpp's
            // STArray<3, std::array<int, 4>>.
            val subtiles = mutableListOf<IntArray>()
            var s2 = -1
            if (splitIndices[1] - splitIndices[0] == 2) {
                // Opposite edges — trivial 2-quad split.
                if (splitIndices[0] == 0) {
                    subtiles.add(intArrayOf(kTL, kS0, kS1, kBL))
                    subtiles.add(intArrayOf(kS0, kTR, kBR, kS1))
                } else {
                    subtiles.add(intArrayOf(kTL, kTR, kS0, kS1))
                    subtiles.add(intArrayOf(kS1, kS0, kBR, kBL))
                }
            } else {
                // Adjacent edges — degenerate-pentagon split.
                when (splitIndices[0]) {
                    0 -> if (splitIndices[1] == 1) {
                        s2 = kBL
                        subtiles.add(intArrayOf(kS0, kTR, kS1, kS0)) // degenerate
                        subtiles.add(intArrayOf(kTL, kS0, if (edgeAA[0]) kS0 else kBL, kBL))
                        subtiles.add(intArrayOf(kS0, kS1, kBR, kBL))
                    } else {
                        check(splitIndices[1] == 3)
                        s2 = kBR
                        subtiles.add(intArrayOf(kTL, kS0, kS1, kS1)) // degenerate
                        subtiles.add(intArrayOf(kS1, if (edgeAA[3]) kS1 else kBR, kBR, kBL))
                        subtiles.add(intArrayOf(kS0, kTR, kBR, kS1))
                    }
                    1 -> {
                        check(splitIndices[1] == 2)
                        s2 = kTL
                        subtiles.add(intArrayOf(kS0, kS0, kBR, kS1)) // degenerate
                        subtiles.add(intArrayOf(kTL, kTR, kS0, if (edgeAA[1]) kS0 else kTL))
                        subtiles.add(intArrayOf(kTL, kS0, kS1, kBL))
                    }
                    2 -> {
                        check(splitIndices[1] == 3)
                        s2 = kTR
                        subtiles.add(intArrayOf(kS1, kS0, kS0, kBL)) // degenerate
                        subtiles.add(intArrayOf(if (edgeAA[2]) kS0 else kTR, kTR, kBR, kS0))
                        subtiles.add(intArrayOf(kTL, kTR, kS0, kS1))
                    }
                    else -> {
                        // splitIndices[0] == 3 should have been swapped earlier.
                        error("Unexpected splitIndices[0]=${splitIndices[0]}")
                    }
                }
            }

            val sub = Array(4) { SkPoint() }
            val subAA = BooleanArray(4)
            var draws = 0
            for (subtile in subtiles) {
                for (j in 0 until 4) {
                    val p = subtile[j]
                    sub[j] = SkPoint(points[p].fX, points[p].fY)

                    val np = if (j == 3) subtile[0] else subtile[j + 1]
                    if ((p >= kS0 && (np == s2 || np >= kS0)) ||
                        (np >= kS0 && (p == s2 || p >= kS0))
                    ) {
                        subAA[j] = false
                    } else {
                        subAA[j] = edgeAA[j]
                    }
                }
                draws += clipTile(
                    canvas, tileID, baseRect, sub, subAA, lines,
                    lineOffset + 2, lineCount - 1, quadCount,
                )
            }
            return draws
        }
    }

    // ── Boundary visualisation (cpp lines 139-193) ────────────────────

    /** Mirror of `draw_outset_line` (cpp lines 139-147). */
    private fun drawOutsetLine(
        canvas: SkCanvas,
        local: SkMatrix,
        p0: SkPoint, p1: SkPoint,
        paint: SkPaint,
    ) {
        val kLineOutset = 10f
        val mapped = Array(2) { SkPoint() }
        local.mapPoints(mapped, arrayOf(p0, p1), 2)
        val v0 = mapped[1] - mapped[0]
        val lenOut = v0.length() + kLineOutset
        val v = SkPoint(v0.fX, v0.fY).also { it.setLength(it.fX, it.fY, lenOut) }
        canvas.drawLine(
            mapped[1].fX - v.fX, mapped[1].fY - v.fY,
            mapped[0].fX + v.fX, mapped[0].fY + v.fY,
            paint,
        )
    }

    /** Mirror of `draw_tile_boundaries` (cpp lines 150-164). */
    private fun drawTileBoundaries(canvas: SkCanvas, local: SkMatrix) {
        val paint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorRED
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
        }
        for (x in 1 until kColCount) {
            drawOutsetLine(
                canvas, local,
                SkPoint(x * kTileWidth, 0f),
                SkPoint(x * kTileWidth, kRowCount * kTileHeight),
                paint,
            )
        }
        for (y in 1 until kRowCount) {
            drawOutsetLine(
                canvas, local,
                SkPoint(0f, y * kTileHeight),
                SkPoint(kTileWidth * kColCount, y * kTileHeight),
                paint,
            )
        }
    }

    /**
     * Mirror of `draw_clipping_boundaries` (cpp lines 167-193). Upstream
     * uses `SkLineClipper::IntersectLine` to trim the extended line to
     * the tile-grid bbox ; that helper is not ported to `:math` yet, so
     * we approximate by drawing the outset endpoint pair directly. The
     * resulting overlay extends slightly beyond the tile grid (matching
     * upstream within ~`kLineOutset` px on each end).
     */
    private fun drawClippingBoundaries(canvas: SkCanvas, local: SkMatrix) {
        val paint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorGREEN
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0f
        }

        // Note : without SkLineClipper, we draw the unclipped outset
        // line directly. Pixel diff localised to the 4 corners outside
        // the tile grid.
        val line = Array(2) { SkPoint() }
        clippingLineSegment(kClipP1, kClipP2, line, 0)
        drawOutsetLine(canvas, local, line[0], line[1], paint)

        clippingLineSegment(kClipP2, kClipP3, line, 0)
        drawOutsetLine(canvas, local, line[0], line[1], paint)

        clippingLineSegment(kClipP3, kClipP1, line, 0)
        drawOutsetLine(canvas, local, line[0], line[1], paint)
    }

    // ── Geometry helpers (cpp lines 78-136) ───────────────────────────

    /**
     * Mirror of `clipping_line_segment` (cpp lines 78-84). Writes the
     * 10×-extended line into `line[offset]` and `line[offset+1]`.
     */
    private fun clippingLineSegment(p0: SkPoint, p1: SkPoint, line: Array<SkPoint>, offset: Int) {
        val v: SkVector = p1 - p0
        line[offset] = SkPoint(p0.fX - v.fX * 10f, p0.fY - v.fY * 10f)
        line[offset + 1] = SkPoint(p1.fX + v.fX * 10f, p1.fY + v.fY * 10f)
    }

    /**
     * Iso-aligned port of `intersect_line_segments` (cpp lines 88-136).
     * Uses doubles for the discriminant to preserve T-junction precision
     * (mirrors the upstream double-precision intermediate).
     */
    private fun intersectLineSegments(
        p0: SkPoint, p1: SkPoint,
        l0: SkPoint, l1: SkPoint,
        intersect: SkPoint,
    ): Boolean {
        val kHorizontalTolerance = 0.01f

        val pY = (p1.fY - p0.fY).toDouble()
        val pX = (p1.fX - p0.fX).toDouble()
        val lY = (l1.fY - l0.fY).toDouble()
        val lX = (l1.fX - l0.fX).toDouble()
        val plY = (p0.fY - l0.fY).toDouble()
        val plX = (p0.fX - l0.fX).toDouble()

        if (SkScalarNearlyZero(pY.toFloat(), kHorizontalTolerance)) {
            if (SkScalarNearlyZero(lY.toFloat(), kHorizontalTolerance)) {
                return false // two horizontal lines
            } else {
                return intersectLineSegments(l0, l1, p0, p1, intersect)
            }
        }

        val lNumerator = plX * pY - plY * pX
        val lDenom = lX * pY - lY * pX
        if (SkScalarNearlyZero(lDenom.toFloat())) {
            return false // parallel or identical
        }

        val alphaL = lNumerator / lDenom
        if (alphaL < 0.0 || alphaL > 1.0) {
            return false
        }

        val alphaP = (alphaL * lY - plY) / pY
        if (alphaP < 0.0 || alphaP > 1.0) {
            return false
        }

        val aL = alphaL.toFloat()
        val ix = l1.fX * aL + l0.fX * (1f - aL)
        val iy = l1.fY * aL + l0.fY * (1f - aL)
        intersect.set(ix, iy)
        return true
    }

    @Suppress("UnusedPrivateProperty")
    private companion object {
        // Grid metrics (cpp lines 59-63)
        const val kTileWidth: Float = 40f
        const val kTileHeight: Float = 30f
        const val kRowCount: Int = 4
        const val kColCount: Int = 3

        // BSP clip points (cpp lines 68-70)
        val kClipP1: SkPoint = SkPoint(1.75f * kTileWidth, 0.8f * kTileHeight)
        val kClipP2: SkPoint = SkPoint(0.6f * kTileWidth, 2f * kTileHeight)
        val kClipP3: SkPoint = SkPoint(2.9f * kTileWidth, 3.5f * kTileHeight)

        // CompositorGM layout (cpp lines 430-498)
        const val kMatrixCount: Int = 5
        const val kGap: Float = 40f
        const val kBannerWidth: Float = 120f
        const val kOffset: Float = 15f

        // Cell pad — 1.3× to leave room for transforms (cpp line 438)
        const val kCellWidth: Float = 1.3f * kColCount * kTileWidth
        const val kCellHeight: Float = 1.3f * kRowCount * kTileHeight

        // Silence "unused" warnings — kept for documentation /
        // future-port hooks.
        @Suppress("unused")
        private fun keepReferences() {
            SkScalarAbs(0f)
        }
    }
}
