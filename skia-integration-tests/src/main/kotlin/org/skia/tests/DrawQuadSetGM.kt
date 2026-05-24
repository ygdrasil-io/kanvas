package org.skia.tests

import org.skia.core.QuadAAFlags
import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

// ─── Constants mirroring the C++ file-scope statics ───────────────────────────

private const val kTileWidth: Float = 40f
private const val kTileHeight: Float = 30f
private const val kRowCount: Int = 4
private const val kColCount: Int = 3
private const val kLineOutset: Float = 10f

private val kTileSetNames = arrayOf("Local", "Aligned", "Green", "Multicolor")

// ─── Helpers ──────────────────────────────────────────────────────────────────

/** Builds the per-edge AA bitmask for tile at `(row=i, col=j)`. */
private fun dqsEdgeAaFlags(i: Int, j: Int): Int {
    var aa = QuadAAFlags.kNone_QuadAAFlags
    if (i == 0)              aa = aa or QuadAAFlags.kTop_QuadAAFlag
    if (i == kRowCount - 1) aa = aa or QuadAAFlags.kBottom_QuadAAFlag
    if (j == 0)              aa = aa or QuadAAFlags.kLeft_QuadAAFlag
    if (j == kColCount - 1) aa = aa or QuadAAFlags.kRight_QuadAAFlag
    return aa
}

/**
 * Mirrors `draw_text` from the upstream C++ — draws [text] at the canvas
 * origin with a 12 pt portable typeface and a default (black fill) paint.
 */
private fun dqsDrawText(canvas: SkCanvas, text: String) {
    val font = SkFont(ToolUtils.DefaultPortableTypeface(), 12f)
    canvas.drawString(text, 0f, 0f, font, SkPaint())
}

/**
 * Raster fallback for `draw_gradient_tiles(canvas, alignGradients=false)`.
 *
 * Upstream's GPU branch draws a blue–white linear gradient; on the raster
 * backend the code falls through to [SkCanvas.experimental_DrawEdgeAAQuad]
 * with solid colours: alternating BLUE/WHITE based on `(i*kColCount+j) % 2`.
 * Tiles are placed by save/translate/restore (matching the unaligned branch).
 */
private fun dqsDrawGradientTilesLocal(canvas: SkCanvas) {
    for (i in 0 until kRowCount) {
        for (j in 0 until kColCount) {
            val tile = SkRect.MakeWH(kTileWidth, kTileHeight)
            canvas.save()
            canvas.translate(j * kTileWidth, i * kTileHeight)
            val aa = dqsEdgeAaFlags(i, j)
            val color = if ((i * kColCount + j) % 2 == 0) SK_ColorBLUE else SK_ColorWHITE
            canvas.experimental_DrawEdgeAAQuad(tile, null, aa, color, SkBlendMode.kSrcOver)
            canvas.restore()
        }
    }
}

/**
 * Raster fallback for `draw_gradient_tiles(canvas, alignGradients=true)`.
 *
 * On the raster backend upstream always draws BLUE (the `alignGradients`
 * branch sets `color = SK_ColorBLUE`). Tiles are positioned by offsetting
 * the rect directly (no save/restore).
 */
private fun dqsDrawGradientTilesAligned(canvas: SkCanvas) {
    for (i in 0 until kRowCount) {
        for (j in 0 until kColCount) {
            val tile = SkRect.MakeXYWH(j * kTileWidth, i * kTileHeight, kTileWidth, kTileHeight)
            val aa = dqsEdgeAaFlags(i, j)
            canvas.experimental_DrawEdgeAAQuad(tile, null, aa, SK_ColorBLUE, SkBlendMode.kSrcOver)
        }
    }
}

/**
 * Mirrors `draw_color_tiles(canvas, multicolor=false)`.
 *
 * Upstream colour: `SkColor4f{0.2f, 0.8f, 0.3f, 1.f}`.toSkColor() ≈ 0xFF33CC4C.
 * We compute the 8-bit round-trip exactly: `round(c * 255)`.
 */
private fun dqsDrawColorTilesGreen(canvas: SkCanvas) {
    // SkColor4f{.2f, .8f, .3f, 1.f} → R=51 G=204 B=77 A=255
    val color = (255 shl 24) or (51 shl 16) or (204 shl 8) or 77
    for (i in 0 until kRowCount) {
        for (j in 0 until kColCount) {
            val tile = SkRect.MakeXYWH(j * kTileWidth, i * kTileHeight, kTileWidth, kTileHeight)
            val aa = dqsEdgeAaFlags(i, j)
            canvas.experimental_DrawEdgeAAQuad(tile, null, aa, color, SkBlendMode.kSrcOver)
        }
    }
}

/**
 * Mirrors `draw_color_tiles(canvas, multicolor=true)`.
 *
 * Upstream colour: `{(i+1)/kRowCount, (j+1)/kColCount, 0.4f, 1.f}` per tile.
 */
private fun dqsDrawColorTilesMulticolor(canvas: SkCanvas) {
    for (i in 0 until kRowCount) {
        for (j in 0 until kColCount) {
            val tile = SkRect.MakeXYWH(j * kTileWidth, i * kTileHeight, kTileWidth, kTileHeight)
            val aa = dqsEdgeAaFlags(i, j)
            val r = ((i + 1f) / kRowCount * 255f + 0.5f).toInt().coerceIn(0, 255)
            val g = ((j + 1f) / kColCount * 255f + 0.5f).toInt().coerceIn(0, 255)
            val b = (0.4f * 255f + 0.5f).toInt().coerceIn(0, 255)
            val color = (255 shl 24) or (r shl 16) or (g shl 8) or b
            canvas.experimental_DrawEdgeAAQuad(tile, null, aa, color, SkBlendMode.kSrcOver)
        }
    }
}

