package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

/** IIIc3c2's external Picture filter: graph b0 followed by the planner-owned W5f b1 uniform. */
public enum class W6FilterCompositePictureGraphFilteredShaderFamilyV1 { GraphTextureColorFilter }
public enum class W6FilterCompositePictureGraphFilteredGroupZeroAbiV1 { GraphTextureThenColorFilterUniform }

public class W6FilterCompositePictureGraphFilteredRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val source: PlanResourceId, public val destination: PlanResourceId,
    public val boundSource: PlanResourceId, public val graphSealedSource: PlanResourceId,
    public val graphSealedSourceGenerationI64: Long, public val alphaF32: Float,
    sourceSceneCanonicalId: String, public val sourceCommandIndexI32: Int,
    public val plannedCommandId: FramePlannedCommandIdI32, public val sourceGenerationI64: Long,
    public val filter: ColorFilterExecutionPlanV1, public val uniformResource: PlanResourceId,
    public val uniformOffsetBytesI64: Long, public val uniformCapacityBytesI64: Long,
    targetExtentI32: SizeI32, sourceExtentI32: SizeI32, graphSourceExtentI32: SizeI32,
    sourceBoundsTargetI32: RectI32, destinationOriginTargetI32: Point2I32,
    sourceSampleOffsetTargetLocalI32: Point2I32, compositeScissorTargetLocalI32: RectI32,
    public val blend: BlendPlan, public val destinationVersionBefore: DestinationVersionI64,
    public val destinationVersionAfter: DestinationVersionI64, public val compositeScissorAdmitted: Boolean,
    public val targetFormat: PlanLogicalColorFormat, public val targetSampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat, public val sourceSampleCountI32: Int,
    public val graphSourceFormat: PlanLogicalColorFormat, public val graphSourceSampleCountI32: Int,
    public val load: AttachmentLoadPlan, public val store: AttachmentStorePlan,
    public val shaderFamily: W6FilterCompositePictureGraphFilteredShaderFamilyV1 = W6FilterCompositePictureGraphFilteredShaderFamilyV1.GraphTextureColorFilter,
    public val groupZeroAbi: W6FilterCompositePictureGraphFilteredGroupZeroAbiV1 = W6FilterCompositePictureGraphFilteredGroupZeroAbiV1.GraphTextureThenColorFilterUniform,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val sourceScene = sourceSceneCanonicalId; private val targetExtent = targetExtentI32.copy(); private val sourceExtent = sourceExtentI32.copy(); private val graphSourceExtent = graphSourceExtentI32.copy()
    private val bounds = sourceBoundsTargetI32.copy(); private val origin = Point2I32(destinationOriginTargetI32.x, destinationOriginTargetI32.y); private val offset = Point2I32(sourceSampleOffsetTargetLocalI32.x, sourceSampleOffsetTargetLocalI32.y); private val scissor = compositeScissorTargetLocalI32.copy()
    init {
        require(source != destination && graphSealedSource != destination && sourceScene.isNotBlank() && sourceCommandIndexI32 >= 0 && sourceGenerationI64 >= 0L && graphSealedSourceGenerationI64 >= 0L)
        require(alphaF32.isFinite() && alphaF32 in 0f..1f && !bounds.isEmpty && !scissor.isEmpty && blend !is BlendPlan.DestinationReadV1)
        require(targetExtent.width > 0 && targetExtent.height > 0 && sourceExtent.width > 0 && sourceExtent.height > 0 && graphSourceExtent.width > 0 && graphSourceExtent.height > 0)
        require(targetFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL && sourceFormat == targetFormat && graphSourceFormat == targetFormat && targetSampleCountI32 == 1 && sourceSampleCountI32 == 1 && graphSourceSampleCountI32 == 1 && load == AttachmentLoadPlan.Load && store == AttachmentStorePlan.Store)
        require(uniformResource.value.startsWith("${PlanResourceRole.SourceUniformData.name}:") && uniformOffsetBytesI64 >= 0L && uniformCapacityBytesI64 >= 16L && uniformCapacityBytesI64 % 16L == 0L && Math.addExact(uniformOffsetBytesI64, maxOf(16L, filter.dynamicByteCountI64)) <= uniformCapacityBytesI64)
    }
    public fun copySourceSceneCanonicalId(): String = sourceScene; public fun copyTargetExtentI32(): SizeI32 = targetExtent.copy(); public fun copySourceExtentI32(): SizeI32 = sourceExtent.copy(); public fun copyGraphSourceExtentI32(): SizeI32 = graphSourceExtent.copy()
    public fun copySourceBoundsTargetI32(): RectI32 = bounds.copy(); public fun copyDestinationOriginTargetI32(): Point2I32 = Point2I32(origin.x, origin.y); public fun copySourceSampleOffsetTargetLocalI32(): Point2I32 = Point2I32(offset.x, offset.y); public fun copyCompositeScissorTargetLocalI32(): RectI32 = scissor.copy()
    public fun nativeSiteOwnerV1(): NativeSiteOwnerV1 = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1(): String = W6FilterCompositePictureGraphFilteredNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}

