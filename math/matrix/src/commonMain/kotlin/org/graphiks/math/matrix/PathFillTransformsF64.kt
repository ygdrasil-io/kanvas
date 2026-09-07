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
    val matrixF64 = toMatrix3x3F64()
    require(matrixF64.isFinite() && matrixF64.classifyPathTransform() != PathTransformClass.Perspective) {
        "pathStrokeDeviceFillSegmentMapperF64 requires a finite affine Matrix3x3F32"
    }
    return PathStrokeDeviceFillSegmentMapperF64 { sourceSegmentF64 ->
        try {
            matrixF64.mapAffinePathFillSegmentF64(sourceSegmentF64)
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}
