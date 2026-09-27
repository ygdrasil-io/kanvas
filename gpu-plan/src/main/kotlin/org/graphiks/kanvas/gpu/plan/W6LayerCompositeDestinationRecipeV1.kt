package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

/** IIIa2a: direct LayerComposite restore which samples its sealed fresh parent snapshot. */
public enum class W6LayerCompositeDestinationShaderFamilyV1 { SampledTextureDestinationRead }
public enum class W6LayerCompositeDestinationGroupZeroAbiV1 { SourceTextureThenDestinationSnapshot }

public class W6LayerCompositeDestinationRecipeV1 internal constructor(
    public val site: W6LayerCompositeSiteKeyV1, public val source: PlanResourceId, public val destination: PlanResourceId,
    public val destinationSnapshot: PlanResourceId, public val requiredDestinationVersion: DestinationVersionI64,
    targetExtentI32: SizeI32, sourceExtentI32: SizeI32, snapshotExtentI32: SizeI32,
    sourceBoundsLayerI32: RectI32, destinationOriginParentI32: Point2I32, scissorParentI32: RectI32,
    public val alphaF32: Float, public val blend: BlendPlan.DestinationReadV1,
    public val parentVersionBefore: DestinationVersionI64, public val parentVersionAfter: DestinationVersionI64,
    public val targetFormat: PlanLogicalColorFormat, public val targetSampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat, public val sourceSampleCountI32: Int,
    public val snapshotFormat: PlanLogicalColorFormat, public val snapshotSampleCountI32: Int,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.Load, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val shaderFamily: W6LayerCompositeDestinationShaderFamilyV1 = W6LayerCompositeDestinationShaderFamilyV1.SampledTextureDestinationRead,
    public val groupZeroAbi: W6LayerCompositeDestinationGroupZeroAbiV1 = W6LayerCompositeDestinationGroupZeroAbiV1.SourceTextureThenDestinationSnapshot,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val targetExtent = targetExtentI32.copy(); private val sourceExtent = sourceExtentI32.copy(); private val snapshotExtent = snapshotExtentI32.copy()
    private val bounds = sourceBoundsLayerI32.copy(); private val origin = Point2I32(destinationOriginParentI32.x, destinationOriginParentI32.y); private val scissor = scissorParentI32.copy()
    init {
        require(source != destination && source != destinationSnapshot && destination != destinationSnapshot)
        require(requiredDestinationVersion.valueI64 >= 0L && parentVersionBefore.valueI64 >= 0L && parentVersionAfter.valueI64 >= parentVersionBefore.valueI64)
        require(alphaF32.isFinite() && alphaF32 in 0f..1f && !bounds.isEmpty && !scissor.isEmpty)
        require(targetExtent.width > 0 && targetExtent.height > 0 && sourceExtent.width > 0 && sourceExtent.height > 0 && snapshotExtent == targetExtent)
        require(blend.snapshotResource == destinationSnapshot && blend.requiredDestinationVersion == requiredDestinationVersion)
        require(targetFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL && sourceFormat == targetFormat && snapshotFormat == targetFormat)
        require(targetSampleCountI32 == 1 && sourceSampleCountI32 == 1 && snapshotSampleCountI32 == 1 && load == AttachmentLoadPlan.Load && store == AttachmentStorePlan.Store)
    }
    public fun copyTargetExtentI32() = targetExtent.copy(); public fun copySourceExtentI32() = sourceExtent.copy(); public fun copySnapshotExtentI32() = snapshotExtent.copy()
    public fun copySourceBoundsLayerI32() = bounds.copy(); public fun copyDestinationOriginParentI32() = Point2I32(origin.x, origin.y); public fun copyScissorParentI32() = scissor.copy()
    public fun nativeSiteOwnerV1() = NativeSiteOwnerV1(site.ownerPassId, site.siteOrdinalI32, 0)
    public fun canonicalLogicalEncodingV1() = W6LayerCompositeDestinationNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}

