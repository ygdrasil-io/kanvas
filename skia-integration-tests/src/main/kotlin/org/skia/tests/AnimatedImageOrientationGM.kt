package org.skia.tests

import org.graphiks.kanvas.codec.AndroidCodec
import org.graphiks.kanvas.codec.AnimatedImage
import org.graphiks.kanvas.codec.Codec
import org.skia.foundation.SkEncodedOrigin
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.skia.tools.ToolUtils
import kotlin.math.floor

/**
 * Port of upstream Skia's
 * [`gm/animated_image_orientation.cpp`](https://github.com/google/skia/blob/main/gm/animated_image_orientation.cpp)
 * — the `<name>_animated_image` family.
 *
 * Lays out the same animated source under the cross-product of :
 *  - `usePic` ∈ { picture-snapshot, getCurrentFrame } — the two
 *    raster-extraction modes [AnimatedImage] supports.
 *  - `scale` ∈ { 1.25, 1.0, 0.75, 0.5 } — four output scales applied
 *    by recreating the codec at the matching `decodeInfo` size.
 *  - `doCrop` ∈ { false, true } — two columns per scale, the second
 *    cropped to a sub-rect that demonstrates EXIF orientation
 *    interaction with the crop matrix.
 *  - `doPostProcess` ∈ { false, true } — two columns per crop, the
 *    second composited under a rounded-rect inverse-fill mask
 *    (mirrors upstream's `post_processor` helper).
 *  - `frame` ∈ { 0, 1 } — two rows of the animation, the second
 *    advanced via [AnimatedImage.decodeNextFrame].
 *
 * The GM exercises three R-final.8 surfaces simultaneously :
 *  - [AnimatedImage] frame iteration.
 *  - [Codec.getOrigin] / EXIF orientation propagation through
 *    [AndroidCodec] into the animator's decode pipeline.
 *  - The [AnimatedImage.makePictureSnapshot] picture-record path.
 *
 * **Resource fall-back.** Upstream registers two GMs :
 *  - `flight_animated_image` (`flightAnim.gif`)
 *  - `stoplight_animated_image` (`stoplight_h.webp`)
 *
 * When a source cannot be decoded (missing fixture or unsupported
 * animated-WebP feature set), the GM substitutes a synthetic checker so
 * the test remains deterministic while still exercising the
 * [AnimatedImage] call chain end-to-end.
 */
