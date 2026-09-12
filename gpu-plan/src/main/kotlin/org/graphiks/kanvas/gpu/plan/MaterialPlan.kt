package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.color.ColorF32
import org.graphiks.math.geometry.Point2F32

@JvmInline
public value class MaterialPlanRef(public val indexI32: Int) {
    init { require(indexI32 >= 0) { "Material plan references must be non-negative" } }
}

@JvmInline
public value class MaterialProgramPlanId(public val value: String) {
    init { require(value.isNotBlank()) { "Material program IDs must not be blank" } }
}

/** Immutable code-shaping half of a W5a material. */
public sealed interface MaterialProgramPlan {
    public val versionI32: Int
    public val structuralId: MaterialProgramPlanId
    public fun copyNumericOperationGraphV1(): NumericOperationGraphV1

    public data object TransparentV1 : MaterialProgramPlan {
        override val versionI32: Int = 1
        override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId("w5a-transparent-v1")
        override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.transparent()
    }

    public data object SolidLinearPremulV1 : MaterialProgramPlan {
        override val versionI32: Int = 1
        override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId("w5a-solid-linear-premul-v1")
        override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.solid()
    }

    public data object LinearGradientClampSrgbV1 : MaterialProgramPlan {
        override val versionI32: Int = 1
        // MaterialPlan remains V1; the code/binding layout revision is part of
        // the structural ID authenticated by numeric and native authorities.
        override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId("w5c-linear-clamp-srgb-stop-abi-v1-uniform-v2-numeric-v2")
        override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.gradient()
        public fun copyGradientNumericOperationGraphV1(): GradientNumericOperationGraphV1 = GradientNumericOperationGraphV1.linear()
    }

    public data object RadialGradientClampSrgbV1 : MaterialProgramPlan {
        override val versionI32: Int = 1
        override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId("w5c-radial-clamp-srgb-stop-abi-v1-numeric-v1")
        override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.gradient()
        public fun copyGradientNumericOperationGraphV1(): GradientNumericOperationGraphV1 = GradientNumericOperationGraphV1.radial()
    }

    public data object SweepGradientClampSrgbV1 : MaterialProgramPlan {
        override val versionI32: Int = 1
        override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId("w5c-sweep-clamp-srgb-stop-abi-v1-numeric-v1")
        override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.gradient()
        public fun copyGradientNumericOperationGraphV1(): GradientNumericOperationGraphV1 = GradientNumericOperationGraphV1.sweep()
    }

    public data object ConicalGradientClampSrgbV1 : MaterialProgramPlan {
        override val versionI32: Int = 1
        override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId("w5c-conical-clamp-srgb-stop-abi-v1-numeric-v1")
        override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.gradient()
        public fun copyGradientNumericOperationGraphV1(): GradientNumericOperationGraphV1 = GradientNumericOperationGraphV1.conical()
    }

    /** Child topology is code shape, while alpha remains a dynamic binding value. */
    public class OpacityV1(public val child: MaterialProgramPlan) : MaterialProgramPlan {
        override val versionI32: Int = 1
        override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId("w5a-opacity-v1(${child.structuralId.value})")
        override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.opacity()
    }
}

/** Immutable value/binding half of a W5a material. */
public sealed interface MaterialBindingPlan {
    public val versionI32: Int

    public data object EmptyV1 : MaterialBindingPlan { override val versionI32: Int = 1 }

    public sealed interface GradientV1 : MaterialBindingPlan {
        public val stopRange: GradientStopRangeV1
        public val numericAuthority: GradientNumericAuthorityV1
        public val gradientDegenerate: Boolean
        public fun copyUniformValuesF32(): List<Float>
        public fun rebind(range: GradientStopRangeV1, authority: GradientNumericAuthorityV1 = numericAuthority): GradientV1
    }

    public sealed interface GradientV2 : MaterialBindingPlan {
        override val versionI32: Int get() = 2
        public val stopRange: GradientStopRangeV1
        public val numericAuthority: GradientNumericAuthorityV2
        public val gradientDegenerate: Boolean
        public fun copyUniformValuesF32(): List<Float>
        public fun rebind(range: GradientStopRangeV1, authority: GradientNumericAuthorityV2 = numericAuthority): GradientV2
    }

