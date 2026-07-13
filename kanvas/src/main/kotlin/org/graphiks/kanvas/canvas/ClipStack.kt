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

    /** True when a clip was captured under perspective and must refuse the affine GPU route. */
    val perspectiveCaptureRefusal: Boolean get() = when (this) {
        WideOpen,
        is DeviceRect -> false
        is Complex -> ops.any(ClipStackOp::perspectiveCaptureRefusal)
    }
}

/**
 * A single clip operation within a [ClipStack.Complex] stack.
 *
 * Each operation pairs a geometric shape with a [ClipOp] that specifies
 * whether it intersects with or replaces the prior clip.
 */
sealed interface ClipStackOp {
    val antiAlias: Boolean
    /** Preserves the capture-time perspective refusal after later CTM changes. */
    val perspectiveCaptureRefusal: Boolean
    /** Axis-aligned rectangle clip operation. */
    data class RectOp(
        val rect: org.graphiks.kanvas.types.Rect,
        val op: ClipOp,
        override val antiAlias: Boolean = true,
        override val perspectiveCaptureRefusal: Boolean = false,
    ) : ClipStackOp

    /** Rounded-rectangle clip operation. */
    data class RRectOp(
        val rrect: org.graphiks.kanvas.types.RRect,
        val op: ClipOp,
        override val antiAlias: Boolean = true,
        override val perspectiveCaptureRefusal: Boolean = false,
    ) : ClipStackOp

    /** Arbitrary path clip operation. */
    data class PathOp(
        val path: org.graphiks.kanvas.geometry.Path,
        val op: ClipOp,
        override val antiAlias: Boolean = true,
        override val perspectiveCaptureRefusal: Boolean = false,
    ) : ClipStackOp
}

/**
 * Returns the exact intersection of this stack followed by [other].
 *
 * Two device rectangles retain their compact representation only when their anti-aliasing is
 * identical. Any path, rounded-rectangle, mixed-AA rectangle, or difference operation remains
 * ordered in a complex stack so callers do not reduce
 * non-rectangular geometry to a bounding rectangle while replaying or restoring a layer.
 */
internal fun ClipStack.intersectWith(other: ClipStack?): ClipStack = when (other) {
    null,
    ClipStack.WideOpen,
    -> this
    else -> when (this) {
        ClipStack.WideOpen -> other
        is ClipStack.DeviceRect -> when (other) {
            is ClipStack.DeviceRect -> if (antiAlias == other.antiAlias) {
                ClipStack.DeviceRect(
                    org.graphiks.kanvas.types.Rect.fromLTRB(
                        maxOf(rect.left, other.rect.left),
                        maxOf(rect.top, other.rect.top),
                        minOf(rect.right, other.rect.right),
                        minOf(rect.bottom, other.rect.bottom),
                    ),
                    antiAlias,
                )
            } else {
                ClipStack.Complex(
                    listOf(
                        ClipStackOp.RectOp(rect, ClipOp.INTERSECT, antiAlias),
                        ClipStackOp.RectOp(other.rect, ClipOp.INTERSECT, other.antiAlias),
                    ),
                )
            }
            is ClipStack.Complex -> ClipStack.Complex(
                listOf(ClipStackOp.RectOp(rect, ClipOp.INTERSECT, antiAlias)) + other.asIntersectionOps(),
            )
            ClipStack.WideOpen -> this
        }
        is ClipStack.Complex -> ClipStack.Complex(ops + other.asIntersectionOps())
    }
}

private fun ClipStack.asIntersectionOps(): List<ClipStackOp> = when (this) {
    ClipStack.WideOpen -> emptyList()
    is ClipStack.DeviceRect -> listOf(ClipStackOp.RectOp(rect, ClipOp.INTERSECT, antiAlias))
    is ClipStack.Complex -> ops
}
