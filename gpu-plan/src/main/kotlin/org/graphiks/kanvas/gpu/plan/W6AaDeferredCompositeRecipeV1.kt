package org.graphiks.kanvas.gpu.plan

/** Fullscreen composition of a separately resolved geometric alpha and a sealed W5 solid. */
public class W6AaDeferredCompositeRecipeV1 internal constructor(
    public val pass: PlanPass.AaDeferredComposite,
    public val uniformResource: PlanResourceId,
    public val material: MaterialPlanRef,
    public val raw: RawMaterialRequirementsV2,
) : NativeSiteRecipeV1 {
    public val composite: PlanAaDeferredCompositeV1 = requireNotNull(pass.contract)
    public val coverageResource: PlanResourceId = composite.coverage.resources().single { it.role == PlanResourceRole.CoverageSource }.id
    override val owner: NativeSiteOwnerV1 = NativeSiteOwnerV1(pass.id, 0, 0)
    override val versionI32: Int = 1
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W6AaDeferredComposite
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", pass.id.value); int("command", composite.commandIndexI32)
        text("coverage", coverageResource.value); text("coverage.producer", composite.coverage.recipe.canonicalLogicalEncodingV1)
        text("coverage.channel", "ResolveAlphaUnorm8"); text("target", composite.target.value)
        int("width", composite.copyTargetExtentI32().width); int("height", composite.copyTargetExtentI32().height)
        rect("scissor", composite.copySourceBoundsTargetI32()); point("origin", composite.copyTargetOriginDeviceI32())
        composite.coordinateProvenance?.let { provenance ->
            rect("coordinate.source-raster-local-domain", provenance.copySourceRasterDomainLocalI32())
            rect("coordinate.target-local-domain", provenance.copyTargetLocalDomainI32())
            rect("coordinate.source-device-domain", provenance.copySourceDeviceDomainI32())
            point("coordinate.source-raster-local-origin", provenance.copySourceRasterOriginLocalI32())
            point("coordinate.owner-device-origin", provenance.copyOwnerOriginDeviceI32())
        }
        text("mapping.present", (composite.mapping != null).toString())
        composite.mapping?.let { mapping ->
            point("mapping.origin", mapping.copyLayerOriginDeviceI32())
            listOf(mapping.copyLocalToDeviceF64(), mapping.copyDeviceToLayerF64(), mapping.copyLocalToLayerF64())
                .forEachIndexed { index, matrix ->
                    listOf(matrix.sxF64, matrix.kxF64, matrix.txF64, matrix.kyF64, matrix.syF64, matrix.tyF64,
                        matrix.persp0F64, matrix.persp1F64, matrix.persp2F64).forEachIndexed { coefficient, value ->
                        double("mapping.$index.$coefficient", value)
                    }
                }
        }
        text("target.format", "RGBA8_UNORM_SRGB_LINEAR_PREMUL"); int("samples", 1)
        text("target.clamp", "NormalizedAttachment"); text("abi", "coverage0-material1-destination2")
        int("material", material.indexI32); text("material.structural", raw.structuralId)
        text("material.coordinates", (composite.sourceDraw.materialAuthority as PlanDrawMaterialAuthority.MaterialV1).coordinates?.canonicalIdentity.orEmpty())
        text("material.canonical", raw.canonicalIdentity); text("uniform", uniformResource.value)
        long("uniform.bytes", raw.uniformByteCountI64); blend("blend", composite.blend)
        text("coverage.law", (composite.blend as? BlendPlan.DestinationReadV1)?.coverageLaw?.name ?: "SourceMultiplication")
        long("version.before", composite.destinationVersionBefore.valueI64)
        long("version.after", composite.destinationVersionAfter.valueI64)
    }
}

