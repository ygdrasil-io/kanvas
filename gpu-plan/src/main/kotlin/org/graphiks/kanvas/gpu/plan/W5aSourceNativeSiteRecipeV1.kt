package org.graphiks.kanvas.gpu.plan

/**
 * The bounded 2A0d ordinary W5a source site.  This deliberately admits only a final ordinary
 * SolidRect backed by one explicitly admitted W5 MaterialV1 program: no coverage source,
 * destination read, W4e packet, image/noise/runtime binding, or renderer-owned handle is present.
 */
public enum class W5aSourceNativeVariantV1 {
    OrdinarySolidMaterialV1,
    OrdinaryLinearGradientMaterialV1,
    OrdinaryRadialGradientMaterialV1,
    OrdinarySweepGradientMaterialV1,
    OrdinaryConicalGradientMaterialV1,
    OrdinarySolidOpacityMaterialV1,
    OrdinaryLinearGradientOpacityMaterialV1,
    OrdinaryRadialGradientOpacityMaterialV1,
    OrdinarySweepGradientOpacityMaterialV1,
    OrdinaryConicalGradientOpacityMaterialV1,
}
public enum class W5aSourceNativeBindingKindV1 { UniformBuffer, StorageBuffer }

/** Closed-world admission for the only W5a source variants that consume the stop slab. */
public fun W5aSourceNativeVariantV1.isW5aGradientSourceVariantV1(): Boolean = when (this) {
    W5aSourceNativeVariantV1.OrdinaryLinearGradientMaterialV1,
    W5aSourceNativeVariantV1.OrdinaryRadialGradientMaterialV1,
    W5aSourceNativeVariantV1.OrdinarySweepGradientMaterialV1,
    W5aSourceNativeVariantV1.OrdinaryConicalGradientMaterialV1,
    W5aSourceNativeVariantV1.OrdinaryLinearGradientOpacityMaterialV1,
    W5aSourceNativeVariantV1.OrdinaryRadialGradientOpacityMaterialV1,
    W5aSourceNativeVariantV1.OrdinarySweepGradientOpacityMaterialV1,
    W5aSourceNativeVariantV1.OrdinaryConicalGradientOpacityMaterialV1 -> true
    W5aSourceNativeVariantV1.OrdinarySolidMaterialV1,
    W5aSourceNativeVariantV1.OrdinarySolidOpacityMaterialV1 -> false
}

private fun W5aSourceNativeVariantV1.leafProgramV1(): MaterialProgramPlan = when (this) {
    W5aSourceNativeVariantV1.OrdinarySolidMaterialV1, W5aSourceNativeVariantV1.OrdinarySolidOpacityMaterialV1 -> MaterialProgramPlan.SolidLinearPremulV1
    W5aSourceNativeVariantV1.OrdinaryLinearGradientMaterialV1, W5aSourceNativeVariantV1.OrdinaryLinearGradientOpacityMaterialV1 -> MaterialProgramPlan.LinearGradientClampSrgbV1
    W5aSourceNativeVariantV1.OrdinaryRadialGradientMaterialV1, W5aSourceNativeVariantV1.OrdinaryRadialGradientOpacityMaterialV1 -> MaterialProgramPlan.RadialGradientClampSrgbV1
    W5aSourceNativeVariantV1.OrdinarySweepGradientMaterialV1, W5aSourceNativeVariantV1.OrdinarySweepGradientOpacityMaterialV1 -> MaterialProgramPlan.SweepGradientClampSrgbV1
    W5aSourceNativeVariantV1.OrdinaryConicalGradientMaterialV1, W5aSourceNativeVariantV1.OrdinaryConicalGradientOpacityMaterialV1 -> MaterialProgramPlan.ConicalGradientClampSrgbV1
}

private fun W5aSourceNativeVariantV1.hasOpacityV1(): Boolean = when (this) {
    W5aSourceNativeVariantV1.OrdinarySolidOpacityMaterialV1,
    W5aSourceNativeVariantV1.OrdinaryLinearGradientOpacityMaterialV1,
    W5aSourceNativeVariantV1.OrdinaryRadialGradientOpacityMaterialV1,
    W5aSourceNativeVariantV1.OrdinarySweepGradientOpacityMaterialV1,
    W5aSourceNativeVariantV1.OrdinaryConicalGradientOpacityMaterialV1 -> true
    else -> false
}

