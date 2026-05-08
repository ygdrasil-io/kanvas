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
 *
 * **Phase Q3 — bounding-box hierarchy** : [beginRecording] accepts an
 * optional [SkBBHFactory] (typically [SkRTreeFactory]). When supplied,
 * [finishRecordingAsPicture] computes per-op device-space bounds via
 * [SkPictureBoundsBuilder], bulk-loads them into the factory's
 * hierarchy, and bakes the hierarchy into the picture. Subsequent
 * playbacks under sub-rect clips skip non-intersecting ops in
 * `O(log N + K)` instead of walking every record.
 */
public class SkPictureRecorder {
    private var activeCanvas: SkRecordingCanvas? = null
    private var activeBounds: SkRect = SkRect.MakeEmpty()
    private var activeRecords: MutableList<SkRecord>? = null
    private var activeBBHFactory: SkBBHFactory? = null

    /**
     * Begin a new recording sized at `width × height`. Returns the
     * recording [SkCanvas] to drive the GM / draw code with. The
     * returned canvas is owned by this recorder — do not reuse it
     * after [finishRecordingAsPicture].
     *
     * If [bbhFactory] is non-null, [finishRecordingAsPicture] will
     * build a bounding-box hierarchy from the recorded ops and bake
     * it into the resulting picture for fast cull queries at
     * playback time.
     *
     * Mirrors Skia's `SkPictureRecorder::beginRecording(width, height,
     * SkBBHFactory*)`.
     */
    public fun beginRecording(
        width: Float,
        height: Float,
        bbhFactory: SkBBHFactory? = null,
    ): SkCanvas {
        val w = maxOf(1, ceil(width.toDouble()).toInt())
        val h = maxOf(1, ceil(height.toDouble()).toInt())
        val records = mutableListOf<SkRecord>()
        val canvas = SkRecordingCanvas(w, h, records)
        activeCanvas = canvas
        activeRecords = records
        activeBounds = SkRect.MakeWH(width, height)
        activeBBHFactory = bbhFactory
        return canvas
    }

    /**
     * Convenience overload taking an [SkRect] for the cull rect — the
     * recorded picture's [SkPicture.cullRect] will reflect this exact
     * value (not snapped to the bitmap dummy's integer dimensions).
     *
     * Mirrors Skia's `SkPictureRecorder::beginRecording(SkRect,
     * SkBBHFactory*)`.
     */
    public fun beginRecording(
        bounds: SkRect,
        bbhFactory: SkBBHFactory? = null,
    ): SkCanvas {
        val canvas = beginRecording(bounds.width(), bounds.height(), bbhFactory)
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
     * If the recording was started with an [SkBBHFactory], we walk
     * the recorded ops once via [SkPictureBoundsBuilder] to compute
     * per-op bounds, then bulk-load the factory's hierarchy. The
     * resulting [SkPicture] carries the hierarchy and uses it for
     * playback culling.
     *
     * Mirrors Skia's `SkPictureRecorder::finishRecordingAsPicture()`.
     */
    public fun finishRecordingAsPicture(): SkPicture {
        val records = activeRecords?.toList() ?: emptyList()
        val bounds = activeBounds
        val factory = activeBBHFactory
        activeCanvas = null
        activeRecords = null
        activeBounds = SkRect.MakeEmpty()
        activeBBHFactory = null

        val bbh: SkBBoxHierarchy? = if (factory != null && records.isNotEmpty()) {
            val opBounds = SkPictureBoundsBuilder.build(records, bounds)
            factory.create().also { it.insert(opBounds, opBounds.size) }
        } else null

        return SkPicture(bounds, records, bbh)
    }
}
