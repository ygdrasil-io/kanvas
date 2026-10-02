package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.ClipCombineOperation
import org.graphiks.kanvas.gpu.plan.ClipPlanStrategy
import org.graphiks.kanvas.gpu.plan.ClippedBinaryMaskedPathDraw
import org.graphiks.kanvas.gpu.plan.ClippedGeneralPathDraw
import org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan
import org.graphiks.kanvas.gpu.plan.AttachmentStorePlan
import org.graphiks.kanvas.gpu.plan.BinaryMaskFetchPlan
import org.graphiks.kanvas.gpu.plan.BlendPlan
import org.graphiks.kanvas.gpu.plan.CoveragePlan
import org.graphiks.kanvas.gpu.plan.PathFillStrategy
import org.graphiks.kanvas.gpu.plan.PlanDepthStencilAccess
import org.graphiks.kanvas.gpu.plan.PlanDepthStencilLoadStore
import org.graphiks.kanvas.gpu.plan.PlanResource
import org.graphiks.kanvas.gpu.plan.PlanResourceKind
import org.graphiks.kanvas.gpu.plan.PlanResourceLifetime
import org.graphiks.kanvas.gpu.plan.PlanResourceRole
import org.graphiks.kanvas.gpu.plan.PlanResourceUsage
import org.graphiks.kanvas.gpu.plan.PlanTextureFormat
import org.graphiks.kanvas.gpu.plan.PathDrawGeometry
import org.graphiks.kanvas.gpu.plan.PathRenderPhase
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.SamplePlan
import org.graphiks.kanvas.gpu.plan.W4eClipPlanCompiler
import org.graphiks.kanvas.gpu.plan.hasLegacyPathColorContract
import org.graphiks.kanvas.gpu.plan.hasW5aMaterialPathContract
import org.graphiks.kanvas.gpu.plan.W4eNativePayloadPlan
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameStep
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUse
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameBufferRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceRole
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.resources.GPUResourcePreparationRequest
import org.graphiks.math.geometry.InverseInteriorCoverageF32
import org.graphiks.math.geometry.InversePathGeometryF32
import org.graphiks.math.geometry.PathFillGeometryF32
import org.graphiks.math.geometry.PathFillScanScissorsI32
import org.graphiks.math.geometry.PathFillScanSpansI32
import org.graphiks.math.geometry.Point2I32
import org.graphiks.math.geometry.SizeI32
import org.graphiks.math.geometry.ClipGeometryF32

/**
 * Immutable consumer fact copied from the sealed W4e graph before task lowering begins.
 *
 * This is deliberately separate from [GPUClipExecutionPlan]: W4e has already chosen its
 * strategy, so no mapper may reconstruct or classify a clip after `Ready`.
 */
public sealed interface GPUW4ePreparedClipConsumerAuthority {
    public val consumerPassId: String
    public val domain: GPUPixelBounds

    public class Mask internal constructor(
        override val consumerPassId: String,
        public val maskResourceId: String,
        override val domain: GPUPixelBounds,
    ) : GPUW4ePreparedClipConsumerAuthority

    public class InverseMask internal constructor(
        override val consumerPassId: String,
        public val maskResourceId: String,
        override val domain: GPUPixelBounds,
        public val interiorCoverage: GPUW4ePreparedInverseInteriorCoverage,
    ) : GPUW4ePreparedClipConsumerAuthority

    public class InverseDomain internal constructor(
        override val consumerPassId: String,
        override val domain: GPUPixelBounds,
        public val interiorCoverage: GPUW4ePreparedInverseInteriorCoverage,
    ) : GPUW4ePreparedClipConsumerAuthority
}

/** Defensive inverse interior snapshot paired with its finite W4e consumer domain. */
public sealed interface GPUW4ePreparedInverseInteriorCoverage {
    public data object Zero : GPUW4ePreparedInverseInteriorCoverage

    public class Geometry internal constructor(geometry: PathFillGeometryF32) :
        GPUW4ePreparedInverseInteriorCoverage {
        private val snapshot: PathFillGeometryF32 =
            InverseInteriorCoverageF32.Geometry.of(geometry).copyGeometryF32()

        public fun copyGeometryF32(): PathFillGeometryF32 =
            InverseInteriorCoverageF32.Geometry.of(snapshot).copyGeometryF32()
    }
}

/** Complete prepared clip-prefix handoff, with no packet-side reconstruction. */
public sealed interface GPUW4ePreparedClipPassAuthority {
    public val passId: String
    public val kind: Kind
    public val atomicGroupId: String
    public val resourceIds: List<String>

    public enum class Kind { Initialize, Producer, Fold, PathMaskClear }

    public class Initialize internal constructor(
        override val passId: String,
        public val outputResourceId: String,
        public val domain: GPUPixelBounds,
        public val clearCoverage: Float,
        override val atomicGroupId: String,
    ) : GPUW4ePreparedClipPassAuthority {
        override val kind: Kind = Kind.Initialize
        override val resourceIds: List<String> = listOf(outputResourceId)
    }

    public class Producer internal constructor(
        override val passId: String,
        public val targetResourceId: String,
        public val resolveTargetResourceId: String?,
        public val depthStencilResourceId: String?,
        public val sampleCount: Int,
        public val geometry: GPUW4ePreparedClipGeometry,
        public val scissor: GPUPixelBounds,
        public val inverseCoverage: Boolean,
        public val antiAlias: Boolean,
        public val realization: PlanPass.W4eClipMaskProducerRealizationV1,
        override val atomicGroupId: String,
    ) : GPUW4ePreparedClipPassAuthority {
        override val kind: Kind = Kind.Producer
        override val resourceIds: List<String> = listOfNotNull(
            targetResourceId, resolveTargetResourceId, depthStencilResourceId,
        )
    }

    public class Fold internal constructor(
        override val passId: String,
        public val previousResourceId: String,
        public val sourceResourceId: String,
        public val outputResourceId: String,
        public val operation: ClipCombineOperation,
        public val domain: GPUPixelBounds,
        override val atomicGroupId: String,
    ) : GPUW4ePreparedClipPassAuthority {
        override val kind: Kind = Kind.Fold
        override val resourceIds: List<String> = listOf(
            previousResourceId, sourceResourceId, outputResourceId,
        )
    }

