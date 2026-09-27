@file:OptIn(ExperimentalUnsignedTypes::class)

package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.TileMode
import org.graphiks.kanvas.render.ir.CapturedDropShadowModeV1

/**
 * Stable planner owner of one native-program selection.  The ordinal denotes a draw for W6
 * RenderPass entries and a packet for W4e entries; bundle distinguishes native programs inside
 * that packet.  The bounded 2P0–2P6 families currently have exactly one bundle each.
 */
public data class NativeSiteOwnerV1(
    public val ownerPassId: PlanPassId,
    public val drawOrPacketOrdinalI32: Int,
    public val bundleOrdinalI32: Int,
) {
    init {
        require(drawOrPacketOrdinalI32 >= 0)
        require(bundleOrdinalI32 >= 0)
    }
}

public enum class NativeSiteRecipeFamilyV1 {
    W6SolidRect,
    W6AnalyticRect,
    W6AnalyticRRect,
    W6Point,
    W6PreparedVertices,
    W6PlainLayerComposite,
    W4eClipMaskInitialize,
    W6bCoverageRaster,
    W6FullscreenEmpty,
    W6FullscreenCoverageAlpha,
    W6FullscreenCoverageSolidRect,
    W6FullscreenCoverageRetain,
    W6FullscreenPictureSourceLayer,
    W6FullscreenPictureSourceGraph,
    W6FilterSpatialCrop,
    W6FilterSpatialOffset,
    W6FilterSpatialTile,
    W6FilterMorphology,
    W6FilterColorFilter,
    W6FilterMerge,
    W6FilterBlend,
    W6FilterSeparableBlur,
    W6FilterMaskBlurNormal,
    W6FilterMaskBlurDualSource,
    W6FilterMaskShader,
    W6FilterMaskTable,
    W6FilterMaterializedSource,
    W6FilterDropShadowColorize,
    W6FilterDropShadowComposite,
    W6FilteredLayerComposite,
    W6PictureCompositeGraph,
    W6PictureCompositeGraphFiltered,
    W6PictureCompositeGraphDestination,
    W6FilterCompositeDraw,
    W6FilterCompositeLayerPlain,
    W6FilterCompositeLayerFiltered,
    W6FilterCompositeLayerDestination,
    W6FilterCompositeLayerFilteredDestination,
    W6FilterCompositePicturePlain,
    W6FilterCompositePictureGraph,
    W6FilterCompositePictureGraphFiltered,
    W6FilterCompositePictureDestination,
}

public class W6FilteredLayerCompositeNativeSiteRecipeV1 internal constructor(public val host: W6FilteredLayerCompositeRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1
    override val owner = host.nativeSiteOwnerV1()
    override val family = NativeSiteRecipeFamilyV1.W6FilteredLayerComposite
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", owner.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); text("source", host.source.value); text("target", host.destination.value)
        rect("sourceBounds", host.copySourceBoundsLayerI32()); point("destinationOrigin", host.copyDestinationOriginParentI32()); rect("scissor", host.copyScissorParentI32()); int("targetExtentWidth", host.copyTargetExtentI32().width); int("targetExtentHeight", host.copyTargetExtentI32().height); int("sourceExtentWidth", host.copySourceExtentI32().width); int("sourceExtentHeight", host.copySourceExtentI32().height)
        int("alphaBits", host.alphaF32.toBits()); blend("blend", host.blend); text("execution.structural", host.execution.structuralIdentity); text("execution.canonical", host.execution.canonicalIdentity); long("execution.dynamicBytes", host.execution.dynamicByteCountI64)
        text("uniform", host.uniformResource.value); long("uniformOffset", host.uniformOffsetBytesI64); long("uniformCapacity", host.uniformCapacityBytesI64); enum("load", host.load); enum("store", host.store); enum("target", host.target.format); int("targetSamples", host.target.sampleCountI32); enum("sourceFormat", host.sourceFormat); int("sourceSamples", host.sourceSampleCountI32); enum("abi", host.groupZeroAbi); enum("shader", host.shaderFamily); int("draw.vertices", host.draw.vertexCountI32); int("draw.instances", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

/** IIf1's one-coverage plus pre-issued W5 material ABI; neither table nor source materialization belongs here. */
public enum class W6FilterMaskShaderFamilyV1 { FrozenW5MaterialCoverageAlpha }
public enum class W6FilterMaskShaderGroupZeroAbiV1 { CoverageThenFrozenW5Material }
public enum class W6FilterMaskShaderBindingKindV1 { UniformBuffer, StorageBuffer, SampledTexture, Sampler }
public data class W6FilterMaskShaderBindingAbiV1(public val bindingI32: Int, public val kind: W6FilterMaskShaderBindingKindV1) {
    init { require(bindingI32 >= 0) }
}
public class W6FilterMaskShaderRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val target: PlanResourceId, public val coverageSource: PlanResourceId,
    public val occurrenceIdI32: Int, public val material: MaterialPlanRef, public val uniformResource: PlanResourceId,
    public val uniformOffsetBytesI64: Long, public val uniformCapacityBytesI64: Long,
    public val materialStructuralId: String, public val materialCanonicalIdentity: String,
    public val materialUniformByteCountI64: Long, bindingManifest: List<W6FilterMaskShaderBindingAbiV1>,
    materialDeviceOriginI32: org.graphiks.math.geometry.Point2I32,
    extent: org.graphiks.math.geometry.SizeI32, coverageExtent: org.graphiks.math.geometry.SizeI32,
    known: org.graphiks.math.geometry.RectI32, offset: org.graphiks.math.geometry.Point2I32,
    public val targetFormat: PlanLogicalColorFormat, public val coverageFormat: PlanLogicalColorFormat,
    public val sampleCountI32: Int, public val coverageSampleCountI32: Int,
    public val shaderFamily: W6FilterMaskShaderFamilyV1 = W6FilterMaskShaderFamilyV1.FrozenW5MaterialCoverageAlpha,
    public val groupZeroAbi: W6FilterMaskShaderGroupZeroAbiV1 = W6FilterMaskShaderGroupZeroAbiV1.CoverageThenFrozenW5Material,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1, public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val frozenExtent = extent.copy(); private val frozenCoverageExtent = coverageExtent.copy(); private val frozenKnown = known.copy()
    private val frozenOffset = org.graphiks.math.geometry.Point2I32(offset.x, offset.y)
    private val frozenMaterialDeviceOrigin = org.graphiks.math.geometry.Point2I32(materialDeviceOriginI32.x, materialDeviceOriginI32.y)
    private val frozenBindingManifest = immutableList(bindingManifest)
    init { require(occurrenceIdI32 >= 0 && materialStructuralId.isNotBlank() && materialCanonicalIdentity.isNotBlank() && materialUniformByteCountI64 >= 16L && uniformOffsetBytesI64 >= 0L && uniformCapacityBytesI64 >= 16L && uniformOffsetBytesI64 % 4L == 0L && uniformCapacityBytesI64 % 16L == 0L && sampleCountI32 == 1 && coverageSampleCountI32 == 1 && !frozenKnown.isEmpty && frozenBindingManifest.firstOrNull() == W6FilterMaskShaderBindingAbiV1(0, W6FilterMaskShaderBindingKindV1.UniformBuffer) && frozenBindingManifest.map { it.bindingI32 }.distinct().size == frozenBindingManifest.size) }
    public fun copyExtent() = frozenExtent.copy(); public fun copyCoverageExtent() = frozenCoverageExtent.copy(); public fun copyKnownContentTargetLocalI32() = frozenKnown.copy()
    public fun copyOutputToCoverageOffsetTargetLocalI32() = org.graphiks.math.geometry.Point2I32(frozenOffset.x, frozenOffset.y)
    public fun copyMaterialDeviceOriginI32() = org.graphiks.math.geometry.Point2I32(frozenMaterialDeviceOrigin.x, frozenMaterialDeviceOrigin.y)
    public fun bindingManifest(): List<W6FilterMaskShaderBindingAbiV1> = frozenBindingManifest
    public fun canonicalLogicalEncodingV1() = W6FilterMaskShaderNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}
public class W6FilterMaskShaderNativeSiteRecipeV1 internal constructor(public val host: W6FilterMaskShaderRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = NativeSiteOwnerV1(host.ownerPassId, 0, 0); override val family = NativeSiteRecipeFamilyV1.W6FilterMaskShader
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); text("target", host.target.value); text("coverageSource", host.coverageSource.value); int("occurrence", host.occurrenceIdI32); int("material", host.material.indexI32); text("uniformResource", host.uniformResource.value); long("uniformOffset", host.uniformOffsetBytesI64); long("uniformCapacity", host.uniformCapacityBytesI64); text("materialStructuralId", host.materialStructuralId); text("materialCanonicalIdentity", host.materialCanonicalIdentity); long("materialUniformByteCount", host.materialUniformByteCountI64); host.bindingManifest().forEachIndexed { index, abi -> int("binding.$index.index", abi.bindingI32); enum("binding.$index.kind", abi.kind) }; int("bindingCount", host.bindingManifest().size); point("materialDeviceOrigin", host.copyMaterialDeviceOriginI32())
        int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height); int("coverageExtentWidth", host.copyCoverageExtent().width); int("coverageExtentHeight", host.copyCoverageExtent().height); rect("known", host.copyKnownContentTargetLocalI32()); point("offset", host.copyOutputToCoverageOffsetTargetLocalI32())
        enum("targetFormat", host.targetFormat); enum("coverageFormat", host.coverageFormat); int("sampleCount", host.sampleCountI32); int("coverageSampleCount", host.coverageSampleCountI32); enum("shaderFamily", host.shaderFamily); enum("groupZeroAbi", host.groupZeroAbi); enum("load", host.load); enum("store", host.store); blend("blend", host.blend); int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

/** IIf2's captured coverage LUT.  Its 256 bytes and exact storage window are planner authority. */
public enum class W6FilterMaskTableShaderFamilyV1 { CoverageLookupStorageU32 }
public enum class W6FilterMaskTableGroupZeroAbiV1 { CoverageTextureThenTableStorage }
public class W6FilterMaskTableRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val target: PlanResourceId, public val coverageSource: PlanResourceId,
    public val tableResource: PlanResourceId, table: org.graphiks.kanvas.render.ir.ImmutableUBytes,
    public val tableGenerationI64: Long, public val tableOwnerMaskOccurrenceI32: Int,
    extent: org.graphiks.math.geometry.SizeI32, coverageExtent: org.graphiks.math.geometry.SizeI32,
    known: org.graphiks.math.geometry.RectI32, offset: org.graphiks.math.geometry.Point2I32,
    public val targetFormat: PlanLogicalColorFormat, public val coverageFormat: PlanLogicalColorFormat,
    public val sampleCountI32: Int, public val coverageSampleCountI32: Int,
    public val tableOffsetBytesI64: Long = 0L, public val tableRangeBytesI64: Long = 256L,
    public val shaderFamily: W6FilterMaskTableShaderFamilyV1 = W6FilterMaskTableShaderFamilyV1.CoverageLookupStorageU32,
    public val groupZeroAbi: W6FilterMaskTableGroupZeroAbiV1 = W6FilterMaskTableGroupZeroAbiV1.CoverageTextureThenTableStorage,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1, public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val frozenTable = org.graphiks.kanvas.render.ir.ImmutableUBytes.copyOf(table.copyToUByteArray())
    private val frozenExtent = extent.copy(); private val frozenCoverageExtent = coverageExtent.copy(); private val frozenKnown = known.copy()
    private val frozenOffset = org.graphiks.math.geometry.Point2I32(offset.x, offset.y)
    init { require(tableResource.value.startsWith("${PlanResourceRole.MaskTableData.name}:") && frozenTable.sizeI32 == 256 && tableGenerationI64 >= 0L && tableOwnerMaskOccurrenceI32 >= 0 && tableOffsetBytesI64 == 0L && tableRangeBytesI64 == 256L && sampleCountI32 == 1 && coverageSampleCountI32 == 1 && !frozenKnown.isEmpty) }
    public fun copyTable() = org.graphiks.kanvas.render.ir.ImmutableUBytes.copyOf(frozenTable.copyToUByteArray())
    public fun copyExtent() = frozenExtent.copy(); public fun copyCoverageExtent() = frozenCoverageExtent.copy(); public fun copyKnownContentTargetLocalI32() = frozenKnown.copy()
    public fun copyOutputToCoverageOffsetTargetLocalI32() = org.graphiks.math.geometry.Point2I32(frozenOffset.x, frozenOffset.y)
    public fun canonicalLogicalEncodingV1() = W6FilterMaskTableNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}
public class W6FilterMaskTableNativeSiteRecipeV1 internal constructor(public val host: W6FilterMaskTableRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = NativeSiteOwnerV1(host.ownerPassId, 0, 0); override val family = NativeSiteRecipeFamilyV1.W6FilterMaskTable
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); text("target", host.target.value); text("coverageSource", host.coverageSource.value); text("tableResource", host.tableResource.value); long("tableGeneration", host.tableGenerationI64); int("tableOwnerMaskOccurrence", host.tableOwnerMaskOccurrenceI32); long("tableOffset", host.tableOffsetBytesI64); long("tableRange", host.tableRangeBytesI64); text("tableBytes", host.copyTable().copyToUByteArray().joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) })
        int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height); int("coverageExtentWidth", host.copyCoverageExtent().width); int("coverageExtentHeight", host.copyCoverageExtent().height); rect("known", host.copyKnownContentTargetLocalI32()); point("offset", host.copyOutputToCoverageOffsetTargetLocalI32())
        enum("targetFormat", host.targetFormat); enum("coverageFormat", host.coverageFormat); int("sampleCount", host.sampleCountI32); int("coverageSampleCount", host.coverageSampleCountI32); enum("shaderFamily", host.shaderFamily); enum("groupZeroAbi", host.groupZeroAbi); enum("load", host.load); enum("store", host.store); blend("blend", host.blend); int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

public enum class W6FilterMaterializedSourceShaderFamilyV1 { SourceAndCoverageTextureLoad }
public enum class W6FilterMaterializedSourceGroupZeroAbiV1 { SourceThenCoverageTextures }
public enum class W6FilterMaterializedSourceAlphaModeV1 { ReplaceSourceAlpha, MultiplySourceAlpha }
public class W6FilterMaterializedSourceRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val target: PlanResourceId, public val source: PlanResourceId, public val coverage: PlanResourceId,
    extent: org.graphiks.math.geometry.SizeI32, sourceExtent: org.graphiks.math.geometry.SizeI32, coverageExtent: org.graphiks.math.geometry.SizeI32,
    sourceKnown: org.graphiks.math.geometry.RectI32, coverageKnown: org.graphiks.math.geometry.RectI32,
    sourceOffset: org.graphiks.math.geometry.Point2I32, coverageOffset: org.graphiks.math.geometry.Point2I32,
    targetOriginDeviceI32: org.graphiks.math.geometry.Point2I32, public val alphaMode: W6FilterMaterializedSourceAlphaModeV1,
    public val targetFormat: PlanLogicalColorFormat, public val sourceFormat: PlanLogicalColorFormat, public val coverageFormat: PlanLogicalColorFormat,
    public val sampleCountI32: Int, public val sourceSampleCountI32: Int, public val coverageSampleCountI32: Int,
    public val shaderFamily: W6FilterMaterializedSourceShaderFamilyV1 = W6FilterMaterializedSourceShaderFamilyV1.SourceAndCoverageTextureLoad,
    public val groupZeroAbi: W6FilterMaterializedSourceGroupZeroAbiV1 = W6FilterMaterializedSourceGroupZeroAbiV1.SourceThenCoverageTextures,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1, public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val e = extent.copy(); private val se = sourceExtent.copy(); private val ce = coverageExtent.copy(); private val sk = sourceKnown.copy(); private val ck = coverageKnown.copy(); private val so = org.graphiks.math.geometry.Point2I32(sourceOffset.x, sourceOffset.y); private val co = org.graphiks.math.geometry.Point2I32(coverageOffset.x, coverageOffset.y); private val origin = org.graphiks.math.geometry.Point2I32(targetOriginDeviceI32.x, targetOriginDeviceI32.y)
    init { require(target != source && target != coverage && source != coverage && sampleCountI32 == 1 && sourceSampleCountI32 == 1 && coverageSampleCountI32 == 1 && !sk.isEmpty && !ck.isEmpty) }
    public fun copyExtent() = e.copy(); public fun copySourceExtent() = se.copy(); public fun copyCoverageExtent() = ce.copy(); public fun copySourceKnownContentTargetLocalI32() = sk.copy(); public fun copyCoverageKnownContentTargetLocalI32() = ck.copy(); public fun copyOutputToSourceOffsetTargetLocalI32() = org.graphiks.math.geometry.Point2I32(so.x, so.y); public fun copyOutputToCoverageOffsetTargetLocalI32() = org.graphiks.math.geometry.Point2I32(co.x, co.y); public fun copyTargetOriginDeviceI32() = org.graphiks.math.geometry.Point2I32(origin.x, origin.y)
    public fun canonicalLogicalEncodingV1() = W6FilterMaterializedSourceNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}
public class W6FilterMaterializedSourceNativeSiteRecipeV1 internal constructor(public val host: W6FilterMaterializedSourceRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = NativeSiteOwnerV1(host.ownerPassId, 0, 0); override val family = NativeSiteRecipeFamilyV1.W6FilterMaterializedSource
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) { text("owner", host.ownerPassId.value); text("target", host.target.value); text("input.0", host.source.value); text("input.1", host.coverage.value); int("inputCount", 2); int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height); int("sourceExtentWidth", host.copySourceExtent().width); int("sourceExtentHeight", host.copySourceExtent().height); int("coverageExtentWidth", host.copyCoverageExtent().width); int("coverageExtentHeight", host.copyCoverageExtent().height); rect("sourceKnown", host.copySourceKnownContentTargetLocalI32()); rect("coverageKnown", host.copyCoverageKnownContentTargetLocalI32()); point("sourceOffset", host.copyOutputToSourceOffsetTargetLocalI32()); point("coverageOffset", host.copyOutputToCoverageOffsetTargetLocalI32()); point("targetOriginDevice", host.copyTargetOriginDeviceI32()); enum("alphaMode", host.alphaMode); enum("targetFormat", host.targetFormat); enum("sourceFormat", host.sourceFormat); enum("coverageFormat", host.coverageFormat); int("sampleCount", host.sampleCountI32); int("sourceSampleCount", host.sourceSampleCountI32); int("coverageSampleCount", host.coverageSampleCountI32); enum("shaderFamily", host.shaderFamily); enum("groupZeroAbi", host.groupZeroAbi); enum("load", host.load); enum("store", host.store); blend("blend", host.blend); int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32) }
}

/** IIg1's single blurred-alpha input, with its exact color and linear DECAL mapping frozen by the planner. */
public enum class W6FilterDropShadowColorizeShaderFamilyV1 { LinearDecalBlurredAlphaColorize }
public enum class W6FilterDropShadowColorizeGroupZeroAbiV1 { BlurredAlphaTexture }
public class W6FilterDropShadowColorizeRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val target: PlanResourceId, public val blurredSource: PlanResourceId,
    public val colorArgbU32: UInt, offsetF64: org.graphiks.math.vector.Vector2F64,
    sourceCoordinateOffsetTargetLocalF64: org.graphiks.math.vector.Vector2F64,
    sourceFootprintTargetLocalI32: org.graphiks.math.geometry.RectI32,
    outputFootprintTargetLocalI32: org.graphiks.math.geometry.RectI32,
    scissorTargetLocalI32: org.graphiks.math.geometry.RectI32,
    extent: org.graphiks.math.geometry.SizeI32, blurredExtent: org.graphiks.math.geometry.SizeI32,
    public val targetFormat: PlanLogicalColorFormat, public val blurredFormat: PlanLogicalColorFormat,
    public val sampleCountI32: Int, public val blurredSampleCountI32: Int,
    public val shaderFamily: W6FilterDropShadowColorizeShaderFamilyV1 = W6FilterDropShadowColorizeShaderFamilyV1.LinearDecalBlurredAlphaColorize,
    public val groupZeroAbi: W6FilterDropShadowColorizeGroupZeroAbiV1 = W6FilterDropShadowColorizeGroupZeroAbiV1.BlurredAlphaTexture,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1, public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val o = org.graphiks.math.vector.Vector2F64(offsetF64.x, offsetF64.y); private val so = org.graphiks.math.vector.Vector2F64(sourceCoordinateOffsetTargetLocalF64.x, sourceCoordinateOffsetTargetLocalF64.y)
    private val sf = sourceFootprintTargetLocalI32.copy(); private val of = outputFootprintTargetLocalI32.copy(); private val sc = scissorTargetLocalI32.copy(); private val e = extent.copy(); private val be = blurredExtent.copy()
    init { require(target != blurredSource && o.x.isFinite() && o.y.isFinite() && so.x.isFinite() && so.y.isFinite() && !sf.isEmpty && !of.isEmpty && !sc.isEmpty && sf.left == 0 && sf.top == 0 && of.left == 0 && of.top == 0 && sc.left >= 0 && sc.top >= 0 && sc.right <= e.width && sc.bottom <= e.height && sampleCountI32 == 1 && blurredSampleCountI32 == 1 && e.width == of.width() && e.height == of.height() && be.width == sf.width() && be.height == sf.height()) }
    public fun copyOffsetF64() = org.graphiks.math.vector.Vector2F64(o.x, o.y); public fun copySourceCoordinateOffsetTargetLocalF64() = org.graphiks.math.vector.Vector2F64(so.x, so.y)
    public fun copySourceFootprintTargetLocalI32() = sf.copy(); public fun copyOutputFootprintTargetLocalI32() = of.copy(); public fun copyScissorTargetLocalI32() = sc.copy(); public fun copyExtent() = e.copy(); public fun copyBlurredExtent() = be.copy()
    public fun canonicalLogicalEncodingV1() = W6FilterDropShadowColorizeNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}
