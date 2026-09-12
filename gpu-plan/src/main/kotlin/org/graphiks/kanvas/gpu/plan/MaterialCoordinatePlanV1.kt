package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.toMatrix3x3F64
import org.graphiks.math.matrix.invertToMatrix3x3F32OrNull

/** Authenticated before the Rect lane discards its draw CTM. */
public class MaterialCoordinatePlanV1 private constructor(inverseCtmF32: Matrix3x3F32) {
    private val inverseF32 = inverseCtmF32.copy()
    public fun copyInverseCtmF32(): Matrix3x3F32 = inverseF32.copy()
    public val uniformByteSizeI64: Long get() = 48L
    public val canonicalIdentity: String = "material-coordinate-v1:" + listOf(inverseF32.sx, inverseF32.kx,
        inverseF32.tx, inverseF32.ky, inverseF32.sy, inverseF32.ty, inverseF32.persp0,
        inverseF32.persp1, inverseF32.persp2).joinToString(",") { it.toBits().toString() }
    override fun toString(): String = canonicalIdentity
    override fun equals(other: Any?): Boolean = other is MaterialCoordinatePlanV1 && canonicalIdentity == other.canonicalIdentity
    override fun hashCode(): Int = canonicalIdentity.hashCode()
    public companion object {
        public fun fromCtm(ctmF32: Matrix3x3F32): MaterialCoordinatePlanV1? =
            ctmF32.toMatrix3x3F64().invertToMatrix3x3F32OrNull()?.let(::MaterialCoordinatePlanV1)
    }
}