    public data class LinearGradientV2(public val startF32: Point2F32, public val endF32: Point2F32,
        override val stopRange: GradientStopRangeV1, public val degeneracy: LinearGradientDegeneracyV1,
        override val numericAuthority: GradientNumericAuthorityV2) : GradientV2 {
        override val gradientDegenerate: Boolean get() = degeneracy.linearDegenerate
        override fun copyUniformValuesF32(): List<Float> = listOf(startF32.x, startF32.y, endF32.x, endF32.y)
        override fun rebind(range: GradientStopRangeV1, authority: GradientNumericAuthorityV2): GradientV2 =
            copy(stopRange = range, numericAuthority = authority)
    }

    public data class LinearGradientV1(public val startF32: Point2F32, public val endF32: Point2F32,
        override val stopRange: GradientStopRangeV1, public val degeneracy: LinearGradientDegeneracyV1,
        override val numericAuthority: GradientNumericAuthorityV1) : GradientV1 {
        override val versionI32: Int = 1
        override val gradientDegenerate: Boolean get() = degeneracy.linearDegenerate
        override fun copyUniformValuesF32(): List<Float> = listOf(startF32.x, startF32.y, endF32.x, endF32.y)
        override fun rebind(range: GradientStopRangeV1, authority: GradientNumericAuthorityV1): GradientV1 =
            copy(stopRange = range, numericAuthority = authority)
    }

    public data class RadialGradientV1(public val centerF32: Point2F32, public val radiusF32: Float,
        override val stopRange: GradientStopRangeV1, public val degeneracy: RadialGradientDegeneracyV1,
        override val numericAuthority: GradientNumericAuthorityV1) : GradientV1 {
        override val versionI32: Int = 1
        override val gradientDegenerate: Boolean get() = degeneracy.radialDegenerate
        override fun copyUniformValuesF32(): List<Float> = listOf(centerF32.x, centerF32.y, radiusF32, 0f)
        override fun rebind(range: GradientStopRangeV1, authority: GradientNumericAuthorityV1): GradientV1 =
            copy(stopRange = range, numericAuthority = authority)
    }

    public data class SweepGradientV1(public val centerF32: Point2F32,
        override val stopRange: GradientStopRangeV1, public val degeneracy: SweepGradientDegeneracyV1,
        override val numericAuthority: GradientNumericAuthorityV1) : GradientV1 {
        override val versionI32: Int = 1
        override val gradientDegenerate: Boolean get() = degeneracy.sweepDegenerate
        override fun copyUniformValuesF32(): List<Float> = listOf(centerF32.x, centerF32.y,
            degeneracy.startAngleDegreesF32, degeneracy.endAngleDegreesF32)
        override fun rebind(range: GradientStopRangeV1, authority: GradientNumericAuthorityV1): GradientV1 =
            copy(stopRange = range, numericAuthority = authority)
    }

    public data class ConicalGradientV1(public val startF32: Point2F32, public val endF32: Point2F32,
        override val stopRange: GradientStopRangeV1, public val degeneracy: ConicalGradientDegeneracyV1,
        override val numericAuthority: GradientNumericAuthorityV1) : GradientV1 {
        override val versionI32: Int = 1
        override val gradientDegenerate: Boolean get() = degeneracy.conicalFullyDegenerate
        override fun copyUniformValuesF32(): List<Float> = listOf(startF32.x, startF32.y, endF32.x, endF32.y)
        override fun rebind(range: GradientStopRangeV1, authority: GradientNumericAuthorityV1): GradientV1 =
            copy(stopRange = range, numericAuthority = authority)
    }

    /** Exact public Solid input, in straight sRGB; the program performs conversion and premultiplication. */
    public class SolidRgbaF32V1 private constructor(private val rgbaF32: ColorF32) : MaterialBindingPlan {
        override val versionI32: Int = 1
        public fun copyRgbaF32(): ColorF32 = ColorF32.of(rgbaF32.red, rgbaF32.green, rgbaF32.blue, rgbaF32.alpha)
        public companion object {
            public fun of(rgbaF32: ColorF32): SolidRgbaF32V1 = SolidRgbaF32V1(
                ColorF32.of(rgbaF32.red, rgbaF32.green, rgbaF32.blue, rgbaF32.alpha),
            )
        }
    }

    public class OpacityF32V1 private constructor(public val alphaF32: Float) : MaterialBindingPlan {
        override val versionI32: Int = 1
        init { require(alphaF32.isFinite() && alphaF32 in 0f..1f) }
        public companion object { public fun of(alphaF32: Float): OpacityF32V1 = OpacityF32V1(alphaF32) }
    }
}

public data class MaterialPlanEntry(public val program: MaterialProgramPlan, public val bindings: MaterialBindingPlan,
    public val stopSlab: GradientStopSlabPlanV1? = null)

