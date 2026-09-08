package org.graphiks.kanvas.gpu.renderer.passes

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Collections
import org.graphiks.kanvas.gpu.plan.BinaryMaskFetchPlan
import org.graphiks.kanvas.gpu.plan.BinaryMaskedPathDraw
import org.graphiks.kanvas.gpu.plan.CoveragePlan
import org.graphiks.kanvas.gpu.plan.GeneralPathDraw
import org.graphiks.kanvas.gpu.plan.PathRenderPhase
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.PlanResource
import org.graphiks.kanvas.gpu.plan.PlanResourceKind
import org.graphiks.kanvas.gpu.plan.PlanResourceLifetime
import org.graphiks.kanvas.gpu.plan.PlanResourceRole
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.plan.PlanTextureFormat
import org.graphiks.kanvas.gpu.plan.PathDrawGeometry
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.SamplePlan
import org.graphiks.kanvas.gpu.plan.W4dGeneralPathPlanCompiler
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.corePrimitiveUniformBytes
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTextureRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPayload
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanner
import org.graphiks.kanvas.gpu.renderer.resources.GPUUniformSlabPlanningResult
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity

/** The only W4d.2 binary-mask fetch ABI: a target-texel integer load without filtering. */
public enum class GPUW4dBinaryMaskFetch {
    TextureLoadIntegerAtTargetTexelUnfiltered,
}

/** Immutable per-cover proof that one binary mask result is broadcast to all four MSAA samples. */
public class GPUW4dBinaryMaskCoverageContract private constructor(
    public val maskResourceId: String,
    public val fetch: GPUW4dBinaryMaskFetch,
    public val broadcastSampleCountI32: Int,
    public val broadcastsSameBinaryColorAndAlpha: Boolean,
) {
    override fun equals(other: Any?): Boolean = other is GPUW4dBinaryMaskCoverageContract &&
        maskResourceId == other.maskResourceId &&
        fetch == other.fetch &&
        broadcastSampleCountI32 == other.broadcastSampleCountI32 &&
        broadcastsSameBinaryColorAndAlpha == other.broadcastsSameBinaryColorAndAlpha

    override fun hashCode(): Int = listOf(
        maskResourceId,
        fetch,
        broadcastSampleCountI32,
        broadcastsSameBinaryColorAndAlpha,
    ).hashCode()

    internal companion object {
        fun exact(maskResourceId: String): GPUW4dBinaryMaskCoverageContract =
            GPUW4dBinaryMaskCoverageContract(
                maskResourceId = maskResourceId,
                fetch = GPUW4dBinaryMaskFetch.TextureLoadIntegerAtTargetTexelUnfiltered,
                broadcastSampleCountI32 = 4,
                broadcastsSameBinaryColorAndAlpha = true,
            )
    }
}

/**
 * Versioned, handle-free proof that W4d.2 path packets came from one fully validated graph.
 *
 * The constructor and issuer remain module-internal: callers can observe or revalidate a sealed
 * authority but cannot manufacture a sample, resolve, or atomic-group history.
 */
