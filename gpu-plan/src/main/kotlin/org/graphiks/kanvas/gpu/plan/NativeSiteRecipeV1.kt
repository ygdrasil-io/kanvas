package org.graphiks.kanvas.gpu.plan

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
        clipMaskInitializes: Map<PlanPassId, W4eClipMaskInitializeRecipeV1>,
        coverageRasters: Map<PlanPassId, W6bCoverageRasterHostRecipeV1>,
        fullscreenEmpties: Map<PlanPassId, W6FullscreenEmptyRecipeV1>,
        coverageAlphas: Map<PlanPassId, W6FullscreenCoverageAlphaRecipeV1>,
        coverageSolidRects: Map<PlanPassId, W6FullscreenCoverageSolidRectRecipeV1>,
    ): Boolean = orderedRecipes.all { recipe ->
        when (recipe) {
            is W6SolidRectNativeSiteRecipeV1 -> solidRects[recipe.host.site] === recipe.host
            is W6CorePrimitiveNativeSiteRecipeV1 -> corePrimitives[recipe.host.site] === recipe.host
            is W6PreparedVerticesNativeSiteRecipeV1 -> preparedVertices[recipe.host.site] === recipe.host
            is W6PlainLayerCompositeNativeSiteRecipeV1 -> plainLayerComposites[recipe.host.site] === recipe.host
            is W4eClipMaskInitializeNativeSiteRecipeV1 -> clipMaskInitializes[recipe.host.passId] === recipe.host
            is W6bCoverageRasterNativeSiteRecipeV1 -> coverageRasters[recipe.host.ownerPassId]?.bundle(recipe.host.bundleOrdinalI32) === recipe.host
            is W6FullscreenEmptyNativeSiteRecipeV1 -> fullscreenEmpties[recipe.host.ownerPassId] === recipe.host
            is W6FullscreenCoverageAlphaNativeSiteRecipeV1 -> coverageAlphas[recipe.host.ownerPassId] === recipe.host
            is W6FullscreenCoverageSolidRectNativeSiteRecipeV1 -> coverageSolidRects[recipe.host.ownerPassId] === recipe.host
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
    clipMaskInitializes: Map<PlanPassId, W4eClipMaskInitializeRecipeV1>,
    coverageRasters: Map<PlanPassId, W6bCoverageRasterHostRecipeV1>,
    fullscreenEmpties: Map<PlanPassId, W6FullscreenEmptyRecipeV1> = emptyMap(),
    coverageAlphas: Map<PlanPassId, W6FullscreenCoverageAlphaRecipeV1> = emptyMap(),
    coverageSolidRects: Map<PlanPassId, W6FullscreenCoverageSolidRectRecipeV1> = emptyMap(),
): NativeSiteRecipeCatalogV1 = NativeSiteRecipeCatalogV1(buildList {
    solidRects.forEach { (site, recipe) -> require(site == recipe.site) }
    corePrimitives.forEach { (site, recipe) -> require(site == recipe.site) }
    preparedVertices.forEach { (site, recipe) -> require(site == recipe.site) }
    plainLayerComposites.forEach { (site, recipe) -> require(site == recipe.site) }
    clipMaskInitializes.forEach { (passId, recipe) -> require(passId == recipe.passId) }
    coverageRasters.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    fullscreenEmpties.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    coverageAlphas.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    coverageSolidRects.forEach { (passId, recipe) -> require(passId == recipe.ownerPassId) }
    val remainingSolidRects = solidRects.toMutableMap()
    val remainingCorePrimitives = corePrimitives.toMutableMap()
    val remainingPreparedVertices = preparedVertices.toMutableMap()
    val remainingPlainComposites = plainLayerComposites.toMutableMap()
    val remainingClipInitializes = clipMaskInitializes.toMutableMap()
    val remainingCoverageRasters = coverageRasters.toMutableMap()
    val remainingEmpties = fullscreenEmpties.toMutableMap()
    val remainingCoverageAlphas = coverageAlphas.toMutableMap()
    val remainingCoverageSolidRects = coverageSolidRects.toMutableMap()
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
            is PlanPass.PictureAggregateBeginPass, is PlanPass.FilterSourceClear,
            is PlanPass.PictureAggregateSealPass, is PlanPass.PictureComposite,
            is PlanPass.FilterComposite -> remainingEmpties.remove(pass.id)?.let { add(W6FullscreenEmptyNativeSiteRecipeV1(it)) }
            else -> Unit
        }
    }
    require(remainingSolidRects.isEmpty() && remainingCorePrimitives.isEmpty() && remainingPreparedVertices.isEmpty() &&
        remainingPlainComposites.isEmpty() && remainingClipInitializes.isEmpty() && remainingCoverageRasters.isEmpty() &&
        remainingEmpties.isEmpty() && remainingCoverageAlphas.isEmpty() && remainingCoverageSolidRects.isEmpty()) {
        "Native-site recipes must all be owned by final planner passes."
    }
})

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

private fun W6GeometrySiteKeyV1.nativeSiteOwnerV1(): NativeSiteOwnerV1 =
    NativeSiteOwnerV1(ownerPassId, drawOrdinalI32, 0)

private fun W6PlainLayerCompositeRecipeV1.nativeSiteOwnerV1(): NativeSiteOwnerV1 =
    NativeSiteOwnerV1(site.ownerPassId, site.siteOrdinalI32, 0)

private fun W4eClipMaskInitializeRecipeV1.nativeSiteOwnerV1(): NativeSiteOwnerV1 =
    NativeSiteOwnerV1(passId, packetOrdinalI32, 0)

private class NativeSiteEncodingWriterV1(family: NativeSiteRecipeFamilyV1) {
    private val fields = StringBuilder("native-site-recipe-v1")

    init {
        enum("family", family)
    }

    fun int(name: String, value: Int) { field(name, value.toString()) }
    fun long(name: String, value: Long) { field(name, value.toString()) }
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

private fun nativeSiteEncodingV1(
    family: NativeSiteRecipeFamilyV1,
    block: NativeSiteEncodingWriterV1.() -> Unit,
): String = NativeSiteEncodingWriterV1(family).apply(block).finish()
