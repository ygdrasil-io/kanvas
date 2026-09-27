package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

/** IIIc2b2 W5f filtered-layer restore sampling the planner-owned parent snapshot. */
public enum class W6FilterCompositeLayerFilteredDestinationShaderFamilyV1 { W5fColorOperationDestinationRead }
public enum class W6FilterCompositeLayerFilteredDestinationGroupZeroAbiV1 { SourceTextureThenUniformThenDestinationSnapshot }

public class W6FilterCompositeLayerFilteredDestinationRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val source: PlanResourceId, public val destination: PlanResourceId,
    public val replacedLayerSource: PlanResourceId, public val destinationSnapshot: PlanResourceId,
    public val requiredDestinationVersion: DestinationVersionI64,
    targetExtentI32: SizeI32, sourceExtentI32: SizeI32, snapshotExtentI32: SizeI32,
    sourceBoundsTargetI32: RectI32, destinationOriginParentI32: Point2I32,
    sourceSampleOffsetTargetLocalI32: Point2I32, compositeScissorTargetLocalI32: RectI32,
    public val alphaF32: Float, public val blend: BlendPlan.DestinationReadV1,
    public val parentVersionBefore: DestinationVersionI64, public val parentVersionAfter: DestinationVersionI64,
    public val filter: ColorFilterExecutionPlanV1, public val uniformResource: PlanResourceId,
    public val uniformOffsetBytesI64: Long, public val uniformCapacityBytesI64: Long,
    public val targetFormat: PlanLogicalColorFormat, public val targetSampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat, public val sourceSampleCountI32: Int,
    public val snapshotFormat: PlanLogicalColorFormat, public val snapshotSampleCountI32: Int,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.Load, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val shaderFamily: W6FilterCompositeLayerFilteredDestinationShaderFamilyV1 = W6FilterCompositeLayerFilteredDestinationShaderFamilyV1.W5fColorOperationDestinationRead,
    public val groupZeroAbi: W6FilterCompositeLayerFilteredDestinationGroupZeroAbiV1 = W6FilterCompositeLayerFilteredDestinationGroupZeroAbiV1.SourceTextureThenUniformThenDestinationSnapshot,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val targetExtent = targetExtentI32.copy(); private val sourceExtent = sourceExtentI32.copy(); private val snapshotExtent = snapshotExtentI32.copy()
    private val bounds = sourceBoundsTargetI32.copy(); private val origin = Point2I32(destinationOriginParentI32.x, destinationOriginParentI32.y)
    private val offset = Point2I32(sourceSampleOffsetTargetLocalI32.x, sourceSampleOffsetTargetLocalI32.y); private val scissor = compositeScissorTargetLocalI32.copy()
    init {
        require(source != destination && source != destinationSnapshot && destination != destinationSnapshot)
        require(requiredDestinationVersion.valueI64 >= 0L && parentVersionBefore.valueI64 >= 0L && parentVersionAfter.valueI64 >= parentVersionBefore.valueI64)
        require(alphaF32.isFinite() && alphaF32 in 0f..1f && !bounds.isEmpty && !scissor.isEmpty)
        require(targetExtent.width > 0 && targetExtent.height > 0 && sourceExtent.width > 0 && sourceExtent.height > 0 && snapshotExtent == targetExtent)
        require(blend.snapshotResource == destinationSnapshot && blend.requiredDestinationVersion == requiredDestinationVersion)
        require(targetFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL && sourceFormat == targetFormat && snapshotFormat == targetFormat)
        require(targetSampleCountI32 == 1 && sourceSampleCountI32 == 1 && snapshotSampleCountI32 == 1)
        require(uniformResource.value.startsWith("${PlanResourceRole.UniformData.name}:") && uniformOffsetBytesI64 >= 0L && uniformCapacityBytesI64 >= 16L && uniformCapacityBytesI64 % 16L == 0L && Math.addExact(uniformOffsetBytesI64, maxOf(16L, filter.dynamicByteCountI64)) <= uniformCapacityBytesI64)
        require(load == AttachmentLoadPlan.Load && store == AttachmentStorePlan.Store)
    }
    public fun copyTargetExtentI32() = targetExtent.copy(); public fun copySourceExtentI32() = sourceExtent.copy(); public fun copySnapshotExtentI32() = snapshotExtent.copy()
    public fun copySourceBoundsTargetI32() = bounds.copy(); public fun copyDestinationOriginParentI32() = Point2I32(origin.x, origin.y)
    public fun copySourceSampleOffsetTargetLocalI32() = Point2I32(offset.x, offset.y); public fun copyCompositeScissorTargetLocalI32() = scissor.copy()
    public fun nativeSiteOwnerV1() = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1() = W6FilterCompositeLayerFilteredDestinationNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}

