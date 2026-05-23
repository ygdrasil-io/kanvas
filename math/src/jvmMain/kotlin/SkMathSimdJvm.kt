package org.graphiks.math

import jdk.incubator.vector.FloatVector
import jdk.incubator.vector.VectorOperators
import jdk.incubator.vector.VectorSpecies

internal object SkMathSimdJvm {
    private val F32X4: VectorSpecies<Float> = FloatVector.SPECIES_128
    private val row0 = intArrayOf(0, 4, 8, 12)
    private val row1 = intArrayOf(1, 5, 9, 13)
    private val row2 = intArrayOf(2, 6, 10, 14)
    private val row3 = intArrayOf(3, 7, 11, 15)
    private val left = ThreadLocal.withInitial { FloatArray(4) }
    private val right = ThreadLocal.withInitial { FloatArray(4) }

    fun dot4(
        ax: Float,
        ay: Float,
        az: Float,
        aw: Float,
        bx: Float,
        by: Float,
        bz: Float,
        bw: Float,
    ): Float {
        val leftValues = left.get()
        val rightValues = right.get()
        leftValues[0] = ax
        leftValues[1] = ay
        leftValues[2] = az
        leftValues[3] = aw
        rightValues[0] = bx
        rightValues[1] = by
        rightValues[2] = bz
        rightValues[3] = bw

        return FloatVector.fromArray(F32X4, leftValues, 0)
            .mul(FloatVector.fromArray(F32X4, rightValues, 0))
            .reduceLanes(VectorOperators.ADD)
    }

    fun m44Concat(a: FloatArray, b: FloatArray, out: FloatArray) {
        val aRow0 = FloatVector.fromArray(F32X4, a, 0, row0, 0)
        val aRow1 = FloatVector.fromArray(F32X4, a, 0, row1, 0)
        val aRow2 = FloatVector.fromArray(F32X4, a, 0, row2, 0)
        val aRow3 = FloatVector.fromArray(F32X4, a, 0, row3, 0)

        for (c in 0 until 4) {
            val offset = c * 4
            val col = FloatVector.fromArray(F32X4, b, offset)
            out[offset] = aRow0.mul(col).reduceLanes(VectorOperators.ADD)
            out[offset + 1] = aRow1.mul(col).reduceLanes(VectorOperators.ADD)
            out[offset + 2] = aRow2.mul(col).reduceLanes(VectorOperators.ADD)
            out[offset + 3] = aRow3.mul(col).reduceLanes(VectorOperators.ADD)
        }
    }
}
