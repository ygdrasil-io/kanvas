package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

public enum class W6PictureCompositeGraphFilteredShaderFamilyV1 { GraphTextureColorFilter }
public enum class W6PictureCompositeGraphFilteredGroupZeroAbiV1 { SourceTextureThenColorFilterUniform }

/** IIIb2b1's graph-texture terminal: the W5f program and its binding window are planner-owned. */
public class W6PictureCompositeGraphFilteredRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val source: PlanResourceId, public val destination: PlanResourceId,
    public val graphSealedSource: PlanResourceId, public val graphSealedSourceGenerationI64: Long, public val alphaF32: Float,
    public val filter: ColorFilterExecutionPlanV1, public val uniformResource: PlanResourceId,
    public val uniformOffsetBytesI64: Long, public val uniformCapacityBytesI64: Long,
    targetExtentI32: SizeI32, sourceExtentI32: SizeI32, sourceBoundsTargetI32: RectI32,
    sourceSampleOffsetTargetLocalI32: Point2I32, compositeScissorTargetLocalI32: RectI32,
    public val blend: BlendPlan, public val targetFormat: PlanLogicalColorFormat, public val targetSampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat, public val sourceSampleCountI32: Int,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.Load, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val shaderFamily: W6PictureCompositeGraphFilteredShaderFamilyV1 = W6PictureCompositeGraphFilteredShaderFamilyV1.GraphTextureColorFilter,
    public val groupZeroAbi: W6PictureCompositeGraphFilteredGroupZeroAbiV1 = W6PictureCompositeGraphFilteredGroupZeroAbiV1.SourceTextureThenColorFilterUniform,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val targetExtent = targetExtentI32.copy(); private val sourceExtent = sourceExtentI32.copy()
    private val sourceBounds = sourceBoundsTargetI32.copy(); private val sampleOffset = Point2I32(sourceSampleOffsetTargetLocalI32.x, sourceSampleOffsetTargetLocalI32.y)
    private val scissor = compositeScissorTargetLocalI32.copy()
    init { require(source != destination && graphSealedSource != destination && graphSealedSourceGenerationI64 >= 0L && alphaF32.isFinite() && alphaF32 in 0f..1f)
        require(!sourceBounds.isEmpty && !scissor.isEmpty && targetExtent.width > 0 && targetExtent.height > 0 && sourceExtent.width > 0 && sourceExtent.height > 0)
        require(uniformResource.value.startsWith("${PlanResourceRole.SourceUniformData.name}:") && uniformOffsetBytesI64 >= 0L && uniformCapacityBytesI64 >= 16L && uniformCapacityBytesI64 % 16L == 0L)
        require(Math.addExact(uniformOffsetBytesI64, maxOf(16L, filter.dynamicByteCountI64)) <= uniformCapacityBytesI64)
        require(targetFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL && sourceFormat == targetFormat && targetSampleCountI32 == 1 && sourceSampleCountI32 == 1)
        require(blend !is BlendPlan.DestinationReadV1 && load == AttachmentLoadPlan.Load && store == AttachmentStorePlan.Store) }
    public fun copyTargetExtentI32() = targetExtent.copy(); public fun copySourceExtentI32() = sourceExtent.copy(); public fun copySourceBoundsTargetI32() = sourceBounds.copy()
    public fun copySourceSampleOffsetTargetLocalI32() = Point2I32(sampleOffset.x, sampleOffset.y); public fun copyCompositeScissorTargetLocalI32() = scissor.copy()
    public fun nativeSiteOwnerV1() = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1() = W6PictureCompositeGraphFilteredNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}
public class W6PictureCompositeGraphFilteredNativeSiteRecipeV1 internal constructor(public val host: W6PictureCompositeGraphFilteredRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = host.nativeSiteOwnerV1(); override val family = NativeSiteRecipeFamilyV1.W6PictureCompositeGraphFiltered
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value)
        int("ordinal", owner.drawOrPacketOrdinalI32)
        text("source", host.source.value)
        text("destination", host.destination.value)
        text("sealed", host.graphSealedSource.value)
        long("generation", host.graphSealedSourceGenerationI64)
        int("alpha", host.alphaF32.toBits())
        text("filter.structural", host.filter.structuralIdentity)
        text("filter.canonical", host.filter.canonicalIdentity)
        long("filter.dynamic", host.filter.dynamicByteCountI64)
        text("uniform", host.uniformResource.value)
        long("uniform.offset", host.uniformOffsetBytesI64)
        long("uniform.capacity", host.uniformCapacityBytesI64)
        val targetExtent = host.copyTargetExtentI32()
        int("target.width", targetExtent.width)
        int("target.height", targetExtent.height)
        val sourceExtent = host.copySourceExtentI32()
        int("source.width", sourceExtent.width)
        int("source.height", sourceExtent.height)
        rect("bounds", host.copySourceBoundsTargetI32())
        point("offset", host.copySourceSampleOffsetTargetLocalI32())
        rect("scissor", host.copyCompositeScissorTargetLocalI32())
        blend("blend", host.blend)
        enum("load", host.load)
        enum("store", host.store)
        enum("shader", host.shaderFamily)
        enum("abi", host.groupZeroAbi)
        enum("target.format", host.targetFormat)
        enum("source.format", host.sourceFormat)
        int("target.samples", host.targetSampleCountI32)
        int("source.samples", host.sourceSampleCountI32)
        int("draw.vertices", host.draw.vertexCountI32)
        int("draw.instances", host.draw.instanceCountI32)
        int("draw.firstVertex", host.draw.firstVertexI32)
        int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}
public fun freezeW6PictureCompositeGraphFilteredRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6PictureCompositeGraphFilteredRecipeV1> = java.util.Collections.unmodifiableMap(linkedMapOf<PlanPassId, W6PictureCompositeGraphFilteredRecipeV1>().apply {
    val sources = passes.filterIsInstance<PlanPass.PictureSourcePass>().associateBy { it.output }
    passes.filterIsInstance<PlanPass.PictureComposite>().forEach { pass -> val operands = requireNotNull(pass.operands); val scissor = operands.copyCompositeScissorTargetLocalI32() ?: return@forEach; val operand = sources[pass.source]?.graphTextureOperand ?: return@forEach; val filter = operand.colorFilter ?: return@forEach; if (operands.blend is BlendPlan.DestinationReadV1) return@forEach; val target = resources.single { it.id == pass.destination }; val source = resources.single { it.id == pass.source }; require(put(pass.id, W6PictureCompositeGraphFilteredRecipeV1(pass.id, pass.source, pass.destination, operand.sealedSourceId, operand.sealedSourceGenerationI64, operand.alphaF32, filter, operand.uniformResource, requireNotNull(operand.colorFilterUniformOffsetI64), requireNotNull(operand.colorFilterUniformByteCountI64), requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), operands.copySourceBoundsTargetI32(), operands.copySourceSampleOffsetTargetLocalI32(), scissor, operands.blend, (target.format as PlanTextureFormat.Color).value, target.sampleCountI32, (source.format as PlanTextureFormat.Color).value, source.sampleCountI32)) == null) }
})
