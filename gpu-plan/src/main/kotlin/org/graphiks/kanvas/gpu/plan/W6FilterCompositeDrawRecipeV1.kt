package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

/** IIIc1's direct-filter terminal: one sealed sampled source, never a restore or destination read. */
public enum class W6FilterCompositeDrawShaderFamilyV1 { SampledTextureAlpha }
public enum class W6FilterCompositeDrawGroupZeroAbiV1 { SourceTexture }

public class W6FilterCompositeDrawRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val source: PlanResourceId, public val destination: PlanResourceId,
    targetExtentI32: SizeI32, sourceExtentI32: SizeI32, sourceBoundsTargetI32: RectI32,
    destinationOriginParentI32: Point2I32, sourceSampleOffsetTargetLocalI32: Point2I32,
    compositeScissorTargetLocalI32: RectI32, public val blend: BlendPlan,
    public val targetFormat: PlanLogicalColorFormat, public val targetSampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat, public val sourceSampleCountI32: Int,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.Load, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val shaderFamily: W6FilterCompositeDrawShaderFamilyV1 = W6FilterCompositeDrawShaderFamilyV1.SampledTextureAlpha,
    public val groupZeroAbi: W6FilterCompositeDrawGroupZeroAbiV1 = W6FilterCompositeDrawGroupZeroAbiV1.SourceTexture,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val targetExtent = targetExtentI32.copy(); private val sourceExtent = sourceExtentI32.copy()
    private val sourceBounds = sourceBoundsTargetI32.copy(); private val destinationOrigin = Point2I32(destinationOriginParentI32.x, destinationOriginParentI32.y)
    private val sampleOffset = Point2I32(sourceSampleOffsetTargetLocalI32.x, sourceSampleOffsetTargetLocalI32.y); private val scissor = compositeScissorTargetLocalI32.copy()
    init { require(source != destination && !sourceBounds.isEmpty && !scissor.isEmpty && targetExtent.width > 0 && targetExtent.height > 0 && sourceExtent.width > 0 && sourceExtent.height > 0)
        require(targetFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL && sourceFormat == targetFormat && targetSampleCountI32 == 1 && sourceSampleCountI32 == 1)
        require(load == AttachmentLoadPlan.Load && store == AttachmentStorePlan.Store && blend !is BlendPlan.DestinationReadV1) }
    public fun copyTargetExtentI32() = targetExtent.copy(); public fun copySourceExtentI32() = sourceExtent.copy(); public fun copySourceBoundsTargetI32() = sourceBounds.copy()
    public fun copyDestinationOriginParentI32() = Point2I32(destinationOrigin.x, destinationOrigin.y); public fun copySourceSampleOffsetTargetLocalI32() = Point2I32(sampleOffset.x, sampleOffset.y); public fun copyCompositeScissorTargetLocalI32() = scissor.copy()
    public fun nativeSiteOwnerV1() = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1() = W6FilterCompositeDrawNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}
public class W6FilterCompositeDrawNativeSiteRecipeV1 internal constructor(public val host: W6FilterCompositeDrawRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = host.nativeSiteOwnerV1(); override val family = NativeSiteRecipeFamilyV1.W6FilterCompositeDraw
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) { text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); text("source", host.source.value); text("destination", host.destination.value); rect("bounds", host.copySourceBoundsTargetI32()); point("origin", host.copyDestinationOriginParentI32()); point("offset", host.copySourceSampleOffsetTargetLocalI32()); rect("scissor", host.copyCompositeScissorTargetLocalI32()); int("target.width", host.copyTargetExtentI32().width); int("target.height", host.copyTargetExtentI32().height); int("source.width", host.copySourceExtentI32().width); int("source.height", host.copySourceExtentI32().height); blend("blend", host.blend); enum("load", host.load); enum("store", host.store); enum("target.format", host.targetFormat); int("target.samples", host.targetSampleCountI32); enum("source.format", host.sourceFormat); int("source.samples", host.sourceSampleCountI32); enum("shader", host.shaderFamily); enum("abi", host.groupZeroAbi); int("draw.vertices", host.draw.vertexCountI32); int("draw.instances", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32) }
}
public fun freezeW6FilterCompositeDrawRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FilterCompositeDrawRecipeV1> = java.util.Collections.unmodifiableMap(linkedMapOf<PlanPassId, W6FilterCompositeDrawRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.FilterComposite>().forEach { pass -> val operation = pass.operation as? FilterCompositeOperationV1.Draw ?: return@forEach; if (operation.noOp) return@forEach; val scissor = pass.copyCompositeScissorTargetLocalI32() ?: return@forEach; val target = resources.single { it.id == pass.destination }; val source = resources.single { it.id == pass.source }; require(put(pass.id, W6FilterCompositeDrawRecipeV1(pass.id, pass.source, pass.destination, requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), pass.copySourceBoundsTargetI32(), pass.copyDestinationOriginParentI32(), pass.copySourceSampleOffsetTargetLocalI32(), scissor, operation.blend, (target.format as PlanTextureFormat.Color).value, target.sampleCountI32, (source.format as PlanTextureFormat.Color).value, source.sampleCountI32)) == null) }
})
