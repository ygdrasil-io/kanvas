package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

/** IIIc3b inner Picture terminal: direct source plus the planner-owned destination snapshot. */
public enum class W6FilterCompositePictureDestinationShaderFamilyV1 { SampledTextureDestinationRead }
public enum class W6FilterCompositePictureDestinationGroupZeroAbiV1 { SourceTextureThenDestinationSnapshot }
public enum class W6FilterCompositePictureDestinationProvenanceV1 { NoPictureSourcePass, PictureSourcePassWithoutGraphOperand }

public class W6FilterCompositePictureDestinationRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val source: PlanResourceId, public val destination: PlanResourceId,
    public val boundSource: PlanResourceId, public val provenance: W6FilterCompositePictureDestinationProvenanceV1,
    sourceSceneCanonicalId: String, public val sourceCommandIndexI32: Int,
    public val plannedCommandId: FramePlannedCommandIdI32, public val sourceGenerationI64: Long,
    public val destinationSnapshot: PlanResourceId, public val requiredDestinationVersion: DestinationVersionI64,
    targetExtentI32: SizeI32, sourceExtentI32: SizeI32, snapshotExtentI32: SizeI32,
    sourceBoundsTargetI32: RectI32, destinationOriginTargetI32: Point2I32,
    sourceSampleOffsetTargetLocalI32: Point2I32, compositeScissorTargetLocalI32: RectI32,
    public val blend: BlendPlan.DestinationReadV1,
    public val destinationVersionBefore: DestinationVersionI64, public val destinationVersionAfter: DestinationVersionI64,
    public val compositeScissorAdmitted: Boolean,
    public val targetFormat: PlanLogicalColorFormat, public val targetSampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat, public val sourceSampleCountI32: Int,
    public val snapshotFormat: PlanLogicalColorFormat, public val snapshotSampleCountI32: Int,
    public val load: AttachmentLoadPlan, public val store: AttachmentStorePlan,
    public val shaderFamily: W6FilterCompositePictureDestinationShaderFamilyV1 = W6FilterCompositePictureDestinationShaderFamilyV1.SampledTextureDestinationRead,
    public val groupZeroAbi: W6FilterCompositePictureDestinationGroupZeroAbiV1 = W6FilterCompositePictureDestinationGroupZeroAbiV1.SourceTextureThenDestinationSnapshot,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val sourceScene = sourceSceneCanonicalId; private val targetExtent = targetExtentI32.copy(); private val sourceExtent = sourceExtentI32.copy(); private val snapshotExtent = snapshotExtentI32.copy()
    private val bounds = sourceBoundsTargetI32.copy(); private val origin = Point2I32(destinationOriginTargetI32.x, destinationOriginTargetI32.y); private val offset = Point2I32(sourceSampleOffsetTargetLocalI32.x, sourceSampleOffsetTargetLocalI32.y); private val scissor = compositeScissorTargetLocalI32.copy()
    init {
        require(source != destination && source != destinationSnapshot && destination != destinationSnapshot && sourceScene.isNotBlank() && sourceCommandIndexI32 >= 0 && sourceGenerationI64 >= 0L)
        require(!bounds.isEmpty && !scissor.isEmpty && requiredDestinationVersion.valueI64 >= 0L && destinationVersionBefore.valueI64 >= 0L && destinationVersionAfter.valueI64 >= destinationVersionBefore.valueI64)
        require(blend.snapshotResource == destinationSnapshot && blend.requiredDestinationVersion == requiredDestinationVersion && targetExtent.width > 0 && targetExtent.height > 0 && sourceExtent.width > 0 && sourceExtent.height > 0 && snapshotExtent == targetExtent)
        require(targetFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL && sourceFormat == targetFormat && snapshotFormat == targetFormat && targetSampleCountI32 == 1 && sourceSampleCountI32 == 1 && snapshotSampleCountI32 == 1 && load == AttachmentLoadPlan.Load && store == AttachmentStorePlan.Store)
    }
    public fun copySourceSceneCanonicalId(): String = sourceScene; public fun copyTargetExtentI32(): SizeI32 = targetExtent.copy(); public fun copySourceExtentI32(): SizeI32 = sourceExtent.copy(); public fun copySnapshotExtentI32(): SizeI32 = snapshotExtent.copy()
    public fun copySourceBoundsTargetI32(): RectI32 = bounds.copy(); public fun copyDestinationOriginTargetI32(): Point2I32 = Point2I32(origin.x, origin.y); public fun copySourceSampleOffsetTargetLocalI32(): Point2I32 = Point2I32(offset.x, offset.y); public fun copyCompositeScissorTargetLocalI32(): RectI32 = scissor.copy()
    public fun nativeSiteOwnerV1(): NativeSiteOwnerV1 = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1(): String = W6FilterCompositePictureDestinationNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}

