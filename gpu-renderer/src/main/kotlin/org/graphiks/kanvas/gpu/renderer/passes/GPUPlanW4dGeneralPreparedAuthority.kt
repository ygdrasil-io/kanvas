package org.graphiks.kanvas.gpu.renderer.passes

import java.util.Collections
import org.graphiks.kanvas.gpu.plan.BinaryMaskFetchPlan
import org.graphiks.kanvas.gpu.plan.BinaryMaskedPathDraw
import org.graphiks.kanvas.gpu.plan.CoveragePlan
import org.graphiks.kanvas.gpu.plan.GeneralPathDraw
import org.graphiks.kanvas.gpu.plan.PathRenderPhase
import org.graphiks.kanvas.gpu.plan.PlanPass
import org.graphiks.kanvas.gpu.plan.RenderGraph
import org.graphiks.kanvas.gpu.plan.SamplePlan
import org.graphiks.kanvas.gpu.plan.W4dGeneralPathPlanCompiler
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey

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
) {
    private val passFacts: List<W4dGeneralPreparedPassFact> =
        Collections.unmodifiableList(passFacts.toList())
    public val binaryMaskCoverageContracts: List<GPUW4dBinaryMaskCoverageContract> =
        Collections.unmodifiableList(binaryMaskCoverageContracts.toList())

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
        return fact == passFact(pass) &&
            binaryContracts.all { it in binaryMaskCoverageContracts } &&
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
            return GPUPlanW4dGeneralPreparedAuthority(
                VERSION,
                graph.id.value,
                graph.capabilityId,
                facts,
                binaryContracts,
                continuation,
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
            val expectedRole = when (pass.phase) {
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
            val expectedPacketRole = when (expectedRole) {
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilProducer ->
                    GPUDrawPacketRole.PathStencilProducer
                GPUCorePrimitiveRenderPipelineStructuralKey.Role.PathStencilCover ->
                    GPUDrawPacketRole.PathStencilCover
                else -> GPUDrawPacketRole.Shading
            }
            val draw = pass.draw
            val source = when (draw) {
                is GeneralPathDraw -> W4dGeneralPreparedDrawFact(
                    isBinaryMaskCover = false,
                    maskResourceId = null,
                    sourceCommandIdValue = draw.commandIndex,
                )
                is BinaryMaskedPathDraw -> W4dGeneralPreparedDrawFact(
                    isBinaryMaskCover = true,
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

private data class W4dGeneralPreparedDrawFact(
    val isBinaryMaskCover: Boolean,
    val maskResourceId: String?,
    val sourceCommandIdValue: Int,
)
