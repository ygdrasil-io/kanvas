package org.skia.foundation

import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkScalar
import org.skia.math.SkVector

/**
 * Convenience wrapper around [SkContourMeasureIter] that exposes a
 * single-contour-at-a-time view. Mirrors Skia's
 * [`SkPathMeasure`](https://github.com/google/skia/blob/main/include/core/SkPathMeasure.h).
 *
 * Construct with a path and `forceClosed` flag; the wrapper
 * immediately advances to the first contour. Call [nextContour] to
 * walk forward through the remaining contours. All position/tangent/
 * matrix/segment queries delegate to the current [SkContourMeasure].
 */
public class SkPathMeasure {

    private val iter: SkContourMeasureIter = SkContourMeasureIter()
    private var contour: SkContourMeasure? = null

    /** Empty measure — `getLength` returns 0 until [setPath] is called. */
    public constructor()

    /**
     * Build over [path] with the given `forceClosed` and `resScale`.
     * Mirrors the same-named C++ constructor.
     */
    public constructor(path: SkPath, forceClosed: Boolean, resScale: SkScalar = 1f) {
        iter.reset(path, forceClosed, resScale)
        contour = iter.next()
    }

    /**
     * Re-target this measure to a new path (or detach from any path
     * when `path` is `null`). Mirrors `SkPathMeasure::setPath`.
     */
    public fun setPath(path: SkPath?, forceClosed: Boolean) {
        iter.reset(path ?: SkPathBuilder().detach(), forceClosed)
        contour = iter.next()
    }

    /** Returns the current contour's length, or `0` when none. */
    public fun getLength(): SkScalar = contour?.length() ?: 0f

    /** Delegates to [SkContourMeasure.getPosTan]; returns `false` when no contour. */
    public fun getPosTan(distance: SkScalar, position: SkPoint?, tangent: SkVector?): Boolean =
        contour?.getPosTan(distance, position, tangent) ?: false

    /** Delegates to [SkContourMeasure.getMatrix]; returns `false` when no contour. */
    public fun getMatrix(
        distance: SkScalar,
        matrix: Array<SkMatrix?>,
        flags: SkContourMeasure.MatrixFlags = SkContourMeasure.MatrixFlags.kGetPosAndTan_MatrixFlag,
    ): Boolean = contour?.getMatrix(distance, matrix, flags) ?: false

    /** Delegates to [SkContourMeasure.getSegment]; returns `false` when no contour. */
    public fun getSegment(
        startD: SkScalar,
        stopD: SkScalar,
        dst: SkPathBuilder,
        startWithMoveTo: Boolean,
    ): Boolean = contour?.getSegment(startD, stopD, dst, startWithMoveTo) ?: false

    /** Returns `true` if the current contour is closed. */
    public fun isClosed(): Boolean = contour?.isClosed() ?: false

    /**
     * Advance to the next contour of the path. Returns `true` on
     * success (there is now a current contour), `false` when the
     * path is exhausted (no current contour).
     */
    public fun nextContour(): Boolean {
        contour = iter.next()
        return contour != null
    }

    /** The current contour, or `null` when the path is exhausted. */
    public fun currentMeasure(): SkContourMeasure? = contour
}