public class W6FilterDropShadowColorizeNativeSiteRecipeV1 internal constructor(public val host: W6FilterDropShadowColorizeRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = NativeSiteOwnerV1(host.ownerPassId, 0, 0); override val family = NativeSiteRecipeFamilyV1.W6FilterDropShadowColorize
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); text("target", host.target.value); text("blurredSource", host.blurredSource.value); text("colorArgb", host.colorArgbU32.toString(16)); double("offset.x", host.copyOffsetF64().x); double("offset.y", host.copyOffsetF64().y); double("sourceCoordinateOffset.x", host.copySourceCoordinateOffsetTargetLocalF64().x); double("sourceCoordinateOffset.y", host.copySourceCoordinateOffsetTargetLocalF64().y); rect("sourceFootprint", host.copySourceFootprintTargetLocalI32()); rect("outputFootprint", host.copyOutputFootprintTargetLocalI32()); rect("scissor", host.copyScissorTargetLocalI32()); int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height); int("blurredExtentWidth", host.copyBlurredExtent().width); int("blurredExtentHeight", host.copyBlurredExtent().height); enum("targetFormat", host.targetFormat); enum("blurredFormat", host.blurredFormat); int("sampleCount", host.sampleCountI32); int("blurredSampleCount", host.blurredSampleCountI32); enum("shaderFamily", host.shaderFamily); enum("groupZeroAbi", host.groupZeroAbi); enum("load", host.load); enum("store", host.store); blend("blend", host.blend); int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

/** IIg2's ordered colorized-shadow/original pair; its SrcOver is internal to the frozen shader. */
public enum class W6FilterDropShadowCompositeShaderFamilyV1 { ShadowThenOriginalSrcOverTextureLoad }
public enum class W6FilterDropShadowCompositeGroupZeroAbiV1 { ColorizedShadowThenOriginalTextures }
public class W6FilterDropShadowCompositeRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val target: PlanResourceId,
    public val colorizedShadow: PlanResourceId, public val originalSource: PlanResourceId,
    public val mode: CapturedDropShadowModeV1,
    shadowOffsetTargetLocalI32: org.graphiks.math.geometry.Point2I32,
    originalOffsetTargetLocalI32: org.graphiks.math.geometry.Point2I32,
    shadowFootprintTargetLocalI32: org.graphiks.math.geometry.RectI32,
    originalFootprintTargetLocalI32: org.graphiks.math.geometry.RectI32,
    scissorTargetLocalI32: org.graphiks.math.geometry.RectI32,
    extent: org.graphiks.math.geometry.SizeI32, shadowExtent: org.graphiks.math.geometry.SizeI32,
    originalExtent: org.graphiks.math.geometry.SizeI32,
    public val targetFormat: PlanLogicalColorFormat, public val shadowFormat: PlanLogicalColorFormat,
    public val originalFormat: PlanLogicalColorFormat, public val sampleCountI32: Int,
    public val shadowSampleCountI32: Int, public val originalSampleCountI32: Int,
    public val shaderFamily: W6FilterDropShadowCompositeShaderFamilyV1 = W6FilterDropShadowCompositeShaderFamilyV1.ShadowThenOriginalSrcOverTextureLoad,
    public val groupZeroAbi: W6FilterDropShadowCompositeGroupZeroAbiV1 = W6FilterDropShadowCompositeGroupZeroAbiV1.ColorizedShadowThenOriginalTextures,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1, public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val so = org.graphiks.math.geometry.Point2I32(shadowOffsetTargetLocalI32.x, shadowOffsetTargetLocalI32.y); private val oo = org.graphiks.math.geometry.Point2I32(originalOffsetTargetLocalI32.x, originalOffsetTargetLocalI32.y)
    private val sf = shadowFootprintTargetLocalI32.copy(); private val of = originalFootprintTargetLocalI32.copy(); private val sc = scissorTargetLocalI32.copy(); private val e = extent.copy(); private val se = shadowExtent.copy(); private val oe = originalExtent.copy()
    init { require(target != colorizedShadow && target != originalSource && colorizedShadow != originalSource && mode == CapturedDropShadowModeV1.COMPOSITE && !sf.isEmpty && !of.isEmpty && !sc.isEmpty && sf.left == 0 && sf.top == 0 && of.left == 0 && of.top == 0 && sc.left >= 0 && sc.top >= 0 && sc.right <= e.width && sc.bottom <= e.height && e.width == sc.width() && e.height == sc.height() && se.width == sf.width() && se.height == sf.height() && oe.width == of.width() && oe.height == of.height() && sampleCountI32 == 1 && shadowSampleCountI32 == 1 && originalSampleCountI32 == 1) }
    public fun copyShadowOffsetTargetLocalI32() = org.graphiks.math.geometry.Point2I32(so.x, so.y); public fun copyOriginalOffsetTargetLocalI32() = org.graphiks.math.geometry.Point2I32(oo.x, oo.y)
    public fun copyShadowFootprintTargetLocalI32() = sf.copy(); public fun copyOriginalFootprintTargetLocalI32() = of.copy(); public fun copyScissorTargetLocalI32() = sc.copy(); public fun copyExtent() = e.copy(); public fun copyShadowExtent() = se.copy(); public fun copyOriginalExtent() = oe.copy()
    public fun canonicalLogicalEncodingV1() = W6FilterDropShadowCompositeNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}
public class W6FilterDropShadowCompositeNativeSiteRecipeV1 internal constructor(public val host: W6FilterDropShadowCompositeRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = NativeSiteOwnerV1(host.ownerPassId, 0, 0); override val family = NativeSiteRecipeFamilyV1.W6FilterDropShadowComposite
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); text("target", host.target.value); text("input.0", host.colorizedShadow.value); text("input.1", host.originalSource.value); int("inputCount", 2); enum("mode", host.mode); point("shadowOffset", host.copyShadowOffsetTargetLocalI32()); point("originalOffset", host.copyOriginalOffsetTargetLocalI32()); rect("shadowFootprint", host.copyShadowFootprintTargetLocalI32()); rect("originalFootprint", host.copyOriginalFootprintTargetLocalI32()); rect("scissor", host.copyScissorTargetLocalI32()); int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height); int("shadowExtentWidth", host.copyShadowExtent().width); int("shadowExtentHeight", host.copyShadowExtent().height); int("originalExtentWidth", host.copyOriginalExtent().width); int("originalExtentHeight", host.copyOriginalExtent().height); enum("targetFormat", host.targetFormat); enum("shadowFormat", host.shadowFormat); enum("originalFormat", host.originalFormat); int("sampleCount", host.sampleCountI32); int("shadowSampleCount", host.shadowSampleCountI32); int("originalSampleCount", host.originalSampleCountI32); enum("shaderFamily", host.shaderFamily); enum("groupZeroAbi", host.groupZeroAbi); enum("load", host.load); enum("store", host.store); blend("blend", host.blend); int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

/** IIe2a's one-texture mask-style site.  The two-texture styles stay outside this slice. */
public enum class W6FilterMaskBlurNormalShaderFamilyV1 { BlurredCoverageTextureLoad }
public enum class W6FilterMaskBlurNormalGroupZeroAbiV1 { BlurredCoverageTexture }
public class W6FilterMaskBlurNormalRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val target: PlanResourceId, public val blurredSource: PlanResourceId,
    public val style: org.graphiks.kanvas.render.ir.MaskBlurStyle = org.graphiks.kanvas.render.ir.MaskBlurStyle.NORMAL,
    extent: org.graphiks.math.geometry.SizeI32, blurredExtent: org.graphiks.math.geometry.SizeI32,
    known: org.graphiks.math.geometry.RectI32, offset: org.graphiks.math.geometry.Point2I32,
    public val targetFormat: PlanLogicalColorFormat, public val blurredFormat: PlanLogicalColorFormat,
    public val sampleCountI32: Int, public val blurredSampleCountI32: Int,
    public val shaderFamily: W6FilterMaskBlurNormalShaderFamilyV1 = W6FilterMaskBlurNormalShaderFamilyV1.BlurredCoverageTextureLoad,
    public val groupZeroAbi: W6FilterMaskBlurNormalGroupZeroAbiV1 = W6FilterMaskBlurNormalGroupZeroAbiV1.BlurredCoverageTexture,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1, public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val frozenExtent = extent.copy(); private val frozenBlurredExtent = blurredExtent.copy(); private val frozenKnown = known.copy()
    private val frozenOffset = org.graphiks.math.geometry.Point2I32(offset.x, offset.y)
    init { require(style == org.graphiks.kanvas.render.ir.MaskBlurStyle.NORMAL && sampleCountI32 == 1 && blurredSampleCountI32 == 1 && !frozenKnown.isEmpty) }
    public fun copyExtent() = frozenExtent.copy(); public fun copyBlurredExtent() = frozenBlurredExtent.copy()
    public fun copyKnownContentTargetLocalI32() = frozenKnown.copy()
    public fun copyOutputToBlurredOffsetTargetLocalI32() = org.graphiks.math.geometry.Point2I32(frozenOffset.x, frozenOffset.y)
    public fun canonicalLogicalEncodingV1() = W6FilterMaskBlurNormalNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}
public class W6FilterMaskBlurNormalNativeSiteRecipeV1 internal constructor(public val host: W6FilterMaskBlurNormalRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = NativeSiteOwnerV1(host.ownerPassId, 0, 0); override val family = NativeSiteRecipeFamilyV1.W6FilterMaskBlurNormal
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); text("target", host.target.value); text("blurredSource", host.blurredSource.value); enum("style", host.style)
        int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height)
        int("blurredExtentWidth", host.copyBlurredExtent().width); int("blurredExtentHeight", host.copyBlurredExtent().height)
        rect("known", host.copyKnownContentTargetLocalI32()); point("offset", host.copyOutputToBlurredOffsetTargetLocalI32())
        enum("targetFormat", host.targetFormat); enum("blurredFormat", host.blurredFormat); int("sampleCount", host.sampleCountI32); int("blurredSampleCount", host.blurredSampleCountI32)
        enum("shaderFamily", host.shaderFamily); enum("groupZeroAbi", host.groupZeroAbi); enum("load", host.load); enum("store", host.store); blend("blend", host.blend)
        int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

/** IIe2b's ordered two-texture mask-style site for SOLID, OUTER and INNER. */
public enum class W6FilterMaskBlurDualSourceShaderFamilyV1 { BlurredThenOriginalCoverageTextureLoad }
public enum class W6FilterMaskBlurDualSourceGroupZeroAbiV1 { BlurredThenOriginalCoverageTextures }
public class W6FilterMaskBlurDualSourceRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val target: PlanResourceId, public val blurredSource: PlanResourceId, public val originalSource: PlanResourceId,
    public val style: org.graphiks.kanvas.render.ir.MaskBlurStyle,
    extent: org.graphiks.math.geometry.SizeI32, blurredExtent: org.graphiks.math.geometry.SizeI32, originalExtent: org.graphiks.math.geometry.SizeI32,
    blurredKnown: org.graphiks.math.geometry.RectI32, originalKnown: org.graphiks.math.geometry.RectI32,
    blurredOffset: org.graphiks.math.geometry.Point2I32, originalOffset: org.graphiks.math.geometry.Point2I32,
    public val targetFormat: PlanLogicalColorFormat, public val blurredFormat: PlanLogicalColorFormat, public val originalFormat: PlanLogicalColorFormat,
    public val sampleCountI32: Int, public val blurredSampleCountI32: Int, public val originalSampleCountI32: Int,
    public val shaderFamily: W6FilterMaskBlurDualSourceShaderFamilyV1 = W6FilterMaskBlurDualSourceShaderFamilyV1.BlurredThenOriginalCoverageTextureLoad,
    public val groupZeroAbi: W6FilterMaskBlurDualSourceGroupZeroAbiV1 = W6FilterMaskBlurDualSourceGroupZeroAbiV1.BlurredThenOriginalCoverageTextures,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1, public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val frozenExtent = extent.copy(); private val frozenBlurredExtent = blurredExtent.copy(); private val frozenOriginalExtent = originalExtent.copy()
    private val frozenBlurredKnown = blurredKnown.copy(); private val frozenOriginalKnown = originalKnown.copy()
    private val frozenBlurredOffset = org.graphiks.math.geometry.Point2I32(blurredOffset.x, blurredOffset.y); private val frozenOriginalOffset = org.graphiks.math.geometry.Point2I32(originalOffset.x, originalOffset.y)
    init { require(style in setOf(org.graphiks.kanvas.render.ir.MaskBlurStyle.SOLID, org.graphiks.kanvas.render.ir.MaskBlurStyle.OUTER, org.graphiks.kanvas.render.ir.MaskBlurStyle.INNER) && sampleCountI32 == 1 && blurredSampleCountI32 == 1 && originalSampleCountI32 == 1 && !frozenBlurredKnown.isEmpty && !frozenOriginalKnown.isEmpty) }
    public fun copyExtent() = frozenExtent.copy(); public fun copyBlurredExtent() = frozenBlurredExtent.copy(); public fun copyOriginalExtent() = frozenOriginalExtent.copy()
    public fun copyBlurredKnownContentTargetLocalI32() = frozenBlurredKnown.copy(); public fun copyOriginalKnownContentTargetLocalI32() = frozenOriginalKnown.copy()
    public fun copyOutputToBlurredOffsetTargetLocalI32() = org.graphiks.math.geometry.Point2I32(frozenBlurredOffset.x, frozenBlurredOffset.y); public fun copyOutputToOriginalOffsetTargetLocalI32() = org.graphiks.math.geometry.Point2I32(frozenOriginalOffset.x, frozenOriginalOffset.y)
    public fun canonicalLogicalEncodingV1() = W6FilterMaskBlurDualSourceNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}
public class W6FilterMaskBlurDualSourceNativeSiteRecipeV1 internal constructor(public val host: W6FilterMaskBlurDualSourceRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = NativeSiteOwnerV1(host.ownerPassId, 0, 0); override val family = NativeSiteRecipeFamilyV1.W6FilterMaskBlurDualSource
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); text("target", host.target.value); text("blurredSource", host.blurredSource.value); text("originalSource", host.originalSource.value); enum("style", host.style)
        int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height); int("blurredExtentWidth", host.copyBlurredExtent().width); int("blurredExtentHeight", host.copyBlurredExtent().height); int("originalExtentWidth", host.copyOriginalExtent().width); int("originalExtentHeight", host.copyOriginalExtent().height)
        rect("blurredKnown", host.copyBlurredKnownContentTargetLocalI32()); rect("originalKnown", host.copyOriginalKnownContentTargetLocalI32()); point("blurredOffset", host.copyOutputToBlurredOffsetTargetLocalI32()); point("originalOffset", host.copyOutputToOriginalOffsetTargetLocalI32())
        enum("targetFormat", host.targetFormat); enum("blurredFormat", host.blurredFormat); enum("originalFormat", host.originalFormat); int("sampleCount", host.sampleCountI32); int("blurredSampleCount", host.blurredSampleCountI32); int("originalSampleCount", host.originalSampleCountI32); enum("shaderFamily", host.shaderFamily); enum("groupZeroAbi", host.groupZeroAbi); enum("load", host.load); enum("store", host.store); blend("blend", host.blend)
        int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

/** IIe1's backend-neutral fullscreen blur selection; X/Y and image/mask phases remain distinct. */
public enum class W6FilterSeparableBlurShaderFamilyV1 { GaussianTextureLoad }
public enum class W6FilterSeparableBlurGroupZeroAbiV1 { SourceTexture }
public class W6FilterSeparableBlurRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val target: PlanResourceId, public val source: PlanResourceId,
    public val kind: FilterImplementationKindV1, public val axis: FilterAxisV1, public val sigmaF32: Float,
    public val tileMode: TileMode, extent: org.graphiks.math.geometry.SizeI32, sourceExtent: org.graphiks.math.geometry.SizeI32,
    known: org.graphiks.math.geometry.RectI32, offset: org.graphiks.math.geometry.Point2I32,
    public val targetFormat: PlanLogicalColorFormat, public val sourceFormat: PlanLogicalColorFormat,
    public val sampleCountI32: Int, public val sourceSampleCountI32: Int,
    public val shaderFamily: W6FilterSeparableBlurShaderFamilyV1 = W6FilterSeparableBlurShaderFamilyV1.GaussianTextureLoad,
    public val groupZeroAbi: W6FilterSeparableBlurGroupZeroAbiV1 = W6FilterSeparableBlurGroupZeroAbiV1.SourceTexture,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1, public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val frozenExtent = extent.copy(); private val frozenSourceExtent = sourceExtent.copy(); private val frozenKnown = known.copy(); private val frozenOffset = org.graphiks.math.geometry.Point2I32(offset.x, offset.y)
    init { require(sigmaF32.isFinite() && sigmaF32 >= 0f && sampleCountI32 == 1 && sourceSampleCountI32 == 1 && !frozenKnown.isEmpty && kind in setOf(FilterImplementationKindV1.IMAGE_BLUR_X, FilterImplementationKindV1.IMAGE_BLUR_Y, FilterImplementationKindV1.MASK_COVERAGE_BLUR_X, FilterImplementationKindV1.MASK_COVERAGE_BLUR_Y) && (kind.name.endsWith("_X") == (axis == FilterAxisV1.X))) }
    public fun copyExtent() = frozenExtent.copy(); public fun copySourceExtent() = frozenSourceExtent.copy(); public fun copyKnownContentTargetLocalI32() = frozenKnown.copy(); public fun copyOutputToInputOffsetTargetLocalI32() = org.graphiks.math.geometry.Point2I32(frozenOffset.x, frozenOffset.y)
    public fun canonicalLogicalEncodingV1() = W6FilterSeparableBlurNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}
public class W6FilterSeparableBlurNativeSiteRecipeV1 internal constructor(public val host: W6FilterSeparableBlurRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = NativeSiteOwnerV1(host.ownerPassId, 0, 0); override val family = NativeSiteRecipeFamilyV1.W6FilterSeparableBlur
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) { text("owner", host.ownerPassId.value); text("target", host.target.value); text("source", host.source.value); enum("kind", host.kind); enum("axis", host.axis); float("sigma", host.sigmaF32); enum("tileMode", host.tileMode); int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height); int("sourceExtentWidth", host.copySourceExtent().width); int("sourceExtentHeight", host.copySourceExtent().height); rect("known", host.copyKnownContentTargetLocalI32()); point("offset", host.copyOutputToInputOffsetTargetLocalI32()); enum("targetFormat", host.targetFormat); enum("sourceFormat", host.sourceFormat); int("sampleCount", host.sampleCountI32); int("sourceSampleCount", host.sourceSampleCountI32); enum("shaderFamily", host.shaderFamily); enum("groupZeroAbi", host.groupZeroAbi); enum("load", host.load); enum("store", host.store); blend("blend", host.blend); int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32) }
}

/** IId2 keeps background and foreground in public order, even if their resource ids match. */
public class W6FilterBlendInputRecipeV1 internal constructor(public val source: PlanResourceId, extent: org.graphiks.math.geometry.SizeI32, known: org.graphiks.math.geometry.RectI32, offset: org.graphiks.math.geometry.Point2I32, public val format: PlanLogicalColorFormat, public val sampleCountI32: Int) {
    private val frozenExtent = extent.copy(); private val frozenKnown = known.copy(); private val frozenOffset = org.graphiks.math.geometry.Point2I32(offset.x, offset.y)
    init { require(sampleCountI32 == 1 && !frozenKnown.isEmpty) }
    public fun copyExtent() = frozenExtent.copy(); public fun copyKnownContentTargetLocalI32() = frozenKnown.copy(); public fun copyOutputToInputOffsetTargetLocalI32() = org.graphiks.math.geometry.Point2I32(frozenOffset.x, frozenOffset.y)
}
public enum class W6FilterBlendGroupZeroAbiV1 { BackgroundAndForegroundTextures }
public enum class W6FilterBlendShaderFamilyV1 { FrozenW5BlendFormulaTextureLoad }
/** Backend-neutral identity of the W5 formula renderer asks the planner to materialize. */
public enum class W6FilterBlendFormulaV1 { W5BlendFormulaV1 }
public class W6FilterBlendRecipeV1 internal constructor(public val ownerPassId: PlanPassId, public val target: PlanResourceId, background: W6FilterBlendInputRecipeV1, foreground: W6FilterBlendInputRecipeV1, extent: org.graphiks.math.geometry.SizeI32, public val blend: BlendPlan, public val formula: W6FilterBlendFormulaV1, public val blendFormulaWgsl: String, public val targetFormat: PlanLogicalColorFormat, public val sampleCountI32: Int, public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent, public val store: AttachmentStorePlan = AttachmentStorePlan.Store, public val groupZeroAbi: W6FilterBlendGroupZeroAbiV1 = W6FilterBlendGroupZeroAbiV1.BackgroundAndForegroundTextures, public val shaderFamily: W6FilterBlendShaderFamilyV1 = W6FilterBlendShaderFamilyV1.FrozenW5BlendFormulaTextureLoad, public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1()) {
    private val frozenBackground = background; private val frozenForeground = foreground; private val frozenExtent = extent.copy()
    init { require(sampleCountI32 == 1 && blendFormulaWgsl == frozenW6FilterBlendFormulaWgslV1(blend, formula)) }
    public fun background() = frozenBackground; public fun foreground() = frozenForeground; public fun copyExtent() = frozenExtent.copy(); public fun nativeSiteOwnerV1() = NativeSiteOwnerV1(ownerPassId, 0, 0); public fun canonicalLogicalEncodingV1() = W6FilterBlendNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}
