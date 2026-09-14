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
        override val versionI32: Int = if (child.versionI32 == 4) 4 else 1
        override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId("w5a-opacity-v1(${child.structuralId.value})")
        override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 =
            if (versionI32 == 4) NumericOperationGraphV1.colorSourceV4() else NumericOperationGraphV1.opacity()
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
        public val degenerateAverageSrgbaF32: GradientAverageSrgbaF32?
        public fun copyUniformValuesF32(): List<Float>
        public fun rebind(range: GradientStopRangeV1, authority: GradientNumericAuthorityV2 = numericAuthority): GradientV2
    }

    public data class LinearGradientV2(public val startF32: Point2F32, public val endF32: Point2F32,
        override val stopRange: GradientStopRangeV1, public val degeneracy: LinearGradientDegeneracyV1,
        override val numericAuthority: GradientNumericAuthorityV2,
        override val degenerateAverageSrgbaF32: GradientAverageSrgbaF32?) : GradientV2 {
        override val gradientDegenerate: Boolean get() = degeneracy.linearDegenerate
        override fun copyUniformValuesF32(): List<Float> = listOf(startF32.x, startF32.y, endF32.x, endF32.y)
        override fun rebind(range: GradientStopRangeV1, authority: GradientNumericAuthorityV2): GradientV2 =
            copy(stopRange = range, numericAuthority = authority)
    }

    public data class RadialGradientV2(public val centerF32: Point2F32, public val radiusF32: Float,
        override val stopRange: GradientStopRangeV1, public val degeneracy: RadialGradientDegeneracyV1,
        override val numericAuthority: GradientNumericAuthorityV2,
        override val degenerateAverageSrgbaF32: GradientAverageSrgbaF32?) : GradientV2 {
        override val gradientDegenerate: Boolean get() = degeneracy.radialDegenerate
        override fun copyUniformValuesF32(): List<Float> = listOf(centerF32.x, centerF32.y, radiusF32, 0f)
        override fun rebind(range: GradientStopRangeV1, authority: GradientNumericAuthorityV2): GradientV2 =
            copy(stopRange = range, numericAuthority = authority)
    }

    public data class SweepGradientV2(public val centerF32: Point2F32,
        override val stopRange: GradientStopRangeV1, public val degeneracy: SweepGradientDegeneracyV1,
        override val numericAuthority: GradientNumericAuthorityV2,
        override val degenerateAverageSrgbaF32: GradientAverageSrgbaF32?) : GradientV2 {
        override val gradientDegenerate: Boolean get() = degeneracy.sweepDegenerate
        override fun copyUniformValuesF32(): List<Float> = listOf(centerF32.x, centerF32.y,
            degeneracy.startAngleDegreesF32, degeneracy.endAngleDegreesF32)
        override fun rebind(range: GradientStopRangeV1, authority: GradientNumericAuthorityV2): GradientV2 =
            copy(stopRange = range, numericAuthority = authority)
    }

    public data class ConicalGradientV2(public val startF32: Point2F32, public val endF32: Point2F32,
        override val stopRange: GradientStopRangeV1, public val degeneracy: ConicalGradientDegeneracyV1,
        override val numericAuthority: GradientNumericAuthorityV2,
        override val degenerateAverageSrgbaF32: GradientAverageSrgbaF32?) : GradientV2 {
        override val gradientDegenerate: Boolean get() = degeneracy.conicalFullyDegenerate
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

public class MaterialPlanTable private constructor(entries: List<MaterialPlanEntry>,
    proofsV4: Map<Int, ColorSourceProofV1> = emptyMap()) {
    private val storedProofsV4 = java.util.Collections.unmodifiableMap(LinkedHashMap(proofsV4))
    public fun colorSourceProofV4(root: MaterialPlanRef): ColorSourceProofV1 =
        requireNotNull(storedProofsV4[root.indexI32]) { W5fPlanDiagnostics.Schema }.also {
            require(it.authenticates(this, root, it.coordinates)) { W5fPlanDiagnostics.Schema }
        }
    public fun colorSourceProofV5(root: MaterialPlanRef): ColorSourceProofV1 =
        (entry(root).bindings as? ComposedMaterialBindingV5)?.sourceProof?.also {
            require(it.authenticates(this,root,SourceCoordinatesV4.None)) { W5gPlanDiagnostics.Schema }
        } ?: throw IllegalArgumentException(W5gPlanDiagnostics.Schema)
    /** Issue from the actual selected table; callers supply no graph, range or certificate. */
    internal fun sealColorSourceV4(root: MaterialPlanRef,coordinates: SourceCoordinatesV4,
        bounds: org.graphiks.math.geometry.RectF32): MaterialPlanTable {
        val proof = when (val result = ColorSourceProofCompilerV1.seal(this,root,coordinates,bounds)) {
            is ColorSourceProofResultV1.Ready -> result.source
            is ColorSourceProofResultV1.Refused -> throw IllegalArgumentException(result.diagnosticCode)
        }
        return MaterialPlanTable(entries(),storedProofsV4 + (root.indexI32 to proof))
    }
    private data class StoredEntry(val programIndex: Int, val bindings: MaterialBindingPlan)
    private val storedPrograms: List<MaterialProgramPlan>
    private val storedEntries: List<StoredEntry>
    public val gradientStopSlab: GradientStopSlabPlanV1? = entries.firstNotNullOfOrNull { it.stopSlab }

    /** Actual source chain, not the mere presence of a different lane's shared slab. */
    internal fun sourceUsesGradientStopSlab(root: MaterialPlanRef): Boolean {
        var ref = root
        while (true) {
            val source = entry(ref)
            when (source.bindings) {
                is MaterialBindingPlan.GradientV1,is MaterialBindingPlan.GradientV2,is GradientInterpolationBindingV4 -> return true
                is MaterialBindingPlan.OpacityF32V1,is ColorFilterBindingV4 -> {
                    require(ref.indexI32 > 0) { W5fPlanDiagnostics.Schema }
                    ref = MaterialPlanRef(ref.indexI32-1)
                }
                is ImageSampleV3 -> return imageChildAuthority(ref)?.let {
                    sourceUsesGradientStopSlab(it.materialPlanRef())
                } ?: false
                else -> return false
            }
        }
    }

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

    /** Binding/coordinate/stop content identity independent of slab-range rebasing. */
    public fun sourceIdentity(root: MaterialPlanRef): String {
        val source = entry(root)
        if (source.bindings is ComposedMaterialBindingV5) return source.bindings.definition.capturedIdentity
        if (source.bindings is GradientInterpolationBindingV4) return source.bindings.definition.definitionIdentity
        if (source.program is ColorFilteredProgramV4) return ColorSourceProofV1.filteredIdentity(
            sourceIdentity(MaterialPlanRef(root.indexI32 - 1)), (source.bindings as ColorFilterBindingV4).execution)
        return source.interningKey() + if (source.program is MaterialProgramPlan.OpacityV1 || source.program is ImageMaterialProgramV3.MaskV3)
            ":child:" + sourceIdentity(MaterialPlanRef(root.indexI32 - 1)) else ""
    }

    public fun authenticatesImage(root: MaterialPlanRef, execution: ImageSampleExecutionPlanV1): Boolean {
        val source = entry(root)
        val program = source.program as? ImageMaterialProgramV3 ?: return false
        return (source.bindings as? ImageSampleV3)?.execution === execution &&
            execution.numericAuthority.authenticates(program, execution) && when (program) {
                is ImageMaterialProgramV3.ColorV3 -> execution.childSourceIdentity == null
                is ImageMaterialProgramV3.MaskV3 -> root.indexI32 > 0 &&
                    entry(MaterialPlanRef(root.indexI32 - 1)).program.structuralId == program.child.structuralId &&
                    sourceIdentity(MaterialPlanRef(root.indexI32 - 1)) == execution.childSourceIdentity
            }
    }

    internal fun isUnfilteredGradientV4(root: MaterialPlanRef): Boolean {
        var leaf = root
        while (entry(leaf).bindings is MaterialBindingPlan.OpacityF32V1) leaf = MaterialPlanRef(leaf.indexI32-1)
        return entry(leaf).bindings is GradientInterpolationBindingV4
    }

    public fun imageChildAuthority(root: MaterialPlanRef): PlanDrawMaterialAuthority? {
        val execution = (entry(root).bindings as? ImageSampleV3)?.execution ?: return null
        require(authenticatesImage(root, execution)) { W5eImagePlanDiagnostics.InvalidContract }
        if (entry(root).program !is ImageMaterialProgramV3.MaskV3) return null
        val child = MaterialPlanRef(root.indexI32 - 1)
        var leaf = child
        while (entry(leaf).bindings is MaterialBindingPlan.OpacityF32V1) leaf = MaterialPlanRef(leaf.indexI32 - 1)
        return when (val binding = entry(leaf).bindings) {
            is ColorFilterBindingV4 -> PlanDrawMaterialAuthority.MaterialV4(child,binding.numericAuthority.outputSourceProof.coordinates)
            is GradientInterpolationBindingV4 -> PlanDrawMaterialAuthority.MaterialV4(child,binding.sourceProof.coordinates)
            is MaterialBindingPlan.GradientV2 -> PlanDrawMaterialAuthority.MaterialV2(child, binding.numericAuthority.coordinates)
            is MaterialBindingPlan.GradientV1 -> PlanDrawMaterialAuthority.MaterialV1(child, binding.numericAuthority.coordinates)
            else -> PlanDrawMaterialAuthority.MaterialV1(child)
        }
    }

    public companion object {
        /** Public bound for a closed W5a material table; rendering fails closed beyond it. */
        public const val MAX_ENTRIES_I32: Int = 2048

        public fun of(entries: List<MaterialPlanEntry>): MaterialPlanTable {
            require(entries.isNotEmpty() && entries.size <= MAX_ENTRIES_I32) {
                "A material table must contain at most $MAX_ENTRIES_I32 entries"
            }
            entries.forEachIndexed { index, entry ->
                val legacyRange = when (val binding = entry.bindings) {
                    is MaterialBindingPlan.GradientV1 -> binding.stopRange
                    is MaterialBindingPlan.GradientV2 -> binding.stopRange
                    else -> null
                }
                require(legacyRange == null || entry.stopSlab?.rangeHasDomain(legacyRange,
                    org.graphiks.kanvas.render.ir.ColorInterpolation.SRGB) == true) { W5fPlanDiagnostics.Schema }
                when (val program = entry.program) {
                    is ComposedMaterialProgramV5 -> require(entry.bindings is ComposedMaterialBindingV5 &&
                        entry.bindings.authenticates(program)) { W5gPlanDiagnostics.Schema }
                    is GradientInterpolationProgramV4 -> require(entry.bindings is GradientInterpolationBindingV4 &&
                        entry.bindings.authenticates(program,entry.stopSlab)) { W5fPlanDiagnostics.Schema }
                    is ColorFilteredProgramV4 -> require(entry.bindings is ColorFilterBindingV4 && index > 0 &&
                        entries[index-1].program.structuralId == program.child.structuralId &&
                        program.filterStructureIdentity == entry.bindings.execution.structuralIdentity &&
                        entry.bindings.numericAuthority.authenticates(entry.bindings.execution,entry.bindings.sourceProof)) {
                        W5fPlanDiagnostics.Schema
                    }
                    is ImageMaterialProgramV3.ColorV3 -> require(entry.bindings is ImageSampleV3 &&
                        entry.bindings.execution.numericAuthority.authenticates(program, entry.bindings.execution)) {
                        W5eImagePlanDiagnostics.InvalidContract
                    }
                    is ImageMaterialProgramV3.MaskV3 -> {
                        require(entry.bindings is ImageSampleV3 && index > 0 &&
                            entries[index - 1].program.structuralId == program.child.structuralId &&
                            entry.bindings.execution.colorAlpha.channelOrder == ImageChannelOrderV1.ALPHA &&
                            entry.bindings.execution.numericAuthority.authenticates(program, entry.bindings.execution)) {
                            W5eImagePlanDiagnostics.InvalidContract
                        }
                    }
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
                    is GradientAddressingProgramV2 -> require(entry.bindings is MaterialBindingPlan.GradientV2 &&
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
                    is GradientInterpolationBindingV4 -> binding.stopRange
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
                    is GradientInterpolationBindingV4 -> binding
                })
            }
            val slab = stops.takeIf { it.isNotEmpty() }?.let(GradientStopSlabPlanV1::of)
            val rebasedEntries = rewritten.mapIndexed { indexI32, entry ->
                val binding = entry.bindings
                val sealed = when (binding) {
                    is GradientInterpolationBindingV4 -> {
                        val original = requireNotNull(entry.stopSlab).copyStops()
                        val sequence = original.subList(binding.stopRange.baseIndexU32.toInt(),
                            (binding.stopRange.baseIndexU32.toLong()+binding.stopRange.countU32.toLong()).toInt())
                        entry.copy(bindings=binding.rebase(requireNotNull(ranges[sequence]),requireNotNull(slab)))
                    }
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
            }.toMutableList()
            // Interning can select another immutable owner with equal bytes. Reissue the
            // certificate from the actual selected prefix rather than retaining that owner.
            rebasedEntries.indices.forEach { indexI32 ->
                val entry = rebasedEntries[indexI32]
                val binding = entry.bindings as? ColorFilterBindingV4 ?: return@forEach
                val prefix = MaterialPlanTable(rebasedEntries.subList(0,indexI32))
                val child = MaterialPlanRef(indexI32-1)
                if (!binding.sourceProof.authenticates(prefix,child,binding.sourceProof.coordinates)) {
                    val source = (ColorSourceProofCompilerV1.seal(prefix,child,binding.sourceProof.coordinates,
                        binding.sourceProof.deviceBoundsF32) as? ColorSourceProofResultV1.Ready)?.source
                        ?: error(W5fPlanDiagnostics.Schema)
                    val numeric = ColorNumericAuthorityV1.seal(binding.execution,source) ?: error(W5fPlanDiagnostics.NumericDomainUnbounded)
                    rebasedEntries[indexI32] = entry.copy(bindings = ColorFilterBindingV4.seal(binding.execution,source,numeric))
                }
            }
            val table = MaterialPlanTable(rebasedEntries)
            table.entries().forEachIndexed { indexI32, entry ->
                (entry.bindings as? GradientInterpolationBindingV4)?.let {
                    require(it.sourceProof.authenticates(table,MaterialPlanRef(indexI32),it.sourceProof.coordinates)) {
                        W5fPlanDiagnostics.Schema
                    }
                }
                (entry.bindings as? ColorFilterBindingV4)?.let {
                    require(it.sourceProof.authenticates(table,MaterialPlanRef(indexI32-1),it.sourceProof.coordinates) &&
                        it.numericAuthority.outputSourceProof.authenticates(table,MaterialPlanRef(indexI32),it.sourceProof.coordinates)) {
                        W5fPlanDiagnostics.Schema
                    }
                }
                (entry.bindings as? ImageSampleV3)?.let {
                    require(table.authenticatesImage(MaterialPlanRef(indexI32), it.execution)) {
                        W5eImagePlanDiagnostics.InvalidContract
                    }
                }
            }
            val proofs = linkedMapOf<Int, ColorSourceProofV1>()
            table.entries().forEachIndexed { indexI32, entry ->
                val filter = entry.bindings as? ColorFilterBindingV4
                if (entry.bindings is ComposedMaterialBindingV5) proofs[indexI32] = entry.bindings.sourceProof
                else if (entry.bindings is GradientInterpolationBindingV4) proofs[indexI32] = entry.bindings.sourceProof
                else if (filter != null) proofs[indexI32] = filter.numericAuthority.outputSourceProof
                else if (entry.bindings is MaterialBindingPlan.OpacityF32V1 && indexI32 - 1 in proofs) {
                    val child = requireNotNull(proofs[indexI32 - 1])
                    proofs[indexI32] = (ColorSourceProofCompilerV1.seal(table, MaterialPlanRef(indexI32),
                        child.coordinates, child.deviceBoundsF32) as? ColorSourceProofResultV1.Ready)?.source
                        ?: error(W5fPlanDiagnostics.NumericDomainUnbounded)
                }
            }
            return if (proofs.isEmpty()) table else MaterialPlanTable(rebasedEntries, proofs)
        }

        /**
         * Interns sealed lane tables in input order and returns one frame-owned table plus
         * exact old-to-new reference maps.  This is deliberately structural: no material is
         * re-evaluated and every reference remains a table index issued before lowering.
         */
        public fun intern(tables: List<MaterialPlanTable>): MaterialPlanTableInterning {
            val sources = tables.map { it.entries() }
            val recipe = MaterialTableInterningRecipeV4.of(sources.map { source ->
                source.map { it.internerDescriptorV4() }
            })
            val entries = recipe.bind(sources) { it.internerDescriptorV4() }.map { it.copyForInterning() }
            return MaterialPlanTableInterning(of(entries), recipe.laneRemaps())
        }
    }
}

/** A source-independent record of the existing contiguous-child interning algorithm. */
internal data class MaterialInternerDescriptorV4(val identity: String, val unary: Boolean)

internal class MaterialTableInterningRecipeV4 private constructor(
    descriptors: List<List<MaterialInternerDescriptorV4>>,
    positions: List<Pair<Int, Int>>,
    remaps: List<List<MaterialPlanRef>>,
) {
    private val storedDescriptors = immutableList(descriptors.map(::immutableList))
    private val storedPositions = immutableList(positions)
    private val storedRemaps = immutableList(remaps.map(::immutableList))
    val sizeI32: Int get() = storedPositions.size

    fun laneRemaps(): List<List<MaterialPlanRef>> = storedRemaps

    /** Final issuance consumes exactly the checked entries/order, never a second dedup pass. */
    fun <T> bind(sources: List<List<T>>, describe: (T) -> MaterialInternerDescriptorV4): List<T> {
        require(sources.map { lane -> lane.map(describe) } == storedDescriptors) { W5fPlanDiagnostics.Schema }
        return storedPositions.map { (lane, entry) -> sources[lane][entry] }
    }

    companion object {
        fun of(sources: List<List<MaterialInternerDescriptorV4>>): MaterialTableInterningRecipeV4 {
            require(sources.isNotEmpty()) { "At least one lane table is required" }
            require(sources.all { it.isNotEmpty() }) { "A material table must contain at least one entry" }
            data class ChainKey(val entryKey: String, val childRef: MaterialPlanRef?)
            val positions = mutableListOf<Pair<Int, Int>>()
            val indexByKey = linkedMapOf<ChainKey, Int>()
            val remaps = sources.mapIndexed { lane, source ->
                val laneRemap = mutableListOf<MaterialPlanRef>()
                source.forEachIndexed { localIndex, entry ->
                    require(!entry.unary || localIndex > 0) { "Unary child topology must precede its parent" }
                    val childRef = if (entry.unary) laneRemap[localIndex - 1] else null
                    val index = indexByKey.getOrPut(ChainKey(entry.identity, childRef)) {
                        var first = localIndex
                        if (childRef != null && childRef.indexI32 != positions.lastIndex) {
                            // The evaluated child is ref - 1, including copied nested unary chains.
                            while (source[first].unary) first--
                        }
                        require(localIndex - first + 1 <= MaterialPlanTable.MAX_ENTRIES_I32 - positions.size) {
                            "A material table must contain at most ${MaterialPlanTable.MAX_ENTRIES_I32} entries"
                        }
                        for (indexToCopy in first..localIndex) positions += lane to indexToCopy
                        positions.lastIndex
                    }
                    laneRemap += MaterialPlanRef(index)
                }
                laneRemap
            }
            return MaterialTableInterningRecipeV4(sources, positions, remaps)
        }
    }
}

internal fun MaterialPlanEntry.internerDescriptorV4(): MaterialInternerDescriptorV4 = MaterialInternerDescriptorV4(
    interningKey(), program is MaterialProgramPlan.OpacityV1 || program is ImageMaterialProgramV3.MaskV3 ||
        program is ColorFilteredProgramV4,
)

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
        is ComposedMaterialBindingV5 -> binding
        is GradientInterpolationBindingV4 -> binding
        is ColorFilterBindingV4 -> binding
        is ImageSampleV3 -> ImageSampleV3.of(binding.execution)
        MaterialBindingPlan.EmptyV1 -> MaterialBindingPlan.EmptyV1
        is MaterialBindingPlan.GradientV1 -> binding.rebind(binding.stopRange)
        is MaterialBindingPlan.GradientV2 -> binding.rebind(binding.stopRange)
        is MaterialBindingPlan.SolidRgbaF32V1 -> binding
        is MaterialBindingPlan.OpacityF32V1 -> binding
    }, stopSlab,
)

