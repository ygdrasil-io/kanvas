package org.skia.tests

import org.graphiks.math.SK_ColorDKGRAY
import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize
import org.skia.core.SkCanvas
import org.skia.core.SrcRectConstraint
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorType
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/drawimageset.cpp::DrawImageSetAlphaOnlyGM`](https://github.com/google/skia/blob/main/gm/drawimageset.cpp#L297-L359)
 * (`draw_image_set_alpha_only`, `kM*kTileW × 2*kN*kTileH` =
 * 200 × 400 at the default `kM=4, kN=4, kTile=50`).
 *
 * Probes that alpha-only and colour textures both compose correctly with
 * the paint's solid colour on the batched
 * [SkCanvas.experimental_DrawEdgeAAImageSet] API :
 *  - Every `(y, x)` entry has its alpha scaled to `(kM - x) / kM`
 *    (so column 0 stays opaque, column kM-1 collapses to alpha 0).
 *  - Every even-row entry has its image re-tagged to
 *    [SkColorType.kAlpha_8] — the alpha-only path should pick up the
 *    paint's RGB colour `(0.2, 0.8, 0.4, 1)` instead of the tile's own
 *    colour. Odd rows keep the original RGBA image and the paint colour
 *    has no effect (alpha multiplier only).
 *  - The top half draws through the experimental edge-set API ; the
 *    bottom half draws each entry as a regular [SkCanvas.drawImageRect]
 *    so the two paths can be eyeballed side-by-side.
 *
 * Upstream calls `fImage->makeColorTypeAndColorSpace(kAlpha_8, sRGB)`
 * — our [SkImage] surface doesn't expose `makeColorTypeAndColorSpace`,
 * but the [SkImage] constructor accepts a [SkColorType] argument that the
 * batched-image-set device implementation should honour as a "treat as
 * alpha-only" hint. The port re-builds each even-row tile with
 * [SkColorType.kAlpha_8] re-tagged, mirroring upstream's intent without
 * requiring the extra factory method.
 *
 * ## Port status
 *
 * Body fully ported against the new [SkCanvas.experimental_DrawEdgeAAImageSet]
 * surface (resolves to `TODO("STUB.EDGE_AA_IMAGE_SET")` at runtime).
 * Matching [DrawImageSetAlphaOnlyTest] is `@Disabled` until that body
 * lands.
 */
public class DrawImageSetAlphaOnlyGM : GM() {

    private var fSet: Array<SkCanvas.ImageSetEntry>? = null

    override fun getName(): String = "draw_image_set_alpha_only"
    override fun getISize(): SkISize = SkISize.Make(kM * kTileW, 2 * kN * kTileH)

    override fun onOnceBeforeDraw() {
        val kColors = arrayOf(
            SkColor4f.kBlue, SkColor4f.kTransparent,
            SkColor4f.kRed, SkColor4f.kTransparent,
        )
        val kBGColor = SkColorSetARGB(128, 128, 128, 128)
        var set = makeImageTiles(kTileW, kTileH, kM, kN, kColors, kBGColor)

        // Modify alpha by column, convert every even row to Alpha-8.
        set = Array(set.size) { i ->
            val y = i / kM
            val x = i % kM
            val entry = set[i]
            val newAlpha = (kM - x) / kM.toFloat()
            val newImage = if (y % 2 == 0) {
                // Upstream :
                //   fImage = fImage->makeColorTypeAndColorSpace(
                //       kAlpha_8_SkColorType, sRGB);
                // Our SkImage stores 8888 pixels uniformly so we tag the
                // existing snapshot as Alpha-8 (the alpha plane is the
                // only signal a downstream sampler should read from it).
                val src = entry.image
                SkImage(
                    width = src.width,
                    height = src.height,
                    pixels = src.pixels,
                    colorType = SkColorType.kAlpha_8,
                    colorSpace = src.colorSpace,
                )
            } else {
                entry.image
            }
            entry.copy(image = newImage, alpha = newAlpha)
        }
        fSet = set
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val set = fSet ?: return

        ToolUtils.draw_checkerboard(c, SK_ColorGRAY, SK_ColorDKGRAY, 25)

        val paint = SkPaint().apply {
            blendMode = SkBlendMode.kSrcOver
            // setColor4f({0.2, 0.8, 0.4, 1}) — colourises even rows
            // (alpha-only tagged tiles) and is a no-op on odd-row RGBA tiles
            // beyond the per-entry alpha multiplication.
            setColor4f(SkColor4f(0.2f, 0.8f, 0.4f, 1f))
        }

        // Top half — batched edge-set API.
        c.experimental_DrawEdgeAAImageSet(
            set, kM * kN, null, null,
            SkSamplingOptions(SkFilterMode.kLinear), paint,
            SrcRectConstraint.kFast,
        )

        c.translate(0f, (kN * kTileH).toFloat())

        // Bottom half — replay each entry through regular drawImageRect
        // so the two pipelines can be compared side-by-side. Upstream
        // folds the per-entry alpha into the paint, then routes through
        // `MakeTextureImage(canvas, std::move(orig))` — a GPU helper that
        // is identity on raster, so we just reuse `entry.image` directly.
        for (y in 0 until kN) {
            for (x in 0 until kM) {
                val i = y * kM + x
                val entry = set[i]
                val entryPaint = SkPaint().apply {
                    blendMode = paint.blendMode
                    color4f = paint.color4f
                    alphaf = entry.alpha * paint.alphaf
                }
                c.drawImageRect(
                    entry.image, entry.srcRect, entry.dstRect,
                    SkSamplingOptions.Default, entryPaint,
                    SrcRectConstraint.kFast,
                )
            }
        }
    }

    private companion object {
        const val kM = 4
        const val kN = 4
        const val kTileW = 50
        const val kTileH = 50
    }
}