public class W6FilterBlendNativeSiteRecipeV1 internal constructor(public val host: W6FilterBlendRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = host.nativeSiteOwnerV1(); override val family = NativeSiteRecipeFamilyV1.W6FilterBlend
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); int("bundle", owner.bundleOrdinalI32); text("target", host.target.value); int("inputCount", 2)
        listOf(host.background(), host.foreground()).forEachIndexed { index, input -> text("input.$index", input.source.value); int("input.$index.extentWidth", input.copyExtent().width); int("input.$index.extentHeight", input.copyExtent().height); rect("input.$index.knownContent", input.copyKnownContentTargetLocalI32()); point("input.$index.offset", input.copyOutputToInputOffsetTargetLocalI32()); enum("input.$index.format", input.format); int("input.$index.sampleCount", input.sampleCountI32) }
        int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height); blend("blend", host.blend); enum("formula", host.formula); enum("load", host.load); enum("store", host.store); enum("targetFormat", host.targetFormat); int("sampleCount", host.sampleCountI32); enum("groupZeroAbi", host.groupZeroAbi); enum("shaderFamily", host.shaderFamily); int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

/** Ordered merge input: position is semantic, including repeated source resources. */
public class W6FilterMergeInputRecipeV1 internal constructor(
    public val source: PlanResourceId,
    extent: org.graphiks.math.geometry.SizeI32,
    knownContentTargetLocalI32: org.graphiks.math.geometry.RectI32,
    outputToInputOffsetTargetLocalI32: org.graphiks.math.geometry.Point2I32,
    public val format: PlanLogicalColorFormat,
    public val sampleCountI32: Int,
) {
    private val frozenExtent = extent.copy(); private val frozenKnown = knownContentTargetLocalI32.copy()
    private val frozenOffset = org.graphiks.math.geometry.Point2I32(outputToInputOffsetTargetLocalI32.x, outputToInputOffsetTargetLocalI32.y)
    init { require(sampleCountI32 == 1 && !frozenKnown.isEmpty) }
    public fun copyExtent() = frozenExtent.copy()
    public fun copyKnownContentTargetLocalI32() = frozenKnown.copy()
    public fun copyOutputToInputOffsetTargetLocalI32() = org.graphiks.math.geometry.Point2I32(frozenOffset.x, frozenOffset.y)
}

/** IId1's native authority for ordered source-over Merge. */
public enum class W6FilterMergeGroupZeroAbiV1 { OrderedTextures }
public enum class W6FilterMergeShaderFamilyV1 { OrderedSourceOverTextureLoad }
public class W6FilterMergeRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val target: PlanResourceId, inputs: List<W6FilterMergeInputRecipeV1>,
    extent: org.graphiks.math.geometry.SizeI32, public val targetFormat: PlanLogicalColorFormat, public val sampleCountI32: Int,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1,
    public val groupZeroAbi: W6FilterMergeGroupZeroAbiV1 = W6FilterMergeGroupZeroAbiV1.OrderedTextures,
    public val shaderFamily: W6FilterMergeShaderFamilyV1 = W6FilterMergeShaderFamilyV1.OrderedSourceOverTextureLoad,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val frozenInputs = immutableList(inputs); private val frozenExtent = extent.copy()
    init { require(frozenInputs.isNotEmpty() && sampleCountI32 == 1) }
    public fun inputs(): List<W6FilterMergeInputRecipeV1> = frozenInputs
    public fun copyExtent() = frozenExtent.copy()
    public fun nativeSiteOwnerV1() = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1() = W6FilterMergeNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}
public class W6FilterMergeNativeSiteRecipeV1 internal constructor(public val host: W6FilterMergeRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = host.nativeSiteOwnerV1(); override val family = NativeSiteRecipeFamilyV1.W6FilterMerge
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); int("bundle", owner.bundleOrdinalI32); text("target", host.target.value); int("inputCount", host.inputs().size)
        host.inputs().forEachIndexed { index, input -> text("input.$index", input.source.value); int("input.$index.extentWidth", input.copyExtent().width); int("input.$index.extentHeight", input.copyExtent().height); rect("input.$index.knownContent", input.copyKnownContentTargetLocalI32()); point("input.$index.offset", input.copyOutputToInputOffsetTargetLocalI32()); enum("input.$index.format", input.format); int("input.$index.sampleCount", input.sampleCountI32) }
        int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height); enum("load", host.load); enum("store", host.store); enum("targetFormat", host.targetFormat); int("sampleCount", host.sampleCountI32); blend("blend", host.blend); enum("groupZeroAbi", host.groupZeroAbi); enum("shaderFamily", host.shaderFamily); int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

/** IIc's sole native ABI: one source texture and the W5f-issued uniform row. */
public enum class W6FilterColorFilterGroupZeroAbiV1 { TextureAndW5fUniform }
public enum class W6FilterColorFilterShaderFamilyV1 { W5fColorOperationTextureLoad }
public class W6FilterColorFilterRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val target: PlanResourceId, public val source: PlanResourceId,
    extent: org.graphiks.math.geometry.SizeI32, sourceExtent: org.graphiks.math.geometry.SizeI32,
    public val execution: ColorFilterExecutionPlanV1, public val uniformResource: PlanResourceId,
    public val uniformOffsetBytesI64: Long, public val uniformCapacityBytesI64: Long,
    outputToInputOffsetTargetLocalI32: org.graphiks.math.geometry.Point2I32,
    public val targetFormat: PlanLogicalColorFormat, public val sampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat, public val sourceSampleCountI32: Int,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1,
    public val groupZeroAbi: W6FilterColorFilterGroupZeroAbiV1 = W6FilterColorFilterGroupZeroAbiV1.TextureAndW5fUniform,
    public val shaderFamily: W6FilterColorFilterShaderFamilyV1 = W6FilterColorFilterShaderFamilyV1.W5fColorOperationTextureLoad,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val frozenExtent = extent.copy(); private val frozenSourceExtent = sourceExtent.copy()
    private val frozenOffset = org.graphiks.math.geometry.Point2I32(outputToInputOffsetTargetLocalI32.x, outputToInputOffsetTargetLocalI32.y)
    init { require(target != source && sampleCountI32 == 1 && sourceSampleCountI32 == 1 && uniformResource.value.startsWith("${PlanResourceRole.SourceUniformData.name}:") && uniformOffsetBytesI64 >= 0L && uniformCapacityBytesI64 >= 16L && Math.addExact(uniformOffsetBytesI64, maxOf(16L, execution.dynamicByteCountI64)) <= uniformCapacityBytesI64) }
    public fun copyExtent() = frozenExtent.copy(); public fun copySourceExtent() = frozenSourceExtent.copy()
    public fun copyOutputToInputOffsetTargetLocalI32() = org.graphiks.math.geometry.Point2I32(frozenOffset.x, frozenOffset.y)
    public fun nativeSiteOwnerV1(): NativeSiteOwnerV1 = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1(): String = W6FilterColorFilterNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}
public class W6FilterColorFilterNativeSiteRecipeV1 internal constructor(public val host: W6FilterColorFilterRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = host.nativeSiteOwnerV1(); override val family = NativeSiteRecipeFamilyV1.W6FilterColorFilter
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); int("bundle", owner.bundleOrdinalI32); text("target", host.target.value); text("input.0", host.source.value); int("inputCount", 1)
        int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height); int("sourceExtentWidth", host.copySourceExtent().width); int("sourceExtentHeight", host.copySourceExtent().height); point("outputToInputOffsetTargetLocal", host.copyOutputToInputOffsetTargetLocalI32())
        text("execution.structural", host.execution.structuralIdentity); text("execution.canonical", host.execution.canonicalIdentity); long("execution.dynamicBytes", host.execution.dynamicByteCountI64); text("uniform", host.uniformResource.value); long("uniformOffset", host.uniformOffsetBytesI64); long("uniformCapacity", host.uniformCapacityBytesI64)
        enum("load", host.load); enum("store", host.store); enum("targetFormat", host.targetFormat); int("sampleCount", host.sampleCountI32); enum("sourceFormat", host.sourceFormat); int("sourceSampleCount", host.sourceSampleCountI32); blend("blend", host.blend); enum("groupZeroAbi", host.groupZeroAbi); enum("shaderFamily", host.shaderFamily); int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

/** Bounded IIb selection. Morphology is a separable target-local texture-load pass. */
public enum class W6FilterMorphologyGroupZeroAbiV1 { Texture }
public enum class W6FilterMorphologyShaderFamilyV1 { TargetLocalSeparableTextureLoad }
public class W6FilterMorphologyRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val target: PlanResourceId, public val source: PlanResourceId,
    extent: org.graphiks.math.geometry.SizeI32, sourceExtent: org.graphiks.math.geometry.SizeI32,
    public val morphologyKind: FilterPassOperationV1.Morphology.Kind,
    public val axis: FilterAxisV1,
    public val radiusXF64: Double, public val radiusYF64: Double,
    public val radiusXTexelsI32: Int, public val radiusYTexelsI32: Int,
    sourceKnownContentTargetLocalI32: org.graphiks.math.geometry.RectI32,
    outputToInputOffsetTargetLocalI32: org.graphiks.math.geometry.Point2I32,
    public val targetFormat: PlanLogicalColorFormat, public val sampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat, public val sourceSampleCountI32: Int,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1,
    public val groupZeroAbi: W6FilterMorphologyGroupZeroAbiV1 = W6FilterMorphologyGroupZeroAbiV1.Texture,
    public val shaderFamily: W6FilterMorphologyShaderFamilyV1 = W6FilterMorphologyShaderFamilyV1.TargetLocalSeparableTextureLoad,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val frozenExtent = extent.copy(); private val frozenSourceExtent = sourceExtent.copy()
    private val frozenKnownContent = sourceKnownContentTargetLocalI32.copy()
    private val frozenOffset = org.graphiks.math.geometry.Point2I32(outputToInputOffsetTargetLocalI32.x, outputToInputOffsetTargetLocalI32.y)
    init {
        require(target != source && sampleCountI32 == 1 && sourceSampleCountI32 == 1 && !frozenKnownContent.isEmpty)
        require(radiusXF64.isFinite() && radiusYF64.isFinite() && radiusXF64 >= 0.0 && radiusYF64 >= 0.0)
        require(radiusXTexelsI32 >= 0 && radiusYTexelsI32 >= 0)
    }
    public fun copyExtent() = frozenExtent.copy(); public fun copySourceExtent() = frozenSourceExtent.copy()
    public fun copySourceKnownContentTargetLocalI32() = frozenKnownContent.copy()
    public fun copyOutputToInputOffsetTargetLocalI32() = org.graphiks.math.geometry.Point2I32(frozenOffset.x, frozenOffset.y)
    public fun nativeSiteOwnerV1(): NativeSiteOwnerV1 = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1(): String = W6FilterMorphologyNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}
public class W6FilterMorphologyNativeSiteRecipeV1 internal constructor(public val host: W6FilterMorphologyRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = host.nativeSiteOwnerV1(); override val family = NativeSiteRecipeFamilyV1.W6FilterMorphology
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); int("bundle", owner.bundleOrdinalI32); text("target", host.target.value); text("input.0", host.source.value); int("inputCount", 1)
        int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height); int("sourceExtentWidth", host.copySourceExtent().width); int("sourceExtentHeight", host.copySourceExtent().height)
        enum("morphologyKind", host.morphologyKind); enum("axis", host.axis); double("radiusXF64", host.radiusXF64); double("radiusYF64", host.radiusYF64); int("radiusXTexels", host.radiusXTexelsI32); int("radiusYTexels", host.radiusYTexelsI32)
        rect("sourceKnownContent", host.copySourceKnownContentTargetLocalI32()); point("outputToInputOffsetTargetLocal", host.copyOutputToInputOffsetTargetLocalI32())
        enum("load", host.load); enum("store", host.store); enum("targetFormat", host.targetFormat); int("sampleCount", host.sampleCountI32); enum("sourceFormat", host.sourceFormat); int("sourceSampleCount", host.sourceSampleCountI32); blend("blend", host.blend); enum("groupZeroAbi", host.groupZeroAbi); enum("shaderFamily", host.shaderFamily)
        int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

/** Bounded IIa2b selection. Tile is the periodic REPEAT operation, never a generic sampler. */
public enum class W6FilterSpatialTileGroupZeroAbiV1 { Texture }
public enum class W6FilterSpatialTileShaderFamilyV1 { TargetLocalTileRepeatTextureLoad }
public class W6FilterSpatialTileRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId, public val target: PlanResourceId, public val source: PlanResourceId,
    extent: org.graphiks.math.geometry.SizeI32, sourceExtent: org.graphiks.math.geometry.SizeI32,
    sourceDomainTargetLocalI32: org.graphiks.math.geometry.RectI32, clipTargetLocalF64: org.graphiks.math.geometry.RectF64,
    outputToInputOffsetTargetLocalF64: org.graphiks.math.vector.Vector2F64,
    public val targetFormat: PlanLogicalColorFormat, public val sampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat, public val sourceSampleCountI32: Int,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1,
    public val groupZeroAbi: W6FilterSpatialTileGroupZeroAbiV1 = W6FilterSpatialTileGroupZeroAbiV1.Texture,
    public val shaderFamily: W6FilterSpatialTileShaderFamilyV1 = W6FilterSpatialTileShaderFamilyV1.TargetLocalTileRepeatTextureLoad,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val frozenExtent = extent.copy(); private val frozenSourceExtent = sourceExtent.copy(); private val frozenDomain = sourceDomainTargetLocalI32.copy(); private val frozenClip = clipTargetLocalF64.copy()
    private val frozenOffset = org.graphiks.math.vector.Vector2F64(outputToInputOffsetTargetLocalF64.x, outputToInputOffsetTargetLocalF64.y)
    init { require(target != source && sampleCountI32 == 1 && sourceSampleCountI32 == 1 && !frozenDomain.isEmpty && !frozenClip.isEmpty) }
    public fun copyExtent() = frozenExtent.copy(); public fun copySourceExtent() = frozenSourceExtent.copy(); public fun copySourceDomainTargetLocalI32() = frozenDomain.copy(); public fun copyClipTargetLocalF64() = frozenClip.copy()
    public fun copyOutputToInputOffsetTargetLocalF64() = org.graphiks.math.vector.Vector2F64(frozenOffset.x, frozenOffset.y)
    public fun nativeSiteOwnerV1(): NativeSiteOwnerV1 = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1(): String = W6FilterSpatialTileNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}
public class W6FilterSpatialTileNativeSiteRecipeV1 internal constructor(public val host: W6FilterSpatialTileRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = host.nativeSiteOwnerV1(); override val family = NativeSiteRecipeFamilyV1.W6FilterSpatialTile
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); int("bundle", owner.bundleOrdinalI32); text("target", host.target.value); text("input.0", host.source.value); int("inputCount", 1)
        int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height); int("sourceExtentWidth", host.copySourceExtent().width); int("sourceExtentHeight", host.copySourceExtent().height)
        rect("sourceDomain", host.copySourceDomainTargetLocalI32()); rectF64("clip", host.copyClipTargetLocalF64()); double("offsetX", host.copyOutputToInputOffsetTargetLocalF64().x); double("offsetY", host.copyOutputToInputOffsetTargetLocalF64().y)
        text("tileMode", "REPEAT"); enum("load", host.load); enum("store", host.store); enum("targetFormat", host.targetFormat); int("sampleCount", host.sampleCountI32); enum("sourceFormat", host.sourceFormat); int("sourceSampleCount", host.sourceSampleCountI32); blend("blend", host.blend); enum("groupZeroAbi", host.groupZeroAbi); enum("shaderFamily", host.shaderFamily)
        int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

/** Bounded IIa2a selection. Offset is always target-local DECAL sampling. */
public enum class W6FilterSpatialOffsetGroupZeroAbiV1 { Texture }
public enum class W6FilterSpatialOffsetShaderFamilyV1 { TargetLocalOffsetDecalTextureLoad }

public class W6FilterSpatialOffsetRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId,
    public val target: PlanResourceId,
    public val source: PlanResourceId,
    extent: org.graphiks.math.geometry.SizeI32,
    sourceExtent: org.graphiks.math.geometry.SizeI32,
    sourceDomainTargetLocalI32: org.graphiks.math.geometry.RectI32,
    clipTargetLocalF64: org.graphiks.math.geometry.RectF64,
    outputToInputOffsetTargetLocalF64: org.graphiks.math.vector.Vector2F64,
    public val targetFormat: PlanLogicalColorFormat,
    public val sampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat,
    public val sourceSampleCountI32: Int,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent,
    public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1,
    public val groupZeroAbi: W6FilterSpatialOffsetGroupZeroAbiV1 = W6FilterSpatialOffsetGroupZeroAbiV1.Texture,
    public val shaderFamily: W6FilterSpatialOffsetShaderFamilyV1 = W6FilterSpatialOffsetShaderFamilyV1.TargetLocalOffsetDecalTextureLoad,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val frozenExtent = extent.copy(); private val frozenSourceExtent = sourceExtent.copy()
    private val frozenDomain = sourceDomainTargetLocalI32.copy(); private val frozenClip = clipTargetLocalF64.copy()
    private val frozenOffset = org.graphiks.math.vector.Vector2F64(outputToInputOffsetTargetLocalF64.x, outputToInputOffsetTargetLocalF64.y)
    init { require(target != source && sampleCountI32 == 1 && sourceSampleCountI32 == 1 && !frozenDomain.isEmpty && !frozenClip.isEmpty) }
    public fun copyExtent(): org.graphiks.math.geometry.SizeI32 = frozenExtent.copy()
    public fun copySourceExtent(): org.graphiks.math.geometry.SizeI32 = frozenSourceExtent.copy()
    public fun copySourceDomainTargetLocalI32(): org.graphiks.math.geometry.RectI32 = frozenDomain.copy()
    public fun copyClipTargetLocalF64(): org.graphiks.math.geometry.RectF64 = frozenClip.copy()
    public fun copyOutputToInputOffsetTargetLocalF64(): org.graphiks.math.vector.Vector2F64 = org.graphiks.math.vector.Vector2F64(frozenOffset.x, frozenOffset.y)
    public fun nativeSiteOwnerV1(): NativeSiteOwnerV1 = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1(): String = W6FilterSpatialOffsetNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}

public class W6FilterSpatialOffsetNativeSiteRecipeV1 internal constructor(public val host: W6FilterSpatialOffsetRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = host.nativeSiteOwnerV1()
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W6FilterSpatialOffset
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); int("bundle", owner.bundleOrdinalI32)
        text("target", host.target.value); text("input.0", host.source.value); int("inputCount", 1)
        int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height)
        int("sourceExtentWidth", host.copySourceExtent().width); int("sourceExtentHeight", host.copySourceExtent().height)
        rect("sourceDomain", host.copySourceDomainTargetLocalI32()); rectF64("clip", host.copyClipTargetLocalF64())
        double("offsetX", host.copyOutputToInputOffsetTargetLocalF64().x); double("offsetY", host.copyOutputToInputOffsetTargetLocalF64().y)
        text("tileMode", "DECAL"); enum("load", host.load); enum("store", host.store); enum("targetFormat", host.targetFormat); int("sampleCount", host.sampleCountI32)
        enum("sourceFormat", host.sourceFormat); int("sourceSampleCount", host.sourceSampleCountI32); blend("blend", host.blend)
        enum("groupZeroAbi", host.groupZeroAbi); enum("shaderFamily", host.shaderFamily)
        int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

/** The first bounded IIa site. Offset and Tile deliberately remain outside this family. */
public enum class W6FilterSpatialCropGroupZeroAbiV1 { Texture }
public enum class W6FilterSpatialCropShaderFamilyV1 { TargetLocalCropTextureLoad }

/**
 * Planner-owned Crop selection.  These are the complete target-local facts consumed by the
 * renderer's WGSL translation; native lowering never reselects them from FilterPass.
 */
public class W6FilterSpatialCropRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId,
    public val target: PlanResourceId,
    public val source: PlanResourceId,
    extent: org.graphiks.math.geometry.SizeI32,
    sourceExtent: org.graphiks.math.geometry.SizeI32,
    sourceDomainTargetLocalI32: org.graphiks.math.geometry.RectI32,
    clipTargetLocalF64: org.graphiks.math.geometry.RectF64,
    outputToInputOffsetTargetLocalF64: org.graphiks.math.vector.Vector2F64,
    public val tileMode: org.graphiks.kanvas.render.ir.TileMode,
    public val targetFormat: PlanLogicalColorFormat,
    public val sampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat,
    public val sourceSampleCountI32: Int,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent,
    public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1,
    public val groupZeroAbi: W6FilterSpatialCropGroupZeroAbiV1 = W6FilterSpatialCropGroupZeroAbiV1.Texture,
    public val shaderFamily: W6FilterSpatialCropShaderFamilyV1 = W6FilterSpatialCropShaderFamilyV1.TargetLocalCropTextureLoad,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) {
    private val frozenExtent = extent.copy(); private val frozenSourceExtent = sourceExtent.copy()
    private val frozenDomain = sourceDomainTargetLocalI32.copy(); private val frozenClip = clipTargetLocalF64.copy()
    private val frozenOffset = org.graphiks.math.vector.Vector2F64(outputToInputOffsetTargetLocalF64.x, outputToInputOffsetTargetLocalF64.y)
    init { require(target != source && sampleCountI32 == 1 && sourceSampleCountI32 == 1 && !frozenDomain.isEmpty && !frozenClip.isEmpty) }
    public fun copyExtent(): org.graphiks.math.geometry.SizeI32 = frozenExtent.copy()
    public fun copySourceExtent(): org.graphiks.math.geometry.SizeI32 = frozenSourceExtent.copy()
    public fun copySourceDomainTargetLocalI32(): org.graphiks.math.geometry.RectI32 = frozenDomain.copy()
    public fun copyClipTargetLocalF64(): org.graphiks.math.geometry.RectF64 = frozenClip.copy()
    public fun copyOutputToInputOffsetTargetLocalF64(): org.graphiks.math.vector.Vector2F64 = org.graphiks.math.vector.Vector2F64(frozenOffset.x, frozenOffset.y)
    public fun nativeSiteOwnerV1(): NativeSiteOwnerV1 = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1(): String = W6FilterSpatialCropNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}

