package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.MaterialNode
import org.graphiks.kanvas.render.ir.ColorInterpolation

/** Code shape only: the addressing program is not an evaluated sRGB child. */
public class GradientInterpolationProgramV4(public val addressing: GradientAddressingProgramV2,
    public val domain: ColorInterpolation) : MaterialProgramPlan {
    override val versionI32: Int = 4
    override val structuralId: MaterialProgramPlanId = MaterialProgramPlanId(
        "gradient-interpolation-v4:$domain:oklab-srgb-2021-v1:polar-achromatic-original-srgb-v1:shortest-positive-tie-v1:${addressing.structuralId.value}")
    override fun copyNumericOperationGraphV1(): NumericOperationGraphV1 = NumericOperationGraphV1.colorSourceV4()
}

/** Issued only from the exact immutable definition proved by the common compiler. */
public class GradientInterpolationBindingV4 private constructor(
    internal val definition: PreparedSourceDefinitionV4,
    public val sourceProof: ColorSourceProofV1,
) : MaterialBindingPlan {
    override val versionI32: Int = 4
    public val stopRange: GradientStopRangeV1 get() = definition.range
    public val canonicalIdentity: String = "gradient-interpolation-binding-v4:${definition.executionIdentity}:${sourceProof.canonicalIdentity}"
    internal fun authenticates(program: GradientInterpolationProgramV4,slab: GradientStopSlabPlanV1?): Boolean =
        slab === definition.slab && program.domain == definition.domain &&
            program.addressing.structuralId == definition.addressing.structuralId &&
            sourceProof.preparedDefinition === definition && sourceProof.sourceIdentity == definition.definitionIdentity
    internal fun rebase(range: GradientStopRangeV1,slab: GradientStopSlabPlanV1): GradientInterpolationBindingV4 =
        if (range == stopRange && slab === definition.slab) this else seal(definition.rebase(range,slab))
    internal companion object {
        fun seal(definition: PreparedSourceDefinitionV4): GradientInterpolationBindingV4 {
            val proof = ColorSourceProofCompilerV1.sealPrepared(definition)
                ?: throw RawMaterialRequirementsV2.Refusal(W5fPlanDiagnostics.NumericDomainUnbounded)
            require(proof.preparedDefinition === definition) { W5fPlanDiagnostics.Schema }
            return GradientInterpolationBindingV4(definition,proof)
        }
    }
}

/** Header/family/average fields precede the existing ordered coordinate ABI. */
internal object GradientInterpolationUniformLayoutV4 {
    const val HEADER_WORD_COUNT_I32: Int = 28
    fun leafByteCountI64(source: MaterialSourceConstructionV4): Long {
        val gradient = requireNotNull(source.gradient) { W5fPlanDiagnostics.Schema }
        if (gradient.stops.solidColor != null) return 16L
        val coordinates = source.coordinates as? SourceCoordinatesV4.V2 ?: error(W5fPlanDiagnostics.Schema)
        return Math.addExact(HEADER_WORD_COUNT_I32*4L,coordinates.plan.uniformByteSizeI64)
    }
    fun sourceByteCountI64(source: MaterialSourceConstructionV4): Long =
        requireNotNull(source.gradient).wrappers.fold(leafByteCountI64(source)) { bytes,wrapper ->
            Math.addExact(bytes,if (wrapper is SourceUnaryMetadataV4.Opacity) 16L else 0L)
        }
    fun uniformByteCountI64(source: MaterialSourceConstructionV4): Long =
        requireNotNull(source.gradient).wrappers.fold(leafByteCountI64(source)) { bytes,wrapper ->
            Math.addExact(bytes,when (wrapper) {
                is SourceUnaryMetadataV4.Opacity -> 16L
                is SourceUnaryMetadataV4.Filter -> wrapper.execution.dynamicByteCountI64
            })
        }
}

/** Immutable prepared inputs, not a MaterialBindingPlan, table or numerical certificate.
 * The sole first producer requires the checked final frame and its actual prepared data. */
