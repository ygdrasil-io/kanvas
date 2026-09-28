package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

/** IIIc3c1's external Picture filter: the graph texture is the sole b0 operand. */
public enum class W6FilterCompositePictureGraphShaderFamilyV1 { GraphTextureAlpha }
public enum class W6FilterCompositePictureGraphGroupZeroAbiV1 { GraphTexture }

public class W6FilterCompositePictureGraphRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId,
    public val source: PlanResourceId,
    public val destination: PlanResourceId,
    public val boundSource: PlanResourceId,
    public val graphSealedSource: PlanResourceId,
    public val graphSealedSourceGenerationI64: Long,
    public val alphaF32: Float,
    sourceSceneCanonicalId: String,
    public val sourceCommandIndexI32: Int,
    public val plannedCommandId: FramePlannedCommandIdI32,
    public val sourceGenerationI64: Long,
    targetExtentI32: SizeI32,
    sourceExtentI32: SizeI32,
    graphSourceExtentI32: SizeI32,
    sourceBoundsTargetI32: RectI32,
    destinationOriginTargetI32: Point2I32,
    sourceSampleOffsetTargetLocalI32: Point2I32,
    compositeScissorTargetLocalI32: RectI32,
    public val blend: BlendPlan,
    public val destinationVersionBefore: DestinationVersionI64,
    public val destinationVersionAfter: DestinationVersionI64,
    public val compositeScissorAdmitted: Boolean,
    public val targetFormat: PlanLogicalColorFormat,
    public val targetSampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat,
    public val sourceSampleCountI32: Int,
    public val graphSourceFormat: PlanLogicalColorFormat,
    public val graphSourceSampleCountI32: Int,
    public val load: AttachmentLoadPlan,
    public val store: AttachmentStorePlan,
    public val shaderFamily: W6FilterCompositePictureGraphShaderFamilyV1 = W6FilterCompositePictureGraphShaderFamilyV1.GraphTextureAlpha,
    public val groupZeroAbi: W6FilterCompositePictureGraphGroupZeroAbiV1 = W6FilterCompositePictureGraphGroupZeroAbiV1.GraphTexture,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val sourceScene = sourceSceneCanonicalId
    private val targetExtent = targetExtentI32.copy()
    private val sourceExtent = sourceExtentI32.copy()
    private val graphSourceExtent = graphSourceExtentI32.copy()
    private val bounds = sourceBoundsTargetI32.copy()
    private val origin = Point2I32(destinationOriginTargetI32.x, destinationOriginTargetI32.y)
    private val offset = Point2I32(sourceSampleOffsetTargetLocalI32.x, sourceSampleOffsetTargetLocalI32.y)
    private val scissor = compositeScissorTargetLocalI32.copy()

    init {
        require(source != destination && graphSealedSource != destination && sourceScene.isNotBlank())
        require(sourceCommandIndexI32 >= 0 && sourceGenerationI64 >= 0L && graphSealedSourceGenerationI64 >= 0L)
        require(alphaF32.isFinite() && alphaF32 in 0f..1f && !bounds.isEmpty && !scissor.isEmpty)
        require(blend !is BlendPlan.DestinationReadV1 && targetExtent.width > 0 && targetExtent.height > 0 &&
            sourceExtent.width > 0 && sourceExtent.height > 0 && graphSourceExtent.width > 0 && graphSourceExtent.height > 0)
        require(targetFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL && sourceFormat == targetFormat && graphSourceFormat == targetFormat)
        require(targetSampleCountI32 == 1 && sourceSampleCountI32 == 1 && graphSourceSampleCountI32 == 1 &&
            load == AttachmentLoadPlan.Load && store == AttachmentStorePlan.Store)
    }

    public fun copySourceSceneCanonicalId(): String = sourceScene
    public fun copyTargetExtentI32(): SizeI32 = targetExtent.copy()
    public fun copySourceExtentI32(): SizeI32 = sourceExtent.copy()
    public fun copyGraphSourceExtentI32(): SizeI32 = graphSourceExtent.copy()
    public fun copySourceBoundsTargetI32(): RectI32 = bounds.copy()
    public fun copyDestinationOriginTargetI32(): Point2I32 = Point2I32(origin.x, origin.y)
    public fun copySourceSampleOffsetTargetLocalI32(): Point2I32 = Point2I32(offset.x, offset.y)
    public fun copyCompositeScissorTargetLocalI32(): RectI32 = scissor.copy()
    public fun nativeSiteOwnerV1(): NativeSiteOwnerV1 = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1(): String = W6FilterCompositePictureGraphNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}

