package org.graphiks.math.matrix

import org.graphiks.math.geometry.Point3F32
import org.graphiks.math.vector.Vector3F32

/**
 * Immutable 3 × 4 single-precision matrix stored in row-major coefficient order.
 *
 * The matrix represents an affine transform from a 3D point to a 3D point:
 *
 * ```
 * [ m00 m01 m02 m03 ] [ x ]   [ m00·x + m01·y + m02·z + m03 ]
 * [ m10 m11 m12 m13 ] [ y ] = [ m10·x + m11·y + m12·z + m13 ]
 * [ m20 m21 m22 m23 ] [ z ]   [ m20·x + m21·y + m22·z + m23 ]
 *                     [ 1 ]
 * ```
 *
 * It deliberately owns scalar coefficients instead of a mutable nested array,
 * so instances are safe to share and cannot be changed through an alias to
 * their input storage.
 */
public data class Matrix3x4F32(
    public val m00: Float = 0f,
    public val m01: Float = 0f,
    public val m02: Float = 0f,
    public val m03: Float = 0f,
    public val m10: Float = 0f,
    public val m11: Float = 0f,
    public val m12: Float = 0f,
    public val m13: Float = 0f,
    public val m20: Float = 0f,
    public val m21: Float = 0f,
    public val m22: Float = 0f,
    public val m23: Float = 0f,
) {

    /** Returns the coefficient at [row], [column]. */
    public operator fun get(row: Int, column: Int): Float {
        require(row in 0..2 && column in 0..3) {
            "get($row, $column) out of range for 3x4 matrix"
        }
        return when (row) {
            0 -> when (column) {
                0 -> m00; 1 -> m01; 2 -> m02; else -> m03
            }
            1 -> when (column) {
                0 -> m10; 1 -> m11; 2 -> m12; else -> m13
            }
            else -> when (column) {
                0 -> m20; 1 -> m21; 2 -> m22; else -> m23
            }
        }
    }

    /** Named equivalent of [get] for callers that prefer function syntax. */
    public fun rc(row: Int, column: Int): Float = get(row, column)

    /** Returns the coefficients in row-major order. */
    public fun toFloatArray(): FloatArray = floatArrayOf(
        m00, m01, m02, m03,
        m10, m11, m12, m13,
        m20, m21, m22, m23,
    )

    /** Transforms [point], including the affine translation column. */
    public fun transform(point: Point3F32): Point3F32 = Point3F32(
        m00 * point.x + m01 * point.y + m02 * point.z + m03,
        m10 * point.x + m11 * point.y + m12 * point.z + m13,
        m20 * point.x + m21 * point.y + m22 * point.z + m23,
    )

    /** Transforms [vector] using only the linear 3 × 3 coefficients. */
    public fun transform(vector: Vector3F32): Vector3F32 = Vector3F32(
        m00 * vector.x + m01 * vector.y + m02 * vector.z,
        m10 * vector.x + m11 * vector.y + m12 * vector.z,
        m20 * vector.x + m21 * vector.y + m22 * vector.z,
    )

    /** Operator equivalent of [transform] for a point. */
    public operator fun times(point: Point3F32): Point3F32 = transform(point)

    /** Operator equivalent of [transform] for a vector. */
    public operator fun times(vector: Vector3F32): Vector3F32 = transform(vector)

    public companion object {
        /** Returns the all-zero 3 × 4 matrix. */
        public fun zero(): Matrix3x4F32 = Matrix3x4F32()

        /** Creates a matrix from twelve row-major coefficients. */
        public fun of(
            m00: Float, m01: Float, m02: Float, m03: Float,
            m10: Float, m11: Float, m12: Float, m13: Float,
            m20: Float, m21: Float, m22: Float, m23: Float,
        ): Matrix3x4F32 = Matrix3x4F32(
            m00, m01, m02, m03,
            m10, m11, m12, m13,
            m20, m21, m22, m23,
        )

        /** Creates a matrix from a twelve-element row-major array. */
        public fun fromRowMajor(values: FloatArray): Matrix3x4F32 {
            require(values.size == 12) {
                "Matrix3x4F32 requires 12 row-major values (got ${values.size})"
            }
            return of(
                values[0], values[1], values[2], values[3],
                values[4], values[5], values[6], values[7],
                values[8], values[9], values[10], values[11],
            )
        }
    }
}
