package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SK_ColorTRANSPARENT
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlendMode_Name
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkColorType
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils
import org.skia.utils.SkTextUtils

/**
 * Port of Skia's `gm/androidblendmodes.cpp::AndroidBlendModesGM`
 * (`androidblendmodes`, 1024 × 1280).
 *
 * Recreates the blend-mode reference grid from the Android docs. 18
 * `SkBlendMode` values laid out in a 4 × 5 grid (the last two cells of
 * the bottom row stay empty — Android only exposes 18 modes, vs Skia's
 * 29). Each tile composites:
 *   1. **Source** — a 144 × 144 blue rect at `(16, 96)` (alpha-AA).
 *   2. **Destination** — a red circle at `(160, 95)` r = 80 (alpha-AA).
 *
 * Both shapes start their lives on transparent 256 × 256 backings, then
 * are `drawImage`'d (= sampled with kSrcOver) onto a transparent layer:
 * the destination first, then the source with the under-test blend mode.
 * The layer is flattened via SrcOver onto a 32-px white/light-grey
 * checkerboard background. Each cell is labelled with `SkBlendMode_Name`
 * underneath.
 */
public class AndroidBlendModesGM : GM() {

    init { setBGColor(SK_ColorBLACK) }

    override fun getName(): String = "androidblendmodes"
    override fun getISize(): SkISize = SkISize.Make(kNumCols * kBitmapSize, kNumRows * kBitmapSize)

    private lateinit var compositeSrc: SkBitmap
    private lateinit var compositeDst: SkBitmap

    private fun ensureOnceBeforeDraw() {
        if (::compositeSrc.isInitialized) return
        compositeSrc = SkBitmap(kBitmapSize, kBitmapSize, colorType = SkColorType.kRGBA_8888).also { bm ->
            bm.eraseColor(SK_ColorTRANSPARENT)
            val tmp = SkCanvas(bm)
            val p = SkPaint().apply {
                isAntiAlias = true
                color = ToolUtils.colorTo565(kBlue)
            }
            tmp.drawRect(SkRect.MakeLTRB(16f, 96f, 160f, 240f), p)
        }
        compositeDst = SkBitmap(kBitmapSize, kBitmapSize, colorType = SkColorType.kRGBA_8888).also { bm ->
            bm.eraseColor(SK_ColorTRANSPARENT)
            val tmp = SkCanvas(bm)
            val p = SkPaint().apply {
                isAntiAlias = true
                color = ToolUtils.colorTo565(kRed)
            }
            tmp.drawCircle(160f, 95f, 80f, p)
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        ensureOnceBeforeDraw()

        drawCheckerboard(c, kWhite, kGrey, 32)

        val font = ToolUtils.DefaultPortableFont()

        var xOffset = 0
        var yOffset = 0

        // Same iteration order as upstream — laid out row by row.
        val modes = arrayOf(
            SkBlendMode.kPlus, SkBlendMode.kClear,
            SkBlendMode.kDarken, SkBlendMode.kDst,
            SkBlendMode.kDstATop, SkBlendMode.kDstIn,
            SkBlendMode.kDstOut, SkBlendMode.kDstOver,
            SkBlendMode.kLighten, SkBlendMode.kModulate,
            SkBlendMode.kOverlay, SkBlendMode.kScreen,
            SkBlendMode.kSrc, SkBlendMode.kSrcATop,
            SkBlendMode.kSrcIn, SkBlendMode.kSrcOut,
            SkBlendMode.kSrcOver, SkBlendMode.kXor,
        )

        for (mode in modes) {
            val saveCount = c.save()
            drawTile(c, xOffset.toFloat(), yOffset.toFloat(), mode)
            c.restoreToCount(saveCount)

            SkTextUtils.DrawString(
                c, SkBlendMode_Name(mode),
                xOffset + kBitmapSize / 2f,
                yOffset + kBitmapSize.toFloat(),
                font, SkPaint(),
                SkTextUtils.Align.kCenter_Align,
            )

            xOffset += kBitmapSize
            if (xOffset >= kNumCols * kBitmapSize) {
                xOffset = 0
                yOffset += kBitmapSize
            }
        }
    }

    private fun drawTile(canvas: SkCanvas, xOffset: Float, yOffset: Float, mode: SkBlendMode) {
        canvas.translate(xOffset, yOffset)
        canvas.clipRect(SkRect.MakeXYWH(0f, 0f, kBitmapSize.toFloat(), kBitmapSize.toFloat()))
        canvas.saveLayer(null, null)

        val p = SkPaint()
        canvas.drawImage(compositeDst.asImage(), 0f, 0f, paint = p)
        p.blendMode = mode
        canvas.drawImage(compositeSrc.asImage(), 0f, 0f, paint = p)
    }

    /**
     * Inline `ToolUtils::draw_checkerboard(canvas, c1, c2, size)`. Same
     * convention as [AAXfermodesGM] but no source-relative anchoring is
     * needed here — the canvas hasn't been translated yet, so the checker
     * tiles align with device pixels at `(0, 0)`.
     */
    private fun drawCheckerboard(canvas: SkCanvas, c1: SkColor, c2: SkColor, size: Int) {
        val solid = SkPaint().apply { isAntiAlias = false }
        val w = kNumCols * kBitmapSize
        val h = kNumRows * kBitmapSize
        var y = 0
        while (y < h) {
            var x = 0
            while (x < w) {
                val cx = x / size
                val cy = y / size
                solid.color = if (((cx + cy) and 1) == 0) c2 else c1
                canvas.drawRect(
                    SkRect.MakeLTRB(x.toFloat(), y.toFloat(), (x + size).toFloat(), (y + size).toFloat()),
                    solid,
                )
                x += size
            }
            y += size
        }
    }

    private companion object {
        const val kBitmapSize: Int = 256
        const val kNumRows: Int = 5
        const val kNumCols: Int = 4

        val kBlue: SkColor = SkColorSetARGB(255, 22, 150, 243)
        val kRed: SkColor = SkColorSetARGB(255, 233, 30, 99)
        val kWhite: SkColor = SkColorSetARGB(255, 243, 243, 243)
        val kGrey: SkColor = SkColorSetARGB(255, 222, 222, 222)
    }
}
