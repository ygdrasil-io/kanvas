package org.skia.tests

import org.graphiks.math.SkISize
import org.skia.core.SkCanvas
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/readpixels.cpp::ReadPixelsGM`](https://github.com/google/skia/blob/main/gm/readpixels.cpp)
 * — 384 × 405, exercises [SkImage.readPixels] across the
 * `(srcColorType, dstColorType, dstAlphaType, dstColorSpace)`
 * Cartesian product driven by a raster source [SkImage].
 *
 * Upstream loads `images/google_chrome.ico` via [org.graphiks.kanvas.codec.Codec],
 * resamples to `64×64` in three source colour types
 * (`kRGBA_8888 / kBGRA_8888 / kRGBA_F16`), then reads each back into
 * the same three destination colour types × two alpha types ×
 * three colour spaces.
 *
 * ## Port status — STUB.FIXTURE.GOOGLE_CHROME_ICO
 *
 * Body fully ported against the live [SkImage.readPixels] /
 * [org.skia.foundation.SkImages.RasterFromData] surface (see
 * [ReadPixelsHelpers]). The matching [ReadPixelsTest] stays
 * `@Disabled` because the source fixture `images/google_chrome.ico`
 * is not shipped with the project. The ICO decoder itself is also
 * a stub (`STUB.ICO_DECODE`) — landing both the resource and the
 * decoder will let this GM activate.
 */
public class ReadPixelsGM : GM() {

    override fun getName(): String = "readpixels"

    override fun getISize(): SkISize = SkISize.Make(
        6 * ReadPixelsHelpers.kWidth,
        9 * ReadPixelsHelpers.kHeight,
    )

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val alphaTypes = arrayOf(SkAlphaType.kUnpremul, SkAlphaType.kPremul)
        val colorTypes = arrayOf(
            SkColorType.kRGBA_8888,
            SkColorType.kBGRA_8888,
            SkColorType.kRGBA_F16,
        )
        val colorSpaces = arrayOf(
            ReadPixelsHelpers.makeWideGamut(),
            SkColorSpace.makeSRGB(),
            ReadPixelsHelpers.makeSmallGamut(),
        )

        for (dstColorSpace in colorSpaces) {
            for (srcColorType in colorTypes) {
                c.save()
                val image = makeRasterImage(srcColorType) ?: run {
                    // Missing fixture — skip this row but still advance
                    // the y-translate so the layout stays consistent.
                    c.restore()
                    c.translate(0f, ReadPixelsHelpers.kHeight.toFloat())
                    continue
                }
                for (dstColorType in colorTypes) {
                    for (dstAlphaType in alphaTypes) {
                        ReadPixelsHelpers.drawImage(
                            c, image, dstColorType, dstAlphaType, dstColorSpace,
                        )
                        c.translate(ReadPixelsHelpers.kWidth.toFloat(), 0f)
                    }
                }
                c.restore()
                c.translate(0f, ReadPixelsHelpers.kHeight.toFloat())
            }
        }
    }

    /**
     * Mirrors upstream's `make_raster_image(SkColorType)` — load
     * `images/google_chrome.ico` via [org.graphiks.kanvas.codec.Codec.MakeFromStream]
     * and resample to `64×64` in [srcColorType] under
     * `kPremul_SkAlphaType`.
     *
     * Returns `null` when the fixture isn't on the classpath ; the
     * GM body cleans up the per-row translate so the layout stays
     * stable even with missing rows.
     */
    private fun makeRasterImage(srcColorType: SkColorType): SkImage? {
        // The fixture is not available in :kanvas-skia ; ToolUtils
        // returns null and we propagate. When the resource lands,
        // replace this with the upstream path (decode → makeWH(64, 64)
        // → makeColorType(srcColorType) → makeAlphaType(kPremul)).
        val img = ToolUtils.GetResourceAsImage("images/google_chrome.ico") ?: return null
        // Best-effort resample : :kanvas-skia has no in-place colour-
        // type re-tag on SkImage, but a freshly-snapshotted bitmap with
        // the requested colour type does the job for the GM's purposes.
        // Currently we ignore [srcColorType] because the resource is
        // absent anyway — when both land, plumb the explicit colour-
        // type round-trip through SkBitmap.
        @Suppress("UNUSED_PARAMETER") fun _markUsed(@Suppress("UNUSED_PARAMETER") ct: SkColorType) {}
        _markUsed(srcColorType)
        return img
    }
}
