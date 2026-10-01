package org.graphiks.math.matrix

import kotlin.math.abs
import org.graphiks.math.geometry.PathFillInputF64
import org.graphiks.math.geometry.PathFillSegmentF64
import org.graphiks.math.geometry.toPathFillInputF64

/** Numerical clip-transform facts, deliberately independent from renderer diagnostics. */
public enum class ClipTransformAdmissionF64 {
    Ready,
    NonFinite,
    Perspective,
    Singular,
    NonFiniteProjection,
}

/**
 * Classifies the finite affine boundary before any geometry projection.  The comparisons are
 * exact IEEE-754 comparisons: a near-singular matrix is not silently treated as singular.
 */
public fun Matrix3x3F64.classifyClipTransformMatrixAdmissionF64(): ClipTransformAdmissionF64 {
    if (!isFinite()) return ClipTransformAdmissionF64.NonFinite
    if (classifyPathTransform() == PathTransformClass.Perspective) return ClipTransformAdmissionF64.Perspective
    val determinant = determinantF64()
    return when {
        !determinant.isFinite() -> ClipTransformAdmissionF64.NonFinite
        determinant == 0.0 -> ClipTransformAdmissionF64.Singular
        else -> ClipTransformAdmissionF64.Ready
    }
}

/**
 * Validates the actual affine clip coordinates at the one F64-to-F32 boundary. It does not
 * flatten paths or debit renderer work, so callers retain the existing resource budgets. Path
 * and RRect admission does materialize widened and mapped path snapshots before the finite check
 * can return, which is O(path size) temporary host allocation.
 */
public fun classifyClipTransformAdmissionF64(
    geometryF64: ClipTransformGeometryF64,
    matrixF64: Matrix3x3F64,
): ClipTransformAdmissionF64 {
    val matrixAdmission = matrixF64.classifyClipTransformMatrixAdmissionF64()
    if (matrixAdmission != ClipTransformAdmissionF64.Ready) return matrixAdmission
    return if (geometryF64.hasFiniteF32ProjectionThrough(matrixF64)) {
        ClipTransformAdmissionF64.Ready
    } else {
        ClipTransformAdmissionF64.NonFiniteProjection
    }
}

private fun ClipTransformGeometryF64.hasFiniteF32ProjectionThrough(matrixF64: Matrix3x3F64): Boolean = when (this) {
    is ClipTransformGeometryF64.Rect -> copyRectF64().let { rect ->
        matrixF64.mapsFiniteF32(rect.left, rect.top) &&
            matrixF64.mapsFiniteF32(rect.right, rect.top) &&
            matrixF64.mapsFiniteF32(rect.right, rect.bottom) &&
            matrixF64.mapsFiniteF32(rect.left, rect.bottom)
    }
    is ClipTransformGeometryF64.RRect -> matrixF64.mapsPathInputFiniteF32(
        copyRRectF64().normalizedForSkiaF64().toPathFillInputF64(),
    )
    is ClipTransformGeometryF64.Path -> matrixF64.mapsPathInputFiniteF32(PathFillInputF64.fromPathF32(pathF32))
}

/**
 * Reuses the affine arc metadata mapper with a no-op renderer-work debit. It materializes an
 * O(path size) mapped host snapshot before `.all` can inspect a segment.
 */
private fun Matrix3x3F64.mapsPathInputFiniteF32(inputF64: PathFillInputF64): Boolean = try {
    mapAffinePathFillInputF64(inputF64, PathTransformWorkDebitI64 { }).all { segment ->
        segment.hasFiniteF32Representation()
    }
} catch (_: IllegalArgumentException) {
    false
}

private fun PathFillSegmentF64.hasFiniteF32Representation(): Boolean = when (this) {
    is PathFillSegmentF64.MoveTo -> point.hasFiniteF32Representation()
    is PathFillSegmentF64.LineTo -> point.hasFiniteF32Representation()
    is PathFillSegmentF64.QuadTo -> control.hasFiniteF32Representation() && point.hasFiniteF32Representation()
    is PathFillSegmentF64.CubicTo -> control1.hasFiniteF32Representation() && control2.hasFiniteF32Representation() && point.hasFiniteF32Representation()
    is PathFillSegmentF64.ArcTo -> radius.x.isFiniteF32() && radius.y.isFiniteF32() &&
        xAxisRotationDegreesF64.isFiniteF32() && point.hasFiniteF32Representation()
    PathFillSegmentF64.Close -> true
}

private fun org.graphiks.math.geometry.Point2F64.hasFiniteF32Representation(): Boolean =
    x.isFiniteF32() && y.isFiniteF32()

private fun Matrix3x3F64.mapsFiniteF32(xF64: Double, yF64: Double): Boolean =
    xF64.isFinite() && yF64.isFinite() &&
        (sxF64 * xF64 + kxF64 * yF64 + txF64).isFiniteF32() &&
        (kyF64 * xF64 + syF64 * yF64 + tyF64).isFiniteF32()

/** Matches ClipStackPreparationF64's checked device-coordinate F64→F32 representability. */
private fun Double.isFiniteF32(): Boolean = isFinite() && abs(this) <= Float.MAX_VALUE.toDouble()
