package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

/** IIIb2a's active PictureComposite: a graph-texture source with no filter or destination read. */
public enum class W6PictureCompositeGraphShaderFamilyV1 { GraphTextureAlpha }
public enum class W6PictureCompositeGraphGroupZeroAbiV1 { OneTexture }

public class W6PictureCompositeGraphRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId,
    public val source: PlanResourceId,
    public val destination: PlanResourceId,
    public val graphSealedSource: PlanResourceId,
    public val graphSealedSourceGenerationI64: Long,
    public val alphaF32: Float,
    targetExtentI32: SizeI32,
    sourceExtentI32: SizeI32,
    sourceBoundsTargetI32: RectI32,
    sourceSampleOffsetTargetLocalI32: Point2I32,
    compositeScissorTargetLocalI32: RectI32,
    public val blend: BlendPlan,
    public val targetFormat: PlanLogicalColorFormat,
    public val targetSampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat,
    public val sourceSampleCountI32: Int,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.Load,
    public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val shaderFamily: W6PictureCompositeGraphShaderFamilyV1 = W6PictureCompositeGraphShaderFamilyV1.GraphTextureAlpha,
    public val groupZeroAbi: W6PictureCompositeGraphGroupZeroAbiV1 = W6PictureCompositeGraphGroupZeroAbiV1.OneTexture,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val frozenTargetExtent = targetExtentI32.copy()
    private val frozenSourceExtent = sourceExtentI32.copy()
    private val frozenSourceBounds = sourceBoundsTargetI32.copy()
    private val frozenSampleOffset = Point2I32(sourceSampleOffsetTargetLocalI32.x, sourceSampleOffsetTargetLocalI32.y)
    private val frozenScissor = compositeScissorTargetLocalI32.copy()

    init {
        require(source != destination && graphSealedSource != destination && alphaF32.isFinite() && alphaF32 in 0f..1f)
        require(graphSealedSourceGenerationI64 >= 0L && !frozenSourceBounds.isEmpty && !frozenScissor.isEmpty)
        require(frozenTargetExtent.width > 0 && frozenTargetExtent.height > 0 && frozenSourceExtent.width > 0 && frozenSourceExtent.height > 0)
        require(targetFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL && sourceFormat == targetFormat)
        require(targetSampleCountI32 == 1 && sourceSampleCountI32 == 1)
        require(load == AttachmentLoadPlan.Load && store == AttachmentStorePlan.Store && blend !is BlendPlan.DestinationReadV1)
    }

    public fun copyTargetExtentI32(): SizeI32 = frozenTargetExtent.copy()
    public fun copySourceExtentI32(): SizeI32 = frozenSourceExtent.copy()
    public fun copySourceBoundsTargetI32(): RectI32 = frozenSourceBounds.copy()
    public fun copySourceSampleOffsetTargetLocalI32(): Point2I32 = Point2I32(frozenSampleOffset.x, frozenSampleOffset.y)
    public fun copyCompositeScissorTargetLocalI32(): RectI32 = frozenScissor.copy()
    public fun nativeSiteOwnerV1(): NativeSiteOwnerV1 = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1(): String = W6PictureCompositeGraphNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}

public class W6PictureCompositeGraphNativeSiteRecipeV1 internal constructor(public val host: W6PictureCompositeGraphRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = host.nativeSiteOwnerV1()
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W6PictureCompositeGraph
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32)
        text("source", host.source.value); text("destination", host.destination.value)
        text("graphSealedSource", host.graphSealedSource.value); long("graphGeneration", host.graphSealedSourceGenerationI64)
        int("alphaBits", host.alphaF32.toBits()); rect("sourceBounds", host.copySourceBoundsTargetI32())
        point("sampleOffset", host.copySourceSampleOffsetTargetLocalI32()); rect("scissor", host.copyCompositeScissorTargetLocalI32())
        int("targetExtentWidth", host.copyTargetExtentI32().width); int("targetExtentHeight", host.copyTargetExtentI32().height)
        int("sourceExtentWidth", host.copySourceExtentI32().width); int("sourceExtentHeight", host.copySourceExtentI32().height)
        blend("blend", host.blend); enum("load", host.load); enum("store", host.store)
        enum("targetFormat", host.targetFormat); int("targetSamples", host.targetSampleCountI32)
        enum("sourceFormat", host.sourceFormat); int("sourceSamples", host.sourceSampleCountI32)
        enum("shader", host.shaderFamily); enum("abi", host.groupZeroAbi)
        int("draw.vertices", host.draw.vertexCountI32); int("draw.instances", host.draw.instanceCountI32)
        int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

public fun freezeW6PictureCompositeGraphRecipesV1(
    passes: List<PlanPass>, resources: List<PlanResource>,
): Map<PlanPassId, W6PictureCompositeGraphRecipeV1> = java.util.Collections.unmodifiableMap(
    linkedMapOf<PlanPassId, W6PictureCompositeGraphRecipeV1>().apply {
        val pictureSources = passes.filterIsInstance<PlanPass.PictureSourcePass>().associateBy { it.output }
        passes.filterIsInstance<PlanPass.PictureComposite>().forEach { pass ->
            val operands = requireNotNull(pass.operands)
            val scissor = operands.copyCompositeScissorTargetLocalI32() ?: return@forEach
            val sourcePass = pictureSources[pass.source] ?: return@forEach
            val operand = sourcePass.graphTextureOperand ?: return@forEach
            if (operand.colorFilter != null || operands.blend is BlendPlan.DestinationReadV1) return@forEach
            require(operand.finalBlend.canonicalLabel == operands.blend.canonicalLabel)
            val target = resources.single { it.id == pass.destination }
            val source = resources.single { it.id == pass.source }
            require(resources.any { it.id == operand.sealedSourceId })
            require(put(pass.id, W6PictureCompositeGraphRecipeV1(
                pass.id, pass.source, pass.destination, operand.sealedSourceId, operand.sealedSourceGenerationI64, operand.alphaF32,
                requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), operands.copySourceBoundsTargetI32(),
                operands.copySourceSampleOffsetTargetLocalI32(), scissor, operands.blend,
                (target.format as PlanTextureFormat.Color).value, target.sampleCountI32,
                (source.format as PlanTextureFormat.Color).value, source.sampleCountI32,
            )) == null)
        }
    },
)