public class GPUPlanW4dGeneralPreparedAuthority private constructor(
    public val version: String,
    private val planId: String,
    private val capabilityId: String,
    passFacts: List<W4dGeneralPreparedPassFact>,
    binaryMaskCoverageContracts: List<GPUW4dBinaryMaskCoverageContract>,
    public val sampleContinuation: GPUW4dPathSampleContinuationAuthority?,
    private val nativeMaterialization: W4dGeneralNativeMaterializationSnapshot,
) {
    private val passFacts: List<W4dGeneralPreparedPassFact> =
        Collections.unmodifiableList(passFacts.toList())
    public val binaryMaskCoverageContracts: List<GPUW4dBinaryMaskCoverageContract> =
        Collections.unmodifiableList(binaryMaskCoverageContracts.toList())

    /**
     * Binds the immutable Task 7 graph snapshot to one renderer frame exactly once.  The returned
     * authority contains only typed resource identities and scalar facts; it never retains the
     * mutable graph or reconstructs resource labels in Task 8.
     */
    internal fun bindNativeMaterializationFrame(
        sessionIdentity: String,
        capabilitySealHash: String,
        deviceGeneration: GPUDeviceGenerationID,
        structuralKeysByPathPass: Map<String, GPUCorePrimitiveRenderPipelineStructuralKey>,
        uniformPayloadsByPathPass: Map<String, ByteArray>,
        uniformAlignmentBytes: Long,
        maxBufferSize: Long,
        maxDynamicUniformBuffersPerPipelineLayout: Long,
    ): GPUW4dGeneralPreparedFrameMaterializationAuthority? =
        nativeMaterialization.bind(
            planId = planId,
            capabilityId = capabilityId,
            sessionIdentity = sessionIdentity,
            capabilitySealHash = capabilitySealHash,
            deviceGeneration = deviceGeneration,
            structuralKeysByPathPass = structuralKeysByPathPass,
            uniformPayloadsByPathPass = uniformPayloadsByPathPass,
            uniformAlignmentBytes = uniformAlignmentBytes,
            maxBufferSize = maxBufferSize,
            maxDynamicUniformBuffersPerPipelineLayout = maxDynamicUniformBuffersPerPipelineLayout,
        )

    /**
     * Captures the sole native uniform ABI while Task 7 still owns the authenticated packets.
     * Task 8 receives only the resulting immutable slab seal and never revisits packet payloads
     * to select, rebuild, or plan a uniform layout.
     */
    internal fun nativeUniformPayloadFor(
        pass: PlanPass.PathRenderPass,
    ): ByteArray? {
        val fact = nativeMaterialization.pathPass(pass.id.value) ?: return null
        return fact.uniformPayloadBytes.toByteArray()
    }

    internal fun preflightRevalidates(
        graph: RenderGraph,
        pathPasses: List<PlanPass.PathRenderPass>,
    ): Boolean =
        version == VERSION &&
            graph.id.value == planId &&
            graph.capabilityId == capabilityId &&
            graph.verifyW4dGeneralCompilerWitness() &&
            passFacts == passFacts(pathPasses) &&
            binaryMaskCoverageContracts == binaryMaskCoverageContracts(pathPasses) &&
            nativeMaterialization.matches(graph, pathPasses) &&
            when (sampleContinuation) {
                null -> pathPasses.none { it.draw.sample == SamplePlan.Multisample4 }
                else -> sampleContinuation.revalidates(pathPasses)
            }

    internal fun matchesPreparedPacket(
        packet: GPUDrawPacket,
        pass: PlanPass.PathRenderPass,
        structuralPipelineKey: GPUCorePrimitiveRenderPipelineStructuralKey,
        renderPipelineKey: GPURenderPipelineKey,
    ): Boolean {
        val fact = passFacts.singleOrNull { it.pathPassId == pass.id.value } ?: return false
        val binaryContracts = binaryMaskCoverageContracts(listOf(pass)) ?: return false
        val binaryConsumerMatches = when (val expected = fact.draw) {
            is W4dGeneralPreparedDrawFact.General -> packet.w4dBinaryMaskConsumer == null
            is W4dGeneralPreparedDrawFact.BinaryMaskCover -> {
                val consumer = packet.w4dBinaryMaskConsumer
                consumer != null &&
                    consumer.maskResourceId == expected.maskResourceId &&
                    consumer.resourceSlot == packet.resourceSlot &&
                    consumer.bindingLayoutHash == packet.bindingLayoutHash &&
                    consumer.renderPipelineKey == renderPipelineKey &&
                    consumer.pipelineIntent == GPUW4dBinaryMaskPipelineIntent.CoverageMaskConsumer &&
                    consumer.fetch == GPUW4dBinaryMaskFetch.TextureLoadIntegerAtTargetTexelUnfiltered &&
                    consumer.coverGeometry == GPUW4dBinaryMaskCoverGeometry.TargetScissorQuad &&
                    consumer.broadcastSampleCountI32 == 4 &&
                    consumer.broadcastsSameBinaryColorAndAlpha &&
                    packetHasExactBinaryMaskCoverGeometry(packet, pass)
            }
        }
        return fact == passFact(pass) &&
            binaryContracts.all { it in binaryMaskCoverageContracts } &&
            binaryConsumerMatches &&
            packet.passId == fact.pathPassId &&
            packet.commandIdValue == fact.commandIdValue &&
            packet.renderPipelineKey == renderPipelineKey &&
            structuralPipelineKey.sampleCount == fact.sampleCount &&
            structuralPipelineKey.role == fact.expectedRole &&
            packet.role == fact.expectedPacketRole
    }

    internal companion object {
        const val VERSION: String = "w4d.2-general-prepared-authority-v1"

        fun issueAfterFullGraphValidation(
            graph: RenderGraph,
            pathPasses: List<PlanPass.PathRenderPass>,
        ): GPUPlanW4dGeneralPreparedAuthority {
            require(graph.capabilityId in setOf(
                W4dGeneralPathPlanCompiler.HARD_CAPABILITY_ID,
                W4dGeneralPathPlanCompiler.AA_CAPABILITY_ID,
            ) && graph.verifyW4dGeneralCompilerWitness()) {
                "W4d.2 prepared authority requires the compiler-authenticated graph"
            }
            val facts = requireNotNull(passFacts(pathPasses)) {
                "W4d.2 prepared authority requires complete ordered path-pass facts"
            }
            val binaryContracts = requireNotNull(binaryMaskCoverageContracts(pathPasses)) {
                "W4d.2 prepared authority requires exact binary-mask coverage contracts"
            }
            val continuation = if (pathPasses.any { it.draw.sample == SamplePlan.Multisample4 }) {
                GPUW4dPathSampleContinuationAuthority.issueFromValidated(pathPasses)
            } else {
                null
            }
            val nativeMaterialization = W4dGeneralNativeMaterializationSnapshot.from(
                graph = graph,
                pathPasses = pathPasses,
            ) ?: throw IllegalArgumentException(
                "W4d.2 prepared authority requires an exact native materialization snapshot",
            )
            return GPUPlanW4dGeneralPreparedAuthority(
                VERSION,
                graph.id.value,
                graph.capabilityId,
                facts,
                binaryContracts,
                continuation,
                nativeMaterialization,
            )
        }

        private fun passFacts(
            pathPasses: List<PlanPass.PathRenderPass>,
        ): List<W4dGeneralPreparedPassFact>? {
            if (pathPasses.isEmpty() || pathPasses.map { it.id }.distinct().size != pathPasses.size) return null
            return pathPasses.map { pass -> passFact(pass) }
        }

        private fun binaryMaskCoverageContracts(
            pathPasses: List<PlanPass.PathRenderPass>,
        ): List<GPUW4dBinaryMaskCoverageContract>? = buildList {
            pathPasses.forEach { pass ->
                val draw = pass.draw as? BinaryMaskedPathDraw ?: return@forEach
                if (
                    draw.coverage != CoveragePlan.BinaryMaskCover4 ||
                    draw.maskFetch != BinaryMaskFetchPlan.TextureLoadUnfiltered ||
                    draw.broadcastSampleCountI32 != 4
                ) return null
                add(GPUW4dBinaryMaskCoverageContract.exact(draw.mask.value))
            }
        }

        private fun passFact(pass: PlanPass.PathRenderPass): W4dGeneralPreparedPassFact {
            val expectedRole = when {
                pass.draw is BinaryMaskedPathDraw ->
                    GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskConsumer
                else -> when (pass.phase) {
                PathRenderPhase.SingleSampleStencilProducer,
                PathRenderPhase.MultisampleStencilProducer,
                PathRenderPhase.HardEdgeMaskStencilProducer,
                -> GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer
                PathRenderPhase.SingleSampleStencilColorCover,
                PathRenderPhase.MultisampleStencilColorCover,
                PathRenderPhase.HardEdgeMaskStencilCover,
                -> GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover
                else -> GPUCorePrimitiveRenderPipelineStructuralKey.Role.Shading
                }
            }
            val expectedPacketRole = when (expectedRole) {
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer ->
                    GPUDrawPacketRole.PathStencilProducer
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover ->
                    GPUDrawPacketRole.PathStencilCover
                else -> GPUDrawPacketRole.Shading
            }
            val draw = pass.draw
            val source = when (draw) {
                is GeneralPathDraw -> W4dGeneralPreparedDrawFact.General(draw.commandIndex)
                is BinaryMaskedPathDraw -> W4dGeneralPreparedDrawFact.BinaryMaskCover(
                    maskResourceId = draw.mask.value,
                    sourceCommandIdValue = draw.producer.commandIndex,
                )
            }
            return W4dGeneralPreparedPassFact(
                pathPassId = pass.id.value,
                commandIdValue = draw.commandIndex,
                phase = pass.phase,
                targetResourceId = pass.target.value,
                vertexResourceId = pass.drawDataResources.vertex.value,
                indexResourceId = pass.drawDataResources.index.value,
                uniformResourceId = pass.drawDataResources.uniform.value,
                depthStencilResourceId = pass.depthStencil?.value,
                resolveTargetResourceId = pass.resolveTarget?.value,
                atomicGroupId = pass.atomicGroup?.value,
                sampleCount = if (draw.sample == SamplePlan.Multisample4) 4 else 1,
                expectedRole = expectedRole,
                expectedPacketRole = expectedPacketRole,
                draw = source,
            )
        }
    }
}