    /** Exact single-sample clear that starts an atomic hard-edge mask sequence. */
    public class PathMaskClear internal constructor(
        override val passId: String,
        public val targetResourceId: String,
        public val load: AttachmentLoadPlan,
        public val store: AttachmentStorePlan,
        override val atomicGroupId: String,
    ) : GPUW4ePreparedClipPassAuthority {
        override val kind: Kind = Kind.PathMaskClear
        override val resourceIds: List<String> = listOf(targetResourceId)
    }

    /** Resource references and attachment state for one exact W4e path render pass. */
    public class Path internal constructor(
        public val passId: String,
        public val commandIdValue: Int,
        public val phase: PathRenderPhase,
        public val materialAuthority: org.graphiks.kanvas.gpu.plan.PlanDrawMaterialAuthority,
        public val fillStrategy: PathFillStrategy,
        public val coverage: CoveragePlan,
        public val blend: BlendPlan,
        public val scissor: GPUPixelBounds,
        public val binarySourceMaskResourceId: String?,
        public val binaryMaskFetch: BinaryMaskFetchPlan?,
        public val binaryBroadcastSampleCount: Int?,
        public val targetResourceId: String,
        public val resolveTargetResourceId: String?,
        public val depthStencilResourceId: String?,
        public val vertexResourceId: String,
        public val indexResourceId: String,
        public val uniformResourceId: String,
        public val sample: SamplePlan,
        public val load: AttachmentLoadPlan,
        public val store: AttachmentStorePlan,
        public val depthStencilAccess: PlanDepthStencilAccess?,
        public val depthStencilLoadStore: PlanDepthStencilLoadStore?,
        public val atomicGroupId: String?,
        geometry: PathDrawGeometry,
        public val scanSpansDeviceI32: PathFillScanSpansI32?,
        public val scanScissorsLocalI32: PathFillScanScissorsI32?,
    ) {
        private val geometrySnapshot: PathDrawGeometry = geometry
        init {
            require((scanSpansDeviceI32 == null) == (scanScissorsLocalI32 == null))
        }
        public fun copyGeometry(): PathDrawGeometry = geometrySnapshot
    }
}

/** Defensive geometry snapshot for a complete W4e mask producer. */
public sealed interface GPUW4ePreparedClipGeometry {
    public class Rect internal constructor(private val snapshot: org.graphiks.math.geometry.RectF32) : GPUW4ePreparedClipGeometry {
        public fun copyRectF32(): org.graphiks.math.geometry.RectF32 =
            org.graphiks.math.geometry.RectF32(snapshot.left, snapshot.top, snapshot.right, snapshot.bottom)
    }
    public class RRect internal constructor(private val snapshot: org.graphiks.math.geometry.RRectF32) : GPUW4ePreparedClipGeometry {
        public fun copyRRectF32(): org.graphiks.math.geometry.RRectF32 = snapshot
    }
    public class Path internal constructor(geometry: PathFillGeometryF32) : GPUW4ePreparedClipGeometry {
        private val snapshot = InverseInteriorCoverageF32.Geometry.of(geometry).copyGeometryF32()
        public fun copyPathGeometryF32(): PathFillGeometryF32 =
            InverseInteriorCoverageF32.Geometry.of(snapshot).copyGeometryF32()
    }
    public data object Empty : GPUW4ePreparedClipGeometry
}

