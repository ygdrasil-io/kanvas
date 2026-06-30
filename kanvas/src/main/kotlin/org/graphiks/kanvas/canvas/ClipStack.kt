package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.geometry.Path as GeometryPath
import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.types.RRect as TypesRRect
import org.graphiks.kanvas.types.Rect as TypesRect

/**
 * Represents the current clipping state of a [Canvas].
 *
 * Clipping is modelled as a stack that transitions from wide-open through
 * device-rect to complex (multi-op) forms as non-rectangular clips are applied.
 */
sealed interface ClipStack {
    /** No clipping applied; every pixel is visible. */
    data object WideOpen : ClipStack

    /** Clipping to a single axis-aligned device rectangle. */
    data class DeviceRect(val rect: TypesRect) : ClipStack

    /** Clipping to a list of clip operations (paths, round-rects, etc.). */
    data class Complex(val ops: List<ClipStackOp>) : ClipStack
}

/**
 * A single clip operation within a [ClipStack.Complex] stack.
 *
 * Each operation pairs a geometric shape with a [ClipOp] that specifies
 * whether it intersects with or replaces the prior clip.
 */
sealed interface ClipStackOp {
    /** Axis-aligned rectangle clip operation. */
    data class Rect(val rect: TypesRect, val op: ClipOp) : ClipStackOp

    /** Rounded-rectangle clip operation. */
    data class RRect(val rrect: TypesRRect, val op: ClipOp) : ClipStackOp

    /** Arbitrary path clip operation. */
    data class Path(val path: GeometryPath, val op: ClipOp) : ClipStackOp
}