public class W6FilterSpatialCropNativeSiteRecipeV1 internal constructor(public val host: W6FilterSpatialCropRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = host.nativeSiteOwnerV1()
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W6FilterSpatialCrop
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); int("bundle", owner.bundleOrdinalI32)
        text("target", host.target.value); text("input.0", host.source.value); int("inputCount", 1)
        int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height)
        int("sourceExtentWidth", host.copySourceExtent().width); int("sourceExtentHeight", host.copySourceExtent().height)
        rect("sourceDomain", host.copySourceDomainTargetLocalI32()); rectF64("clip", host.copyClipTargetLocalF64())
        double("offsetX", host.copyOutputToInputOffsetTargetLocalF64().x); double("offsetY", host.copyOutputToInputOffsetTargetLocalF64().y)
        enum("tileMode", host.tileMode); enum("load", host.load); enum("store", host.store); enum("targetFormat", host.targetFormat); int("sampleCount", host.sampleCountI32)
        enum("sourceFormat", host.sourceFormat); int("sourceSampleCount", host.sourceSampleCountI32); blend("blend", host.blend)
        enum("groupZeroAbi", host.groupZeroAbi); enum("shaderFamily", host.shaderFamily)
        int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

/**
 * Versioned, backend-neutral selection recipe.  [canonicalLogicalEncodingV1] is deliberately
 * assembled from typed planner facts; it never serializes a native handle, WGSL, driver bytes,
 * object `toString()`, or a hash-derived identity.
 */
public sealed interface NativeSiteRecipeV1 {
    public val versionI32: Int
    public val owner: NativeSiteOwnerV1
    public val family: NativeSiteRecipeFamilyV1
    public val canonicalLogicalEncodingV1: String
}

/** The bounded fullscreen contract is closed up front; Ib1 instantiates only [CoverageAlpha]. */
public enum class W6FullscreenRecipeVariantV1 {
    Empty, CoverageAlpha, CoverageSolidRect, CoverageRetain, PictureSourceLayer, PictureSourceGraph,
}

/** One planner-owned fullscreen recipe family.  Concrete variants never use nullable selector fields. */
public sealed interface W6FullscreenRecipeV1 {
    public val variant: W6FullscreenRecipeVariantV1
    public val ownerPassId: PlanPassId
}

/** A semantic reason for an Empty program that intentionally renders transparent pixels; never a skip. */
public enum class W6FullscreenEmptyPhaseV1 {
    PictureAggregateBegin, FilterTransparentBlack, CoverageAbsent, PictureAggregateSeal,
    PictureCompositeNoScissor, FilterCompositeNoOp, FilterCompositeNoScissor,
}
public enum class W6FullscreenEmptyGroupZeroAbiV1 { Empty }
public enum class W6FullscreenEmptyTopologyV1 { FullscreenTriangle }
public data class W6FullscreenEmptyDrawV1(
    public val vertexCountI32: Int = 3,
    public val instanceCountI32: Int = 1,
    public val firstVertexI32: Int = 0,
    public val firstInstanceI32: Int = 0,
) { init { require(vertexCountI32 == 3 && instanceCountI32 == 1 && firstVertexI32 == 0 && firstInstanceI32 == 0) } }

/** Planner-owned 2A0b.Ia fullscreen Empty recipe. Group zero is deliberately empty. */
public class W6FullscreenEmptyRecipeV1 internal constructor(
    override val ownerPassId: PlanPassId,
    public val phase: W6FullscreenEmptyPhaseV1,
    public val target: PlanResourceId,
    extent: org.graphiks.math.geometry.SizeI32,
    inputs: List<PlanResourceId>,
    public val load: AttachmentLoadPlan,
    public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val targetFormat: PlanLogicalColorFormat,
    public val sampleCountI32: Int,
    public val clearColor: W6CanonicalColorF32V1 = W6CanonicalColorF32V1.of(0f, 0f, 0f, 0f),
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1,
    public val topology: W6FullscreenEmptyTopologyV1 = W6FullscreenEmptyTopologyV1.FullscreenTriangle,
    public val groupZeroAbi: W6FullscreenEmptyGroupZeroAbiV1 = W6FullscreenEmptyGroupZeroAbiV1.Empty,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) : W6FullscreenRecipeV1 {
    override val variant: W6FullscreenRecipeVariantV1 = W6FullscreenRecipeVariantV1.Empty
    private val frozenInputs = immutableList(inputs)
    private val frozenExtent = extent.copy()
    init { require(frozenInputs.isEmpty() && sampleCountI32 == 1 && target !in frozenInputs) }
    public fun inputs(): List<PlanResourceId> = frozenInputs
    public fun copyExtent(): org.graphiks.math.geometry.SizeI32 = frozenExtent.copy()
    public fun nativeSiteOwnerV1(): NativeSiteOwnerV1 = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1(): String = W6FullscreenEmptyNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}

public class W6FullscreenEmptyNativeSiteRecipeV1 internal constructor(
    public val host: W6FullscreenEmptyRecipeV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = host.nativeSiteOwnerV1()
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W6FullscreenEmpty
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        enum("variant", host.variant); text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); int("bundle", owner.bundleOrdinalI32)
        enum("phase", host.phase); text("target", host.target.value); int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height); int("inputCount", host.inputs().size)
        enum("load", host.load); enum("store", host.store); enum("targetFormat", host.targetFormat)
        int("sampleCount", host.sampleCountI32); color("clearColor", host.clearColor); blend("blend", host.blend)
        enum("topology", host.topology); enum("groupZeroAbi", host.groupZeroAbi)
        int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32)
        int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

/** Group-zero ABI for the bounded alpha-source coverage fullscreen site. */
public enum class W6FullscreenCoverageAlphaGroupZeroAbiV1 { Texture }
public enum class W6FullscreenCoverageAlphaPhaseV1 { CoverageAlpha }

/** The typed shader selection is deliberately not inferred from the pass at native lowering. */
public enum class W6FullscreenCoverageAlphaShaderFamilyV1 { AlphaCoverageTextureLoad }

/** Planner-owned 2A0b.Ib1 recipe for a(S), sampled with no sampler in group zero. */
public class W6FullscreenCoverageAlphaRecipeV1 internal constructor(
    override val ownerPassId: PlanPassId,
    public val target: PlanResourceId,
    public val source: PlanResourceId,
    public val sourceGenerationI64: Long,
    extent: org.graphiks.math.geometry.SizeI32,
    sourceSampleBoundsTargetI32: org.graphiks.math.geometry.RectI32,
    outputToInputOffsetTargetLocalI32: org.graphiks.math.geometry.Point2I32,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.Load,
    public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val targetFormat: PlanLogicalColorFormat,
    public val sampleCountI32: Int,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1,
    public val topology: W6FullscreenEmptyTopologyV1 = W6FullscreenEmptyTopologyV1.FullscreenTriangle,
    public val groupZeroAbi: W6FullscreenCoverageAlphaGroupZeroAbiV1 = W6FullscreenCoverageAlphaGroupZeroAbiV1.Texture,
    public val shaderFamily: W6FullscreenCoverageAlphaShaderFamilyV1 = W6FullscreenCoverageAlphaShaderFamilyV1.AlphaCoverageTextureLoad,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) : W6FullscreenRecipeV1 {
    override val variant: W6FullscreenRecipeVariantV1 = W6FullscreenRecipeVariantV1.CoverageAlpha
    public val phase: W6FullscreenCoverageAlphaPhaseV1 = W6FullscreenCoverageAlphaPhaseV1.CoverageAlpha
    private val frozenExtent = extent.copy()
    private val frozenSourceSampleBoundsTargetI32 = sourceSampleBoundsTargetI32.copy()
    private val frozenOutputToInputOffsetTargetLocalI32 = org.graphiks.math.geometry.Point2I32(
        outputToInputOffsetTargetLocalI32.x, outputToInputOffsetTargetLocalI32.y,
    )
    init {
        require(sourceGenerationI64 >= 0L && target != source && sampleCountI32 == 1 &&
            !frozenSourceSampleBoundsTargetI32.isEmpty)
    }
    public fun copyExtent(): org.graphiks.math.geometry.SizeI32 = frozenExtent.copy()
    public fun copySourceSampleBoundsTargetI32(): org.graphiks.math.geometry.RectI32 = frozenSourceSampleBoundsTargetI32.copy()
    public fun copyOutputToInputOffsetTargetLocalI32(): org.graphiks.math.geometry.Point2I32 =
        org.graphiks.math.geometry.Point2I32(frozenOutputToInputOffsetTargetLocalI32.x, frozenOutputToInputOffsetTargetLocalI32.y)
    public fun nativeSiteOwnerV1(): NativeSiteOwnerV1 = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1(): String = W6FullscreenCoverageAlphaNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}

public class W6FullscreenCoverageAlphaNativeSiteRecipeV1 internal constructor(
    public val host: W6FullscreenCoverageAlphaRecipeV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = host.nativeSiteOwnerV1()
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W6FullscreenCoverageAlpha
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); int("bundle", owner.bundleOrdinalI32)
        enum("variant", host.variant); enum("phase", host.phase); text("target", host.target.value); text("source", host.source.value); long("sourceGeneration", host.sourceGenerationI64)
        int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height)
        rect("sourceSampleBounds", host.copySourceSampleBoundsTargetI32())
        point("outputToInputOffsetTargetLocal", host.copyOutputToInputOffsetTargetLocalI32())
        enum("load", host.load); enum("store", host.store); enum("targetFormat", host.targetFormat); int("sampleCount", host.sampleCountI32)
        blend("blend", host.blend); enum("topology", host.topology); enum("groupZeroAbi", host.groupZeroAbi); enum("shaderFamily", host.shaderFamily)
        int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32)
        int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

public enum class W6FullscreenCoverageSolidRectGroupZeroAbiV1 { Empty }
public enum class W6FullscreenCoverageSolidRectPhaseV1 { CoverageSolidRect }
public enum class W6FullscreenCoverageSolidRectShaderFamilyV1 { SolidRectCoverageOpaque }

/** Planner-owned Ib2 full-target raw coverage for the existing admitted SolidRect raster path. */
public class W6FullscreenCoverageSolidRectRecipeV1 internal constructor(
    override val ownerPassId: PlanPassId,
    public val target: PlanResourceId,
    extent: org.graphiks.math.geometry.SizeI32,
    scissorTargetLocalI32: org.graphiks.math.geometry.RectI32,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent,
    public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val clearColor: W6CanonicalColorF32V1 = W6CanonicalColorF32V1.of(0f, 0f, 0f, 0f),
    public val targetFormat: PlanLogicalColorFormat,
    public val sampleCountI32: Int,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1,
    public val topology: W6FullscreenEmptyTopologyV1 = W6FullscreenEmptyTopologyV1.FullscreenTriangle,
    public val groupZeroAbi: W6FullscreenCoverageSolidRectGroupZeroAbiV1 = W6FullscreenCoverageSolidRectGroupZeroAbiV1.Empty,
    public val shaderFamily: W6FullscreenCoverageSolidRectShaderFamilyV1 = W6FullscreenCoverageSolidRectShaderFamilyV1.SolidRectCoverageOpaque,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) : W6FullscreenRecipeV1 {
    override val variant: W6FullscreenRecipeVariantV1 = W6FullscreenRecipeVariantV1.CoverageSolidRect
    public val phase: W6FullscreenCoverageSolidRectPhaseV1 = W6FullscreenCoverageSolidRectPhaseV1.CoverageSolidRect
    private val frozenExtent = extent.copy()
    private val frozenScissorTargetLocalI32 = scissorTargetLocalI32.copy()
    init {
        require(sampleCountI32 == 1 && frozenScissorTargetLocalI32 == org.graphiks.math.geometry.RectI32(
            0, 0, frozenExtent.width, frozenExtent.height,
        ))
    }
    public fun copyExtent(): org.graphiks.math.geometry.SizeI32 = frozenExtent.copy()
    public fun copyScissorTargetLocalI32(): org.graphiks.math.geometry.RectI32 = frozenScissorTargetLocalI32.copy()
    public fun nativeSiteOwnerV1(): NativeSiteOwnerV1 = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1(): String = W6FullscreenCoverageSolidRectNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}

public class W6FullscreenCoverageSolidRectNativeSiteRecipeV1 internal constructor(
    public val host: W6FullscreenCoverageSolidRectRecipeV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = host.nativeSiteOwnerV1()
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W6FullscreenCoverageSolidRect
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        enum("variant", host.variant); enum("phase", host.phase); text("owner", host.ownerPassId.value)
        int("ordinal", owner.drawOrPacketOrdinalI32); int("bundle", owner.bundleOrdinalI32); text("target", host.target.value)
        int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height); rect("scissor", host.copyScissorTargetLocalI32())
        enum("load", host.load); enum("store", host.store); color("clearColor", host.clearColor); enum("targetFormat", host.targetFormat)
        int("sampleCount", host.sampleCountI32); blend("blend", host.blend); enum("topology", host.topology)
        enum("groupZeroAbi", host.groupZeroAbi); enum("shaderFamily", host.shaderFamily)
        int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32)
        int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

public enum class W6FullscreenCoverageRetainGroupZeroAbiV1 { Texture }
public enum class W6FullscreenCoverageRetainPhaseV1 { CoverageRetain }
public enum class W6FullscreenCoverageRetainShaderFamilyV1 { SampledCoverageTextureLoad }

/** Planner-owned Ib3 retained-coverage copy, sampled target-locally with no sampler. */
public class W6FullscreenCoverageRetainRecipeV1 internal constructor(
    override val ownerPassId: PlanPassId,
    public val target: PlanResourceId,
    public val source: PlanResourceId,
    extent: org.graphiks.math.geometry.SizeI32,
    sourceKnownContentTargetLocalI32: org.graphiks.math.geometry.RectI32,
    outputToInputOffsetTargetLocalI32: org.graphiks.math.geometry.Point2I32,
    scissorTargetLocalI32: org.graphiks.math.geometry.RectI32,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.Load,
    public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val targetFormat: PlanLogicalColorFormat,
    public val sampleCountI32: Int,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1,
    public val topology: W6FullscreenEmptyTopologyV1 = W6FullscreenEmptyTopologyV1.FullscreenTriangle,
    public val groupZeroAbi: W6FullscreenCoverageRetainGroupZeroAbiV1 = W6FullscreenCoverageRetainGroupZeroAbiV1.Texture,
    public val shaderFamily: W6FullscreenCoverageRetainShaderFamilyV1 = W6FullscreenCoverageRetainShaderFamilyV1.SampledCoverageTextureLoad,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
) : W6FullscreenRecipeV1 {
    override val variant: W6FullscreenRecipeVariantV1 = W6FullscreenRecipeVariantV1.CoverageRetain
    public val phase: W6FullscreenCoverageRetainPhaseV1 = W6FullscreenCoverageRetainPhaseV1.CoverageRetain
    private val frozenExtent = extent.copy()
    private val frozenKnownContent = sourceKnownContentTargetLocalI32.copy()
    private val frozenOffset = org.graphiks.math.geometry.Point2I32(outputToInputOffsetTargetLocalI32.x, outputToInputOffsetTargetLocalI32.y)
    private val frozenScissor = scissorTargetLocalI32.copy()
    init { require(target != source && sampleCountI32 == 1 && !frozenKnownContent.isEmpty &&
        frozenScissor == org.graphiks.math.geometry.RectI32(0, 0, frozenExtent.width, frozenExtent.height)) }
    public fun copyExtent(): org.graphiks.math.geometry.SizeI32 = frozenExtent.copy()
    public fun copySourceKnownContentTargetLocalI32(): org.graphiks.math.geometry.RectI32 = frozenKnownContent.copy()
    public fun copyOutputToInputOffsetTargetLocalI32(): org.graphiks.math.geometry.Point2I32 = org.graphiks.math.geometry.Point2I32(frozenOffset.x, frozenOffset.y)
    public fun copyScissorTargetLocalI32(): org.graphiks.math.geometry.RectI32 = frozenScissor.copy()
    public fun nativeSiteOwnerV1(): NativeSiteOwnerV1 = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1(): String = W6FullscreenCoverageRetainNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}

public class W6FullscreenCoverageRetainNativeSiteRecipeV1 internal constructor(public val host: W6FullscreenCoverageRetainRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = host.nativeSiteOwnerV1()
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W6FullscreenCoverageRetain
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        enum("variant", host.variant); enum("phase", host.phase); text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); int("bundle", owner.bundleOrdinalI32)
        text("target", host.target.value); text("input.0", host.source.value); int("inputCount", 1); int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height)
        rect("sourceKnownContent", host.copySourceKnownContentTargetLocalI32()); point("outputToInputOffsetTargetLocal", host.copyOutputToInputOffsetTargetLocalI32()); rect("scissor", host.copyScissorTargetLocalI32())
        enum("load", host.load); enum("store", host.store); enum("targetFormat", host.targetFormat); int("sampleCount", host.sampleCountI32); blend("blend", host.blend)
        enum("topology", host.topology); enum("groupZeroAbi", host.groupZeroAbi); enum("shaderFamily", host.shaderFamily)
        int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

public enum class W6FullscreenPictureSourceLayerGroupZeroAbiV1 { Texture }
public enum class W6FullscreenPictureSourceLayerShaderFamilyV1 { SampledLayerTextureLoad }
public class W6FullscreenPictureSourceLayerRecipeV1 internal constructor(
    override val ownerPassId: PlanPassId, public val target: PlanResourceId, public val source: PlanResourceId,
    extent: org.graphiks.math.geometry.SizeI32, sourceExtent: org.graphiks.math.geometry.SizeI32,
    outputToInputOffsetTargetLocalI32: org.graphiks.math.geometry.Point2I32,
    public val targetFormat: PlanLogicalColorFormat, public val sampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat, public val sourceSampleCountI32: Int,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1, public val topology: W6FullscreenEmptyTopologyV1 = W6FullscreenEmptyTopologyV1.FullscreenTriangle,
    public val groupZeroAbi: W6FullscreenPictureSourceLayerGroupZeroAbiV1 = W6FullscreenPictureSourceLayerGroupZeroAbiV1.Texture,
    public val shaderFamily: W6FullscreenPictureSourceLayerShaderFamilyV1 = W6FullscreenPictureSourceLayerShaderFamilyV1.SampledLayerTextureLoad,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
    scissorTargetLocalI32: org.graphiks.math.geometry.RectI32,
    public val clearColor: W6CanonicalColorF32V1 = W6CanonicalColorF32V1.of(0f, 0f, 0f, 0f),
) : W6FullscreenRecipeV1 {
    override val variant = W6FullscreenRecipeVariantV1.PictureSourceLayer
    private val frozenExtent = extent.copy(); private val frozenSourceExtent = sourceExtent.copy(); private val frozenOffset = org.graphiks.math.geometry.Point2I32(outputToInputOffsetTargetLocalI32.x, outputToInputOffsetTargetLocalI32.y)
    private val frozenScissor = org.graphiks.math.geometry.RectI32(scissorTargetLocalI32.left, scissorTargetLocalI32.top, scissorTargetLocalI32.right, scissorTargetLocalI32.bottom)
    init { require(target != source && sampleCountI32 == 1 && sourceSampleCountI32 == 1 && frozenScissor == org.graphiks.math.geometry.RectI32(0, 0, frozenExtent.width, frozenExtent.height)) }
    public fun copyExtent() = frozenExtent.copy(); public fun copyOutputToInputOffsetTargetLocalI32() = org.graphiks.math.geometry.Point2I32(frozenOffset.x, frozenOffset.y)
    public fun copySourceExtent() = frozenSourceExtent.copy()
    public fun copyScissorTargetLocalI32() = org.graphiks.math.geometry.RectI32(frozenScissor.left, frozenScissor.top, frozenScissor.right, frozenScissor.bottom)
    public fun nativeSiteOwnerV1() = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1() = W6FullscreenPictureSourceLayerNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}
public class W6FullscreenPictureSourceLayerNativeSiteRecipeV1 internal constructor(public val host: W6FullscreenPictureSourceLayerRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = host.nativeSiteOwnerV1(); override val family = NativeSiteRecipeFamilyV1.W6FullscreenPictureSourceLayer
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) {
        enum("variant", host.variant); text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); int("bundle", owner.bundleOrdinalI32); text("target", host.target.value); text("input.0", host.source.value); int("inputCount", 1)
        int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height); int("sourceExtentWidth", host.copySourceExtent().width); int("sourceExtentHeight", host.copySourceExtent().height); point("outputToInputOffsetTargetLocal", host.copyOutputToInputOffsetTargetLocalI32()); rect("scissor", host.copyScissorTargetLocalI32()); color("clearColor", host.clearColor); enum("load", host.load); enum("store", host.store); enum("targetFormat", host.targetFormat); int("sampleCount", host.sampleCountI32); enum("sourceFormat", host.sourceFormat); int("sourceSampleCount", host.sourceSampleCountI32); blend("blend", host.blend); enum("topology", host.topology); enum("groupZeroAbi", host.groupZeroAbi); enum("shaderFamily", host.shaderFamily); int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