public class MaterialPlanTable private constructor(entries: List<MaterialPlanEntry>) {
    private data class StoredEntry(val programIndex: Int, val bindings: MaterialBindingPlan)
    private val storedPrograms: List<MaterialProgramPlan>
    private val storedEntries: List<StoredEntry>
    public val gradientStopSlab: GradientStopSlabPlanV1? = entries.firstNotNullOfOrNull { it.stopSlab }

    init {
        val programIndexById = linkedMapOf<MaterialProgramPlanId, Int>()
        val programs = mutableListOf<MaterialProgramPlan>()
        storedEntries = immutableList(entries.map { entry ->
            val index = programIndexById.getOrPut(entry.program.structuralId) {
                programs += entry.program
                programs.lastIndex
            }
            require(programs[index]::class == entry.program::class) { "Structural program IDs must not alias different programs" }
            StoredEntry(index, entry.bindings)
        })
        storedPrograms = immutableList(programs)
    }
    public val sizeI32: Int get() = storedEntries.size
    /** Number of interned code-shaped programs, deliberately independent from bindings. */
    public val programCountI32: Int get() = storedPrograms.size

    public fun entry(ref: MaterialPlanRef): MaterialPlanEntry = storedEntries.getOrElse(ref.indexI32) {
        throw IllegalArgumentException("Material plan reference is outside the sealed table")
    }.let { MaterialPlanEntry(storedPrograms[it.programIndex], it.bindings, gradientStopSlab) }

    public fun entries(): List<MaterialPlanEntry> = storedEntries.indices.map { entry(MaterialPlanRef(it)) }

