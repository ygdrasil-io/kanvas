package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkImage
import org.skia.foundation.SK_ColorBLUE
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorMAGENTA
import org.skia.foundation.SK_ColorRED
import org.skia.foundation.SK_ColorYELLOW
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect
import org.skia.utils.SkTiledImageUtils
import org.skia.core.SrcRectConstraint

/**
 * Port of Skia's `gm/verylargebitmap.cpp::VeryLargeBitmapGM`
 * (`verylargebitmap`, 500 × 600).
 *
 * Renders four oversized raster images (`small × small`, `big × small`,
 * `medium × medium`, `veryBig × small`) filled with a 2-stop radial
 * gradient via `SkBlendMode::kSrc`. Each image is displayed three
 * times :
 *  1. `drawImage(image, 0, 0)` — natural size, clipped to a 128 × 128
 *     destination rect.
 *  2. `drawImageRect(image, subset, dst)` — 128 × 64 subset around the
 *     image centre, drawn into a 128 × 128 dst.
 *  3. `drawImageRect(image, dst)` — entire image scaled into 128 × 128.
 *
 * Tests both the direct path (`fManuallyTile = false`) and the
 * [SkTiledImageUtils] path (`fManuallyTile = true`). This port wires
 * only the direct, raster-image variant matching the
 * `verylargebitmap` reference PNG. The other three upstream variants
 * (`verylargebitmap_manual`, `verylarge_picture_image[_manual]`) live
 * on `:kanvas-skia` follow-ups for `SkImages::DeferredFromPicture` +
 * the manual-tile dedicated reference.
 *
 * Upstream dims: 65 K (`veryBig`), 33 K (`big`), 5 K (`medium`),
 * 150 (`small`). 65 K × 150 raster pixels ≈ 40 MB — within the JVM
 * test heap; 5 K × 5 K is the heaviest at ≈ 105 MB so we keep the
 * draw paths sequential.
 */
public class VeryLargeBitmapGM(
    private val proc: ImageMakerProc,
    private val baseName: String,
    private val manuallyTile: Boolean,
) : GM() {

    public fun interface ImageMakerProc {
        public fun make(width: Int, height: Int, colors: IntArray): SkImage?
    }

    public constructor() : this(RasterImageMaker, "verylargebitmap", manuallyTile = false)

    override fun getName(): String = if (manuallyTile) "${baseName}_manual" else baseName
    override fun getISize(): SkISize = SkISize.Make(500, 600)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val veryBig = 65 * 1024 // 64K < size
        val big = 33 * 1024     // 32K < size < 64K
        val medium = 5 * 1024   // smaller than max texture, but big enough to tile.
        val small = 150

        val colors = IntArray(2)

        c.translate(10f, 10f)

        colors[0] = SK_ColorRED
        colors[1] = SK_ColorGREEN
        showImage(c, small, small, colors)
        c.translate(0f, 150f)

        colors[0] = SK_ColorBLUE
        colors[1] = SK_ColorMAGENTA
        showImage(c, big, small, colors)
        c.translate(0f, 150f)

        colors[0] = SK_ColorMAGENTA
        colors[1] = SK_ColorYELLOW
        showImage(c, medium, medium, colors)
        c.translate(0f, 150f)

        colors[0] = SK_ColorGREEN
        colors[1] = SK_ColorYELLOW
        showImage(c, veryBig, small, colors)
    }

    private fun showImage(canvas: SkCanvas, width: Int, height: Int, colors: IntArray) {
        val image = proc.make(width, height, colors) ?: return

        val borderPaint = SkPaint().apply { style = SkPaint.Style.kStroke_Style }

        var dstRect = SkRect.MakeWH(128f, 128f)

        canvas.save()
        canvas.clipRect(dstRect)
        if (manuallyTile) {
            SkTiledImageUtils.DrawImage(canvas, image, 0f, 0f)
        } else {
            canvas.drawImage(image, 0f, 0f)
        }
        canvas.restore()
        canvas.drawRect(dstRect, borderPaint)

        dstRect = SkRect.MakeXYWH(dstRect.left + 150f, dstRect.top, dstRect.width(), dstRect.height())
        val hw = width / 2
        val hh = height / 2
        val subset = SkRect.MakeLTRB((hw - 64).toFloat(), (hh - 32).toFloat(), (hw + 64).toFloat(), (hh + 32).toFloat())
        if (manuallyTile) {
            SkTiledImageUtils.DrawImageRect(
                canvas, image, subset, dstRect,
                SkSamplingOptions.Default, null, SrcRectConstraint.kStrict,
            )
        } else {
            canvas.drawImageRect(
                image, subset, dstRect,
                SkSamplingOptions.Default, null, SrcRectConstraint.kStrict,
            )
        }
        canvas.drawRect(dstRect, borderPaint)

        dstRect = SkRect.MakeXYWH(dstRect.left + 150f, dstRect.top, dstRect.width(), dstRect.height())
        if (manuallyTile) {
            SkTiledImageUtils.DrawImageRect(canvas, image, dstRect)
        } else {
            canvas.drawImageRect(
                image,
                SkRect.MakeIWH(image.width, image.height),
                dstRect,
            )
        }
        canvas.drawRect(dstRect, borderPaint)
    }

    private companion object {
        val RasterImageMaker: ImageMakerProc = ImageMakerProc { w, h, colors -> makeRasterImage(w, h, colors) }

        private fun draw(canvas: SkCanvas, width: Int, height: Int, colors: IntArray) {
            val center = SkPoint(width / 2f, height / 2f)
            val radius = 40f
            val paint = SkPaint().apply {
                shader = SkRadialGradient.Make(
                    center = center,
                    radius = radius,
                    colors = colors,
                    positions = null,
                    tileMode = SkTileMode.kMirror,
                )
                blendMode = SkBlendMode.kSrc
            }
            canvas.drawPaint(paint)
        }

        private fun makeRasterImage(width: Int, height: Int, colors: IntArray): SkImage? {
            val info = SkImageInfo.MakeN32Premul(width, height)
            val surface = SkSurface.MakeRaster(info)
            draw(surface.canvas, width, height, colors)
            return surface.makeImageSnapshot()
        }
    }
}
