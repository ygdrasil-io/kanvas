package org.graphiks.kanvas.gpu.plan

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.render.ir.ColorFilterNode
import org.graphiks.kanvas.render.ir.ImmutableUBytes

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
    /** W5 graph-derived restore fact; unknown graph input remains conservatively affecting. */
    public val affectsTransparentBlack: Boolean by lazy {
        val words = FloatArray(Math.toIntExact(dynamicByteCountI64 / 4L))
        val tables = mutableMapOf<Long, ImmutableUBytes>()
        forEachWord { offset, bits -> words[Math.toIntExact(offset)] = Float.fromBits(bits) }
        forEachTable { offset, table -> tables[offset] = table }
        graph.transparentBlackOutputV1(
            dynamicF32 = { offset -> words.getOrNull(Math.toIntExact(offset)) ?: error("Missing color filter word") },
            tableByte = { offset, index -> requireNotNull(tables[offset])[index].toInt() },
        )?.any { value -> value != 0f } ?: true
    }
    internal fun forEachWord(action: (Long, Int) -> Unit) {
        var offsetI64 = 0L
        records.forEach { record ->
            if (record is Record.Numeric) repeat(record.wordsI32) { action(Math.addExact(offsetI64,it.toLong()),record.value(it).toRawBits()) }
            offsetI64 = Math.addExact(offsetI64,record.wordsI32.toLong())
        }
    }
    internal fun forEachTable(action: (Long, ImmutableUBytes) -> Unit) {
        var offsetI64 = 0L
        records.forEach { record ->
            if (record is Record.Table) action(offsetI64,record.captured.table)
            offsetI64 = Math.addExact(offsetI64,record.wordsI32.toLong())
        }
    }
    public fun copyDynamicBytes(): ByteArray = ByteBuffer.allocate(Math.toIntExact(dynamicByteCountI64)).order(ByteOrder.LITTLE_ENDIAN).apply {
        forEachWord { offset, bits -> putInt(Math.toIntExact(offset*4L),bits) }
        forEachTable { offset, table -> repeat(256) { put(Math.toIntExact(offset*4L+it),table[it].toByte()) } }
    }.array()
    internal sealed interface Record {
        val wordsI32: Int
        sealed interface Numeric : Record { fun value(indexI32: Int): Float }
        class Matrix(val captured: ColorFilterNode.Matrix) : Numeric {
            override val wordsI32 = 20
            override fun value(indexI32: Int): Float = captured.values[indexI32]
        }
        class Hsla(val captured: ColorFilterNode.HSLAMatrix) : Numeric {
            override val wordsI32 = 20
            override fun value(indexI32: Int): Float = captured.values[indexI32]
        }
        class Lerp(val tF32: Float) : Numeric {
            override val wordsI32 = 4
            override fun value(indexI32: Int): Float = if (indexI32 == 0) tF32 else 0f
        }
        class Table(val captured: ColorFilterNode.Table) : Record {
            override val wordsI32 = 64
        }
        class Lighting(val captured: ColorFilterNode.Lighting) : Numeric {
            override val wordsI32 = 8
            override fun value(indexI32: Int): Float {
                val color = if (indexI32 < 4) captured.mul else captured.add
                return when (indexI32 % 4) { 0 -> color.red / 255f; 1 -> color.green / 255f; 2 -> color.blue / 255f; else -> 0f }
            }
        }
        class Blend(val captured: ColorFilterNode.Blend) : Numeric {
            override val wordsI32 = 4
            override fun value(indexI32: Int): Float = when (indexI32) {
                0 -> captured.color.red / 255f; 1 -> captured.color.green / 255f
                2 -> captured.color.blue / 255f; else -> captured.color.alpha / 255f
            }
        }
    }
    internal companion object {
        fun hsla(filter: ColorFilterNode.HSLAMatrix): ColorFilterExecutionPlanV1 {
            require(filter.values.sizeI32 == 20 && (0 until 20).all { filter.values[it].isFinite() })
            val graph = ColorOperationGraphV1.hsla()
            val structure = "hsla20-v1:${graph.canonicalIdentity}"
            return ColorFilterExecutionPlanV1(structure,"$structure:${filter.canonicalId.value}",listOf(Record.Hsla(filter)),graph)
        }
        fun preset(filter: ColorFilterNode): ColorFilterExecutionPlanV1 {
            val graph = when (filter) {
                ColorFilterNode.HighContrast -> ColorOperationGraphV1.highContrast()
                ColorFilterNode.Luma -> ColorOperationGraphV1.luma()
                ColorFilterNode.Overdraw -> ColorOperationGraphV1.overdraw()
                else -> error("Not a parameterless preset")
            }
            val structure = "preset-v1:${filter.canonicalId.value}:${graph.canonicalIdentity}"
            return ColorFilterExecutionPlanV1(structure,structure,emptyList(),graph)
        }
        fun blend(filter: ColorFilterNode.Blend): ColorFilterExecutionPlanV1 {
            val alpha = ColorOperationGraphV1.Scalar.DynamicF32(3L)
            val src = List(4) { if (it == 3) alpha else ColorOperationGraphV1.Scalar.Multiply(
                ColorOperationGraphV1.eotf(ColorOperationGraphV1.Scalar.DynamicF32(it.toLong())),alpha) }
            val graph = BlendFormulaProgramV1.colorOperations(filter.mode.name.lowercase(),src,
                List(4) { ColorOperationGraphV1.Scalar.InputLinearPremul(it) })
            val structure = "blend-filter-v1:${filter.mode.name}:${BlendFormulaProgramV1.REVISION_I32}:${graph.canonicalIdentity}"
            return ColorFilterExecutionPlanV1(structure,"$structure:${filter.canonicalId.value}",listOf(Record.Blend(filter)),graph)
        }
        fun table(filter: ColorFilterNode.Table): ColorFilterExecutionPlanV1 {
            require(filter.table.sizeI32 == 256)
            val graph = ColorOperationGraphV1.table()
            val structure = "table256-v1:${graph.canonicalIdentity}"
            return ColorFilterExecutionPlanV1(structure,"$structure:${filter.canonicalId.value}",listOf(Record.Table(filter)),graph)
        }
        fun lighting(filter: ColorFilterNode.Lighting): ColorFilterExecutionPlanV1 {
            val graph = ColorOperationGraphV1.lighting()
            val structure = "lighting-v1:${graph.canonicalIdentity}"
            return ColorFilterExecutionPlanV1(structure,"$structure:${filter.canonicalId.value}",listOf(Record.Lighting(filter)),graph)
        }
        fun transfer(filter: ColorFilterNode): ColorFilterExecutionPlanV1 {
            require(filter == ColorFilterNode.SRGBToLinear || filter == ColorFilterNode.LinearToSRGB)
            val graph = ColorOperationGraphV1.transferFilter(filter == ColorFilterNode.SRGBToLinear)
            val structure = "transfer-v1:${filter.canonicalId.value}:${graph.canonicalIdentity}"
            return ColorFilterExecutionPlanV1(structure,structure,emptyList(),graph)
        }
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