public data class W5aSourceNativeBindingAbiV1(
    public val bindingI32: Int,
    public val kind: W5aSourceNativeBindingKindV1,
) { init { require(bindingI32 >= 0) } }

public class W5aSourceNativeSiteRecipeV1 internal constructor(
    public val ownerPassId: PlanPassId,
    public val drawOrdinalI32: Int,
    public val bundleOrdinalI32: Int,
    public val commandIndexI32: Int,
    public val material: MaterialPlanRef,
    /** Table-program identity, distinct from the endpoint-specialized Raw structural ID below. */
    public val materialProgramStructuralId: String,
    public val leafMaterialProgramStructuralId: String,
    public val opacityBindingCountI32: Int,
    public val materialStructuralId: String,
    public val materialCanonicalIdentity: String,
    public val uniformResource: PlanResourceId,
    public val uniformByteCountI64: Long,
    uniformBytes: ByteArray,
    /** The V1 gradient slab is one already-published frame resource, never a new allocation. */
    public val gradientStopResource: PlanResourceId?,
    public val gradientStopByteCountI64: Long,
    public val gradientStopCanonicalIdentity: String,
    /** V1 coordinates are material ABI, rather than a late renderer choice. */
    public val materialCoordinateCanonicalIdentity: String,
    bindingManifest: List<W5aSourceNativeBindingAbiV1>,
    public val geometryOwner: NativeSiteOwnerV1,
    public val geometryCanonicalEncodingV1: String,
    public val targetFormat: W6SolidRectTargetFormatV1,
    public val targetSampleCountI32: Int,
    public val blend: BlendPlan,
    public val variant: W5aSourceNativeVariantV1 = W5aSourceNativeVariantV1.OrdinarySolidMaterialV1,
) {
    private val frozenUniformBytes = uniformBytes.copyOf()
    private val frozenBindingManifest = java.util.Collections.unmodifiableList(bindingManifest.toList())
    init {
        require(drawOrdinalI32 >= 0 && bundleOrdinalI32 >= 1 && commandIndexI32 >= 0) {
            "W5a source owner is invalid: pass=${ownerPassId.value}, draw=$drawOrdinalI32, bundle=$bundleOrdinalI32, command=$commandIndexI32"
        }
        // Raw requirements own the structural ID, including their endpoint ABI suffix.
        require(materialStructuralId.isNotBlank()) {
            "W5a source structural ID from raw material requirements is blank"
        }
        require(materialCanonicalIdentity.isNotBlank()) { "W5a source canonical identity is blank" }
        require(frozenUniformBytes.size.toLong() == uniformByteCountI64) {
            "W5a source uniform bytes disagree with its frozen byte count"
        }
        val leaf = variant.leafProgramV1()
        var expectedProgram = leaf
        repeat(opacityBindingCountI32) { expectedProgram = MaterialProgramPlan.OpacityV1(expectedProgram) }
        val baseBytes = when (leaf) {
            MaterialProgramPlan.SolidLinearPremulV1 -> 16L
            MaterialProgramPlan.LinearGradientClampSrgbV1 -> 128L
            MaterialProgramPlan.RadialGradientClampSrgbV1 -> 96L
            MaterialProgramPlan.SweepGradientClampSrgbV1 -> 112L
            MaterialProgramPlan.ConicalGradientClampSrgbV1 -> 192L
            else -> error("Unadmitted W5a source leaf")
        }
        require(materialProgramStructuralId == expectedProgram.structuralId.value &&
            leafMaterialProgramStructuralId == leaf.structuralId.value &&
            opacityBindingCountI32 >= 0 && (opacityBindingCountI32 > 0) == variant.hasOpacityV1() &&
            uniformByteCountI64 == baseBytes + opacityBindingCountI32 * 16L &&
            (if (variant.isW5aGradientSourceVariantV1()) gradientStopResource != null && gradientStopByteCountI64 > 0L &&
                gradientStopCanonicalIdentity.isNotBlank() && materialCoordinateCanonicalIdentity.isNotBlank() &&
                frozenBindingManifest == listOf(W5aSourceNativeBindingAbiV1(0, W5aSourceNativeBindingKindV1.UniformBuffer), W5aSourceNativeBindingAbiV1(1, W5aSourceNativeBindingKindV1.StorageBuffer))
            else gradientStopResource == null && gradientStopByteCountI64 == 0L && gradientStopCanonicalIdentity.isEmpty() &&
                materialCoordinateCanonicalIdentity.isEmpty() && frozenBindingManifest == listOf(W5aSourceNativeBindingAbiV1(0, W5aSourceNativeBindingKindV1.UniformBuffer)))) {
            "W5a ordinary legacy source ABI changed"
        }
        require(geometryCanonicalEncodingV1.isNotBlank() && targetFormat == W6SolidRectTargetFormatV1.RGBA8UnormSrgb && targetSampleCountI32 == 1) {
            "W5a source geometry target is not the admitted RGBA8UnormSrgb single-sample SolidRect"
        }
        require(geometryOwner.ownerPassId == ownerPassId && geometryOwner.drawOrPacketOrdinalI32 == drawOrdinalI32) {
            "W5a source geometry owner is not the final SolidRect site"
        }
    }
    public val owner: NativeSiteOwnerV1 get() = NativeSiteOwnerV1(ownerPassId, drawOrdinalI32, bundleOrdinalI32)
    public fun copyUniformBytes(): ByteArray = frozenUniformBytes.copyOf()
    public fun bindingManifest(): List<W5aSourceNativeBindingAbiV1> = frozenBindingManifest
    public fun canonicalLogicalEncodingV1(): String = W5aSourceNativeSiteNativeRecipeV1(this).canonicalLogicalEncodingV1
}