private fun packetHasExactBinaryMaskCoverGeometry(
    packet: GPUDrawPacket,
    pass: PlanPass.PathRenderPass,
): Boolean {
    val semantic = packet.semanticPayload as? GPUDrawSemanticPayload.CorePrimitive ?: return false
    val geometry = semantic.geometry as? GPUCorePrimitiveGeometry.TriangulatedPath ?: return false
    val scissor = pass.draw.copyScissorI32()
    val expectedVertices = listOf(
        scissor.left.toFloat(), scissor.top.toFloat(),
        scissor.right.toFloat(), scissor.top.toFloat(),
        scissor.right.toFloat(), scissor.bottom.toFloat(),
        scissor.left.toFloat(), scissor.bottom.toFloat(),
    )
    return geometry.geometryMode == GPUCorePrimitiveGeometryMode.DirectTriangles &&
        geometry.sourceVertexCount == 4 &&
        geometry.vertices == expectedVertices &&
        geometry.indices == listOf(0, 1, 2, 0, 2, 3) &&
        geometry.coverBounds.left == scissor.left && geometry.coverBounds.top == scissor.top &&
        geometry.coverBounds.right == scissor.right && geometry.coverBounds.bottom == scissor.bottom
}

/** Immutable scalar snapshot; no graph or mutable geometry object crosses the authority boundary. */
private data class W4dGeneralPreparedPassFact(
    val pathPassId: String,
    val commandIdValue: Int,
    val phase: PathRenderPhase,
    val targetResourceId: String,
    val vertexResourceId: String,
    val indexResourceId: String,
    val uniformResourceId: String,
    val depthStencilResourceId: String?,
    val resolveTargetResourceId: String?,
    val atomicGroupId: String?,
    val sampleCount: Int,
    val expectedRole: GPUCorePrimitiveRenderPipelineStructuralKey.Role,
    val expectedPacketRole: GPUDrawPacketRole,
    val draw: W4dGeneralPreparedDrawFact,
)

private sealed interface W4dGeneralPreparedDrawFact {
    data class General(val sourceCommandIdValue: Int) : W4dGeneralPreparedDrawFact

    data class BinaryMaskCover(
        val maskResourceId: String,
        val sourceCommandIdValue: Int,
    ) : W4dGeneralPreparedDrawFact
}

/**
 * Internal Task 7 snapshot consumed by Task 8.  It deliberately stores scalar resource and pass
 * facts rather than the [RenderGraph], so native materialization cannot observe or retain mutable
 * planning input after the lowerer has published its task list.
 */
