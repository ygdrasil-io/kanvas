package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.SizeI32

/**
 * Closed W4d-to-W6 handoff for one opaque-white AA coverage occurrence.
 *
 * The binding deliberately owns no paint authority: its resolve alpha is geometric coverage C,
 * and the original W5 material is evaluated later over the whole filtered domain.
 */
public class PlanW4dAaCoverageSourceBindingV1 internal constructor(
    public val sourceCapabilityId: String,
    public val commandIndexI32: Int,
    sourcePassIds: List<PlanPassId>,
    passes: List<PlanPass.PathRenderPass>,
    resourceRemapping: Map<PlanResourceId, PlanResourceId>,
    resources: List<PlanResource>,
    extent: SizeI32,
    origin: Point2I32,
) {
    private val sourceIds = immutableList(sourcePassIds)
    private val phases = immutableList(passes)
    private val remapping = java.util.Collections.unmodifiableMap(resourceRemapping.toMap())
    private val rows = immutableList(resources)
    private val extentSnapshot = extent.copy()
    private val originSnapshot = Point2I32(origin.x, origin.y)
    public val recipe: W4dAaCoverageSourceNativeSiteRecipeV1
    public fun sourcePassIds(): List<PlanPassId> = sourceIds
    public fun passes(): List<PlanPass.PathRenderPass> = phases
    public fun resourceRemapping(): Map<PlanResourceId, PlanResourceId> = remapping
    public fun resources(): List<PlanResource> = rows
    public fun copyExtentI32(): SizeI32 = extentSnapshot.copy()
    public fun copyOriginDeviceI32(): Point2I32 = Point2I32(originSnapshot.x, originSnapshot.y)

    init {
        require(sourceCapabilityId == W4dGeneralPathPlanCompiler.W6_AA_COVERAGE_SOURCE_CAPABILITY_ID)
        require(sourceIds.size == 1 && phases.size == 1 && sourceIds.single() == phases.single().id)
        val direct = phases.single()
        require(direct.phase == PathRenderPhase.MultisampleDirectColor &&
            direct.draw is GeneralPathDraw && direct.draw.commandIndex == commandIndexI32 &&
            direct.draw.strategy == PathFillStrategy.DirectTriangle && direct.draw.coverage == CoveragePlan.StencilAA4 &&
            direct.draw.sample == SamplePlan.Multisample4 && direct.draw.blend == BlendPlan.SrcOver &&
            direct.depthStencil == null && direct.atomicGroup == null &&
            direct.load == AttachmentLoadPlan.ClearTransparent && direct.store == AttachmentStorePlan.Store &&
            direct.depthStencilAccess == null && direct.depthStencilLoadStore == null)
        require(remapping.values.toSet() == rows.map { it.id }.toSet() && remapping.values.size == rows.size)
        require(rows.none { it.role in setOf(PlanResourceRole.LogicalTarget, PlanResourceRole.ReadbackStaging, PlanResourceRole.PathAaResolvedColor) })
        val target = rows.single { it.id == direct.target }
        val resolve = rows.single { it.id == direct.resolveTarget }
        require(target.role == PlanResourceRole.MultisampleColorTarget && target.sampleCountI32 == 4 &&
            resolve.role == PlanResourceRole.CoverageSource && resolve.sampleCountI32 == 1 && target.id != resolve.id &&
            target.copyExtent() == extent && resolve.copyExtent() == extent && target.format == resolve.format &&
            target.usages() == setOf(PlanResourceUsage.RenderAttachment) &&
            resolve.usages() == setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled))
        require(rows.map { it.id }.toSet() == setOf(target.id, resolve.id, direct.drawDataResources.vertex,
            direct.drawDataResources.index, direct.drawDataResources.uniform))
        recipe = W4dAaCoverageSourceNativeSiteRecipeV1(this)
    }
}

/** Native identity for the coverage-only sibling; colour AA keeps its own recipe family. */
public class W4dAaCoverageSourceNativeSiteRecipeV1 internal constructor(
    public val binding: PlanW4dAaCoverageSourceBindingV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = binding.passes().single().let { NativeSiteOwnerV1(it.id, it.ordinal, 0) }
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W4dAaCoverageSource
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("capability", binding.sourceCapabilityId); int("command", binding.commandIndexI32)
        text("shader", "CorePrimitive.W4dAaCoverage"); text("color", "CanonicalOpaqueWhite")
        text("topology", "TriangleList"); int("samples", 4); text("resolve", "CoverageSource")
        val extent = binding.copyExtentI32(); int("width", extent.width); int("height", extent.height)
        val origin = binding.copyOriginDeviceI32(); int("origin.x", origin.x); int("origin.y", origin.y)
        val pass = binding.passes().single()
        text("phase.source", binding.sourcePassIds().single().value); text("phase.owner", pass.id.value)
        enum("phase.kind", pass.phase); enum("phase.load", pass.load); enum("phase.store", pass.store)
        text("target", pass.target.value); text("resolve.target", requireNotNull(pass.resolveTarget).value)
        text("vertex", pass.drawDataResources.vertex.value); text("index", pass.drawDataResources.index.value)
        text("uniform", pass.drawDataResources.uniform.value); text("uniform.abi", "W4dUniform32"); int("uniform.bytes", 32)
        blend("blend", pass.draw.blend); rect("scissor", pass.draw.copyScissorI32())
        val geometry = (pass.draw.copyPathGeometry() as PathDrawGeometry.Fill).valueF32
        requireNotNull(geometry.copyDirectTriangleF32OrNull()).let { triangle ->
            triangle.copyVerticesF32().forEachIndexed { i, value -> float("vertex.$i", value) }
            triangle.copyIndicesI32().forEachIndexed { i, value -> int("index.$i", value) }
        }
        binding.resourceRemapping().entries.sortedBy { it.key.value }.forEachIndexed { i, entry ->
            text("resource.$i.source", entry.key.value); text("resource.$i.final", entry.value.value)
        }
    }
}
