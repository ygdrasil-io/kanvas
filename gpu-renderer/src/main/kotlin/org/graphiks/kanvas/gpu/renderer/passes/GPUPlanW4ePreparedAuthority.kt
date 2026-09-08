package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.ClipPlanStrategy
import org.graphiks.kanvas.gpu.plan.ClippedBinaryMaskedPathDraw
import org.graphiks.kanvas.gpu.plan.ClippedGeneralPathDraw
import org.graphiks.kanvas.gpu.plan.PlanResource
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.W4eClipPlanCompiler
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.math.geometry.InverseInteriorCoverageF32
import org.graphiks.math.geometry.InversePathGeometryF32
import org.graphiks.math.geometry.PathFillGeometryF32

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

/** Explicit prepared-pass handoff contract for Task 7 command encoding. */
public class GPUW4ePreparedClipPassAuthority internal constructor(
    public val passId: String,
    public val kind: Kind,
    resourceIds: List<String>,
) {
    public enum class Kind { Initialize, Producer, Fold }

    /** Resource identities copied in exact sealed-pass order. */
    public val resourceIds: List<String> = resourceIds.toList()

    init {
        require(passId.isNotBlank()) { "W4e prepared pass ID must not be blank" }
        require(resourceIds.isNotEmpty()) { "W4e prepared pass must name its sealed resources" }
    }
}

/** Immutable W4e trust boundary: lowering consumes only a compiler-sealed graph snapshot. */
internal class GPUPlanW4ePreparedAuthority private constructor(
    private val planId: String,
    private val capabilityId: String,
    private val resourceFacts: List<String>,
    private val passFacts: List<String>,
    private val consumersByPassId: Map<String, GPUW4ePreparedClipConsumerAuthority>,
    private val passesById: Map<String, GPUW4ePreparedClipPassAuthority>,
) {
    /** Returns only an already-copied W4e fact; it never maps or reclassifies a clip. */
    fun consumerFor(passId: String): GPUW4ePreparedClipConsumerAuthority? = consumersByPassId[passId]

    /** Returns only an explicit prepared Task 7 handoff contract. */
    fun clipPassFor(passId: String): GPUW4ePreparedClipPassAuthority? = passesById[passId]

    fun revalidates(graph: RenderGraph): Boolean =
        graph.verifyW4eCompilerWitness() &&
            graph.id.value == planId &&
            graph.capabilityId == capabilityId &&
            graph.resources().map(PlanResource::id).map { it.value } == resourceFacts &&
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
                graph.resources().map(PlanResource::id).map { it.value },
                graph.passes().map(PlanPass::id).map { it.value },
                graph.passes().filterIsInstance<PlanPass.PathRenderPass>().mapNotNull { pass ->
                    consumerFact(pass, graph)
                }.associateBy(GPUW4ePreparedClipConsumerAuthority::consumerPassId),
                graph.passes().mapNotNull(::clipPassFact).associateBy(GPUW4ePreparedClipPassAuthority::passId),
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

        private fun clipPassFact(pass: PlanPass): GPUW4ePreparedClipPassAuthority? = when (pass) {
            is PlanPass.ClipMaskInitialize -> GPUW4ePreparedClipPassAuthority(
                pass.id.value, GPUW4ePreparedClipPassAuthority.Kind.Initialize, listOf(pass.output.value),
            )
            is PlanPass.ClipMaskProducer -> GPUW4ePreparedClipPassAuthority(
                pass.id.value,
                GPUW4ePreparedClipPassAuthority.Kind.Producer,
                listOfNotNull(pass.target.value, pass.resolveTarget?.value, pass.depthStencil?.value),
            )
            is PlanPass.ClipMaskFold -> GPUW4ePreparedClipPassAuthority(
                pass.id.value,
                GPUW4ePreparedClipPassAuthority.Kind.Fold,
                listOf(pass.previous.value, pass.source.value, pass.output.value),
            )
            else -> null
        }

        private fun domainFor(graph: RenderGraph): GPUPixelBounds =
            GPUPixelBounds(0, 0, graph.targetExtent.width, graph.targetExtent.height)

        private fun domainFor(geometry: InversePathGeometryF32): GPUPixelBounds =
            geometry.copyDomainI32().let { domain ->
                GPUPixelBounds(domain.left, domain.top, domain.right, domain.bottom)
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