/** IIIa2b: W5f LayerComposite restore which samples its sealed fresh parent snapshot. */
public enum class W6LayerCompositeFilteredDestinationShaderFamilyV1 { W5fColorOperationDestinationRead }
public enum class W6LayerCompositeFilteredDestinationGroupZeroAbiV1 { SourceTextureThenUniformThenDestinationSnapshot }

public class W6LayerCompositeFilteredDestinationRecipeV1 internal constructor(
    public val site: W6LayerCompositeSiteKeyV1, public val source: PlanResourceId, public val destination: PlanResourceId,
    public val destinationSnapshot: PlanResourceId, public val requiredDestinationVersion: DestinationVersionI64,
    targetExtentI32: SizeI32, sourceExtentI32: SizeI32, snapshotExtentI32: SizeI32,
    sourceBoundsLayerI32: RectI32, destinationOriginParentI32: Point2I32, scissorParentI32: RectI32,
    public val alphaF32: Float, public val blend: BlendPlan.DestinationReadV1,
    public val parentVersionBefore: DestinationVersionI64, public val parentVersionAfter: DestinationVersionI64,
    public val execution: ColorFilterExecutionPlanV1, public val uniformResource: PlanResourceId,
    public val uniformOffsetBytesI64: Long, public val uniformCapacityBytesI64: Long,
    public val targetFormat: PlanLogicalColorFormat, public val targetSampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat, public val sourceSampleCountI32: Int,
    public val snapshotFormat: PlanLogicalColorFormat, public val snapshotSampleCountI32: Int,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.Load, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val shaderFamily: W6LayerCompositeFilteredDestinationShaderFamilyV1 = W6LayerCompositeFilteredDestinationShaderFamilyV1.W5fColorOperationDestinationRead,
    public val groupZeroAbi: W6LayerCompositeFilteredDestinationGroupZeroAbiV1 = W6LayerCompositeFilteredDestinationGroupZeroAbiV1.SourceTextureThenUniformThenDestinationSnapshot,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val targetExtent = targetExtentI32.copy(); private val sourceExtent = sourceExtentI32.copy(); private val snapshotExtent = snapshotExtentI32.copy()
    private val bounds = sourceBoundsLayerI32.copy(); private val origin = Point2I32(destinationOriginParentI32.x, destinationOriginParentI32.y); private val scissor = scissorParentI32.copy()
    init {
        require(source != destination && source != destinationSnapshot && destination != destinationSnapshot)
        require(requiredDestinationVersion.valueI64 >= 0L && parentVersionBefore.valueI64 >= 0L && parentVersionAfter.valueI64 >= parentVersionBefore.valueI64)
        require(alphaF32.isFinite() && alphaF32 in 0f..1f && !bounds.isEmpty && !scissor.isEmpty)
        require(targetExtent.width > 0 && targetExtent.height > 0 && sourceExtent.width > 0 && sourceExtent.height > 0 && snapshotExtent == targetExtent)
        require(blend.snapshotResource == destinationSnapshot && blend.requiredDestinationVersion == requiredDestinationVersion)
        require(targetFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL && sourceFormat == targetFormat && snapshotFormat == targetFormat)
        require(targetSampleCountI32 == 1 && sourceSampleCountI32 == 1 && snapshotSampleCountI32 == 1)
        require(uniformResource.value.startsWith("${PlanResourceRole.UniformData.name}:") && uniformOffsetBytesI64 >= 0L && uniformCapacityBytesI64 >= 16L && uniformCapacityBytesI64 % 16L == 0L && Math.addExact(uniformOffsetBytesI64, maxOf(16L, execution.dynamicByteCountI64)) <= uniformCapacityBytesI64)
        require(load == AttachmentLoadPlan.Load && store == AttachmentStorePlan.Store)
    }
    public fun copyTargetExtentI32() = targetExtent.copy(); public fun copySourceExtentI32() = sourceExtent.copy(); public fun copySnapshotExtentI32() = snapshotExtent.copy()
    public fun copySourceBoundsLayerI32() = bounds.copy(); public fun copyDestinationOriginParentI32() = Point2I32(origin.x, origin.y); public fun copyScissorParentI32() = scissor.copy()
    public fun nativeSiteOwnerV1() = NativeSiteOwnerV1(site.ownerPassId, site.siteOrdinalI32, 0)
    public fun canonicalLogicalEncodingV1() = W6LayerCompositeFilteredDestinationNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}

