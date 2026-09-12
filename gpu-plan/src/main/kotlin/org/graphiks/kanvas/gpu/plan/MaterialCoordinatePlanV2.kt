package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.toMatrix3x3F64
import org.graphiks.math.matrix.invertToMatrix3x3F32OrNull
import org.graphiks.math.matrix.determinantF64
import org.graphiks.math.matrix.isFinite

/** Ordered device-to-material operations. Values never enter the topology key. */
public class MaterialCoordinatePlanV2 private constructor(operations: List<InverseMatrixF32>) {
    public class InverseMatrixF32 internal constructor(matrixF32: Matrix3x3F32) {
        private val matrixF32 = matrixF32.copy()
        public fun copyMatrixF32(): Matrix3x3F32 = matrixF32.copy()
        internal val identity: String = listOf(matrixF32.sx, matrixF32.kx, matrixF32.tx,
            matrixF32.ky, matrixF32.sy, matrixF32.ty, matrixF32.persp0, matrixF32.persp1, matrixF32.persp2)
            .joinToString(",") { it.toBits().toString() }
    }
    private val operations = immutableList(operations.map { InverseMatrixF32(it.copyMatrixF32()) })
    public fun copyOperations(): List<InverseMatrixF32> = operations.map { InverseMatrixF32(it.copyMatrixF32()) }
    public val uniformByteSizeI64: Long = Math.multiplyExact(operations.size.toLong(), 48L)
    public val topologyIdentity: String = "material-coordinate-v2:" + operations.joinToString(",") { "inverse-matrix-f32" }
    public val canonicalIdentity: String = topologyIdentity + ":" + operations.joinToString(";") { it.identity }
    override fun equals(other: Any?): Boolean = other is MaterialCoordinatePlanV2 && canonicalIdentity == other.canonicalIdentity
    override fun hashCode(): Int = canonicalIdentity.hashCode()
    override fun toString(): String = canonicalIdentity

    internal sealed interface Build {
        data class Ready(val coordinates: MaterialCoordinatePlanV2) : Build
        data class Refused(val code: String) : Build
    }
    internal companion object {
        fun fromCtmAndLocal(ctmF32: Matrix3x3F32, localF32: Matrix3x3F32): Build {
            val localF64 = localF32.toMatrix3x3F64()
            if (!localF64.isFinite()) return Build.Refused(W5dPlanDiagnostics.LocalMatrixNonFinite)
            if (localF64.determinantF64() == 0.0) return Build.Refused(W5dPlanDiagnostics.LocalMatrixSingular)
            if (localF32.persp0 != 0f || localF32.persp1 != 0f || localF32.persp2 != 1f)
                return Build.Refused(W5dPlanDiagnostics.LocalMatrixUnrepresentable)
            val inverseLocalF32 = localF64.invertToMatrix3x3F32OrNull()
                ?: return Build.Refused(W5dPlanDiagnostics.LocalMatrixUnrepresentable)
            val inverseCtmF32 = ctmF32.toMatrix3x3F64().invertToMatrix3x3F32OrNull()
                ?: return Build.Refused(W5cPlanDiagnostics.CoordinatesUnavailable)
            if (inverseCtmF32.persp0 != 0f || inverseCtmF32.persp1 != 0f || inverseCtmF32.persp2 != 1f)
                return Build.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
            return Build.Ready(MaterialCoordinatePlanV2(listOf(InverseMatrixF32(inverseCtmF32), InverseMatrixF32(inverseLocalF32))))
        }
    }
}
