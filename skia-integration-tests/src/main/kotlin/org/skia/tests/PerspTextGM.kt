package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/persptext.cpp::PerspTextGM` (1024 × 768).
 *
 * Draws the string "Hamburgefons" repeatedly under perspective
 * matrices that vary the `persp0` / `persp1` coefficient in three
 * columns (X-perspective, Y-perspective, XY-perspective). 8 rows of
 * gradually-increasing perspective factor per column. The
 * `fMinimal` boolean tunes the X-row factor 32× weaker, registered
 * as `persptext_minimal`.
 *
 * Each transform is applied around the text origin via a
 * pre / post-translate pair so the perspective pivots on the
 * baseline anchor rather than the canvas origin.
 *
 * Known fidelity caveats :
 *  - Subpixel rasterisation is downgraded to integer-snap on the
 *    portable OpenType glyph path, so glyph edges may drift
 *    1 px from the upstream reference at strong perspective.
 */
public open class PerspTextGM public constructor(private val fMinimal: Boolean = false) : GM() {

    init { setBGColor(0xFFFFFFFF.toInt()) }

    override fun getName(): String = if (fMinimal) "persptext_minimal" else "persptext"
    override fun getISize(): SkISize = SkISize.Make(1024, 768)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        c.clear(SK_ColorWHITE)

        val paint = SkPaint().apply { isAntiAlias = true }

        val font = SkFont(ToolUtils.CreatePortableTypeface("serif", SkFontStyle())).apply {
            isSubpixel = true
            size = 32f
            isBaselineSnap = false
        }

        val text = "Hamburgefons"
        val textLen = text.length

        val textWidth = font.measureText(text, textLen, SkTextEncoding.kUTF8, null)
        val metrics = SkFontMetrics()
        val textHeight = font.getMetrics(metrics)

        var x = 10f
        var y = textHeight + 5f
        val kSteps = 8
        val kMinimalFactor = if (fMinimal) 32f else 1f
        for (pm in PerspMode.entries) {
            for (i in 0 until kSteps) {
                c.withSave {
                    var persp = SkMatrix.I()
                    persp = when (pm) {
                        PerspMode.kX -> if (fMinimal) {
                            persp.copy(persp0 = i * 0.0005f / kSteps / kMinimalFactor)
                        } else {
                            persp.copy(persp0 = i * 0.00025f / kSteps)
                        }
                        PerspMode.kY -> persp.copy(persp1 = i * 0.0025f / kSteps / kMinimalFactor)
                        PerspMode.kXY -> persp.copy(
                            persp0 = i * -0.00025f / kSteps / kMinimalFactor,
                            persp1 = i * -0.00125f / kSteps / kMinimalFactor,
                        )
                    }
                    // persp = T(x,y) · persp · T(-x,-y) — mirrors upstream's
                    // `Concat(Translate(x,y), persp, Translate(-x,-y))`.
                    persp = SkMatrix.MakeTrans(x, y).preConcat(persp).preConcat(SkMatrix.MakeTrans(-x, -y))
                    concat(persp)

                    paint.color = SK_ColorBLACK
                    drawSimpleText(text, textLen, SkTextEncoding.kUTF8, x, y, font, paint)
                }
                y += textHeight + 5f
            }
            x += textWidth + 10f
            y = textHeight + 5f
        }
    }

    private enum class PerspMode { kX, kY, kXY }

    public companion object {
        public fun minimal(): PerspTextGM = PerspTextGM(fMinimal = true)
    }
}
