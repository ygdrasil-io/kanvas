package org.graphiks.kanvas.gpu.plan

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Typed image composition ABI; existing standalone V1/V2 layouts are unchanged. */
public class ImageSourceLayoutV3 internal constructor(public val hasChildGradientStorage: Boolean,
    public val selectsCells: Boolean = false, public val latticeSelector: Boolean = false,
    public val cellCapacityI32: Int = 9, public val hasAtlasColor: Boolean = false) {
    public val uniformBindingU32: UInt = 0u
    public val gradientStorageBindingU32: UInt? = if (hasChildGradientStorage) 1u else null
    public val imageTextureBindingU32: UInt = if (hasChildGradientStorage) 2u else 1u
    public val imageUniformByteCountI64: Long = Math.addExact(if (latticeSelector)
        Math.addExact(128L, Math.multiplyExact(cellCapacityI32.toLong(), 80L)) else if (selectsCells) 704L else 112L,
        if (hasAtlasColor) 16L else 0L)
    public val structuralIdentity: String = "image-source-layout-v3:uniform0:" +
        (if (hasChildGradientStorage) "stops1:texture2" else "texture1") + ":cubic-parameters" +
        (if (latticeSelector) ":lattice-cell-selector-v1:$cellCapacityI32" else if (selectsCells) ":nine-local-cell-selector-v2" else "") +
        (if (hasAtlasColor) ":atlas-entry-color-v1" else "")
}

