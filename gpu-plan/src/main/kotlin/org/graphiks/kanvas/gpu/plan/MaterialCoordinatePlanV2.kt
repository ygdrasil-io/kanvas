package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.RectF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.matrix.composeInOrderF64
import org.graphiks.math.matrix.toMatrix3x3F64
import org.graphiks.math.matrix.invertFiniteOrNull
import org.graphiks.math.matrix.toFiniteMatrix3x3F32OrNull
import org.graphiks.math.matrix.determinantF64
import org.graphiks.math.matrix.isFinite

public sealed interface MaterialCoordinateOperationV2 {
    public data class InverseMatrixF32(public val inverseF32: Matrix3x3F32) : MaterialCoordinateOperationV2
    public data class ClampRectF32(public val subsetF32: RectF32) : MaterialCoordinateOperationV2
}

/** Ordered device-to-material operations. Values never enter the topology key. */
public class MaterialCoordinatePlanV2 private constructor(operations: List<MaterialCoordinateOperationV2>) {
    private val operations = immutableList(operations.map { it.snapshot() })
    public fun copyOperations(): List<MaterialCoordinateOperationV2> = operations.map { it.snapshot() }
    public val uniformByteSizeI64: Long = operations.fold(0L) { bytesI64, operation ->
        Math.addExact(bytesI64, when (operation) {
            is MaterialCoordinateOperationV2.InverseMatrixF32 -> 48L
            is MaterialCoordinateOperationV2.ClampRectF32 -> 16L
        })
    }
    public val topologyIdentity: String = "material-coordinate-v2:" + operations.joinToString(",") {
        when (it) {
            is MaterialCoordinateOperationV2.InverseMatrixF32 -> "inverse-matrix-f32"
            is MaterialCoordinateOperationV2.ClampRectF32 -> "clamp-rect-f32"
        }
    }
    public val canonicalIdentity: String = topologyIdentity + ":" + this.operations.joinToString(";") { operation ->
        when (operation) {
            is MaterialCoordinateOperationV2.InverseMatrixF32 -> operation.inverseF32.let { matrixF32 ->
                listOf(matrixF32.sx, matrixF32.kx, matrixF32.tx, matrixF32.ky, matrixF32.sy, matrixF32.ty,
                    matrixF32.persp0, matrixF32.persp1, matrixF32.persp2)
            }
            is MaterialCoordinateOperationV2.ClampRectF32 -> operation.subsetF32.let { subsetF32 ->
                listOf(subsetF32.left, subsetF32.top, subsetF32.right, subsetF32.bottom)
            }
        }.joinToString(",") { it.toBits().toString() }
    }
    override fun equals(other: Any?): Boolean = other is MaterialCoordinatePlanV2 && canonicalIdentity == other.canonicalIdentity
    override fun hashCode(): Int = canonicalIdentity.hashCode()
    override fun toString(): String = canonicalIdentity

    internal sealed interface Build {
        data class Ready(val coordinates: MaterialCoordinatePlanV2) : Build
        data class Refused(val code: String) : Build
    }
    internal companion object {
        fun fromCtmAndNodes(ctmF32: Matrix3x3F32, nodes: List<CoordinateNodeV2>): Build {
            val segment = mutableListOf<Matrix3x3F32>()
            for (node in nodes) {
                // Clamp boundaries become executable in Task 3; never combine through one.
                val localF32 = (node as? CoordinateNodeV2.LocalMatrix)?.copyMatrixF32()
                    ?: return Build.Refused(W5aPlanDiagnostics.UnsupportedMaterial)
                val localF64 = localF32.toMatrix3x3F64()
                if (!localF64.isFinite()) return Build.Refused(W5dPlanDiagnostics.LocalMatrixNonFinite)
                if (localF64.determinantF64() == 0.0) return Build.Refused(W5dPlanDiagnostics.LocalMatrixSingular)
                if (localF32.persp0 != 0f || localF32.persp1 != 0f || localF32.persp2 != 1f)
                    return Build.Refused(W5dPlanDiagnostics.LocalMatrixUnrepresentable)
                segment += localF32
            }
            val composedF64 = try { composeInOrderF64(segment) } catch (_: IllegalArgumentException) {
                return Build.Refused(W5dPlanDiagnostics.LocalMatrixUnrepresentable)
            }
            if (composedF64.determinantF64() == 0.0) return Build.Refused(W5dPlanDiagnostics.LocalMatrixSingular)
            val inverseLocalF32 = composedF64.invertFiniteOrNull()?.toFiniteMatrix3x3F32OrNull()
                ?: return Build.Refused(W5dPlanDiagnostics.LocalMatrixUnrepresentable)
            val inverseCtmF32 = ctmF32.toMatrix3x3F64().invertFiniteOrNull()?.toFiniteMatrix3x3F32OrNull()
                ?: return Build.Refused(W5cPlanDiagnostics.CoordinatesUnavailable)
            if (inverseCtmF32.persp0 != 0f || inverseCtmF32.persp1 != 0f || inverseCtmF32.persp2 != 1f)
                return Build.Refused(W5cPlanDiagnostics.NumericDomainUnbounded)
            return Build.Ready(MaterialCoordinatePlanV2(listOf(
                MaterialCoordinateOperationV2.InverseMatrixF32(inverseCtmF32),
                MaterialCoordinateOperationV2.InverseMatrixF32(inverseLocalF32),
            )))
        }

        private fun MaterialCoordinateOperationV2.snapshot(): MaterialCoordinateOperationV2 = when (this) {
            is MaterialCoordinateOperationV2.InverseMatrixF32 -> copy(inverseF32 = inverseF32.copy())
            is MaterialCoordinateOperationV2.ClampRectF32 -> copy(subsetF32 = subsetF32.copy())
        }
    }
}
