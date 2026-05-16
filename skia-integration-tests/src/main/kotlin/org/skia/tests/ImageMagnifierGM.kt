package org.skia.tests

import org.skia.core.SaveLayerRec
import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.math.SK_ColorBLACK
import org.skia.math.SK_ColorBLUE
import org.skia.math.SK_ColorRED
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/imagemagnifier.cpp::DEF_SIMPLE_GM_BG(imagemagnifier,
 * canvas, 500, 500, SK_ColorBLACK)`.
 *
 * Draws 25 strings of `"The quick brown fox jumped over the lazy dog."`
 * at deterministic random positions / colors / font-sizes inside a
 * `saveLayer` whose paint carries an [SkImageFilters.Magnifier]
 * (`lensBounds = (0,0,500,500), zoom = 2, inset = 100`). The lens
 * magnifies the centre of the canvas 2× with a 100-px feather to the
 * unscaled edges.
 *
 * Bit-compatible random text seeding via [SkRandom] + [colorToRGB565]
 * matches `imageblur`-family GMs.
 */
public class ImageMagnifierGM : GM() {

    init { setBGColor(SK_ColorBLACK) }
    override fun getName(): String = "imagemagnifier"
    override fun getISize(): SkISize = SkISize.Make(WIDTH, HEIGHT)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            imageFilter = SkImageFilters.Magnifier(
                SkRect.MakeWH(WIDTH.toFloat(), HEIGHT.toFloat()),
                zoomAmount = 2f, inset = 100f,
                sampling = SkSamplingOptions(SkFilterMode.kLinear),
            )
        }
        c.saveLayer(null, paint)
        try {
            drawContent(c, 300f, 25)
        } finally {
            c.restore()
        }
    }

    private companion object {
        private const val WIDTH: Int = 500
        private const val HEIGHT: Int = 500
    }
}

/**
 * Port of Skia's `gm/imagemagnifier.cpp::DEF_SIMPLE_GM_BG(
 * imagemagnifier_cropped, canvas, 256, 256, SK_ColorBLACK)`.
 *
 * A blue-grid image is wrapped as an [SkImageFilter] source, then run
 * through an [SkImageFilters.Magnifier] cropped to the centre
 * `(16, 16, 240, 240)` rect. `saveLayer(null, paint)` + immediate
 * `restore()` triggers the filter's evaluation onto the canvas.
 *
 * **Adaptation** — upstream's `Magnifier(..., input, &cropRect)` is the
 * 6-arg overload ; ours doesn't expose `cropRect`, so the magnifier
 * output is wrapped in [SkImageFilters.Crop] at the same rect.
 */
public class ImageMagnifierCroppedGM : GM() {

    init { setBGColor(SK_ColorBLACK) }
    override fun getName(): String = "imagemagnifier_cropped"
    override fun getISize(): SkISize = SkISize.Make(WH, WH)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val source = makeBlueGridImage()
        val imageSource: SkImageFilter =
            SkImageFilters.Image(source, SkSamplingOptions(SkFilterMode.kNearest))

        val cropRect = SkIRect.MakeXYWH(16, 16, WH - 32, WH - 32)
        val magnifier = SkImageFilters.Magnifier(
            lensBounds = SkRect.MakeWH(WH.toFloat(), WH.toFloat()),
            zoomAmount = WH.toFloat() / (WH - 96f),
            inset = 64f,
            sampling = SkSamplingOptions.Default,
            input = imageSource,
        )
        val cropped = SkImageFilters.Crop(SkRect.Make(cropRect), magnifier)

        val paint = SkPaint().apply { imageFilter = cropped }
        c.saveLayer(null, paint)
        c.restore()
    }

    private fun makeBlueGridImage(): SkImage {
        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(WH, WH))
        val canvas = surface.canvas
        canvas.clear(0x00000000)
        val paint = SkPaint().apply { color = SK_ColorBLUE }
        var pos = 0f
        while (pos < WH) {
            canvas.drawLine(0f, pos, WH.toFloat(), pos, paint)
            canvas.drawLine(pos, 0f, pos, WH.toFloat(), paint)
            pos += 16f
        }
        return surface.makeImageSnapshot()
    }

    private companion object {
        private const val WH: Int = 256
    }
}

/**
 * Port of Skia's `gm/imagemagnifier.cpp::ImageMagnifierBounds`
 * (`DEF_GM(return new ImageMagnifierBounds();)`, name
 * `"imagemagnifier_bounds"`, 768 × 512).
 *
 * Demonstrates the magnifier filter's bounds handling : draws two rows
 * each containing three columns (backdrop-filtered, regular-filtered,
 * un-filtered) over a randomly-positioned text content.
 *
 * Top row : `inset = 16` (fish-eye distortion). Bottom row : `inset = 0`
 * (pure zoom, no distortion). Each row overlays its
 * `widgetBounds = (16, 24, 220, 248)` as a black border ; the clipped
 * inset bounds in red (only for inset > 0) ; and a blue source-rect in
 * the un-filtered column.
 *
 * **Static-snapshot semantics** — upstream's `onAnimate` sets `fX, fY`
 * to a `SineWave(nanos)` time-varying offset, but the static reference
 * dump is taken at `nanos = 0` with `fX = fY = 0` (matches the GM's
 * field-initializer state). We don't override `onAnimate` so the same
 * initial state is preserved.
 *
 * **Adaptation** — upstream uses the `Magnifier(..., outBounds)` 6-arg
 * overload to clip the magnifier's input to `kOutBounds = (0, 0, 256,
 * 256)`. We wrap the magnifier output with [SkImageFilters.Crop] at
 * `kOutBounds` to mirror the semantic. Backdrop-filter saveLayer uses
 * [SaveLayerRec] with `backdrop = magnifier`.
 */
public class ImageMagnifierBoundsGM : GM() {

    override fun getName(): String = "imagemagnifier_bounds"
    override fun getISize(): SkISize = SkISize.Make(768, 512)

    private val fX: Float = 0f
    private val fY: Float = 0f

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawRow(c, 16f) // fish-eye distortion
        c.translate(0f, 256f)
        drawRow(c, 0f) // no distortion, just zoom
    }

    private fun drawBorder(canvas: SkCanvas, rect: SkRect, color: Int, width: Float, borderInset: Float = 0f) {
        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = width
            this.color = color
            isAntiAlias = true
        }
        val r = SkRect(
            rect.left + borderInset, rect.top + borderInset,
            rect.right - borderInset, rect.bottom - borderInset,
        )
        val rr = SkRRect.MakeRectXY(r, borderInset, borderInset)
        canvas.drawRRect(rr, paint)
    }

    private fun drawRow(canvas: SkCanvas, inset: Float) {
        val widgetBounds = SkRect(16f, 24f, 220f, 248f)
        widgetBounds.offset(fX, fY)
        val kZoomAmount = 2.5f
        val kOutBounds = SkRect(0f, 0f, 256f, 256f)

        // Clipped widget bounds (intersected with kOutBounds).
        val clippedWidget = SkRect(
            maxOf(widgetBounds.left, kOutBounds.left),
            maxOf(widgetBounds.top, kOutBounds.top),
            minOf(widgetBounds.right, kOutBounds.right),
            minOf(widgetBounds.bottom, kOutBounds.bottom),
        )
        val cx = (clippedWidget.left + clippedWidget.right) / 2f
        val cy = (clippedWidget.top + clippedWidget.bottom) / 2f
        val zcx = cx.coerceIn(clippedWidget.left, clippedWidget.right)
        val zcy = cy.coerceIn(clippedWidget.top, clippedWidget.bottom)
        val zoomCx = zcx * (1f - 1f / kZoomAmount)
        val zoomCy = zcy * (1f - 1f / kZoomAmount)
        val srcRect = SkRect(
            clippedWidget.left / kZoomAmount + zoomCx,
            clippedWidget.top / kZoomAmount + zoomCy,
            clippedWidget.right / kZoomAmount + zoomCx,
            clippedWidget.bottom / kZoomAmount + zoomCy,
        )

        val magnifier: SkImageFilter = SkImageFilters.Crop(
            kOutBounds,
            SkImageFilters.Magnifier(
                lensBounds = widgetBounds,
                zoomAmount = kZoomAmount,
                inset = inset,
                sampling = SkSamplingOptions(SkFilterMode.kLinear),
                input = null,
            ),
        )

        // Backdrop-filter column.
        canvas.save()
        canvas.clipRect(kOutBounds)
        drawContent(canvas, 32f, 350)
        canvas.saveLayer(SaveLayerRec(bounds = null, paint = null, backdrop = magnifier))
        canvas.restore()
        drawBorder(canvas, widgetBounds, SK_ColorBLACK, 2f)
        if (inset > 0f) {
            drawBorder(canvas, clippedWidget, SK_ColorRED, 2f, inset)
        }
        canvas.restore()

        // Regular-filter column.
        canvas.save()
        canvas.translate(256f, 0f)
        canvas.clipRect(kOutBounds)
        val paint = SkPaint().apply { imageFilter = magnifier }
        canvas.saveLayer(null, paint)
        drawContent(canvas, 32f, 350)
        canvas.restore()
        drawBorder(canvas, widgetBounds, SK_ColorBLACK, 2f)
        if (inset > 0f) {
            drawBorder(canvas, clippedWidget, SK_ColorRED, 2f, inset)
        }
        canvas.restore()

        // Un-filtered column.
        canvas.save()
        canvas.translate(512f, 0f)
        canvas.clipRect(kOutBounds)
        drawContent(canvas, 32f, 350)
        drawBorder(canvas, widgetBounds, SK_ColorBLACK, 2f)
        drawBorder(canvas, srcRect, SK_ColorBLUE, 2f, inset / kZoomAmount)
        canvas.restore()
    }
}

// ── Shared random-text helper (mirrors upstream `draw_content`). ───────
internal fun drawContent(canvas: SkCanvas, maxTextSize: Float, count: Int) {
    val str = "The quick brown fox jumped over the lazy dog."
    val rand = SkRandom()
    val font: SkFont = ToolUtils.DefaultPortableFont()
    repeat(count) {
        // imagemagnifier uses WIDTH=HEIGHT=500 ; the bounds variant
        // uses 768×512. Upstream's `draw_content` hard-codes 500 as
        // the random range — we keep that since the references were
        // captured with those exact `rand.nextULessThan(500)` calls.
        val x = rand.nextULessThan(500)
        val y = rand.nextULessThan(500)
        val paint = SkPaint().apply {
            color = ToolUtils.colorTo565(rand.nextBits(24) or (0xFF shl 24))
        }
        font.size = rand.nextRangeScalar(0f, maxTextSize)
        canvas.drawString(str, x.toFloat(), y.toFloat(), font, paint)
    }
}

