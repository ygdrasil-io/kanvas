package org.skia.tests

import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRSXform
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSurfaces
import org.skia.foundation.SkTextBlob
import org.skia.foundation.SkTextBlobBuilder
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTileMode
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/rsxtext.cpp::RSXShaderGM`](https://github.com/google/skia/blob/main/gm/rsxtext.cpp)
 * (`rsx_blob_shader`, 882 × 882).
 *
 * Exercises [SkTextBlobBuilder.allocRunRSXform] (per-glyph rotation +
 * scale + translate via [SkRSXform]) combined with a bitmap shader
 * carrying a `(localMatrix, outerLocalMatrix)` pair. The shader is a
 * 30 × 30 yellow tile with an inset green inner rect ; the GM draws it
 * over a backdrop rectangle and then twice on top through the RSX
 * text blob so the yellow grid stays aligned for the text vs. the
 * backdrop (upstream's header comment).
 *
 * Four 300 × 300 quadrants laid out as 2 × 2 with `kSZ * 1.1f` gutter,
 * scaled up by `kScale = 1.4f` :
 *  - `(0, 0)`           : `(localMatrix = I, outerLocalMatrix = I)`
 *  - `(kSZ*1.1f, 0)`    : `Scale(2, 2)` outer-identity
 *  - `(0, kSZ*1.1f)`    : identity + `Rotate(45°)` outer
 *  - `(kSZ*1.1f, kSZ*1.1f)` : `Scale(2, 2)` + `Rotate(45°)` outer
 *
 * The TEST text blob lays out four glyphs at `{1, 0, xAdvance, 0}`
 * (identity 2×2, translate only) so the visible per-glyph layout is
 * the upstream horizontal stride — the RSX path stresses the new
 * builder + canvas plumbing rather than producing rotated glyphs.
 *
 * ## Port note — pure raster
 *
 * Upstream builds the 30 × 30 tile via `SkSurfaces::Raster` and a
 * `Premul` 8888 surface ; the kanvas-skia [SkSurfaces.Raster] mirrors
 * that contract (premul N32 surface backed by an [org.skia.foundation.SkImage]
 * snapshot). The shader stacking uses [SkImage.makeShader] for the
 * inner `localMatrix` and [org.skia.foundation.SkShader.makeWithLocalMatrix]
 * for the outer one — upstream's two `makeShader` / `makeWithLocalMatrix`
 * calls fold to a single [org.skia.foundation.SkLocalMatrixShader] in
 * kanvas-skia (see the folding rule in `SkShader.makeWithLocalMatrix`),
 * which preserves the visual outcome.
 */
public class RSXShaderGM : GM() {

    override fun getName(): String = "rsx_blob_shader"

    override fun getISize(): SkISize {
        val px = (kSZ * kScale * 2.1f).toInt()
        return SkISize.Make(px, px)
    }

    private var fBlob: SkTextBlob? = null

    override fun onOnceBeforeDraw() {
        // Mirrors upstream's `onOnceBeforeDraw` — resolve the portable
        // ExtraBlack / Normal / Upright Sans typeface, build an RSXform
        // blob for "TEST" with one per-glyph transform that shifts each
        // glyph horizontally by its measured advance.
        val style = SkFontStyle(
            SkFontStyle.kExtraBlack_Weight,
            SkFontStyle.kNormal_Width,
            SkFontStyle.Slant.kUpright_Slant,
        )
        val font = SkFont(ToolUtils.CreatePortableTypeface("Sans", style), kFontSZ).apply {
            edging = SkFont.Edging.kAntiAlias
        }

        val txt = "TEST"
        val glyphs = font.textToGlyphs(txt, SkTextEncoding.kUTF8)
        val widths = font.getWidths(glyphs)

        val builder = SkTextBlobBuilder()
        val buf = builder.allocRunRSXform(font, glyphs.size)
        // Copy glyph IDs into the builder's slot.
        for (i in glyphs.indices) {
            buf.glyphs[i] = glyphs[i]
        }
        // Each glyph : identity 2×2 ({1, 0} / {0, 1}) with horizontal
        // translate stepping by the previous glyph's advance.
        var x = 0f
        for (i in glyphs.indices) {
            buf.xforms[i] = SkRSXform(fSCos = 1f, fSSin = 0f, fTx = x, fTy = 0f)
            x += widths[i]
        }

        fBlob = builder.make()
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        if (fBlob == null) onOnceBeforeDraw()

        c.scale(kScale, kScale)
        drawOne(c, 0f, 0f, SkMatrix.I(), SkMatrix.I())
        drawOne(c, kSZ * 1.1f, 0f, SkMatrix.MakeScale(2f, 2f), SkMatrix.I())
        drawOne(c, 0f, kSZ * 1.1f, SkMatrix.I(), SkMatrix.MakeRotate(45f))
        drawOne(c, kSZ * 1.1f, kSZ * 1.1f, SkMatrix.MakeScale(2f, 2f), SkMatrix.MakeRotate(45f))
    }

    /**
     * Mirrors upstream's `draw_one(canvas, pos, lm, outer_lm)` — under
     * an [SkAutoCanvasRestore] scope, translate to [posX], [posY], paint
     * the [kSZ] × [kSZ] background rect with the shader at 0.75 alpha,
     * then stamp the RSX text blob twice (at y = `kFontSZ` and
     * `kFontSZ*2`) at full alpha.
     */
    private fun drawOne(
        canvas: SkCanvas,
        posX: Float,
        posY: Float,
        lm: SkMatrix,
        outerLm: SkMatrix,
    ) {
        val blob = fBlob ?: return
        val save = canvas.save()
        try {
            canvas.translate(posX, posY)

            val p = SkPaint().apply {
                shader = makeShader(lm, outerLm)
                alphaf = 0.75f
            }
            canvas.drawRect(SkRect.MakeWH(kSZ, kSZ), p)

            p.alphaf = 1f
            canvas.drawTextBlob(blob, 0f, kFontSZ * 1f, p)
            canvas.drawTextBlob(blob, 0f, kFontSZ * 2f, p)
        } finally {
            canvas.restoreToCount(save)
        }
    }

    /**
     * Mirrors upstream's `make_shader(lm, outer_lm)`. Builds a 30 × 30
     * tile (yellow background with an inset green rect at 90% of the
     * tile size) on a raster surface, snapshots it, then wraps it
     * twice :
     *  - inner [org.skia.foundation.SkImage.makeShader] takes `(kRepeat,
     *    kRepeat, linear, lm)` ;
     *  - outer [org.skia.foundation.SkShader.makeWithLocalMatrix] folds
     *    [outerLm] into the same wrapper.
     */
    private fun makeShader(lm: SkMatrix, outerLm: SkMatrix): org.skia.foundation.SkShader {
        val tileW = kTileSize
        val tileH = kTileSize
        val surface = SkSurfaces.Raster(SkImageInfo.MakeN32Premul(tileW, tileH))!!
        val tileCanvas = surface.canvas

        // Yellow fill.
        val yellow = SkPaint().apply { color = 0xFFFFFF00.toInt() }
        tileCanvas.drawPaint(yellow)

        // Green inset rect at 90% of the tile size, anchored at the
        // top-left — leaves a 10%-wide yellow border on the right /
        // bottom that, when tiled with kRepeat, draws the visible grid.
        val green = SkPaint().apply { color = 0xFF008000.toInt() }
        tileCanvas.drawRect(
            SkRect.MakeLTRB(0f, 0f, tileW * 0.9f, tileH * 0.9f),
            green,
        )

        val image = surface.makeImageSnapshot()
        return image.makeShader(
            tileX = SkTileMode.kRepeat,
            tileY = SkTileMode.kRepeat,
            sampling = SkSamplingOptions(SkFilterMode.kLinear),
            localMatrix = lm,
        ).makeWithLocalMatrix(outerLm)
    }

    public companion object {
        /** Upstream's `static constexpr float kSZ = 300`. */
        public const val kSZ: Float = 300f

        /** Upstream's `static constexpr float kFontSZ = kSZ * 0.38`. */
        public const val kFontSZ: Float = kSZ * 0.38f

        /** Upstream's `static constexpr float kScale = 1.4f`. */
        public const val kScale: Float = 1.4f

        /** Upstream's `kTileSize = SkISize::Make(30, 30)`. */
        public const val kTileSize: Int = 30
    }
}
