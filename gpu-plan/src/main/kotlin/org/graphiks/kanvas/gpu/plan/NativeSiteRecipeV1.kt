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
    ): Boolean = orderedRecipes.all { recipe ->
        when (recipe) {
            is W6SolidRectNativeSiteRecipeV1 -> solidRects[recipe.host.site] === recipe.host
            is W6CorePrimitiveNativeSiteRecipeV1 -> corePrimitives[recipe.host.site] === recipe.host
            is W6PreparedVerticesNativeSiteRecipeV1 -> preparedVertices[recipe.host.site] === recipe.host
            is W6PlainLayerCompositeNativeSiteRecipeV1 -> plainLayerComposites[recipe.host.site] === recipe.host
            is W4eClipMaskInitializeNativeSiteRecipeV1 -> clipMaskInitializes[recipe.host.passId] === recipe.host
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
): NativeSiteRecipeCatalogV1 = NativeSiteRecipeCatalogV1(buildList {
    solidRects.forEach { (site, recipe) -> require(site == recipe.site) }
    corePrimitives.forEach { (site, recipe) -> require(site == recipe.site) }
    preparedVertices.forEach { (site, recipe) -> require(site == recipe.site) }
    plainLayerComposites.forEach { (site, recipe) -> require(site == recipe.site) }
    clipMaskInitializes.forEach { (passId, recipe) -> require(passId == recipe.passId) }
    val remainingSolidRects = solidRects.toMutableMap()
    val remainingCorePrimitives = corePrimitives.toMutableMap()
    val remainingPreparedVertices = preparedVertices.toMutableMap()
    val remainingPlainComposites = plainLayerComposites.toMutableMap()
    val remainingClipInitializes = clipMaskInitializes.toMutableMap()
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
            else -> Unit
        }
    }
    require(remainingSolidRects.isEmpty() && remainingCorePrimitives.isEmpty() && remainingPreparedVertices.isEmpty() &&
        remainingPlainComposites.isEmpty() && remainingClipInitializes.isEmpty()) {
        "Native-site recipes must all be owned by final planner passes."
    }
})

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
                value.snapshotResource?.let { text("$name.snapshotResource", it.value) }
                    ?: text("$name.snapshotResource", "none")
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
