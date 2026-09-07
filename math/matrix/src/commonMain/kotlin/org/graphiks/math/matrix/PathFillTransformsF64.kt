package org.graphiks.math.matrix

import org.graphiks.math.geometry.PathF32
import org.graphiks.math.geometry.PathFillInputF64
import org.graphiks.math.geometry.PathStrokeDeviceFillSegmentMapperF64

/** Maps a path into an immutable F64 device-space fill input. */
public fun Matrix3x3F32.mapPathFillInputF64(path: PathF32): PathFillInputF64 {
    val matrixF64 = toMatrix3x3F64()
    return matrixF64.mapAffinePathFillInputF64(
        inputF64 = PathFillInputF64.fromPathF32(path),
        debitI64 = PathTransformWorkDebitI64 { },
    )
}

/** Supplies the shared affine fill mapping one source command at a time for W4d geometry. */
public fun Matrix3x3F32.pathStrokeDeviceFillSegmentMapperF64(): PathStrokeDeviceFillSegmentMapperF64 {
    return toMatrix3x3F64().pathStrokeDeviceFillSegmentMapperF64()
}

/** Supplies the affine device-fill authority shared by the F64 transformed-geometry seam. */
internal fun Matrix3x3F64.pathStrokeDeviceFillSegmentMapperF64(): PathStrokeDeviceFillSegmentMapperF64 {
    require(isFinite() && classifyPathTransform() != PathTransformClass.Perspective) {
        "pathStrokeDeviceFillSegmentMapperF64 requires a finite affine Matrix3x3F64"
    }
    return PathStrokeDeviceFillSegmentMapperF64 { sourceSegmentF64 ->
        try {
            mapAffinePathFillSegmentF64(sourceSegmentF64)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
