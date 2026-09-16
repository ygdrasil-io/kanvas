package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.plan.*
import org.graphiks.kanvas.gpu.renderer.color.*
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.passes.*
import org.graphiks.kanvas.gpu.renderer.planning.*
import org.graphiks.kanvas.gpu.renderer.resources.*
import org.graphiks.kanvas.gpu.renderer.state.*
import org.graphiks.math.color.ColorF32

/** Exact handle-free projection of a compiler-authenticated frame, including empty clear scopes. */
class GPUW6aLayerFramePlan internal constructor(private val request: GpuPlanLoweringRequest) {
    internal val graph: RenderGraph = request.graph
    private val layers = requireNotNull(graph.layerFramePlanOrNull())
    private val seal = GPUFrameCapabilitySeal.capture(request.frameId, request.deviceGeneration, request.capabilities)
    private val recording = GPURecordingSeal(request.recordingId, 0L, graph.id.value, graph.id.value, seal.sealHash)
    internal val refs: Map<PlanResourceId, GPUFrameResourceRef> = graph.resources().associate { resource ->
        resource.id to if (resource.kind == PlanResourceKind.Texture2D) GPUFrameTargetRef("w6a.${resource.id.value}")
            else GPUFrameBufferRef("w6a.${resource.id.value}")
    }
    private val bounds = GPUPixelBounds(0, 0, graph.targetExtent.width, graph.targetExtent.height)
    internal val readback = GPUFrameReadbackRequest(GPUReadbackRequestID("w6a.${graph.id.value}.readback"), bounds,
        GPUReadbackPixelFormat.Rgba8Unorm, GPUColorInterpretation.EncodedPremulSrgb)
    private val templates = mutableMapOf<GPUDrawPacketID, GPUW5aGeometryHostTemplateV1>()
    internal val memory: GPUFrameMemoryBudgetPlan
    internal val steps: List<GPUFrameStep>
    private val tasks: List<GPUTask>

