package org.graphiks.kanvas.gpu.plan

import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Typed image composition ABI; existing standalone V1/V2 layouts are unchanged. */
public class ImageSourceLayoutV3 internal constructor(public val hasChildGradientStorage: Boolean) {
    public val uniformBindingU32: UInt = 0u
    public val gradientStorageBindingU32: UInt? = if (hasChildGradientStorage) 1u else null
    public val imageTextureBindingU32: UInt = if (hasChildGradientStorage) 2u else 1u
    public val imageUniformByteCountI64: Long = 96L
    public val structuralIdentity: String = "image-source-layout-v3:uniform0:" +
        if (hasChildGradientStorage) "stops1:texture2" else "texture1"
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
        uniformByteCountI64 in 1L..Int.MAX_VALUE.toLong() &&
            uniformByteCountI64 <= UInt.MAX_VALUE.toLong() &&
            uniformByteCountI64 % BINDING_STRIDE_BYTES_I64 == 0L &&
            capabilities.maxUniformBufferBindingSizeBytesI64?.let { uniformByteCountI64 <= it } == true &&
            uniformByteCountI64 <= capabilities.maxBufferSizeBytes &&
            (!(hasCoordinatesV2 || imageSource) || capabilities.minUniformBufferOffsetAlignment.let { it > 0 && it and (it - 1) == 0 } &&
                capabilities.maxBindingsPerBindGroupI32?.let { it >= bindGroupEntryCountI32 } == true &&
                capabilities.maxUniformBuffersPerShaderStageI32?.let { it >= 2 } == true &&
                capabilities.maxBindGroupsI32?.let { it >= 2 } == true)

    public class Refusal internal constructor(public val code: String) : IllegalArgumentException(code)

    public companion object {
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

        public fun of(table: MaterialPlanTable, root: MaterialPlanRef): RawMaterialRequirementsV2 {
            require(root.indexI32 in 0 until table.sizeI32)
            val image = table.entry(root).bindings as? ImageSampleV3
            if (image != null) {
                val execution = image.execution
                require(table.authenticatesImage(root, execution)) { W5eImagePlanDiagnostics.InvalidContract }
                val child = if (table.entry(root).program is ImageMaterialProgramV3.MaskV3)
                    of(table, MaterialPlanRef(root.indexI32 - 1)) else null
                val layout = ImageSourceLayoutV3(child?.bindGroupEntryCountI32 == 2)
                val bytesI64 = Math.addExact(layout.imageUniformByteCountI64, child?.uniformByteCountI64 ?: 0L)
                require(bytesI64 <= Int.MAX_VALUE) { W5eImagePlanDiagnostics.BindingLimit }
                val bytes = ByteBuffer.allocate(bytesI64.toInt()).order(ByteOrder.LITTLE_ENDIAN).apply {
                    execution.coordinates.uniformValuesF32().forEach(::putFloat)
                    putFloat(execution.upload.widthI32.toFloat()).putFloat(execution.upload.heightI32.toFloat())
                    putFloat(execution.paintAlphaF32).putFloat(0f)
                    child?.copyUniformBytes()?.let(::put)
                }.array()
                return RawMaterialRequirementsV2(1 + (child?.bindingCountI32 ?: 0), bytesI64,
                    child?.hasCoordinatesV2 == true, 1 + (child?.bindGroupEntryCountI32 ?: 1),
                    table.entry(root).program.structuralId.value + ":" + layout.structuralIdentity + ":" + child?.structuralId.orEmpty(),
                    bytes, execution.canonicalIdentity + (child?.canonicalIdentity ?: ""), imageSource = true, imageLayoutV3 = layout)
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
            return RawMaterialRequirementsV2(countI32, bytesI64, binding is MaterialBindingPlan.GradientV2,
                if (gradient || binding is MaterialBindingPlan.GradientV2) 2 else 1,
                table.entry(root).program.structuralId.value + ":srgb-endpoints-v1",
                packUniformBytes(table, root, indexI32, bytesI64.toInt()),
                if (gradient || binding is MaterialBindingPlan.GradientV2) requireNotNull(table.gradientStopSlab).canonicalIdentity else "")
        }

        /** Raw uniform ABI only; stop-buffer storage and native ownership remain W5c. */
        private fun packUniformBytes(table: MaterialPlanTable, root: MaterialPlanRef, leafI32: Int, bytesI32: Int): ByteArray {
            val uniforms = ByteBuffer.allocate(bytesI32).order(ByteOrder.LITTLE_ENDIAN)
            for (indexI32 in leafI32..root.indexI32) {
                when (val binding = table.entry(MaterialPlanRef(indexI32)).bindings) {
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
            val range = when (binding) {
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
            check(uniforms.position() == uniforms.capacity())
            return uniforms.array()
        }
    }
}
