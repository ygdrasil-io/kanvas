package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.types.*

/**
 * A immediate-mode style 2D drawing surface that records drawing operations into
 * a [DisplayListBuffer] for deferred playback.
 *
 * [Canvas] manages a stack of transform and clip state, offering a Skia-like API
 * for drawing rectangles, paths, images, and text. All operations are appended to
 * the internal buffer for subsequent rendering by a pipeline consumer.
 */
class Canvas internal constructor(private val buffer: DisplayListBuffer) {
    private var currentTransform = Matrix33.identity()
    private var currentClip: ClipStack = ClipStack.WideOpen
    private var saveStack = mutableListOf<CanvasState>()

    /** The current transform matrix. */
    val matrix: Matrix33 get() = currentTransform

    /** The number of states on the save stack. */
    val saveCount: Int get() = saveStack.size

    /**
     * The local clip bounds, expressed in the current coordinate system.
     *
     * Returns an infinite rect when the clip is wide-open, the device rect when
     * clipping to a single axis-aligned rectangle, or [Rect.EMPTY] for complex
     * clip stacks.
     */
    val localClipBounds: Rect
        get() = when (val clip = currentClip) {
            ClipStack.WideOpen -> Rect.fromLTRB(Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY)
            is ClipStack.DeviceRect -> clip.rect
            is ClipStack.Complex -> Rect.EMPTY
        }

    /** Draw an axis-aligned rectangle filled/stroked with [paint]. */
    fun drawRect(rect: Rect, paint: Paint) {
        buffer.append(DisplayOp.DrawRect(rect, paint, currentTransform, currentClip))
    }

    /** Draw a rounded rectangle filled/stroked with [paint]. */
    fun drawRRect(rrect: RRect, paint: Paint) {
        buffer.append(DisplayOp.DrawRRect(rrect, paint, currentTransform, currentClip))
    }

    /** Draw an arbitrary [path] filled/stroked with [paint]. */
    fun drawPath(path: Path, paint: Paint) {
        buffer.append(DisplayOp.DrawPath(path, paint, currentTransform, currentClip))
    }

    /**
     * Draw an [image] scaled to fill [dst].
     *
     * @param paint Optional [Paint] for alpha modulation or color filtering.
     */
    fun drawImage(image: Image, dst: Rect, paint: Paint? = null) {
        val src = Rect.fromLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat())
        buffer.append(DisplayOp.DrawImage(image, src, dst, paint, currentTransform, currentClip))
    }

    /**
     * Draw a sub-region [src] of [image] scaled to fill [dst].
     *
     * @param paint Optional [Paint] for alpha modulation or color filtering.
     */
    fun drawImageRect(image: Image, src: Rect, dst: Rect, paint: Paint? = null) {
        buffer.append(DisplayOp.DrawImage(image, src, dst, paint, currentTransform, currentClip))
    }

    /** Draw a [TextBlob] at the given position with [paint]. */
    fun drawText(blob: TextBlob, x: Float, y: Float, paint: Paint) {
        buffer.append(DisplayOp.DrawText(blob, x, y, paint, currentTransform, currentClip))
    }

    /**
     * Save the current transform and clip state onto the stack.
     *
     * @return The new save count (depth of the save stack).
     */
    fun save(): Int {
        saveStack.add(CanvasState(currentTransform, currentClip))
        return saveStack.size
    }

    /**
     * Save state and begin a new layer (offscreen render target).
     *
     * @param bounds Optional bounds for the offscreen surface.
     * @param paint  Optional [Paint] to apply when compositing the layer back.
     * @return The new save count.
     */
    fun saveLayer(bounds: Rect? = null, paint: Paint? = null): Int {
        buffer.append(DisplayOp.BeginLayer(bounds, paint))
        saveStack.add(CanvasState(currentTransform, currentClip))
        return saveStack.size
    }

    /** Restore the most recently saved state and end the current layer if one was active. */
    fun restore() {
        if (saveStack.isNotEmpty()) {
            val state = saveStack.removeLast()
            currentTransform = state.transform
            currentClip = state.clip
        }
        buffer.append(DisplayOp.EndLayer)
    }

    /**
     * Repeatedly [restore] until the save stack depth reaches [count].
     *
     * Has no effect if [count] is greater than or equal to [saveCount].
     */
    fun restoreToCount(count: Int) {
        while (saveStack.size > count) restore()
    }

    /** Pre-concatenate a translation by (x, y) into the current transform. */
    fun translate(x: Float, y: Float) { concat(Matrix33.translate(x, y)) }

    /** Pre-concatenate a scale by (sx, sy) into the current transform. */
    fun scale(sx: Float, sy: Float) { concat(Matrix33.scale(sx, sy)) }

    /**
     * Pre-concatenate a rotation of [degrees] about an optional pivot point (px, py).
     *
     * When the pivot is omitted the rotation is about the origin.
     */
    fun rotate(degrees: Float, px: Float = 0f, py: Float = 0f) {
        if (px == 0f && py == 0f) { concat(Matrix33.rotate(degrees)) }
        else { translate(px, py); concat(Matrix33.rotate(degrees)); translate(-px, -py) }
    }

    /** Pre-concatenate a skew by (sx, sy) into the current transform. */
    fun skew(sx: Float, sy: Float) { concat(Matrix33.skew(sx, sy)) }

    /**
     * Pre-concatenate [matrix] with the current transform.
     *
     * The new transform becomes `currentTransform * matrix`.
     */
    fun concat(matrix: Matrix33) {
        currentTransform = currentTransform * matrix
        buffer.append(DisplayOp.SetTransform(currentTransform))
    }

    /** Replace the current transform with [matrix]. */
    fun setMatrix(matrix: Matrix33) {
        currentTransform = matrix
        buffer.append(DisplayOp.SetTransform(currentTransform))
    }

    /** Reset the current transform to the identity matrix. */
    fun resetMatrix() { setMatrix(Matrix33.identity()) }

    /**
     * Replace the current clip with an axis-aligned rectangle.
     *
     * @param antiAlias Whether the clip edges should be anti-aliased.
     */
    fun clipRect(rect: Rect, op: ClipOp = ClipOp.INTERSECT, antiAlias: Boolean = true) {
        currentClip = ClipStack.DeviceRect(rect)
        buffer.append(DisplayOp.SetClip(currentClip))
    }

    /**
     * Replace the current clip with a rounded rectangle.
     *
     * @param antiAlias Whether the clip edges should be anti-aliased.
     */
    fun clipRRect(rrect: RRect, op: ClipOp = ClipOp.INTERSECT, antiAlias: Boolean = true) {
        currentClip = ClipStack.Complex(listOf(ClipStackOp.RRect(rrect, op)))
        buffer.append(DisplayOp.SetClip(currentClip))
    }

    /**
     * Replace the current clip with an arbitrary [path].
     *
     * @param antiAlias Whether the clip edges should be anti-aliased.
     */
    fun clipPath(path: Path, op: ClipOp = ClipOp.INTERSECT, antiAlias: Boolean = true) {
        currentClip = ClipStack.Complex(listOf(ClipStackOp.Path(path, op)))
        buffer.append(DisplayOp.SetClip(currentClip))
    }

    private data class CanvasState(val transform: Matrix33, val clip: ClipStack)
}
