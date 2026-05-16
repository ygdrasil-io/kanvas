package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlendMode_Name
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.tools.ToolUtils
import org.skia.utils.SkTextUtils

/**
 * Port of Skia's `gm/xfermodes.cpp::XfermodesGM` (1990 × 570).
 *
 * The canonical visual regression for the 29 [SkBlendMode] values
 * applied through 8 distinct *source-shape* drawing strategies. Each
 * cell is `64 × 64`, prefilled with a checkerboard background shader,
 * has its source contribution composed inside a [SkCanvas.saveLayer],
 * then the layer is composited onto the bg via the cell's blend mode
 * — the exact pattern unblocked by Phase 6r (`compositeFrom` honors
 * `paint.blendMode`) and Phase 6s (F16 blend pipeline for all 29
 * modes).
 *
 * **Source-type matrix** (`SrcType` enum upstream, kept here as Int
 * bit-flags) :
 *
 *  - [kRectangleImage] / [kRectangleImageWithAlpha] /
 *    [kSmallRectangleImageWithAlpha] : `drawImage(fDstB, ...)` over the
 *    layer with the mode-bearing paint.
 *  - [kRectangle] : `drawRect(...)` directly.
 *  - [kQuarterClear] : two coloured rectangles dividing the cell.
 *  - [kQuarterClearInLayer] : same but inside an additional layer
 *    that itself uses the mode (the inner draws revert to SrcOver).
 *  - [kSmallTransparentImage] : a transparent image scaled down.
 *  - [kRectangleWithMask] : a clipRect(top-strip) before drawing the
 *    rect (the inset slice tests how the mode interacts with a clip).
 *
 * Modes that aren't in the [kBasic] set are tested only for
 * [kRectangleImage] + [kRectangleImageWithAlpha] (most non-Porter-Duff
 * modes are visually meaningful only on rectangle-image input —
 * upstream's choice).
 *
 * **Background substitute** — upstream uses an `ARGB_4444` 2 × 2
 * checkerboard (`{0xFFFF, 0xCCCF, 0xCCCF, 0xFFFF}`) with
 * `kOpaque_AlphaType`, so the 4-bit alpha is forced to opaque. We
 * substitute an `8888` bitmap with the same RGB values
 * (`(0xFF, 0xFF, 0xFF)` and `(0xCC, 0xCC, 0xFF)`) — visually the
 * checkerboard tile differs only by the 4-bit-vs-8-bit precision of
 * the colour, which is invisible at the 6× shader scale used here.
 * Adding native ARGB_4444 support is a separate slice (no other GM in
 * scope needs it).
 */
public class XfermodesGM : GM() {

    public companion object {
        public const val W: Int = 64
        public const val H: Int = 64

        // Source-type bit-flags. Mirrors upstream `enum SrcType`.
        private const val kRectangleImage: Int               = 0x01
        private const val kRectangleImageWithAlpha: Int      = 0x02
        private const val kSmallRectangleImageWithAlpha: Int = 0x04
        private const val kRectangle: Int                    = 0x08
        private const val kQuarterClear: Int                 = 0x10
        private const val kQuarterClearInLayer: Int          = 0x20
        private const val kSmallTransparentImage: Int        = 0x40
        private const val kRectangleWithMask: Int            = 0x80

        private const val kAll: Int = 0xFF
        private const val kBasic: Int = 0x03

        private data class ModeRow(val mode: SkBlendMode, val mask: Int)

        // Mirrors upstream's `gModes[]` order — 29 modes, with the
        // source-type mask narrowing some of them to `kBasic` only.
        private val gModes: List<ModeRow> = listOf(
            ModeRow(SkBlendMode.kClear,      kAll),
            ModeRow(SkBlendMode.kSrc,        kAll),
            ModeRow(SkBlendMode.kDst,        kAll),
            ModeRow(SkBlendMode.kSrcOver,    kAll),
            ModeRow(SkBlendMode.kDstOver,    kAll),
            ModeRow(SkBlendMode.kSrcIn,      kAll),
            ModeRow(SkBlendMode.kDstIn,      kAll),
            ModeRow(SkBlendMode.kSrcOut,     kAll),
            ModeRow(SkBlendMode.kDstOut,     kAll),
            ModeRow(SkBlendMode.kSrcATop,    kAll),
            ModeRow(SkBlendMode.kDstATop,    kAll),
            ModeRow(SkBlendMode.kXor,        kBasic),
            ModeRow(SkBlendMode.kPlus,       kBasic),
            ModeRow(SkBlendMode.kModulate,   kAll),
            ModeRow(SkBlendMode.kScreen,     kBasic),
            ModeRow(SkBlendMode.kOverlay,    kBasic),
            ModeRow(SkBlendMode.kDarken,     kBasic),
            ModeRow(SkBlendMode.kLighten,    kBasic),
            ModeRow(SkBlendMode.kColorDodge, kBasic),
            ModeRow(SkBlendMode.kColorBurn,  kBasic),
            ModeRow(SkBlendMode.kHardLight,  kBasic),
            ModeRow(SkBlendMode.kSoftLight,  kBasic),
            ModeRow(SkBlendMode.kDifference, kBasic),
            ModeRow(SkBlendMode.kExclusion,  kBasic),
            ModeRow(SkBlendMode.kMultiply,   kAll),
            ModeRow(SkBlendMode.kHue,        kBasic),
            ModeRow(SkBlendMode.kSaturation, kBasic),
            ModeRow(SkBlendMode.kColor,      kBasic),
            ModeRow(SkBlendMode.kLuminosity, kBasic),
        )
    }

