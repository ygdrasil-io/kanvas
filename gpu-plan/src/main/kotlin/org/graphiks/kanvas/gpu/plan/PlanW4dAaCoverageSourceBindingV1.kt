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
    public val ownerPassId: PlanPassId,
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
        require(commandIndexI32 >= 0 && extent.width > 0 && extent.height > 0)
        require(sourceIds.size == phases.size && sourceIds.distinct().size == sourceIds.size)
        val terminal = when (phases.size) {
            1 -> phases.single().also { direct ->
                require(direct.phase == PathRenderPhase.MultisampleDirectColor &&
                    direct.draw.strategy == PathFillStrategy.DirectTriangle && direct.depthStencil == null &&
                    direct.atomicGroup == null && direct.load == AttachmentLoadPlan.ClearTransparent &&
                    direct.store == AttachmentStorePlan.Store && direct.depthStencilAccess == null &&
                    direct.depthStencilLoadStore == null)
            }
            2 -> phases[1].also { cover ->
                val producer = phases[0]
                require(producer.phase == PathRenderPhase.MultisampleStencilProducer &&
                    cover.phase == PathRenderPhase.MultisampleStencilColorCover &&
                    producer.draw.strategy == PathFillStrategy.StencilCover && cover.draw.strategy == PathFillStrategy.StencilCover &&
                    producer.target == cover.target && producer.depthStencil != null && producer.depthStencil == cover.depthStencil &&
                    producer.atomicGroup != null && producer.atomicGroup == cover.atomicGroup &&
                    producer.load == AttachmentLoadPlan.ClearTransparent && producer.store == AttachmentStorePlan.Store &&
                    producer.depthStencilAccess == PlanDepthStencilAccess.Write &&
                    producer.depthStencilLoadStore == PlanDepthStencilLoadStore.ClearZeroStore && producer.resolveTarget == null &&
                    cover.load == AttachmentLoadPlan.Load && cover.store == AttachmentStorePlan.Store &&
                    cover.depthStencilAccess == PlanDepthStencilAccess.ReadWrite &&
                    cover.depthStencilLoadStore == PlanDepthStencilLoadStore.LoadStoreTestReset)
            }
            else -> error("W6 AA coverage occurrence has one direct pass or one stencil pair")
        }
        require(phases.all { phase ->
            phase.draw is GeneralPathDraw && phase.draw.commandIndex == commandIndexI32 &&
                phase.draw.coverage == CoveragePlan.StencilAA4 && phase.draw.sample == SamplePlan.Multisample4 &&
                phase.draw.blend == BlendPlan.SrcOver
        })
        require(remapping.values.toSet() == rows.map { it.id }.toSet() && remapping.values.size == rows.size)
        require(rows.none { it.role in setOf(PlanResourceRole.LogicalTarget, PlanResourceRole.ReadbackStaging, PlanResourceRole.PathAaResolvedColor) })
        val target = rows.single { it.id == terminal.target }
        val resolve = rows.single { it.id == terminal.resolveTarget }
        require(target.role == PlanResourceRole.MultisampleColorTarget && target.sampleCountI32 == 4 &&
            resolve.role == PlanResourceRole.CoverageSource && resolve.sampleCountI32 == 1 && target.id != resolve.id &&
            target.copyExtent() == extent && resolve.copyExtent() == extent && target.format == resolve.format &&
            target.kind == PlanResourceKind.Texture2D && resolve.kind == PlanResourceKind.Texture2D &&
            target.format == PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL) &&
            target.usages() == setOf(PlanResourceUsage.RenderAttachment) &&
            resolve.usages() == setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled))
        require(rows.map { it.id }.toSet() == buildSet {
            add(target.id); add(resolve.id); add(terminal.drawDataResources.vertex)
            add(terminal.drawDataResources.index); add(terminal.drawDataResources.uniform)
            phases.first().depthStencil?.let(::add)
        })
        require(rows.all { it.lifetime == PlanResourceLifetime.FrameLocal })
        listOf(terminal.drawDataResources.vertex to PlanResourceRole.VertexData,
            terminal.drawDataResources.index to PlanResourceRole.IndexData,
            terminal.drawDataResources.uniform to PlanResourceRole.UniformData).forEach { (id, role) ->
            val row = rows.single { it.id == id }
            require(row.role == role && row.kind == PlanResourceKind.Buffer && row.format == null && row.copyExtent() == null)
        }
        recipe = W4dAaCoverageSourceNativeSiteRecipeV1(this)
    }
}

