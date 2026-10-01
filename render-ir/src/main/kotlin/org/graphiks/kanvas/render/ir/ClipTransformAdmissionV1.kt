package org.graphiks.kanvas.render.ir

import org.graphiks.math.geometry.CornerRadiiF64
import org.graphiks.math.geometry.RRectF64
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.matrix.ClipTransformAdmissionF64
import org.graphiks.math.matrix.ClipTransformGeometryF64
import org.graphiks.math.matrix.Matrix3x3F64
import org.graphiks.math.matrix.classifyClipTransformAdmissionF64
import org.graphiks.math.matrix.classifyClipTransformMatrixAdmissionF64
import org.graphiks.math.matrix.timesCheckedOrNull
import org.graphiks.math.matrix.toMatrix3x3F64

/** Typed source-clip admission facts; gpu-plan maps them to its established public diagnostics. */
public enum class ClipTransformAdmissionV1 {
    Ready,
    NonFinite,
    Perspective,
    Singular,
    NonFiniteProjection,
    LegacyUnavailable,
}

/**
 * Validates the captured transform provenance, then its actual forward F64 prefix, then the
 * resulting F32 geometry projection.  DeviceRect has no synthetic capture snapshot: it is
 * inspected only through its real effective prefix.
 */
public fun ClipStackNode.clipTransformAdmissionV1(enclosingF64: Matrix3x3F64): ClipTransformAdmissionV1 {
    return when (this) {
        ClipStackNode.Empty -> ClipTransformAdmissionV1.Ready
        is ClipStackNode.DeviceRect -> geometryAdmissionV1(
            ClipTransformGeometryF64.Rect(copyBounds().toRectF64()), enclosingF64,
        )
        is ClipStackNode.Operations -> {
            for (entry in this) {
                val admission = entry.clipTransformAdmissionV1(enclosingF64)
                if (admission != ClipTransformAdmissionV1.Ready) return admission
            }
            ClipTransformAdmissionV1.Ready
        }
    }
}

private fun ClipEntry.clipTransformAdmissionV1(enclosingF64: Matrix3x3F64): ClipTransformAdmissionV1 {
    return when (val snapshot = transform) {
        is ClipTransformSnapshot.LegacyUnavailable -> if (snapshot.perspectiveCaptureRefusal) {
            ClipTransformAdmissionV1.Perspective
        } else {
            ClipTransformAdmissionV1.LegacyUnavailable
        }
        is ClipTransformSnapshot.Known -> {
            val capturedF64 = snapshot.copyMatrixF32().toMatrix3x3F64()
            val capturedAdmission = capturedF64.classifyClipTransformMatrixAdmissionF64().toV1()
            if (capturedAdmission != ClipTransformAdmissionV1.Ready) {
                capturedAdmission
            } else {
                val effectiveF64 = enclosingF64.timesCheckedOrNull(capturedF64)
                val geometryF64 = geometry.toClipTransformGeometryF64OrNull()
                when {
                    effectiveF64 == null -> ClipTransformAdmissionV1.NonFinite
                    geometryF64 == null -> ClipTransformAdmissionV1.Ready
                    else -> geometryAdmissionV1(geometryF64, effectiveF64)
                }
            }
        }
    }
}

private fun geometryAdmissionV1(
    geometryF64: ClipTransformGeometryF64,
    matrixF64: Matrix3x3F64,
): ClipTransformAdmissionV1 = classifyClipTransformAdmissionF64(geometryF64, matrixF64).toV1()

private fun ClipTransformAdmissionF64.toV1(): ClipTransformAdmissionV1 = when (this) {
    ClipTransformAdmissionF64.Ready -> ClipTransformAdmissionV1.Ready
    ClipTransformAdmissionF64.NonFinite -> ClipTransformAdmissionV1.NonFinite
    ClipTransformAdmissionF64.Perspective -> ClipTransformAdmissionV1.Perspective
    ClipTransformAdmissionF64.Singular -> ClipTransformAdmissionV1.Singular
    ClipTransformAdmissionF64.NonFiniteProjection -> ClipTransformAdmissionV1.NonFiniteProjection
}

private fun GeometryNode.toClipTransformGeometryF64OrNull(): ClipTransformGeometryF64? = when (this) {
    is GeometryNode.Rect -> ClipTransformGeometryF64.Rect(copyBounds().toRectF64())
    is GeometryNode.RRect -> copyShape().toClipTransformGeometryF64()
    is GeometryNode.Path -> ClipTransformGeometryF64.Path(path)
    else -> null
}

private fun org.graphiks.math.geometry.RectF32.toRectF64(): RectF64 = RectF64(
    left.toDouble(), top.toDouble(), right.toDouble(), bottom.toDouble(),
)

private fun org.graphiks.math.geometry.RRectF32.toClipTransformGeometryF64(): ClipTransformGeometryF64.RRect =
    ClipTransformGeometryF64.RRect(RRectF64.of(
        rect.toRectF64(),
        CornerRadiiF64.of(topLeft.x.toDouble(), topLeft.y.toDouble()),
        CornerRadiiF64.of(topRight.x.toDouble(), topRight.y.toDouble()),
        CornerRadiiF64.of(bottomRight.x.toDouble(), bottomRight.y.toDouble()),
        CornerRadiiF64.of(bottomLeft.x.toDouble(), bottomLeft.y.toDouble()),
    ))
