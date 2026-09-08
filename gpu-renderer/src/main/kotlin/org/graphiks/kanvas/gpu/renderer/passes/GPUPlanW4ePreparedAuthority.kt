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
import org.graphiks.kanvas.gpu.plan.PathDrawGeometry
import org.graphiks.kanvas.gpu.plan.PathRenderPhase
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.SamplePlan
import org.graphiks.kanvas.gpu.plan.W4eClipPlanCompiler
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.math.geometry.InverseInteriorCoverageF32
import org.graphiks.math.geometry.InversePathGeometryF32
import org.graphiks.math.geometry.PathFillGeometryF32
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
        public val inverseCoverage: Boolean,
        public val antiAlias: Boolean,
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
        public val color: org.graphiks.math.color.ColorF32,
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
    ) {
        private val geometrySnapshot: PathDrawGeometry = geometry
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
    private val planId: String,
    private val capabilityId: String,
    private val resourceFacts: List<String>,
    private val passFacts: List<String>,
    private val consumersByPassId: Map<String, GPUW4ePreparedClipConsumerAuthority>,
    private val passesById: Map<String, GPUW4ePreparedClipPassAuthority>,
    private val pathsById: Map<String, GPUW4ePreparedClipPassAuthority.Path>,
) {
    /** Returns only an already-copied W4e fact; it never maps or reclassifies a clip. */
    fun consumerFor(passId: String): GPUW4ePreparedClipConsumerAuthority? = consumersByPassId[passId]

    /** Returns only an explicit prepared Task 7 handoff contract. */
    fun clipPassFor(passId: String): GPUW4ePreparedClipPassAuthority? = passesById[passId]

    fun pathFor(passId: String): GPUW4ePreparedClipPassAuthority.Path? = pathsById[passId]

    fun revalidates(graph: RenderGraph): Boolean =
        graph.verifyW4eCompilerWitness() &&
            graph.id.value == planId &&
            graph.capabilityId == capabilityId &&
            graph.resources().map(::resourceFact) == resourceFacts &&
            graph.passes().map(PlanPass::id).map { it.value } == passFacts

    companion object {
        fun issueAfterFullGraphValidation(graph: RenderGraph): GPUPlanW4ePreparedAuthority {
            require(graph.capabilityId in setOf(
                W4eClipPlanCompiler.HARD_CAPABILITY_ID,
                W4eClipPlanCompiler.AA_CAPABILITY_ID,
            ) && graph.verifyW4eCompilerWitness()) {
                "W4e prepared authority requires the compiler-authenticated graph"
            }
            return GPUPlanW4ePreparedAuthority(
                graph.id.value,
                graph.capabilityId,
                graph.resources().map(::resourceFact),
                graph.passes().map(PlanPass::id).map { it.value },
                graph.passes().filterIsInstance<PlanPass.PathRenderPass>().mapNotNull { pass ->
                    consumerFact(pass, graph)
                }.associateBy(GPUW4ePreparedClipConsumerAuthority::consumerPassId),
                graph.passes().mapNotNull(::clipPassFact).associateBy(GPUW4ePreparedClipPassAuthority::passId),
                graph.passes().filterIsInstance<PlanPass.PathRenderPass>().associate { pass ->
                    pass.id.value to GPUW4ePreparedClipPassAuthority.Path(
                        pass.id.value,
                        pass.draw.commandIndex,
                        pass.phase,
                        pass.draw.color,
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
                    )
                },
            )
        }

        private fun consumerFact(
            pass: PlanPass.PathRenderPass,
            graph: RenderGraph,
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
                    domainFor(graph),
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
                pass.sampleCountI32, clipGeometryFor(pass.copyGeometryF32()), pass.inverseCoverage, pass.antiAlias,
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