public class W6FilterCompositePictureGraphFilteredNativeSiteRecipeV1 internal constructor(public val host: W6FilterCompositePictureGraphFilteredRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = host.nativeSiteOwnerV1(); override val family = NativeSiteRecipeFamilyV1.W6FilterCompositePictureGraphFiltered
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); text("source", host.source.value); text("destination", host.destination.value); text("bound", host.boundSource.value); text("sealed", host.graphSealedSource.value); long("graphGeneration", host.graphSealedSourceGenerationI64); int("alpha", host.alphaF32.toBits()); text("scene", host.copySourceSceneCanonicalId()); int("command", host.sourceCommandIndexI32); int("planned", host.plannedCommandId.valueI32); long("sourceGeneration", host.sourceGenerationI64)
        text("filter.structural", host.filter.structuralIdentity); text("filter.canonical", host.filter.canonicalIdentity); long("filter.dynamic", host.filter.dynamicByteCountI64); text("uniform", host.uniformResource.value); long("uniform.offset", host.uniformOffsetBytesI64); long("uniform.capacity", host.uniformCapacityBytesI64)
        rect("bounds", host.copySourceBoundsTargetI32()); point("origin", host.copyDestinationOriginTargetI32()); point("offset", host.copySourceSampleOffsetTargetLocalI32()); rect("scissor", host.copyCompositeScissorTargetLocalI32()); int("target.width", host.copyTargetExtentI32().width); int("target.height", host.copyTargetExtentI32().height); int("source.width", host.copySourceExtentI32().width); int("source.height", host.copySourceExtentI32().height); int("graph.width", host.copyGraphSourceExtentI32().width); int("graph.height", host.copyGraphSourceExtentI32().height)
        blend("blend", host.blend); long("before", host.destinationVersionBefore.valueI64); long("after", host.destinationVersionAfter.valueI64); int("scissorAdmitted", if (host.compositeScissorAdmitted) 1 else 0); enum("load", host.load); enum("store", host.store); enum("target.format", host.targetFormat); int("target.samples", host.targetSampleCountI32); enum("source.format", host.sourceFormat); int("source.samples", host.sourceSampleCountI32); enum("graph.format", host.graphSourceFormat); int("graph.samples", host.graphSourceSampleCountI32); enum("shader", host.shaderFamily); enum("abi", host.groupZeroAbi); int("draw.vertices", host.draw.vertexCountI32); int("draw.instances", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

public fun freezeW6FilterCompositePictureGraphFilteredRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FilterCompositePictureGraphFilteredRecipeV1> = java.util.Collections.unmodifiableMap(linkedMapOf<PlanPassId, W6FilterCompositePictureGraphFilteredRecipeV1>().apply {
    val pictureSources = passes.filterIsInstance<PlanPass.PictureSourcePass>().associateBy { it.output }
    passes.filterIsInstance<PlanPass.FilterComposite>().forEach { pass ->
        val operation = pass.operation as? FilterCompositeOperationV1.Picture ?: return@forEach; val terminal = requireNotNull(operation.terminal); val sourcePass = pictureSources[pass.evaluationKey.boundSourceId] ?: return@forEach; val operand = sourcePass.graphTextureOperand ?: return@forEach; val filter = operand.colorFilter ?: return@forEach
        if (terminal.blend is BlendPlan.DestinationReadV1 || terminal.copyCompositeScissorTargetLocalI32() == null) return@forEach
        val offset = requireNotNull(operand.colorFilterUniformOffsetI64); val uniform = resources.single { it.id == operand.uniformResource }; require(pass.source == terminal.source && pass.replacedLayerSource == null && operand.finalBlend.canonicalLabel == terminal.blend.canonicalLabel && operand.colorFilterUniformByteCountI64 == uniform.byteSize)
        val target = resources.single { it.id == pass.destination }; val source = resources.single { it.id == pass.source }; val graph = resources.single { it.id == operand.sealedSourceId }
        require(PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in source.usages() && PlanResourceUsage.Sampled in graph.usages() && uniform.usages() == setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination))
        require(put(pass.id, W6FilterCompositePictureGraphFilteredRecipeV1(pass.id, pass.source, pass.destination, pass.evaluationKey.boundSourceId, operand.sealedSourceId, operand.sealedSourceGenerationI64, operand.alphaF32, operation.sourceSceneCanonicalId, operation.sourceCommandIndexI32, terminal.plannedCommandId, terminal.sourceGenerationI64, filter, uniform.id, offset, uniform.byteSize, requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), requireNotNull(graph.copyExtent()), terminal.copySourceBoundsTargetI32(), terminal.copyDestinationOriginTargetI32(), terminal.copySourceSampleOffsetTargetLocalI32(), requireNotNull(terminal.copyCompositeScissorTargetLocalI32()), terminal.blend, terminal.destinationVersionBefore, pass.destinationVersionAfter, terminal.compositeScissorAdmitted, (target.format as PlanTextureFormat.Color).value, target.sampleCountI32, (source.format as PlanTextureFormat.Color).value, source.sampleCountI32, (graph.format as PlanTextureFormat.Color).value, graph.sampleCountI32, terminal.load, terminal.store)) == null)
    }
})