public class W6FilterCompositeLayerFilteredDestinationNativeSiteRecipeV1 internal constructor(public val host: W6FilterCompositeLayerFilteredDestinationRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = host.nativeSiteOwnerV1(); override val family = NativeSiteRecipeFamilyV1.W6FilterCompositeLayerFilteredDestination
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); text("source", host.source.value); text("destination", host.destination.value); text("replaced", host.replacedLayerSource.value); text("snapshot", host.destinationSnapshot.value); text("uniform", host.uniformResource.value)
        long("requiredSnapshotVersion", host.requiredDestinationVersion.valueI64); long("before", host.parentVersionBefore.valueI64); long("after", host.parentVersionAfter.valueI64); int("alpha", host.alphaF32.toBits()); blend("blend", host.blend)
        rect("bounds", host.copySourceBoundsTargetI32()); point("origin", host.copyDestinationOriginParentI32()); point("offset", host.copySourceSampleOffsetTargetLocalI32()); rect("scissor", host.copyCompositeScissorTargetLocalI32())
        val target = host.copyTargetExtentI32(); int("target.width", target.width); int("target.height", target.height); val source = host.copySourceExtentI32(); int("source.width", source.width); int("source.height", source.height); val snapshot = host.copySnapshotExtentI32(); int("snapshot.width", snapshot.width); int("snapshot.height", snapshot.height)
        text("filter.structural", host.filter.structuralIdentity); text("filter.canonical", host.filter.canonicalIdentity); long("filter.dynamic", host.filter.dynamicByteCountI64); long("uniform.offset", host.uniformOffsetBytesI64); long("uniform.capacity", host.uniformCapacityBytesI64)
        enum("target.format", host.targetFormat); int("target.samples", host.targetSampleCountI32); enum("source.format", host.sourceFormat); int("source.samples", host.sourceSampleCountI32); enum("snapshot.format", host.snapshotFormat); int("snapshot.samples", host.snapshotSampleCountI32); enum("load", host.load); enum("store", host.store); enum("shader", host.shaderFamily); enum("abi", host.groupZeroAbi)
        int("draw.vertices", host.draw.vertexCountI32); int("draw.instances", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

public fun freezeW6FilterCompositeLayerFilteredDestinationRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FilterCompositeLayerFilteredDestinationRecipeV1> {
    val uniform = resources.single { it.role == PlanResourceRole.UniformData && it.kind == PlanResourceKind.Buffer }
    return java.util.Collections.unmodifiableMap(linkedMapOf<PlanPassId, W6FilterCompositeLayerFilteredDestinationRecipeV1>().apply {
        passes.filterIsInstance<PlanPass.FilterComposite>().forEach { pass ->
            val operation = pass.operation as? FilterCompositeOperationV1.Layer ?: return@forEach; val restore = operation.restore
            val filter = restore.colorFilter ?: return@forEach; val blend = restore.blend as? BlendPlan.DestinationReadV1 ?: return@forEach
            if (operation.noOp) return@forEach
            val uniformOffset = requireNotNull(restore.colorFilterUniformOffsetI64) { "Active filtered destination-read FilterComposite.Layer ${pass.id.value} is missing its sealed W5f uniform window." }
            val snapshotId = requireNotNull(blend.snapshotResource); val target = resources.single { it.id == pass.destination }; val source = resources.single { it.id == pass.source }; val snapshot = resources.single { it.id == snapshotId }
            require(put(pass.id, W6FilterCompositeLayerFilteredDestinationRecipeV1(pass.id, pass.source, pass.destination, requireNotNull(pass.replacedLayerSource), snapshotId, blend.requiredDestinationVersion, requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), requireNotNull(snapshot.copyExtent()), pass.copySourceBoundsTargetI32(), pass.copyDestinationOriginParentI32(), pass.copySourceSampleOffsetTargetLocalI32(), requireNotNull(pass.copyCompositeScissorTargetLocalI32()), restore.alphaF32, blend, restore.parentVersionBefore, restore.parentVersionAfter, filter, uniform.id, uniformOffset, uniform.byteSize, (target.format as PlanTextureFormat.Color).value, target.sampleCountI32, (source.format as PlanTextureFormat.Color).value, source.sampleCountI32, (snapshot.format as PlanTextureFormat.Color).value, snapshot.sampleCountI32)) == null)
        }
    })
}
