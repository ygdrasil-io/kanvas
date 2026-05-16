package org.skia.core

import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect
import java.util.concurrent.atomic.AtomicInteger

/**
 * Mirrors Skia's
 * [`SkDrawable`](https://github.com/google/skia/blob/main/include/core/SkDrawable.h)
 * — base class for arbitrary objects that draw into an [SkCanvas].
 *
 * **Use case** : a thin extension slot for callers that want to encapsulate
 * a piece of complex draw logic (e.g. a stateful animation, a third-party
 * widget, a heavy GM helper) and pass it around as a value before
 * deciding *where* it gets drawn. The object's [draw] method preserves
 * the canvas's matrix / clip / save-stack — semantically a "scoped sub-
 * picture" without needing to record into an [SkPicture] first.
 *
 * **Subclassing** : implement [onDraw] (mandatory) and optionally
 * [onGetBounds] for callers that want a conservative bounding box
 * (e.g. for the [SkBBoxHierarchy] cull integration). The default
 * [onGetBounds] returns an empty rect — meaning "I have no useful bound,
 * cull pessimistically".
 *
 * **Thread-safety** : not thread-safe. The [generationId] increments
 * via an `AtomicInteger` only to keep `notifyDrawingChanged` cheap
 * across threads ; the [draw] / [onDraw] paths are caller-driven and
 * inherit the canvas's threading constraints (i.e. single-threaded).
 *
 * **Generation ID** : every [SkDrawable] starts with a unique id
 * derived from a process-wide [AtomicInteger]. Subclasses whose
 * internal state mutates (so the next [draw] would render
 * differently) call [notifyDrawingChanged] to invalidate downstream
 * caches. Mirrors upstream's `getGenerationID` /
 * `notifyDrawingChanged` contract.
 */
public abstract class SkDrawable protected constructor() {

    private var genId: Int = NEXT_ID.incrementAndGet()

    /**
     * Subclasses override : draw the object into [canvas]. Called
     * inside the [draw] save / restore wrapper, so the implementation
     * is free to mutate the canvas's matrix / clip — they are reset
     * automatically on return.
     */
    protected abstract fun onDraw(canvas: SkCanvas)

    /**
     * Subclasses may override : conservative device-space bounds
     * the drawable might touch. The default returns an empty rect —
     * use it to keep the contract simple when bounds aren't trivial
     * to compute. Mirrors upstream's `onGetBounds`.
     */
    protected open fun onGetBounds(): SkRect = SkRect.MakeEmpty()

    /**
     * Public bounds query. Forwards to [onGetBounds].
     */
    public fun getBounds(): SkRect = onGetBounds()

    /**
     * Process-unique generation id. Two calls returning the same value
     * imply the next [draw] will render identically. Subclasses
     * mutating state must call [notifyDrawingChanged] to invalidate.
     */
    public fun getGenerationID(): Int = genId

    /**
     * Invalidate the [generationId] — call after any state change
     * that would alter the next [draw] output. Mirrors upstream's
     * `notifyDrawingChanged`.
     */
    public fun notifyDrawingChanged() {
        genId = NEXT_ID.incrementAndGet()
    }

    /**
     * Draw the object into [canvas]. The canvas's save count, matrix
     * and clip are guaranteed to match the entry state on return —
     * we wrap the call in `save` / `restore`, optionally pre-
     * concatenating [matrix] when non-null.
     *
     * Mirrors Skia's `SkDrawable::draw(SkCanvas*, const SkMatrix*)`.
     */
    public fun draw(canvas: SkCanvas, matrix: SkMatrix? = null) {
        val saveCount = canvas.getSaveCount()
        canvas.save()
        try {
            if (matrix != null) canvas.concat(matrix)
            onDraw(canvas)
        } finally {
            canvas.restoreToCount(saveCount)
        }
    }

    /**
     * Convenience overload — translates by `(x, y)` before drawing.
     * Mirrors `SkDrawable::draw(SkCanvas*, SkScalar, SkScalar)`.
     */
    public fun draw(canvas: SkCanvas, x: Float, y: Float) {
        draw(canvas, SkMatrix.MakeTrans(x, y))
    }

    public companion object {
        /**
         * Process-wide unique-id source for [SkDrawable.genId]. Mirrors
         * Skia's static counter ; we use [AtomicInteger] for a free
         * thread-safe lazy initialization (the increments themselves
         * are infrequent — only called on construct + notifyDrawingChanged).
         */
        private val NEXT_ID: AtomicInteger = AtomicInteger(0)
    }
}