internal class W4dGeneralNativeMaterializationSnapshot private constructor(
    val targetBounds: GPUPixelBounds,
    val peakFrameLocalBytes: Long,
    resourceFacts: List<W4dGeneralNativeResourceFact>,
    pathPassFacts: List<W4dGeneralNativePathPassFact>,
    maskClearFacts: List<W4dGeneralNativeMaskClearFact>,
    val frameResources: W4dGeneralNativeFrameResourceSeal,
    val readbackSourceResourceId: String,
    val readbackStagingResourceId: String,
) {
    val resourceFacts: List<W4dGeneralNativeResourceFact> =
        Collections.unmodifiableList(resourceFacts.toList())
    val pathPassFacts: List<W4dGeneralNativePathPassFact> =
        Collections.unmodifiableList(pathPassFacts.toList())
    val maskClearFacts: List<W4dGeneralNativeMaskClearFact> =
        Collections.unmodifiableList(maskClearFacts.toList())

    internal fun matches(
        graph: RenderGraph,
        pathPasses: List<PlanPass.PathRenderPass>,
    ): Boolean {
        val candidate = from(graph, pathPasses) ?: return false
        return targetBounds == candidate.targetBounds &&
            peakFrameLocalBytes == candidate.peakFrameLocalBytes &&
            resourceFacts == candidate.resourceFacts &&
            pathPassFacts == candidate.pathPassFacts &&
            maskClearFacts == candidate.maskClearFacts &&
            frameResources.sameAs(candidate.frameResources) &&
            readbackSourceResourceId == candidate.readbackSourceResourceId &&
            readbackStagingResourceId == candidate.readbackStagingResourceId
    }

    internal fun bind(
        planId: String,
        capabilityId: String,
        sessionIdentity: String,
        capabilitySealHash: String,
        deviceGeneration: GPUDeviceGenerationID,
        structuralKeysByPathPass: Map<String, GPUCorePrimitiveRenderPipelineStructuralKey>,
        uniformPayloadsByPathPass: Map<String, ByteArray>,
        uniformAlignmentBytes: Long,
        maxBufferSize: Long,
        maxDynamicUniformBuffersPerPipelineLayout: Long,
    ): GPUW4dGeneralPreparedFrameMaterializationAuthority? {
        if (sessionIdentity.isBlank() || capabilitySealHash.isBlank() || planId.isBlank() ||
            resourceFacts.map(W4dGeneralNativeResourceFact::resourceId).distinct().size != resourceFacts.size
        ) return null
        val uniformSlab = W4dGeneralNativeUniformSlabSeal.create(
            pathPassFacts = pathPassFacts,
            uniformPayloadsByPathPass = uniformPayloadsByPathPass,
            deviceGeneration = deviceGeneration.value,
            alignmentBytes = uniformAlignmentBytes,
            maxBufferSize = maxBufferSize,
            maxDynamicUniformBuffersPerPipelineLayout = maxDynamicUniformBuffersPerPipelineLayout,
        ) ?: return null
        val bindings = resourceFacts.map { fact ->
            val suffix = when (fact.role) {
                PlanResourceRole.LogicalTarget -> "logical-target"
                PlanResourceRole.ReadbackStaging -> "staging"
                else -> fact.resourceId
            }
            val resource: GPUFrameResourceRef = when (fact.kind) {
                PlanResourceKind.Buffer -> GPUFrameBufferRef("$sessionIdentity.$suffix")
                PlanResourceKind.Texture2D -> when (fact.role) {
                    PlanResourceRole.LogicalTarget,
                    PlanResourceRole.MultisampleColorTarget,
                    PlanResourceRole.PathHardEdgeMask,
                    -> GPUFrameTargetRef("$sessionIdentity.$suffix")
                    else -> GPUFrameTextureRef("$sessionIdentity.$suffix")
                }
            }
            W4dGeneralNativeResourceBinding(
                fact = fact,
                resource = resource,
                attachmentIdentity = if (
                    fact.kind == PlanResourceKind.Texture2D &&
                    fact.role != PlanResourceRole.LogicalTarget
                ) {
                    GPUTargetIdentity("$sessionIdentity.${fact.resourceId}.attachment")
                } else {
                    null
                },
            )
        }
        val factsByTarget = resourceFacts.associateBy(W4dGeneralNativeResourceFact::resourceId)
        if (structuralKeysByPathPass.keys != pathPassFacts.map(
                W4dGeneralNativePathPassFact::pathPassId,
            ).toSet() || pathPassFacts.any { fact ->
                val structural = structuralKeysByPathPass[fact.pathPassId] ?: return null
                val target = factsByTarget[fact.targetResourceId] ?: return null
                val colorFormat = when (target.format) {
                    is PlanTextureFormat.Color ->
                        GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8UnormSrgb
                    PlanTextureFormat.CoverageMask ->
                        GPUCorePrimitiveRenderPipelineStructuralKey.ColorFormat.Rgba8Unorm
                    else -> return null
                }
                structural.colorFormat != colorFormat || structural.sampleCount != fact.sampleCountI32 ||
                    (fact.maskResourceId != null && structural.role !=
                        GPUCorePrimitiveRenderPipelineStructuralKey.Role.CoverageMaskConsumer)
            }
        ) return null
        return GPUW4dGeneralPreparedFrameMaterializationAuthority(
            planId = planId,
            capabilityId = capabilityId,
            capabilitySealHash = capabilitySealHash,
            deviceGeneration = deviceGeneration,
            targetBounds = targetBounds.copy(),
            peakFrameLocalBytes = peakFrameLocalBytes,
            bindings = bindings,
            pathPassFacts = pathPassFacts,
            maskClearFacts = maskClearFacts,
            frameResources = frameResources,
            readbackSourceResourceId = readbackSourceResourceId,
            readbackStagingResourceId = readbackStagingResourceId,
            structuralKeysByPathPass = structuralKeysByPathPass,
            uniformSlab = uniformSlab,
        )
    }

    internal fun pathPass(pathPassId: String): W4dGeneralNativePathPassFact? =
        pathPassFacts.singleOrNull { fact -> fact.pathPassId == pathPassId }

    internal companion object {
        fun from(
            graph: RenderGraph,
            pathPasses: List<PlanPass.PathRenderPass>,
        ): W4dGeneralNativeMaterializationSnapshot? {
            val graphPasses = graph.passes()
            if (graphPasses.filterIsInstance<PlanPass.PathRenderPass>() != pathPasses ||
                graphPasses.dropLast(1).any { pass ->
                    pass !is PlanPass.PathRenderPass && pass !is PlanPass.PathMaskClearPass
                }
            ) return null
            val readback = graphPasses.lastOrNull() as? PlanPass.ReadbackPass ?: return null
            val resources = graph.resources().map { resource ->
                val extent = resource.copyExtent()
                W4dGeneralNativeResourceFact(
                    resourceId = resource.id.value,
                    role = resource.role,
                    kind = resource.kind,
                    format = resource.format,
                    width = extent?.width,
                    height = extent?.height,
                    byteSize = resource.byteSize,
                    usages = resource.usages(),
                    lifetime = resource.lifetime,
                    firstPassIndex = resource.firstPassIndex,
                    lastPassIndexExclusive = resource.lastPassIndexExclusive,
                    sampleCountI32 = resource.sampleCountI32,
                )
            }
            val known = resources.associateBy(W4dGeneralNativeResourceFact::resourceId)
            val pathFacts = pathPasses.map { pass ->
                val maskResourceId = (pass.draw as? BinaryMaskedPathDraw)?.mask?.value
                val consumerUniform64 = (pass.draw as? BinaryMaskedPathDraw)?.let { binary ->
                    val mask = requireNotNull(known[maskResourceId]) {
                        "W4d.2 binary mask has no sealed resource fact"
                    }
                    w4dGeneralCoverageMaskConsumerUniform64(
                        targetBounds = GPUPixelBounds(
                            0,
                            0,
                            graph.targetExtent.width,
                            graph.targetExtent.height,
                        ),
                        maskWidth = requireNotNull(mask.width),
                        maskHeight = requireNotNull(mask.height),
                        premultipliedRgba = listOf(
                            binary.color.red,
                            binary.color.green,
                            binary.color.blue,
                            binary.color.alpha,
                        ),
                    )
                }
                val uniformPayload = consumerUniform64 ?: corePrimitiveUniformBytes(
                    GPUPixelBounds(0, 0, graph.targetExtent.width, graph.targetExtent.height),
                    if (pass.phase in setOf(
                            PathRenderPhase.HardEdgeMaskProducer,
                            PathRenderPhase.HardEdgeMaskStencilProducer,
                            PathRenderPhase.HardEdgeMaskStencilCover,
                        )
                    ) {
                        listOf(1f, 1f, 1f, 1f)
                    } else {
                        listOf(
                            pass.draw.color.red,
                            pass.draw.color.green,
                            pass.draw.color.blue,
                            pass.draw.color.alpha,
                        )
                    },
                ).map(Int::toByte)
                W4dGeneralNativePathPassFact(
                    pathPassId = pass.id.value,
                    commandIdValue = pass.draw.commandIndex,
                    phase = pass.phase,
                    targetResourceId = pass.target.value,
                    vertexResourceId = pass.drawDataResources.vertex.value,
                    indexResourceId = pass.drawDataResources.index.value,
                    uniformResourceId = pass.drawDataResources.uniform.value,
                    depthStencilResourceId = pass.depthStencil?.value,
                    resolveTargetResourceId = pass.resolveTarget?.value,
                    maskResourceId = maskResourceId,
                    atomicGroupId = pass.atomicGroup?.value,
                    sampleCountI32 = if (pass.draw.sample == SamplePlan.Multisample4) 4 else 1,
                    load = pass.load,
                    store = pass.store,
                    depthStencilAccess = pass.depthStencilAccess,
                    depthStencilLoadStore = pass.depthStencilLoadStore,
                    coverageMaskConsumerUniform64 = consumerUniform64,
                    uniformPayloadBytes = Collections.unmodifiableList(uniformPayload.toList()),
                )
            }
            val clearFacts = buildList {
                graphPasses.forEachIndexed { index, pass ->
                    val clear = pass as? PlanPass.PathMaskClearPass ?: return@forEachIndexed
                    val producer = graphPasses.getOrNull(index + 1) as? PlanPass.PathRenderPass
                        ?: return null
                    if (producer.target != clear.target || producer.atomicGroup != clear.atomicGroup) {
                        return null
                    }
                    add(
                        W4dGeneralNativeMaskClearFact(
                            passId = clear.id.value,
                            targetResourceId = clear.target.value,
                            atomicGroupId = clear.atomicGroup.value,
                            followingPathPassId = producer.id.value,
                        ),
                    )
                }
            }
            val frameResources = W4dGeneralNativeFrameResourceSeal.from(
                graph = graph,
                graphPasses = graphPasses,
                resourceFacts = resources,
                pathPassFacts = pathFacts,
            ) ?: return null
            val allResourceIds = buildSet {
                pathFacts.forEach { fact ->
                    add(fact.targetResourceId)
                    add(fact.vertexResourceId)
                    add(fact.indexResourceId)
                    add(fact.uniformResourceId)
                    fact.depthStencilResourceId?.let(::add)
                    fact.resolveTargetResourceId?.let(::add)
                    fact.maskResourceId?.let(::add)
                }
                clearFacts.forEach { fact -> add(fact.targetResourceId) }
                add(readback.source.value)
                add(readback.staging.value)
            }
            if (allResourceIds.any { it !in known } || pathFacts.isEmpty()) return null
            return W4dGeneralNativeMaterializationSnapshot(
                targetBounds = GPUPixelBounds(0, 0, graph.targetExtent.width, graph.targetExtent.height),
                peakFrameLocalBytes = graph.peakFrameLocalBytes,
                resourceFacts = resources,
                pathPassFacts = pathFacts,
                maskClearFacts = clearFacts,
                frameResources = frameResources,
                readbackSourceResourceId = readback.source.value,
                readbackStagingResourceId = readback.staging.value,
            )
        }
    }
}

