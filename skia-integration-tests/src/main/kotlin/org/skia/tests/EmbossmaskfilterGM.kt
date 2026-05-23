package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlendMode_Name
import org.skia.foundation.SkEmbossMaskFilter
import org.skia.foundation.SkMaskFilter
import org.skia.foundation.SkPaint
import org.skia.tools.ToolUtils
import org.skia.utils.SkTextUtils

/**
 * Port of Skia's `gm/emboss.cpp::embossmaskfilter` (640 × 960).
 *
 * Exercises [SkEmbossMaskFilter] across two sections :
 *
 * **Top half (4 cells, 2 × 2 grid)** — four different [SkEmbossMaskFilter.Light]
 * configurations applied to overlapping red circle + blue rect pairs, each
 * labelled with the light parameters.
 *
 * **Bottom half (18 cells, 6 × 3 grid)** — a shared emboss filter paired with
 * every [SkBlendMode] in the `androidblendmodes` set, showing how each
 * composite-mode interacts with the 3D emboss rendering.
 */
public class EmbossmaskfilterGM : GM() {

    override fun getName(): String = "embossmaskfilter"
    override fun getISize(): SkISize = SkISize.Make(640, 960)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val totalWidth = 640f
        val topHeight = 480f
        val bottomHeight = 480f

        // ── Top half : vary Light parameters ────────────────────────────────
        val gridTopCols = 2
        val gridTopRows = 2
        val cellTopW = totalWidth / gridTopCols
        val cellTopH = topHeight / gridTopRows

        val blurSigmaTop = 2.5f

        val embossRecs = listOf(
            SkEmbossMaskFilter.Light(floatArrayOf(-1f, -1f, 1f), ambient = 80, specular = 16)
                to "Light: TL, Amb: 80, Spec: 16",
            SkEmbossMaskFilter.Light(floatArrayOf(1f, 1f, 0.8f), ambient = 180, specular = 16)
                to "Light: BR, Amb: 180, Spec: 16",
            SkEmbossMaskFilter.Light(floatArrayOf(0f, -1f, 1f), ambient = 30, specular = 128)
                to "Light: Top, Amb: 30, Spec: 128",
            SkEmbossMaskFilter.Light(floatArrayOf(-1f, 0f, 0.5f), ambient = 80, specular = 64)
                to "Light: Left, Amb: 80, Spec: 64",
        )

        var exampleIndex = 0
        for (r in 0 until gridTopRows) {
            for (col in 0 until gridTopCols) {
                if (exampleIndex < embossRecs.size) {
                    val (light, label) = embossRecs[exampleIndex]
                    val cellBounds = SkRect.MakeXYWH(
                        col * cellTopW, r * cellTopH, cellTopW, cellTopH,
                    )
                    drawEmbossExample(c, cellBounds.makeInset(10f, 10f), light, blurSigmaTop, label)
                    exampleIndex++
                }
            }
        }

        // ── Bottom half : vary BlendMode ─────────────────────────────────────
        val gridBotCols = 6
        val gridBotRows = 3
        val cellBotW = totalWidth / gridBotCols
        val cellBotH = bottomHeight / gridBotRows

        val sharedLight = SkEmbossMaskFilter.Light(
            floatArrayOf(-0.707f, -0.707f, 0.707f), ambient = 60, specular = 48,
        )
        val sharedMF: SkMaskFilter = SkEmbossMaskFilter.Make(2.0f, sharedLight) ?: return

        val blendModesToTest = listOf(
            SkBlendMode.kSrc,
            SkBlendMode.kDst,
            SkBlendMode.kSrcOver,
            SkBlendMode.kDstOver,
            SkBlendMode.kSrcIn,
            SkBlendMode.kDstIn,
            SkBlendMode.kSrcOut,
            SkBlendMode.kDstOut,
            SkBlendMode.kSrcATop,
            SkBlendMode.kDstATop,
            SkBlendMode.kXor,
            SkBlendMode.kPlus,
            SkBlendMode.kModulate,
            SkBlendMode.kScreen,
            SkBlendMode.kOverlay,
            SkBlendMode.kMultiply,
            SkBlendMode.kDarken,
            SkBlendMode.kLighten,
        )

        var bIndex = 0
        for (r in 0 until gridBotRows) {
            for (col in 0 until gridBotCols) {
                if (bIndex < blendModesToTest.size) {
                    val mode = blendModesToTest[bIndex]
                    val cellBounds = SkRect.MakeXYWH(
                        col * cellBotW, topHeight + r * cellBotH, cellBotW, cellBotH,
                    )
                    drawEmbossBlendExample(
                        c, cellBounds.makeInset(10f, 10f), sharedMF, mode, SkBlendMode_Name(mode),
                    )
                    bIndex++
                }
            }
        }
    }

    // ── Helpers (mirror C++ file-static helpers) ─────────────────────────────

    private fun drawEmbossExample(
        canvas: SkCanvas,
        bounds: SkRect,
        light: SkEmbossMaskFilter.Light,
        blurSigma: Float,
        label: String,
    ) {
        canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(bounds.left, bounds.top)

        val embossFilter: SkMaskFilter = SkEmbossMaskFilter.Make(blurSigma, light) ?: run {
            canvas.restore(); return
        }

        val radius = bounds.width() * 0.25f
        val offset = radius * 0.3f
        val cx = bounds.width() * 0.5f
        val cy = bounds.height() * 0.5f

        val paint1 = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorRED
            maskFilter = embossFilter
        }
        val paint2 = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorBLUE
            maskFilter = embossFilter
        }

        canvas.drawCircle(cx - offset, cy, radius, paint1)
        canvas.drawRect(
            SkRect.MakeXYWH(cx + offset - radius, cy - radius, radius * 2f, radius * 2f),
            paint2,
        )

        val textPaint = SkPaint().apply { color = SK_ColorBLACK }
        SkTextUtils.DrawString(canvas, label, 5f, 15f, ToolUtils.DefaultPortableFont(), textPaint)

        canvas.restore()
    }

    private fun drawEmbossBlendExample(
        canvas: SkCanvas,
        bounds: SkRect,
        embossFilter: SkMaskFilter,
        blendMode: SkBlendMode,
        label: String,
    ) {
        canvas.save()
        canvas.clipRect(bounds)
        canvas.translate(bounds.left, bounds.top)

        val radius = bounds.width() * 0.25f
        val offset = radius * 0.3f
        val cx = bounds.width() * 0.5f
        val cy = bounds.height() * 0.5f

        val paint1 = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorRED
        }
        val paint2 = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorBLUE
            maskFilter = embossFilter
            this.blendMode = blendMode
        }

        canvas.drawCircle(cx - offset, cy, radius, paint1)
        canvas.drawRect(
            SkRect.MakeXYWH(cx + offset - radius, cy - radius, radius * 2f, radius * 2f),
            paint2,
        )

        val textPaint = SkPaint().apply { color = SK_ColorRED }
        SkTextUtils.DrawString(canvas, label, 5f, 15f, ToolUtils.DefaultPortableFont(), textPaint)

        canvas.restore()
    }
}