/** Native identity for the coverage-only sibling; colour AA keeps its own recipe family. */
public class W4dAaCoverageSourceNativeSiteRecipeV1 internal constructor(
    public val binding: PlanW4dAaCoverageSourceBindingV1,
) : NativeSiteRecipeV1 {
    override val versionI32: Int = 1
    override val owner: NativeSiteOwnerV1 = NativeSiteOwnerV1(binding.ownerPassId, 0, 0)
    override val family: NativeSiteRecipeFamilyV1 = NativeSiteRecipeFamilyV1.W4dAaCoverageSource
    override val canonicalLogicalEncodingV1: String = nativeSiteEncodingV1(family) {
        text("owner", binding.ownerPassId.value)
        text("capability", binding.sourceCapabilityId); int("command", binding.commandIndexI32)
        text("shader", "CorePrimitive.W4dAaCoverage"); text("color", "CanonicalOpaqueWhite")
        text("topology", "TriangleList"); int("samples", 4); text("resolve", "CoverageSource")
        val extent = binding.copyExtentI32(); int("width", extent.width); int("height", extent.height)
        val origin = binding.copyOriginDeviceI32(); int("origin.x", origin.x); int("origin.y", origin.y)
        binding.sourcePassIds().zip(binding.passes()).forEachIndexed { phaseIndex, (source, pass) ->
            text("phase.$phaseIndex.source", source.value); text("phase.$phaseIndex.owner", pass.id.value)
            enum("phase.$phaseIndex.kind", pass.phase); enum("phase.$phaseIndex.load", pass.load); enum("phase.$phaseIndex.store", pass.store)
            text("phase.$phaseIndex.target", pass.target.value); pass.resolveTarget?.let { text("phase.$phaseIndex.resolve.target", it.value) }
            pass.depthStencil?.let { text("phase.$phaseIndex.depth", it.value) }; pass.atomicGroup?.let { text("phase.$phaseIndex.group", it.value) }
            text("phase.$phaseIndex.vertex", pass.drawDataResources.vertex.value); text("phase.$phaseIndex.index", pass.drawDataResources.index.value)
            text("phase.$phaseIndex.uniform", pass.drawDataResources.uniform.value); text("phase.$phaseIndex.uniform.abi", "W4dUniform32"); int("phase.$phaseIndex.uniform.bytes", 32)
            blend("phase.$phaseIndex.blend", pass.draw.blend); rect("phase.$phaseIndex.scissor", pass.draw.copyScissorI32())
            val geometry = (pass.draw.copyPathGeometry() as PathDrawGeometry.Fill).valueF32
            geometry.copyDirectTriangleF32OrNull()?.let { triangle ->
                triangle.copyVerticesF32().forEachIndexed { i, value -> float("phase.$phaseIndex.vertex.$i", value) }
                triangle.copyIndicesI32().forEachIndexed { i, value -> int("phase.$phaseIndex.index.$i", value) }
            } ?: requireNotNull(geometry.copyStencilEdgeFanF32OrNull()).let { fan ->
                fan.copyVerticesF32().forEachIndexed { i, value -> float("phase.$phaseIndex.fan.vertex.$i", value) }
                fan.copyIndicesI32().forEachIndexed { i, value -> int("phase.$phaseIndex.fan.index.$i", value) }
                fan.copyContourStartsI32().forEachIndexed { i, value -> int("phase.$phaseIndex.fan.contour.$i", value) }
            }
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
