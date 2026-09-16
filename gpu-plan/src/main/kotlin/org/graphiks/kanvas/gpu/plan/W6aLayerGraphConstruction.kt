package org.graphiks.kanvas.gpu.plan

import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.RectF64
import org.graphiks.math.geometry.RectI32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.matrix.LayerMappingF64
import org.graphiks.math.matrix.Matrix3x3F64
import org.graphiks.math.matrix.mapRectBoundsF64OrNull

/** One occurrence binding, still before material issuance and graph publication. */
internal class W6aLayerSourceBinding(val scopeI32: Int?, val source: SourceDeferredRenderConstructionV4)

internal class W6aLayerGraphConstruction(
    private val id: PlanId,
    val extent: SizeI32,
    val caps: PlanCapabilitySnapshot,
    val budget: PlanBudget,
    occurrences: List<W6aLayerPlanCompiler.ScopeOccurrence>,
    bindings: List<W6aLayerSourceBinding>,
) {
    private val occurrences = immutableList(occurrences)
    private val bindings = immutableList(bindings)
    val lanes = immutableList(bindings.map { it.source })
    private val root = planResourceId(PlanResourceRole.LogicalTarget, 0)
    private val staging = planResourceId(PlanResourceRole.ReadbackStaging, 0)
    private val domain = RectI32(0, 0, extent.width, extent.height)
    private val rowBytesI64 = Math.multiplyExact(extent.width.toLong(), 4L).let {
        Math.addExact(it, (caps.copyBytesPerRowAlignment - it % caps.copyBytesPerRowAlignment) % caps.copyBytesPerRowAlignment)
    }
    private val rawPasses: List<PlanPass>
    private val frame: LayerFramePlanV1
    private val resources: List<PlanResource>
    val nonUniformBytesI64: Long

    init {
        require(lanes.all { it.capabilities == caps && it.budget == budget })
        require(bindings.all { it.scopeI32 == null || occurrences.any { scope -> scope.idI32 == it.scopeI32 } })
        require(lanes.all { source -> source.passes().all { it is PlanPass.RenderPass || it is PlanPass.ReadbackPass } &&
            RenderGraph.visualDraws(source.passes()).all { it is SolidRectDraw && it.blend !is BlendPlan.DestinationReadV1 } }) {
            "w6a.layer.unsupported_child"
        }
        val passes = mutableListOf<PlanPass>()
        val steps = mutableListOf<LayerExecutionStepV1>()
        val scopes = mutableListOf<LayerScopePlanV1>()
        var versionI64 = 0L
        fun render(target: PlanResourceId, draws: List<PlanDraw>, clear: Boolean): PlanPass.RenderPass {
            val version = if (target == root) DestinationVersionI64(versionI64.also { versionI64 += draws.size }) else null
            return PlanPass.RenderPass(passes.count { it is PlanPass.RenderPass }, target, draws,
                if (clear) AttachmentLoadPlan.ClearTransparent else AttachmentLoadPlan.Load, AttachmentStorePlan.Store,
                destinationVersionAfter = if (target == root) DestinationVersionI64(versionI64) else version).also { passes += it }
        }
        render(root, emptyList(), true)
        fun segment(binding: W6aLayerSourceBinding) {
            val target = binding.scopeI32?.let { planResourceId(PlanResourceRole.LayerTarget, it) } ?: root
            val draws = RenderGraph.visualDraws(binding.source.passes())
            if (draws.isNotEmpty()) {
                val pass = render(target, draws, false)
                binding.scopeI32?.let { steps += LayerExecutionStepV1.RenderChildren(LayerScopeIdI32(it), pass.id) }
            }
        }
        var previousEndI32 = -1
        occurrences.forEach { occurrence ->
            bindings.filter { it.scopeI32 == null && RenderGraph.visualDraws(it.source.passes()).firstOrNull()?.commandIndex?.let {
                command -> command > previousEndI32 && command < occurrence.beginCommandIndexI32 } == true }.forEach(::segment)
            val scopeId = LayerScopeIdI32(occurrence.idI32)
            val target = planResourceId(PlanResourceRole.LayerTarget, occurrence.idI32)
            steps += LayerExecutionStepV1.Initialize(scopeId, render(target, emptyList(), true).id)
            bindings.filter { it.scopeI32 == occurrence.idI32 }.forEach(::segment)
            val restore = LayerRestorePlanV1(1f, null, BlendPlan.LegacySrcOverV1, false, false,
                DestinationVersionI64(versionI64), DestinationVersionI64(++versionI64))
            val composite = PlanPass.LayerComposite(occurrence.idI32, scopeId, target, root, domain, Point2I32.Origin,
                restore, AttachmentLoadPlan.Load, AttachmentStorePlan.Store, restore.parentVersionAfter)
            passes += composite
            steps += LayerExecutionStepV1.Restore(scopeId, composite.id)
            val transform = occurrence.descriptor.transform
            val mapping = requireNotNull(LayerMappingF64.ofOrNull(Matrix3x3F64(
                transform.sx.toDouble(), transform.kx.toDouble(), transform.tx.toDouble(),
                transform.ky.toDouble(), transform.sy.toDouble(), transform.ty.toDouble(),
                transform.persp0.toDouble(), transform.persp1.toDouble(), transform.persp2.toDouble()), Point2I32.Origin))
            val hint = occurrence.descriptor.copyBounds()?.let { mapping.copyLocalToDeviceF64().mapRectBoundsF64OrNull(
                RectF64(it.left.toDouble(), it.top.toDouble(), it.right.toDouble(), it.bottom.toDouble())) }
            scopes += LayerScopePlanV1(scopeId, null, occurrence.beginCommandIndexI32, occurrence.endCommandIndexI32,
                emptyList(), mapping, LayerBoundsPlanV1(hint, null, domain, domain, domain, domain),
                LayerInitializationPlanV1.TransparentBlack, restore, target)
            previousEndI32 = occurrence.endCommandIndexI32
        }
        bindings.filter { it.scopeI32 == null && RenderGraph.visualDraws(it.source.passes()).firstOrNull()?.commandIndex?.let {
            command -> command > previousEndI32 } == true }.forEach(::segment)
        passes += PlanPass.ReadbackPass(0, root, staging, rowBytesI64,
            Math.addExact(Math.multiplyExact(rowBytesI64, (extent.height - 1).toLong()), Math.multiplyExact(extent.width.toLong(), 4L)))
        rawPasses = immutableList(passes)
        frame = LayerFramePlanV1(scopes, steps)
        // Every handle is acquired by one frame draft and retained until completion.
        // Its physical lifetime is therefore the complete frame, even for sibling layers.
        resources = immutableList(buildList {
            add(PlanResource.of(PlanResourceRole.LogicalTarget, 0, PlanResourceKind.Texture2D,
                PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), extent,
                checkedTextureBytesI64(4, extent.width, extent.height, 1),
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource), PlanResourceLifetime.FrameLocal, 0, passes.size))
            occurrences.forEach { occurrence -> add(PlanResource.of(PlanResourceRole.LayerTarget, occurrence.idI32,
                PlanResourceKind.Texture2D, PlanTextureFormat.Color(PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL), extent,
                checkedTextureBytesI64(4, extent.width, extent.height, 1),
                setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.Sampled), PlanResourceLifetime.FrameLocal, 0, passes.size)) }
            add(PlanResource.of(PlanResourceRole.ReadbackStaging, 0, PlanResourceKind.Buffer, null, null,
                Math.multiplyExact(rowBytesI64, extent.height.toLong()), setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead),
                PlanResourceLifetime.FrameLocal, 0, passes.size))
            add(PlanResource.of(PlanResourceRole.UniformData, 0, PlanResourceKind.Buffer, null, null, 16L,
                setOf(PlanResourceUsage.Uniform, PlanResourceUsage.CopyDestination), PlanResourceLifetime.FrameLocal, 0, passes.size))
        })
        nonUniformBytesI64 = W6aLayerPlanBudget.peak(resources, passes.size, budget)
    }

    fun publish(table: MaterialPlanTable?, bound: List<List<PlanPass>>, sourceAllocations: List<LayerSourceAllocationV1> = emptyList()): RenderGraph {
        require(bound.size == lanes.size || bound.isEmpty() && table == null)
        val byCommand = bound.flatMap { RenderGraph.visualDraws(it) }.associateBy { it.commandIndex }
        val passes = rawPasses.map { pass -> if (pass is PlanPass.RenderPass) PlanPass.RenderPass(pass.ordinal, pass.target,
            pass.draws().map { byCommand.getValue(it.commandIndex) }, pass.load, pass.store,
            destinationVersionAfter = pass.destinationVersionAfter) else pass }
        val construction = RenderGraph.construct(id, W6aLayerPlanCompiler.CAPABILITY_ID, extent,
            PlanLogicalColorFormat.RGBA8_UNORM_SRGB_LINEAR_PREMUL, caps, budget, byCommand.size, resources, passes,
            passes.zipWithNext { a, b -> PlanPassDependency(a.id, b.id) }, nonUniformBytesI64, table)
        val finalFrame = LayerFramePlanV1(frame.scopes(), frame.executionSteps(), sourceAllocations)
        require(Math.addExact(construction.peakFrameLocalBytes, finalFrame.sourceBytesI64) <= budget.maxFrameLocalBytes) {
            "w6a.layer.resource_limit"
        }
        val sourceNonUniform = sourceAllocations.filterNot { it.uniform }.fold(construction.peakFrameLocalBytes) { bytes, row ->
            Math.addExact(bytes, row.bytesI64)
        }
        return RenderGraph.publishW6a(construction, finalFrame,
            packConstructedFrame(listOf(construction), table, sourceNonUniform))
    }
}
