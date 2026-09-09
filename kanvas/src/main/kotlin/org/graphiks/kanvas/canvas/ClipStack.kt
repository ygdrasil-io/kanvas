package org.graphiks.kanvas.canvas

import org.graphiks.kanvas.pipeline.ClipOp
import org.graphiks.kanvas.render.ir.ClipTransformSnapshot
import org.graphiks.math.matrix.Matrix3x3F32

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
    data class DeviceRect(val rect: org.graphiks.math.geometry.RectF32, val antiAlias: Boolean = true) : ClipStack

    /** Clipping to a list of clip operations (paths, round-rects, etc.). */
    data class Complex(val ops: List<ClipStackOp>) : ClipStack

    val isEmpty: Boolean get() = when (this) {
        WideOpen -> false
        is DeviceRect -> rect.isEmpty
        is Complex -> false
    }

    val isRect: Boolean get() = this is DeviceRect

    /** True only for a historical clip whose unavailable transform recorded perspective refusal. */
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
    /** Capture-time transform authority, retained with source geometry. */
    val transform: ClipTransformSnapshot

    /** Compatibility fact retained solely for historical v8 payloads. */
    val perspectiveCaptureRefusal: Boolean
        get() = (transform as? ClipTransformSnapshot.LegacyUnavailable)?.perspectiveCaptureRefusal == true

    /** Compatibility metadata for historical consumers; typed transforms never classify by string. */
    val transformClass: String
        get() = (transform as? ClipTransformSnapshot.LegacyUnavailable)?.transformClass ?: "typed-snapshot"

    /** Axis-aligned rectangle clip operation. */
    data class RectOp(
        val rect: org.graphiks.math.geometry.RectF32,
        val op: ClipOp,
        override val antiAlias: Boolean = true,
        override val transform: ClipTransformSnapshot = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
    ) : ClipStackOp {
        /** Historical constructor; new captures must provide [transform] instead. */
        constructor(
            rect: org.graphiks.math.geometry.RectF32,
            op: ClipOp,
            antiAlias: Boolean = true,
            transformClass: String,
        ) : this(rect, op, antiAlias, legacyClipTransform(transformClass, false))

        /** Historical constructor; new captures must provide [transform] instead. */
        constructor(
            rect: org.graphiks.math.geometry.RectF32,
            op: ClipOp,
            antiAlias: Boolean = true,
            perspectiveCaptureRefusal: Boolean,
        ) : this(rect, op, antiAlias, legacyClipTransform(if (perspectiveCaptureRefusal) "perspective" else "identity", perspectiveCaptureRefusal))

        /** Historical constructor; new captures must provide [transform] instead. */
        constructor(
            rect: org.graphiks.math.geometry.RectF32,
            op: ClipOp,
            antiAlias: Boolean,
            perspectiveCaptureRefusal: Boolean,
            transformClass: String,
        ) : this(rect, op, antiAlias, legacyClipTransform(transformClass, perspectiveCaptureRefusal))
    }

    /** Rounded-rectangle clip operation. */
    data class RRectOp(
        val rrect: org.graphiks.math.geometry.RRectF32,
        val op: ClipOp,
        override val antiAlias: Boolean = true,
        override val transform: ClipTransformSnapshot = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
    ) : ClipStackOp {
        /** Historical constructor; new captures must provide [transform] instead. */
        constructor(
            rrect: org.graphiks.math.geometry.RRectF32,
            op: ClipOp,
            antiAlias: Boolean = true,
            transformClass: String,
        ) : this(rrect, op, antiAlias, legacyClipTransform(transformClass, false))

        /** Historical constructor; new captures must provide [transform] instead. */
        constructor(
            rrect: org.graphiks.math.geometry.RRectF32,
            op: ClipOp,
            antiAlias: Boolean,
            perspectiveCaptureRefusal: Boolean,
            transformClass: String,
        ) : this(rrect, op, antiAlias, legacyClipTransform(transformClass, perspectiveCaptureRefusal))
    }

    /** Arbitrary path clip operation. */
    data class PathOp(
        val path: org.graphiks.kanvas.geometry.Path,
        val op: ClipOp,
        override val antiAlias: Boolean = true,
        override val transform: ClipTransformSnapshot = ClipTransformSnapshot.Known.of(Matrix3x3F32.Identity),
    ) : ClipStackOp {
        /** Historical constructor; new captures must provide [transform] instead. */
        constructor(
            path: org.graphiks.kanvas.geometry.Path,
            op: ClipOp,
            antiAlias: Boolean = true,
            transformClass: String,
        ) : this(path, op, antiAlias, legacyClipTransform(transformClass, false))

        /** Historical constructor; new captures must provide [transform] instead. */
        constructor(
            path: org.graphiks.kanvas.geometry.Path,
            op: ClipOp,
            antiAlias: Boolean = true,
            perspectiveCaptureRefusal: Boolean,
        ) : this(path, op, antiAlias, legacyClipTransform(if (perspectiveCaptureRefusal) "perspective" else "identity", perspectiveCaptureRefusal))

        /** Historical constructor; new captures must provide [transform] instead. */
        constructor(
            path: org.graphiks.kanvas.geometry.Path,
            op: ClipOp,
            antiAlias: Boolean,
            perspectiveCaptureRefusal: Boolean,
            transformClass: String,
        ) : this(path, op, antiAlias, legacyClipTransform(transformClass, perspectiveCaptureRefusal))
    }
}

private fun legacyClipTransform(
    transformClass: String,
    perspectiveCaptureRefusal: Boolean,
): ClipTransformSnapshot.LegacyUnavailable = ClipTransformSnapshot.LegacyUnavailable(
    transformClass = transformClass,
    perspectiveCaptureRefusal = perspectiveCaptureRefusal,
)

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
                    org.graphiks.math.geometry.RectF32.ofLTRB(
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
