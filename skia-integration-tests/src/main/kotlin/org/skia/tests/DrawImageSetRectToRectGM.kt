package org.skia.tests

import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.graphiks.math.SkVector
import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.core.withSave
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/drawimageset.cpp::DrawImageSetRectToRectGM`](https://github.com/google/skia/blob/main/gm/drawimageset.cpp#L211-L294)
 * (`draw_image_set_rect_to_rect`, 1250 × 850).
 *
 * Exercises the rect-stays-rect family of CTMs through
 * [SkCanvas.experimental_DrawEdgeAAImageSet] so that filtering and AA are
 * not erroneously disabled : identity, 90° rotation, non-uniform scale,
 * mirror in x+y, and a mirror-y / rotate / scale composite.
 *
 * Each CTM is rendered with two sub-pixel offsets (0 and 0.5) and then
 * with two scale-overrides (`(2, 0.5)` and `(0.5, 2)`) applied per-entry
 * to the [SkCanvas.ImageSetEntry.dstRect] family — the latter case also
 * sets `alpha = 0.4f` on every third entry to verify the per-entry alpha
 * multiplier is honoured.
 *
 * Body fully ported against the raster
 * [SkCanvas.experimental_DrawEdgeAAImageSet] fallback.
 */
public class DrawImageSetRectToRectGM : GM() {

    private var fSet: Array<SkCanvas.ImageSetEntry>? = null

    override fun getName(): String = "draw_image_set_rect_to_rect"
    override fun getISize(): SkISize = SkISize.Make(1250, 850)

    override fun onOnceBeforeDraw() {
        val kColors = arrayOf(
            SkColor4f.kBlue, SkColor4f.kWhite,
            SkColor4f.kRed, SkColor4f.kWhite,
        )
        fSet = makeImageTiles(kTileW, kTileH, kM, kN, kColors)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val set = fSet ?: return

        ToolUtils.draw_checkerboard(c, SK_ColorBLACK, SK_ColorWHITE, 50)

        val kW = (kM * kTileW).toFloat()
        val kH = (kN * kTileH).toFloat()

        // 5 rect-stays-rect matrices.
        val matrices = arrayOf(
            // Identity.
            SkMatrix.I(),
            // 90° rotation around the centre.
            SkMatrix.MakeRotate(90f, kW / 2f, kH / 2f),
            // Scale 2x in x, 0.5x in y.
            SkMatrix.MakeScale(2f, 0.5f),
            // Mirror in x+y, then translate back into the positive quadrant.
            SkMatrix.MakeScale(-1f, -1f).postTranslate(kW, kH),
            // Mirror in y → translate y back → rotate 90° around centre →
            // postScale (2, 0.5) — full composite.
            SkMatrix.MakeScale(1f, -1f)
                .postTranslate(0f, kH)
                .postRotate(90f, kW / 2f, kH / 2f)
                .postScale(2f, 0.5f),
        )

        val paint = SkPaint().apply { blendMode = SkBlendMode.kSrcOver }
        val kTranslate = maxOf(kW, kH) * 2f + 10f

        c.translate(5f, 5f)

        // Block 1 : 5 matrices × 2 sub-pixel offsets ((0, 0) and (0.5, 0.5)).
        c.withSave {
            for (frac in floatArrayOf(0f, 0.5f)) {
                this.withSave {
                    translate(frac, frac)
                    for (m in matrices.indices) {
                        this.withSave {
                            concat(matrices[m])
                            experimental_DrawEdgeAAImageSet(
                                set, kM * kN, null, null,
                                SkSamplingOptions(SkFilterMode.kLinear), paint,
                                SrcRectConstraint.kFast,
                            )
                        }
                        translate(kTranslate, 0f)
                    }
                }
                // Upstream emits `canvas->restore()` then opens a fresh
                // save before translate-down. We collapse that by relying
                // on the outer withSave to pop the inner state after each
                // row.
                translate(0f, kTranslate)
            }
        }

        // Block 2 : 5 matrices × 2 per-entry scale variants. Upstream
        // *continues* from the same translation cursor block 1 left off
        // at — we mirror that by leaving the cursor where the previous
        // `withSave { … }` saved/restored. Re-apply the two `translate
        // (0, kTranslate)` from block 1 to advance to the third row.
        c.translate(0f, 2f * kTranslate)

        for (scale in arrayOf(SkVector(2f, 0.5f), SkVector(0.5f, 2f))) {
            // Scale the dstRect of each entry by `scale`; every third
            // entry (`i % 3 == 0`) gets alpha = 0.4f.
            val scaledSet = Array(kM * kN) { i ->
                val original = set[i]
                val sd = SkRect.MakeLTRB(
                    original.dstRect.left * scale.fX,
                    original.dstRect.top * scale.fY,
                    original.dstRect.right * scale.fX,
                    original.dstRect.bottom * scale.fY,
                )
                original.copy(
                    dstRect = sd,
                    alpha = if (i % 3 == 0) 0.4f else 1f,
                )
            }
            c.withSave {
                for (m in matrices.indices) {
                    this.withSave {
                        concat(matrices[m])
                        experimental_DrawEdgeAAImageSet(
                            scaledSet, kM * kN, null, null,
                            SkSamplingOptions(SkFilterMode.kLinear), paint,
                            SrcRectConstraint.kFast,
                        )
                    }
                    translate(kTranslate, 0f)
                }
            }
            c.translate(0f, kTranslate)
        }
    }

    private companion object {
        const val kM = 2
        const val kN = 2
        const val kTileW = 40
        const val kTileH = 50
    }
}
