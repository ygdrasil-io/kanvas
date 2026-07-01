package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.pipeline.ClipOp

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
    data class DeviceRect(val rect: org.graphiks.kanvas.types.Rect, val antiAlias: Boolean = true) : ClipStack

    /** Clipping to a list of clip operations (paths, round-rects, etc.). */
    data class Complex(val ops: List<ClipStackOp>) : ClipStack

    val isEmpty: Boolean get() = when (this) {
        WideOpen -> false
        is DeviceRect -> rect.isEmpty
        is Complex -> false
    }

    val isRect: Boolean get() = this is DeviceRect
}

/**
 * A single clip operation within a [ClipStack.Complex] stack.
 *
 * Each operation pairs a geometric shape with a [ClipOp] that specifies
 * whether it intersects with or replaces the prior clip.
 */
sealed interface ClipStackOp {
    val antiAlias: Boolean
    /** Axis-aligned rectangle clip operation. */
    data class RectOp(val rect: org.graphiks.kanvas.types.Rect, val op: ClipOp, override val antiAlias: Boolean = true) : ClipStackOp

    /** Rounded-rectangle clip operation. */
    data class RRectOp(val rrect: org.graphiks.kanvas.types.RRect, val op: ClipOp, override val antiAlias: Boolean = true) : ClipStackOp

    /** Arbitrary path clip operation. */
    data class PathOp(val path: org.graphiks.kanvas.geometry.Path, val op: ClipOp, override val antiAlias: Boolean = true) : ClipStackOp
}
