package org.graphiks.kanvas.gpu.plan

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.render.ir.ColorFilterNode

public class ColorFilterExecutionPlanV1 private constructor(
    public val structuralIdentity: String,
    public val canonicalIdentity: String,
    private val records: List<Record>,
    private val graph: ColorOperationGraphV1,
    internal val composeChildren: Pair<ColorFilterExecutionPlanV1,ColorFilterExecutionPlanV1>? = null,
    internal val lerpChildren: Pair<ColorFilterExecutionPlanV1,ColorFilterExecutionPlanV1>? = null,
) {
    public val dynamicByteCountI64: Long = records.fold(0L) { size, record -> Math.addExact(size, record.wordsI32 * 4L) }
    public fun copyOperationGraph(): ColorOperationGraphV1 = graph
    internal fun forEachWord(action: (Long, Float) -> Unit) {
        var offsetI64 = 0L
        records.forEach { record -> repeat(record.wordsI32) { action(offsetI64++, record.value(it)) } }
    }
    public fun copyDynamicBytes(): ByteArray = ByteBuffer.allocate(Math.toIntExact(dynamicByteCountI64)).order(ByteOrder.LITTLE_ENDIAN).apply {
        forEachWord { _, value -> putFloat(value) }
    }.array()
    internal sealed interface Record {
        val wordsI32: Int
        fun value(indexI32: Int): Float
        class Matrix(val captured: ColorFilterNode.Matrix) : Record {
            override val wordsI32 = 20
            override fun value(indexI32: Int): Float = captured.values[indexI32]
        }
        class Lerp(val tF32: Float) : Record {
            override val wordsI32 = 4
            override fun value(indexI32: Int): Float = if (indexI32 == 0) tF32 else 0f
        }
    }
    internal companion object {
        fun matrix(filter: ColorFilterNode.Matrix): ColorFilterExecutionPlanV1 {
            require(filter.values.sizeI32 == 20 && (0 until 20).all { filter.values[it].isFinite() })
            val graph = ColorOperationGraphV1.matrix()
            val structure = "color-filter-v1:matrix20-row-major-linear-straight-clamp-premul:${graph.canonicalIdentity}"
            return ColorFilterExecutionPlanV1(structure, "$structure:${filter.canonicalId.value}", listOf(Record.Matrix(filter)), graph)
        }
        fun compose(filter: ColorFilterNode.Compose, outer: ColorFilterExecutionPlanV1,
            inner: ColorFilterExecutionPlanV1): ColorFilterExecutionPlanV1 {
            val graph = outer.graph.bindInput(inner.graph, inner.dynamicByteCountI64 / 4L)
            val structure = "compose-v1:${outer.structuralIdentity}:${inner.structuralIdentity}"
            return ColorFilterExecutionPlanV1(structure, "$structure:${filter.canonicalId.value}",
                immutableList(inner.records + outer.records), graph, composeChildren = outer to inner)
        }
        fun lerp(filter: ColorFilterNode.Lerp, dst: ColorFilterExecutionPlanV1,
            src: ColorFilterExecutionPlanV1): ColorFilterExecutionPlanV1 {
            val original = ColorOperationGraphV1(List(4) { ColorOperationGraphV1.Scalar.InputLinearPremul(it) })
            val right = src.graph.bindInput(original, dst.dynamicByteCountI64 / 4L)
            val t = ColorOperationGraphV1.Scalar.DynamicF32(Math.addExact(dst.dynamicByteCountI64, src.dynamicByteCountI64) / 4L)
            val inverse = ColorOperationGraphV1.Scalar.Subtract(ColorOperationGraphV1.constant(1f), t)
            val graph = ColorOperationGraphV1(List(4) { ColorOperationGraphV1.Scalar.Add(
                ColorOperationGraphV1.Scalar.Multiply(inverse, dst.graph.outputs[it]),
                ColorOperationGraphV1.Scalar.Multiply(t, right.outputs[it])) })
            val structure = "lerp-v1:${dst.structuralIdentity}:${src.structuralIdentity}"
            return ColorFilterExecutionPlanV1(structure, "$structure:${filter.canonicalId.value}",
                immutableList(dst.records + src.records + Record.Lerp(filter.t)), graph, lerpChildren = dst to src)
        }
    }
}
