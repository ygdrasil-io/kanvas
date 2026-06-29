package org.graphiks.kanvas.codec

import org.skia.core.SkCanvas
import org.skia.core.SkPicture
import org.skia.core.SkPictureRecorder
import org.skia.foundation.SkEncodedOrigin
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.graphiks.math.SkIRect
import org.graphiks.math.SkRect
import org.skia.utils.SkPixmapUtils

/**
 * Mirrors Skia's
 * [`SkAnimatedImage`](https://github.com/google/skia/blob/main/include/android/SkAnimatedImage.h)
 * — a stateful drawable wrapping an [SkAndroidCodec] (and through it
 * an animated [SkCodec]) that keeps a "current frame" cursor and
 * advances through the animation under client control via
 * [decodeNextFrame].
 *
 * **R-final.8 scope.** The kanvas-skia port surfaces the API the
 * `gm/animated_image_orientation.cpp` GM exercises :
 *  - [Make] — construct from an [SkAndroidCodec] (with optional
 *    `requestedInfo` / `cropRect` / `postProcess` for the GM's
 *    cropped + scaled + post-processed variants).
 *  - [decodeNextFrame] / [getCurrentFrame] / [makePictureSnapshot]
 *    — frame iteration + snapshotting.
 *  - [currentFrameDuration] — ms-until-next-frame, or [kFinished]
 *    once the animation has completed (per [setRepetitionCount]).
 *  - [reset] / [setRepetitionCount] — playback controls.
 *
 * **Ownership boundary.** `codec-animated` owns playback state, frame
 * cursoring, orientation/crop/scale/post-processing, and snapshotting.
 * Container-specific parsing and frame composition remain owned by the
 * format codec that supplies the [SkCodec] (GIF today, future animated
 * WebP later). This module therefore does not register any
 * [CodecDecoderProvider] and should stay free of format sniffing logic.
 *
 * **Implementation detail.** Built on top of R-final.5's
 * [SkCodec.getFrameCount] + [SkCodec.getFrameInfo] +
 * [SkCodec.getPixels] (`Options{frameIndex, priorFrame}`). The
 * underlying codec owns the disposal-method state machine ; this
 * class just plays back the per-frame pre-composed bitmaps and
 * applies the orientation/crop/scale/post-processing transform
 * pipeline expected by the GM.
 *
 * **Thread model.** Like upstream, [SkAnimatedImage] is **not**
 * thread-safe — callers serialize access to a given instance.
 */