    init {
        require(graph.verifyW6aLayerCompilerWitness())
        val allocations = graph.resources().map { resource -> GPUFrameMemoryAllocation(refs.getValue(resource.id).value,
            when (resource.role) {
                PlanResourceRole.LogicalTarget -> GPUFrameMemoryCategory.CanonicalTarget
                PlanResourceRole.LayerTarget -> GPUFrameMemoryCategory.LayerTarget
                PlanResourceRole.ReadbackStaging -> GPUFrameMemoryCategory.ReadbackStaging
                else -> GPUFrameMemoryCategory.ReusableScratch
            }, resource.byteSize,
            if (resource.kind == PlanResourceKind.Texture2D) GPUFrameMemoryResourceKind.Texture2D else GPUFrameMemoryResourceKind.Buffer,
            resource.copyExtent()?.let { GPUPixelBounds(0, 0, it.width, it.height) }, resource.firstPassIndex, resource.lastPassIndexExclusive) } +
            layers.sourceAllocations().mapIndexed { index, row -> GPUFrameMemoryAllocation("w6a.source.$index.${row.identity}",
                GPUFrameMemoryCategory.ReusableScratch, row.bytesI64,
                if (row.kind == PlanResourceKind.Texture2D) GPUFrameMemoryResourceKind.Texture2D else GPUFrameMemoryResourceKind.Buffer,
                row.copyExtentI32()?.let { GPUPixelBounds(0, 0, it.width, it.height) }, 0, graph.passes().size) }
        memory = GPUFrameMemoryBudgetPlanner.plan(GPUFrameMemoryBudgetRequest(allocations,
            minOf(graph.budget.maxFrameLocalBytes, request.rendererAggregateMemoryBudgetBytes ?: Long.MAX_VALUE), requireNotNull(request.capabilities.limits)))
        require(memory.diagnostic == null && memory.targetResidentBytes + memory.peakFrameTransientBytes ==
            Math.addExact(graph.peakFrameLocalBytes, layers.sourceBytesI64))
        val preparations = graph.resources().filter { it.role in setOf(PlanResourceRole.LogicalTarget, PlanResourceRole.LayerTarget, PlanResourceRole.ReadbackStaging) }
            .map { resource -> GPUResourcePreparationRequest(refs.getValue(resource.id),
                resource.copyExtent()?.let { GPUFrameTextureDescriptor(GPUPixelBounds(0, 0, it.width, it.height), GPUColorFormat.RGBA8UnormSrgb, resource.sampleCountI32) }
                    ?: GPUFrameBufferDescriptor(resource.byteSize, graph.capabilities.copyBytesPerRowAlignment.toLong()),
                when (resource.role) {
                    PlanResourceRole.LogicalTarget -> GPUFrameResourceRole.SceneTarget
                    PlanResourceRole.LayerTarget -> GPUFrameResourceRole.LayerTarget
                    else -> GPUFrameResourceRole.ReadbackStaging
                }, resource.usages().map { usage -> when (usage) {
                    PlanResourceUsage.RenderAttachment -> GPUFrameResourceUsage.RenderAttachment
                    PlanResourceUsage.Sampled -> GPUFrameResourceUsage.TextureBinding
                    PlanResourceUsage.CopySource -> GPUFrameResourceUsage.CopySource
                    PlanResourceUsage.CopyDestination -> GPUFrameResourceUsage.CopyDestination
                    PlanResourceUsage.MapRead -> GPUFrameResourceUsage.MapRead
                    else -> error("Unsupported W6 target usage")
                } }.toSet(), GPUFrameResourceLifetime.FrameLocal, resource.byteSize, refs.getValue(resource.id).value) }
        steps = java.util.Collections.unmodifiableList(buildList {
            add(GPUFrameStep.PrepareResourcesStep(preparations, listOf(GPUTaskID("w6a.prepare"))))
            graph.passes().forEach { pass ->
                val task = listOf(GPUTaskID("w6a.${pass.id.value}"))
                when (pass) {
                    is PlanPass.RenderPass, is PlanPass.LayerComposite -> {
                        val render = pass as? PlanPass.RenderPass
                        val targetId = render?.target ?: (pass as PlanPass.LayerComposite).destination
                        val targetExtent = requireNotNull(graph.resources().single { it.id == targetId }.copyExtent())
                        val targetBounds = GPUPixelBounds(0, 0, targetExtent.width, targetExtent.height)
                        val packets = render?.draws().orEmpty().map { draw ->
                            val packet = GpuPlanTaskListLowerer().packet(draw, ColorF32.Transparent, draw.commandIndex, targetBounds,
                                graph.materialPlanTableOrNull(), null,
                                draw.materialAuthority.colorSourceCoordinatesV4()?.let { graph.packedMaterialSourceV4(draw.materialAuthority) })
                            templates[packet.packetId] = w6aGeometryTemplate(packet, draw.blend)
                            packet
                        }
                        add(GPUFrameStep.RenderPassStep(refs.getValue(targetId) as GPUFrameTargetRef,
                            GPULoadStorePlan(if (render?.load == AttachmentLoadPlan.ClearTransparent) "clear" else "load", GPUStorePlan.Store),
                            GPUSamplePlan.SingleSampleFrame,
                            resourceUses = if (pass is PlanPass.LayerComposite) listOf(GPUFrameResourceUse(refs.getValue(pass.source),
                                GPUFrameResourceRole.LayerTarget, GPUFrameResourceUsage.TextureBinding, GPUFrameResourceLifetime.FrameLocal, false)) else emptyList(),
                            drawPackets = packets, sourceTaskIds = task,
                            batches = if (packets.isEmpty()) emptyList() else listOf(GPUFrameRenderBatch("w6a.${pass.id.value}", GPUPassBatchKind.Isolated, packets, task)),
                            w6aPassV1 = pass))
                    }
                    is PlanPass.ReadbackPass -> add(GPUFrameStep.ReadbackCopyStep(refs.getValue(pass.source) as GPUFrameTargetRef,
                        refs.getValue(pass.staging) as GPUFrameBufferRef, readback, task))
                    else -> error("Unadmitted W6 pass")
                }
            }
        })
        tasks = steps.map { step ->
            val id = step.sourceTaskIds.single()
            when (step) {
                is GPUFrameStep.PrepareResourcesStep -> GPUTask.PrepareResources(id, request.recordingId, GPUTaskPhase.Prepare, step.requests)
                is GPUFrameStep.RenderPassStep -> GPUTask.Render(id, request.recordingId, GPUTaskPhase.Render,
                    step.target, step.loadStore, step.samplePlan, resourceUses = step.resourceUses,
                    drawPackets = step.drawPackets, batchEligibilityByPacketId = step.drawPackets.associate {
                        it.packetId to GPUPassBatchEligibility(GPUPassBatchKind.Isolated)
                    }, w6aPassV1 = step.w6aPassV1)
                is GPUFrameStep.ReadbackCopyStep -> GPUTask.Readback(id, request.recordingId, GPUTaskPhase.Readback,
                    step.source, step.staging, step.request)
                else -> error("Unadmitted W6 step")
            }
        }
    }

    internal fun taskList(): GPUTaskList = GPUTaskList(request.frameId, seal, listOf(recording), graph.id.value,
        tasks, emptyList(), GPUTaskPhase.entries, memory, w6aLayerFrameV1 = this)

    internal fun frame(tasks: GPUTaskList): GPUFramePlan {
        require(tasks.w6aLayerFrameV1 === this && tasks.capabilitySeal === seal &&
            tasks.frameId == request.frameId && tasks.recordingSeals == listOf(recording) && tasks.memoryBudget == memory &&
            tasks.dependencies.isEmpty() && tasks.phaseOrder == GPUTaskPhase.entries && tasks.compositeCommands.isEmpty() &&
            tasks.tasks.size == this.tasks.size && tasks.tasks.zip(this.tasks).all { (a, b) -> a === b })
        return GPUFramePlan(request.frameId, seal, listOf(recording), steps, memory, emptyList(), w6aLayerFrameV1 = this)
    }

    internal fun validates(frame: GPUFramePlan): Boolean = frame.w6aLayerFrameV1 === this &&
        frame.capabilitySeal === seal && frame.steps.size == steps.size && frame.steps.zip(steps).all { (a, b) -> a === b } &&
        frame.frameId == request.frameId && frame.recordingSeals == listOf(recording) && frame.dependencies.isEmpty() &&
        frame.phaseOrder == GPUTaskPhase.entries && frame.diagnostics.isEmpty() && frame.elidedNoOpDraws.isEmpty() &&
        !frame.atomicallyRefused && frame.memoryBudget == memory && graph.verifyW6aLayerCompilerWitness()

    internal fun template(packet: GPUDrawPacket): GPUW5aGeometryHostTemplateV1? = templates[packet.packetId]
}
