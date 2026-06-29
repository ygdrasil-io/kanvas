package org.skia.tests

import org.graphiks.kanvas.codec.SkImageGeneratorImages
import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.core.SrcRectConstraint
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageGenerator
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkSurfaces
import org.skia.tools.ToolUtils
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Port of Skia's
 * [`gm/imagemasksubset.cpp`](https://github.com/google/skia/blob/main/gm/imagemasksubset.cpp)
 * (`DEF_SIMPLE_GM(imagemasksubset, canvas, 480, 480)`).
 *
 * Checks whether subset [SkImage]s preserve the original color type (A8 in
 * this case). Three rows are drawn — one per image-backing type:
 *  1. **SkImage_Raster** — a plain raster surface.
 *  2. **SkImage_Ganesh** — GPU texture (raster fallback in kanvas-skia).
 *  3. **SkImage_Lazy** — deferred via [SkImageGenerator].
 *
 * For each row two cells are drawn:
 *  - Left  : the full 100×100 image, subsetted at draw time via
 *    [SkCanvas.drawImageRect] with `src = kSubset` (25,25)–(75,75).
 *  - Right : a materialised [SkImage.makeSubset] of the same rect, drawn
 *    full-bounds.
 *
 * The paint carries `color = 0xFF00FF00` (green). In Skia, drawing an
 * `kAlpha_8` image with a coloured paint uses the image as an alpha mask:
 * the paint's RGB fills covered pixels, modulated by the image's alpha.
 */
public class ImageMaskSubsetGM : GM() {

    override fun getName(): String = "imagemasksubset"
    override fun getISize(): SkISize = SkISize.Make(480, 480)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val paint = SkPaint().apply {
            color = SkColorSetARGB(0xFF, 0x00, 0xFF, 0x00) // 0xFF00FF00 green
        }

        val info = SkImageInfo.MakeA8(kSize.width, kSize.height)

        for (maker in makers) {
            val image = ToolUtils.MakeTextureImage(c, maker(c, info))
            if (image != null) {
                // Left cell: drawImageRect using src=kSubset, dst=kDest.
                c.drawImageRect(
                    image,
                    SkRect.Make(kSubset),
                    kDest,
                    SkSamplingOptions.Default,
                    paint,
                    SrcRectConstraint.kStrict,
                )
                // Right cell: materialised subset drawn full-bounds.
                // C++: drawImageRect(subset, kDest.makeOffset(…), sampling, paint)
                // — no src rect → full subset bounds are implied.
                val subset = image.makeSubset(kSubset)
                if (subset != null) {
                    val dstRight = kDest.makeOffset(kSize.width * 1.5f, 0f)
                    c.drawImageRect(
                        subset,
                        SkRect.MakeWH(subset.width.toFloat(), subset.height.toFloat()),
                        dstRight,
                        SkSamplingOptions.Default,
                        paint,
                        SrcRectConstraint.kFast,
                    )
                }
            }
            c.translate(0f, kSize.height * 1.5f)
        }
    }

    // ── helpers ────────────────────────────────────────────────────────────

    /**
     * Draws a 2-colour checkerboard on [surface]'s canvas, then returns
     * a snapshot image. Mirrors C++'s `make_mask`.
     */
    private fun makeMask(surface: SkSurface): SkImage {
        ToolUtils.draw_checkerboard(
            surface.canvas,
            SkColorSetARGB(0x80, 0x80, 0x80, 0x80), // 0x80808080
            SkColorSetARGB(0x00, 0x00, 0x00, 0x00), // 0x00000000
            5,
        )
        return surface.makeImageSnapshot()
    }

    /**
     * Generator that produces a checkerboard mask on demand.
     * Mirrors C++'s `MaskGenerator : public SkImageGenerator`.
     */
    private inner class MaskGenerator(info: SkImageInfo) : SkImageGenerator(info) {

        override fun onGetPixels(
            info: SkImageInfo,
            pixels: ByteBuffer,
            rowBytes: Int,
        ): Boolean {
            // Upstream switches to null color-space for A8 targets
            // (`surfaceInfo = surfaceInfo.makeColorSpace(nullptr)`) to
            // avoid an implicit sRGB encode on alpha-only data.
            // In kanvas-skia SkColorSpace is never null; we keep the
            // caller's color-space which is effectively a no-op for A8.
            val surfaceInfo = if (info.colorType == SkColorType.kAlpha_8) {
                // Mirror: makeColorSpace(nullptr) → stay A8, sRGB (neutral).
                SkImageInfo.MakeA8(info.width, info.height)
            } else {
                info
            }

            // Build a temporary surface over the caller's buffer.
            val surface = SkSurfaces.WrapPixels(surfaceInfo, pixels, rowBytes) ?: return false
            ToolUtils.draw_checkerboard(
                surface.canvas,
                SkColorSetARGB(0x80, 0x80, 0x80, 0x80),
                SkColorSetARGB(0x00, 0x00, 0x00, 0x00),
                5,
            )
            return true
        }
    }

    // Three image-making strategies (raster / GPU-fallback / lazy).
    private val makers: Array<(SkCanvas, SkImageInfo) -> SkImage?> = arrayOf(
        // SkImage_Raster: allocate a CPU surface.
        { _, info ->
            SkSurfaces.Raster(info)?.let { makeMask(it) }
        },
        // SkImage_Ganesh: GPU when available, raster otherwise.
        // kanvas-skia is raster-only so this reduces to SkSurfaces.Raster.
        { _, info ->
            SkSurfaces.Raster(info)?.let { makeMask(it) }
        },
        // SkImage_Lazy: deferred via SkImageGenerator.
        { _, info ->
            SkImageGeneratorImages.DeferredFromGenerator(MaskGenerator(info))
        },
    )

    private companion object {
        val kSize   = SkISize.Make(100, 100)
        val kSubset = SkIRect.MakeLTRB(25, 25, 75, 75)
        val kDest   = SkRect.MakeXYWH(10f, 10f, 100f, 100f)
    }
}