    public companion object {
        /** Public bound for a closed W5a material table; rendering fails closed beyond it. */
        public const val MAX_ENTRIES_I32: Int = 2048

        public fun of(entries: List<MaterialPlanEntry>): MaterialPlanTable {
            require(entries.isNotEmpty() && entries.size <= MAX_ENTRIES_I32) {
                "A material table must contain at most $MAX_ENTRIES_I32 entries"
            }
            entries.forEachIndexed { index, entry ->
                when (val program = entry.program) {
                    MaterialProgramPlan.TransparentV1 -> require(entry.bindings is MaterialBindingPlan.EmptyV1) {
                        "Transparent programs require empty bindings"
                    }
                    MaterialProgramPlan.SolidLinearPremulV1 -> require(entry.bindings is MaterialBindingPlan.SolidRgbaF32V1) {
                        "Solid programs require RGBA bindings"
                    }
                    MaterialProgramPlan.LinearGradientClampSrgbV1 -> require(entry.bindings is MaterialBindingPlan.LinearGradientV1 && entry.stopSlab != null)
                    MaterialProgramPlan.RadialGradientClampSrgbV1 -> require(entry.bindings is MaterialBindingPlan.RadialGradientV1 && entry.stopSlab != null)
                    MaterialProgramPlan.SweepGradientClampSrgbV1 -> require(entry.bindings is MaterialBindingPlan.SweepGradientV1 && entry.stopSlab != null)
                    MaterialProgramPlan.ConicalGradientClampSrgbV1 -> require(entry.bindings is MaterialBindingPlan.ConicalGradientV1 && entry.stopSlab != null)
                    is GradientAddressingProgramV2 -> require(entry.bindings is MaterialBindingPlan.LinearGradientV2 &&
                        entry.stopSlab != null && entry.bindings.numericAuthority.authenticates(program,
                            entry.bindings, entry.stopSlab, entry.bindings.numericAuthority.coordinates)) {
                        W5dPlanDiagnostics.CoordinatePlanSchema
                    }
                    is MaterialProgramPlan.OpacityV1 -> {
                        require(entry.bindings is MaterialBindingPlan.OpacityF32V1) {
                            "Opacity programs require opacity bindings"
                        }
                        require(index > 0 && entries[index - 1].program.structuralId == program.child.structuralId) { "Opacity child topology must precede its parent" }
                    }
                }
            }
            val stops = mutableListOf<GradientStopPlanV1>()
            val ranges = linkedMapOf<List<GradientStopPlanV1>, GradientStopRangeV1>()
            val rewritten = entries.map { entry ->
                val binding = entry.bindings
                val originalRange = when (binding) {
                    is MaterialBindingPlan.GradientV1 -> binding.stopRange
                    is MaterialBindingPlan.GradientV2 -> binding.stopRange
                    else -> return@map entry
                }
                val slabStops = requireNotNull(entry.stopSlab).copyStops()
                val firstI64 = originalRange.baseIndexU32.toLong()
                val lastI64 = firstI64 + originalRange.countU32.toLong()
                require(lastI64 <= slabStops.size.toLong())
                val sequence = slabStops.subList(firstI64.toInt(), lastI64.toInt()).toList()
                val range = ranges.getOrPut(sequence) {
                    require((stops.size.toLong() + sequence.size) * 32L <= Int.MAX_VALUE)
                    GradientStopRangeV1(stops.size.toUInt(), sequence.size.toUInt()).also { stops += sequence }
                }
                entry.copy(bindings = when (binding) {
                    is MaterialBindingPlan.GradientV1 -> binding.rebind(range)
                    is MaterialBindingPlan.GradientV2 -> binding.rebind(range)
                })
            }
            val slab = stops.takeIf { it.isNotEmpty() }?.let(GradientStopSlabPlanV1::of)
            return MaterialPlanTable(rewritten.mapIndexed { indexI32, entry ->
                val binding = entry.bindings
                val sealed = when (binding) {
                    is MaterialBindingPlan.GradientV2 -> {
                        val source = entries[indexI32]
                        val original = source.bindings as MaterialBindingPlan.GradientV2
                        entry.copy(bindings = binding.rebind(binding.stopRange, original.numericAuthority.rebase(
                            original, requireNotNull(source.stopSlab), binding.stopRange, requireNotNull(slab))))
                    }
                    is MaterialBindingPlan.GradientV1 -> {
                        val source = entries[indexI32]
                        val original = source.bindings as MaterialBindingPlan.GradientV1
                        entry.copy(bindings = binding.rebind(binding.stopRange, original.numericAuthority.rebase(
                            original, requireNotNull(source.stopSlab), binding.stopRange, requireNotNull(slab))))
                    }
                    else -> entry
                }
                sealed.copy(stopSlab = slab)
            })
        }

        /**
         * Interns sealed lane tables in input order and returns one frame-owned table plus
         * exact old-to-new reference maps.  This is deliberately structural: no material is
         * re-evaluated and every reference remains a table index issued before lowering.
         */
        public fun intern(tables: List<MaterialPlanTable>): MaterialPlanTableInterning {
            require(tables.isNotEmpty()) { "At least one lane table is required" }
            val entries = mutableListOf<MaterialPlanEntry>()
            // A canonical child ref identifies its entire reachable binding chain, not only code shape.
            data class ChainKey(val entryKey: String, val childRef: MaterialPlanRef?)
            val indexByKey = linkedMapOf<ChainKey, Int>()
            val remaps = tables.map { table ->
                val source = table.entries()
                val laneRemap = mutableListOf<MaterialPlanRef>()
                source.forEachIndexed { localIndex, entry ->
                    val childRef = if (entry.program is MaterialProgramPlan.OpacityV1) laneRemap[localIndex - 1] else null
                    val key = ChainKey(entry.interningKey(), childRef)
                    val index = indexByKey.getOrPut(key) {
                        var first = localIndex
                        if (childRef != null && childRef.indexI32 != entries.lastIndex) {
                            // V1 evaluates child at ref - 1. Reuse an existing whole chain, or append
                            // an exact contiguous copy; never append a parent after an unrelated child.
                            while (source[first].program is MaterialProgramPlan.OpacityV1) first--
                        }
                        require(localIndex - first + 1 <= MAX_ENTRIES_I32 - entries.size) {
                            "A material table must contain at most $MAX_ENTRIES_I32 entries"
                        }
                        for (indexToCopy in first..localIndex) entries += source[indexToCopy].copyForInterning()
                        entries.lastIndex
                    }
                    laneRemap += MaterialPlanRef(index)
                }
                laneRemap
            }
            return MaterialPlanTableInterning(of(entries), remaps)
        }
    }
}

/** Immutable result of deterministic frame-wide material-table interning. */
public class MaterialPlanTableInterning internal constructor(
    public val table: MaterialPlanTable,
    laneRemaps: List<List<MaterialPlanRef>>,
) {
    private val storedLaneRemaps: List<List<MaterialPlanRef>> = laneRemaps.map { it.toList() }
    public fun remap(laneOrdinalI32: Int, ref: MaterialPlanRef): MaterialPlanRef =
        storedLaneRemaps.getOrNull(laneOrdinalI32)?.getOrNull(ref.indexI32)
            ?: throw IllegalArgumentException("Material reference is outside its sealed lane table")
    public fun copyLaneRemap(laneOrdinalI32: Int): List<MaterialPlanRef> =
        storedLaneRemaps.getOrElse(laneOrdinalI32) { throw IllegalArgumentException("Unknown material lane") }.toList()
}

