package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32

/** IIIb1's active direct PictureComposite.  Graph-texture operands deliberately remain open. */
public enum class W6PictureCompositeShaderFamilyV1 { SampledSource }
public enum class W6PictureCompositeGroupZeroAbiV1 { OneTexture }

public class W6PictureCompositeRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId,
    public val source: PlanResourceId,
    public val destination: PlanResourceId,
    targetExtentI32: SizeI32,
    sourceExtentI32: SizeI32,
    sourceBoundsTargetI32: RectI32,
    sourceSampleOffsetTargetLocalI32: Point2I32,
    compositeScissorTargetLocalI32: RectI32,
    public val blend: BlendPlan,
    public val destinationSnapshot: PlanResourceId?,
    public val targetFormat: PlanLogicalColorFormat,
    public val targetSampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat,
    public val sourceSampleCountI32: Int,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.Load,
    public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val shaderFamily: W6PictureCompositeShaderFamilyV1 = W6PictureCompositeShaderFamilyV1.SampledSource,
    public val groupZeroAbi: W6PictureCompositeGroupZeroAbiV1 = W6PictureCompositeGroupZeroAbiV1.OneTexture,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val frozenTargetExtent = targetExtentI32.copy()
    private val frozenSourceExtent = sourceExtentI32.copy()
    private val frozenSourceBounds = sourceBoundsTargetI32.copy()
    private val frozenSampleOffset = Point2I32(sourceSampleOffsetTargetLocalI32.x, sourceSampleOffsetTargetLocalI32.y)
    private val frozenScissor = compositeScissorTargetLocalI32.copy()
    init {
        require(source != destination && !frozenSourceBounds.isEmpty && !frozenScissor.isEmpty)
        require(frozenTargetExtent.width > 0 && frozenTargetExtent.height > 0 && frozenSourceExtent.width > 0 && frozenSourceExtent.height > 0)
        require(targetFormat == PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL && sourceFormat == targetFormat)
        require(targetSampleCountI32 == 1 && sourceSampleCountI32 == 1)
        require(load == AttachmentLoadPlan.Load && store == AttachmentStorePlan.Store)
        require((blend as? BlendPlan.DestinationReadV1)?.snapshotResource == destinationSnapshot)
    }
    public fun copyTargetExtentI32(): SizeI32 = frozenTargetExtent.copy()
    public fun copySourceExtentI32(): SizeI32 = frozenSourceExtent.copy()
    public fun copySourceBoundsTargetI32(): RectI32 = frozenSourceBounds.copy()
    public fun copySourceSampleOffsetTargetLocalI32(): Point2I32 = Point2I32(frozenSampleOffset.x, frozenSampleOffset.y)
    public fun copyCompositeScissorTargetLocalI32(): RectI32 = frozenScissor.copy()
    public fun nativeSiteOwnerV1(): NativeSiteOwnerV1 = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1(): String = W6PictureCompositeNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}

public fun freezeW6PictureCompositeRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6PictureCompositeRecipeV1> =
    java.util.Collections.unmodifiableMap(linkedMapOf<PlanPassId, W6PictureCompositeRecipeV1>().apply {
        passes.filterIsInstance<PlanPass.PictureComposite>().forEach { pass ->
            // Empty is the already-sealed no-op recipe; IIIb1 owns only active direct sources.
            val operands = requireNotNull(pass.operands)
            val scissor = operands.copyCompositeScissorTargetLocalI32() ?: return@forEach
            if (passes.filterIsInstance<PlanPass.PictureSourcePass>().any { it.output == pass.source && it.graphTextureOperand != null }) return@forEach
            val target = resources.single { it.id == pass.destination }
            val source = resources.single { it.id == pass.source }
            val snapshot = (operands.blend as? BlendPlan.DestinationReadV1)?.snapshotResource
            require(put(pass.id, W6PictureCompositeRecipeV1(pass.id, pass.source, pass.destination,
                requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), operands.copySourceBoundsTargetI32(),
                operands.copySourceSampleOffsetTargetLocalI32(), scissor, operands.blend, snapshot,
                (target.format as PlanTextureFormat.Color).value, target.sampleCountI32,
                (source.format as PlanTextureFormat.Color).value, source.sampleCountI32)) == null)
        }
    })