public class W5aSourceNativeSiteNativeRecipeV1 internal constructor(
    public val host: W5aSourceNativeSiteRecipeV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = host.owner
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W5aOrdinarySolidSource
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", host.ownerPassId.value); int("draw", host.drawOrdinalI32); int("bundle", host.bundleOrdinalI32)
        int("command", host.commandIndexI32); int("material", host.material.indexI32)
        text("program.structural", host.materialProgramStructuralId)
        text("program.leaf", host.leafMaterialProgramStructuralId); int("opacity.count", host.opacityBindingCountI32)
        text("raw.structural", host.materialStructuralId); text("canonical", host.materialCanonicalIdentity)
        text("uniform.resource", host.uniformResource.value); long("uniform.bytes", host.uniformByteCountI64)
        text("uniform.data", host.copyUniformBytes().joinToString("") { "%02x".format(it.toInt() and 0xff) })
        host.gradientStopResource?.let { text("gradient.resource", it.value) }
        long("gradient.bytes", host.gradientStopByteCountI64); text("gradient.canonical", host.gradientStopCanonicalIdentity)
        text("coordinates.canonical", host.materialCoordinateCanonicalIdentity)
        text("geometry.owner", host.geometryOwner.ownerPassId.value); int("geometry.draw", host.geometryOwner.drawOrPacketOrdinalI32); int("geometry.bundle", host.geometryOwner.bundleOrdinalI32)
        text("geometry.canonical", host.geometryCanonicalEncodingV1); enum("target.format", host.targetFormat); int("target.samples", host.targetSampleCountI32)
        blend("blend", host.blend); enum("variant", host.variant)
        host.bindingManifest().forEachIndexed { index, binding -> int("binding.$index.index", binding.bindingI32); enum("binding.$index.kind", binding.kind) }
    }
}