/** One sealed renderer-frame binding over the Task 7 snapshot; no public constructor exists. */
internal class GPUW4dGeneralPreparedFrameMaterializationAuthority internal constructor(
    val planId: String,
    val capabilityId: String,
    val capabilitySealHash: String,
    val deviceGeneration: GPUDeviceGenerationID,
    targetBounds: GPUPixelBounds,
    val peakFrameLocalBytes: Long,
    bindings: List<W4dGeneralNativeResourceBinding>,
    pathPassFacts: List<W4dGeneralNativePathPassFact>,
    maskClearFacts: List<W4dGeneralNativeMaskClearFact>,
    val frameResources: W4dGeneralNativeFrameResourceSeal,
    val readbackSourceResourceId: String,
    val readbackStagingResourceId: String,
    structuralKeysByPathPass: Map<String, GPUCorePrimitiveRenderPipelineStructuralKey>,
    val uniformSlab: W4dGeneralNativeUniformSlabSeal,
) {
    val targetBounds: GPUPixelBounds = targetBounds.copy()
    private val bindings: List<W4dGeneralNativeResourceBinding> =
        Collections.unmodifiableList(bindings.toList())
    val pathPassFacts: List<W4dGeneralNativePathPassFact> =
        Collections.unmodifiableList(pathPassFacts.toList())
    val maskClearFacts: List<W4dGeneralNativeMaskClearFact> =
        Collections.unmodifiableList(maskClearFacts.toList())
    private val structuralKeysByPathPass: Map<String, GPUCorePrimitiveRenderPipelineStructuralKey> =
        Collections.unmodifiableMap(structuralKeysByPathPass.toMap())

    init {
        require(bindings.map { binding -> binding.fact.resourceId }.distinct().size == bindings.size) {
            "W4d.2 native materialization bindings must be unique"
        }
        require(pathPassFacts.map(W4dGeneralNativePathPassFact::pathPassId).distinct().size ==
            pathPassFacts.size) {
            "W4d.2 native materialization pass facts must be unique"
        }
        require(uniformSlab.pathPassIds == pathPassFacts.map(W4dGeneralNativePathPassFact::pathPassId) &&
            uniformSlab.plan.deviceGeneration == deviceGeneration.value
        ) { "W4d.2 native materialization uniform slab must match the sealed pass order" }
        require(frameResources.pathPassIds == pathPassFacts.map(W4dGeneralNativePathPassFact::pathPassId) &&
            uniformSlab.plan.totalBytes == frameResources.uniformReservedBytes
        ) { "W4d.2 native frame resources must match the sealed pass and uniform layout" }
    }

    internal fun resource(resourceId: String): GPUFrameResourceRef? =
        bindings.singleOrNull { binding -> binding.fact.resourceId == resourceId }?.resource

    internal fun resourceFact(resourceId: String): W4dGeneralNativeResourceFact? =
        bindings.singleOrNull { binding -> binding.fact.resourceId == resourceId }?.fact

    internal fun attachmentIdentity(resourceId: String): GPUTargetIdentity? =
        bindings.singleOrNull { binding -> binding.fact.resourceId == resourceId }
            ?.attachmentIdentity

    internal fun pathPass(passId: String): W4dGeneralNativePathPassFact? =
        pathPassFacts.singleOrNull { fact -> fact.pathPassId == passId }

    internal fun structuralPipelineKey(
        pathPassId: String,
    ): GPUCorePrimitiveRenderPipelineStructuralKey? = structuralKeysByPathPass[pathPassId]

    internal fun allBindings(): List<W4dGeneralNativeResourceBinding> = bindings
}