private fun MaterialPlanEntry.interningKey(): String = buildString {
    append(program.structuralId.value).append('|')
    when (val binding = bindings) {
        is ComposedMaterialBindingV5 -> append(binding.definition.capturedIdentity)
        is GradientInterpolationBindingV4 -> append(binding.canonicalIdentity)
        is ColorFilterBindingV4 -> append(binding.canonicalIdentity)
        is ImageSampleV3 -> append(binding.execution.canonicalIdentity)
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
    public data class MaterialV5(public val ref: MaterialPlanRef) : PlanDrawMaterialAuthority
    public data class MaterialV4(public val ref: MaterialPlanRef, public val coordinates: SourceCoordinatesV4) : PlanDrawMaterialAuthority
    public data class MaterialV3(public val ref: MaterialPlanRef,
        public val imageCoordinates: ImageCoordinatePlanV1) : PlanDrawMaterialAuthority
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
    is PlanDrawMaterialAuthority.MaterialV5 -> ref
    is PlanDrawMaterialAuthority.MaterialV4 -> ref
    is PlanDrawMaterialAuthority.MaterialV3 -> ref
    is PlanDrawMaterialAuthority.MaterialV1 -> ref
    is PlanDrawMaterialAuthority.MaterialV2 -> ref
    is PlanDrawMaterialAuthority.LegacyColorV1 -> error("Legacy colors have no material reference")
}

/** Coordinate contract for the existing graph-backed source families only. */
public fun PlanDrawMaterialAuthority.colorSourceCoordinatesV4(): SourceCoordinatesV4? = when(this) {
    is PlanDrawMaterialAuthority.MaterialV5 -> SourceCoordinatesV4.None
    is PlanDrawMaterialAuthority.MaterialV4 -> coordinates
    else -> null
}

public sealed interface SourceCoordinatesV4 {
    public data object None : SourceCoordinatesV4
    public data class V1(public val plan: MaterialCoordinatePlanV1) : SourceCoordinatesV4
    public data class V2(public val plan: MaterialCoordinatePlanV2) : SourceCoordinatesV4
    public data class V3(public val plan: ImageCoordinatePlanV1) : SourceCoordinatesV4
}
internal fun SourceCoordinatesV4.identityV4(): String = when(this) {
    SourceCoordinatesV4.None -> "none-v4"
    is SourceCoordinatesV4.V1 -> plan.canonicalIdentity
    is SourceCoordinatesV4.V2 -> plan.canonicalIdentity
    is SourceCoordinatesV4.V3 -> plan.canonicalIdentity
}

internal fun MaterialPlanTable.coordinatesV2(root: MaterialPlanRef): MaterialCoordinatePlanV2? {
    var indexI32 = root.indexI32
    while (entry(MaterialPlanRef(indexI32)).bindings is MaterialBindingPlan.OpacityF32V1) indexI32--
    return (entry(MaterialPlanRef(indexI32)).bindings as? MaterialBindingPlan.GradientV2)?.numericAuthority?.coordinates
}
internal fun MaterialPlanTable.coordinatesV4(root: MaterialPlanRef): SourceCoordinatesV4? {
    var leaf = root
    while (entry(leaf).bindings is MaterialBindingPlan.OpacityF32V1) leaf = MaterialPlanRef(leaf.indexI32-1)
    return if (entry(leaf).bindings is ColorFilterBindingV4) colorSourceProofV4(root).coordinates else null
}