    private lateinit var fBG: SkBitmap
    private lateinit var fSrcB: SkBitmap
    private lateinit var fDstB: SkBitmap
    private lateinit var fTransparent: SkBitmap

    override fun getName(): String = "xfermodes"
    override fun getISize(): SkISize = SkISize.Make(1990, 570)

    override fun onOnceBeforeDraw() {
        // Background : 2 × 2 ARGB_4444 checkerboard (Phase C5 — native
        // ARGB_4444 storage). Upstream's tiles : `{0xFFFF, 0xCCCF,
        // 0xCCCF, 0xFFFF}` with bit layout `[R:15..12 G:11..8 B:7..4
        // A:3..0]` = `{opaque-white, opaque-grey-CC, opaque-grey-CC,
        // opaque-white}`.
        fBG = SkBitmap(2, 2, SkColorSpace.makeSRGB(), SkColorType.kARGB_4444).also {
            val white = SkColorSetARGB(0xFF, 0xFF, 0xFF, 0xFF)
            val cc = SkColorSetARGB(0xFF, 0xCC, 0xCC, 0xCC)
            it.setPixel(0, 0, white)
            it.setPixel(1, 0, cc)
            it.setPixel(0, 1, cc)
            it.setPixel(1, 1, white)
        }
        // fSrcB = 64 × 64 transparent + a `0xFFFFCC44` (warm-yellow)
        // 48 × 48 oval in the upper-left.
        fSrcB = SkBitmap(W, H).also { it.eraseColor(0) }
        run {
            val c = SkCanvas(fSrcB)
            val p = SkPaint(ToolUtils.colorTo565(SkColorSetARGB(0xFF, 0xFF, 0xCC, 0x44)))
                .apply { isAntiAlias = true }
            c.drawOval(SkRect.MakeWH(W * 3f / 4f, H * 3f / 4f), p)
        }
        // fDstB = 64 × 64 transparent + a `0xFF66AAFF` (light blue) rect
        // in the lower-right.
        fDstB = SkBitmap(W, H).also { it.eraseColor(0) }
        run {
            val c = SkCanvas(fDstB)
            val p = SkPaint(ToolUtils.colorTo565(SkColorSetARGB(0xFF, 0x66, 0xAA, 0xFF)))
                .apply { isAntiAlias = true }
            c.drawRect(
                SkRect.MakeLTRB(W / 3f, H / 3f, W * 19f / 20f, H * 19f / 20f),
                p,
            )
        }
        fTransparent = SkBitmap(W, H).also { it.eraseColor(0) }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(10f, 20f)

        val w = W.toFloat()
        val h = H.toFloat()
        val bgShaderMatrix = SkMatrix.Identity.preScale(6f, 6f)
        val bgShader = fBG.makeShader(
            SkTileMode.kRepeat,
            SkTileMode.kRepeat,
            SkSamplingOptions.Default,
            bgShaderMatrix,
        )

        val labelP = SkPaint().apply { isAntiAlias = true }
        val font = ToolUtils.DefaultPortableFont()
        val kWrap = 5

        var x0 = 0f
        var y0 = 0f
        var sourceType = 1
        while (sourceType and kAll != 0) {
            var x = x0; var y = y0
            for ((i, row) in gModes.withIndex()) {
                if ((row.mask and sourceType) == 0) continue
                val r = SkRect.MakeLTRB(x, y, x + w, y + h)

                // 1. Bg checkerboard fill.
                val bgPaint = SkPaint().apply {
                    style = SkPaint.Style.kFill_Style
                    shader = bgShader
                }
                c.drawRect(r, bgPaint)

                // 2. saveLayer + draw_mode + restore.
                c.saveLayer(r, null)
                drawMode(c, row.mode, sourceType, r.left, r.top)
                c.restore()

                // 3. Stroke frame, slightly inset.
                val frame = SkRect.MakeLTRB(
                    r.left - 0.5f, r.top - 0.5f,
                    r.right + 0.5f, r.bottom + 0.5f,
                )
                val framePaint = SkPaint().apply { style = SkPaint.Style.kStroke_Style }
                c.drawRect(frame, framePaint)

                // 4. Mode label centered above the cell.
                val label = SkBlendMode_Name(row.mode)
                SkTextUtils.DrawString(
                    c, label, x + w / 2f, y - font.size / 2f,
                    font, labelP, SkTextUtils.Align.kCenter_Align,
                )

                x += w + 10f
                if ((i % kWrap) == kWrap - 1) {
                    x = x0
                    y += h + 30f
                }
            }
            // After this source type's grid, advance y / wrap to next column.
            if (y < 320f) {
                if (x > x0) y += h + 30f
                y0 = y
            } else {
                x0 += 400f
                y0 = 0f
            }
            sourceType = sourceType shl 1
        }
    }