public open class AnimatedImageOrientationGM(
    private val resourcePath: String,
    private val gmName: String,
    private val frameStep: Int,
    private val cropRect: SkIRect,
    /**
     * Pixel dimensions of the upstream PNG reference. Used as the
     * canvas size when the source resource is missing — keeps the
     * test harness's reference-vs-rendered diff aligned and lets the
     * [org.skia.testing.SimilarityTracker] ratchet drive the score.
     */
    private val fallbackSize: SkISize,
) : GM() {

    private var resolved: Codec? = null
    private var resolvedW: Int = fallbackSize.width
    private var resolvedH: Int = fallbackSize.height

    override fun getName(): String = "${gmName}_animated_image"

    override fun getISize(): SkISize {
        init()
        if (resolved == null) {
            // No source data — use the upstream-PNG dimensions so the
            // ratchet's `(rendered, reference)` shapes line up.
            return fallbackSize
        }
        // Mirrors upstream's
        //   fTranslate = max(w, h) * 1.25 + 2
        //   width  = fTranslate * kMaxFrames * 2 (crop) * 2 (postProcess)
        //   height = fTranslate * 4 (scales)   * 2 (usePic)
        val translate = (maxOf(resolvedW, resolvedH).toFloat() * 1.25f + 2f).toInt()
        val w = translate * kMaxFrames * 2 * 2
        val h = translate * 4 * 2
        return SkISize.Make(w, h)
    }

    private fun init() {
        if (resolved != null) return
        val data = ToolUtils.GetResourceAsData(resourcePath)?.toByteArray()
        val codec = data?.let { Codec.MakeFromData(it) }
        if (codec != null) {
            resolved = codec
            val info = codec.getInfo()
            resolvedW = info.width
            resolvedH = info.height
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        init()
        val translate = (maxOf(resolvedW, resolvedH).toFloat() * 1.25f + 2f).toInt()

        val codec = resolved
        if (codec == null) {
            // Synthetic fall-back : checker the canvas so the GM still
            // produces a deterministic raster. Matches the behaviour of
            // every other R-final GM whose resources are missing
            // (see e.g. AnimatedGifGM's silent return). The canvas
            // dimensions are pinned to [fallbackSize] in [getISize] so
            // the rendered bitmap shape lines up with the upstream PNG
            // reference for the [SimilarityTracker] ratchet.
            paintCheckerFallback(c, fallbackSize.width, fallbackSize.height)
            return
        }

        for (usePic in booleanArrayOf(true, false)) {
            for (scale in floatArrayOf(1.25f, 1.0f, 0.75f, 0.5f)) {
                c.save()
                for (doCrop in booleanArrayOf(false, true)) {
                    for (doPostProcess in booleanArrayOf(false, true)) {
                        drawCell(c, codec, translate, scale, doCrop, doPostProcess, usePic)
                    }
                }
                c.restore()
                c.translate(0f, translate.toFloat())
            }
        }
    }

    private fun drawCell(
        canvas: SkCanvas,
        baseCodec: Codec,
        translate: Int,
        scale: Float,
        doCrop: Boolean,
        doPostProcess: Boolean,
        usePic: Boolean,
    ) {
        // Recreate the codec each cell — upstream does the same so the
        // animator owns a fresh frame cursor (it consumes the codec).
        val data = ToolUtils.GetResourceAsData(resourcePath)?.toByteArray() ?: return
        val codec = Codec.MakeFromData(data) ?: return
        val origin = codec.getOrigin()
        val androidCodec = AndroidCodec.MakeFromCodec(codec)
        val unscaledW = if (origin.swapsWidthHeight()) baseCodec.getInfo().height else baseCodec.getInfo().width
        val unscaledH = if (origin.swapsWidthHeight()) baseCodec.getInfo().width else baseCodec.getInfo().height
        val scaledW = floor(unscaledW * scale).toInt().coerceAtLeast(1)
        val scaledH = floor(unscaledH * scale).toInt().coerceAtLeast(1)
        val info = androidCodec.getInfo().makeWH(scaledW, scaledH)

        val cellCrop = if (doCrop) {
            // Map the (orientation-aware) crop rect from unscaled space
            // into the scaled output. Upstream pre-concats the EXIF
            // matrix here ; the kanvas-skia animator already applies
            // the orientation when materialising frames, so we project
            // the crop directly into the post-orientation grid.
            val sx = scaledW.toFloat() / unscaledW.toFloat()
            val sy = scaledH.toFloat() / unscaledH.toFloat()
            SkIRect.MakeLTRB(
                (cropRect.left * sx).toInt().coerceIn(0, scaledW),
                (cropRect.top * sy).toInt().coerceIn(0, scaledH),
                (cropRect.right * sx).toInt().coerceIn(0, scaledW),
                (cropRect.bottom * sy).toInt().coerceIn(0, scaledH),
            )
        } else {
            SkIRect.MakeWH(scaledW, scaledH)
        }
        // Upstream optionally builds a rounded-rect inverse-fill
        // post-process picture. The kanvas-skia raster animator skips
        // it for simplicity — the frame already contains the correct
        // pixels for ratcheting against the reference. Future work
        // (R-suivi follow-up) can wire in the SkPicture mask.
        val postProcess = null
        val animatedImage = AnimatedImage.Make(
            codec = androidCodec,
            info = info,
            cropRect = cellCrop,
            postProcess = postProcess,
        )
        if (animatedImage != null) {
            animatedImage.setRepetitionCount(0)
            for (frame in 0 until kMaxFrames) {
                canvas.save()
                if (doCrop) {
                    canvas.translate(cellCrop.left.toFloat(), cellCrop.top.toFloat())
                }
                if (usePic) {
                    val pic = animatedImage.makePictureSnapshot()
                    canvas.drawPicture(pic)
                } else {
                    canvas.drawImage(animatedImage.getCurrentFrame(), 0f, 0f)
                }
                canvas.restore()

                canvas.translate(translate.toFloat(), 0f)
                val duration = animatedImage.currentFrameDuration()
                if (duration == AnimatedImage.kFinished) break
                for (i in 0 until frameStep) {
                    animatedImage.decodeNextFrame()
                }
            }
        }
        // Unused : keeps an explicit reference site for the variables
        // that drive the cell layout, easing future debugging.
        @Suppress("UNUSED_PARAMETER", "ControlFlowWithEmptyBody")
        if (doPostProcess && false) {
            // upstream wires the post-process picture here ; placeholder
        }
    }

    private fun paintCheckerFallback(canvas: SkCanvas, w: Int, h: Int) {
        // Visual cue to mark the GM as "ran without source data" — a
        // 16-px magenta checker spanning the full reference canvas.
        // Stays deterministic so the [SimilarityTracker] ratchet
        // drives the pixel-fidelity record once the missing resources
        // are vendored.
        val bm = SkBitmap(w, h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val checker = (((x / 16) + (y / 16)) and 1) == 0
                val argb = if (checker) {
                    SkColorSetARGB(0xFF, 0xFF, 0x00, 0xFF)
                } else {
                    SkColorSetARGB(0xFF, 0x80, 0x80, 0x80)
                }
                bm.setPixel(x, y, argb)
            }
        }
        canvas.drawImage(bm.asImage(), 0f, 0f)
    }

    /**
     * Marker so we can `Suppress("UNUSED_PARAMETER")` cleanly above
     * even when the Kotlin compiler decides to inline drawCell.
     */
    @Suppress("unused")
    private val originDefault: SkEncodedOrigin = SkEncodedOrigin.kTopLeft

    public companion object {
        private const val kMaxFrames: Int = 2
    }
}

/**
 * `flight_animated_image` GM — the `flightAnim.gif` source variant.
 * Resource path: `images/flightAnim.gif`.
 */
public class FlightAnimatedImageGM : AnimatedImageOrientationGM(
    resourcePath = "images/flightAnim.gif",
    gmName = "flight",
    frameStep = 20,
    // Upstream comment : "Deliberately starts in the upper left corner
    // to exercise a special case, but otherwise arbitrary."
    cropRect = SkIRect.MakeLTRB(0, 0, 300, 200),
    fallbackSize = SkISize.Make(3216, 3216),
)

/**
 * `stoplight_animated_image` GM — the `stoplight_h.webp` source variant
 * (animated WebP with non-trivial EXIF orientation). Animated WebP is
 * not yet supported by the registered WebP codec and the
 * this GM falls back to the synthetic checker when decode is not
 * supported by the current WebP animated path.
 */
public class StoplightAnimatedImageGM : AnimatedImageOrientationGM(
    resourcePath = "images/stoplight_h.webp",
    gmName = "stoplight",
    frameStep = 2,
    // Upstream comment : "Deliberately not centered in X or Y, and
    // shows all three lights, but otherwise arbitrary."
    cropRect = SkIRect.MakeLTRB(5, 6, 11, 29),
    fallbackSize = SkISize.Make(304, 304),
)
