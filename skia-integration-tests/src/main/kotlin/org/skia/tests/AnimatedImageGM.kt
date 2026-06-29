package org.skia.tests

import org.graphiks.math.SkIRect
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import org.graphiks.kanvas.codec.AndroidCodec
import org.graphiks.kanvas.codec.AnimatedImage
import org.graphiks.kanvas.codec.Codec
import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.skia.core.withRestore
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkData
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkRRect
import org.graphiks.math.SK_ColorTRANSPARENT
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/animated_image_orientation.cpp`](https://github.com/google/skia/blob/main/gm/animated_image_orientation.cpp)
 * — exercises the [AnimatedImage] frame-decode pipeline through
 * every combination of :
 *   * `usePic` : capture each frame as an [SkPicture] vs decode to [org.skia.foundation.SkImage]
 *   * `scale`  : 4 scales (1.25, 1.0, 0.75, 0.5) applied to the decoded info
 *   * `doCrop` : feed the codec a sub-region, with the EXIF origin
 *                transform applied to the requested crop rect
 *   * `doPostProcess` : mask each frame against a rounded-rect picture
 *                       (using `SK_ColorTRANSPARENT` + `kSrc` blend)
 *
 * Two source images : `images/stoplight_h.webp` (step 2) and
 * `images/flightAnim.gif` (step 20) — see the two `DEF_GM` calls at
 * the bottom of the upstream cpp.
 *
 * ## Port status
 *
 * Body fully ported against the new `org.graphiks.kanvas.codec.{Codec,
 * AndroidCodec, AnimatedImage}` flag-planting surface (all three
 * resolve to `TODO("STUB.…")` at runtime). The matching
 * `AnimatedImageTest` is `@Disabled("STUB.ANIMATED_IMAGE")` until
 * those backends are implemented.
 */
public class AnimatedImageGM(
    private val path: String,
    private val gmName: String,
    private val step: Int,
    private val cropRect: SkIRect,
) : GM() {

    // No-arg constructor kept for the JUnit harness's `runGmTest(GM())`
    // pattern — defaults to the upstream `stoplight` variant.
    public constructor() : this(
        path = "images/stoplight_h.webp",
        gmName = "stoplight",
        step = 2,
        cropRect = SkIRect.MakeLTRB(5, 6, 11, 29),
    )

    private var initialized = false
    private var sizeCache: SkISize = SkISize.Make(0, 0)
    private var translate: Int = 0
    private var data: SkData? = null

    private fun init() {
        if (initialized) return
        initialized = true

        val d = ToolUtils.GetResourceAsData(path)
            ?: error("AnimatedImageGM: resource not found at $path")
        data = d

        val codec = Codec.MakeFromData(d.toByteArray())
            ?: error("AnimatedImageGM: Codec.MakeFromData returned null for $path")
        val dimensions = codec.dimensions()

        // Match upstream's formula for the per-frame translation : largest
        // edge × 1.25 (room for the up-scale variant) + 2 px padding.
        translate = (maxOf(dimensions.width, dimensions.height) * 1.25f + 2f).toInt()

        sizeCache = SkISize.Make(
            translate * kMaxFrames *
                2 *   // crop + no-crop
                2,    // post-process + no post-process
            translate * 4 *  // 4 scales
                2,    // makePictureSnapshot + getCurrentFrame
        )
    }

    override fun getName(): String = "${gmName}_animated_image"

    override fun getISize(): SkISize {
        init()
        return sizeCache
    }

    override fun onDraw(canvas: SkCanvas?) {
        if (canvas == null) return
        init()
        val srcData = data ?: return

        for (usePic in booleanArrayOf(true, false)) {
            val drawProc: (AnimatedImage) -> Unit = { anim ->
                if (usePic) {
                    val pic: SkPicture = anim.makePictureSnapshot()
                    canvas.drawPicture(pic)
                } else {
                    canvas.drawImage(anim.getCurrentFrame(), 0f, 0f)
                }
            }

            for (scale in floatArrayOf(1.25f, 1.0f, 0.75f, 0.5f)) {
                canvas.save()
                for (doCrop in booleanArrayOf(false, true)) {
                    for (doPostProcess in booleanArrayOf(false, true)) {
                        val codec = Codec.MakeFromData(srcData.toByteArray())
                            ?: error("Codec.MakeFromData returned null mid-loop")
                        val origin = codec.getOrigin()
                        val androidCodec = AndroidCodec.MakeFromCodec(codec)
                        var info = androidCodec.getInfo()
                        val unscaledSize = if (origin.swapsWidthHeight()) {
                            SkISize.Make(info.height, info.width)
                        } else {
                            info.dimensions()
                        }

                        val scaledSize = SkISize.Make(
                            (unscaledSize.width  * scale).toInt(),
                            (unscaledSize.height * scale).toInt(),
                        )
                        info = info.makeWH(scaledSize.width, scaledSize.height)

                        var frameCropRect = SkIRect.MakeSize(scaledSize)
                        if (doCrop) {
                            // Map the requested crop rect through the EXIF
                            // origin + scale transform so the codec receives
                            // the sub-region in its native (decoded) pixel
                            // grid.
                            // RectToRectOrIdentity : the upstream helper falls
                            // back to identity when src is degenerate.
                            val rectToRect = SkMatrix.MakeRectToRect(
                                SkRect.Make(unscaledSize),
                                SkRect.Make(scaledSize),
                                SkMatrix.ScaleToFit.kFill_ScaleToFit,
                            ) ?: SkMatrix.I()
                            val originMatrix = origin.toMatrix(
                                unscaledSize.width, unscaledSize.height,
                            )
                            val composed = rectToRect.preConcat(originMatrix)
                            var cropRectFloat = SkRect.Make(cropRect)
                            cropRectFloat = composed.mapRect(cropRectFloat)
                            frameCropRect = cropRectFloat.roundOut()
                        }

                        val postProcessor: SkPicture? = if (doPostProcess) {
                            buildPostProcessorPicture(
                                SkRect.MakeWH(
                                    frameCropRect.width().toFloat(),
                                    frameCropRect.height().toFloat(),
                                ),
                            )
                        } else {
                            null
                        }
                        val animatedImage = AnimatedImage.Make(
                            androidCodec, info, frameCropRect, postProcessor,
                        ) ?: error("AnimatedImage.Make returned null")
                        animatedImage.setRepetitionCount(0)

                        for (frame in 0 until kMaxFrames) {
                            // SkAutoCanvasRestore(canvas, doCrop) — Kotlin
                            // equivalent : only `save()` when doCrop, but
                            // always restore to entry depth on exit.
                            if (doCrop) {
                                canvas.save()
                                canvas.translate(
                                    frameCropRect.left.toFloat(),
                                    frameCropRect.top.toFloat(),
                                )
                                drawProc(animatedImage)
                                canvas.restore()
                            } else {
                                drawProc(animatedImage)
                            }

                            canvas.translate(translate.toFloat(), 0f)
                            val duration = animatedImage.currentFrameDuration()
                            if (duration == AnimatedImage.kFinished) {
                                break
                            }
                            for (i in 0 until step) {
                                animatedImage.decodeNextFrame()
                            }
                        }
                    }
                }
                canvas.restore()
                canvas.translate(0f, translate.toFloat())
            }
        }
    }

    /**
     * Mirrors upstream's `post_processor(bounds)` — records a picture
     * that punches an inverse-even-odd rounded-rect hole with
     * `SK_ColorTRANSPARENT` + `kSrc` blend, so any image replayed
     * underneath gets masked to a rounded shape.
     */
    private fun buildPostProcessorPicture(bounds: SkRect): SkPicture {
        val radius = ((bounds.width() + bounds.height()) / 6f)
        val pathBuilder = SkPathBuilder()
            .setFillType(SkPathFillType.kInverseEvenOdd)
            .addRRect(SkRRect.MakeRectXY(bounds, radius, radius))

        val paint = SkPaint().apply {
            isAntiAlias = true
            color = SK_ColorTRANSPARENT
            blendMode = SkBlendMode.kSrc
        }

        val recorder = SkPictureRecorder()
        val recCanvas = recorder.beginRecording(bounds)
        recCanvas.drawPath(pathBuilder.detach(), paint)
        return recorder.finishRecordingAsPicture()
    }

    private companion object {
        const val kMaxFrames = 2
    }
}