public class W6FilterCompositePictureGraphNativeSiteRecipeV1 internal constructor(
    public val host: W6FilterCompositePictureGraphRecipeV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = host.nativeSiteOwnerV1()
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W6FilterCompositePictureGraph
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32)
        text("source", host.source.value); text("destination", host.destination.value); text("boundSource", host.boundSource.value)
        text("graphSealedSource", host.graphSealedSource.value); long("graphGeneration", host.graphSealedSourceGenerationI64); int("alphaBits", host.alphaF32.toBits())
        text("scene", host.copySourceSceneCanonicalId()); int("command", host.sourceCommandIndexI32); int("plannedCommand", host.plannedCommandId.valueI32); long("sourceGeneration", host.sourceGenerationI64)
        rect("bounds", host.copySourceBoundsTargetI32()); point("origin", host.copyDestinationOriginTargetI32()); point("offset", host.copySourceSampleOffsetTargetLocalI32()); rect("scissor", host.copyCompositeScissorTargetLocalI32())
        int("target.width", host.copyTargetExtentI32().width); int("target.height", host.copyTargetExtentI32().height); int("source.width", host.copySourceExtentI32().width); int("source.height", host.copySourceExtentI32().height); int("graph.width", host.copyGraphSourceExtentI32().width); int("graph.height", host.copyGraphSourceExtentI32().height)
        blend("blend", host.blend); long("before", host.destinationVersionBefore.valueI64); long("after", host.destinationVersionAfter.valueI64); int("scissorAdmitted", if (host.compositeScissorAdmitted) 1 else 0)
        enum("load", host.load); enum("store", host.store); enum("target.format", host.targetFormat); int("target.samples", host.targetSampleCountI32); enum("source.format", host.sourceFormat); int("source.samples", host.sourceSampleCountI32); enum("graph.format", host.graphSourceFormat); int("graph.samples", host.graphSourceSampleCountI32); enum("shader", host.shaderFamily); enum("abi", host.groupZeroAbi)
        int("draw.vertices", host.draw.vertexCountI32); int("draw.instances", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

public fun freezeW6FilterCompositePictureGraphRecipesV1(
    passes: List<PlanPass>, resources: List<PlanResource>,
): Map<PlanPassId, W6FilterCompositePictureGraphRecipeV1> = java.util.Collections.unmodifiableMap(
    linkedMapOf<PlanPassId, W6FilterCompositePictureGraphRecipeV1>().apply {
        val pictureSources = passes.filterIsInstance<PlanPass.PictureSourcePass>().associateBy { it.output }
        passes.filterIsInstance<PlanPass.FilterComposite>().forEach { pass ->
            val operation = pass.operation as? FilterCompositeOperationV1.Picture ?: return@forEach
            val terminal = requireNotNull(operation.terminal)
            val sourcePass = pictureSources[pass.evaluationKey.boundSourceId] ?: return@forEach
            val operand = sourcePass.graphTextureOperand ?: return@forEach
            if (operand.colorFilter != null || terminal.blend is BlendPlan.DestinationReadV1 || terminal.copyCompositeScissorTargetLocalI32() == null) return@forEach
            require(pass.source == terminal.source && pass.replacedLayerSource == null && operand.finalBlend.canonicalLabel == terminal.blend.canonicalLabel)
            val target = resources.single { it.id == pass.destination }
            val source = resources.single { it.id == pass.source }
            val graphSource = resources.single { it.id == operand.sealedSourceId }
            val targetFormat = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 FilterComposite.Picture graph requires a color target.")
            val sourceFormat = (source.format as? PlanTextureFormat.Color)?.value ?: error("W6 FilterComposite.Picture graph requires a color source.")
            val graphFormat = (graphSource.format as? PlanTextureFormat.Color)?.value ?: error("W6 FilterComposite.Picture graph requires a color operand.")
            require(PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in source.usages() && PlanResourceUsage.Sampled in graphSource.usages())
            require(put(pass.id, W6FilterCompositePictureGraphRecipeV1(
                pass.id, pass.source, pass.destination, pass.evaluationKey.boundSourceId, operand.sealedSourceId,
                operand.sealedSourceGenerationI64, operand.alphaF32, operation.sourceSceneCanonicalId, operation.sourceCommandIndexI32,
                terminal.plannedCommandId, terminal.sourceGenerationI64, requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()),
                requireNotNull(graphSource.copyExtent()), terminal.copySourceBoundsTargetI32(), terminal.copyDestinationOriginTargetI32(),
                terminal.copySourceSampleOffsetTargetLocalI32(), requireNotNull(terminal.copyCompositeScissorTargetLocalI32()), terminal.blend,
                terminal.destinationVersionBefore, pass.destinationVersionAfter, terminal.compositeScissorAdmitted, targetFormat, target.sampleCountI32,
                sourceFormat, source.sampleCountI32, graphFormat, graphSource.sampleCountI32, terminal.load, terminal.store,
            )) == null)
        }
    },
)