public class W6FilterCompositePictureDestinationNativeSiteRecipeV1 internal constructor(public val host: W6FilterCompositePictureDestinationRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = host.nativeSiteOwnerV1(); override val family = NativeSiteRecipeFamilyV1.W6FilterCompositePictureDestination
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); text("source", host.source.value); text("destination", host.destination.value); text("boundSource", host.boundSource.value); enum("provenance", host.provenance); text("scene", host.copySourceSceneCanonicalId()); int("command", host.sourceCommandIndexI32); int("plannedCommand", host.plannedCommandId.valueI32); long("sourceGeneration", host.sourceGenerationI64); text("snapshot", host.destinationSnapshot.value); long("requiredSnapshotVersion", host.requiredDestinationVersion.valueI64)
        rect("bounds", host.copySourceBoundsTargetI32()); point("origin", host.copyDestinationOriginTargetI32()); point("offset", host.copySourceSampleOffsetTargetLocalI32()); rect("scissor", host.copyCompositeScissorTargetLocalI32()); int("target.width", host.copyTargetExtentI32().width); int("target.height", host.copyTargetExtentI32().height); int("source.width", host.copySourceExtentI32().width); int("source.height", host.copySourceExtentI32().height); int("snapshot.width", host.copySnapshotExtentI32().width); int("snapshot.height", host.copySnapshotExtentI32().height)
        blend("blend", host.blend); long("before", host.destinationVersionBefore.valueI64); long("after", host.destinationVersionAfter.valueI64); int("scissorAdmitted", if (host.compositeScissorAdmitted) 1 else 0); enum("load", host.load); enum("store", host.store); enum("target.format", host.targetFormat); int("target.samples", host.targetSampleCountI32); enum("source.format", host.sourceFormat); int("source.samples", host.sourceSampleCountI32); enum("snapshot.format", host.snapshotFormat); int("snapshot.samples", host.snapshotSampleCountI32); enum("shader", host.shaderFamily); enum("abi", host.groupZeroAbi); int("draw.vertices", host.draw.vertexCountI32); int("draw.instances", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

public fun freezeW6FilterCompositePictureDestinationRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FilterCompositePictureDestinationRecipeV1> = java.util.Collections.unmodifiableMap(linkedMapOf<PlanPassId, W6FilterCompositePictureDestinationRecipeV1>().apply {
    val pictureSources = passes.filterIsInstance<PlanPass.PictureSourcePass>().associateBy { it.output }
    passes.filterIsInstance<PlanPass.FilterComposite>().forEach { pass ->
        val operation = pass.operation as? FilterCompositeOperationV1.Picture ?: return@forEach; val terminal = requireNotNull(operation.terminal); val blend = terminal.blend as? BlendPlan.DestinationReadV1 ?: return@forEach; val boundSource = pass.evaluationKey.boundSourceId
        val provenance = when (val sourcePass = pictureSources[boundSource]) { null -> W6FilterCompositePictureDestinationProvenanceV1.NoPictureSourcePass; else -> if (sourcePass.graphTextureOperand == null) W6FilterCompositePictureDestinationProvenanceV1.PictureSourcePassWithoutGraphOperand else return@forEach }
        if (terminal.copyCompositeScissorTargetLocalI32() == null) return@forEach
        require(pass.source == terminal.source && pass.replacedLayerSource == null); val snapshotId = requireNotNull(blend.snapshotResource); val target = resources.single { it.id == pass.destination }; val source = resources.single { it.id == pass.source }; val snapshot = resources.single { it.id == snapshotId }
        val targetFormat = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 FilterComposite.Picture requires a color target."); val sourceFormat = (source.format as? PlanTextureFormat.Color)?.value ?: error("W6 FilterComposite.Picture requires a color source."); val snapshotFormat = (snapshot.format as? PlanTextureFormat.Color)?.value ?: error("W6 FilterComposite.Picture requires a color snapshot.")
        require(PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in source.usages() && PlanResourceUsage.Sampled in snapshot.usages())
        require(put(pass.id, W6FilterCompositePictureDestinationRecipeV1(pass.id, pass.source, pass.destination, boundSource, provenance, operation.sourceSceneCanonicalId, operation.sourceCommandIndexI32, terminal.plannedCommandId, terminal.sourceGenerationI64, snapshotId, blend.requiredDestinationVersion, requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), requireNotNull(snapshot.copyExtent()), terminal.copySourceBoundsTargetI32(), terminal.copyDestinationOriginTargetI32(), terminal.copySourceSampleOffsetTargetLocalI32(), requireNotNull(terminal.copyCompositeScissorTargetLocalI32()), blend, terminal.destinationVersionBefore, pass.destinationVersionAfter, terminal.compositeScissorAdmitted, targetFormat, target.sampleCountI32, sourceFormat, source.sampleCountI32, snapshotFormat, snapshot.sampleCountI32, terminal.load, terminal.store)) == null)
    }
})
