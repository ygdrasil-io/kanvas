package org.skia.core

import org.skia.math.SkRect

/**
 * Mirrors Skia's
 * [`GrDeferredDisplayListRecorder`](https://github.com/google/skia/blob/main/include/private/chromium/GrDeferredDisplayListRecorder.h)
 * — recording-side companion to [SkDeferredDisplayList].
 *
 * **Lifecycle** :
 * ```
 * val recorder = SkDeferredDisplayListRecorder(characterization)
 * val canvas = recorder.getCanvas()!!
 * gm.draw(canvas)                          // any normal canvas calls
 * val ddl = recorder.detach()              // immutable handoff
 * surface.draw(ddl)                        // characterization-gated playback
 * ```
 *
 * Calling [getCanvas] after [detach] returns `null` — once snapped,
 * the recording is sealed and the recorder is single-shot. Matches
 * upstream's contract.
 *
 * **Implementation note** — we delegate the recording to
 * [SkPictureRecorder] verbatim ; the only real addition is the
 * [SkSurfaceCharacterization] compatibility lock that follows the
 * resulting DDL into [SkSurface.draw]. The "thread-safe parallel
 * recording across tiles" use case is honoured by the API but
 * single-threaded under the hood (raster-only, no concurrent GPU
 * state).
 */
public class SkDeferredDisplayListRecorder(
    public val characterization: SkSurfaceCharacterization,
) {
    private val recorder: SkPictureRecorder = SkPictureRecorder()
    private val recordingCanvas: SkCanvas = recorder.beginRecording(
        SkRect.MakeWH(
            characterization.width.toFloat(),
            characterization.height.toFloat(),
        ),
    )
    private var detached: Boolean = false

    /**
     * The canvas that records draws into the in-flight DDL. Returns
     * `null` after [detach] (the recording is sealed). Multiple
     * calls before [detach] return the same instance.
     *
     * Mirrors `GrDeferredDisplayListRecorder::getCanvas()`.
     */
    public fun getCanvas(): SkCanvas? = if (detached) null else recordingCanvas

    /**
     * Snap the in-flight recording into an immutable
     * [SkDeferredDisplayList]. After this call the recorder is
     * sealed — [getCanvas] returns `null` and a second [detach]
     * throws. Matches upstream's `GrDeferredDisplayListRecorder::detach()`.
     */
    public fun detach(): SkDeferredDisplayList {
        check(!detached) { "SkDeferredDisplayListRecorder.detach already called" }
        detached = true
        val picture = recorder.finishRecordingAsPicture()
        return SkDeferredDisplayList(characterization, picture)
    }
}
