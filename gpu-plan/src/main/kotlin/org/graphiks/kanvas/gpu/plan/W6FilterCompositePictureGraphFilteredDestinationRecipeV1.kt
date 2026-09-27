package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

/** IIIc3d2's external Picture terminal: graph b0, W5f uniform b1, and destination snapshot b2. */
public enum class W6FilterCompositePictureGraphFilteredDestinationShaderFamilyV1 { GraphTextureColorFilterDestinationRead }
public enum class W6FilterCompositePictureGraphFilteredDestinationGroupZeroAbiV1 { GraphTextureThenColorFilterUniformThenDestinationSnapshot }

public class W6FilterCompositePictureGraphFilteredDestinationRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val source: PlanResourceId, public val destination: PlanResourceId,
    public val boundSource: PlanResourceId, public val graphSealedSource: PlanResourceId, public val graphSealedSourceGenerationI64: Long,
    public val alphaF32: Float, sourceSceneCanonicalId: String, public val sourceCommandIndexI32: Int,
    public val plannedCommandId: FramePlannedCommandIdI32, public val sourceGenerationI64: Long,
    public val filter: ColorFilterExecutionPlanV1, public val uniformResource: PlanResourceId,
    public val uniformOffsetBytesI64: Long, public val uniformCapacityBytesI64: Long,
    public val destinationSnapshot: PlanResourceId, public val requiredDestinationVersion: DestinationVersionI64,
    targetExtentI32: SizeI32, sourceExtentI32: SizeI32, graphSourceExtentI32: SizeI32, snapshotExtentI32: SizeI32,
    sourceBoundsTargetI32: RectI32, destinationOriginTargetI32: Point2I32, sourceSampleOffsetTargetLocalI32: Point2I32, compositeScissorTargetLocalI32: RectI32,
    public val blend: BlendPlan.DestinationReadV1, public val destinationVersionBefore: DestinationVersionI64, public val destinationVersionAfter: DestinationVersionI64,
    public val compositeScissorAdmitted: Boolean, public val targetFormat: PlanLogicalColorFormat, public val targetSampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat, public val sourceSampleCountI32: Int, public val graphSourceFormat: PlanLogicalColorFormat,
    public val graphSourceSampleCountI32: Int, public val snapshotFormat: PlanLogicalColorFormat, public val snapshotSampleCountI32: Int,
    public val load: AttachmentLoadPlan, public val store: AttachmentStorePlan,
    public val shaderFamily: W6FilterCompositePictureGraphFilteredDestinationShaderFamilyV1 = W6FilterCompositePictureGraphFilteredDestinationShaderFamilyV1.GraphTextureColorFilterDestinationRead,
    public val groupZeroAbi: W6FilterCompositePictureGraphFilteredDestinationGroupZeroAbiV1 = W6FilterCompositePictureGraphFilteredDestinationGroupZeroAbiV1.GraphTextureThenColorFilterUniformThenDestinationSnapshot,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val scene = sourceSceneCanonicalId; private val targetExtent = targetExtentI32.copy(); private val sourceExtent = sourceExtentI32.copy(); private val graphExtent = graphSourceExtentI32.copy(); private val snapshotExtent = snapshotExtentI32.copy()
    private val bounds = sourceBoundsTargetI32.copy(); private val origin = Point2I32(destinationOriginTargetI32.x, destinationOriginTargetI32.y); private val offset = Point2I32(sourceSampleOffsetTargetLocalI32.x, sourceSampleOffsetTargetLocalI32.y); private val scissor = compositeScissorTargetLocalI32.copy()
    init {
        require(source != destination && graphSealedSource != destination && destinationSnapshot !in setOf(destination, source) && scene.isNotBlank() && sourceCommandIndexI32 >= 0 && sourceGenerationI64 >= 0L && graphSealedSourceGenerationI64 >= 0L)
        require(alphaF32.isFinite() && alphaF32 in 0f..1f && !bounds.isEmpty && !scissor.isEmpty && requiredDestinationVersion.valueI64 >= 0L && destinationVersionBefore.valueI64 >= 0L && destinationVersionAfter.valueI64 >= destinationVersionBefore.valueI64 && blend.snapshotResource == destinationSnapshot && blend.requiredDestinationVersion == requiredDestinationVersion)
        require(targetExtent.width > 0 && targetExtent.height > 0 && sourceExtent.width > 0 && sourceExtent.height > 0 && graphExtent.width > 0 && graphExtent.height > 0 && snapshotExtent == targetExtent)
        require(targetFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL && sourceFormat == targetFormat && graphSourceFormat == targetFormat && snapshotFormat == targetFormat && targetSampleCountI32 == 1 && sourceSampleCountI32 == 1 && graphSourceSampleCountI32 == 1 && snapshotSampleCountI32 == 1 && load == AttachmentLoadPlan.Load && store == AttachmentStorePlan.Store)
        require(uniformResource.value.startsWith("${PlanResourceRole.SourceUniformData.name}:") && uniformOffsetBytesI64 >= 0L && uniformCapacityBytesI64 >= 16L && uniformCapacityBytesI64 % 16L == 0L && Math.addExact(uniformOffsetBytesI64, maxOf(16L, filter.dynamicByteCountI64)) <= uniformCapacityBytesI64)
    }
    public fun copySourceSceneCanonicalId(): String = scene; public fun copyTargetExtentI32(): SizeI32 = targetExtent.copy(); public fun copySourceExtentI32(): SizeI32 = sourceExtent.copy(); public fun copyGraphSourceExtentI32(): SizeI32 = graphExtent.copy(); public fun copySnapshotExtentI32(): SizeI32 = snapshotExtent.copy()
    public fun copySourceBoundsTargetI32(): RectI32 = bounds.copy(); public fun copyDestinationOriginTargetI32(): Point2I32 = Point2I32(origin.x, origin.y); public fun copySourceSampleOffsetTargetLocalI32(): Point2I32 = Point2I32(offset.x, offset.y); public fun copyCompositeScissorTargetLocalI32(): RectI32 = scissor.copy()
    public fun nativeSiteOwnerV1(): NativeSiteOwnerV1 = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1(): String = W6FilterCompositePictureGraphFilteredDestinationNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}