internal data class W4dGeneralNativeResourceBinding(
    val fact: W4dGeneralNativeResourceFact,
    val resource: GPUFrameResourceRef,
    /** Task 7 derives stable non-logical attachment identity; Task 8 only consumes it. */
    val attachmentIdentity: GPUTargetIdentity?,
)

/**
 * Immutable byte/capacity authority for one W4d.2 native frame.  Task 8 only uploads and slices
 * this snapshot; it never reconstructs geometry or selects late pool capacities from packets.
 */
internal class W4dGeneralNativeFrameResourceSeal private constructor(
    pathPassIds: List<String>,
    vertexData: FloatArray,
    indexData: IntArray,
    slices: List<W4dGeneralNativeGeometrySlice>,
    val vertexUsefulBytes: Long,
    val indexUsefulBytes: Long,
    val uniformUsefulBytes: Long,
    val uniformReservedBytes: Long,
    val vertexCapacityBytes: Long,
    val indexCapacityBytes: Long,
    val uniformCapacityBytes: Long,
    val peakFrameLocalBytes: Long,
) {
    private val pathPassIdsSnapshot = Collections.unmodifiableList(pathPassIds.toList())
    private val vertexDataSnapshot = vertexData.copyOf()
    private val indexDataSnapshot = indexData.copyOf()
    private val slicesSnapshot = Collections.unmodifiableList(slices.toList())

    val pathPassIds: List<String>
        get() = pathPassIdsSnapshot

    val slices: List<W4dGeneralNativeGeometrySlice>
        get() = slicesSnapshot

    init {
        require(pathPassIdsSnapshot.isNotEmpty() &&
            pathPassIdsSnapshot.distinct().size == pathPassIdsSnapshot.size &&
            slicesSnapshot.map(W4dGeneralNativeGeometrySlice::pathPassId) == pathPassIdsSnapshot &&
            vertexUsefulBytes == vertexDataSnapshot.size.toLong() * Float.SIZE_BYTES &&
            indexUsefulBytes == indexDataSnapshot.size.toLong() * Int.SIZE_BYTES &&
            listOf(vertexCapacityBytes, indexCapacityBytes, uniformCapacityBytes).all { capacity ->
                capacity > 0L && capacity and (capacity - 1L) == 0L
            } &&
            vertexUsefulBytes <= vertexCapacityBytes &&
            indexUsefulBytes <= indexCapacityBytes &&
            uniformReservedBytes in uniformUsefulBytes..uniformCapacityBytes &&
            peakFrameLocalBytes > 0L
        ) { "W4d.2 native frame resource seal is not exact" }
    }

    fun copyVertexData(): FloatArray = vertexDataSnapshot.copyOf()

    fun copyIndexData(): IntArray = indexDataSnapshot.copyOf()

    fun sameAs(other: W4dGeneralNativeFrameResourceSeal): Boolean =
        pathPassIdsSnapshot == other.pathPassIdsSnapshot &&
            slicesSnapshot == other.slicesSnapshot &&
            vertexUsefulBytes == other.vertexUsefulBytes &&
            indexUsefulBytes == other.indexUsefulBytes &&
            uniformUsefulBytes == other.uniformUsefulBytes &&
            uniformReservedBytes == other.uniformReservedBytes &&
            vertexCapacityBytes == other.vertexCapacityBytes &&
            indexCapacityBytes == other.indexCapacityBytes &&
            uniformCapacityBytes == other.uniformCapacityBytes &&
            peakFrameLocalBytes == other.peakFrameLocalBytes &&
            vertexDataSnapshot.indices.all { index ->
                vertexDataSnapshot[index].toRawBits() == other.vertexDataSnapshot[index].toRawBits()
            } && indexDataSnapshot.contentEquals(other.indexDataSnapshot)

    internal companion object {
        fun from(
            graph: RenderGraph,
            graphPasses: List<PlanPass>,
            resourceFacts: List<W4dGeneralNativeResourceFact>,
            pathPassFacts: List<W4dGeneralNativePathPassFact>,
        ): W4dGeneralNativeFrameResourceSeal? {
            val pathPasses = graphPasses.filterIsInstance<PlanPass.PathRenderPass>()
            if (pathPasses.map { pass -> pass.id.value } != pathPassFacts.map { fact -> fact.pathPassId }) {
                return null
            }
            val vertexResource = resourceFacts.singleOrNull { fact -> fact.role == PlanResourceRole.VertexData }
                ?: return null
            val indexResource = resourceFacts.singleOrNull { fact -> fact.role == PlanResourceRole.IndexData }
                ?: return null
            val uniformResource = resourceFacts.singleOrNull { fact -> fact.role == PlanResourceRole.UniformData }
                ?: return null
            if (listOf(vertexResource, indexResource, uniformResource).any { fact ->
                    fact.kind != PlanResourceKind.Buffer || fact.lifetime != PlanResourceLifetime.FrameLocal
                } || pathPassFacts.any { fact ->
                    fact.vertexResourceId != vertexResource.resourceId ||
                        fact.indexResourceId != indexResource.resourceId ||
                        fact.uniformResourceId != uniformResource.resourceId
                }
            ) return null

            val vertices = ArrayList<Float>()
            val indices = ArrayList<Int>()
            val slices = ArrayList<W4dGeneralNativeGeometrySlice>()
            pathPasses.zip(pathPassFacts).forEach { (pass, fact) ->
                val (entryVertices, entryIndices) = geometryFor(pass) ?: return null
                if (entryVertices.size !in 2..Int.MAX_VALUE || entryVertices.size % 2 != 0 ||
                    entryIndices.isEmpty() || entryIndices.any { index -> index !in 0 until entryVertices.size / 2 }
                ) return null
                val baseVertex = vertices.size / 2
                val firstIndex = indices.size
                vertices += entryVertices.toList()
                indices += entryIndices.toList()
                slices += W4dGeneralNativeGeometrySlice(
                    pathPassId = fact.pathPassId,
                    firstIndex = firstIndex,
                    indexCount = entryIndices.size,
                    baseVertex = baseVertex,
                    vertexCount = entryVertices.size / 2,
                    maxLocalIndex = requireNotNull(entryIndices.maxOrNull()),
                )
            }
            return try {
                val vertexData = vertices.toFloatArray()
                val indexData = indices.toIntArray()
                val vertexUsefulBytes = Math.multiplyExact(vertexData.size.toLong(), Float.SIZE_BYTES.toLong())
                val indexUsefulBytes = Math.multiplyExact(indexData.size.toLong(), Int.SIZE_BYTES.toLong())
                val alignment = graph.capabilities.minUniformBufferOffsetAlignment.toLong()
                val uniformUsefulBytes = pathPassFacts.fold(0L) { total, fact ->
                    Math.addExact(total, fact.uniformPayloadBytes.size.toLong())
                }
                val uniformReservedBytes = pathPassFacts.fold(0L) { total, fact ->
                    Math.addExact(total, alignUp(fact.uniformPayloadBytes.size.toLong(), alignment))
                }
                val policy = graph.capabilities.bufferAllocationPolicy
                val expectedVertexCapacity = policy.reserve(
                    org.graphiks.kanvas.gpu.plan.PlanScratchBufferKind.Vertex,
                    vertexUsefulBytes,
                ) ?: return null
                val expectedIndexCapacity = policy.reserve(
                    org.graphiks.kanvas.gpu.plan.PlanScratchBufferKind.Index,
                    indexUsefulBytes,
                ) ?: return null
                val expectedUniformCapacity = policy.reserve(
                    org.graphiks.kanvas.gpu.plan.PlanScratchBufferKind.Uniform,
                    uniformReservedBytes,
                ) ?: return null
                if (vertexResource.byteSize != expectedVertexCapacity ||
                    indexResource.byteSize != expectedIndexCapacity ||
                    uniformResource.byteSize != expectedUniformCapacity
                ) return null
                val computedPeak = graphPasses.indices.maxOf { passIndex ->
                    resourceFacts.filter { fact ->
                        fact.lifetime == PlanResourceLifetime.FrameLocal &&
                            passIndex in fact.firstPassIndex until fact.lastPassIndexExclusive
                    }.fold(0L) { total, fact -> Math.addExact(total, fact.byteSize) }
                }
                if (computedPeak != graph.peakFrameLocalBytes) return null
                W4dGeneralNativeFrameResourceSeal(
                    pathPassIds = pathPassFacts.map(W4dGeneralNativePathPassFact::pathPassId),
                    vertexData = vertexData,
                    indexData = indexData,
                    slices = slices,
                    vertexUsefulBytes = vertexUsefulBytes,
                    indexUsefulBytes = indexUsefulBytes,
                    uniformUsefulBytes = uniformUsefulBytes,
                    uniformReservedBytes = uniformReservedBytes,
                    vertexCapacityBytes = vertexResource.byteSize,
                    indexCapacityBytes = indexResource.byteSize,
                    uniformCapacityBytes = uniformResource.byteSize,
                    peakFrameLocalBytes = computedPeak,
                )
            } catch (_: ArithmeticException) {
                null
            } catch (_: IllegalArgumentException) {
                null
            }
        }

        private fun geometryFor(pass: PlanPass.PathRenderPass): Pair<FloatArray, IntArray>? {
            val geometry = when (val value = pass.draw.copyPathGeometry()) {
                is PathDrawGeometry.Fill -> value.valueF32
                is PathDrawGeometry.Stroke -> value.valueF32.copyFillGeometryF32()
            }
            return when (pass.phase) {
                PathRenderPhase.SingleSampleDirectColor,
                PathRenderPhase.MultisampleDirectColor,
                PathRenderPhase.HardEdgeMaskProducer,
                -> requireNotNull(geometry.copyDirectTriangleF32OrNull()).let { direct ->
                    direct.copyVerticesF32() to direct.copyIndicesI32()
                }
                PathRenderPhase.SingleSampleStencilProducer,
                PathRenderPhase.MultisampleStencilProducer,
                PathRenderPhase.HardEdgeMaskStencilProducer,
                -> requireNotNull(geometry.copyStencilEdgeFanF32OrNull()).let { fan ->
                    fan.copyVerticesF32() to fan.copyIndicesI32()
                }
                PathRenderPhase.SingleSampleStencilColorCover,
                PathRenderPhase.MultisampleStencilColorCover,
                PathRenderPhase.HardEdgeMaskStencilCover,
                PathRenderPhase.HardEdgeBinaryColorCover,
                -> quad(pass.draw.copyScissorI32())
            }
        }

        private fun quad(bounds: org.graphiks.math.geometry.RectI32): Pair<FloatArray, IntArray> = floatArrayOf(
            bounds.left.toFloat(), bounds.top.toFloat(),
            bounds.right.toFloat(), bounds.top.toFloat(),
            bounds.right.toFloat(), bounds.bottom.toFloat(),
            bounds.left.toFloat(), bounds.bottom.toFloat(),
        ) to intArrayOf(0, 2, 1, 0, 3, 2)

        private fun alignUp(value: Long, alignment: Long): Long {
            require(value > 0L && alignment > 0L)
            val remainder = value % alignment
            return if (remainder == 0L) value else Math.addExact(value, alignment - remainder)
        }
    }
}

