package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorWHITE
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/srcmode.cpp::SrcModeGM` (640 × 760, BG black).
 *
 * Sweeps five draw procs (hairline, thick line, rect, oval, "Hamburge"
 * text) × three blend modes ([SkBlendMode.kSrcOver],
 * [SkBlendMode.kSrc], [SkBlendMode.kClear]) × two paint shaders (solid
 * `0x80F60000` translucent red, then a green→blue linear gradient) ×
 * two anti-alias settings (alias / AA) — 60 cells total laid out in
 * a 12-column × 5-row block, with the AA half stacked under the alias
 * half.
 *
 * Upstream renders into an offscreen `SkSurface` (white background)
 * and composites it back to the destination canvas. We mirror that:
 * draw into [SkSurface.MakeRaster] then `draw(canvas, 0, 0)` onto
 * the destination — needed so [SkBlendMode.kClear] writes
 * **transparent** pixels into the offscreen instead of into the GM's
 * black background (the visual difference upstream's GM trace
 * highlights).
 *
 * **`:kanvas-skia` rasterizer caveat** — only [SkBlendMode.kSrcOver]
 * is implemented; [SkBlendMode.kSrc] and [SkBlendMode.kClear] fall
 * back to [SkBlendMode.kSrcOver] at composite time (see
 * `SkBlendMode.kt` KDoc). The cells under those two columns will
 * therefore not visually distinguish themselves from `kSrcOver`
 * upstream — that's a known similarity ceiling, not a port defect.
 */
public class SrcModeGM : GM() {

    init {
        setBGColor(SK_ColorBLACK)
    }

    override fun getName(): String = "srcmode"
    override fun getISize(): SkISize = SkISize.Make(640, 760)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val size = getISize()
        val surf = compatSurface(size)
        surf.canvas.drawColor(SK_ColorWHITE)
        drawContent(surf.canvas)
        surf.draw(c, 0f, 0f)
    }

    private fun compatSurface(size: SkISize): SkSurface =
        SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(size.width, size.height))

    private fun drawContent(canvas: SkCanvas) {
        canvas.translate(20f, 20f)

        val paint = SkPaint().apply { color = 0x80F60000.toInt() }
        val font = SkFont(ToolUtils.DefaultPortableTypeface(), H / 4f)

        val procs = arrayOf(
            ::drawHair, ::drawThick, ::drawRect, ::drawOval, ::drawText,
        )
        val modes = arrayOf(
            SkBlendMode.kSrcOver, SkBlendMode.kSrc, SkBlendMode.kClear,
        )
        val paintProcs = arrayOf<(SkPaint) -> Unit>(::identityPaint, ::gradientPaint)

        for (aa in 0..1) {
            paint.isAntiAlias = aa != 0
            font.edging = if (aa != 0) SkFont.Edging.kAntiAlias else SkFont.Edging.kAlias
            canvas.save()
            for (paintProc in paintProcs) {
                paintProc(paint)
                for (mode in modes) {
                    paint.blendMode = mode
                    canvas.save()
                    for (proc in procs) {
                        proc(canvas, paint, font)
                        canvas.translate(0f, H * 5f / 4f)
                    }
                    canvas.restore()
                    canvas.translate(W * 5f / 4f, 0f)
                }
            }
            canvas.restore()
            canvas.translate(0f, (H * 5f / 4f) * procs.size.toFloat())
        }
    }

    private fun identityPaint(paint: SkPaint) {
        paint.shader = null
    }

    private fun gradientPaint(paint: SkPaint) {
        paint.shader = SkLinearGradient.Make(
            SkPoint(0f, 0f),
            SkPoint(W, H),
            intArrayOf(0xFF00FF00.toInt(), 0xFF0000FF.toInt()),
            null,
            SkTileMode.kClamp,
        )
    }

    private fun drawHair(canvas: SkCanvas, paint: SkPaint, @Suppress("UNUSED_PARAMETER") font: SkFont) {
        val p = paint.copy().apply { strokeWidth = 0f }
        canvas.drawLine(0f, 0f, W, H, p)
    }

    private fun drawThick(canvas: SkCanvas, paint: SkPaint, @Suppress("UNUSED_PARAMETER") font: SkFont) {
        val p = paint.copy().apply { strokeWidth = H / 5f }
        canvas.drawLine(0f, 0f, W, H, p)
    }

    private fun drawRect(canvas: SkCanvas, paint: SkPaint, @Suppress("UNUSED_PARAMETER") font: SkFont) {
        canvas.drawRect(SkRect.MakeWH(W, H), paint)
    }

    private fun drawOval(canvas: SkCanvas, paint: SkPaint, @Suppress("UNUSED_PARAMETER") font: SkFont) {
        canvas.drawOval(SkRect.MakeWH(W, H), paint)
    }

    private fun drawText(canvas: SkCanvas, paint: SkPaint, font: SkFont) {
        canvas.drawString("Hamburge", 0f, H * 2f / 3f, font, paint)
    }

    public companion object {
        private const val W: Float = 80f
        private const val H: Float = 60f
    }
}
