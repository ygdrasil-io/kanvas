package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkColor4f
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkColorMatrix
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkColorType
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkImages
import org.skia.foundation.SkMipmapMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRRect
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.skia.math.SkIPoint
import org.skia.math.SkIRect
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/hdr_pip_blur.cpp::HDRPiPBlurGM` (`hdr-pip-blur`,
 * 640 × 360 in DM mode).
 *
 * Emulates Android HDR-mode pipeline rendering : a wide-gamut sRGB
 * surface composites an SDR background image, an HDR-PQ "PiP" video
 * over a rounded rect, then a faded blur shade over the whole frame.
 *
 * Pipeline upstream (mirrored here) :
 *  1. allocate an offscreen RGBA8 surface tagged with a Rec.2020 OETF
 *     on a Display-P3 gamut (the wide-gamut SDR working space) ;
 *  2. clear it dark grey, draw the SDR background, draw a 20-radius
 *     rounded rect for the PiP frame, clip to that rect and draw the
 *     PQ-tagged image inside ;
 *  3. snapshot the offscreen via [org.skia.core.SkSurface.makeTemporaryImage] ;
 *  4. on the destination canvas, draw the snapshot, then draw it again
 *     after running it through a wide-radius `Blur` image filter via
 *     [SkImages.MakeWithFilter], at 90% alpha — the visual "shade".
 *
 * **Color management caveat — known limitation** : the GM relies on
 * faithful tone-mapping through the wide-gamut intermediate (Rec.2020
 * OETF on Display P3 gamut) into an HDR-PQ-tagged PiP image. The
 * kanvas-skia raster pipeline tags colorspaces correctly via
 * [SkImage.makeColorSpace] / [SkColorSpace.MakePqHdr] but the per-pixel
 * tonemapping used to convert PQ → SDR working space is approximate
 * (no full BT.2390 EETF, no display-referred mapping). The structural
 * layout (background image, rounded PiP, blur shade) reproduces ; the
 * per-pixel HDR roll-off vs. upstream sees ~10-15 % nominal divergence.
 * Score < 100 % is expected here.
 */
public class HdrPipBlurGM : GM() {

    override fun getName(): String = "hdr-pip-blur"
    override fun getISize(): SkISize = K_NON_BENCH_SIZE

    private var fBackgroundImage: SkImage? = null
    private var fPiPImage: SkImage? = null
    private var fShadeBlur: SkImageFilter? = null
    private val fPaint: SkPaint = SkPaint()

    override fun onOnceBeforeDraw() {
        // The base paint has a black colour and a saturation-1.5 colour-matrix
        // filter. Upstream uses [SkColor4f] {0,0,0,1}; we set the equivalent
        // 32-bit ARGB.
        fPaint.color = 0xFF000000.toInt()

        val cm = SkColorMatrix()
        cm.setSaturation(1.5f)
        fPaint.colorFilter = SkColorFilters.Matrix(cm)

        // SDR background — sRGB-tagged.
        fBackgroundImage = ToolUtils.GetResourceAsImage("images/yellow_rose.png")
            ?.makeColorSpace(SkColorSpace.makeSRGB())

        // PiP — PQ-on-Rec.2020 (HDR). The conversion through
        // [SkImage.makeColorSpace] retags the pixels into the PQ
        // colorspace ; per-pixel tonemapping happens at draw time.
        fPiPImage = ToolUtils.GetResourceAsImage("images/mandrill_512.png")
            ?.makeColorSpace(SkColorSpace.MakePqHdr())

        // Wide-radius blur (sigma 32 at full size scaled to non-bench
        // size — 32 * 640/2560 = 8 px on each axis, matching the DM
        // path upstream takes when `getMode() == kGM_Mode`).
        val sigma = 32f
        val sigmaX = sigma * K_NON_BENCH_SIZE.width.toFloat() / K_FULL_SIZE.width.toFloat()
        val sigmaY = sigma * K_NON_BENCH_SIZE.height.toFloat() / K_FULL_SIZE.height.toFloat()
        fShadeBlur = SkImageFilters.Blur(sigmaX, sigmaY, SkTileMode.kClamp, null)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val canvasOut = canvas ?: return
        val screenBounds = SkIRect.MakeWH(K_NON_BENCH_SIZE.width, K_NON_BENCH_SIZE.height)

        // 1) Wide-gamut intermediate surface — RGBA8 with a Rec.2020
        //    transfer fn on the Display P3 gamut. Falls back to sRGB if
        //    the colour space cannot be built (defensive — kRec2020 +
        //    Rec.2020 / DisplayP3 are bit-stable named singletons today).
        val widegamutCs = SkColorSpace.makeRGB(
            SkNamedTransferFn.kRec2020,
            SkNamedGamut.kDisplayP3,
        ) ?: SkColorSpace.makeSRGB()
        val contentInfo = SkImageInfo.Make(
            width = screenBounds.width(),
            height = screenBounds.height(),
            colorType = SkColorType.kRGBA_8888,
            alphaType = SkAlphaType.kUnpremul,
            colorSpace = widegamutCs,
        )
        val content = canvasOut.makeSurface(contentInfo) ?: return

        val c = content.canvas
        // SkColors::kDkGray = (.25, .25, .25, 1).
        c.clear(SkColor4f.kDkGray.toSkColor())

        // Map the conceptual full-size space (2560 × 1440) down to the
        // screen bounds so all subsequent draws compose in "full size"
        // coords. Identity at DM scale (full-size == screen-size when we
        // run as GM) but kept verbatim from upstream for fidelity.
        val toScreen = SkMatrix.MakeRectToRect(
            SkRect.MakeWH(K_FULL_SIZE.width.toFloat(), K_FULL_SIZE.height.toFloat()),
            SkRect.MakeWH(screenBounds.width().toFloat(), screenBounds.height().toFloat()),
            SkMatrix.ScaleToFit.kFill_ScaleToFit,
        )
        if (toScreen != null) c.concat(toScreen)

        // 2) SDR background fill.
        fBackgroundImage?.let { bg ->
            c.drawImageRect(
                bg,
                SkRect.MakeWH(bg.width.toFloat(), bg.height.toFloat()),
                SkRect.MakeWH(K_FULL_SIZE.width.toFloat(), K_FULL_SIZE.height.toFloat()),
                LINEAR_SAMPLING,
                fPaint,
            )
        }

        // 3) PiP region — rounded-corner rect outline + clipped HDR PQ
        //    image fill.
        val pip = SkRRect.MakeRectXY(
            SkRect.MakeXYWH(1500f, 700f, 800f, 600f),
            20f, 20f,
        )
        c.drawRRect(pip, fPaint)
        c.save()
        try {
            c.clipRRect(pip, doAntiAlias = true)
            fPiPImage?.let { pipImg ->
                c.drawImageRect(
                    pipImg,
                    SkRect.MakeWH(pipImg.width.toFloat(), pipImg.height.toFloat()),
                    pip.rect(),
                    LINEAR_SAMPLING,
                    fPaint,
                )
            }
        } finally {
            c.restore()
        }

        // 4) Snapshot via makeTemporaryImage (raster pipeline = same
        //    pixels as makeImageSnapshot — see SkSurface.makeTemporaryImage
        //    KDoc).
        val input = content.makeTemporaryImage()

        canvasOut.save()
        try {
            canvasOut.clipRect(
                SkRect.MakeWH(screenBounds.width().toFloat(), screenBounds.height().toFloat()),
            )
            canvasOut.drawImage(input, 0f, 0f, LINEAR_SAMPLING, fPaint)

            // 5) Blur the input through the dedicated shade filter, then
            //    composite at 90% alpha. The MakeWithFilter pipeline
            //    crops the output to screenBounds and reports the
            //    actually-rendered region via outSubset / outOffset.
            val outSubset = SkIRect(0, 0, 0, 0)
            val outOffset = SkIPoint(0, 0)
            val blur = SkImages.MakeWithFilter(
                input,
                fShadeBlur,
                screenBounds,
                screenBounds,
                outSubset,
                outOffset,
            )
            if (blur != null) {
                val fadedBlur = SkPaint().apply { alpha = (0.9f * 255f).toInt() }
                canvasOut.drawImageRect(
                    blur,
                    SkRect.MakeWH(outSubset.width().toFloat(), outSubset.height().toFloat()),
                    SkRect.MakeXYWH(
                        outOffset.fX.toFloat(),
                        outOffset.fY.toFloat(),
                        outSubset.width().toFloat(),
                        outSubset.height().toFloat(),
                    ),
                    LINEAR_SAMPLING,
                    fadedBlur,
                )
            }
        } finally {
            canvasOut.restore()
        }
    }

    public companion object {
        /**
         * Full-mode (benchmark) raster size — 2560 × 1440. Drives the
         * blur sigma scaling so the GM's pixel-noise patterns match
         * upstream regardless of which mode is active.
         */
        private val K_FULL_SIZE: SkISize = SkISize.Make(2560, 1440)

        /**
         * DM (test) raster size — 640 × 360. The default for the GM
         * mode used by `:kanvas-skia`'s test harness.
         */
        private val K_NON_BENCH_SIZE: SkISize = SkISize.Make(640, 360)

        private val LINEAR_SAMPLING: SkSamplingOptions = SkSamplingOptions(
            SkFilterMode.kLinear,
            SkMipmapMode.kNone,
            cubic = null,
            maxAniso = 0,
        )
    }
}
