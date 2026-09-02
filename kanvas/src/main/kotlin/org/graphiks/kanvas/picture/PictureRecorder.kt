package org.graphiks.kanvas.picture

import org.graphiks.kanvas.canvas.Canvas
import org.graphiks.kanvas.canvas.DisplayListBuffer
import org.graphiks.kanvas.canvas.SnapshotDisplayListBuffer
import org.graphiks.math.geometry.RectF32

/**
 * Records drawing commands into a [Picture].
 *
 * Usage:
 * ```kotlin
 * val recorder = PictureRecorder()
 * val canvas = recorder.beginRecording(RectF32.ofLTRB(0, 0, 100, 100))
 * canvas.drawRect(..., paint)
 * val picture = recorder.finishRecordingAsPicture()
 * ```
 */
class PictureRecorder {
    private var activeCanvas: Canvas? = null
    private var activeBuffer: DisplayListBuffer? = null
    private var recordingBounds: RectF32? = null

    /**
     * Begin recording drawing commands for a picture with the given [bounds].
     *
     * [bounds] serves as a cull hint — the returned canvas will clip to these
     * bounds. The picture's [Picture.cullRect] is set to [bounds].
     *
     * @throws IllegalStateException if a recording is already in progress
     */
    fun beginRecording(bounds: RectF32): Canvas {
        check(activeCanvas == null) { "Recording already in progress" }
        val buffer = SnapshotDisplayListBuffer()
        val canvas = Canvas(buffer)
        canvas.clipRect(bounds)
        activeBuffer = buffer
        activeCanvas = canvas
        recordingBounds = RectF32(bounds.left, bounds.top, bounds.right, bounds.bottom)
        return canvas
    }

    /**
     * Complete the current recording and return the resulting [Picture].
     *
     * @throws IllegalStateException if no recording is in progress
     */
    fun finishRecordingAsPicture(): Picture {
        val buffer = activeBuffer ?: throw IllegalStateException("No recording in progress")
        val bounds = recordingBounds ?: throw IllegalStateException("No recording bounds")
        val picture = Picture(bounds, buffer.ops())
        activeCanvas = null
        activeBuffer = null
        recordingBounds = null
        return picture
    }
}