/** Immutable W4e trust boundary: lowering consumes only a compiler-sealed graph snapshot. */
internal class GPUPlanW4ePreparedAuthority private constructor(
    private val version: String,
    private val planId: String,
    private val capabilityId: String,
    private val resourceFacts: List<String>,
    private val passFacts: List<String>,
    private val consumersByPassId: Map<String, GPUW4ePreparedClipConsumerAuthority>,
    private val passesById: Map<String, GPUW4ePreparedClipPassAuthority>,
    private val pathsById: Map<String, GPUW4ePreparedClipPassAuthority.Path>,
    private val nativePayload: W4eNativePayloadPlan,
    private val rootGraph: RenderGraph? = null,
) {
    /** Returns only an already-copied W4e fact; it never maps or reclassifies a clip. */
    fun consumerFor(passId: String): GPUW4ePreparedClipConsumerAuthority? = consumersByPassId[passId]

    /** Returns only an explicit prepared Task 7 handoff contract. */
    fun clipPassFor(passId: String): GPUW4ePreparedClipPassAuthority? = passesById[passId]

    fun pathFor(passId: String): GPUW4ePreparedClipPassAuthority.Path? = pathsById[passId]

    /**
     * Closes task lowering as one frame.  This seal joins the compiler-authenticated graph to the
     * exact packet order, target, resource uses, and atomic group; packet-local facts alone are
     * deliberately insufficient because they could otherwise be recombined across lowerings.
     */
    fun issueFrameAuthority(
        frameId: Long,
        capabilitySealHash: String,
        renders: List<GPUTask.Render>,
    ): GPUW4ePreparedFrameAuthority = GPUW4ePreparedFrameAuthority.issue(
        planId,
        capabilityId,
        frameId,
        capabilitySealHash,
        renders,
        nativePayload,
    )

    /** Only the complete root graph can authenticate a resolve-only scene target. */
    fun issueRootFrameAuthority(
        graph: RenderGraph,
        refs: Map<String, GPUFrameResourceRef>,
        preparations: List<GPUResourcePreparationRequest>,
        frameId: Long,
        capabilitySealHash: String,
        renders: List<GPUTask.Render>,
    ): GPUW4ePreparedFrameAuthority = GPUW4ePreparedFrameAuthority.issueRoot(
        this, graph, refs, preparations, frameId, capabilitySealHash, renders,
    )

    fun revalidatesRoot(graph: RenderGraph): Boolean = rootGraph === graph && revalidates(graph)

    fun revalidates(graph: RenderGraph): Boolean =
        version == versionForCapability(capabilityId) &&
            graph.verifyW4eCompilerWitness() &&
            graph.w4eNativePayloadOrNull() === nativePayload &&
            nativePayload.matchesDeclaredResources(graph.resources()) &&
            graph.id.value == planId &&
            graph.capabilityId == capabilityId &&
            graph.resources().map(::resourceFact) == resourceFacts &&
            graph.passes().map(PlanPass::id).map { it.value } == passFacts

    companion object {
        private const val VERSION: String = "w4e-prepared-authority-v1"
        private const val W5A_VERSION: String = "w4e-prepared-authority-w5a-material-v2"

        fun issueClipOnly(planId: String, plan: org.graphiks.kanvas.gpu.plan.W4eClipOnlyPlan): GPUPlanW4ePreparedAuthority {
            require(plan.nativePayload.matchesDeclaredResources(plan.resources()))
            val facts = plan.passes().map { requireNotNull(clipPassFact(it)) }
            require(facts.size == plan.passes().size && facts.first() is GPUW4ePreparedClipPassAuthority.Initialize)
            return GPUPlanW4ePreparedAuthority("w4e-clip-only-v4", planId, "w4e-clip-only-v4",
                plan.resources().map(::resourceFact), plan.passes().map { it.id.value }, emptyMap(),
                facts.associateBy { it.passId }, emptyMap(), plan.nativePayload)
        }

        fun issueAfterFullGraphValidation(graph: RenderGraph): GPUPlanW4ePreparedAuthority {
            require(
                (W4eClipPlanCompiler.isLegacyCapabilityId(graph.capabilityId) ||
                    W4eClipPlanCompiler.isW5aMaterialCapabilityId(graph.capabilityId)) &&
                    graph.verifyW4eCompilerWitness() &&
                    if (W4eClipPlanCompiler.isW5aMaterialCapabilityId(graph.capabilityId)) {
                        graph.hasW5aMaterialPathContract()
                    } else {
                        graph.hasLegacyPathColorContract()
                    },
            ) {
                "W4e prepared authority requires the compiler-authenticated graph"
            }
            val nativePayload = requireNotNull(graph.w4eNativePayloadOrNull()) {
                "W4e prepared authority requires a graph-sealed native V/I/U payload"
            }
            require(nativePayload.matchesDeclaredResources(graph.resources())) {
                "W4e graph V/I/U resources differ from their compiler-sealed native payload"
            }
            return GPUPlanW4ePreparedAuthority(
                versionForCapability(graph.capabilityId),
                graph.id.value,
                graph.capabilityId,
                graph.resources().map(::resourceFact),
                graph.passes().map(PlanPass::id).map { it.value },
                graph.passes().filterIsInstance<PlanPass.PathRenderPass>().mapNotNull { pass ->
                    consumerFact(pass, domainFor(graph))
                }.associateBy(GPUW4ePreparedClipConsumerAuthority::consumerPassId),
                graph.passes().mapNotNull(::clipPassFact).associateBy(GPUW4ePreparedClipPassAuthority::passId),
                graph.passes().filterIsInstance<PlanPass.PathRenderPass>().associate {
                    it.id.value to pathFact(it, Point2I32.Origin, graph.targetExtent)
                },
                nativePayload,
                rootGraph = graph,
            )
        }

        /** The enclosing W6 graph authenticates these exact native passes and their physical slots. */
        fun issueLayered(graph: RenderGraph, binding: org.graphiks.kanvas.gpu.plan.PlanW4eGeometryBindingV1): GPUPlanW4ePreparedAuthority {
            require(graph.verifyW6aLayerCompilerWitness() &&
                graph.physicalLayoutOrNull()?.w4eGeometryBindings()?.any { it === binding } == true &&
                binding.payload.matchesDeclaredResources(graph.resources()))
            val passes = binding.nativePasses()
            val extent = binding.copyExtentI32()
            val domain = GPUPixelBounds(0, 0, extent.width, extent.height)
            return GPUPlanW4ePreparedAuthority("w6a-w4e-binding-v1", graph.id.value, graph.capabilityId,
                graph.resources().map(::resourceFact), passes.map { it.id.value },
                passes.filterIsInstance<PlanPass.PathRenderPass>().mapNotNull { consumerFact(it, domain) }
                    .associateBy { it.consumerPassId },
                passes.mapNotNull(::clipPassFact).associateBy { it.passId },
                passes.filterIsInstance<PlanPass.PathRenderPass>().associate {
                    it.id.value to pathFact(it, binding.copyMaterialDeviceOriginI32(), extent)
                }, binding.payload)
        }

        /**
         * W7 retains a W4e payload in a coverage-source binding rather than publishing a root
         * W4e graph.  Keep that provenance separate from the historical geometry binding.
         */
        fun issueLayered(graph: RenderGraph,
            binding: org.graphiks.kanvas.gpu.plan.PlanW4eInverseAaCoverageSourceBindingV1): GPUPlanW4ePreparedAuthority {
            require(graph.verifyW6aLayerCompilerWitness() &&
                graph.physicalLayoutOrNull()?.w4eInverseAaCoverageSourceBindings()?.any { it === binding } == true &&
                binding.payload().matchesDeclaredResources(binding.resources()))
            val passes = binding.passes()
            val extent = binding.copyExtentI32()
            val domain = GPUPixelBounds(0, 0, extent.width, extent.height)
            return GPUPlanW4ePreparedAuthority("w6a-w4e-inverse-aa-coverage-v1", graph.id.value, graph.capabilityId,
                binding.resources().map(::resourceFact), passes.map { it.id.value },
                passes.mapNotNull { pass -> consumerFact(pass, domain) }.associateBy { it.consumerPassId },
                emptyMap(), passes.associate { pass ->
                    pass.id.value to pathFact(pass, binding.copyOriginDeviceI32(), extent)
                }, binding.payload())
        }

        private fun pathFact(
            pass: PlanPass.PathRenderPass,
            originDeviceI32: Point2I32,
            targetExtentI32: SizeI32,
        ): GPUW4ePreparedClipPassAuthority.Path {
            val scanSpans = pass.scanSpansDeviceI32
            val scanScissors = scanSpans?.let { spans -> requireNotNull(
                spans.localScissorsI32OrNull(originDeviceI32, targetExtentI32),
            ) }
            return GPUW4ePreparedClipPassAuthority.Path(
                        pass.id.value,
                        pass.draw.commandIndex,
                        pass.phase,
                        pass.draw.materialAuthority,
                        pass.draw.strategy,
                        pass.draw.coverage,
                        pass.draw.blend,
                        domainFor(pass.draw.copyScissorI32()),
                        binarySourceMaskId(pass),
                        binaryMaskFetch(pass),
                        binaryBroadcastSamples(pass),
                        pass.target.value,
                        pass.resolveTarget?.value,
                        pass.depthStencil?.value,
                        pass.drawDataResources.vertex.value,
                        pass.drawDataResources.index.value,
                        pass.drawDataResources.uniform.value,
                        pass.draw.sample,
                        pass.load,
                        pass.store,
                        pass.depthStencilAccess,
                        pass.depthStencilLoadStore,
                        pass.atomicGroup?.value,
                        pass.draw.copyPathGeometry(),
                        scanSpans,
                        scanScissors,
                    )
        }

        private fun versionForCapability(capabilityId: String): String =
            if (W4eClipPlanCompiler.isW5aMaterialCapabilityId(capabilityId)) W5A_VERSION else VERSION

        private fun consumerFact(
            pass: PlanPass.PathRenderPass,
            targetDomain: GPUPixelBounds,
        ): GPUW4ePreparedClipConsumerAuthority? {
            val strategy = when (val draw = pass.draw) {
                is ClippedGeneralPathDraw -> draw.clip
                is ClippedBinaryMaskedPathDraw -> draw.clip
                else -> null
            } ?: return null
            return when (strategy) {
                is ClipPlanStrategy.Mask -> GPUW4ePreparedClipConsumerAuthority.Mask(
                    pass.id.value,
                    strategy.resource.value,
                    targetDomain,
                )
                is ClipPlanStrategy.InverseMask -> GPUW4ePreparedClipConsumerAuthority.InverseMask(
                    pass.id.value,
                    strategy.resource.value,
                    domainFor(strategy.geometryF32),
                    inverseInteriorFor(strategy.geometryF32),
                )
                is ClipPlanStrategy.InverseDomain -> GPUW4ePreparedClipConsumerAuthority.InverseDomain(
                    pass.id.value,
                    domainFor(strategy.geometryF32),
                    inverseInteriorFor(strategy.geometryF32),
                )
                else -> null
            }
        }

        private fun resourceFact(resource: PlanResource): String = buildString {
            append(resource.id.value).append('|').append(resource.role).append('|').append(resource.kind)
            append('|').append(resource.format).append('|').append(resource.copyExtent())
            append('|').append(resource.byteSize).append('|').append(resource.sampleCountI32)
            append('|').append(resource.lifetime).append('|').append(resource.firstPassIndex)
            append('|').append(resource.lastPassIndexExclusive).append('|')
            append(resource.usages().map { it.name }.sorted().joinToString(","))
        }

        private fun clipPassFact(pass: PlanPass): GPUW4ePreparedClipPassAuthority? = when (pass) {
            is PlanPass.PathMaskClearPass -> GPUW4ePreparedClipPassAuthority.PathMaskClear(
                pass.id.value, pass.target.value, pass.load, pass.store, pass.atomicGroup.value,
            )
            is PlanPass.ClipMaskInitialize -> GPUW4ePreparedClipPassAuthority.Initialize(
                pass.id.value, pass.output.value, domainFor(pass.copyDomainI32()), pass.clearCoverageF32,
                pass.atomicGroup.value,
            )
            is PlanPass.ClipMaskProducer -> GPUW4ePreparedClipPassAuthority.Producer(
                pass.id.value, pass.target.value, pass.resolveTarget?.value, pass.depthStencil?.value,
                pass.sampleCountI32, clipGeometryFor(pass.copyGeometryF32()), domainFor(pass.copyScissorI32()), pass.inverseCoverage, pass.antiAlias, pass.realization,
                pass.atomicGroup.value,
            )
            is PlanPass.ClipMaskFold -> GPUW4ePreparedClipPassAuthority.Fold(
                pass.id.value, pass.previous.value, pass.source.value, pass.output.value, pass.operation,
                domainFor(pass.copyDomainI32()), pass.atomicGroup.value,
            )
            else -> null
        }

        private fun domainFor(graph: RenderGraph): GPUPixelBounds =
            GPUPixelBounds(0, 0, graph.targetExtent.width, graph.targetExtent.height)

        private fun domainFor(geometry: InversePathGeometryF32): GPUPixelBounds =
            domainFor(geometry.copyDomainI32())

        private fun domainFor(domain: org.graphiks.math.geometry.RectI32): GPUPixelBounds =
            GPUPixelBounds(domain.left, domain.top, domain.right, domain.bottom)

        private fun clipGeometryFor(geometry: ClipGeometryF32): GPUW4ePreparedClipGeometry = when (geometry) {
            is ClipGeometryF32.Rect -> GPUW4ePreparedClipGeometry.Rect(geometry.copyRectF32())
            is ClipGeometryF32.RRect -> GPUW4ePreparedClipGeometry.RRect(geometry.copyRRectF32())
            is ClipGeometryF32.Path -> GPUW4ePreparedClipGeometry.Path(geometry.copyPathGeometryF32())
            ClipGeometryF32.Empty -> GPUW4ePreparedClipGeometry.Empty
        }

        private fun binarySourceMaskId(pass: PlanPass.PathRenderPass): String? = when (val draw = pass.draw) {
            is ClippedBinaryMaskedPathDraw -> draw.source.mask.value
            else -> null
        }
        private fun binaryMaskFetch(pass: PlanPass.PathRenderPass): BinaryMaskFetchPlan? = when (val draw = pass.draw) {
            is ClippedBinaryMaskedPathDraw -> draw.source.maskFetch
            else -> null
        }
        private fun binaryBroadcastSamples(pass: PlanPass.PathRenderPass): Int? = when (val draw = pass.draw) {
            is ClippedBinaryMaskedPathDraw -> draw.source.broadcastSampleCountI32
            else -> null
        }

        private fun inverseInteriorFor(
            geometry: InversePathGeometryF32,
        ): GPUW4ePreparedInverseInteriorCoverage = when (val interior = geometry.interiorCoverageF32) {
            InverseInteriorCoverageF32.Zero -> GPUW4ePreparedInverseInteriorCoverage.Zero
            is InverseInteriorCoverageF32.Geometry ->
                GPUW4ePreparedInverseInteriorCoverage.Geometry(interior.copyGeometryF32())
        }
    }
}

