package org.skia.core

import org.skia.math.SkRect
import kotlin.math.ceil

/**
 * Mirrors Skia's
 * [`SkPictureRecorder`](https://github.com/google/skia/blob/main/include/core/SkPictureRecorder.h)
 * — a one-shot factory that produces a [SkCanvas] for recording, then
 * finalises the recorded ops as an immutable [SkPicture].
 *
 * Lifecycle :
 * ```
 * val recorder = SkPictureRecorder()
 * val canvas = recorder.beginRecording(800f, 600f)
 * gm.onDraw(canvas)                          // any normal canvas calls
 * val picture = recorder.finishRecordingAsPicture()
 * picture.playback(realCanvas)               // replay anywhere
 * ```
 *
 * Calling [beginRecording] twice on the same recorder without an
 * intervening [finishRecordingAsPicture] resets the in-flight recording
 * (matches Skia's contract — the previous record list is discarded).
 * Calling [finishRecordingAsPicture] without an active recording
 * returns an empty picture sized at `(0, 0, 0, 0)`.
 */
public class SkPictureRecorder {
    private var activeCanvas: SkRecordingCanvas? = null
    private var activeBounds: SkRect = SkRect.MakeEmpty()
    private var activeRecords: MutableList<SkRecord>? = null

    /**
     * Begin a new recording sized at `width × height`. Returns the
     * recording [SkCanvas] to drive the GM / draw code with. The
     * returned canvas is owned by this recorder — do not reuse it
     * after [finishRecordingAsPicture].
     *
     * Mirrors Skia's `SkPictureRecorder::beginRecording(width, height)`.
     */
    public fun beginRecording(width: Float, height: Float): SkCanvas {
        val w = maxOf(1, ceil(width.toDouble()).toInt())
        val h = maxOf(1, ceil(height.toDouble()).toInt())
        val records = mutableListOf<SkRecord>()
        val canvas = SkRecordingCanvas(w, h, records)
        activeCanvas = canvas
        activeRecords = records
        activeBounds = SkRect.MakeWH(width, height)
        return canvas
    }

    /**
     * Convenience overload taking an [SkRect] for the cull rect — the
     * recorded picture's [SkPicture.cullRect] will reflect this exact
     * value (not snapped to the bitmap dummy's integer dimensions).
     *
     * Mirrors Skia's `SkPictureRecorder::beginRecording(SkRect)`.
     */
    public fun beginRecording(bounds: SkRect): SkCanvas {
        val canvas = beginRecording(bounds.width(), bounds.height())
        activeBounds = bounds
        return canvas
    }

    /**
     * Mirrors Skia's `SkPictureRecorder::getRecordingCanvas()`. Returns
     * `null` if no recording is in flight.
     */
    public fun getRecordingCanvas(): SkCanvas? = activeCanvas

    /**
     * Finalise the in-flight recording into an immutable [SkPicture].
     * After this call the recorder is reset and may be reused via a
     * fresh [beginRecording]. Calling without an active recording
     * returns an empty picture (cull rect `(0, 0, 0, 0)`).
     *
     * Mirrors Skia's `SkPictureRecorder::finishRecordingAsPicture()`.
     */
    public fun finishRecordingAsPicture(): SkPicture {
        val records = activeRecords
        val bounds = activeBounds
        activeCanvas = null
        activeRecords = null
        activeBounds = SkRect.MakeEmpty()
        return SkPicture(bounds, records?.toList() ?: emptyList())
    }
}
