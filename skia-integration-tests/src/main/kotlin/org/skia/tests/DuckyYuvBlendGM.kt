package org.skia.tests

import org.graphiks.math.SK_ColorDKGRAY
import org.graphiks.math.SK_ColorLTGRAY
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/ducky_yuv_blend.cpp::ducky_yuv_blend`
 * (`DEF_SIMPLE_GM_CAN_FAIL`, 560 × 1130).
 *
 * Reproduces the layout test `css3/blending/background-blend-mode-image-image.html`
 * to exercise `skbug.com/40040948`. Two foreground images (one plain JPEG,
 * one GPU-backed YUV image in the upstream Ganesh path) are composited over
 * the same PNG background through every non-coefficient, non-last
 * [SkBlendMode] (kOverlay … kColor, 13 modes), laid out in a 4-column grid
 * of 130 × 130 tiles separated by 10-pixel gaps.
 *
 * ## Adaptation vs upstream
 *
 * Upstream's second foreground pass creates a Ganesh / Mipmapped YUV image
 * via `LazyYUVImage::Make(GetResourceAsData("images/ducky.jpg"),
 * skgpu::Mipmapped::kYes)` — a GPU-test utility not available in the
 * `:kanvas-skia` raster backend.
 *
 * On the raster path upstream falls back to `duckyFG[1] = duckyFG[0]`
 * (the same JPEG used for the first pass), which is exactly what this port
 * does.
 *
 * ## Bucket : PARTIAL (raster first-pass works; GPU YUV second pass is stubbed)
 *
 * TODO: STUB.LAZY_YUV_IMAGE — LazyYUVImage::Make (GPU-backed mipmapped YUV
 *       image upload from JPEG data) not implemented in the kanvas-skia raster backend.
 */
public class DuckyYuvBlendGM : GM() {

    override fun getName(): String = "ducky_yuv_blend"
    override fun getISize(): SkISize = SkISize.Make(560, 1130)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val duckyBG: SkImage = ToolUtils.GetResourceAsImage("images/ducky.png") ?: return
        val duckyJpeg: SkImage = ToolUtils.GetResourceAsImage("images/ducky.jpg") ?: return

        // Upstream's GPU path creates a YUV-backed mipmapped image for the
        // second foreground via LazyYUVImage::Make — not available in raster.
        // TODO: STUB.LAZY_YUV_IMAGE — LazyYUVImage::Make not implemented.
        // On CPU upstream falls back to: duckyFG[1] = duckyFG[0].
        val duckyFG: Array<SkImage> = arrayOf(duckyJpeg, duckyJpeg)

        val kNumPerRow = 4
        val kPad = 10
        val kDstRect = SkRect.MakeWH(130f, 130f)
        val bgSrcRect = SkRect.MakeIWH(duckyBG.width, duckyBG.height)
        val fgSrcRect = SkRect.MakeIWH(duckyJpeg.width, duckyJpeg.height)

        // SkSamplingOptions(SkFilterMode::kLinear, SkMipmapMode::kNearest)
        val sampling = SkSamplingOptions(SkFilterMode.kLinear, SkMipmapMode.kNearest)

        // Non-coefficient, non-last blend modes:
        // kLastCoeffMode = kScreen (ordinal 14), kLastMode = kLuminosity (ordinal 28).
        // Loop: bm in (kLastCoeffMode+1) until kLastMode → kOverlay(15) .. kColor(27).
        val separableAndHslModes: List<SkBlendMode> = SkBlendMode.entries
            .filter { bm ->
                bm.ordinal > SkBlendMode.kLastCoeffMode.ordinal &&
                    bm.ordinal < SkBlendMode.kLastMode.ordinal
            }

        c.translate(kPad.toFloat(), kPad.toFloat())
        c.save()

        // Draw a checkerboard backdrop across the entire canvas.
        ToolUtils.draw_checkerboard(
            c,
            SK_ColorDKGRAY,
            SK_ColorLTGRAY,
            ((kDstRect.height() + kPad) / 5).toInt(),
        )

        var rowCnt = 0

        fun newRow() {
            c.restore()
            c.translate(0f, kDstRect.height() + kPad)
            c.save()
            rowCnt = 0
        }

        for (fg in duckyFG) {
            for (bm in separableAndHslModes) {
                // Background.
                c.drawImageRect(duckyBG, bgSrcRect, kDstRect, sampling, null)
                // Foreground with blend mode.
                val paint = SkPaint().apply { blendMode = bm }
                c.drawImageRect(fg, fgSrcRect, kDstRect, sampling, paint)
                c.translate(kDstRect.width() + kPad, 0f)
                if (++rowCnt == kNumPerRow) {
                    newRow()
                }
            }
            // Force a new row between the two foreground passes.
            newRow()
        }

        c.restore()
    }
}