public enum class W6FullscreenPictureSourceGraphGroupZeroAbiV1 { Texture }
public enum class W6FullscreenPictureSourceGraphShaderFamilyV1 { SampledGraphTextureLoad }
public class W6FullscreenPictureSourceGraphRecipeV1 internal constructor(
    override val ownerPassId: PlanPassId, public val target: PlanResourceId, public val source: PlanResourceId,
    extent: org.graphiks.math.geometry.SizeI32, sourceExtent: org.graphiks.math.geometry.SizeI32,
    outputToInputOffsetTargetLocalI32: org.graphiks.math.geometry.Point2I32,
    public val targetFormat: PlanLogicalColorFormat, public val sampleCountI32: Int,
    public val sourceFormat: PlanLogicalColorFormat, public val sourceSampleCountI32: Int,
    public val load: AttachmentLoadPlan = AttachmentLoadPlan.ClearTransparent, public val store: AttachmentStorePlan = AttachmentStorePlan.Store,
    public val blend: BlendPlan = BlendPlan.LegacySrcOverV1, public val topology: W6FullscreenEmptyTopologyV1 = W6FullscreenEmptyTopologyV1.FullscreenTriangle,
    public val groupZeroAbi: W6FullscreenPictureSourceGraphGroupZeroAbiV1 = W6FullscreenPictureSourceGraphGroupZeroAbiV1.Texture,
    public val shaderFamily: W6FullscreenPictureSourceGraphShaderFamilyV1 = W6FullscreenPictureSourceGraphShaderFamilyV1.SampledGraphTextureLoad,
    public val draw: W6FullscreenEmptyDrawV1 = W6FullscreenEmptyDrawV1(),
    scissorTargetLocalI32: org.graphiks.math.geometry.RectI32,
    public val clearColor: W6CanonicalColorF32V1 = W6CanonicalColorF32V1.of(0f, 0f, 0f, 0f),
) : W6FullscreenRecipeV1 {
    override val variant = W6FullscreenRecipeVariantV1.PictureSourceGraph
    private val frozenExtent = extent.copy(); private val frozenSourceExtent = sourceExtent.copy(); private val frozenOffset = org.graphiks.math.geometry.Point2I32(outputToInputOffsetTargetLocalI32.x, outputToInputOffsetTargetLocalI32.y)
    private val frozenScissor = org.graphiks.math.geometry.RectI32(scissorTargetLocalI32.left, scissorTargetLocalI32.top, scissorTargetLocalI32.right, scissorTargetLocalI32.bottom)
    init { require(target != source && sampleCountI32 == 1 && sourceSampleCountI32 == 1 && frozenScissor == org.graphiks.math.geometry.RectI32(0, 0, frozenExtent.width, frozenExtent.height)) }
    public fun copyExtent() = frozenExtent.copy(); public fun copySourceExtent() = frozenSourceExtent.copy()
    public fun copyOutputToInputOffsetTargetLocalI32() = org.graphiks.math.geometry.Point2I32(frozenOffset.x, frozenOffset.y)
    public fun copyScissorTargetLocalI32() = org.graphiks.math.geometry.RectI32(frozenScissor.left, frozenScissor.top, frozenScissor.right, frozenScissor.bottom)
    public fun nativeSiteOwnerV1() = NativeSiteOwnerV1(ownerPassId, 0, 0)
    public fun canonicalLogicalEncodingV1() = W6FullscreenPictureSourceGraphNativeSiteRecipeV1(this).canonicalLogicalEncodingV1
}
public class W6FullscreenPictureSourceGraphNativeSiteRecipeV1 internal constructor(public val host: W6FullscreenPictureSourceGraphRecipeV1) : NativeSiteRecipeV1 {
    override val versionI32 = 1; override val owner = host.nativeSiteOwnerV1(); override val family = NativeSiteRecipeFamilyV1.W6FullscreenPictureSourceGraph
    override val canonicalLogicalEncodingV1 = nativeSiteEncodingV1(family) {
        enum("variant", host.variant); text("owner", host.ownerPassId.value); int("ordinal", owner.drawOrPacketOrdinalI32); int("bundle", owner.bundleOrdinalI32); text("target", host.target.value); text("input.0", host.source.value); int("inputCount", 1)
        int("extentWidth", host.copyExtent().width); int("extentHeight", host.copyExtent().height); int("sourceExtentWidth", host.copySourceExtent().width); int("sourceExtentHeight", host.copySourceExtent().height); point("outputToInputOffsetTargetLocal", host.copyOutputToInputOffsetTargetLocalI32()); rect("scissor", host.copyScissorTargetLocalI32()); color("clearColor", host.clearColor); enum("load", host.load); enum("store", host.store); enum("targetFormat", host.targetFormat); int("sampleCount", host.sampleCountI32); enum("sourceFormat", host.sourceFormat); int("sourceSampleCount", host.sourceSampleCountI32); blend("blend", host.blend); enum("topology", host.topology); enum("groupZeroAbi", host.groupZeroAbi); enum("shaderFamily", host.shaderFamily); int("draw.vertexCount", host.draw.vertexCountI32); int("draw.instanceCount", host.draw.instanceCountI32); int("draw.firstVertex", host.draw.firstVertexI32); int("draw.firstInstance", host.draw.firstInstanceI32)
    }
}

public class W6SolidRectNativeSiteRecipeV1 internal constructor(
    public val host: W6SolidRectHostRecipeV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = host.site.nativeSiteOwnerV1()
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W6SolidRect
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        enum("geometry", host.family)
        when (val color = host.colorMode) {
            W6SolidRectColorModeV1.UniformColor16 -> text("colorMode", "UniformColor16")
            is W6SolidRectColorModeV1.FrozenColor -> {
                text("colorMode", "FrozenColor")
                float("red", color.color.redF32)
                float("green", color.color.greenF32)
                float("blue", color.color.blueF32)
                float("alpha", color.color.alphaF32)
            }
        }
        blend("blend", host.blend)
        point("materialOrigin", host.materialOriginDeviceI32)
        enum("targetFormat", host.target.format)
        int("sampleCount", host.target.sampleCountI32)
        enum("coordinate", host.coordinateSlot)
        enum("groupZeroAbi", host.groupZeroAbi)
    }
}

public class W6CorePrimitiveNativeSiteRecipeV1 internal constructor(
    public val host: W6CorePrimitiveHostRecipeV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = host.site.nativeSiteOwnerV1()
    override val family: NativeSiteRecipeFamilyV1 = when (host) {
        is W6AnalyticRectHostRecipeV1 -> NativeSiteRecipeFamilyV1.W6AnalyticRect
        is W6AnalyticRRectHostRecipeV1 -> NativeSiteRecipeFamilyV1.W6AnalyticRRect
        is W6PointHostRecipeV1 -> NativeSiteRecipeFamilyV1.W6Point
    }
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        selector(host.selector)
        rect("scissor", host.scissor)
        point("materialOrigin", host.materialOriginDeviceI32)
        when (host) {
            is W6AnalyticRectHostRecipeV1,
            is W6AnalyticRRectHostRecipeV1,
            -> Unit // The analytic family is already a shader/pipeline selector.
            is W6PointHostRecipeV1 -> {
                // V/I upload bytes remain in the already sealed payload and do not select a
                // shader, layout, pipeline, binding ABI, or point-site variant.
                enum("pointMode", host.pointMode)
            }
        }
    }
}

public class W6PreparedVerticesNativeSiteRecipeV1 internal constructor(
    public val host: W6PreparedVerticesHostRecipeV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = host.site.nativeSiteOwnerV1()
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W6PreparedVertices
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        enum("geometry", host.family)
        host.layout.attributes().forEachIndexed { indexI32, attribute ->
            enum("attribute.$indexI32", attribute)
            int("attributeOffset.$indexI32", host.layout.offsetsBytesI32().getValue(attribute))
        }
        int("stride", host.layout.strideBytesI32)
        enum("topology", host.topology)
        enum("indexWidth", host.indexWidth)
        enum("primitiveAlpha", host.primitiveAlpha)
        enum("sourceKind", host.source.kind)
        enum("sourceIdentityKind", host.source.identityKind)
        int("materialRef", host.source.materialRef.indexI32)
        text("programStructuralId", host.source.programStructuralId.value)
        text("bindingIdentity", host.source.canonicalBindingIdentity)
        blend("blend", host.blend)
        enum("targetFormat", host.target.format)
        int("sampleCount", host.target.sampleCountI32)
        enum("uniformAbi", host.uniformAbi)
        enum("groupZeroAbi", host.groupZeroAbi)
        enum("coordinate", host.coordinateSlot)
        point("materialOrigin", host.materialOriginDeviceI32)
        text("hostProgramIdentity", host.hostProgramIdentity.canonicalIdentity)
    }
}

public class W6PlainLayerCompositeNativeSiteRecipeV1 internal constructor(
    public val host: W6PlainLayerCompositeRecipeV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = host.nativeSiteOwnerV1()
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W6PlainLayerComposite
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        enum("geometry", host.family)
        text("source", host.source.value)
        text("destination", host.destination.value)
        rect("sourceBounds", host.copySourceBoundsLayerI32())
        point("destinationOrigin", host.copyDestinationOriginParentI32())
        float("alpha", host.alphaF32)
        blend("blend", host.blend)
        enum("targetFormat", host.target.format)
        int("sampleCount", host.target.sampleCountI32)
        enum("groupZeroAbi", host.groupZeroAbi)
    }
}

public class W4eClipMaskInitializeNativeSiteRecipeV1 internal constructor(
    public val host: W4eClipMaskInitializeRecipeV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = host.nativeSiteOwnerV1()
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W4eClipMaskInitialize
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("output", host.output.value)
        rect("domain", host.copyDomainI32())
        float("clearCoverage", host.clearCoverageF32)
        enum("geometry", host.family)
        enum("targetFormat", host.target.format)
        int("sampleCount", host.target.sampleCountI32)
        enum("load", host.load)
        enum("groupZeroAbi", host.groupZeroAbi)
    }
}

/** Canonical logical recipe for one W6b direct/producer/cover bundle. */
public class W6bCoverageRasterNativeSiteRecipeV1 internal constructor(
    public val host: W6bCoverageRasterBundleHostRecipeV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = NativeSiteOwnerV1(host.ownerPassId, host.siteOrdinalI32, host.bundleOrdinalI32)
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W6bCoverageRaster
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        enum("geometry", host.family)
        enum("phase", host.phase)
        enum("role", host.role)
        enum("topology", host.topology)
        enum("clip", host.clip)
        enum("uniformAbi", host.uniformAbi)
        enum("target", host.target)
        text("output", host.output.value)
        text("hasDepthStencil", (host.depthStencil != null).toString())
        host.depthStencil?.let { text("depthStencil", it.value) }
        blend("blend", host.blend)
        enum("stencil", host.stencil)
        host.fillRule?.let { enum("fillRule", it) }
        rect("scissor", host.copyScissorI32())
        rect("rasterBounds", host.copyRasterBoundsI32())
        host.pointMode?.let { enum("pointMode", it) }
        host.pathStrategy?.let { enum("pathStrategy", it) }
        host.uniform32?.let { uniform ->
            int("targetWidth", uniform.targetWidthI32); int("targetHeight", uniform.targetHeightI32)
            color("coverageColor", uniform.color)
        }
        host.analytic80?.let { uniform ->
            int("targetWidth", uniform.targetWidthI32); int("targetHeight", uniform.targetHeightI32)
            text("antiAlias", uniform.antiAlias.toString())
            rectF32("deviceBounds", uniform.copyDeviceBounds())
            uniform.copyRadiiF32().forEachIndexed { index, value -> float("radius.$index", value) }
            enum("drawOrigin", uniform.drawOrigin)
            color("coverageColor", uniform.color)
        }
    }
}

/**
 * The closed 2A0a catalog.  It contains only the seven already frozen 2P0–2P6 families;
 * W6a variants still open in 2A0b, remaining W4e packets, W5a, leases, and budget are absent.
 */
public class NativeSiteRecipeCatalogV1 internal constructor(recipes: List<NativeSiteRecipeV1>) {
    private val orderedRecipes = java.util.Collections.unmodifiableList(recipes.toList())
    private val recipesByOwner = java.util.Collections.unmodifiableMap(LinkedHashMap<NativeSiteOwnerV1, NativeSiteRecipeV1>().apply {
        orderedRecipes.forEach { recipe -> require(put(recipe.owner, recipe) == null) }
    })

    init {
        require(orderedRecipes.all { it.versionI32 == 1 && it.canonicalLogicalEncodingV1.isNotBlank() })
        require(recipesByOwner.size == orderedRecipes.size)
    }

    public fun recipes(): List<NativeSiteRecipeV1> = orderedRecipes
    public fun recipe(owner: NativeSiteOwnerV1): NativeSiteRecipeV1 = requireNotNull(recipesByOwner[owner]) {
        "Missing frozen native-site recipe for ${owner.ownerPassId.value}/${owner.drawOrPacketOrdinalI32}/${owner.bundleOrdinalI32}."
    }
    public fun recipeOrNull(owner: NativeSiteOwnerV1): NativeSiteRecipeV1? = recipesByOwner[owner]

    internal fun matches(other: NativeSiteRecipeCatalogV1): Boolean =
        orderedRecipes.size == other.orderedRecipes.size && orderedRecipes.zip(other.orderedRecipes).all { (left, right) ->
            left.owner == right.owner && left.family == right.family &&
                left.canonicalLogicalEncodingV1 == right.canonicalLogicalEncodingV1
        }

    /**
     * Canonical encoding excludes non-selector geometry payload, so retain provenance separately:
     * every wrapper must carry the exact frozen host object published beside this catalog.
     */
    internal fun authenticatesFrozenHosts(
        solidRects: Map<W6GeometrySiteKeyV1, W6SolidRectHostRecipeV1>,
        corePrimitives: Map<W6GeometrySiteKeyV1, W6CorePrimitiveHostRecipeV1>,
        preparedVertices: Map<W6GeometrySiteKeyV1, W6PreparedVerticesHostRecipeV1>,
        plainLayerComposites: Map<W6LayerCompositeSiteKeyV1, W6PlainLayerCompositeRecipeV1>,
        filteredLayerComposites: Map<W6LayerCompositeSiteKeyV1, W6FilteredLayerCompositeRecipeV1>,
        pictureCompositeGraphs: Map<PlanPassId, W6PictureCompositeGraphRecipeV1>,
        pictureCompositeGraphFiltered: Map<PlanPassId, W6PictureCompositeGraphFilteredRecipeV1>,
        pictureCompositeGraphDestinations: Map<PlanPassId, W6PictureCompositeGraphDestinationRecipeV1>,
        filterCompositeDraws: Map<PlanPassId, W6FilterCompositeDrawRecipeV1>,
        filterCompositeLayerPlains: Map<PlanPassId, W6FilterCompositeLayerPlainRecipeV1>,
        filterCompositeLayerFiltered: Map<PlanPassId, W6FilterCompositeLayerFilteredRecipeV1>,
        filterCompositeLayerDestinations: Map<PlanPassId, W6FilterCompositeLayerDestinationRecipeV1>,
        filterCompositeLayerFilteredDestinations: Map<PlanPassId, W6FilterCompositeLayerFilteredDestinationRecipeV1>,
        filterCompositePicturePlains: Map<PlanPassId, W6FilterCompositePicturePlainRecipeV1>,
        filterCompositePictureGraphs: Map<PlanPassId, W6FilterCompositePictureGraphRecipeV1>,
        filterCompositePictureGraphFiltered: Map<PlanPassId, W6FilterCompositePictureGraphFilteredRecipeV1>,
        filterCompositePictureDestinations: Map<PlanPassId, W6FilterCompositePictureDestinationRecipeV1>,
        clipMaskInitializes: Map<PlanPassId, W4eClipMaskInitializeRecipeV1>,
        coverageRasters: Map<PlanPassId, W6bCoverageRasterHostRecipeV1>,
        fullscreenEmpties: Map<PlanPassId, W6FullscreenEmptyRecipeV1>,
        coverageAlphas: Map<PlanPassId, W6FullscreenCoverageAlphaRecipeV1>,
        coverageSolidRects: Map<PlanPassId, W6FullscreenCoverageSolidRectRecipeV1>,
        coverageRetains: Map<PlanPassId, W6FullscreenCoverageRetainRecipeV1>,
        pictureSourceLayers: Map<PlanPassId, W6FullscreenPictureSourceLayerRecipeV1>,
        pictureSourceGraphs: Map<PlanPassId, W6FullscreenPictureSourceGraphRecipeV1>,
        spatialCrops: Map<PlanPassId, W6FilterSpatialCropRecipeV1>,
        spatialOffsets: Map<PlanPassId, W6FilterSpatialOffsetRecipeV1>,
        spatialTiles: Map<PlanPassId, W6FilterSpatialTileRecipeV1>,
        morphologies: Map<PlanPassId, W6FilterMorphologyRecipeV1>,
        colorFilters: Map<PlanPassId, W6FilterColorFilterRecipeV1>,
        merges: Map<PlanPassId, W6FilterMergeRecipeV1>,
        blends: Map<PlanPassId, W6FilterBlendRecipeV1>,
        separableBlurs: Map<PlanPassId, W6FilterSeparableBlurRecipeV1>,
        maskBlurNormals: Map<PlanPassId, W6FilterMaskBlurNormalRecipeV1>,
        maskBlurDualSources: Map<PlanPassId, W6FilterMaskBlurDualSourceRecipeV1>,
        maskShaders: Map<PlanPassId, W6FilterMaskShaderRecipeV1>,
        maskTables: Map<PlanPassId, W6FilterMaskTableRecipeV1>,
        materializedSources: Map<PlanPassId, W6FilterMaterializedSourceRecipeV1>,
        dropShadowColorizes: Map<PlanPassId, W6FilterDropShadowColorizeRecipeV1>,
        dropShadowComposites: Map<PlanPassId, W6FilterDropShadowCompositeRecipeV1>,
    ): Boolean = orderedRecipes.all { recipe ->
        when (recipe) {
            is W6SolidRectNativeSiteRecipeV1 -> solidRects[recipe.host.site] === recipe.host
            is W6CorePrimitiveNativeSiteRecipeV1 -> corePrimitives[recipe.host.site] === recipe.host
            is W6PreparedVerticesNativeSiteRecipeV1 -> preparedVertices[recipe.host.site] === recipe.host
            is W6PlainLayerCompositeNativeSiteRecipeV1 -> plainLayerComposites[recipe.host.site] === recipe.host
            is W6FilteredLayerCompositeNativeSiteRecipeV1 -> filteredLayerComposites[recipe.host.site] === recipe.host
            is W6PictureCompositeGraphNativeSiteRecipeV1 -> pictureCompositeGraphs[recipe.host.ownerPassId] === recipe.host
            is W6PictureCompositeGraphFilteredNativeSiteRecipeV1 -> pictureCompositeGraphFiltered[recipe.host.ownerPassId] === recipe.host
            is W6PictureCompositeGraphDestinationNativeSiteRecipeV1 -> pictureCompositeGraphDestinations[recipe.host.ownerPassId] === recipe.host
            is W6FilterCompositeDrawNativeSiteRecipeV1 -> filterCompositeDraws[recipe.host.ownerPassId] === recipe.host
            is W6FilterCompositeLayerPlainNativeSiteRecipeV1 -> filterCompositeLayerPlains[recipe.host.ownerPassId] === recipe.host
            is W6FilterCompositeLayerFilteredNativeSiteRecipeV1 -> filterCompositeLayerFiltered[recipe.host.ownerPassId] === recipe.host
            is W6FilterCompositeLayerDestinationNativeSiteRecipeV1 -> filterCompositeLayerDestinations[recipe.host.ownerPassId] === recipe.host
            is W6FilterCompositeLayerFilteredDestinationNativeSiteRecipeV1 -> filterCompositeLayerFilteredDestinations[recipe.host.ownerPassId] === recipe.host
            is W6FilterCompositePicturePlainNativeSiteRecipeV1 -> filterCompositePicturePlains[recipe.host.ownerPassId] === recipe.host
            is W6FilterCompositePictureGraphNativeSiteRecipeV1 -> filterCompositePictureGraphs[recipe.host.ownerPassId] === recipe.host
            is W6FilterCompositePictureGraphFilteredNativeSiteRecipeV1 -> filterCompositePictureGraphFiltered[recipe.host.ownerPassId] === recipe.host
            is W6FilterCompositePictureDestinationNativeSiteRecipeV1 -> filterCompositePictureDestinations[recipe.host.ownerPassId] === recipe.host
            is W4eClipMaskInitializeNativeSiteRecipeV1 -> clipMaskInitializes[recipe.host.passId] === recipe.host
            is W6bCoverageRasterNativeSiteRecipeV1 -> coverageRasters[recipe.host.ownerPassId]?.bundle(recipe.host.bundleOrdinalI32) === recipe.host
            is W6FullscreenEmptyNativeSiteRecipeV1 -> fullscreenEmpties[recipe.host.ownerPassId] === recipe.host
            is W6FullscreenCoverageAlphaNativeSiteRecipeV1 -> coverageAlphas[recipe.host.ownerPassId] === recipe.host
            is W6FullscreenCoverageSolidRectNativeSiteRecipeV1 -> coverageSolidRects[recipe.host.ownerPassId] === recipe.host
            is W6FullscreenCoverageRetainNativeSiteRecipeV1 -> coverageRetains[recipe.host.ownerPassId] === recipe.host
            is W6FullscreenPictureSourceLayerNativeSiteRecipeV1 -> pictureSourceLayers[recipe.host.ownerPassId] === recipe.host
            is W6FullscreenPictureSourceGraphNativeSiteRecipeV1 -> pictureSourceGraphs[recipe.host.ownerPassId] === recipe.host
            is W6FilterSpatialCropNativeSiteRecipeV1 -> spatialCrops[recipe.host.ownerPassId] === recipe.host
            is W6FilterSpatialOffsetNativeSiteRecipeV1 -> spatialOffsets[recipe.host.ownerPassId] === recipe.host
            is W6FilterSpatialTileNativeSiteRecipeV1 -> spatialTiles[recipe.host.ownerPassId] === recipe.host
            is W6FilterMorphologyNativeSiteRecipeV1 -> morphologies[recipe.host.ownerPassId] === recipe.host
            is W6FilterColorFilterNativeSiteRecipeV1 -> colorFilters[recipe.host.ownerPassId] === recipe.host
            is W6FilterMergeNativeSiteRecipeV1 -> merges[recipe.host.ownerPassId] === recipe.host
            is W6FilterBlendNativeSiteRecipeV1 -> blends[recipe.host.ownerPassId] === recipe.host
            is W6FilterSeparableBlurNativeSiteRecipeV1 -> separableBlurs[recipe.host.ownerPassId] === recipe.host
            is W6FilterMaskBlurNormalNativeSiteRecipeV1 -> maskBlurNormals[recipe.host.ownerPassId] === recipe.host
            is W6FilterMaskBlurDualSourceNativeSiteRecipeV1 -> maskBlurDualSources[recipe.host.ownerPassId] === recipe.host
            is W6FilterMaskShaderNativeSiteRecipeV1 -> maskShaders[recipe.host.ownerPassId] === recipe.host
            is W6FilterMaskTableNativeSiteRecipeV1 -> maskTables[recipe.host.ownerPassId] === recipe.host
            is W6FilterMaterializedSourceNativeSiteRecipeV1 -> materializedSources[recipe.host.ownerPassId] === recipe.host
            is W6FilterDropShadowColorizeNativeSiteRecipeV1 -> dropShadowColorizes[recipe.host.ownerPassId] === recipe.host
            is W6FilterDropShadowCompositeNativeSiteRecipeV1 -> dropShadowComposites[recipe.host.ownerPassId] === recipe.host
        }
    }