internal class PreparedSourceDefinitionV4 private constructor(
    val frameOwner: FrameSourceLayoutV4,
    val captured: MaterialSourceConstructionV4,
    val slab: GradientStopSlabPlanV1,
    val range: GradientStopRangeV1,
) {
    val metadata = requireNotNull(captured.gradient)
    val domain: ColorInterpolation = metadata.interpolation
    val coordinates: MaterialCoordinatePlanV2 = (captured.coordinates as SourceCoordinatesV4.V2).plan
    val deviceBoundsF32 get() = captured.deviceBoundsF32
    val addressing: GradientAddressingProgramV2 = GradientAddressingProgramV2(metadata.family,
        metadata.tile.requestedMode,metadata.tile.effectiveMode,metadata.tile.contractId,coordinates.topologyIdentity,
        metadata.degeneracy.consumesAverage(metadata.tile.effectiveMode))
    val average: GradientAverageSrgbaF32? = if (addressing.consumesDegenerateAverage) slab.exactAverageSrgbaF32(range) else null
    val uniformWordCountI64: Long = GradientInterpolationUniformLayoutV4.leafByteCountI64(captured)/4L
    val integerWordsU32: Map<Long,UInt>
    val numericWordsF32Bits: Map<Long,Int>
    val preparedRangeIdentity: String
    /** Stable pre-preparation allocation key; authenticity is checked against this exact definition. */
    val allocationIdentity: String = captured.canonicalIdentity
    /** Includes all semantic original/prepared bits, excluding only physical relocation and future proof. */
    val definitionIdentity: String
    val executionIdentity: String

    init {
        require(frameOwner.owns(captured) && metadata.stops.solidColor == null && slab.rangeHasDomain(range,domain)) {
            W5fPlanDiagnostics.Schema
        }
        val stops = slab.copyStops().subList(range.baseIndexU32.toInt(),
            (range.baseIndexU32.toLong()+range.countU32.toLong()).toInt())
        require(stops.size == metadata.stops.countI32) { W5fPlanDiagnostics.Schema }
        val originals = metadata.stops.values().iterator()
        stops.forEach { stop ->
            val original = originals.next()
            val c = original.color
            require(stop.positionF32.toRawBits() == original.positionF32.toRawBits() &&
                stop.straightSrgbF32.red.toRawBits() == c.redNormalized.toRawBits() &&
                stop.straightSrgbF32.green.toRawBits() == c.greenNormalized.toRawBits() &&
                stop.straightSrgbF32.blue.toRawBits() == c.blueNormalized.toRawBits() &&
                stop.straightSrgbF32.alpha.toRawBits() == c.alphaNormalized.toRawBits() &&
                stop.preparationRecipeIdentity == metadata.recipeIdentity) { W5fPlanDiagnostics.Schema }
        }
        require(!originals.hasNext()) { W5fPlanDiagnostics.Schema }
        preparedRangeIdentity = stops.joinToString(";") { stop ->
            "${stop.positionF32.toRawBits()}:${stop.domain}:${stop.preparationRecipeIdentity}:" +
                with(stop.straightSrgbF32) { listOf(red,green,blue,alpha) }.joinToString(",") { it.toRawBits().toString() } + ":" +
                with(stop.preparedTupleF32) { listOf(red,green,blue,alpha) }.joinToString(",") { it.toRawBits().toString() }
        }
        val words = (0L until uniformWordCountI64).associateWithTo(linkedMapOf()) { 0f.toRawBits() }
        val integers = linkedMapOf(0L to range.baseIndexU32,1L to range.countU32,2L to 0u,3L to 0u,22L to 0u,23L to 0u)
        fun f(offset: Long,value: Float) { require(value.isFinite()) { W5fPlanDiagnostics.Schema }; words[offset] = value.toRawBits() }
        fun tuple(offset: Long,values: List<Float>) = values.forEachIndexed { index,value -> f(offset+index,value) }
        when (val leaf = metadata.leaf) {
            is MaterialNode.LinearGradient -> tuple(4L,listOf(leaf.start.x,leaf.start.y,leaf.end.x,leaf.end.y))
            is MaterialNode.RadialGradient -> tuple(4L,listOf(leaf.center.x,leaf.center.y,leaf.radius,0f))
            is MaterialNode.SweepGradient -> tuple(4L,listOf(leaf.center.x,leaf.center.y,leaf.startAngle,leaf.endAngle))
            is MaterialNode.ConicalGradient -> tuple(4L,listOf(leaf.start.x,leaf.start.y,leaf.end.x,leaf.end.y))
            else -> error(W5fPlanDiagnostics.Schema)
        }
        when (val value = metadata.degeneracy) {
            is LinearGradientDegeneracyV1 -> {
                tuple(8L,listOf(value.linearDxF32,value.linearDyF32,value.linearLen2F32))
                integers[2L] = if (value.linearDegenerate) 1u else 0u
            }
            is RadialGradientDegeneracyV1 -> integers[2L] = if (value.radialDegenerate) 1u else 0u
            is SweepGradientDegeneracyV1 -> {
                f(12L,value.sweepSpanDegreesF32)
                integers[2L] = if (value.sweepDegenerate) 1u else 0u
                integers[3L] = if (value.sweepClampLeadingSegment) 1u else 0u
            }
            is ConicalGradientDegeneracyV1 -> {
                tuple(16L,listOf(value.conicalDxF32,value.conicalDyF32,value.conicalStartRadiusF32,
                    value.conicalEndRadiusF32,value.conicalDrF32,value.conicalAF32))
                integers[2L] = if (value.conicalFullyDegenerate) 1u else 0u
                integers[22L] = value.conicalBranchTagU32
                integers[23L] = if (value.conicalSharedRadiusAboveEpsilon) 1u else 0u
            }
        }
        average?.let { tuple(24L,listOf(it.redF32,it.greenF32,it.blueF32,it.alphaF32)) }
        var coordinateOffset = GradientInterpolationUniformLayoutV4.HEADER_WORD_COUNT_I32.toLong()
        coordinates.copyOperations().forEach { operation -> when (operation) {
            is MaterialCoordinateOperationV2.InverseMatrixF32 -> {
                val m = operation.inverseF32
                tuple(coordinateOffset,listOf(m.sx,m.kx,m.tx,0f,m.ky,m.sy,m.ty,0f,m.persp0,m.persp1,m.persp2,
                    if (m.persp0 == 0f && m.persp1 == 0f && m.persp2 == 1f) 1f else 0f))
                coordinateOffset += 12L
            }
            is MaterialCoordinateOperationV2.ClampRectF32 -> {
                val r = operation.subsetF32
                tuple(coordinateOffset,listOf(r.left,r.top,r.right,r.bottom)); coordinateOffset += 4L
            }
        } }
        require(coordinateOffset == uniformWordCountI64) { W5fPlanDiagnostics.Schema }
        integers.keys.forEach(words::remove)
        integerWordsU32 = java.util.Collections.unmodifiableMap(integers)
        numericWordsF32Bits = java.util.Collections.unmodifiableMap(words)
        definitionIdentity = "gradient-source-definition-v4:$allocationIdentity:${addressing.structuralId.value}:$domain:" +
            "${metadata.recipeIdentity}:${metadata.degeneracy}:$average:$preparedRangeIdentity:" +
            "${coordinates.canonicalIdentity}:${numericWordsF32Bits}:${integerWordsU32.filterKeys { it > 1L }}"
        executionIdentity = "$definitionIdentity:physical=$range:${slab.canonicalIdentity}"
    }

    internal fun rebase(newRange: GradientStopRangeV1,newSlab: GradientStopSlabPlanV1): PreparedSourceDefinitionV4 {
        fun selected(slab: GradientStopSlabPlanV1,range: GradientStopRangeV1): List<GradientStopPlanV1> {
            val stops = slab.copyStops()
            val end = range.baseIndexU32.toLong()+range.countU32.toLong()
            require(end <= stops.size.toLong()) { W5fPlanDiagnostics.Schema }
            return stops.subList(range.baseIndexU32.toInt(),end.toInt())
        }
        require(selected(slab,range) == selected(newSlab,newRange)) { W5fPlanDiagnostics.Schema }
        return PreparedSourceDefinitionV4(frameOwner,captured,newSlab,newRange).also {
            require(it.definitionIdentity == definitionIdentity) { W5fPlanDiagnostics.Schema }
        }
    }
    companion object {
        fun fromPrepared(frame: FrameSourceLayoutV4,source: MaterialSourceConstructionV4,
            prepared: FrameSourceLayoutV4.PreparedStops): PreparedSourceDefinitionV4 {
            require(prepared.owner === frame && frame.owns(source)) { W5fPlanDiagnostics.Schema }
            return PreparedSourceDefinitionV4(frame,source,requireNotNull(prepared.slab),frame.range(source))
        }
    }
}
