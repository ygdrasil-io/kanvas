package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorTRANSPARENT
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkBlendMode.Companion.kSkBlendModeCount
import org.skia.foundation.SkBlendMode_Name
import org.skia.math.SkColor4f
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/xfermodes3.cpp::Xfermodes3GM`
 * (`xfermodes3`, 630 × 1215).
 *
 * Stress-tests device-covering rect draws with blend modes — split into
 * 5 cells per `(stroke, mode)` pair (3 solid colours then 2 bitmap-
 * shader alpha-attenuated variants), iterated over the 29 blend modes,
 * doubled across `{Fill, Stroke}` stroke styles. Each cell is
 * `kSize × kSize` (`kSize = 30`) over a checker bg, with the mode name
 * printed below.
 *
 * Upstream uses `canvas->makeSurface(...)` to grab a tightly-sized
 * temporary surface for each cell — that triggers a GPU code path that
 * would otherwise be collapsed to a clear. The raster pipeline doesn't
 * benefit from that optimisation, so this port skips the temp surface
 * for the cell itself but **does** use a 1-time raster surface for the
 * bitmap shader (matching upstream's `fBmpShader` construction).
 *
 * **bg colour** — `ToolUtils.colorTo565(0xFF70D0E0)` matches upstream's
 * `setBGColor` call ; the resulting colour ends up encoded as the 565
 * approximation of a teal-cyan.
 */
public class Xfermodes3GM : GM() {

    init {
        setBGColor(ToolUtils.colorTo565(0xFF70D0E0.toInt()))
    }

    override fun getName(): String = "xfermodes3"
    override fun getISize(): SkISize = SkISize.Make(630, 1215)

    private lateinit var bgShader: SkShader
    private lateinit var bmpShader: SkShader

    override fun onOnceBeforeDraw() {
        // Bg checker — same pattern as xfermodes2 but with `kCheckSize = 8`
        // local-matrix scale.
        val bg = SkBitmap(2, 2)
        val dark = SkColorSetARGB(0xFF, 0x42, 0x41, 0x42)
        val light = SkColorSetARGB(0xFF, 0xD6, 0xD3, 0xD6)
        bg.setPixel(0, 0, dark)
        bg.setPixel(1, 0, light)
        bg.setPixel(0, 1, light)
        bg.setPixel(1, 1, dark)
        bgShader = bg.makeShader(
            SkTileMode.kRepeat, SkTileMode.kRepeat, SkSamplingOptions.Default,
            SkMatrix.MakeScale(kCheckSize.toFloat(), kCheckSize.toFloat()),
        )

        // Bitmap shader source : a `kSize × kSize` image with a radial-
        // gradient-filled rect inside (1/8..7/8 of the image extent).
        val bmpPaint = SkPaint().apply { isAntiAlias = false }
        val center = SkPoint(kSize.toFloat() / 2f, kSize.toFloat() / 2f)
        val colors = intArrayOf(
            SkColor4f.kTransparent.toSkColor(),
            SkColor4f.FromColor(0x80800000.toInt()).toSkColor(),
            SkColor4f.FromColor(0xF020F060.toInt()).toSkColor(),
            SkColor4f.kWhite.toSkColor(),
        )
        bmpPaint.shader = SkRadialGradient.Make(
            center, 3f * kSize / 4f,
            colors, null, SkTileMode.kRepeat,
        )

        val bmp = SkBitmap(kSize, kSize)
        bmp.eraseColor(SK_ColorTRANSPARENT)
        val bmpCanvas = SkCanvas(bmp)
        bmpCanvas.drawRect(
            SkRect.MakeLTRB(
                kSize.toFloat() / 8f, kSize.toFloat() / 8f,
                7f * kSize / 8f, 7f * kSize / 8f,
            ),
            bmpPaint,
        )
        bmpShader = bmp.makeShader(
            SkTileMode.kClamp, SkTileMode.kClamp, SkSamplingOptions.Default,
        )
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.translate(10f, 20f)

        val font = ToolUtils.DefaultPortableFont()
        val labelP = SkPaint()

        val solidColors = intArrayOf(SK_ColorTRANSPARENT, SK_ColorBLUE, 0x80808000.toInt())
        val bmpAlphas = intArrayOf(0xFF, 0x80)

        val strokes = arrayOf(
            Stroke(SkPaint.Style.kFill_Style, 0f),
            Stroke(SkPaint.Style.kStroke_Style, kSize.toFloat() / 2f),
        )

        var test = 0
        var x = 0
        var y = 0
        for (s in strokes.indices) {
            for (m in 0 until kSkBlendModeCount) {
                val mode = SkBlendMode.entries[m]
                c.drawString(
                    SkBlendMode_Name(mode),
                    x.toFloat(),
                    (y + kSize + 3).toFloat() + font.size,
                    font, labelP,
                )
                for (cIdx in solidColors.indices) {
                    val modePaint = SkPaint().apply {
                        blendMode = mode
                        color = solidColors[cIdx]
                        style = strokes[s].style
                        strokeWidth = strokes[s].width
                    }
                    drawMode(c, x, y, kSize, kSize, modePaint)
                    test++
                    x += kSize + 10
                    if (test % kTestsPerRow == 0) {
                        x = 0
                        y += kSize + 30
                    }
                }
                for (a in bmpAlphas.indices) {
                    val modePaint = SkPaint().apply {
                        blendMode = mode
                        alpha = bmpAlphas[a]
                        shader = bmpShader
                        style = strokes[s].style
                        strokeWidth = strokes[s].width
                    }
                    drawMode(c, x, y, kSize, kSize, modePaint)
                    test++
                    x += kSize + 10
                    if (test % kTestsPerRow == 0) {
                        x = 0
                        y += kSize + 30
                    }
                }
            }
        }
    }

    /**
     * Per-cell draw. Mirrors upstream's `drawMode` — uses a tight raster
     * surface as the offscreen target so the cell's full-device draw
     * doesn't get collapsed into a clear (GPU pathology). On the raster
     * pipeline the optimisation doesn't apply, so for performance we
     * still draw to a temp surface to match upstream's bg-shader
     * isolation (the bg checker is drawn into the surface, not the
     * outer canvas, then snapshotted back).
     */
    private fun drawMode(
        canvas: SkCanvas,
        x: Int, y: Int, w: Int, h: Int,
        modePaint: SkPaint,
    ) {
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())

        val r = SkRect.MakeWH(w.toFloat(), h.toFloat())

        // Substitute `canvas->makeSurface(...)` with a raster surface
        // matching kanvas-skia's default colorspace / colortype.
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(w, h))
        val modeCanvas = surface.canvas

        val bgPaint = SkPaint().apply {
            isAntiAlias = false
            shader = bgShader
        }
        modeCanvas.drawRect(r, bgPaint)
        modeCanvas.drawRect(r, modePaint)

        // Composite the surface back onto the outer canvas.
        canvas.drawImage(surface.makeImageSnapshot(), 0f, 0f, SkSamplingOptions.Default, null)

        // Stroked frame, slightly outset.
        val frame = SkRect.MakeLTRB(
            r.left - 0.5f, r.top - 0.5f,
            r.right + 0.5f, r.bottom + 0.5f,
        )
        val borderPaint = SkPaint().apply { style = SkPaint.Style.kStroke_Style }
        canvas.drawRect(frame, borderPaint)

        canvas.restore()
    }

    private data class Stroke(val style: SkPaint.Style, val width: Float)

    private companion object {
        const val kCheckSize: Int = 8
        const val kSize: Int = 30
        const val kTestsPerRow: Int = 15
    }
}