/** The admitted source-native forms remain explicit legacy MaterialV1 ABIs, never a catch-all gradient route. */
public fun w5aOrdinarySourceNativeVariantV1OrNull(
    table: MaterialPlanTable?,
    pass: PlanPass.RenderPass,
    draw: PlanDraw,
): W5aSourceNativeVariantV1? {
    if (pass.w6bMaskSourceBinding != null || draw !is SolidRectDraw || draw.blend is BlendPlan.DestinationReadV1) return null
    val authority = draw.materialAuthority as? PlanDrawMaterialAuthority.MaterialV1 ?: return null
    val materialTable = table ?: return null
    var leaf = authority.ref
    var opacityCount = 0
    while (materialTable.entry(leaf).bindings is MaterialBindingPlan.OpacityF32V1) {
        if (leaf.indexI32 == 0) return null
        leaf = MaterialPlanRef(leaf.indexI32 - 1)
        opacityCount++
    }
    val entry = materialTable.entry(leaf)
    val base = when {
        entry.program == MaterialProgramPlan.SolidLinearPremulV1 && entry.bindings is MaterialBindingPlan.SolidRgbaF32V1 ->
            W5aSourceNativeVariantV1.OrdinarySolidMaterialV1
        entry.program == MaterialProgramPlan.LinearGradientClampSrgbV1 && entry.bindings is MaterialBindingPlan.LinearGradientV1 && authority.coordinates != null ->
            W5aSourceNativeVariantV1.OrdinaryLinearGradientMaterialV1
        entry.program == MaterialProgramPlan.RadialGradientClampSrgbV1 && entry.bindings is MaterialBindingPlan.RadialGradientV1 && authority.coordinates != null ->
            W5aSourceNativeVariantV1.OrdinaryRadialGradientMaterialV1
        entry.program == MaterialProgramPlan.SweepGradientClampSrgbV1 && entry.bindings is MaterialBindingPlan.SweepGradientV1 && authority.coordinates != null ->
            W5aSourceNativeVariantV1.OrdinarySweepGradientMaterialV1
        entry.program == MaterialProgramPlan.ConicalGradientClampSrgbV1 && entry.bindings is MaterialBindingPlan.ConicalGradientV1 && authority.coordinates != null ->
            W5aSourceNativeVariantV1.OrdinaryConicalGradientMaterialV1
        else -> return null
    }
    if (opacityCount == 0) return base
    return when (base) {
        W5aSourceNativeVariantV1.OrdinarySolidMaterialV1 -> W5aSourceNativeVariantV1.OrdinarySolidOpacityMaterialV1
        W5aSourceNativeVariantV1.OrdinaryLinearGradientMaterialV1 -> W5aSourceNativeVariantV1.OrdinaryLinearGradientOpacityMaterialV1
        W5aSourceNativeVariantV1.OrdinaryRadialGradientMaterialV1 -> W5aSourceNativeVariantV1.OrdinaryRadialGradientOpacityMaterialV1
        W5aSourceNativeVariantV1.OrdinarySweepGradientMaterialV1 -> W5aSourceNativeVariantV1.OrdinarySweepGradientOpacityMaterialV1
        W5aSourceNativeVariantV1.OrdinaryConicalGradientMaterialV1 -> W5aSourceNativeVariantV1.OrdinaryConicalGradientOpacityMaterialV1
        else -> error("W5a ordinary source variant has unexpected opacity")
    }
}

