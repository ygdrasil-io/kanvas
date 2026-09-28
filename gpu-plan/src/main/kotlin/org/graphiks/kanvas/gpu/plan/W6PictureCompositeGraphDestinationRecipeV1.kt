package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

/** IIIb2b2's graph-texture terminal with a planner-owned destination snapshot. */
public enum class W6PictureCompositeGraphDestinationShaderFamilyV1 { GraphTextureDestinationRead }
public enum class W6PictureCompositeGraphDestinationGroupZeroAbiV1 { SourceTextureThenDestinationSnapshot }

public class W6PictureCompositeGraphDestinationRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val source: PlanResourceId, public val destination: PlanResourceId,
    public val graphSealedSource: PlanResourceId, public val graphSealedSourceGenerationI64: Long,
    public val destinationSnapshot: PlanResourceId, public val destinationVersion: DestinationVersionI64,
    public val alphaF32: Float, targetExtentI32: SizeI32, sourceExtentI32: SizeI32, snapshotExtentI32: SizeI32,
    sourceBoundsTargetI32: RectI32, sourceSampleOffsetTargetLocalI32: Point2I32,
    compositeScissorTargetLocalI32: RectI32, public val blend: BlendPlan.DestinationReadV1,
    public val targetFormat: PlanLogicalColorFormat, public val targetSampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat, public val sourceSampleCountI32: Int,
    public val snapshotFormat: PlanLogicalColorFormat, public val snapshotSampleCountI32: Int,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.Load, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val shaderFamily: W6PictureCompositeGraphDestinationShaderFamilyV1 = W6PictureCompositeGraphDestinationShaderFamilyV1.GraphTextureDestinationRead,
    public val groupZeroAbi: W6PictureCompositeGraphDestinationGroupZeroAbiV1 = W6PictureCompositeGraphDestinationGroupZeroAbiV1.SourceTextureThenDestinationSnapshot,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val targetExtent = targetExtentI32.copy(); private val sourceExtent = sourceExtentI32.copy(); private val snapshotExtent = snapshotExtentI32.copy()
    private val sourceBounds = sourceBoundsTargetI32.copy(); private val sampleOffset = Point2I32(sourceSampleOffsetTargetLocalI32.x, sourceSampleOffsetTargetLocalI32.y)
    private val scissor = compositeScissorTargetLocalI32.copy()
    init {
        require(source != destination && graphSealedSource != destination && destinationSnapshot != destination && destinationSnapshot != source)
        require(graphSealedSourceGenerationI64 >= 0L && destinationVersion.valueI64 >= 0L && alphaF32.isFinite() && alphaF32 in 0f..1f)
        require(!sourceBounds.isEmpty && !scissor.isEmpty && targetExtent.width > 0 && targetExtent.height > 0 && sourceExtent.width > 0 && sourceExtent.height > 0 && snapshotExtent == targetExtent)
        require(blend.snapshotResource == destinationSnapshot && blend.requiredDestinationVersion == destinationVersion)
        require(targetFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL && sourceFormat == targetFormat && snapshotFormat == targetFormat)
        require(targetSampleCountI32 == 1 && sourceSampleCountI32 == 1 && snapshotSampleCountI32 == 1 && load == AttachmentLoadPlan.Load && store == AttachmentStorePlan.Store)
    }
    public fun copyTargetExtentI32() = targetExtent.copy(); public fun copySourceExtentI32() = sourceExtent.copy(); public fun copySnapshotExtentI32() = snapshotExtent.copy(); public fun copySourceBoundsTargetI32() = sourceBounds.copy()
    public fun copySourceSampleOffsetTargetLocalI32() = Point2I32(sampleOffset.x, sampleOffset.y); public fun copyCompositeScissorTargetLocalI32() = scissor.copy()
    public fun nativeSiteOwnerV1() = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1() = W6PictureCompositeGraphDestinationNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}

public class W6PictureCompositeGraphDestinationNativeSiteRecipeV1 internal constructor(public val host: W6PictureCompositeGraphDestinationRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = host.nativeSiteOwnerV1(); override val family = NativeSiteRecipeFamilyV1.W6PictureCompositeGraphDestination
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); text("source", host.source.value); text("destination", host.destination.value)
        text("sealed", host.graphSealedSource.value); long("generation", host.graphSealedSourceGenerationI64); text("snapshot", host.destinationSnapshot.value); long("destinationVersion", host.destinationVersion.valueI64)
        int("alpha", host.alphaF32.toBits()); rect("bounds", host.copySourceBoundsTargetI32()); point("offset", host.copySourceSampleOffsetTargetLocalI32()); rect("scissor", host.copyCompositeScissorTargetLocalI32())
        val target = host.copyTargetExtentI32(); int("target.width", target.width); int("target.height", target.height); val source = host.copySourceExtentI32(); int("source.width", source.width); int("source.height", source.height); val snapshot = host.copySnapshotExtentI32(); int("snapshot.width", snapshot.width); int("snapshot.height", snapshot.height)
        blend("blend", host.blend); enum("load", host.load); enum("store", host.store); enum("shader", host.shaderFamily); enum("abi", host.groupZeroAbi)
        enum("target.format", host.targetFormat); int("target.samples", host.targetSampleCountI32); enum("source.format", host.sourceFormat); int("source.samples", host.sourceSampleCountI32); enum("snapshot.format", host.snapshotFormat); int("snapshot.samples", host.snapshotSampleCountI32)
        int("draw.vertices", host.draw.vertexCountI32); int("draw.instances", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

public fun freezeW6PictureCompositeGraphDestinationRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6PictureCompositeGraphDestinationRecipeV1> = java.util.Collections.unmodifiableMap(linkedMapOf<PlanPassId, W6PictureCompositeGraphDestinationRecipeV1>().apply {
    val sources = passes.filterIsInstance<PlanPass.PictureSourcePass>().associateBy { it.output }
    passes.filterIsInstance<PlanPass.PictureComposite>().forEach { pass ->
        val operands = requireNotNull(pass.operands); val scissor = operands.copyCompositeScissorTargetLocalI32() ?: return@forEach
        val operand = sources[pass.source]?.graphTextureOperand ?: return@forEach; val blend = operands.blend as? BlendPlan.DestinationReadV1 ?: return@forEach
        if (operand.colorFilter != null) return@forEach
        val snapshotId = requireNotNull(blend.snapshotResource); val target = resources.single { it.id == pass.destination }; val source = resources.single { it.id == pass.source }; val snapshot = resources.single { it.id == snapshotId }
        require(put(pass.id, W6PictureCompositeGraphDestinationRecipeV1(pass.id, pass.source, pass.destination, operand.sealedSourceId, operand.sealedSourceGenerationI64, snapshotId, blend.requiredDestinationVersion, operand.alphaF32, requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), requireNotNull(snapshot.copyExtent()), operands.copySourceBoundsTargetI32(), operands.copySourceSampleOffsetTargetLocalI32(), scissor, blend, (target.format as PlanTextureFormat.Color).value, target.sampleCountI32, (source.format as PlanTextureFormat.Color).value, source.sampleCountI32, (snapshot.format as PlanTextureFormat.Color).value, snapshot.sampleCountI32)) == null)
    }
})