/**
 * Mirrors `draw_tile_boundaries` — red hairlines at interior tile boundaries,
 * mapped through [local] and extended by `kLineOutset` on each end.
 */
private fun dqsDrawTileBoundaries(canvas: SkCanvas, local: SkMatrix) {
    val paint = SkPaint().apply {
        isAntiAlias = true
        color = SK_ColorRED
        style = SkPaint.Style.kStroke_Style
        strokeWidth = 0f
    }
    // Vertical interior lines: x = 1, 2
    for (x in 1 until kColCount) {
        val pts = arrayOf(
            SkPoint(x * kTileWidth, 0f),
            SkPoint(x * kTileWidth, kRowCount * kTileHeight),
        )
        local.mapPoints(pts)
        val v = pts[1] - pts[0]          // direction pts[0]→pts[1]
        v.setLength(v.length() + kLineOutset)
        canvas.drawLine(
            pts[1].fX - v.fX, pts[1].fY - v.fY,
            pts[0].fX + v.fX, pts[0].fY + v.fY,
            paint,
        )
    }
    // Horizontal interior lines: y = 1, 2, 3
    for (y in 1 until kRowCount) {
        val pts = arrayOf(
            SkPoint(0f, y * kTileHeight),
            SkPoint(kTileWidth * kColCount, y * kTileHeight),
        )
        local.mapPoints(pts)
        val v = pts[1] - pts[0]
        v.setLength(v.length() + kLineOutset)
        canvas.drawLine(
            pts[1].fX - v.fX, pts[1].fY - v.fY,
            pts[0].fX + v.fX, pts[0].fY + v.fY,
            paint,
        )
    }
}

// ─── GM class ─────────────────────────────────────────────────────────────────

/**
 * Port of Skia's `gm/drawquadset.cpp` `DrawQuadSetGM` (GM name `draw_quad_set`,
 * 800 × 800).
 *
 * Renders a 5-row × 4-column grid that exercises
 * [org.skia.core.SkCanvas.experimental_DrawEdgeAAQuad] under five CTMs
 * (identity, translate+scale, rotate, skew, perspective) and four tile
 * variants (local-gradient, aligned-gradient, solid-green, multicolor).
 *
 * The upstream C++ uses a Ganesh GPU path for the gradient tile sets; on raster
 * the C++ fallback uses solid BLUE/WHITE, which this port reproduces exactly.
 */
public class DrawQuadSetGM : GM() {

    override fun getName(): String = "draw_quad_set"
    override fun getISize(): SkISize = SkISize.Make(800, 800)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // ─── Row transformation matrices ──────────────────────────────────
        // Identity
        val m0 = SkMatrix.Identity

        // Translate(5.5, 20.25) then postScale(0.9, 0.7)
        val m1 = SkMatrix.MakeTrans(5.5f, 20.25f).postScale(0.9f, 0.7f)

        // Rotate(20°) then preTranslate(15, -20)
        val m2 = SkMatrix.MakeRotate(20.0f).preTranslate(15f, -20f)

        // Skew(0.5, 0.25) then preTranslate(-30, 0)
        val m3 = SkMatrix.MakeSkew(0.5f, 0.25f).preTranslate(-30f, 0f)

        // Perspective: maps the tile-grid corners to a slightly warped quad,
        // then preTranslate(0, +10).
        val totalW = kColCount * kTileWidth
        val totalH = kRowCount * kTileHeight
        val srcQuad = arrayOf(
            SkPoint(0f, 0f),
            SkPoint(totalW, 0f),
            SkPoint(totalW, totalH),
            SkPoint(0f, totalH),
        )
        val dstQuad = arrayOf(
            SkPoint(0f, 0f),
            SkPoint(totalW + 10f, 15f),
            SkPoint(totalW - 28f, totalH + 40f),
            SkPoint(25f, totalH - 15f),
        )
        val m4 = (SkMatrix.MakePolyToPoly(srcQuad, dstQuad) ?: SkMatrix.Identity)
            .preTranslate(0f, +10f)

        val rowMatrices = arrayOf(m0, m1, m2, m3, m4)
        val matrixNames = arrayOf("Identity", "T+S", "Rotate", "Skew", "Perspective")

        val tileSets: Array<(SkCanvas) -> Unit> = arrayOf(
            ::dqsDrawGradientTilesLocal,
            ::dqsDrawGradientTilesAligned,
            ::dqsDrawColorTilesGreen,
            ::dqsDrawColorTilesMulticolor,
        )

        // ─── Column header ────────────────────────────────────────────────
        c.save()
        c.translate(110f, 20f)
        for (name in kTileSetNames) {
            dqsDrawText(c, name)
            c.translate(kColCount * kTileWidth + 30f, 0f)
        }
        c.restore()
        c.translate(0f, 40f)

        // ─── Render all rows ──────────────────────────────────────────────
        for (i in rowMatrices.indices) {
            c.save()
            c.translate(10f, 0.5f * kRowCount * kTileHeight)
            dqsDrawText(c, matrixNames[i])

            c.translate(100f, -0.5f * kRowCount * kTileHeight)
            for (j in tileSets.indices) {
                c.save()
                dqsDrawTileBoundaries(c, rowMatrices[i])
                c.concat(rowMatrices[i])
                tileSets[j](c)
                c.restore()
                c.translate(kColCount * kTileWidth + 30f, 0f)
            }
            c.restore()
            c.translate(0f, kRowCount * kTileHeight + 20f)
        }
    }
}