public fun freezeW5aSourceNativeSiteRecipesV1(
    passes: List<PlanPass>,
    table: MaterialPlanTable?,
    resources: List<PlanResource>,
    uniforms: Map<String, PlanResourceId>,
    solidHosts: Map<W6GeometrySiteKeyV1, W6SolidRectHostRecipeV1>,
): Map<NativeSiteOwnerV1, W5aSourceNativeSiteRecipeV1> {
    val materialTable = table ?: return emptyMap()
    val rows = linkedMapOf<NativeSiteOwnerV1, W5aSourceNativeSiteRecipeV1>()
    passes.filterIsInstance<PlanPass.RenderPass>().forEach { pass ->
        pass.draws().forEachIndexed { ordinal, draw ->
            val variant = w5aOrdinarySourceNativeVariantV1OrNull(materialTable, pass, draw) ?: return@forEachIndexed
            val solid = draw as SolidRectDraw
            val authority = solid.materialAuthority as PlanDrawMaterialAuthority.MaterialV1
            val entry = materialTable.entry(authority.ref)
            val raw = RawMaterialRequirementsV2.of(materialTable, authority.ref)
            var leaf = authority.ref
            var opacityCount = 0
            while (materialTable.entry(leaf).bindings is MaterialBindingPlan.OpacityF32V1) {
                leaf = MaterialPlanRef(leaf.indexI32 - 1); opacityCount++
            }
            val leafEntry = materialTable.entry(leaf)
            val gradient = leafEntry.bindings as? MaterialBindingPlan.GradientV1
            val slab = materialTable.gradientStopSlab
            require(raw.bindingCountI32 == opacityCount + 1 && !raw.hasCoordinatesV2 &&
                (if (variant.isW5aGradientSourceVariantV1()) gradient != null && slab != null && authority.coordinates != null else gradient == null)) {
                "w5a ordinary source raw MaterialV1 ABI is not admitted"
            }
            val uniform = uniforms.getValue(raw.canonicalIdentity)
            val row = resources.single { it.id == uniform }
            require(row.role == PlanResourceRole.SourceUniformData && row.byteSize == raw.uniformByteCountI64 &&
                row.usages() == setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination)) { "w5a ordinary source uniform row changed after source layout" }
            val stopResource = if (variant.isW5aGradientSourceVariantV1()) resources.single {
                it.role == PlanResourceRole.GradientStopData
            }.also { stop ->
                require(stop.byteSize == requireNotNull(slab).byteSizeI64 &&
                    stop.usages() == setOf(PlanResourceUsage.StorageRead, PlanResourceUsage.CopyDestination)) {
                    "w5a ordinary gradient stop row changed after source layout"
                }
            } else null
            val geometry = solidHosts.getValue(W6GeometrySiteKeyV1(pass.id, ordinal))
            require(geometry.colorMode is W6SolidRectColorModeV1.UniformColor16 && geometry.blend == solid.blend) { "w5a ordinary source geometry host no longer matches SolidRect" }
            val baseOwners = solidHosts.keys.asSequence()
                .filter { site -> site.ownerPassId == pass.id && site.drawOrdinalI32 == ordinal }
                .map { site -> NativeSiteOwnerV1(site.ownerPassId, site.drawOrdinalI32, 0) }
                .sortedBy(NativeSiteOwnerV1::bundleOrdinalI32)
                .toList()
            require(baseOwners.map(NativeSiteOwnerV1::bundleOrdinalI32).sorted() == (0 until baseOwners.size).toList()) { "w5a ordinary source geometry catalog is not dense" }
            val geometryOwner = baseOwners.single()
            val recipe = W5aSourceNativeSiteRecipeV1(pass.id, ordinal, baseOwners.size, solid.commandIndex, authority.ref,
                entry.program.structuralId.value, leafEntry.program.structuralId.value, opacityCount, raw.structuralId, raw.canonicalIdentity, uniform, raw.uniformByteCountI64, raw.copyUniformBytes(),
                stopResource?.id, stopResource?.byteSize ?: 0L,
                if (variant.isW5aGradientSourceVariantV1()) requireNotNull(slab).canonicalIdentity else "",
                if (variant.isW5aGradientSourceVariantV1())
                    authority.coordinates?.canonicalIdentity.orEmpty() else "",
                if (variant.isW5aGradientSourceVariantV1()) listOf(
                    W5aSourceNativeBindingAbiV1(0, W5aSourceNativeBindingKindV1.UniformBuffer),
                    W5aSourceNativeBindingAbiV1(1, W5aSourceNativeBindingKindV1.StorageBuffer),
                ) else listOf(W5aSourceNativeBindingAbiV1(0, W5aSourceNativeBindingKindV1.UniformBuffer)),
                geometryOwner, W6SolidRectNativeSiteRecipeV1(geometry).canonicalLogicalEncodingV1,
                geometry.target.format, geometry.target.sampleCountI32, solid.blend, variant)
            require(rows.put(recipe.owner, recipe) == null) {
                "W5a source recipe owner is not unique: ${recipe.ownerPassId.value}/${recipe.drawOrdinalI32}/${recipe.bundleOrdinalI32}"
            }
        }
    }
    return java.util.Collections.unmodifiableMap(LinkedHashMap(rows))
}
