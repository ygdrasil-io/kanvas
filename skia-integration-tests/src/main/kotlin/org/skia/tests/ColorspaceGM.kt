package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkFont
import org.skia.foundation.SkPaint
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.graphiks.math.SkcmsMatrix3x3
import org.graphiks.math.SkcmsTransferFunction
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/colorspace.cpp::DEF_SIMPLE_GM(colorspace, canvas,
 * W*std::size(gTFs), H*std::size(gGamuts))`.
 *
 * Exercises self-consistency of colour-space management by iterating
 * a 7 × 5 grid of intermediate colour spaces (all combinations of
 * [G_TFS] transfer functions × [G_GAMUTS] gamuts) and converting the
 * test image through each one via [org.skia.foundation.SkImage.makeColorSpace]
 * before drawing. Identical cells indicate that
 * `imgCS → midCS → dstCS` is numerically consistent regardless of the
 * intermediate encoding (small precision loss is allowed).
 *
 * Strategy: [org.skia.foundation.SkImage.makeColorSpace] — matches
 * `SkImage_makeColorSpace` in upstream.
 *
 * @see Colorspace2GM for the companion `SkCanvas_makeSurface` strategy.
 */
public class ColorspaceGM : GM() {

    override fun getName(): String = "colorspace"

    override fun getISize(): SkISize = SkISize.Make(W * G_TFS.size, H * G_GAMUTS.size)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val font: SkFont = ToolUtils.DefaultPortableFont()
        val paint = SkPaint()

        // In upstream, a null colorSpace on the canvas signals "not color-managed" →
        // early-exit with a message. Our SkCanvas always has a colorSpace (sRGB by
        // default), so we always proceed.

        val img = ToolUtils.GetResourceAsImage("images/mandrill_128.png")
        if (img == null) {
            c.drawString("Could not load our test image!", W.toFloat(), H.toFloat(), font, paint)
            return
        }

        for (gamut in G_GAMUTS) {
            c.save()
            for (tf in G_TFS) {
                val midCS = SkColorSpace.makeRGB(tf, gamut)
                if (midCS != null) {
                    val converted = img.makeColorSpace(midCS)
                    if (converted != null) {
                        c.drawImage(converted, 0f, 0f)
                    }
                }
                c.translate(W.toFloat(), 0f)
            }
            c.restore()
            c.translate(0f, H.toFloat())
        }
    }

    private companion object {
        private const val W = 128
        private const val H = 128

        private val G_TFS: Array<SkcmsTransferFunction> = arrayOf(
            SkNamedTransferFn.kSRGB,
            SkNamedTransferFn.k2Dot2,
            SkNamedTransferFn.kLinear,
            SkNamedTransferFn.kRec2020,
            SkNamedTransferFn.kPQ,
            SkNamedTransferFn.kHLG,
            SkNamedTransferFn.kRec709,
        )

        private val G_GAMUTS: Array<SkcmsMatrix3x3> = arrayOf(
            SkNamedGamut.kSRGB,
            SkNamedGamut.kAdobeRGB,
            SkNamedGamut.kDisplayP3,
            SkNamedGamut.kRec2020,
            SkNamedGamut.kXYZ,
        )
    }
}
