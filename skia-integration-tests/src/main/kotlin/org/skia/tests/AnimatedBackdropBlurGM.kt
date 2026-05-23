package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SaveLayerRec
import org.graphiks.math.SkISize
import org.graphiks.math.SkIRect
import org.graphiks.math.SkRect
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkFont
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTileMode
import org.skia.tools.ToolUtils

/**
 * Port of upstream Skia's `gm/animatedimageblurs.cpp::AnimatedBackdropBlur`
 * (the third of three GMs declared in that file).
 *
 * Renders a scrolling Lorem-ipsum text block plus a `color_wheel.png`
 * thumbnail, then applies a backdrop blur via a [SaveLayerRec] whose
 * [SaveLayerRec.backdrop] filter crops + blurs the region `{0, 100, 512, 400}`
 * using `kMirror` tiling inside the blur.
 *
 * **Animation.** Upstream animates `fVOffset` via `TimeUtils::PingPong`
 * (range 0 → 350 → 0 over 6 s). For the static t=0 snapshot we use
 * the constructor initialiser `fVOffset = 0`.
 *
 * **Filter chain.**
 * ```cpp
 * const SkRect crop{0, 100, 512, 400};
 * fFilter = SkImageFilters::Crop(crop, SkTileMode::kDecal,
 *               SkImageFilters::Blur(30, 30,
 *                   SkImageFilters::Crop(crop, SkTileMode::kMirror, nullptr)));
 * ```
 * maps verbatim to:
 * ```kotlin
 * val crop = SkRect.MakeLTRB(0f, 100f, 512f, 400f)
 * val filter = SkImageFilters.Crop(crop, SkTileMode.kDecal,
 *     SkImageFilters.Blur(30f, 30f, SkTileMode.kDecal,
 *         SkImageFilters.Crop(crop, SkTileMode.kMirror, null)))
 * ```
 * Note: the `Blur(30, 30, inner)` call in C++ uses the 3-arg legacy
 * overload (no explicit tileMode), which defaults to kDecal.
 *
 * **SaveLayerRec.**
 * ```cpp
 * fLayerRec = SkCanvas::SaveLayerRec(nullptr, nullptr, fFilter.get(), 0);
 * canvas->saveLayer(fLayerRec);
 * ```
 * maps to:
 * ```kotlin
 * val layerRec = SaveLayerRec(bounds = null, paint = null, backdrop = fFilter, flags = 0)
 * canvas.saveLayer(layerRec)
 * ```
 *
 * **drawImageRect.**
 * ```cpp
 * canvas->drawImageRect(fImage.get(),
 *     SkRect::MakeXYWH(16.f, fVOffset, 128.f, dstHeight),
 *     SkFilterMode::kLinear);
 * ```
 * maps to `drawImageRect(img, srcFull, dst, SkSamplingOptions(kLinear), null)`.
 */
public class AnimatedBackdropBlurGM : GM() {

    override fun getName(): String = "animated-backdrop-blur"
    override fun getISize(): SkISize = SkISize.Make(512, 1024)

    private var filter: SkImageFilter? = null
    private var image: SkImage? = null
    private lateinit var font: SkFont

    // t=0 snapshot: matches upstream `fVOffset = 0`.
    private val vOffset: Float = 0f

    override fun onOnceBeforeDraw() {
        font = SkFont(ToolUtils.DefaultPortableTypeface(), 20f)
        image = ToolUtils.GetResourceAsImage("images/color_wheel.png")

        val crop = SkRect.MakeLTRB(0f, 100f, 512f, 400f)
        filter = SkImageFilters.Crop(
            crop, SkTileMode.kDecal,
            SkImageFilters.Blur(
                30f, 30f,
                SkImageFilters.Crop(crop, SkTileMode.kMirror, null),
            ),
        )
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val txts = arrayOf(
            "Lorem ipsum dolor sit amet,",
            "consectetur adipiscing elit,",
            "sed do eiusmod tempor incididunt",
            "ut labore et dolore magna aliqua.",
            "",
            "",
            "Ut enim ad minim veniam,",
            "quis nostrud exercitation ullamco laboris",
            "nisi ut aliquip ex ea commodo consequat.",
            "",
            "",
            "Duis aute irure dolor in reprehenderit",
            "in voluptate velit esse cillum dolore",
            "eu fugiat nulla pariatur.",
        )

        val paint = SkPaint()
        var curOffset = vOffset
        for (txt in txts) {
            if (txt.isNotEmpty()) {
                c.drawSimpleText(
                    txt, txt.length, SkTextEncoding.kUTF8,
                    0f, curOffset, font, paint,
                )
            }
            curOffset += font.size
        }

        val img = image
        if (img != null) {
            val dstHeight = img.height * 128f / img.width
            val dst = SkRect.MakeXYWH(16f, vOffset, 128f, dstHeight)
            val src = SkRect.MakeIWH(img.width, img.height)
            c.drawImageRect(img, src, dst, SkSamplingOptions(SkFilterMode.kLinear), null)
        }

        // Backdrop blur layer — reads parent pixels through fFilter,
        // seeds the new layer, then restore() composites it back.
        val layerRec = SaveLayerRec(bounds = null, paint = null, backdrop = filter, flags = 0)
        c.saveLayer(layerRec)
        c.restore()
    }
}
