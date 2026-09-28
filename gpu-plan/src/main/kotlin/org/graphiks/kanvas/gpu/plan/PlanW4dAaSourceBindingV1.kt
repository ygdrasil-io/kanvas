package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.SizeI32

/** One W6-owned occurrence of the readback-free W4d colour-source contract. */
public class PlanW4dAaSourceBindingV1 internal constructor(
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
    public fun sourcePassIds(): List<PlanPassId> = sourceIds
    public fun passes(): List<PlanPass.PathRenderPass> = phases
    public fun resourceRemapping(): Map<PlanResourceId, PlanResourceId> = remapping
    public fun resources(): List<PlanResource> = rows
    public fun copyExtentI32(): SizeI32 = extentSnapshot.copy()
    public fun copyOriginDeviceI32(): Point2I32 = Point2I32(originSnapshot.x, originSnapshot.y)
    public val recipe: W4dAaSourceNativeSiteRecipeV1

    init {
        require(sourceCapabilityId == W4dGeneralPathPlanCompiler.W6_AA_COLOR_SOURCE_CAPABILITY_ID)
        require(sourceIds.size == phases.size && sourceIds.distinct().size == sourceIds.size)
        // Task 1 admits the direct triangle only; stencil pairs have a separate public oracle.
        val pass = phases.single()
        require(pass.draw is GeneralPathDraw && pass.draw.commandIndex == commandIndexI32 &&
            pass.phase == PathRenderPhase.MultisampleDirectColor && pass.draw.sample == SamplePlan.Multisample4 &&
            pass.draw.strategy == PathFillStrategy.DirectTriangle && pass.depthStencil == null &&
            pass.load == AttachmentLoadPlan.ClearTransparent && pass.store == AttachmentStorePlan.Store)
        require(pass.draw.blend == BlendPlan.LegacySrcOverV1 ||
            (pass.draw.blend as? BlendPlan.FixedFunctionV1)?.mode == org.graphiks.kanvas.render.ir.BlendMode.SRC_OVER)
        require(remapping.values.toSet() == rows.map { it.id }.toSet() && remapping.values.size == rows.size)
        require(rows.none { it.role == PlanResourceRole.LogicalTarget || it.role == PlanResourceRole.ReadbackStaging })
        val target = rows.single { it.id == pass.target }
        val resolve = rows.single { it.id == pass.resolveTarget }
        require(target.role == PlanResourceRole.MultisampleColorTarget && target.sampleCountI32 == 4 &&
            resolve.role == PlanResourceRole.PathAaResolvedColor && resolve.sampleCountI32 == 1 && target.id != resolve.id &&
            target.copyExtent() == extent && resolve.copyExtent() == extent && target.format == resolve.format &&
            target.usages() == setOf(PlanResourceUsage.RenderAttachment) &&
            resolve.usages() == setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled))
        require(rows.map { it.id }.toSet() == setOf(pass.target, requireNotNull(pass.resolveTarget),
            pass.drawDataResources.vertex, pass.drawDataResources.index, pass.drawDataResources.uniform))
        recipe = W4dAaSourceNativeSiteRecipeV1(this)
    }
}

/** Uniform32 is W4d's target-size/premultiplied-colour ABI, never W4e's consumer ABI. */
public class W4dAaSourceNativeSiteRecipeV1 internal constructor(public val binding: PlanW4dAaSourceBindingV1) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = binding.passes().single().let { NativeSiteOwnerV1(it.id, it.ordinal, 0) }
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W4dAaSource
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("capability", binding.sourceCapabilityId); int("command", binding.commandIndexI32)
        text("shader", "CorePrimitive.DirectGeometry"); text("topology", "TriangleList"); int("samples", 4)
        val extent = binding.copyExtentI32(); int("width", extent.width); int("height", extent.height)
        val origin = binding.copyOriginDeviceI32(); int("origin.x", origin.x); int("origin.y", origin.y)
        binding.sourcePassIds().zip(binding.passes()).forEachIndexed { i, (source, pass) ->
            text("phase.$i.source", source.value); text("phase.$i.owner", pass.id.value); int("phase.$i.ordinal", pass.ordinal)
            enum("phase.$i.kind", pass.phase); enum("phase.$i.load", pass.load); enum("phase.$i.store", pass.store)
            text("target", pass.target.value); text("resolve", requireNotNull(pass.resolveTarget).value)
            text("vertex", pass.drawDataResources.vertex.value); text("index", pass.drawDataResources.index.value)
            text("uniform", pass.drawDataResources.uniform.value); text("uniform.abi", "W4dUniform32"); int("uniform.bytes", 32)
            blend("blend", pass.draw.blend); rect("scissor", pass.draw.copyScissorI32())
            text("material.kind", pass.draw.materialAuthority.javaClass.simpleName)
            int("material.ref", pass.draw.materialAuthority.materialPlanRef().indexI32)
            val geometry = pass.draw.copyPathGeometry() as PathDrawGeometry.Fill
            val triangle = requireNotNull(geometry.valueF32.copyDirectTriangleF32OrNull())
            triangle.copyVerticesF32().forEachIndexed { j, value -> float("vertex.$j", value) }
            triangle.copyIndicesI32().forEachIndexed { j, value -> int("index.$j", value) }
        }
        binding.resourceRemapping().entries.sortedBy { it.key.value }.forEachIndexed { i, entry ->
            text("resource.$i.source", entry.key.value); text("resource.$i.final", entry.value.value)
        }
        binding.resources().sortedBy { it.id.value }.forEachIndexed { i, row ->
            text("row.$i.id", row.id.value); enum("row.$i.role", row.role); enum("row.$i.kind", row.kind)
            text("row.$i.format", row.format.toString()); long("row.$i.bytes", row.byteSize)
            int("row.$i.samples", row.sampleCountI32); enum("row.$i.lifetime", row.lifetime)
            int("row.$i.first", row.firstPassIndex); int("row.$i.last", row.lastPassIndexExclusive)
            row.copyExtent()?.let { int("row.$i.width", it.width); int("row.$i.height", it.height) }
            row.usages().sortedBy { it.name }.forEachIndexed { j, use -> enum("row.$i.use.$j", use) }
        }
    }
}