/** Complete W4e frame authority, retained by every packet and revalidated at all native boundaries. */
internal class GPUW4ePreparedFrameAuthority private constructor(
    private val graphId: String,
    private val graphCapabilityId: String,
    private val frameId: Long,
    private val capabilitySealHash: String,
    private val facts: List<Fact>,
    private val rootResolve: RootResolveWitness?,
    private val rootIssued: Boolean,
    private val inversePairOperationsByPassId: Map<String, GPUW4ePreparedInversePairOperation>,
    private val unsupportedMaskedInverseAaDirectOperationsByPassId: Map<String, UnsupportedMaskedInverseAaDirectOperation>,
    private val resourceInventory: GPUW4ePreparedResourceInventory?,
    /** Shared V/I/U byte authority issued from the compiler-authenticated W4e graph. */
    internal val nativePayload: W4eNativePayloadPlan,
) {
    /** Immutable root witness for the one AA inverse-mask direct operation W4e cannot encode. */
    private class UnsupportedMaskedInverseAaDirectOperation private constructor(
        val passId: String,
        private val packet: GPUDrawPacket,
        private val path: GPUW4ePreparedClipPassAuthority.Path,
        private val consumer: GPUW4ePreparedClipConsumerAuthority.InverseMask,
        private val targetResourceId: String,
        private val depthStencilResourceId: String,
    ) {
        fun owns(candidate: GPUDrawPacket, authority: GPUW4ePreparedFrameAuthority): Boolean =
            candidate === packet &&
                candidate.w4ePreparedFrameAuthority === authority &&
                candidate.passId == passId &&
                candidate.w4ePreparedPath === path &&
                candidate.w4ePreparedClipConsumer === consumer &&
                path.passId == passId &&
                consumer.consumerPassId == passId &&
                path.phase == PathRenderPhase.MultisampleDirectColor &&
                path.sample == SamplePlan.Multisample4 &&
                consumer.interiorCoverage is GPUW4ePreparedInverseInteriorCoverage.Geometry &&
                path.targetResourceId == targetResourceId &&
                path.depthStencilResourceId == depthStencilResourceId

        companion object {
            fun issue(
                packet: GPUDrawPacket,
                path: GPUW4ePreparedClipPassAuthority.Path,
                consumer: GPUW4ePreparedClipConsumerAuthority.InverseMask,
            ): UnsupportedMaskedInverseAaDirectOperation {
                require(packet.passId == path.passId && path.passId == consumer.consumerPassId &&
                    path.phase == PathRenderPhase.MultisampleDirectColor &&
                    path.sample == SamplePlan.Multisample4 &&
                    consumer.interiorCoverage is GPUW4ePreparedInverseInteriorCoverage.Geometry
                ) { "W4e unsupported masked inverse AA witness is not its exact prepared direct operation" }
                val depthStencilResourceId = requireNotNull(path.depthStencilResourceId) {
                    "W4e unsupported masked inverse AA operation lacks its sealed D24S8 attachment"
                }
                require(path.targetResourceId.isNotBlank()) {
                    "W4e unsupported masked inverse AA operation lacks its sealed target attachment"
                }
                return UnsupportedMaskedInverseAaDirectOperation(
                    path.passId,
                    packet,
                    path,
                    consumer,
                    path.targetResourceId,
                    depthStencilResourceId,
                )
            }
        }
    }

    private data class RootResolveWitness(
        val sceneTarget: GPUFrameTargetRef,
        val readbackStaging: GPUFrameBufferRef,
        val multisampleTarget: GPUFrameTargetRef,
        val finalScenePassId: String,
        val continuation: GPUW4eSceneContinuationRequest,
    )

    fun validatesRootResolve(
        candidateFrameId: Long,
        candidateSealHash: String,
        sceneTarget: GPUFrameTargetRef,
        readback: GPUTask.Readback?,
        renders: List<GPUTask.Render>,
    ): Boolean {
        val witness = rootResolve ?: return false
        if (readback == null) return false
        if (!validatesRenders(candidateFrameId, candidateSealHash, renders)) return false
        val finalRender = renders.lastOrNull() ?: return false
        return sceneTarget == witness.sceneTarget &&
            readback.source == witness.sceneTarget && readback.staging == witness.readbackStaging &&
            finalRender.target == witness.multisampleTarget &&
            finalRender.drawPackets.single().passId == witness.finalScenePassId &&
            finalRender.w4eSceneContinuation == witness.continuation
    }
    private data class Fact(
        val taskId: String,
        val passId: String,
        val commandIdValue: Int,
        val targetResourceId: String,
        val resourceUses: List<String>,
        val atomicGroupId: String?,
        val maskContinuation: GPUW4eMaskContinuationRequest?,
        val sceneContinuation: GPUW4eSceneContinuationRequest?,
    )

    /** Returns only the root-issued standalone inverse operation owned by this exact packet. */
    internal fun inversePairOperationFor(packet: GPUDrawPacket): GPUW4ePreparedInversePairOperation? =
        inversePairOperationsByPassId[packet.passId]?.takeIf { operation -> operation.owns(packet, this) }

    /**
     * Root standalone inverse Geometry producer/cover packets have no legacy fallback.
     * Non-root W5b/W6 and clip-prefix frame authorities deliberately keep their existing routes.
     */
    internal fun requiredRootInversePairOperationFor(packet: GPUDrawPacket): GPUW4ePreparedInversePairOperation? {
        val operation = inversePairOperationFor(packet)
        if (operation != null) return operation
        val path = packet.w4ePreparedPath
        val consumer = packet.w4ePreparedClipConsumer as? GPUW4ePreparedClipConsumerAuthority.InverseDomain
        val isStandaloneGeometryPhase = path?.phase in setOf(
            PathRenderPhase.SingleSampleStencilProducer,
            PathRenderPhase.MultisampleStencilProducer,
            PathRenderPhase.SingleSampleStencilColorCover,
            PathRenderPhase.MultisampleStencilColorCover,
        )
        require(!(rootIssued && packet.w4ePreparedFrameAuthority === this && isStandaloneGeometryPhase &&
            consumer?.interiorCoverage is GPUW4ePreparedInverseInteriorCoverage.Geometry)) {
            "W4e root inverse Geometry packet lacks its exact root-issued pair operation."
        }
        return null
    }

    /**
     * Returns true only for the exact root-issued AA inverse-mask operation for which W4e has no
     * native complement recipe. The packet's enum or consumer alone never selects this refusal.
     */
    internal fun hasUnsupportedMaskedInverseAaDirectOperation(packet: GPUDrawPacket): Boolean {
        val operation = unsupportedMaskedInverseAaDirectOperationsByPassId[packet.passId] ?: return false
        require(operation.owns(packet, this)) {
            "W4e unsupported masked inverse AA operation lost its root-issued path, consumer, or attachment authority."
        }
        return true
    }

    /** Root inventories fail closed; non-root authorities retain their existing W5b/W6 admission. */
    internal fun validatesResourceInventory(
        preparations: List<GPUResourcePreparationRequest>,
        renders: List<GPUFrameStep.RenderPassStep>,
        readback: GPUFrameStep.ReadbackCopyStep?,
    ): Boolean = resourceInventory?.validates(preparations, renders, readback) ?: !rootIssued

    /** This distinction is issued with the frame authority, never reconstructed from consumers. */
    internal fun requiresRootResourceInventory(): Boolean = rootIssued

    fun validatesRenders(
        candidateFrameId: Long,
        candidateSealHash: String,
        renders: List<GPUTask.Render>,
    ): Boolean {
        if (candidateFrameId != frameId || candidateSealHash != capabilitySealHash ||
            renders.size != facts.size || graphId.isBlank() || graphCapabilityId.isBlank()
        ) return false
        return renders.indices.all { index -> validates(index, renders[index]) }
    }

    fun validatesRenderSteps(
        candidateFrameId: Long,
        candidateSealHash: String,
        renders: List<GPUFrameStep.RenderPassStep>,
    ): Boolean {
        if (candidateFrameId != frameId || candidateSealHash != capabilitySealHash ||
            renders.size != facts.size || graphId.isBlank() || graphCapabilityId.isBlank()
        ) return false
        return renders.indices.all { index ->
            val fact = facts[index]
            val render = renders[index]
            val packet = render.drawPackets.singleOrNull() ?: return@all false
            packet.w4ePreparedFrameAuthority === this &&
                render.sourceTaskIds.singleOrNull()?.value == fact.taskId &&
                packet.passId == fact.passId &&
                packet.commandIdValue == fact.commandIdValue &&
                render.target.value == fact.targetResourceId &&
                render.resourceUses.map(::resourceUseFact) == fact.resourceUses &&
                atomicGroup(packet) == fact.atomicGroupId &&
                render.w4eMaskContinuation == fact.maskContinuation &&
                render.w4eSceneContinuation == fact.sceneContinuation &&
                validatesNoAlias(packet, render.resourceUses)
        }
    }

    private fun validates(index: Int, render: GPUTask.Render): Boolean {
        val fact = facts[index]
        val packet = render.drawPackets.singleOrNull() ?: return false
        return packet.w4ePreparedFrameAuthority === this &&
            render.taskId.value == fact.taskId &&
            packet.passId == fact.passId &&
            packet.commandIdValue == fact.commandIdValue &&
            render.target.value == fact.targetResourceId &&
            render.resourceUses.map(::resourceUseFact) == fact.resourceUses &&
            atomicGroup(packet) == fact.atomicGroupId &&
            render.w4eMaskContinuation == fact.maskContinuation &&
            render.w4eSceneContinuation == fact.sceneContinuation &&
            validatesNoAlias(packet, render.resourceUses)
    }

    private fun validatesNoAlias(packet: GPUDrawPacket, uses: List<GPUFrameResourceUse>): Boolean {
        if (uses.groupBy { it.resource }.values.any { resourceUses ->
                resourceUses.any { it.usage == org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.RenderAttachment } &&
                    resourceUses.any { it.usage == org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding }
            }
        ) return false
        val fold = packet.w4ePreparedClipPass as? GPUW4ePreparedClipPassAuthority.Fold ?: return true
        if (setOf(fold.previousResourceId, fold.sourceResourceId, fold.outputResourceId).size != 3) return false
        return uses.filter { use ->
            use.referencesW4eLogicalResource(fold.previousResourceId) ||
                use.referencesW4eLogicalResource(fold.sourceResourceId)
        }.all { use ->
            use.usage == org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.TextureBinding && !use.write
        } && uses.singleOrNull { use ->
            use.referencesW4eLogicalResource(fold.outputResourceId)
        }?.let { output ->
            output.usage == org.graphiks.kanvas.gpu.renderer.resources.GPUFrameResourceUsage.RenderAttachment && output.write
        } == true
    }

    private fun GPUFrameResourceUse.referencesW4eLogicalResource(resourceId: String): Boolean =
        resource.value == resourceId || resource.value.endsWith(".$resourceId")

    internal companion object {
        internal fun issue(
            graphId: String,
            graphCapabilityId: String,
            frameId: Long,
            capabilitySealHash: String,
            renders: List<GPUTask.Render>,
            nativePayload: W4eNativePayloadPlan,
        ): GPUW4ePreparedFrameAuthority = issueSealed(
            graphId, graphCapabilityId, frameId, capabilitySealHash, renders, nativePayload, null,
            rootIssued = false,
        )

        internal fun issueRoot(
            authority: GPUPlanW4ePreparedAuthority,
            graph: RenderGraph,
            refs: Map<String, GPUFrameResourceRef>,
            preparations: List<GPUResourcePreparationRequest>,
            frameId: Long,
            capabilitySealHash: String,
            renders: List<GPUTask.Render>,
        ): GPUW4ePreparedFrameAuthority {
            require(authority.revalidatesRoot(graph)) { "W4e root resolve requires the validated root graph" }
            val passes = graph.passes()
            val packetIndex = RootPacketIndex.issue(renders)
            val inversePairOperations = issueRootInversePairOperations(authority, graph, packetIndex)
            val unsupportedMaskedInverseAaDirectOperations =
                issueRootUnsupportedMaskedInverseAaDirectOperations(authority, graph, packetIndex)
            val resourceInventory = GPUW4ePreparedResourceInventory.issue(graph, refs, preparations, renders)
            val finalScene = passes.dropLast(1).lastOrNull() as? PlanPass.PathRenderPass
            // Direct 1x frames retain their existing admission, including mixed frames ending at 1x.
            if (finalScene == null || finalScene.draw.sample != SamplePlan.Multisample4 || finalScene.resolveTarget == null) {
                return issueSealed(
                    graph.id.value,
                    graph.capabilityId,
                    frameId,
                    capabilitySealHash,
                    renders,
                    requireNotNull(graph.w4eNativePayloadOrNull()),
                    null,
                    inversePairOperations,
                    unsupportedMaskedInverseAaDirectOperations,
                    resourceInventory,
                    rootIssued = true,
                )
            }
            val readback = passes.last() as? PlanPass.ReadbackPass
                ?: throw IllegalArgumentException("W4e root resolve requires terminal readback")
            require(passes.filterIsInstance<PlanPass.ReadbackPass>().size == 1)
            val resources = graph.resources()
            val scene = resources.single { it.role == PlanResourceRole.LogicalTarget }
            val staging = resources.single { it.role == PlanResourceRole.ReadbackStaging }
            val multisample = resources.single { it.id == finalScene.target }
            require(readback.source == scene.id && readback.staging == staging.id && finalScene.resolveTarget == scene.id)
            // A hard draw in an AA4 frame broadcasts its binary mask into the 4x scene
            // attachment. Its color cover, not its 1x mask producer, owns the final resolve.
            require(finalScene.phase == PathRenderPhase.MultisampleDirectColor ||
                finalScene.phase == PathRenderPhase.MultisampleStencilColorCover ||
                (finalScene.phase == PathRenderPhase.HardEdgeBinaryColorCover &&
                    finalScene.draw.coverage == CoveragePlan.BinaryMaskCover4))
            require(scene.kind == PlanResourceKind.Texture2D && scene.sampleCountI32 == 1 &&
                scene.format is PlanTextureFormat.Color && scene.copyExtent() == graph.targetExtent &&
                scene.usages().containsAll(setOf(PlanResourceUsage.RenderAttachment, PlanResourceUsage.CopySource)))
            require(multisample.role == PlanResourceRole.MultisampleColorTarget &&
                multisample.kind == PlanResourceKind.Texture2D && multisample.sampleCountI32 == 4 &&
                multisample.format == scene.format && multisample.copyExtent() == scene.copyExtent() &&
                PlanResourceUsage.RenderAttachment in multisample.usages())
            require(staging.kind == PlanResourceKind.Buffer &&
                staging.usages().containsAll(setOf(PlanResourceUsage.CopyDestination, PlanResourceUsage.MapRead)))
            val finalIndex = passes.size - 2
            val readbackIndex = passes.lastIndex
            fun liveAt(resource: PlanResource, index: Int): Boolean =
                resource.lifetime == PlanResourceLifetime.FrameLocal &&
                    resource.firstPassIndex <= index && index < resource.lastPassIndexExclusive
            require(liveAt(multisample, finalIndex) && liveAt(scene, finalIndex) &&
                liveAt(scene, readbackIndex) && liveAt(staging, readbackIndex))
            require(refs.keys == resources.map { it.id.value }.toSet() && refs.values.toSet().size == refs.size)
            val sceneRef = refs.getValue(scene.id.value) as? GPUFrameTargetRef
                ?: throw IllegalArgumentException("W4e scene resolve requires a mapped target")
            val multisampleRef = refs.getValue(multisample.id.value) as? GPUFrameTargetRef
                ?: throw IllegalArgumentException("W4e scene resolve requires a mapped multisample target")
            val stagingRef = refs.getValue(staging.id.value) as? GPUFrameBufferRef
                ?: throw IllegalArgumentException("W4e scene resolve requires mapped readback staging")
            require(renders.map { it.drawPackets.single().passId } == passes.dropLast(1).map { it.id.value })
            require(renders.zip(passes.dropLast(1)).all { (render, pass) ->
                val packet = render.drawPackets.single()
                val targetId = when (pass) {
                    is PlanPass.ClipMaskInitialize -> pass.output
                    is PlanPass.ClipMaskProducer -> pass.target
                    is PlanPass.ClipMaskFold -> pass.output
                    is PlanPass.PathMaskClearPass -> pass.target
                    is PlanPass.PathRenderPass -> pass.target
                    else -> return@all false
                }
                render.target == refs.getValue(targetId.value) &&
                    packet.w4ePreparedClipPass === authority.clipPassFor(pass.id.value) &&
                    packet.w4ePreparedPath === authority.pathFor(pass.id.value)
            })
            val finalRender = renders.last()
            val continuation = GPUW4eSceneContinuationRequest(
                multisample.id.value, scene.id.value, GPUW4eSceneResolveAction.ResolveCanonical,
            )
            require(finalRender.target == multisampleRef && finalRender.w4eSceneContinuation == continuation &&
                finalRender.drawPackets.single().w4ePreparedPath === authority.pathFor(finalScene.id.value))
            fun writes(ref: GPUFrameTargetRef, role: GPUFrameResourceRole): Boolean =
                finalRender.resourceUses.singleOrNull { it.resource == ref }?.let {
                    it.role == role && it.usage == GPUFrameResourceUsage.RenderAttachment && it.write
                } == true
            require(writes(multisampleRef, GPUFrameResourceRole.LayerTarget) && writes(sceneRef, GPUFrameResourceRole.SceneTarget))
            return issueSealed(graph.id.value, graph.capabilityId, frameId, capabilitySealHash, renders,
                requireNotNull(graph.w4eNativePayloadOrNull()),
                RootResolveWitness(sceneRef, stagingRef, multisampleRef, finalScene.id.value, continuation),
                inversePairOperations,
                unsupportedMaskedInverseAaDirectOperations,
                resourceInventory,
                rootIssued = true)
        }

        /** One root-issued packet index shared by the distinct pair and refusal authorities. */
        private class RootPacketIndex private constructor(val byPassId: Map<String, GPUDrawPacket>) {
            companion object {
                fun issue(renders: List<GPUTask.Render>): RootPacketIndex {
                    val packets = renders.associate { render ->
                        val packet = requireNotNull(render.drawPackets.singleOrNull()) {
                            "W4e root authority requires one packet per render pass"
                        }
                        packet.passId to packet
                    }
                    require(packets.size == renders.size) { "W4e root packet/pass identities are not one-to-one" }
                    return RootPacketIndex(java.util.Collections.unmodifiableMap(LinkedHashMap(packets)))
                }
            }
        }

        /**
         * W4e's root graph alone authenticates this unsupported direct AA mask route. Retain the
         * precise prepared objects, rather than allowing materialization to infer ownership from
         * a packet phase or consumer enum.
         */
        private fun issueRootUnsupportedMaskedInverseAaDirectOperations(
            authority: GPUPlanW4ePreparedAuthority,
            graph: RenderGraph,
            packetIndex: RootPacketIndex,
        ): Map<String, UnsupportedMaskedInverseAaDirectOperation> {
            return graph.passes().filterIsInstance<PlanPass.PathRenderPass>().mapNotNull { pass ->
                val path = requireNotNull(authority.pathFor(pass.id.value)) {
                    "W4e root path pass lacks its prepared attachment authority"
                }
                val packet = requireNotNull(packetIndex.byPassId[pass.id.value]) {
                    "W4e root path pass is absent from the authenticated render order"
                }
                require(packet.w4ePreparedPath === path && packet.passId == path.passId) {
                    "W4e root masked inverse AA packet differs from its prepared path authority"
                }
                val attachedInverseMask = packet.w4ePreparedClipConsumer as?
                    GPUW4ePreparedClipConsumerAuthority.InverseMask
                val attachedTargetsUnsupportedMaskedInverseAa =
                    path.phase == PathRenderPhase.MultisampleDirectColor &&
                        path.sample == SamplePlan.Multisample4 &&
                        attachedInverseMask?.interiorCoverage is GPUW4ePreparedInverseInteriorCoverage.Geometry
                val consumer = authority.consumerFor(pass.id.value)
                if (consumer == null) {
                    require(!attachedTargetsUnsupportedMaskedInverseAa) {
                        "W4e root masked inverse AA packet lacks its authenticated prepared consumer authority"
                    }
                    return@mapNotNull null
                }
                require(packet.w4ePreparedClipConsumer === consumer && path.passId == consumer.consumerPassId) {
                    "W4e root masked inverse AA packet differs from its prepared path or consumer authority"
                }
                val inverseMask = consumer as? GPUW4ePreparedClipConsumerAuthority.InverseMask
                    ?: return@mapNotNull null
                if (path.phase != PathRenderPhase.MultisampleDirectColor ||
                    path.sample != SamplePlan.Multisample4 ||
                    inverseMask.interiorCoverage !is GPUW4ePreparedInverseInteriorCoverage.Geometry
                ) return@mapNotNull null
                UnsupportedMaskedInverseAaDirectOperation.issue(packet, path, inverseMask)
            }.associateBy(UnsupportedMaskedInverseAaDirectOperation::passId)
        }

        private fun issueRootInversePairOperations(
            authority: GPUPlanW4ePreparedAuthority,
            graph: RenderGraph,
            packetIndex: RootPacketIndex,
        ): Map<String, GPUW4ePreparedInversePairOperation> {
            return graph.passes().zipWithNext().flatMap { (first, second) ->
                val producer = first as? PlanPass.PathRenderPass ?: return@flatMap emptyList()
                val cover = second as? PlanPass.PathRenderPass ?: return@flatMap emptyList()
                if (producer.phase !in setOf(
                        PathRenderPhase.SingleSampleStencilProducer,
                        PathRenderPhase.MultisampleStencilProducer,
                    )
                ) return@flatMap emptyList()
                val producerConsumer = authority.consumerFor(producer.id.value) as?
                    GPUW4ePreparedClipConsumerAuthority.InverseDomain
                    ?: return@flatMap emptyList()
                val coverConsumer = authority.consumerFor(cover.id.value) as?
                    GPUW4ePreparedClipConsumerAuthority.InverseDomain
                    ?: return@flatMap emptyList()
                if (producerConsumer.interiorCoverage !is GPUW4ePreparedInverseInteriorCoverage.Geometry ||
                    coverConsumer.interiorCoverage !is GPUW4ePreparedInverseInteriorCoverage.Geometry
                ) return@flatMap emptyList()
                val producerPacket = requireNotNull(packetIndex.byPassId[producer.id.value]) {
                    "W4e inverse producer is absent from root render order"
                }
                val coverPacket = requireNotNull(packetIndex.byPassId[cover.id.value]) {
                    "W4e inverse cover is absent from root render order"
                }
                GPUW4ePreparedInversePairOperation.issue(
                    producerPacket,
                    requireNotNull(authority.pathFor(producer.id.value)),
                    producerConsumer,
                    coverPacket,
                    requireNotNull(authority.pathFor(cover.id.value)),
                    coverConsumer,
                    requireNotNull(graph.w4eNativePayloadOrNull()),
                )
            }.associateBy(GPUW4ePreparedInversePairOperation::passId).also { operations ->
                val requiredPairPassIds = graph.passes().filterIsInstance<PlanPass.PathRenderPass>()
                    .filter { path ->
                        (authority.consumerFor(path.id.value) as?
                            GPUW4ePreparedClipConsumerAuthority.InverseDomain)
                            ?.interiorCoverage is GPUW4ePreparedInverseInteriorCoverage.Geometry &&
                            path.phase in setOf(
                            PathRenderPhase.SingleSampleStencilProducer,
                            PathRenderPhase.MultisampleStencilProducer,
                            PathRenderPhase.SingleSampleStencilColorCover,
                            PathRenderPhase.MultisampleStencilColorCover,
                        )
                    }.map { path -> path.id.value }.toSet()
                require(operations.keys == requiredPairPassIds &&
                    operations.values.all { operation -> operation.commandOperandRecipe.isNotEmpty() }
                ) { "W4e paired inverse Geometry pass lacks a root-issued operation witness" }
            }
        }

        private fun issueSealed(
            graphId: String,
            graphCapabilityId: String,
            frameId: Long,
            capabilitySealHash: String,
            renders: List<GPUTask.Render>,
            nativePayload: W4eNativePayloadPlan,
            rootResolve: RootResolveWitness?,
            inversePairOperations: Map<String, GPUW4ePreparedInversePairOperation> = emptyMap(),
            unsupportedMaskedInverseAaDirectOperations: Map<String, UnsupportedMaskedInverseAaDirectOperation> = emptyMap(),
            resourceInventory: GPUW4ePreparedResourceInventory? = null,
            rootIssued: Boolean,
        ): GPUW4ePreparedFrameAuthority {
            require(graphId.isNotBlank() && graphCapabilityId.isNotBlank() && capabilitySealHash.isNotBlank())
            require(renders.isNotEmpty()) { "W4e prepared frame requires ordered render scopes" }
            val facts = renders.map { render ->
                val packet = render.drawPackets.singleOrNull()
                    ?: throw IllegalArgumentException("W4e prepared frame requires one packet per render scope")
                require(packet.role == GPUDrawPacketRole.W4ePrepared &&
                    (packet.w4ePreparedClipPass == null) != (packet.w4ePreparedPath == null)
                ) { "W4e prepared frame requires one sealed pass authority per packet" }
                Fact(
                    render.taskId.value,
                    packet.passId,
                    packet.commandIdValue,
                    render.target.value,
                    render.resourceUses.map(::resourceUseFact),
                    atomicGroup(packet),
                    render.w4eMaskContinuation,
                    render.w4eSceneContinuation,
                )
            }
            return GPUW4ePreparedFrameAuthority(
                graphId,
                graphCapabilityId,
                frameId,
                capabilitySealHash,
                facts,
                rootResolve,
                rootIssued,
                inversePairOperations,
                unsupportedMaskedInverseAaDirectOperations,
                resourceInventory,
                nativePayload,
            )
        }

        fun atomicGroup(packet: GPUDrawPacket): String? =
            packet.w4ePreparedClipPass?.atomicGroupId ?: packet.w4ePreparedPath?.atomicGroupId

        fun resourceUseFact(use: GPUFrameResourceUse): String = listOf(
            use.resource::class.qualifiedName.orEmpty(),
            use.resource.value,
            use.role.name,
            use.usage.name,
            use.lifetime.name,
            use.write.toString(),
        ).joinToString("|")
    }
}