public class SkAnimatedImage private constructor(
    private val codec: SkAndroidCodec,
    private val origin: SkEncodedOrigin,
    /**
     * Decode size — the dimensions [decodeNextFrame] writes into the
     * raw frame buffer (post-orientation but pre-crop). Equals the
     * caller's `requestedInfo` if supplied, defaults to the codec's
     * `getInfo()` size with the EXIF rotation applied.
     */
    private val decodeInfo: SkImageInfo,
    private val cropRect: SkIRect,
    private val postProcess: SkPicture?,
) {

    private val frameCount: Int = codec.codec().getFrameCount()
    private val frameInfo: List<SkCodec.FrameInfo> = codec.codec().getFrameInfo()

    /**
     * Current frame buffer — re-decoded in place by [decodeNextFrame].
     * Sized to match the codec's source [SkImageInfo] (pre-orientation).
     */
    private val rawFrame: SkBitmap = SkBitmap(
        width = codec.codec().getInfo().width,
        height = codec.codec().getInfo().height,
        colorSpace = codec.codec().getInfo().colorSpace,
        colorType = SkColorType.kRGBA_8888,
    )

    /**
     * Post-orientation, post-scale frame buffer — what
     * [getCurrentFrame] / [makePictureSnapshot] expose, sized to
     * [decodeInfo] (the caller's requested logical dimensions).
     */
    private val displayFrame: SkBitmap = SkBitmap(
        width = decodeInfo.width,
        height = decodeInfo.height,
        colorSpace = decodeInfo.colorSpace,
        colorType = SkColorType.kRGBA_8888,
    )

    private var currentFrameIndex: Int = -1
    private var currentDuration: Int = 0
    private var finished: Boolean = false
    private var repetitionCount: Int = codec.codec().getRepetitionCount()
    private var repetitionsCompleted: Int = 0

    init {
        // Skia's contract : "On success, this will decode the first frame."
        decodeFrame(0)
    }

    /**
     * Reset the animation cursor to the beginning. Mirrors
     * `SkAnimatedImage::reset()`.
     */
    public fun reset() {
        finished = false
        repetitionsCompleted = 0
        decodeFrame(0)
    }

    /** Mirrors `SkAnimatedImage::isFinished()`. */
    public fun isFinished(): Boolean = finished

    /**
     * Mirrors `SkAnimatedImage::decodeNextFrame()`. Advances the
     * cursor to the next frame in the animation, decoding it into
     * [rawFrame] / [displayFrame] and returning the duration the
     * caller should display it for (in ms).
     *
     * Returns [kFinished] when the animation has reached its end (per
     * the active [setRepetitionCount]).
     */
    public fun decodeNextFrame(): Int {
        if (finished) return kFinished
        val nextIndex = currentFrameIndex + 1
        if (nextIndex >= frameCount) {
            // Wrap or finish, depending on repetitionCount.
            if (repetitionCount == kRepetitionCountInfinite ||
                repetitionsCompleted < repetitionCount
            ) {
                repetitionsCompleted++
                decodeFrame(0)
                return currentDuration
            }
            finished = true
            currentDuration = kFinished
            return kFinished
        }
        decodeFrame(nextIndex)
        return currentDuration
    }

    /**
     * Mirrors `SkAnimatedImage::getCurrentFrame()`. Returns the
     * post-orientation, post-crop, post-postProcess pixels of the
     * current frame as a fresh [SkImage] snapshot.
     */
    public fun getCurrentFrame(): SkImage = displayFrame.asImage()

    /**
     * Mirrors `SkAnimatedImage::currentFrameDuration()`. Returns the
     * duration the *current* frame should be displayed for, in
     * milliseconds — useful for the first frame, which the
     * constructor already decoded internally.
     *
     * Returns [kFinished] once [decodeNextFrame] has signalled the
     * animation has completed.
     */
    public fun currentFrameDuration(): Int = currentDuration

    /**
     * Mirrors `SkAnimatedImage::setRepetitionCount()`. `0` means
     * "show every frame once and stop" ; [kRepetitionCountInfinite]
     * means "loop forever". Other positive values mean the number of
     * extra full passes after the first.
     */
    public fun setRepetitionCount(count: Int) {
        repetitionCount = count
    }

    /** Mirrors `SkAnimatedImage::getRepetitionCount()`. */
    public fun getRepetitionCount(): Int = repetitionCount

    /** Mirrors `SkAnimatedImage::getFrameCount()`. */
    public fun getFrameCount(): Int = frameCount

    /**
     * Mirrors upstream's `SkAnimatedImage::makePictureSnapshot()`
     * (post-R-final.5 surface). Captures the current frame's draw call
     * into an [SkPicture] sized to the post-crop bounds — useful for
     * GMs that compare raster vs picture playback paths.
     */
    public fun makePictureSnapshot(): SkPicture {
        val recorder = SkPictureRecorder()
        val bounds = SkRect.MakeWH(
            cropRect.width().toFloat(),
            cropRect.height().toFloat(),
        )
        val canvas = recorder.beginRecording(bounds)
        canvas.drawImage(getCurrentFrame(), 0f, 0f)
        return recorder.finishRecordingAsPicture()
    }

    /**
     * Decode `index`'s pixels into [rawFrame] then apply the
     * orientation / scale / crop / post-process pipeline into
     * [displayFrame]. Updates [currentFrameIndex] and [currentDuration]
     * on success.
     */
    private fun decodeFrame(index: Int) {
        val opts = SkCodec.Options(frameIndex = index)
        val srcInfo = codec.codec().getInfo()
        val res = codec.codec().getPixels(srcInfo, rawFrame, opts)
        if (res != SkCodec.Result.kSuccess) {
            finished = true
            currentDuration = kFinished
            return
        }

        // 1) EXIF orientation : re-orient `rawFrame` (encoded grid) into
        //    a pre-scale "natural" buffer.
        val orientedW = if (origin.swapsWidthHeight()) rawFrame.height else rawFrame.width
        val orientedH = if (origin.swapsWidthHeight()) rawFrame.width else rawFrame.height
        val oriented = if (origin == SkEncodedOrigin.kTopLeft) {
            rawFrame
        } else {
            SkBitmap(
                width = orientedW,
                height = orientedH,
                colorSpace = rawFrame.colorSpace,
                colorType = rawFrame.colorType,
            ).also { SkPixmapUtils.Orient(it, rawFrame, origin) }
        }

        // 2) Scale + crop : the GM may pass a `decodeInfo` smaller than
        //    `oriented` (down-sample) and a `cropRect` lying inside it.
        //    Nearest-neighbour scale + crop into `displayFrame` pixels.
        val sx = oriented.width.toFloat() / decodeInfo.width.toFloat()
        val sy = oriented.height.toFloat() / decodeInfo.height.toFloat()
        for (dy in 0 until displayFrame.height) {
            val srcY = ((cropRect.top + dy) * sy).toInt().coerceIn(0, oriented.height - 1)
            for (dx in 0 until displayFrame.width) {
                val srcX = ((cropRect.left + dx) * sx).toInt().coerceIn(0, oriented.width - 1)
                displayFrame.setPixel(dx, dy, oriented.getPixel(srcX, srcY))
            }
        }

        // 3) postProcess Picture (per upstream — drawn over the cropped
        //    frame, typically to add a rounded-rect mask).
        if (postProcess != null) {
            val canvas = SkCanvas(displayFrame)
            postProcess.playback(canvas)
        }

        currentFrameIndex = index
        currentDuration = frameInfo.getOrNull(index)?.durationMs ?: 0
    }

    public companion object {
        /**
         * Sentinel returned by [decodeNextFrame] / [currentFrameDuration]
         * when the animation has run to completion. Mirrors
         * `SkAnimatedImage::kFinished`.
         */
        public const val kFinished: Int = -1

        /**
         * Mirrors `SkCodec::kRepetitionCountInfinite`. Pass to
         * [setRepetitionCount] to keep the animation looping
         * indefinitely.
         */
        public const val kRepetitionCountInfinite: Int = SkCodec.kRepetitionCountInfinite

        /**
         * Mirrors upstream's
         * `SkAnimatedImage::Make(std::unique_ptr<SkAndroidCodec>,
         *  const SkImageInfo& info, SkIRect cropRect,
         *  sk_sp<SkPicture> postProcess)`.
         *
         * Returns `null` if [codec] is empty (zero frames). The
         * caller surrenders ownership of [codec] in upstream — Kotlin
         * has no `unique_ptr` so we just hold the reference ;
         * subsequent calls on the original [SkAndroidCodec] will
         * race against the animator's frame cursor.
         */
        public fun Make(
            codec: SkAndroidCodec,
            info: SkImageInfo,
            cropRect: SkIRect,
            postProcess: SkPicture?,
        ): SkAnimatedImage? {
            if (codec.codec().getFrameCount() <= 0) return null
            return SkAnimatedImage(
                codec = codec,
                origin = codec.codec().getOrigin(),
                decodeInfo = info,
                cropRect = cropRect,
                postProcess = postProcess,
            )
        }

        /**
         * Convenience overload mirroring the upstream "no scaling, no
         * crop, no post-process" factory. The decode info defaults to
         * the codec's natural [SkImageInfo] with EXIF orientation
         * applied (i.e. swapped width/height for a 90° rotation), and
         * the crop rect spans the full frame.
         */
        public fun Make(codec: SkAndroidCodec): SkAnimatedImage? {
            val src = codec.codec().getInfo()
            val origin = codec.codec().getOrigin()
            val info = if (origin.swapsWidthHeight()) src.makeWH(src.height, src.width) else src
            return Make(
                codec = codec,
                info = info,
                cropRect = SkIRect.MakeWH(info.width, info.height),
                postProcess = null,
            )
        }

        /**
         * Convenience that takes an [SkCodec] directly — wraps it in
         * an [SkAndroidCodec] before delegating to [Make]. Mirrors the
         * pattern used by upstream's `gm/animated_image_orientation.cpp`
         * test rig.
         */
        public fun MakeFromCodec(codec: SkCodec): SkAnimatedImage? =
            Make(SkAndroidCodec.MakeFromCodec(codec))
    }
}