    internal companion object {
        val Empty: NativeSiteRecipeCatalogV1 = NativeSiteRecipeCatalogV1(emptyList())
    }
}

/** Builds the catalog only from recipes frozen at final planner binding; it performs no reselection. */
public fun freezeNativeSiteRecipeCatalogV1(
    passes: List<PlanPass>,
    solidRects: Map<W6GeometrySiteKeyV1, W6SolidRectHostRecipeV1>,
    corePrimitives: Map<W6GeometrySiteKeyV1, W6CorePrimitiveHostRecipeV1>,
    preparedVertices: Map<W6GeometrySiteKeyV1, W6PreparedVerticesHostRecipeV1>,
    plainLayerComposites: Map<W6LayerCompositeSiteKeyV1, W6PlainLayerCompositeRecipeV1>,
    filteredLayerComposites: Map<W6LayerCompositeSiteKeyV1, W6FilteredLayerCompositeRecipeV1> = emptyMap(),
    pictureCompositeGraphs: Map<PlanPassId, W6PictureCompositeGraphRecipeV1> = emptyMap(),
    pictureCompositeGraphFiltered: Map<PlanPassId, W6PictureCompositeGraphFilteredRecipeV1> = emptyMap(),
    pictureCompositeGraphDestinations: Map<PlanPassId, W6PictureCompositeGraphDestinationRecipeV1> = emptyMap(),
    filterCompositeDraws: Map<PlanPassId, W6FilterCompositeDrawRecipeV1> = emptyMap(),
    filterCompositeLayerPlains: Map<PlanPassId, W6FilterCompositeLayerPlainRecipeV1> = emptyMap(),
    filterCompositeLayerFiltered: Map<PlanPassId, W6FilterCompositeLayerFilteredRecipeV1> = emptyMap(),
    filterCompositeLayerDestinations: Map<PlanPassId, W6FilterCompositeLayerDestinationRecipeV1> = emptyMap(),
    filterCompositeLayerFilteredDestinations: Map<PlanPassId, W6FilterCompositeLayerFilteredDestinationRecipeV1> = emptyMap(),
    filterCompositePicturePlains: Map<PlanPassId, W6FilterCompositePicturePlainRecipeV1> = emptyMap(),
    filterCompositePictureGraphs: Map<PlanPassId, W6FilterCompositePictureGraphRecipeV1> = emptyMap(),
    filterCompositePictureGraphFiltered: Map<PlanPassId, W6FilterCompositePictureGraphFilteredRecipeV1> = emptyMap(),
    filterCompositePictureDestinations: Map<PlanPassId, W6FilterCompositePictureDestinationRecipeV1> = emptyMap(),
    clipMaskInitializes: Map<PlanPassId, W4eClipMaskInitializeRecipeV1>,
    coverageRasters: Map<PlanPassId, W6bCoverageRasterHostRecipeV1>,
    fullscreenEmpties: Map<PlanPassId, W6FullscreenEmptyRecipeV1> = emptyMap(),
    coverageAlphas: Map<PlanPassId, W6FullscreenCoverageAlphaRecipeV1> = emptyMap(),
    coverageSolidRects: Map<PlanPassId, W6FullscreenCoverageSolidRectRecipeV1> = emptyMap(),
    coverageRetains: Map<PlanPassId, W6FullscreenCoverageRetainRecipeV1> = emptyMap(),
    pictureSourceLayers: Map<PlanPassId, W6FullscreenPictureSourceLayerRecipeV1> = emptyMap(),
    pictureSourceGraphs: Map<PlanPassId, W6FullscreenPictureSourceGraphRecipeV1> = emptyMap(),
    spatialCrops: Map<PlanPassId, W6FilterSpatialCropRecipeV1> = emptyMap(),
    spatialOffsets: Map<PlanPassId, W6FilterSpatialOffsetRecipeV1> = emptyMap(),
    spatialTiles: Map<PlanPassId, W6FilterSpatialTileRecipeV1> = emptyMap(),
    morphologies: Map<PlanPassId, W6FilterMorphologyRecipeV1> = emptyMap(),
    colorFilters: Map<PlanPassId, W6FilterColorFilterRecipeV1> = emptyMap(),
    merges: Map<PlanPassId, W6FilterMergeRecipeV1> = emptyMap(),
    blends: Map<PlanPassId, W6FilterBlendRecipeV1> = emptyMap(),
    separableBlurs: Map<PlanPassId, W6FilterSeparableBlurRecipeV1> = emptyMap(),
    maskBlurNormals: Map<PlanPassId, W6FilterMaskBlurNormalRecipeV1> = emptyMap(),
    maskBlurDualSources: Map<PlanPassId, W6FilterMaskBlurDualSourceRecipeV1> = emptyMap(),
    maskShaders: Map<PlanPassId, W6FilterMaskShaderRecipeV1> = emptyMap(),
    maskTables: Map<PlanPassId, W6FilterMaskTableRecipeV1> = emptyMap(),
    materializedSources: Map<PlanPassId, W6FilterMaterializedSourceRecipeV1> = emptyMap(),
    dropShadowColorizes: Map<PlanPassId, W6FilterDropShadowColorizeRecipeV1> = emptyMap(),
    dropShadowComposites: Map<PlanPassId, W6FilterDropShadowCompositeRecipeV1> = emptyMap(),
): NativeSiteRecipeCatalogV1 = NativeSiteRecipeCatalogV1(buildList {
    solidRects.forEach { (site, recipe) -> require(site == recipe.site) }
    corePrimitives.forEach { (site, recipe) -> require(site == recipe.site) }
    preparedVertices.forEach { (site, recipe) -> require(site == recipe.site) }
    plainLayerComposites.forEach { (site, recipe) -> require(site == recipe.site) }
    filteredLayerComposites.forEach { (site, recipe) -> require(site == recipe.site) }
    pictureCompositeGraphs.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    pictureCompositeGraphFiltered.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    pictureCompositeGraphDestinations.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    filterCompositeDraws.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    filterCompositeLayerPlains.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    filterCompositeLayerFiltered.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    filterCompositeLayerDestinations.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    filterCompositeLayerFilteredDestinations.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    filterCompositePicturePlains.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    filterCompositePictureGraphs.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    filterCompositePictureGraphFiltered.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    filterCompositePictureDestinations.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    clipMaskInitializes.forEach { (passId, recipe) -> require(passId == recipe.passId) }
    coverageRasters.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    fullscreenEmpties.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    coverageAlphas.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    coverageSolidRects.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    coverageRetains.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    pictureSourceLayers.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    pictureSourceGraphs.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    spatialCrops.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    spatialOffsets.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    spatialTiles.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    morphologies.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    colorFilters.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    merges.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    blends.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    separableBlurs.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    maskBlurNormals.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    maskBlurDualSources.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    maskShaders.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    maskTables.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    materializedSources.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    dropShadowColorizes.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    dropShadowComposites.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    val remainingSolidRects = solidRects.toMutableMap()
    val remainingCorePrimitives = corePrimitives.toMutableMap()
    val remainingPreparedVertices = preparedVertices.toMutableMap()
    val remainingPlainComposites = plainLayerComposites.toMutableMap()
    val remainingFilteredComposites = filteredLayerComposites.toMutableMap()
    val remainingPictureCompositeGraphs = pictureCompositeGraphs.toMutableMap()
    val remainingPictureCompositeGraphFiltered = pictureCompositeGraphFiltered.toMutableMap()
    val remainingPictureCompositeGraphDestinations = pictureCompositeGraphDestinations.toMutableMap()
    val remainingFilterCompositeDraws = filterCompositeDraws.toMutableMap()
    val remainingFilterCompositeLayerPlains = filterCompositeLayerPlains.toMutableMap()
    val remainingFilterCompositeLayerFiltered = filterCompositeLayerFiltered.toMutableMap()
    val remainingFilterCompositeLayerDestinations = filterCompositeLayerDestinations.toMutableMap()
    val remainingFilterCompositeLayerFilteredDestinations = filterCompositeLayerFilteredDestinations.toMutableMap()
    val remainingFilterCompositePicturePlains = filterCompositePicturePlains.toMutableMap()
    val remainingFilterCompositePictureGraphs = filterCompositePictureGraphs.toMutableMap()
    val remainingFilterCompositePictureGraphFiltered = filterCompositePictureGraphFiltered.toMutableMap()
    val remainingFilterCompositePictureDestinations = filterCompositePictureDestinations.toMutableMap()
    val remainingClipInitializes = clipMaskInitializes.toMutableMap()
    val remainingCoverageRasters = coverageRasters.toMutableMap()
    val remainingEmpties = fullscreenEmpties.toMutableMap()
    val remainingCoverageAlphas = coverageAlphas.toMutableMap()
    val remainingCoverageSolidRects = coverageSolidRects.toMutableMap()
    val remainingCoverageRetains = coverageRetains.toMutableMap()
    val remainingPictureSourceLayers = pictureSourceLayers.toMutableMap()
    val remainingPictureSourceGraphs = pictureSourceGraphs.toMutableMap()
    val remainingSpatialCrops = spatialCrops.toMutableMap()
    val remainingSpatialOffsets = spatialOffsets.toMutableMap()
    val remainingSpatialTiles = spatialTiles.toMutableMap()
    val remainingMorphologies = morphologies.toMutableMap()
    val remainingColorFilters = colorFilters.toMutableMap()
    val remainingMerges = merges.toMutableMap()
    val remainingBlends = blends.toMutableMap()
    val remainingSeparableBlurs = separableBlurs.toMutableMap()
    val remainingMaskBlurNormals = maskBlurNormals.toMutableMap()
    val remainingMaskBlurDualSources = maskBlurDualSources.toMutableMap()
    val remainingMaskShaders = maskShaders.toMutableMap()
    val remainingMaskTables = maskTables.toMutableMap()
    val remainingMaterializedSources = materializedSources.toMutableMap()
    val remainingDropShadowColorizes = dropShadowColorizes.toMutableMap()
    val remainingDropShadowComposites = dropShadowComposites.toMutableMap()
    passes.forEach { pass ->
        when (pass) {
            is PlanPass.RenderPass -> pass.draws().indices.forEach { drawOrdinalI32 ->
                val site = W6GeometrySiteKeyV1(pass.id, drawOrdinalI32)
                remainingSolidRects.remove(site)?.let { add(W6SolidRectNativeSiteRecipeV1(it)) }
                remainingCorePrimitives.remove(site)?.let { add(W6CorePrimitiveNativeSiteRecipeV1(it)) }
                remainingPreparedVertices.remove(site)?.let { add(W6PreparedVerticesNativeSiteRecipeV1(it)) }
            }
            is PlanPass.LayerComposite -> W6LayerCompositeSiteKeyV1(pass.id, 0).let { site ->
                remainingPlainComposites.remove(site)?.let { add(W6PlainLayerCompositeNativeSiteRecipeV1(it)) }
                remainingFilteredComposites.remove(site)?.let { add(W6FilteredLayerCompositeNativeSiteRecipeV1(it)) }
            }
            is PlanPass.ClipMaskInitialize -> remainingClipInitializes.remove(pass.id)?.let {
                add(W4eClipMaskInitializeNativeSiteRecipeV1(it))
            }
            is PlanPass.FilterCoverageSourcePass -> {
                remainingCoverageRasters.remove(pass.id)?.bundles()?.forEach { add(W6bCoverageRasterNativeSiteRecipeV1(it)) }
                remainingEmpties.remove(pass.id)?.let { add(W6FullscreenEmptyNativeSiteRecipeV1(it)) }
                remainingCoverageAlphas.remove(pass.id)?.let { add(W6FullscreenCoverageAlphaNativeSiteRecipeV1(it)) }
                remainingCoverageSolidRects.remove(pass.id)?.let { add(W6FullscreenCoverageSolidRectNativeSiteRecipeV1(it)) }
            }
            is PlanPass.FilterCoverageRetainPass -> remainingCoverageRetains.remove(pass.id)?.let { add(W6FullscreenCoverageRetainNativeSiteRecipeV1(it)) }
            is PlanPass.PictureSourcePass -> {
                remainingPictureSourceLayers.remove(pass.id)?.let { add(W6FullscreenPictureSourceLayerNativeSiteRecipeV1(it)) }
                remainingPictureSourceGraphs.remove(pass.id)?.let { add(W6FullscreenPictureSourceGraphNativeSiteRecipeV1(it)) }
            }
            is PlanPass.FilterPass -> {
                remainingSpatialCrops.remove(pass.id)?.let { add(W6FilterSpatialCropNativeSiteRecipeV1(it)) }
                remainingSpatialOffsets.remove(pass.id)?.let { add(W6FilterSpatialOffsetNativeSiteRecipeV1(it)) }
                remainingSpatialTiles.remove(pass.id)?.let { add(W6FilterSpatialTileNativeSiteRecipeV1(it)) }
                remainingMorphologies.remove(pass.id)?.let { add(W6FilterMorphologyNativeSiteRecipeV1(it)) }
                remainingColorFilters.remove(pass.id)?.let { add(W6FilterColorFilterNativeSiteRecipeV1(it)) }
                remainingMerges.remove(pass.id)?.let { add(W6FilterMergeNativeSiteRecipeV1(it)) }
                remainingBlends.remove(pass.id)?.let { add(W6FilterBlendNativeSiteRecipeV1(it)) }
                remainingSeparableBlurs.remove(pass.id)?.let { add(W6FilterSeparableBlurNativeSiteRecipeV1(it)) }
                remainingMaskBlurNormals.remove(pass.id)?.let { add(W6FilterMaskBlurNormalNativeSiteRecipeV1(it)) }
                remainingMaskBlurDualSources.remove(pass.id)?.let { add(W6FilterMaskBlurDualSourceNativeSiteRecipeV1(it)) }
                remainingMaskShaders.remove(pass.id)?.let { add(W6FilterMaskShaderNativeSiteRecipeV1(it)) }
                remainingMaskTables.remove(pass.id)?.let { add(W6FilterMaskTableNativeSiteRecipeV1(it)) }
                remainingMaterializedSources.remove(pass.id)?.let { add(W6FilterMaterializedSourceNativeSiteRecipeV1(it)) }
                remainingDropShadowColorizes.remove(pass.id)?.let { add(W6FilterDropShadowColorizeNativeSiteRecipeV1(it)) }
                remainingDropShadowComposites.remove(pass.id)?.let { add(W6FilterDropShadowCompositeNativeSiteRecipeV1(it)) }
            }
            is PlanPass.PictureComposite -> remainingPictureCompositeGraphs.remove(pass.id)?.let { add(W6PictureCompositeGraphNativeSiteRecipeV1(it)) }
                ?: remainingPictureCompositeGraphFiltered.remove(pass.id)?.let { add(W6PictureCompositeGraphFilteredNativeSiteRecipeV1(it)) }
                ?: remainingPictureCompositeGraphDestinations.remove(pass.id)?.let { add(W6PictureCompositeGraphDestinationNativeSiteRecipeV1(it)) }
                ?: remainingEmpties.remove(pass.id)?.let { add(W6FullscreenEmptyNativeSiteRecipeV1(it)) }
            is PlanPass.FilterComposite -> remainingFilterCompositeDraws.remove(pass.id)?.let { add(W6FilterCompositeDrawNativeSiteRecipeV1(it)) }
                ?: remainingFilterCompositeLayerPlains.remove(pass.id)?.let { add(W6FilterCompositeLayerPlainNativeSiteRecipeV1(it)) }
                ?: remainingFilterCompositeLayerFiltered.remove(pass.id)?.let { add(W6FilterCompositeLayerFilteredNativeSiteRecipeV1(it)) }
                ?: remainingFilterCompositeLayerDestinations.remove(pass.id)?.let { add(W6FilterCompositeLayerDestinationNativeSiteRecipeV1(it)) }
                ?: remainingFilterCompositeLayerFilteredDestinations.remove(pass.id)?.let { add(W6FilterCompositeLayerFilteredDestinationNativeSiteRecipeV1(it)) }
                ?: remainingFilterCompositePicturePlains.remove(pass.id)?.let { add(W6FilterCompositePicturePlainNativeSiteRecipeV1(it)) }
                ?: remainingFilterCompositePictureGraphs.remove(pass.id)?.let { add(W6FilterCompositePictureGraphNativeSiteRecipeV1(it)) }
                ?: remainingFilterCompositePictureGraphFiltered.remove(pass.id)?.let { add(W6FilterCompositePictureGraphFilteredNativeSiteRecipeV1(it)) }
                ?: remainingFilterCompositePictureDestinations.remove(pass.id)?.let { add(W6FilterCompositePictureDestinationNativeSiteRecipeV1(it)) }
                ?: remainingEmpties.remove(pass.id)?.let { add(W6FullscreenEmptyNativeSiteRecipeV1(it)) }
            is PlanPass.PictureAggregateBeginPass, is PlanPass.FilterSourceClear,
            is PlanPass.PictureAggregateSealPass,
            -> remainingEmpties.remove(pass.id)?.let { add(W6FullscreenEmptyNativeSiteRecipeV1(it)) }
            else -> Unit
        }
    }
    require(remainingSolidRects.isEmpty() && remainingCorePrimitives.isEmpty() && remainingPreparedVertices.isEmpty() &&
        remainingPlainComposites.isEmpty() && remainingFilteredComposites.isEmpty() && remainingPictureCompositeGraphs.isEmpty() && remainingPictureCompositeGraphFiltered.isEmpty() && remainingPictureCompositeGraphDestinations.isEmpty() && remainingClipInitializes.isEmpty() && remainingCoverageRasters.isEmpty() &&
        remainingFilterCompositeDraws.isEmpty() && remainingFilterCompositeLayerPlains.isEmpty() && remainingFilterCompositeLayerFiltered.isEmpty() && remainingFilterCompositeLayerDestinations.isEmpty() && remainingFilterCompositeLayerFilteredDestinations.isEmpty() && remainingFilterCompositePicturePlains.isEmpty() && remainingFilterCompositePictureGraphs.isEmpty() && remainingFilterCompositePictureGraphFiltered.isEmpty() && remainingFilterCompositePictureDestinations.isEmpty() && remainingEmpties.isEmpty() && remainingCoverageAlphas.isEmpty() && remainingCoverageSolidRects.isEmpty() && remainingCoverageRetains.isEmpty() && remainingPictureSourceLayers.isEmpty() && remainingPictureSourceGraphs.isEmpty() && remainingSpatialCrops.isEmpty() && remainingSpatialOffsets.isEmpty() && remainingSpatialTiles.isEmpty() && remainingMorphologies.isEmpty() && remainingColorFilters.isEmpty() && remainingMerges.isEmpty() && remainingBlends.isEmpty() && remainingSeparableBlurs.isEmpty() && remainingMaskBlurNormals.isEmpty() && remainingMaskBlurDualSources.isEmpty() && remainingMaskShaders.isEmpty() && remainingMaskTables.isEmpty() && remainingMaterializedSources.isEmpty() && remainingDropShadowColorizes.isEmpty() && remainingDropShadowComposites.isEmpty()) {
        "Native-site recipes must all be owned by final planner passes."
    }
})

public fun freezeW6FullscreenPictureSourceLayerRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FullscreenPictureSourceLayerRecipeV1> = LinkedHashMap<PlanPassId, W6FullscreenPictureSourceLayerRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.PictureSourcePass>().forEach { pass ->
        if (pass.graphTextureOperand != null) return@forEach
        val source = requireNotNull(pass.layerInput); val sampling = requireNotNull(pass.sourceSampling); val target = resources.single { it.id == pass.output }; val sourceResource = resources.single { it.id == source }
        val format = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 PictureSourceLayer requires color attachment.")
        val sourceFormat = (sourceResource.format as? PlanTextureFormat.Color)?.value ?: error("W6 PictureSourceLayer requires color source.")
        require(PlanResourceUsage.Sampled in sourceResource.usages())
        val extent = requireNotNull(target.copyExtent())
        require(put(pass.id, W6FullscreenPictureSourceLayerRecipeV1(pass.id, pass.output, source, extent, requireNotNull(sourceResource.copyExtent()), sampling.copyOutputToInputOffsetTargetLocalI32(), format, target.sampleCountI32, sourceFormat, sourceResource.sampleCountI32, scissorTargetLocalI32 = org.graphiks.math.geometry.RectI32(0, 0, extent.width, extent.height))) == null)
    }
}

public fun freezeW6FullscreenPictureSourceGraphRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FullscreenPictureSourceGraphRecipeV1> = LinkedHashMap<PlanPassId, W6FullscreenPictureSourceGraphRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.PictureSourcePass>().forEach { pass ->
        val operand = pass.graphTextureOperand ?: return@forEach
        val sampling = requireNotNull(pass.sourceSampling); val target = resources.single { it.id == pass.output }
        val source = resources.single { it.id == operand.sealedSourceId }
        val targetFormat = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 PictureSourceGraph requires color attachment.")
        val sourceFormat = (source.format as? PlanTextureFormat.Color)?.value ?: error("W6 PictureSourceGraph requires color source.")
        require(PlanResourceUsage.Sampled in source.usages())
        val extent = requireNotNull(target.copyExtent())
        require(put(pass.id, W6FullscreenPictureSourceGraphRecipeV1(pass.id, pass.output, operand.sealedSourceId, extent,
            requireNotNull(source.copyExtent()), sampling.copyOutputToInputOffsetTargetLocalI32(), targetFormat,
            target.sampleCountI32, sourceFormat, source.sampleCountI32,
            scissorTargetLocalI32 = org.graphiks.math.geometry.RectI32(0, 0, extent.width, extent.height))) == null)
    }
}

public fun freezeW6FullscreenCoverageRetainRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FullscreenCoverageRetainRecipeV1> =
    LinkedHashMap<PlanPassId, W6FullscreenCoverageRetainRecipeV1>().apply {
        passes.filterIsInstance<PlanPass.FilterCoverageRetainPass>().forEach { pass ->
            val sampling = requireNotNull(pass.sampling)
            val target = resources.single { it.id == pass.output }; val source = resources.single { it.id == pass.source }
            val format = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 CoverageRetain requires a color attachment.")
            val extent = requireNotNull(target.copyExtent())
            require(target.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in source.usages())
            require(put(pass.id, W6FullscreenCoverageRetainRecipeV1(pass.id, pass.output, pass.source, extent,
                sampling.copyKnownContentInputTargetLocalI32(), sampling.copyOutputToInputOffsetTargetLocalI32(),
                org.graphiks.math.geometry.RectI32(0, 0, extent.width, extent.height), targetFormat = format, sampleCountI32 = target.sampleCountI32)) == null)
        }
    }

/** Freezes only Ib2 SolidRect coverage sites; this is intentionally separate from W6b raster bundles. */
public fun freezeW6FullscreenCoverageSolidRectRecipesV1(
    passes: List<PlanPass>,
    resources: List<PlanResource>,
): Map<PlanPassId, W6FullscreenCoverageSolidRectRecipeV1> = LinkedHashMap<PlanPassId, W6FullscreenCoverageSolidRectRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.FilterCoverageSourcePass>().forEach { pass ->
        val binding = pass.rasterBinding ?: return@forEach
        if (binding.draw !is SolidRectDraw) return@forEach
        require(pass.sealedAlphaSource == null && binding.depthStencil == null)
        val target = resources.single { it.id == pass.output }
        val format = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 CoverageSolidRect requires a color attachment.")
        val extent = requireNotNull(target.copyExtent())
        require(target.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages())
        require(put(pass.id, W6FullscreenCoverageSolidRectRecipeV1(pass.id, pass.output, extent,
            org.graphiks.math.geometry.RectI32(0, 0, extent.width, extent.height), targetFormat = format,
            sampleCountI32 = target.sampleCountI32)) == null)
    }
}

/** Freezes only the Ib1 alpha-source coverage sites; raster, solid and retain remain separate variants. */
public fun freezeW6FullscreenCoverageAlphaRecipesV1(
    passes: List<PlanPass>,
    resources: List<PlanResource>,
): Map<PlanPassId, W6FullscreenCoverageAlphaRecipeV1> = LinkedHashMap<PlanPassId, W6FullscreenCoverageAlphaRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.FilterCoverageSourcePass>().forEach { pass ->
        val alpha = pass.sealedAlphaSource ?: return@forEach
        require(pass.rasterBinding == null)
        val sampling = requireNotNull(pass.sealedAlphaSampling)
        val target = resources.single { it.id == pass.output }
        val source = resources.single { it.id == alpha.sealedSourceId }
        val format = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 CoverageAlpha requires a color attachment.")
        require(target.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages() &&
            PlanResourceUsage.Sampled in source.usages() && source.copyExtent() == alpha.copySampleBoundsTargetI32().let {
                org.graphiks.math.geometry.SizeI32(it.width(), it.height()) })
        require(put(pass.id, W6FullscreenCoverageAlphaRecipeV1(pass.id, pass.output, alpha.sealedSourceId,
            alpha.sealedSourceGenerationI64, requireNotNull(target.copyExtent()), alpha.copySampleBoundsTargetI32(),
            sampling.copyOutputToInputOffsetTargetLocalI32(), targetFormat = format, sampleCountI32 = target.sampleCountI32)) == null)
    }
}

/** Freezes exactly the Empty branches that the W6a renderer executes as fullscreen programs. */
public fun freezeW6FullscreenEmptyRecipesV1(
    passes: List<PlanPass>,
    resources: List<PlanResource>,
): Map<PlanPassId, W6FullscreenEmptyRecipeV1> = LinkedHashMap<PlanPassId, W6FullscreenEmptyRecipeV1>().apply {
    fun add(pass: PlanPass, phase: W6FullscreenEmptyPhaseV1, target: PlanResourceId, load: AttachmentLoadPlan) {
        val row = resources.single { it.id == target }
        val format = (row.format as? PlanTextureFormat.Color)?.value
            ?: error("W6 Empty requires a color attachment.")
        require(row.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in row.usages())
        require(put(pass.id, W6FullscreenEmptyRecipeV1(pass.id, phase, target, requireNotNull(row.copyExtent()), emptyList(), load,
            AttachmentStorePlan.Store, format, row.sampleCountI32)) == null)
    }
    passes.forEach { pass -> when (pass) {
        is PlanPass.PictureAggregateBeginPass -> add(pass, W6FullscreenEmptyPhaseV1.PictureAggregateBegin, pass.target, AttachmentLoadPlan.ClearTransparent)
        is PlanPass.FilterSourceClear -> add(pass, W6FullscreenEmptyPhaseV1.FilterTransparentBlack, pass.output, AttachmentLoadPlan.ClearTransparent)
        is PlanPass.FilterCoverageSourcePass -> if (pass.rasterBinding == null && pass.sealedAlphaSource == null)
            add(pass, W6FullscreenEmptyPhaseV1.CoverageAbsent, pass.output, AttachmentLoadPlan.ClearTransparent)
        is PlanPass.PictureAggregateSealPass -> add(pass, W6FullscreenEmptyPhaseV1.PictureAggregateSeal, pass.aggregateTarget, AttachmentLoadPlan.Load)
        is PlanPass.PictureComposite -> if (pass.operands?.copyCompositeScissorTargetLocalI32() == null)
            add(pass, W6FullscreenEmptyPhaseV1.PictureCompositeNoScissor, pass.destination, AttachmentLoadPlan.Load)
        is PlanPass.FilterComposite -> {
            val phase = when {
                (pass.operation as? FilterCompositeOperationV1.Draw)?.noOp == true ||
                    (pass.operation as? FilterCompositeOperationV1.Layer)?.noOp == true -> W6FullscreenEmptyPhaseV1.FilterCompositeNoOp
                pass.copyCompositeScissorTargetLocalI32() == null -> W6FullscreenEmptyPhaseV1.FilterCompositeNoScissor
                else -> null
            }
            if (phase != null) add(pass, phase, pass.destination, AttachmentLoadPlan.Load)
        }
        else -> Unit
    } }
}

/** Freezes IIa1 only. Offset and Tile remain deliberately absent until IIa2. */
public fun freezeW6FilterSpatialCropRecipesV1(
    passes: List<PlanPass>, resources: List<PlanResource>,
): Map<PlanPassId, W6FilterSpatialCropRecipeV1> = LinkedHashMap<PlanPassId, W6FilterSpatialCropRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
        val operation = pass.operation as? FilterPassOperationV1.Crop ?: return@forEach
        require(pass.inputs().size == 1 && pass.frozenSamplingProgram == null)
        val target = resources.single { it.id == pass.output }; val source = resources.single { it.id == pass.inputs().single() }
        val targetFormat = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 Crop requires color target.")
        val sourceFormat = (source.format as? PlanTextureFormat.Color)?.value ?: error("W6 Crop requires color source.")
        require(target.sampleCountI32 == 1 && source.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in source.usages())
        val sampling = operation.sampling
        require(put(pass.id, W6FilterSpatialCropRecipeV1(pass.id, pass.output, pass.inputs().single(),
            requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), sampling.copySourceInputTargetLocalI32(),
            sampling.copyClipOutputTargetLocalF64(), sampling.copyOutputToInputOffsetTargetLocalF64(), operation.tileMode,
            targetFormat, target.sampleCountI32, sourceFormat, source.sampleCountI32)) == null)
    }
}

/** Freezes only IIa2a Offset. Tile remains deliberately outside the catalog. */
public fun freezeW6FilterSpatialOffsetRecipesV1(
    passes: List<PlanPass>, resources: List<PlanResource>,
): Map<PlanPassId, W6FilterSpatialOffsetRecipeV1> = LinkedHashMap<PlanPassId, W6FilterSpatialOffsetRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
        val operation = pass.operation as? FilterPassOperationV1.Offset ?: return@forEach
        require(pass.inputs().size == 1 && pass.frozenSamplingProgram == null)
        val target = resources.single { it.id == pass.output }; val source = resources.single { it.id == pass.inputs().single() }
        val targetFormat = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 Offset requires color target.")
        val sourceFormat = (source.format as? PlanTextureFormat.Color)?.value ?: error("W6 Offset requires color source.")
        require(target.sampleCountI32 == 1 && source.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in source.usages())
        val sampling = operation.sampling
        require(put(pass.id, W6FilterSpatialOffsetRecipeV1(pass.id, pass.output, pass.inputs().single(),
            requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), sampling.copySourceInputTargetLocalI32(),
            sampling.copyClipOutputTargetLocalF64(), sampling.copyOutputToInputOffsetTargetLocalF64(),
            targetFormat, target.sampleCountI32, sourceFormat, source.sampleCountI32)) == null)
    }
}

public fun freezeW6FilterSpatialTileRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FilterSpatialTileRecipeV1> = LinkedHashMap<PlanPassId, W6FilterSpatialTileRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
        val operation = pass.operation as? FilterPassOperationV1.Tile ?: return@forEach
        require(pass.inputs().size == 1 && pass.frozenSamplingProgram == null)
        val target = resources.single { it.id == pass.output }; val source = resources.single { it.id == pass.inputs().single() }
        val targetFormat = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 Tile requires color target.")
        val sourceFormat = (source.format as? PlanTextureFormat.Color)?.value ?: error("W6 Tile requires color source.")
        require(target.sampleCountI32 == 1 && source.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in source.usages())
        val sampling = operation.sampling
        require(put(pass.id, W6FilterSpatialTileRecipeV1(pass.id, pass.output, pass.inputs().single(), requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), operation.copySourceInputTargetLocalI32(), sampling.copyClipOutputTargetLocalF64(), sampling.copyOutputToInputOffsetTargetLocalF64(), targetFormat, target.sampleCountI32, sourceFormat, source.sampleCountI32)) == null)
    }
}

/** Freezes IIb Morphology selection, including the planner-decided separable axis and radii. */
public fun freezeW6FilterMorphologyRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FilterMorphologyRecipeV1> = LinkedHashMap<PlanPassId, W6FilterMorphologyRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
        val operation = pass.operation as? FilterPassOperationV1.Morphology ?: return@forEach
        require(pass.inputs().size == 1 && pass.frozenSamplingProgram == null)
        val target = resources.single { it.id == pass.output }; val source = resources.single { it.id == pass.inputs().single() }
        val targetFormat = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 Morphology requires color target.")
        val sourceFormat = (source.format as? PlanTextureFormat.Color)?.value ?: error("W6 Morphology requires color source.")
        require(target.sampleCountI32 == 1 && source.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in source.usages())
        val sampling = operation.sampling
        require(put(pass.id, W6FilterMorphologyRecipeV1(pass.id, pass.output, pass.inputs().single(),
            requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), operation.morphologyKind, operation.axis,
            operation.radiusXF64, operation.radiusYF64, operation.radiusXTexelsI32, operation.radiusYTexelsI32,
            sampling.copyKnownContentInputTargetLocalI32(), sampling.copyOutputToInputOffsetTargetLocalI32(),
            targetFormat, target.sampleCountI32, sourceFormat, source.sampleCountI32)) == null)
    }
}

/** Freezes IIc's only materialized ColorFilter mode: source texture plus W5f uniform row. */
public fun freezeW6FilterColorFilterRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FilterColorFilterRecipeV1> = LinkedHashMap<PlanPassId, W6FilterColorFilterRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
        val operation = pass.operation as? FilterPassOperationV1.ColorFilter ?: return@forEach
        require(pass.inputs().size == 1 && pass.frozenSamplingProgram == null)
        val target = resources.single { it.id == pass.output }; val source = resources.single { it.id == pass.inputs().single() }
        val uniformId = requireNotNull(operation.uniformResource); val uniform = resources.single { it.id == uniformId }
        val targetFormat = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 ColorFilter requires color target.")
        val sourceFormat = (source.format as? PlanTextureFormat.Color)?.value ?: error("W6 ColorFilter requires color source.")
        require(target.sampleCountI32 == 1 && source.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in source.usages() && uniform.role == PlanResourceRole.SourceUniformData && uniform.kind == PlanResourceKind.Buffer && uniform.usages() == setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination))
        require(put(pass.id, W6FilterColorFilterRecipeV1(pass.id, pass.output, pass.inputs().single(), requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), operation.execution, uniformId, requireNotNull(operation.uniformOffsetBytesI64), requireNotNull(operation.uniformCapacityBytesI64), operation.sampling.copyOutputToInputOffsetTargetLocalI32(), targetFormat, target.sampleCountI32, sourceFormat, source.sampleCountI32)) == null)
    }
}

/** Freezes IId1 Merge's positional sources and samplings; duplicate sources deliberately remain rows. */
public fun freezeW6FilterMergeRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FilterMergeRecipeV1> = LinkedHashMap<PlanPassId, W6FilterMergeRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
        val operation = pass.operation as? FilterPassOperationV1.Merge ?: return@forEach
        require(pass.frozenSamplingProgram == null && pass.inputs().size == operation.inputSamplings().size)
        val target = resources.single { it.id == pass.output }
        val targetFormat = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 Merge requires color target.")
        require(target.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages())
        val inputs = pass.inputs().zip(operation.inputSamplings()).map { (sourceId, sampling) ->
            val source = resources.single { it.id == sourceId }
            val format = (source.format as? PlanTextureFormat.Color)?.value ?: error("W6 Merge requires color source.")
            require(source.sampleCountI32 == 1 && PlanResourceUsage.Sampled in source.usages())
            W6FilterMergeInputRecipeV1(sourceId, requireNotNull(source.copyExtent()), sampling.copyKnownContentInputTargetLocalI32(), sampling.copyOutputToInputOffsetTargetLocalI32(), format, source.sampleCountI32)
        }
        require(put(pass.id, W6FilterMergeRecipeV1(pass.id, pass.output, inputs, requireNotNull(target.copyExtent()), targetFormat, target.sampleCountI32)) == null)
    }
}

public fun freezeW6FilterBlendRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FilterBlendRecipeV1> = LinkedHashMap<PlanPassId, W6FilterBlendRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
        val operation = pass.operation as? FilterPassOperationV1.Blend ?: return@forEach
        require(pass.frozenSamplingProgram == null && pass.inputs().size == 2)
        val target = resources.single { it.id == pass.output }; val targetFormat = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 Blend requires color target.")
        require(target.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages())
        fun input(index: Int, sampling: FilterInputSamplingV1): W6FilterBlendInputRecipeV1 { val source = resources.single { it.id == pass.inputs()[index] }; val format = (source.format as? PlanTextureFormat.Color)?.value ?: error("W6 Blend requires color source."); require(source.sampleCountI32 == 1 && PlanResourceUsage.Sampled in source.usages()); return W6FilterBlendInputRecipeV1(pass.inputs()[index], requireNotNull(source.copyExtent()), sampling.copyKnownContentInputTargetLocalI32(), sampling.copyOutputToInputOffsetTargetLocalI32(), format, source.sampleCountI32) }
        val formula = W6FilterBlendFormulaV1.W5BlendFormulaV1
        require(put(pass.id, W6FilterBlendRecipeV1(pass.id, pass.output, input(0, operation.backgroundSampling()), input(1, operation.foregroundSampling()), requireNotNull(target.copyExtent()), operation.blend, formula, frozenW6FilterBlendFormulaWgslV1(operation.blend, formula), targetFormat, target.sampleCountI32)) == null)
    }
}

public fun freezeW6FilterSeparableBlurRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FilterSeparableBlurRecipeV1> = LinkedHashMap<PlanPassId, W6FilterSeparableBlurRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
        val operation = pass.operation as? FilterPassOperationV1.SeparableBlur ?: return@forEach
        require(pass.frozenSamplingProgram == null && pass.inputs().size == 1)
        val sampling = requireNotNull(operation.sampling)
        val target = resources.single { it.id == pass.output }; val source = resources.single { it.id == pass.inputs().single() }
        val targetFormat = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 SeparableBlur requires color target.")
        val sourceFormat = (source.format as? PlanTextureFormat.Color)?.value ?: error("W6 SeparableBlur requires color source.")
        require(target.sampleCountI32 == 1 && source.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in source.usages())
        require(put(pass.id, W6FilterSeparableBlurRecipeV1(pass.id, pass.output, pass.inputs().single(), operation.kind, operation.axis, operation.sigmaF32, operation.tileMode, requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), sampling.copyKnownContentInputTargetLocalI32(), sampling.copyOutputToInputOffsetTargetLocalI32(), targetFormat, sourceFormat, target.sampleCountI32, source.sampleCountI32)) == null)
    }
}

/** Freezes only IIe2a: NORMAL samples blurred coverage; the original-input styles remain open. */
public fun freezeW6FilterMaskBlurNormalRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FilterMaskBlurNormalRecipeV1> = LinkedHashMap<PlanPassId, W6FilterMaskBlurNormalRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
        val operation = pass.operation as? FilterPassOperationV1.MaskBlurStyle ?: return@forEach
        if (operation.style != org.graphiks.kanvas.render.ir.MaskBlurStyle.NORMAL) return@forEach
        require(pass.frozenSamplingProgram == null && pass.inputs().size == 1 && operation.originalCoverageSource == null && operation.originalSampling == null)
        val sampling = requireNotNull(operation.blurredSampling)
        val target = resources.single { it.id == pass.output }; val blurred = resources.single { it.id == operation.blurredCoverageSource }
        val targetFormat = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 MaskBlur NORMAL requires color target.")
        val blurredFormat = (blurred.format as? PlanTextureFormat.Color)?.value ?: error("W6 MaskBlur NORMAL requires color source.")
        require(target.sampleCountI32 == 1 && blurred.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in blurred.usages())
        require(put(pass.id, W6FilterMaskBlurNormalRecipeV1(pass.id, pass.output, operation.blurredCoverageSource, operation.style,
            requireNotNull(target.copyExtent()), requireNotNull(blurred.copyExtent()), sampling.copyKnownContentInputTargetLocalI32(),
            sampling.copyOutputToInputOffsetTargetLocalI32(), targetFormat, blurredFormat, target.sampleCountI32, blurred.sampleCountI32)) == null)
    }
}

/** Freezes IIe2b only, retaining the explicit blurred-first/original-second ABI order. */
public fun freezeW6FilterMaskBlurDualSourceRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FilterMaskBlurDualSourceRecipeV1> = LinkedHashMap<PlanPassId, W6FilterMaskBlurDualSourceRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
        val operation = pass.operation as? FilterPassOperationV1.MaskBlurStyle ?: return@forEach
        if (operation.style !in setOf(org.graphiks.kanvas.render.ir.MaskBlurStyle.SOLID, org.graphiks.kanvas.render.ir.MaskBlurStyle.OUTER, org.graphiks.kanvas.render.ir.MaskBlurStyle.INNER)) return@forEach
        val originalId = requireNotNull(operation.originalCoverageSource); val blurredSampling = requireNotNull(operation.blurredSampling); val originalSampling = requireNotNull(operation.originalSampling)
        require(pass.frozenSamplingProgram == null && pass.inputs() == listOf(operation.blurredCoverageSource, originalId))
        val target = resources.single { it.id == pass.output }; val blurred = resources.single { it.id == operation.blurredCoverageSource }; val original = resources.single { it.id == originalId }
        val targetFormat = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 dual MaskBlur requires color target.")
        val blurredFormat = (blurred.format as? PlanTextureFormat.Color)?.value ?: error("W6 dual MaskBlur requires blurred color source.")
        val originalFormat = (original.format as? PlanTextureFormat.Color)?.value ?: error("W6 dual MaskBlur requires original color source.")
        require(target.sampleCountI32 == 1 && blurred.sampleCountI32 == 1 && original.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in blurred.usages() && PlanResourceUsage.Sampled in original.usages())
        require(put(pass.id, W6FilterMaskBlurDualSourceRecipeV1(pass.id, pass.output, operation.blurredCoverageSource, originalId, operation.style,
            requireNotNull(target.copyExtent()), requireNotNull(blurred.copyExtent()), requireNotNull(original.copyExtent()),
            blurredSampling.copyKnownContentInputTargetLocalI32(), originalSampling.copyKnownContentInputTargetLocalI32(),
            blurredSampling.copyOutputToInputOffsetTargetLocalI32(), originalSampling.copyOutputToInputOffsetTargetLocalI32(),
            targetFormat, blurredFormat, originalFormat, target.sampleCountI32, blurred.sampleCountI32, original.sampleCountI32)) == null)
    }
}