private fun MaterialPlanEntry.copyForInterning(): MaterialPlanEntry = MaterialPlanEntry(
    program,
    when (val binding = bindings) {
        MaterialBindingPlan.EmptyV1 -> MaterialBindingPlan.EmptyV1
        is MaterialBindingPlan.GradientV1 -> binding.rebind(binding.stopRange)
        is MaterialBindingPlan.GradientV2 -> binding.rebind(binding.stopRange)
        is MaterialBindingPlan.SolidRgbaF32V1 -> MaterialBindingPlan.SolidRgbaF32V1.of(binding.copyRgbaF32())
        is MaterialBindingPlan.OpacityF32V1 -> MaterialBindingPlan.OpacityF32V1.of(binding.alphaF32)
    }, stopSlab,
)

private fun MaterialPlanEntry.interningKey(): String = buildString {
    append(program.structuralId.value).append('|')
    when (val binding = bindings) {
        MaterialBindingPlan.EmptyV1 -> append("empty")
        is MaterialBindingPlan.GradientV2 -> {
            append(binding.copyUniformValuesF32()).append(binding.gradientDegenerate)
            append(binding.numericAuthority.domainIdentity)
            val values = requireNotNull(stopSlab).copyStops()
            append(GradientStopSlabPlanV1.of(values.subList(binding.stopRange.baseIndexU32.toInt(),
                (binding.stopRange.baseIndexU32 + binding.stopRange.countU32).toInt())).canonicalIdentity)
        }
        is MaterialBindingPlan.GradientV1 -> {
            append(binding.copyUniformValuesF32()).append(binding.gradientDegenerate)
            append(binding.numericAuthority.domainIdentity)
            val values = requireNotNull(stopSlab).copyStops()
            append(GradientStopSlabPlanV1.of(values.subList(binding.stopRange.baseIndexU32.toInt(),
                (binding.stopRange.baseIndexU32 + binding.stopRange.countU32).toInt())).canonicalIdentity)
        }
        is MaterialBindingPlan.SolidRgbaF32V1 -> binding.copyRgbaF32().let { color ->
            append("solid:").append(color.red.toBits()).append(':').append(color.green.toBits()).append(':')
                .append(color.blue.toBits()).append(':').append(color.alpha.toBits())
        }
        is MaterialBindingPlan.OpacityF32V1 -> append("opacity:").append(binding.alphaF32.toBits())
    }
}

/** Closed draw authority: W5 material references cannot coexist with legacy colours. */
public sealed interface PlanDrawMaterialAuthority {
    public data class MaterialV2(public val ref: MaterialPlanRef,
        public val coordinates: MaterialCoordinatePlanV2) : PlanDrawMaterialAuthority
    public data class MaterialV1(public val ref: MaterialPlanRef,
        public val coordinates: MaterialCoordinatePlanV1? = null) : PlanDrawMaterialAuthority

    public class LegacyColorV1 private constructor(private val colorF32: ColorF32) : PlanDrawMaterialAuthority {
        public fun copyColorF32(): ColorF32 = ColorF32.of(colorF32.red, colorF32.green, colorF32.blue, colorF32.alpha)
        public companion object {
            public fun of(colorF32: ColorF32): LegacyColorV1 = LegacyColorV1(
                ColorF32.of(colorF32.red, colorF32.green, colorF32.blue, colorF32.alpha),
            )
        }
    }
}

/** Reference extraction preserves the closed, versioned coordinate owner. */
public fun PlanDrawMaterialAuthority.materialPlanRef(): MaterialPlanRef = when (this) {
    is PlanDrawMaterialAuthority.MaterialV1 -> ref
    is PlanDrawMaterialAuthority.MaterialV2 -> ref
    is PlanDrawMaterialAuthority.LegacyColorV1 -> error("Legacy colors have no material reference")
}

internal fun MaterialPlanTable.coordinatesV2(root: MaterialPlanRef): MaterialCoordinatePlanV2? {
    var indexI32 = root.indexI32
    while (entry(MaterialPlanRef(indexI32)).bindings is MaterialBindingPlan.OpacityF32V1) indexI32--
    return (entry(MaterialPlanRef(indexI32)).bindings as? MaterialBindingPlan.GradientV2)?.numericAuthority?.coordinates
}