public fun freezeW6AaDeferredCompositeRecipesV1(
    passes: List<PlanPass>, table: MaterialPlanTable?, resources: List<PlanResource>,
    uniforms: Map<String, PlanResourceId>,
): List<W6AaDeferredCompositeRecipeV1> = passes.filterIsInstance<PlanPass.AaDeferredComposite>().map { pass ->
    val composite = requireNotNull(pass.contract)
    require(composite.coverage is PlanW4dAaCoverageSourceBindingV1 ||
        composite.coverage is PlanW4eInverseAaCoverageSourceBindingV1)
    require(when (val coverageBinding = composite.coverage) {
        is PlanW4dAaCoverageSourceBindingV1 -> coverageBinding.recipe.family == NativeSiteRecipeFamilyV1.W4dAaCoverageSource
        is PlanW4eInverseAaCoverageSourceBindingV1 -> coverageBinding.sourceCapabilityId ==
            W4eClipPlanCompiler.W7_INVERSE_AA_COVERAGE_SOURCE_CAPABILITY_ID &&
            coverageBinding.recipe.family == NativeSiteRecipeFamilyV1.W4eInverseAaCoverageSource
    }) { "W7 deferred composite lost its variant-specific coverage authority" }
    val coverageBinding = composite.coverage as? PlanW4eInverseAaCoverageSourceBindingV1
    if (coverageBinding != null) {
        require(composite.coordinateProvenance === coverageBinding.coordinateProvenance() &&
            composite.coordinateProvenance.matches(composite.copyTargetExtentI32(),
                composite.copySourceBoundsTargetI32(), coverageBinding.sourcePayload().copyOriginDeviceI32(),
                composite.copyTargetOriginDeviceI32(), composite.mapping)) {
            "W7 deferred composite recipe lost its binding target-local/device provenance"
        }
    }
    val materialTable = requireNotNull(table)
    val authority = composite.sourceDraw.materialAuthority as? PlanDrawMaterialAuthority.MaterialV1
        ?: error("W7 deferred consumer requires the selected solid MaterialV1 authority")
    var leaf = authority.ref
    while (materialTable.entry(leaf).bindings is MaterialBindingPlan.OpacityF32V1) {
        require(leaf.indexI32 > 0)
        leaf = MaterialPlanRef(leaf.indexI32 - 1)
    }
    val selected = materialTable.entry(leaf)
    require(
        (selected.program == MaterialProgramPlan.SolidLinearPremulV1 && selected.bindings is MaterialBindingPlan.SolidRgbaF32V1) ||
            (selected.program == MaterialProgramPlan.TransparentV1 && selected.bindings is MaterialBindingPlan.EmptyV1),
    ) { "W7 consumer lost its selected solid LINEAR or normalized transparent material" }
    val raw = RawMaterialRequirementsV2.of(materialTable, authority.ref)
    val uniform = resources.single { it.id == uniforms.getValue(raw.canonicalIdentity) }
    require(uniform.role == PlanResourceRole.SourceUniformData && uniform.kind == PlanResourceKind.Buffer &&
        uniform.byteSize == raw.uniformByteCountI64 && uniform.usages() == setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination)) { "W7 material uniform row differs from its raw requirements" }
    val target = resources.single { it.id == composite.target }
    val coverage = composite.coverage.resources().single { it.role == PlanResourceRole.CoverageSource }
    require(target.sampleCountI32 == 1 && coverage.sampleCountI32 == 1 && target.id != coverage.id &&
        target.format == PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL) &&
        coverage.format == target.format && target.copyExtent() == composite.copyTargetExtentI32() &&
        coverage.copyExtent() == target.copyExtent() && PlanResourceUsage.RenderAttachment in target.usages() &&
        PlanResourceUsage.Sampled in coverage.usages()) { "W7 consumer target/resolve texture contract diverged" }
    composite.destinationSnapshot?.let { id ->
        val snapshot = resources.single { it.id == id }
        val copy = passes[passes.indexOf(pass) - 1] as? PlanPass.TextureCopy
            ?: error("W7 destination snapshot must immediately precede its consumer")
        require(snapshot.role == PlanResourceRole.DestinationSnapshot && snapshot.sampleCountI32 == 1 &&
            snapshot.format == target.format && snapshot.copyExtent() == target.copyExtent() &&
            PlanResourceUsage.Sampled in snapshot.usages() && copy.source == target.id && copy.destination == id &&
            copy.destinationVersion == composite.destinationVersionBefore &&
            copy.copyDestinationOriginI32() == org.graphiks.math.geometry.Point2I32.Origin &&
            copy.copySourceBoundsI32() == org.graphiks.math.geometry.RectI32(0, 0,
                composite.copyTargetExtentI32().width, composite.copyTargetExtentI32().height)) { "W7 snapshot does not capture the preceding target version in target-local coordinates" }
    }
    W6AaDeferredCompositeRecipeV1(pass, uniform.id, authority.ref, raw)
}