/** Handle-free raw V2 binding layout, shared by capability sealing and native packing. */
public class RawMaterialRequirementsV2 private constructor(
    public val bindingCountI32: Int,
    public val uniformByteCountI64: Long,
    public val hasCoordinatesV2: Boolean,
    private val bindGroupEntryCountI32: Int,
    public val structuralId: String,
    private val uniformBytes: ByteArray,
    slabIdentity: String,
    private val imageSource: Boolean = false,
    public val imageLayoutV3: ImageSourceLayoutV3? = null,
) {
    /** The materializer consumes this same immutable packing and allocation identity. */
    public fun copyUniformBytes(): ByteArray = uniformBytes.copyOf()
    public val canonicalIdentity: String = structuralId + ":raw-v2:" + uniformBytes.joinToString(",") + slabIdentity
    /** Native buffers bind at offset zero; alignment never adds a dynamic stride. */
    public fun fitsUniformBinding(capabilities: PlanCapabilitySnapshot): Boolean =
        fitsLegacyUniformBinding(uniformByteCountI64, hasCoordinatesV2, imageSource, bindGroupEntryCountI32, capabilities)

    public class Refusal internal constructor(public val code: String) : IllegalArgumentException(code)

    /** The old ABI's repeatable word recipe; measuring it never allocates a payload. */
    internal class LegacyLayout private constructor(
        val bindingCountI32: Int,
        val uniformByteCountI64: Long,
        val hasCoordinatesV2: Boolean,
        val bindGroupEntryCountI32: Int,
        val structuralId: String,
        val slabIdentity: String,
        private val table: MaterialPlanTable,
        private val root: MaterialPlanRef,
        private val leafI32: Int,
        private val child: LegacyLayout?,
        val imageLayoutV3: ImageSourceLayoutV3?,
    ) {
        fun forEachWord(consume: (Int) -> Unit) = forEachRelocatedWord(emptyMap(),consume)

        internal fun forEachRelocatedWord(ranges: Map<MaterialBindingPlan,GradientStopRangeV1>, consume: (Int) -> Unit) {
            val sink = RawWordSink(consume)
            val image = table.entry(root).bindings as? ImageSampleV3
            if (image == null) writeUniformWords(table, root, leafI32, sink,ranges)
            else writeImageUniformWords(image.execution, child, sink,ranges)
            check(sink.wordCountI64 * 4L == uniformByteCountI64)
        }

        /** An exact historical identity expression with one frame-scoped slab variable.
         * This is metadata only: it has no pack operation and issues no Raw authority. */
        fun relocated(ranges: Map<MaterialBindingPlan,GradientStopRangeV1>, sharedSlabOwner: Any): RelocatedLegacyLayout {
            val captured = java.util.Collections.unmodifiableMap(java.util.IdentityHashMap(ranges))
            fun identity(layout: LegacyLayout): LegacyPhysicalIdentity {
                val image = layout.table.entry(layout.root).bindings as? ImageSampleV3
                val leaf = layout.table.entry(MaterialPlanRef(layout.leafI32)).bindings
                val gradient = leaf is MaterialBindingPlan.GradientV1 || leaf is MaterialBindingPlan.GradientV2
                if (gradient) require(leaf in captured) { W5fPlanDiagnostics.Schema }
                val words = buildString {
                    var first = true
                    layout.forEachRelocatedWord(captured) { word -> repeat(4) { byte ->
                        if (!first) append(',')
                        first = false
                        append((word ushr (byte*8)).toByte())
                    } }
                }
                val suffix = when {
                    image != null -> LegacyPhysicalSuffix.Image(image.execution.canonicalIdentity,layout.child?.let(::identity))
                    gradient -> LegacyPhysicalSuffix.SharedSlab(sharedSlabOwner)
                    else -> LegacyPhysicalSuffix.Empty
                }
                return LegacyPhysicalIdentity(layout.structuralId,words,suffix)
            }
            return RelocatedLegacyLayout(this,identity(this))
        }

        // Preserve byte-for-byte the historical signed Byte.joinToString identity, including
        // its field delimiters. Neither original IR nor device bounds are a substitute.
        val canonicalIdentity: String by lazy {
            buildString {
                append(structuralId).append(":raw-v2:")
                var first = true
                forEachWord { word ->
                    repeat(4) { byte ->
                        if (!first) append(',')
                        first = false
                        append((word ushr (byte * 8)).toByte())
                    }
                }
                append(slabIdentity)
            }
        }

        fun fitsUniformBinding(capabilities: PlanCapabilitySnapshot): Boolean = fitsLegacyUniformBinding(
            uniformByteCountI64, hasCoordinatesV2, imageLayoutV3 != null, bindGroupEntryCountI32, capabilities)

        fun pack(): RawMaterialRequirementsV2 {
            val bytes = ByteBuffer.allocate(Math.toIntExact(uniformByteCountI64)).order(ByteOrder.LITTLE_ENDIAN)
            forEachWord(bytes::putInt)
            check(bytes.position() == bytes.capacity())
            return RawMaterialRequirementsV2(bindingCountI32, uniformByteCountI64, hasCoordinatesV2,
                bindGroupEntryCountI32, structuralId, bytes.array(), slabIdentity,
                imageLayoutV3 != null, imageLayoutV3).also { check(it.canonicalIdentity == canonicalIdentity) }
        }

        companion object {
            fun of(table: MaterialPlanTable, root: MaterialPlanRef): LegacyLayout = measureLegacy(table, root)

            internal fun create(bindingCountI32: Int, bytesI64: Long, hasCoordinatesV2: Boolean,
                entryCountI32: Int, structuralId: String, slabIdentity: String, table: MaterialPlanTable,
                root: MaterialPlanRef, leafI32: Int, child: LegacyLayout? = null,
                imageLayout: ImageSourceLayoutV3? = null): LegacyLayout = LegacyLayout(bindingCountI32,
                bytesI64, hasCoordinatesV2, entryCountI32, structuralId, slabIdentity, table, root,
                leafI32, child, imageLayout)
        }
    }

    internal class RelocatedLegacyLayout internal constructor(val original: LegacyLayout,
        private val identity: LegacyPhysicalIdentity) {
        val uniformByteCountI64: Long get() = original.uniformByteCountI64
        val hasCoordinatesV2: Boolean get() = original.hasCoordinatesV2
        fun sameAllocation(other: RelocatedLegacyLayout): Boolean = identity == other.identity
        fun authenticatesFinal(actual: LegacyLayout, slab: GradientStopSlabPlanV1?): Boolean =
            actual.uniformByteCountI64 == uniformByteCountI64 && actual.structuralId == original.structuralId &&
                actual.canonicalIdentity == identity.resolve(slab?.canonicalIdentity)
        override fun hashCode(): Int = identity.hashCode()
    }
    internal data class LegacyPhysicalIdentity(val structure: String, val signedBytes: String, val suffix: LegacyPhysicalSuffix) {
        fun resolve(slabIdentity: String?): String = structure + ":raw-v2:" + signedBytes + suffix.resolve(slabIdentity)
    }
    internal sealed interface LegacyPhysicalSuffix {
        fun resolve(slabIdentity: String?): String
        data object Empty : LegacyPhysicalSuffix { override fun resolve(slabIdentity: String?): String = "" }
        class SharedSlab(private val frameOwner: Any) : LegacyPhysicalSuffix {
            override fun equals(other: Any?): Boolean = other is SharedSlab && frameOwner === other.frameOwner
            override fun hashCode(): Int = System.identityHashCode(frameOwner)
            override fun resolve(slabIdentity: String?): String = requireNotNull(slabIdentity) { W5fPlanDiagnostics.Schema }
        }
        data class Image(val executionIdentity: String,val child: LegacyPhysicalIdentity?) : LegacyPhysicalSuffix {
            override fun resolve(slabIdentity: String?): String = executionIdentity + (child?.resolve(slabIdentity) ?: "")
        }
    }

    private class RawWordSink(private val consume: (Int) -> Unit) {
        var wordCountI64: Long = 0L
            private set
        fun putInt(value: Int): RawWordSink {
            consume(value)
            wordCountI64 = Math.addExact(wordCountI64, 1L)
            return this
        }
        fun putFloat(value: Float): RawWordSink = putInt(value.toRawBits())
    }

    public companion object {
        internal fun measureV4(table: MaterialPlanTable, root: MaterialPlanRef): MaterialSourceFootprintV4 {
            return MaterialSourceFootprintV4(table,root,table.colorSourceProofV4(root)).also {
                require(it.authenticates()) { W5fPlanDiagnostics.Schema } }
        }
        internal fun requireFrameBudgetV4(sources: List<MaterialSourceFootprintV4>, nonUniformBytesI64: Long,
            budget: PlanBudget, capabilities: PlanCapabilitySnapshot, legacyCode: String): MaterialSourcePackingPermitV4 =
            MaterialSourcePackingPermitV4.issue(sources,nonUniformBytesI64,budget,capabilities,legacyCode)
        internal fun packV4(footprint: MaterialSourceFootprintV4, permit: MaterialSourcePackingPermitV4): RawMaterialRequirementsV2 {
            require(permit.permits(footprint)) { W5fPlanDiagnostics.Schema }
            val bytes = ByteBuffer.allocate(footprint.uniformByteCountI64.toInt()).order(ByteOrder.LITTLE_ENDIAN)
            // Source word gaps are declared zero padding. Only this budget-permitted phase copies values.
            for (wordI64 in 0 until footprint.proof.uniformWordCountI64) {
                val table = footprint.proof.tableRecords.entries.singleOrNull { wordI64 >= it.key && wordI64 < it.key+64L }
                val bitsI32 = if (table == null) footprint.proof.integerWordValuesU32[wordI64]?.toInt()
                    ?: footprint.proof.numericWordBits[wordI64] ?: 0 else {
                    val firstByteI32 = Math.toIntExact((wordI64-table.key)*4L)
                    (0..3).fold(0) { bits, byteI32 -> bits or (table.value[firstByteI32+byteI32].toInt() shl (byteI32*8)) }
                }
                bytes.putInt(bitsI32)
            }
            check(bytes.position() == bytes.capacity())
            return RawMaterialRequirementsV2(1,footprint.uniformByteCountI64,false,footprint.bindingCountI32,
                footprint.table.entry(footprint.root).program.structuralId.value,bytes.array(),footprint.canonicalIdentity,
                footprint.proof.imageExecution != null,footprint.proof.imageLayout)
        }
        public const val BINDING_STRIDE_BYTES_I64: Long = 16L

        /** Geometry, target/readback, snapshots and stops enter exactly once from their owners. */
        public fun requireFrameBudget(
            sources: List<RawMaterialRequirementsV2>,
            nonUniformBytesI64: Long,
            budget: PlanBudget,
            legacyCode: String,
        ) {
            require(nonUniformBytesI64 >= 0L)
            val unique = sources.distinctBy { it.canonicalIdentity }
            fun checkedTotalI64(baseI64: Long, sources: List<RawMaterialRequirementsV2>, code: String): Long {
                val totalI64 = try {
                    sources.fold(baseI64) { totalI64, source -> Math.addExact(totalI64, source.uniformByteCountI64) }
                } catch (_: ArithmeticException) { throw Refusal(code) }
                if (totalI64 > budget.maxFrameLocalBytes) throw Refusal(code)
                return totalI64
            }
            // The owner keeps its refusal when the frame already cannot fit without
            // V2. W5d is causal only when adding its unique physical allocations.
            val baseI64 = checkedTotalI64(nonUniformBytesI64, unique.filterNot { it.hasCoordinatesV2 }, legacyCode)
            checkedTotalI64(baseI64, unique.filter { it.hasCoordinatesV2 }, W5dPlanDiagnostics.CoordinateUniformBudget)
        }

        public fun of(table: MaterialPlanTable, root: MaterialPlanRef): RawMaterialRequirementsV2 =
            measureLegacy(table, root).pack()

        internal fun measureLegacy(table: MaterialPlanTable, root: MaterialPlanRef): LegacyLayout {
            require(root.indexI32 in 0 until table.sizeI32)
            var v4CheckI32 = root.indexI32
            while (true) {
                val candidate = table.entry(MaterialPlanRef(v4CheckI32)).bindings
                require(candidate !is ColorFilterBindingV4) { W5fPlanDiagnostics.Schema }
                if (candidate !is MaterialBindingPlan.OpacityF32V1 || v4CheckI32 == 0) break
                v4CheckI32--
            }
            val image = table.entry(root).bindings as? ImageSampleV3
            if (image != null) {
                val execution = image.execution
                require(table.authenticatesImage(root, execution)) { W5eImagePlanDiagnostics.InvalidContract }
                val child = if (table.entry(root).program is ImageMaterialProgramV3.MaskV3)
                    measureLegacy(table, MaterialPlanRef(root.indexI32 - 1)) else null
                val layout = ImageSourceLayoutV3(child?.bindGroupEntryCountI32 == 2, execution.cellSelection != null,
                    execution.cellSelection?.lattice == true, execution.cellSelection?.capacityI32 ?: 9, execution.atlasBlend != null)
                val bytesI64 = Math.addExact(layout.imageUniformByteCountI64, child?.uniformByteCountI64 ?: 0L)
                require(bytesI64 <= Int.MAX_VALUE) { W5eImagePlanDiagnostics.BindingLimit }
                return LegacyLayout.create(1 + (child?.bindingCountI32 ?: 0), bytesI64,
                    child?.hasCoordinatesV2 == true, 1 + (child?.bindGroupEntryCountI32 ?: 1),
                    table.entry(root).program.structuralId.value + ":" + layout.structuralIdentity + ":" + child?.structuralId.orEmpty(),
                    execution.canonicalIdentity + (child?.canonicalIdentity ?: ""), table, root, root.indexI32, child, layout)
            }
            var indexI32 = root.indexI32
            var countI32 = 1
            while (table.entry(MaterialPlanRef(indexI32)).bindings is MaterialBindingPlan.OpacityF32V1) {
                require(indexI32 > 0) { "Raw material opacity requires its captured child" }
                indexI32--
                countI32 = Math.addExact(countI32, 1)
            }
            val entry = table.entry(MaterialPlanRef(indexI32))
            val binding = entry.bindings
            val gradient = binding is MaterialBindingPlan.GradientV1
            if (binding is MaterialBindingPlan.GradientV1) {
                require(binding.numericAuthority.authenticates(entry.program, binding,
                    requireNotNull(table.gradientStopSlab), binding.numericAuthority.coordinates)) {
                    W5cPlanDiagnostics.NumericDomainUnbounded
                }
            }
            if (binding is MaterialBindingPlan.GradientV2) {
                val coordinates = requireNotNull(table.coordinatesV2(root)) { W5dPlanDiagnostics.CoordinatePlanSchema }
                val slab = requireNotNull(table.gradientStopSlab) { W5dPlanDiagnostics.CoordinatePlanSchema }
                val program = entry.program as? GradientAddressingProgramV2
                require(program != null && binding.numericAuthority.authenticates(program, binding, slab, coordinates)) {
                    W5dPlanDiagnostics.CoordinatePlanSchema
                }
                val endI64 = Math.addExact(binding.stopRange.baseIndexU32.toLong(), binding.stopRange.countU32.toLong())
                require(binding.stopRange.countU32 > 0u && endI64 <= UInt.MAX_VALUE.toLong() &&
                    endI64 <= Int.MAX_VALUE.toLong() && endI64 <= slab.copyStops().size.toLong()) {
                    W5dPlanDiagnostics.CoordinatePlanSchema
                }
            }
            val bytesI64 = Math.addExact(
                Math.multiplyExact(countI32.toLong(), BINDING_STRIDE_BYTES_I64),
                when (binding) {
                    is MaterialBindingPlan.GradientV2 -> {
                        val familyBytesI64 = when (binding) {
                            is MaterialBindingPlan.LinearGradientV2 -> 32L
                            is MaterialBindingPlan.RadialGradientV2 -> 0L
                            is MaterialBindingPlan.SweepGradientV2 -> 16L
                            is MaterialBindingPlan.ConicalGradientV2 -> 96L
                        }
                        // Two headers, family fields, optional straight average, then
                        // the exact ordered coordinate fields. The stop-buffer ABI is separate.
                        listOf(32L, familyBytesI64, if (binding.degenerateAverageSrgbaF32 != null) 16L else 0L,
                            requireNotNull(table.coordinatesV2(root)).uniformByteSizeI64).fold(0L, Math::addExact)
                    }
                    is MaterialBindingPlan.LinearGradientV1 -> 112L // common 80 + 2 sealed scalar vectors
                    is MaterialBindingPlan.ConicalGradientV1 -> 176L // common 80 + 4 scalar vectors + 2 flag vectors
                    is MaterialBindingPlan.SweepGradientV1 -> 96L
                    else -> if (gradient) 80L else 0L
                })
            require(bytesI64 <= UInt.MAX_VALUE.toLong() && bytesI64 <= Int.MAX_VALUE.toLong()) {
                if (binding is MaterialBindingPlan.GradientV2) W5dPlanDiagnostics.CoordinateUniformBudget
                else "resource-limit.w5b.source-binding"
            }
            return LegacyLayout.create(countI32, bytesI64, binding is MaterialBindingPlan.GradientV2,
                if (gradient || binding is MaterialBindingPlan.GradientV2) 2 else 1,
                table.entry(root).program.structuralId.value + ":srgb-endpoints-v1",
                if (gradient || binding is MaterialBindingPlan.GradientV2) requireNotNull(table.gradientStopSlab).canonicalIdentity else "",
                table, root, indexI32)
        }

        /** Raw uniform ABI only; stop-buffer storage and native ownership remain W5c. */
        private fun writeUniformWords(table: MaterialPlanTable, root: MaterialPlanRef, leafI32: Int, uniforms: RawWordSink,
            ranges: Map<MaterialBindingPlan,GradientStopRangeV1> = emptyMap()) {
            for (indexI32 in leafI32..root.indexI32) {
                when (val binding = table.entry(MaterialPlanRef(indexI32)).bindings) {
                    is GradientInterpolationBindingV4 -> error(W5fPlanDiagnostics.Schema)
                    is ColorFilterBindingV4 -> error(W5fPlanDiagnostics.Schema)
                    is ImageSampleV3 -> error(W5eImagePlanDiagnostics.InvalidContract)
                    is MaterialBindingPlan.GradientV1 -> binding.copyUniformValuesF32().forEach(uniforms::putFloat)
                    is MaterialBindingPlan.GradientV2 -> binding.copyUniformValuesF32().forEach(uniforms::putFloat)
                    MaterialBindingPlan.EmptyV1 -> repeat(4) { uniforms.putFloat(0f) }
                    is MaterialBindingPlan.SolidRgbaF32V1 -> binding.copyRgbaF32().let {
                        listOf(it.red, it.green, it.blue, it.alpha).forEach(uniforms::putFloat)
                    }
                    is MaterialBindingPlan.OpacityF32V1 -> {
                        uniforms.putFloat(binding.alphaF32)
                        repeat(3) { uniforms.putFloat(0f) }
                    }
                }
            }
            val binding = table.entry(MaterialPlanRef(leafI32)).bindings
            val range = ranges[binding] ?: when (binding) {
                is MaterialBindingPlan.GradientV1 -> binding.stopRange
                is MaterialBindingPlan.GradientV2 -> binding.stopRange
                else -> null
            }
            if (range != null) {
                uniforms.putInt(range.baseIndexU32.toInt()).putInt(range.countU32.toInt()).putInt(0).putInt(0)
                val degeneracy = when (binding) {
                    is MaterialBindingPlan.LinearGradientV1 -> binding.degeneracy
                    is MaterialBindingPlan.RadialGradientV1 -> binding.degeneracy
                    is MaterialBindingPlan.SweepGradientV1 -> binding.degeneracy
                    is MaterialBindingPlan.ConicalGradientV1 -> binding.degeneracy
                    is MaterialBindingPlan.LinearGradientV2 -> binding.degeneracy
                    is MaterialBindingPlan.RadialGradientV2 -> binding.degeneracy
                    is MaterialBindingPlan.SweepGradientV2 -> binding.degeneracy
                    is MaterialBindingPlan.ConicalGradientV2 -> binding.degeneracy
                    else -> error("Gradient range without family")
                }
                val degenerate = when (binding) {
                    is MaterialBindingPlan.GradientV1 -> binding.gradientDegenerate
                    is MaterialBindingPlan.GradientV2 -> binding.gradientDegenerate
                }
                val sweep = degeneracy as? SweepGradientDegeneracyV1
                uniforms.putInt(if (degenerate) 1 else 0)
                    .putInt(if (sweep?.sweepOrderingInvalid == true) 1 else 0)
                    .putInt(if (sweep?.sweepClampLeadingSegment == true) 1 else 0)
                    .putInt(if (sweep?.sweepFullCoverage == true) 1 else 0)
                when (degeneracy) {
                    is LinearGradientDegeneracyV1 -> {
                        degeneracy.copyScalarsF32().forEach(uniforms::putFloat)
                        repeat(2) { uniforms.putFloat(0f) }
                    }
                    is SweepGradientDegeneracyV1 -> {
                        uniforms.putFloat(degeneracy.sweepSpanDegreesF32)
                        repeat(3) { uniforms.putFloat(0f) }
                    }
                    is ConicalGradientDegeneracyV1 -> {
                        degeneracy.copyScalarsF32().forEach(uniforms::putFloat)
                        repeat(3) { uniforms.putFloat(0f) }
                        listOf(degeneracy.conicalLinearEquation, degeneracy.conicalCentersCoincident, degeneracy.conicalRadiiEqual,
                            degeneracy.conicalFullyDegenerate, degeneracy.conicalConcentric, degeneracy.conicalSharedRadiusAboveEpsilon)
                            .forEach { uniforms.putInt(if (it) 1 else 0) }
                        uniforms.putInt(degeneracy.conicalBranchTagU32.toInt()).putInt(0)
                    }
                    is RadialGradientDegeneracyV1 -> Unit
                }
                if (binding is MaterialBindingPlan.GradientV1) {
                    val inverseF32 = binding.numericAuthority.coordinates.copyInverseCtmF32()
                    listOf(inverseF32.sx, inverseF32.kx, inverseF32.tx, 0f, inverseF32.ky, inverseF32.sy, inverseF32.ty, 0f,
                        inverseF32.persp0, inverseF32.persp1, inverseF32.persp2, 0f).forEach(uniforms::putFloat)
                } else if (binding is MaterialBindingPlan.GradientV2) {
                    binding.degenerateAverageSrgbaF32?.let {
                        listOf(it.redF32, it.greenF32, it.blueF32, it.alphaF32).forEach(uniforms::putFloat)
                    }
                    for (operation in requireNotNull(table.coordinatesV2(root)).copyOperations()) when (operation) {
                        is MaterialCoordinateOperationV2.InverseMatrixF32 -> {
                            val matrixF32 = operation.inverseF32
                            // A normal affine flag avoids shader FTZ of subnormal perspective coefficients.
                            val affineF32 = if (matrixF32.persp0 == 0f && matrixF32.persp1 == 0f && matrixF32.persp2 == 1f) 1f else 0f
                            listOf(matrixF32.sx, matrixF32.kx, matrixF32.tx, 0f, matrixF32.ky, matrixF32.sy, matrixF32.ty, 0f,
                                matrixF32.persp0, matrixF32.persp1, matrixF32.persp2, affineF32).forEach(uniforms::putFloat)
                        }
                        is MaterialCoordinateOperationV2.ClampRectF32 -> operation.subsetF32.let {
                            listOf(it.left, it.top, it.right, it.bottom).forEach(uniforms::putFloat)
                        }
                    }
                }
            }
        }

        internal fun forEachImageHeaderWord(execution: ImageSampleExecutionPlanV1, consume: (Int)->Unit) =
            writeImageHeaderWords(execution,RawWordSink(consume))
        internal fun forEachImageHeaderWord(coordinates: ImageCoordinatePlanV1,upload: ImageUploadPlanV1,
            paintAlphaF32: Float,sampling: ImageSamplingPlanV1,selection: ImageCellSelectionPlanV1?,
            atlasColor: org.graphiks.math.color.ColorARGB?,consume: (Int)->Unit) =
            writeImageHeaderWords(coordinates,upload,paintAlphaF32,sampling,selection,atlasColor,RawWordSink(consume))

        private fun writeImageUniformWords(execution: ImageSampleExecutionPlanV1, child: LegacyLayout?,
            uniforms: RawWordSink, ranges: Map<MaterialBindingPlan,GradientStopRangeV1> = emptyMap()) {
            writeImageHeaderWords(execution,uniforms)
            child?.forEachRelocatedWord(ranges,uniforms::putInt)
        }

        private fun writeImageHeaderWords(execution: ImageSampleExecutionPlanV1, uniforms: RawWordSink) =
            writeImageHeaderWords(execution.coordinates,execution.upload,execution.paintAlphaF32,execution.sampling,
                execution.cellSelection,execution.atlasBlend?.color,uniforms)
        private fun writeImageHeaderWords(coordinates: ImageCoordinatePlanV1,upload: ImageUploadPlanV1,
            paintAlphaF32: Float,sampling: ImageSamplingPlanV1,selection: ImageCellSelectionPlanV1?,
            atlasColor: org.graphiks.math.color.ColorARGB?,uniforms: RawWordSink) = with(uniforms) {
            coordinates.uniformValuesF32().forEach(::putFloat)
            putFloat(upload.widthI32.toFloat()).putFloat(upload.heightI32.toFloat())
            putFloat(paintAlphaF32).putFloat(selection?.cells?.size?.toFloat() ?: 0f)
            val cubic = sampling as? ImageSamplingPlanV1.Cubic
            putFloat(cubic?.bF32 ?: 0f).putFloat(cubic?.cF32 ?: 0f).putFloat(0f).putFloat(0f)
            selection?.let { selection ->
                selection.copyDirectionUniformValuesF32().forEach(::putFloat)
                repeat(selection.capacityI32) { indexI32 ->
                    val cell = selection.cells.getOrNull(indexI32)
                    val sample = selection.samples.firstOrNull { it.cell === cell }
                    if (cell == null) repeat(16) { putFloat(0f) } else {
                        if (sample == null) repeat(8) { putFloat(0f) }
                        else sample.coordinates.uniformValuesF32().drop(12).forEach(::putFloat)
                        cell.outerEdges.forEach { putFloat(if (it) 1f else 0f) }
                        val bounds = cell.copyDestinationF32()
                        listOf(bounds.left, bounds.top, bounds.right, bounds.bottom).forEach(::putFloat)
                    }
                    if (selection.lattice) {
                        val color = (cell as? ImageCellPlanV1.SolidV1)?.color
                        listOf(color?.redNormalized ?: 0f, color?.greenNormalized ?: 0f,
                            color?.blueNormalized ?: 0f, color?.alphaNormalized ?: 0f).forEach(::putFloat)
                    }
                }
            }
            atlasColor?.let { listOf(it.redNormalized,it.greenNormalized,it.blueNormalized,it.alphaNormalized).forEach(::putFloat) }
        }

        private fun fitsLegacyUniformBinding(bytesI64: Long, hasCoordinatesV2: Boolean, imageSource: Boolean,
            entryCountI32: Int, capabilities: PlanCapabilitySnapshot): Boolean =
            bytesI64 in 1L..Int.MAX_VALUE.toLong() && bytesI64 <= UInt.MAX_VALUE.toLong() &&
                bytesI64 % BINDING_STRIDE_BYTES_I64 == 0L &&
                capabilities.maxUniformBufferBindingSizeBytesI64?.let { bytesI64 <= it } == true &&
                bytesI64 <= capabilities.maxBufferSizeBytes &&
                (!(hasCoordinatesV2 || imageSource) || capabilities.minUniformBufferOffsetAlignment.let {
                    it > 0 && it and (it - 1) == 0 } &&
                    capabilities.maxBindingsPerBindGroupI32?.let { it >= entryCountI32 } == true &&
                    capabilities.maxUniformBuffersPerShaderStageI32?.let { it >= 2 } == true &&
                    capabilities.maxBindGroupsI32?.let { it >= 2 } == true)
    }
}
