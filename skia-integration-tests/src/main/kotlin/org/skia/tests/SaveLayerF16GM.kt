package org.skia.tests

import org.skia.core.SaveLayerRec
import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/savelayer.cpp::DEF_SIMPLE_GM(savelayer_f16, canvas, 900, 300)`.
 *
 * Demonstrates the precision benefit of requesting an F16 saveLayer via
 * `SkCanvas::kF16ColorType` flag. The GM:
 *  1. Draws a sweep-gradient oval on the root canvas as a baseline.
 *  2. Opens two saveLayer passes (one default, one with `kF16ColorType`), each
 *     drawing the same oval 15 times with `alpha=1/15` + `kPlus` blend — the
 *     expected result is a full-intensity composite. An 8-bit layer accumulates
 *     rounding errors and produces a subtly washed-out ring; an F16 layer is
 *     precise enough to reconstruct the exact source colours.
 *
 * **F16 flag not implemented** — `SkCanvas::kF16ColorType` (bitmask `1 << 4`)
 * is accepted in [org.skia.core.SaveLayerRec.flags] but **silently ignored**
 * by `:kanvas-skia`'s raster backend. The layer is always allocated as an
 * 8-bit ARGB bitmap, so both saveLayer passes produce identical (8-bit) output
 * and the rendered image deviates from the reference.
 *
 * TODO: missing API — `SkCanvas::kF16ColorType` save-layer flag
 * (`include/core/SkCanvas.h:687`). Implementing requires allocating the
 * offscreen layer as `SkColorType.kRGBA_F16Norm` when the flag is set, then
 * converting back to 8-bit on `restore()`.
 */
public class SaveLayerF16GM : GM() {

    override fun getName(): String = "savelayer_f16"
    override fun getISize(): SkISize = SkISize.Make(900, 300)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val n = 15
        val r = SkRect.MakeWH(300f, 300f)

        val colors = intArrayOf(0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(), 0xFFFF0000.toInt())
        val sweepPaint = SkPaint().apply {
            shader = SkSweepGradient.Make(
                SkPoint.Make(r.centerX(), r.centerY()),
                colors,
                null,
                SkTileMode.kClamp,
            )
        }
        c.drawOval(r, sweepPaint)

        val layerPaint = SkPaint().apply {
            alphaf = 1.0f / n
            blendMode = SkBlendMode.kPlus
        }

        for (flags in intArrayOf(0, F16_COLOR_TYPE_FLAG)) {
            c.translate(r.width(), 0f)

            // TODO("STUB.F16_COLOR_TYPE: kF16ColorType flag (1 << 4) is accepted but
            // ignored — layer is always 8-bit. Both loop iterations produce identical
            // 8-bit output; the second (F16) pass should show a more accurate composite.")
            val rec = SaveLayerRec(flags = flags)
            c.saveLayer(rec)
            repeat(n) {
                c.drawOval(r, layerPaint)
            }
            c.restore()
        }
    }

    private companion object {
        /** Mirrors `SkCanvas::kF16ColorType = 1 << 4` (`include/core/SkCanvas.h:687`). */
        const val F16_COLOR_TYPE_FLAG: Int = 1 shl 4
    }
}