internal data class W4dGeneralNativeGeometrySlice(
    val pathPassId: String,
    val firstIndex: Int,
    val indexCount: Int,
    val baseVertex: Int,
    val vertexCount: Int,
    val maxLocalIndex: Int,
)

internal data class W4dGeneralNativeResourceFact(
    val resourceId: String,
    val role: PlanResourceRole,
    val kind: PlanResourceKind,
    val format: PlanTextureFormat?,
    val width: Int?,
    val height: Int?,
    val byteSize: Long,
    val usages: Set<PlanResourceUsage>,
    val lifetime: PlanResourceLifetime,
    val firstPassIndex: Int,
    val lastPassIndexExclusive: Int,
    val sampleCountI32: Int,
)

internal data class W4dGeneralNativePathPassFact(
    val pathPassId: String,
    val commandIdValue: Int,
    val phase: PathRenderPhase,
    val targetResourceId: String,
    val vertexResourceId: String,
    val indexResourceId: String,
    val uniformResourceId: String,
    val depthStencilResourceId: String?,
    val resolveTargetResourceId: String?,
    val maskResourceId: String?,
    val atomicGroupId: String?,
    val sampleCountI32: Int,
    val load: org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan,
    val store: org.graphiks.kanvas.gpu.plan.AttachmentStorePlan,
    val depthStencilAccess: org.graphiks.kanvas.gpu.plan.PlanDepthStencilAccess?,
    val depthStencilLoadStore: org.graphiks.kanvas.gpu.plan.PlanDepthStencilLoadStore?,
    /** Immutable Uniform64 bytes for the only W4d.2 binary-mask consumer native pipeline. */
    val coverageMaskConsumerUniform64: List<Byte>?,
    /** Complete canonical Uniform32/Uniform64 payload consumed by the native frame materializer. */
    val uniformPayloadBytes: List<Byte>,
)