/** IIf1 freezes the existing W5 material row as an ordered coverage/material group-zero contract. */
public fun freezeW6FilterMaskShaderRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>, materialTable: MaterialPlanTable): Map<PlanPassId, W6FilterMaskShaderRecipeV1> = LinkedHashMap<PlanPassId, W6FilterMaskShaderRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
        val operation = pass.operation as? FilterPassOperationV1.MaskShader ?: return@forEach
        val binding = operation.materialBinding as? FilterPassOperationV1.MaskShaderMaterialBindingV1.Planned
            ?: error("W6 MaskShader requires its published W5 material binding.")
        val sampling = requireNotNull(operation.sampling)
        require(pass.frozenSamplingProgram == null && pass.inputs().size == 1 && binding.materialAuthority.materialPlanRef() == binding.material)
        val materialAbi = w6FilterMaskShaderMaterialAbiV1(materialTable, binding)
        val target = resources.single { it.id == pass.output }; val coverage = resources.single { it.id == pass.inputs().single() }
        val uniform = resources.single { it.id == binding.uniformResource }
        val targetFormat = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 MaskShader requires color target.")
        val coverageFormat = (coverage.format as? PlanTextureFormat.Color)?.value ?: error("W6 MaskShader requires color coverage.")
        require(target.sampleCountI32 == 1 && coverage.sampleCountI32 == 1 && PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in coverage.usages() && uniform.role == PlanResourceRole.SourceUniformData && uniform.byteSize == binding.uniformCapacityBytesI64 && Math.addExact(binding.uniformOffsetBytesI64, 16L) <= binding.uniformCapacityBytesI64)
        require(put(pass.id, W6FilterMaskShaderRecipeV1(pass.id, pass.output, pass.inputs().single(), binding.occurrenceIdI32,
            binding.material, binding.uniformResource, binding.uniformOffsetBytesI64, binding.uniformCapacityBytesI64,
            materialAbi.structuralId, materialAbi.canonicalIdentity, materialAbi.uniformByteCountI64, materialAbi.bindingManifest,
            binding.materialDeviceOriginI32, requireNotNull(target.copyExtent()), requireNotNull(coverage.copyExtent()),
            sampling.copyKnownContentInputTargetLocalI32(), sampling.copyOutputToInputOffsetTargetLocalI32(), targetFormat,
            coverageFormat, target.sampleCountI32, coverage.sampleCountI32)) == null)
    }
}

/** IIf2 freezes the exact captured LUT storage row and target-local coverage sampling. */
public fun freezeW6FilterMaskTableRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FilterMaskTableRecipeV1> = LinkedHashMap<PlanPassId, W6FilterMaskTableRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
        val operation = pass.operation as? FilterPassOperationV1.MaskTable ?: return@forEach
        val sampling = requireNotNull(operation.sampling)
        require(pass.frozenSamplingProgram == null && pass.inputs().size == 1)
        val target = resources.single { it.id == pass.output }
        val coverage = resources.single { it.id == pass.inputs().single() }
        val table = resources.single { it.id == operation.tableResourceId }
        val targetFormat = (target.format as? PlanTextureFormat.Color)?.value ?: error("W6 MaskTable requires color target.")
        val coverageFormat = (coverage.format as? PlanTextureFormat.Color)?.value ?: error("W6 MaskTable requires color coverage.")
        require(target.sampleCountI32 == 1 && coverage.sampleCountI32 == 1 &&
            PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in coverage.usages() &&
            table.role == PlanResourceRole.MaskTableData && table.kind == PlanResourceKind.Buffer && table.byteSize == 256L &&
            table.usages() == setOf(PlanResourceUsage.StorageRead, PlanResourceUsage.CopyDestination))
        require(put(pass.id, W6FilterMaskTableRecipeV1(pass.id, pass.output, pass.inputs().single(), operation.tableResourceId,
            operation.copyTable(), operation.generationI64, operation.ownerMaskOccurrenceI32,
            requireNotNull(target.copyExtent()), requireNotNull(coverage.copyExtent()),
            sampling.copyKnownContentInputTargetLocalI32(), sampling.copyOutputToInputOffsetTargetLocalI32(),
            targetFormat, coverageFormat, target.sampleCountI32, coverage.sampleCountI32)) == null)
    }
}

public fun freezeW6FilterMaterializedSourceRecipesV1(passes: List<PlanPass>, resources: List<PlanResource>): Map<PlanPassId, W6FilterMaterializedSourceRecipeV1> = LinkedHashMap<PlanPassId, W6FilterMaterializedSourceRecipeV1>().apply {
    val alphaReplacement = buildMap<PlanResourceId, Boolean> { passes.forEach { pass -> when (pass) {
        is PlanPass.RenderPass -> pass.coverageSource?.let { put(pass.target, pass.w6bMaskSourceBinding != null) }
        is PlanPass.StencilCover -> pass.coverageSource?.let { put(pass.target, true) }
        is PlanPass.PictureSourcePass -> pass.coverageSource?.let { put(pass.output, true) }
        else -> Unit
    } } }
    passes.filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
        val operation = pass.operation as? FilterPassOperationV1.MaterializedSource ?: return@forEach
        val sourceSampling = requireNotNull(operation.sourceSampling); val coverageSampling = requireNotNull(operation.coverageSampling)
        require(pass.inputs().size == 2)
        val target = resources.single { it.id == pass.output }; val source = resources.single { it.id == pass.inputs().first() }; val coverage = resources.single { it.id == pass.inputs().last() }
        val tf = (target.format as? PlanTextureFormat.Color)?.value ?: error("MaterializedSource requires color target."); val sf = (source.format as? PlanTextureFormat.Color)?.value ?: error("MaterializedSource requires color source."); val cf = (coverage.format as? PlanTextureFormat.Color)?.value ?: error("MaterializedSource requires color coverage.")
        require(PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in source.usages() && PlanResourceUsage.Sampled in coverage.usages())
        require(put(pass.id, W6FilterMaterializedSourceRecipeV1(pass.id, pass.output, pass.inputs().first(), pass.inputs().last(), requireNotNull(target.copyExtent()), requireNotNull(source.copyExtent()), requireNotNull(coverage.copyExtent()), sourceSampling.copyKnownContentInputTargetLocalI32(), coverageSampling.copyKnownContentInputTargetLocalI32(), sourceSampling.copyOutputToInputOffsetTargetLocalI32(), coverageSampling.copyOutputToInputOffsetTargetLocalI32(), operation.bounds.copyTargetOriginDeviceI32(), if (alphaReplacement[pass.inputs().first()] == true) W6FilterMaterializedSourceAlphaModeV1.ReplaceSourceAlpha else W6FilterMaterializedSourceAlphaModeV1.MultiplySourceAlpha, tf, sf, cf, target.sampleCountI32, source.sampleCountI32, coverage.sampleCountI32)) == null)
    }
}

/** Freezes the one-input IIg1 colorize pass; IIg2 composite remains a separate future family. */
public fun freezeW6FilterDropShadowColorizeRecipesV1(
    passes: List<PlanPass>, resources: List<PlanResource>,
): Map<PlanPassId, W6FilterDropShadowColorizeRecipeV1> = LinkedHashMap<PlanPassId, W6FilterDropShadowColorizeRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
        val operation = pass.operation as? FilterPassOperationV1.DropShadowColorize ?: return@forEach
        val sampling = requireNotNull(operation.linearSampling)
        require(pass.inputs().size == 1)
        val target = resources.single { it.id == pass.output }
        val blurred = resources.single { it.id == pass.inputs().single() }
        val targetFormat = (target.format as? PlanTextureFormat.Color)?.value
            ?: error("DropShadowColorize requires color target.")
        val blurredFormat = (blurred.format as? PlanTextureFormat.Color)?.value
            ?: error("DropShadowColorize requires color source.")
        require(PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in blurred.usages())
        require(put(pass.id, W6FilterDropShadowColorizeRecipeV1(
            pass.id, pass.output, pass.inputs().single(), operation.color.value, operation.copyOffsetF64(),
            sampling.copySourceCoordinateOffsetTargetLocalF64(), sampling.copySourceFootprintTargetLocalI32(),
            sampling.copyOutputFootprintTargetLocalI32(), sampling.copyOutputFootprintTargetLocalI32(), requireNotNull(target.copyExtent()),
            requireNotNull(blurred.copyExtent()), targetFormat, blurredFormat, target.sampleCountI32,
            blurred.sampleCountI32,
        )) == null)
    }
}

/** Freezes IIg2 only for COMPOSITE; SHADOW_ONLY terminates at IIg1 and owns no second site. */
public fun freezeW6FilterDropShadowCompositeRecipesV1(
    passes: List<PlanPass>, resources: List<PlanResource>,
): Map<PlanPassId, W6FilterDropShadowCompositeRecipeV1> = LinkedHashMap<PlanPassId, W6FilterDropShadowCompositeRecipeV1>().apply {
    passes.filterIsInstance<PlanPass.FilterPass>().forEach { pass ->
        val operation = pass.operation as? FilterPassOperationV1.DropShadowComposite ?: return@forEach
        require(operation.mode == CapturedDropShadowModeV1.COMPOSITE && pass.inputs().size == 2)
        val shadow = pass.inputs().first(); val original = requireNotNull(operation.originalInput)
        require(pass.inputs() == listOf(shadow, original))
        val target = resources.single { it.id == pass.output }; val shadowRow = resources.single { it.id == shadow }
        val originalRow = resources.single { it.id == original }
        val targetFormat = (target.format as? PlanTextureFormat.Color)?.value ?: error("DropShadowComposite requires color target.")
        val shadowFormat = (shadowRow.format as? PlanTextureFormat.Color)?.value ?: error("DropShadowComposite requires color shadow.")
        val originalFormat = (originalRow.format as? PlanTextureFormat.Color)?.value ?: error("DropShadowComposite requires color original.")
        val extent = requireNotNull(target.copyExtent()); val shadowExtent = requireNotNull(shadowRow.copyExtent()); val originalExtent = requireNotNull(originalRow.copyExtent())
        require(PlanResourceUsage.RenderAttachment in target.usages() && PlanResourceUsage.Sampled in shadowRow.usages() && PlanResourceUsage.Sampled in originalRow.usages())
        require(put(pass.id, W6FilterDropShadowCompositeRecipeV1(
            pass.id, pass.output, shadow, original, operation.mode,
            requireNotNull(operation.copyShadowSampleOffsetTargetLocalI32()), requireNotNull(operation.copyOriginalSampleOffsetTargetLocalI32()),
            org.graphiks.math.geometry.RectI32(0, 0, shadowExtent.width, shadowExtent.height), org.graphiks.math.geometry.RectI32(0, 0, originalExtent.width, originalExtent.height),
            org.graphiks.math.geometry.RectI32(0, 0, extent.width, extent.height), extent, shadowExtent, originalExtent,
            targetFormat, shadowFormat, originalFormat, target.sampleCountI32, shadowRow.sampleCountI32, originalRow.sampleCountI32,
        )) == null)
    }
}

private class W6FilterMaskShaderMaterialAbiV1(
    val structuralId: String, val canonicalIdentity: String, val uniformByteCountI64: Long,
    val bindingManifest: List<W6FilterMaskShaderBindingAbiV1>,
)

/** Planner-side projection of the W5 ABI. It deliberately contains no WGSL or native resource handle. */
private fun w6FilterMaskShaderMaterialAbiV1(table: MaterialPlanTable,
    binding: FilterPassOperationV1.MaskShaderMaterialBindingV1.Planned): W6FilterMaskShaderMaterialAbiV1 {
    val authority = binding.materialAuthority
    val v4 = authority is PlanDrawMaterialAuthority.MaterialV4 || authority is PlanDrawMaterialAuthority.MaterialV5
    val structuralId: String
    val canonicalIdentity: String
    val uniformByteCountI64: Long
    val manifest = mutableListOf(W6FilterMaskShaderBindingAbiV1(0, W6FilterMaskShaderBindingKindV1.UniformBuffer))
    if (v4) {
        val footprint = RawMaterialRequirementsV2.measureV4(table, binding.material)
        structuralId = table.entry(binding.material).program.structuralId.value
        canonicalIdentity = RawMaterialRequirementsV2.canonicalIdentityV4(footprint)
        uniformByteCountI64 = footprint.uniformByteCountI64
        val proof = table.colorSourceProofV4(binding.material)
        proof.composedBindingLayout?.resources?.forEach { resource ->
            val kind = when (resource.kindTagU32) {
                1u -> W6FilterMaskShaderBindingKindV1.StorageBuffer
                2u -> W6FilterMaskShaderBindingKindV1.SampledTexture
                3u -> W6FilterMaskShaderBindingKindV1.Sampler
                else -> error("W6 MaskShader has an unsupported composed binding kind.")
            }
            manifest += W6FilterMaskShaderBindingAbiV1(resource.bindingI32, kind)
        }
        if (proof.composedBindingLayout == null) {
            proof.gradientStopSlab?.let {
                manifest += W6FilterMaskShaderBindingAbiV1(
                    proof.imageLayout?.gradientStorageBindingU32?.toInt() ?: 1,
                    W6FilterMaskShaderBindingKindV1.StorageBuffer,
                )
            }
            proof.imageLayout?.let { image ->
                manifest += W6FilterMaskShaderBindingAbiV1(
                    image.imageTextureBindingU32.toInt(),
                    W6FilterMaskShaderBindingKindV1.SampledTexture,
                )
            }
        }
    } else {
        val raw = RawMaterialRequirementsV2.measureLegacy(table, binding.material)
        structuralId = raw.structuralId
        // W5 lowers MaterialV1/V2 (including ImageSampleV3) through its legacy raw stage.
        // The published uniform row can be selected by a broader source-layout predicate,
        // but it is not the stage identity authority.
        canonicalIdentity = raw.canonicalIdentity
        uniformByteCountI64 = raw.uniformByteCountI64
        val image = raw.imageLayoutV3
        if (image != null) {
            image.gradientStorageBindingU32?.let { manifest += W6FilterMaskShaderBindingAbiV1(it.toInt(), W6FilterMaskShaderBindingKindV1.StorageBuffer) }
            manifest += W6FilterMaskShaderBindingAbiV1(image.imageTextureBindingU32.toInt(), W6FilterMaskShaderBindingKindV1.SampledTexture)
        } else if (w6FilterMaskShaderHasLegacyGradientV1(table, binding.material)) {
            manifest += W6FilterMaskShaderBindingAbiV1(1, W6FilterMaskShaderBindingKindV1.StorageBuffer)
        }
    }
    require(structuralId.isNotBlank() && uniformByteCountI64 >= 16L && manifest.map { it.bindingI32 }.distinct().size == manifest.size)
    return W6FilterMaskShaderMaterialAbiV1(structuralId, canonicalIdentity, uniformByteCountI64, manifest)
}

/** Mirrors W5's lowerer walk: an opacity wrapper retains its child gradient storage ABI. */
private fun w6FilterMaskShaderHasLegacyGradientV1(table: MaterialPlanTable, material: MaterialPlanRef): Boolean {
    var ref = material
    while (true) {
        when (table.entry(ref).bindings) {
            is MaterialBindingPlan.GradientV1 -> return true
            is MaterialBindingPlan.OpacityF32V1 -> {
                if (ref.indexI32 == 0) return false
                ref = MaterialPlanRef(ref.indexI32 - 1)
            }
            else -> return false
        }
    }
}

public fun frozenW6FilterBlendFormulaWgslV1(blend: BlendPlan, formula: W6FilterBlendFormulaV1): String {
    require(formula == W6FilterBlendFormulaV1.W5BlendFormulaV1)
    return requireNotNull(BlendFormulaProgramV1.selectedBlendFunctionWgsl(blend.w6FilterBlendModeLabelV1(), "w6d2_frozen_blend")) {
        "W6 Blend has no frozen formula."
    }
}

private fun BlendPlan.w6FilterBlendModeLabelV1(): String = when (this) {
    BlendPlan.LegacySrcOverV1 -> "src_over"; BlendPlan.NoOpV1 -> "dst"; is BlendPlan.FixedFunctionV1 -> mode.name.lowercase(); is BlendPlan.DestinationReadV1 -> mode.name.lowercase()
}

private fun W6GeometrySiteKeyV1.nativeSiteOwnerV1(): NativeSiteOwnerV1 =
    NativeSiteOwnerV1(ownerPassId, drawOrdinalI32, 0)

private fun W6PlainLayerCompositeRecipeV1.nativeSiteOwnerV1(): NativeSiteOwnerV1 =
    NativeSiteOwnerV1(site.ownerPassId, site.siteOrdinalI32, 0)

private fun W4eClipMaskInitializeRecipeV1.nativeSiteOwnerV1(): NativeSiteOwnerV1 =
    NativeSiteOwnerV1(passId, packetOrdinalI32, 0)

internal class NativeSiteEncodingWriterV1(family: NativeSiteRecipeFamilyV1) {
    private val fields = StringBuilder("native-site-recipe-v1")

    init {
        enum("family", family)
    }

    fun int(name: String, value: Int) { field(name, value.toString()) }
    fun long(name: String, value: Long) { field(name, value.toString()) }
    fun double(name: String, value: Double) { field(name, value.toRawBits().toULong().toString(16)) }
    fun float(name: String, value: Float) { field(name, value.toRawBits().toUInt().toString(16)) }
    fun text(name: String, value: String) { field(name, value) }
    fun enum(name: String, value: Enum<*>) { field(name, value.name) }
    fun blend(name: String, value: BlendPlan) {
        when (value) {
            BlendPlan.LegacySrcOverV1 -> text("$name.kind", "LegacySrcOverV1")
            BlendPlan.NoOpV1 -> text("$name.kind", "NoOpV1")
            is BlendPlan.FixedFunctionV1 -> {
                text("$name.kind", "FixedFunctionV1")
                enum("$name.mode", value.mode)
                enum("$name.colorSource", value.colorSource)
                enum("$name.colorDestination", value.colorDestination)
                enum("$name.alphaSource", value.alphaSource)
                enum("$name.alphaDestination", value.alphaDestination)
                enum("$name.operation", value.operation)
                enum("$name.coverage", value.coverage)
            }
            is BlendPlan.DestinationReadV1 -> {
                text("$name.kind", "DestinationReadV1")
                enum("$name.mode", value.mode)
                text("$name.formulaIdentity", value.formulaIdentity)
                enum("$name.coverage", value.coverage)
                long("$name.requiredDestinationVersion", value.requiredDestinationVersion.valueI64)
                value.snapshotResource?.let {
                    text("$name.snapshotResource.present", "true")
                    text("$name.snapshotResource.value", it.value)
                } ?: text("$name.snapshotResource.present", "false")
                int("$name.compositionAbi", value.compositionAbiI32)
            }
        }
    }
    fun selector(value: W6CorePrimitiveHostSelectorV1) {
        enum("geometry", value.family)
        enum("uniformAbi", value.uniformAbi)
        enum("targetFormat", value.target.format)
        int("sampleCount", value.target.sampleCountI32)
        blend("blend", value.blend)
        enum("coverage", value.coverage)
        enum("topology", value.topology)
        enum("coordinate", value.coordinateSlot)
        enum("groupZeroAbi", value.groupZeroAbi)
    }
    fun point(name: String, value: org.graphiks.math.geometry.Point2I32) {
        int("$name.x", value.x)
        int("$name.y", value.y)
    }
    fun color(name: String, value: W6CanonicalColorF32V1) {
        float("$name.red", value.redF32); float("$name.green", value.greenF32)
        float("$name.blue", value.blueF32); float("$name.alpha", value.alphaF32)
    }
    fun rectF32(name: String, value: org.graphiks.math.geometry.RectF32) {
        float("$name.left", value.left); float("$name.top", value.top)
        float("$name.right", value.right); float("$name.bottom", value.bottom)
    }
    fun rectF64(name: String, value: org.graphiks.math.geometry.RectF64) {
        double("$name.left", value.left); double("$name.top", value.top)
        double("$name.right", value.right); double("$name.bottom", value.bottom)
    }
    fun rect(name: String, value: org.graphiks.math.geometry.RectI32) {
        int("$name.left", value.left)
        int("$name.top", value.top)
        int("$name.right", value.right)
        int("$name.bottom", value.bottom)
    }
    fun finish(): String = fields.toString()

    private fun field(name: String, value: String) {
        fields.append('|').append(name.length).append(':').append(name)
        fields.append('=').append(value.length).append(':').append(value)
    }
}

internal fun nativeSiteEncodingV1(
    family: NativeSiteRecipeFamilyV1,
    block: NativeSiteEncodingWriterV1.() -> Unit,
): String = NativeSiteEncodingWriterV1(family).apply(block).finish()
