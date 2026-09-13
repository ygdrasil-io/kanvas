package org.graphiks.kanvas.gpu.plan

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.render.ir.ColorFilterNode

public class ColorFilterExecutionPlanV1 private constructor(
    public val structuralIdentity: String,
    public val canonicalIdentity: String,
    private val capturedFilter: ColorFilterNode.Matrix,
    private val graph: ColorOperationGraphV1,
) {
    public val dynamicByteCountI64: Long = 80L
    public fun copyOperationGraph(): ColorOperationGraphV1 = graph
    internal fun coefficientF32(indexI32: Int): Float = capturedFilter.values[indexI32]
    public fun copyDynamicBytes(): ByteArray = ByteBuffer.allocate(80).order(ByteOrder.LITTLE_ENDIAN).apply {
        repeat(20) { putFloat(coefficientF32(it)) }
    }.array()
    internal companion object {
        fun matrix(filter: ColorFilterNode.Matrix): ColorFilterExecutionPlanV1 {
            require(filter.values.sizeI32 == 20 && (0 until 20).all { filter.values[it].isFinite() })
            val graph = ColorOperationGraphV1.matrix()
            val structure = "color-filter-v1:matrix20-row-major-linear-straight-clamp-premul:${graph.canonicalIdentity}"
            return ColorFilterExecutionPlanV1(structure, "$structure:${filter.canonicalId.value}", filter, graph)
        }
    }
}