public class W6FilterCompositePictureGraphFilteredDestinationNativeSiteRecipeV1 internal constructor(public val host: W6FilterCompositePictureGraphFilteredDestinationRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = host.nativeSiteOwnerV1(); override val family = NativeSiteRecipeFamilyV1.W6FilterCompositePictureGraphFilteredDestination
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); text("source", host.source.value); text("destination", host.destination.value); text("bound", host.boundSource.value); text("sealed", host.graphSealedSource.value); long("graphGeneration", host.graphSealedSourceGenerationI64); int("alpha", host.alphaF32.toBits()); text("scene", host.copySourceSceneCanonicalId()); int("command", host.sourceCommandIndexI32); int("planned", host.plannedCommandId.valueI32); long("sourceGeneration", host.sourceGenerationI64)
        text("filter.structural", host.filter.structuralIdentity); text("filter.canonical", host.filter.canonicalIdentity); long("filter.dynamic", host.filter.dynamicByteCountI64); text("uniform", host.uniformResource.value); long("uniform.offset", host.uniformOffsetBytesI64); long("uniform.capacity", host.uniformCapacityBytesI64); text("snapshot", host.destinationSnapshot.value); long("requiredSnapshotVersion", host.requiredDestinationVersion.valueI64)
        rect("bounds", host.copySourceBoundsTargetI32()); point("origin", host.copyDestinationOriginTargetI32()); point("offset", host.copySourceSampleOffsetTargetLocalI32()); rect("scissor", host.copyCompositeScissorTargetLocalI32()); int("target.width", host.copyTargetExtentI32().width); int("target.height", host.copyTargetExtentI32().height); int("source.width", host.copySourceExtentI32().width); int("source.height", host.copySourceExtentI32().height); int("graph.width", host.copyGraphSourceExtentI32().width); int("graph.height", host.copyGraphSourceExtentI32().height); int("snapshot.width", host.copySnapshotExtentI32().width); int("snapshot.height", host.copySnapshotExtentI32().height)
        blend("blend", host.blend); long("before", host.destinationVersionBefore.valueI64); long("after", host.destinationVersionAfter.valueI64); int("scissorAdmitted", if (host.compositeScissorAdmitted) 1 else 0); enum("load", host.load); enum("store", host.store); enum("target.format", host.targetFormat); int("target.samples", host.targetSampleCountI32); enum("source.format", host.sourceFormat); int("source.samples", host.sourceSampleCountI32); enum("graph.format", host.graphSourceFormat); int("graph.samples", host.graphSourceSampleCountI32); enum("snapshot.format", host.snapshotFormat); int("snapshot.samples", host.snapshotSampleCountI32); enum("shader", host.shaderFamily); enum("abi", host.groupZeroAbi); int("draw.vertices", host.draw.vertexCountI32); int("draw.instances", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

public fun freezeW6FilterCompositePictureGraphFilteredDestinationRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FilterCompositePictureGraphFilteredDestinationRecipeV1> = java.util.Collections.unmodifiableMap(linkedMapOf<PlanPassId, W6FilterCompositePictureGraphFilteredDestinationRecipeV1>().apply {
    val pictureSources = passes.filterIsInstance<PlanPass.PictureSourcePass>().associateBy { it.output }
    passes.filterIsInstance<PlanPass.FilterComposite>().forEach { pass ->
        val operation = pass.operation as? FilterCompositeOperationV1.Picture ?: return@forEach; val terminal = requireNotNull(operation.terminal); val blend = terminal.blend as? BlendPlan.DestinationReadV1 ?: return@forEach
        val sourcePass = pictureSources[pass.evaluationKey.boundSourceId] ?: return@forEach; val operand = sourcePass.graphTextureOperand ?: return@forEach; val filter = operand.colorFilter ?: return@forEach
        if (terminal.copyCompositeScissorTargetLocalI32() == null) return@forEach
        val offset = requireNotNull(operand.colorFilterUniformOffsetI64); val uniform = resources.single { it.id == operand.uniformResource }
        require(pass.source == terminal.source && pass.replacedLayerSource == null && operand.finalBlend.canonicalLabel == terminal.blend.canonicalLabel && operand.colorFilterUniformByteCountI64 == uniform.byteSize)
        val target = resources.single { it.id == pass.destination }; val source = resources.single { it.id == pass.source }; val graph = resources.single { it.id == operand.sealedSourceId }; val snapshotId = requireNotNull(blend.snapshotResource); val snapshot = resources.single { it.id == snapshotId }
        require(PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.CopySource in target.usages() && PlanResourceUsage.Sampled in source.usages() && PlanResourceUsage.Sampled in graph.usages() && PlanResourceUsage.CopyDestination in snapshot.usages() && PlanResourceUsage.Sampled in snapshot.usages() && uniform.role == PlanResourceRole.SourceUniformData && uniform.kind == PlanResourceKind.Buffer && uniform.usages() == setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination))
        require(put(pass.id, W6FilterCompositePictureGraphFilteredDestinationRecipeV1(pass.id, pass.source, pass.destination, pass.evaluationKey.boundSourceId, operand.sealedSourceId, operand.sealedSourceGenerationI64, operand.alphaF32, operation.sourceSceneCanonicalId, operation.sourceCommandIndexI32, terminal.plannedCommandId, terminal.sourceGenerationI64, filter, uniform.id, offset, uniform.byteSize, snapshotId, blend.requiredDestinationVersion, requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), requireNotNull(graph.copyExtent()), requireNotNull(snapshot.copyExtent()), terminal.copySourceBoundsTargetI32(), terminal.copyDestinationOriginTargetI32(), terminal.copySourceSampleOffsetTargetLocalI32(), requireNotNull(terminal.copyCompositeScissorTargetLocalI32()), blend, terminal.destinationVersionBefore, pass.destinationVersionAfter, terminal.compositeScissorAdmitted, (target.format as PlanTextureFormat.Color).value, target.sampleCountI32, (source.format as PlanTextureFormat.Color).value, source.sampleCountI32, (graph.format as PlanTextureFormat.Color).value, graph.sampleCountI32, (snapshot.format as PlanTextureFormat.Color).value, snapshot.sampleCountI32, terminal.load, terminal.store)) == null)
    }
})