    /**
     * Per-source-type drawing strategy. Always starts by stamping
     * [fSrcB] under the `src` slot, then draws the mode-bearing object
     * whose shape depends on [srcType]. Mirrors upstream's
     * `XfermodesGM::draw_mode`.
     */
    private fun drawMode(canvas: SkCanvas, mode: SkBlendMode, srcType: Int, x: Float, y: Float) {
        val sampling = SkSamplingOptions.Default
        var p = SkPaint()
        var m = SkMatrix.Identity.preTranslate(x, y)

        canvas.drawImage(fSrcB.asImage(), x, y, sampling, p)
        p = SkPaint().apply { blendMode = mode }

        var restoreNeeded = false
        when (srcType) {
            kSmallTransparentImage -> {
                // Scale the transparent image down by half about (x, y).
                m = m.preScale(0.5f, 0.5f, x, y)
                canvas.save()
                canvas.concat(m)
                canvas.drawImage(fTransparent.asImage(), 0f, 0f, sampling, p)
                canvas.restore()
            }
            kQuarterClearInLayer -> {
                // Inner saveLayer with mode-bearing paint ; the two coloured
                // rects then draw with kSrcOver into this inner layer, and
                // the layer composites with `mode` onto the outer one.
                val bounds = SkRect.MakeXYWH(x, y, W.toFloat(), H.toFloat())
                canvas.saveLayer(bounds, p)
                restoreNeeded = true
                p = SkPaint().apply { blendMode = SkBlendMode.kSrcOver }
                drawQuarterClear(canvas, p, x, y)
            }
            kQuarterClear -> drawQuarterClear(canvas, p, x, y)
            kRectangleWithMask -> {
                // clipRect(top-strip) before drawRect.
                canvas.save()
                restoreNeeded = true
                val w = W.toFloat()
                val h = H.toFloat()
                canvas.clipRect(SkRect.MakeXYWH(x, y + h / 4f, w, h * 23f / 60f))
                drawColoredRect(canvas, p, x, y)
            }
            kRectangle -> drawColoredRect(canvas, p, x, y)
            kSmallRectangleImageWithAlpha -> {
                m = m.preScale(0.5f, 0.5f, x, y)
                p.alpha = 0x88
                drawDstImage(canvas, p, m, sampling)
            }
            kRectangleImageWithAlpha -> {
                p.alpha = 0x88
                drawDstImage(canvas, p, m, sampling)
            }
            kRectangleImage -> drawDstImage(canvas, p, m, sampling)
        }

        if (restoreNeeded) canvas.restore()
    }

    /**
     * Draw fDstB at (0, 0) under matrix `m` — used by the three
     * `*RectangleImage*_SrcType` variants.
     */
    private fun drawDstImage(canvas: SkCanvas, p: SkPaint, m: SkMatrix, sampling: SkSamplingOptions) {
        canvas.save()
        canvas.concat(m)
        canvas.drawImage(fDstB.asImage(), 0f, 0f, sampling, p)
        canvas.restore()
    }

    /**
     * Two coloured halves : right half blue, bottom half purple. Their
     * overlap (lower-right quadrant) ends up the second-drawn purple.
     */
    private fun drawQuarterClear(canvas: SkCanvas, p: SkPaint, x: Float, y: Float) {
        val halfW = W / 2f; val halfH = H / 2f
        p.color = ToolUtils.colorTo565(SkColorSetARGB(0xFF, 0x66, 0xAA, 0xFF))
        canvas.drawRect(SkRect.MakeXYWH(x + halfW, y, halfW, H.toFloat()), p)
        p.color = ToolUtils.colorTo565(SkColorSetARGB(0xFF, 0xAA, 0x66, 0xFF))
        canvas.drawRect(SkRect.MakeXYWH(x, y + halfH, W.toFloat(), halfH), p)
    }

    /**
     * Centre rect (37/60 × 37/60 of the cell) coloured light-blue —
     * used by [kRectangle] and [kRectangleWithMask].
     */
    private fun drawColoredRect(canvas: SkCanvas, p: SkPaint, x: Float, y: Float) {
        val w = W.toFloat(); val h = H.toFloat()
        p.color = ToolUtils.colorTo565(SkColorSetARGB(0xFF, 0x66, 0xAA, 0xFF))
        canvas.drawRect(
            SkRect.MakeXYWH(x + w / 3f, y + h / 3f, w * 37f / 60f, h * 37f / 60f),
            p,
        )
    }
}