/**
 * One lowerer-owned native uniform slab for the immutable W4d.2 pass order.  Its labels use pass
 * IDs rather than command IDs because a stencil producer and its cover legitimately share a
 * command ID.  The bytes are defensively captured before packets leave Task 7.
 */
internal class W4dGeneralNativeUniformSlabSeal private constructor(
    val plan: GPUUniformSlabPlan,
    pathPassIds: List<String>,
    packedBytes: ByteArray,
) {
    private val pathPassIdsSnapshot = Collections.unmodifiableList(pathPassIds.toList())
    private val packedBytesSnapshot = packedBytes.copyOf()

    val pathPassIds: List<String>
        get() = pathPassIdsSnapshot

    init {
        require(pathPassIdsSnapshot.isNotEmpty() &&
            pathPassIdsSnapshot.distinct().size == pathPassIdsSnapshot.size &&
            plan.slots.size == pathPassIdsSnapshot.size &&
            plan.totalBytes == packedBytesSnapshot.size.toLong()
        ) { "W4d.2 native uniform slab must seal each path pass exactly once" }
    }

    /** Returns a copy so a caller cannot mutate the authority's sealed uniform slab. */
    fun packedBytesForUpload(): ByteArray = packedBytesSnapshot.copyOf()

    internal companion object {
        const val SOURCE_LABEL: String = "w4d-general-native-uniform-slab-v1"
        private const val UNIFORM32_BYTES: Int = 32
        private const val UNIFORM64_BYTES: Int = 64

        fun create(
            pathPassFacts: List<W4dGeneralNativePathPassFact>,
            uniformPayloadsByPathPass: Map<String, ByteArray>,
            deviceGeneration: Long,
            alignmentBytes: Long,
            maxBufferSize: Long,
            maxDynamicUniformBuffersPerPipelineLayout: Long,
        ): W4dGeneralNativeUniformSlabSeal? {
            val pathPassIds = pathPassFacts.map(W4dGeneralNativePathPassFact::pathPassId)
            if (pathPassIds.isEmpty() || pathPassIds.distinct().size != pathPassIds.size ||
                uniformPayloadsByPathPass.keys != pathPassIds.toSet()
            ) return null
            val payloads = pathPassFacts.map { fact ->
                val bytes = uniformPayloadsByPathPass[fact.pathPassId] ?: return null
                val expectedConsumer = fact.coverageMaskConsumerUniform64
                val expectedBytes = fact.uniformPayloadBytes.toByteArray()
                if (bytes.size != expectedBytes.size || !bytes.contentEquals(expectedBytes) ||
                    (expectedConsumer == null && bytes.size != UNIFORM32_BYTES) ||
                    (expectedConsumer != null && (bytes.size != UNIFORM64_BYTES ||
                        !bytes.contentEquals(expectedConsumer.toByteArray())))
                ) return null
                GPUUniformSlabPayload("w4d-general-${fact.pathPassId}", bytes)
            }
            val plan = when (val planned = GPUUniformSlabPlanner.plan(
                sourceLabel = SOURCE_LABEL,
                deviceGeneration = deviceGeneration,
                alignmentBytes = alignmentBytes,
                uploadBudgetBytes = maxBufferSize,
                payloads = payloads,
                maxBufferSize = maxBufferSize,
                maxDynamicUniformBuffersPerPipelineLayout = maxDynamicUniformBuffersPerPipelineLayout,
            )) {
                is GPUUniformSlabPlanningResult.Accepted -> planned.plan
                is GPUUniformSlabPlanningResult.Refused -> return null
            }
            if (plan.totalBytes !in 1L..Int.MAX_VALUE.toLong()) return null
            val packedBytes = ByteArray(plan.totalBytes.toInt())
            plan.slots.zip(payloads).forEach { (slot, payload) ->
                payload.bytes.copyInto(packedBytes, slot.alignedOffset.toInt())
            }
            return W4dGeneralNativeUniformSlabSeal(plan, pathPassIds, packedBytes)
        }
    }
}

/**
 * Task 7 owns the one-time packing of the native consumer block.  Task 8 may only upload the
 * sealed bytes; it must not reconstruct coverage, bounds, color, or inversion from packet roles.
 */
internal fun w4dGeneralCoverageMaskConsumerUniform64(
    targetBounds: GPUPixelBounds,
    maskWidth: Int,
    maskHeight: Int,
    premultipliedRgba: List<Float>,
): List<Byte> = ByteBuffer.allocate(64).order(ByteOrder.LITTLE_ENDIAN).apply {
    require(premultipliedRgba.size == 4) { "W4d.2 consumer requires premultiplied RGBA" }
    putFloat(targetBounds.width.toFloat())
    putFloat(targetBounds.height.toFloat())
    putInt(0)
    putInt(0)
    putInt(maskWidth)
    putInt(maskHeight)
    putLong(0L)
    // W4d.2 source colors are the already-premultiplied graph contract consumed by CorePrimitive.
    premultipliedRgba.forEach(::putFloat)
    putInt(0) // W4d.2 binary mask never inverts its one producer result.
    repeat(12) { put(0) }
}.array().toList()

internal data class W4dGeneralNativeMaskClearFact(
    val passId: String,
    val targetResourceId: String,
    val atomicGroupId: String,
    /** The graph-validated producer which consumes this clear; never inferred by Task 8. */
    val followingPathPassId: String,
)