public class W6LayerCompositeDestinationNativeSiteRecipeV1 internal constructor(public val host: W6LayerCompositeDestinationRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = host.nativeSiteOwnerV1(); override val family = NativeSiteRecipeFamilyV1.W6LayerCompositeDestination
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) {
        text("owner", owner.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); text("source", host.source.value); text("destination", host.destination.value); text("snapshot", host.destinationSnapshot.value)
        long("requiredSnapshotVersion", host.requiredDestinationVersion.valueI64); long("before", host.parentVersionBefore.valueI64); long("after", host.parentVersionAfter.valueI64); rect("bounds", host.copySourceBoundsLayerI32()); point("origin", host.copyDestinationOriginParentI32()); rect("scissor", host.copyScissorParentI32()); int("alpha", host.alphaF32.toBits()); blend("blend", host.blend)
        val target = host.copyTargetExtentI32(); int("target.width", target.width); int("target.height", target.height); val source = host.copySourceExtentI32(); int("source.width", source.width); int("source.height", source.height); val snapshot = host.copySnapshotExtentI32(); int("snapshot.width", snapshot.width); int("snapshot.height", snapshot.height)
        enum("target.format", host.targetFormat); int("target.samples", host.targetSampleCountI32); enum("source.format", host.sourceFormat); int("source.samples", host.sourceSampleCountI32); enum("snapshot.format", host.snapshotFormat); int("snapshot.samples", host.snapshotSampleCountI32); enum("load", host.load); enum("store", host.store); enum("shader", host.shaderFamily); enum("abi", host.groupZeroAbi); int("draw.vertices", host.draw.vertexCountI32); int("draw.instances", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

public class W6LayerCompositeFilteredDestinationNativeSiteRecipeV1 internal constructor(public val host: W6LayerCompositeFilteredDestinationRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = host.nativeSiteOwnerV1(); override val family = NativeSiteRecipeFamilyV1.W6LayerCompositeFilteredDestination
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) {
        text("owner", owner.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); text("source", host.source.value); text("destination", host.destination.value); text("snapshot", host.destinationSnapshot.value); text("uniform", host.uniformResource.value)
        long("requiredSnapshotVersion", host.requiredDestinationVersion.valueI64); long("before", host.parentVersionBefore.valueI64); long("after", host.parentVersionAfter.valueI64); rect("bounds", host.copySourceBoundsLayerI32()); point("origin", host.copyDestinationOriginParentI32()); rect("scissor", host.copyScissorParentI32()); int("alpha", host.alphaF32.toBits()); blend("blend", host.blend)
        val target = host.copyTargetExtentI32(); int("target.width", target.width); int("target.height", target.height); val source = host.copySourceExtentI32(); int("source.width", source.width); int("source.height", source.height); val snapshot = host.copySnapshotExtentI32(); int("snapshot.width", snapshot.width); int("snapshot.height", snapshot.height)
        text("execution.structural", host.execution.structuralIdentity); text("execution.canonical", host.execution.canonicalIdentity); long("execution.dynamicBytes", host.execution.dynamicByteCountI64); long("uniform.offset", host.uniformOffsetBytesI64); long("uniform.capacity", host.uniformCapacityBytesI64)
        enum("target.format", host.targetFormat); int("target.samples", host.targetSampleCountI32); enum("source.format", host.sourceFormat); int("source.samples", host.sourceSampleCountI32); enum("snapshot.format", host.snapshotFormat); int("snapshot.samples", host.snapshotSampleCountI32); enum("load", host.load); enum("store", host.store); enum("shader", host.shaderFamily); enum("abi", host.groupZeroAbi); int("draw.vertices", host.draw.vertexCountI32); int("draw.instances", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

public fun freezeW6LayerCompositeDestinationRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<W6LayerCompositeSiteKeyV1, W6LayerCompositeDestinationRecipeV1> = java.util.Collections.unmodifiableMap(linkedMapOf<W6LayerCompositeSiteKeyV1, W6LayerCompositeDestinationRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.LayerComposite>().forEach { pass ->
        val blend = pass.restore.blend as? BlendPlan.DestinationReadV1 ?: return@forEach
        if (pass.restore.colorFilter != null) return@forEach
        val snapshotId = requireNotNull(blend.snapshotResource); val target = resources.single { it.id == pass.destination }; val source = resources.single { it.id == pass.source }; val snapshot = resources.single { it.id == snapshotId }
        val bounds = pass.copySourceBoundsLayerI32(); val origin = pass.copyDestinationOriginParentI32(); val scissor = RectI32(origin.x, origin.y, Math.addExact(origin.x, bounds.width()), Math.addExact(origin.y, bounds.height()))
        val site = W6LayerCompositeSiteKeyV1(pass.id, 0)
        require(put(site, W6LayerCompositeDestinationRecipeV1(site, pass.source, pass.destination, snapshotId, blend.requiredDestinationVersion, requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), requireNotNull(snapshot.copyExtent()), bounds, origin, scissor, pass.restore.alphaF32, blend, pass.restore.parentVersionBefore, pass.restore.parentVersionAfter, (target.format as PlanTextureFormat.Color).value, target.sampleCountI32, (source.format as PlanTextureFormat.Color).value, source.sampleCountI32, (snapshot.format as PlanTextureFormat.Color).value, snapshot.sampleCountI32)) == null)
    }
})

public fun freezeW6LayerCompositeFilteredDestinationRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<W6LayerCompositeSiteKeyV1, W6LayerCompositeFilteredDestinationRecipeV1> {
    val uniform = resources.single { it.role == PlanResourceRole.UniformData && it.kind == PlanResourceKind.Buffer }
    return java.util.Collections.unmodifiableMap(linkedMapOf<W6LayerCompositeSiteKeyV1, W6LayerCompositeFilteredDestinationRecipeV1>().apply {
        passes.filterIsInstance<PlanPass.LayerComposite>().forEach { pass ->
            val filter = pass.restore.colorFilter ?: return@forEach; val blend = pass.restore.blend as? BlendPlan.DestinationReadV1 ?: return@forEach
            val uniformOffset = requireNotNull(pass.restore.colorFilterUniformOffsetI64) { "Active filtered destination-read LayerComposite ${pass.id.value} is missing its sealed W5f uniform window." }
            val snapshotId = requireNotNull(blend.snapshotResource); val target = resources.single { it.id == pass.destination }; val source = resources.single { it.id == pass.source }; val snapshot = resources.single { it.id == snapshotId }
            val bounds = pass.copySourceBoundsLayerI32(); val origin = pass.copyDestinationOriginParentI32(); val scissor = RectI32(origin.x, origin.y, Math.addExact(origin.x, bounds.width()), Math.addExact(origin.y, bounds.height()))
            val site = W6LayerCompositeSiteKeyV1(pass.id, 0)
            require(put(site, W6LayerCompositeFilteredDestinationRecipeV1(site, pass.source, pass.destination, snapshotId, blend.requiredDestinationVersion, requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), requireNotNull(snapshot.copyExtent()), bounds, origin, scissor, pass.restore.alphaF32, blend, pass.restore.parentVersionBefore, pass.restore.parentVersionAfter, filter, uniform.id, uniformOffset, uniform.byteSize, (target.format as PlanTextureFormat.Color).value, target.sampleCountI32, (source.format as PlanTextureFormat.Color).value, source.sampleCountI32, (snapshot.format as PlanTextureFormat.Color).value, snapshot.sampleCountI32)) == null)
        }
    })
}
