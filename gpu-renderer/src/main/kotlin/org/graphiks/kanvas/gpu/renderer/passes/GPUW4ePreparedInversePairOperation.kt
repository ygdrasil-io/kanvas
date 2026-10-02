package org.graphiks.kanvas.gpu.renderer.passes

import org.graphiks.kanvas.gpu.plan.AttachmentLoadPlan
import org.graphiks.kanvas.gpu.plan.AttachmentStorePlan
import org.graphiks.kanvas.gpu.plan.PathRenderPhase
import org.graphiks.kanvas.gpu.plan.PlanDepthStencilLoadStore
import org.graphiks.kanvas.gpu.plan.SamplePlan
import org.graphiks.kanvas.gpu.plan.W4eNativeGeometrySlice
import org.graphiks.kanvas.gpu.plan.W4eNativePayloadPlan
import org.graphiks.kanvas.gpu.plan.W4eNativeUniformSlice
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds

/**
 * One root-graph-authenticated half of a standalone finite inverse operation.
 *
 * The graph publishes two adjacent path passes, but each has one non-overlapping native job:
 * the producer writes the finite interior to stencil and the cover shades its zero complement.
 * Packet phase and nullable consumer fields are deliberately insufficient to manufacture this
 * witness; [issue] receives the authenticated adjacent packets and their sealed graph facts.
 */
internal class GPUW4ePreparedInversePairOperation private constructor(
    private val packet: GPUDrawPacket,
    private val path: GPUW4ePreparedClipPassAuthority.Path,
    private val consumer: GPUW4ePreparedClipConsumerAuthority.InverseDomain,
    private val peerPacket: GPUDrawPacket,
    private val peerPath: GPUW4ePreparedClipPassAuthority.Path,
    private val peerConsumer: GPUW4ePreparedClipConsumerAuthority.InverseDomain,
    internal val role: Role,
    internal val commandOperandRecipe: List<CommandOperand>,
    internal val domain: GPUPixelBounds,
    internal val interiorIsDirectTriangle: Boolean,
    internal val interiorUsesParity: Boolean,
    internal val targetResourceId: String,
    internal val depthStencilResourceId: String,
    internal val sample: SamplePlan,
    internal val load: AttachmentLoadPlan,
    internal val store: AttachmentStorePlan,
    internal val depthStencilLoadStore: PlanDepthStencilLoadStore,
    internal val interiorSlice: W4eNativeGeometrySlice?,
    internal val inverseUniformSlice: W4eNativeUniformSlice?,
    internal val finalResolveOwnerPassId: String?,
) {
    internal val passId: String get() = path.passId

    internal enum class Role { Producer, Cover }

    /** Ordered native operands after the exact render attachments. */
    internal enum class CommandOperand { Pipeline, Vertex, Index, BindGroup }

    internal fun owns(candidate: GPUDrawPacket, authority: GPUW4ePreparedFrameAuthority): Boolean =
        candidate === packet &&
            candidate.w4ePreparedFrameAuthority === authority &&
            candidate.w4ePreparedPath === path &&
            candidate.w4ePreparedClipConsumer === consumer &&
            peerPacket.w4ePreparedFrameAuthority === authority &&
            peerPacket.w4ePreparedPath === peerPath &&
            peerPacket.w4ePreparedClipConsumer === peerConsumer

    internal fun bindingSuffix(operand: CommandOperand): String = when (role to operand) {
        Role.Producer to CommandOperand.Pipeline -> "inverse-domain-interior"
        Role.Producer to CommandOperand.Vertex -> "inverse-domain-interior-vertices"
        Role.Producer to CommandOperand.Index -> "inverse-domain-interior-indices"
        Role.Cover to CommandOperand.Pipeline -> "inverse-domain-cover"
        Role.Cover to CommandOperand.BindGroup -> "inverse-domain-color"
        else -> error("W4e inverse pair recipe contains an invalid operand")
    }

    internal companion object {
        internal fun issue(
            producerPacket: GPUDrawPacket,
            producerPath: GPUW4ePreparedClipPassAuthority.Path,
            producerConsumer: GPUW4ePreparedClipConsumerAuthority.InverseDomain,
            coverPacket: GPUDrawPacket,
            coverPath: GPUW4ePreparedClipPassAuthority.Path,
            coverConsumer: GPUW4ePreparedClipConsumerAuthority.InverseDomain,
            nativePayload: W4eNativePayloadPlan,
        ): List<GPUW4ePreparedInversePairOperation> {
            require(producerPacket.w4ePreparedPath === producerPath &&
                producerPacket.w4ePreparedClipConsumer === producerConsumer &&
                coverPacket.w4ePreparedPath === coverPath &&
                coverPacket.w4ePreparedClipConsumer === coverConsumer) {
                "W4e inverse pair requires the exact graph-attached packet/path/consumer identities"
            }
            require(producerPath.phase in setOf(
                PathRenderPhase.SingleSampleStencilProducer,
                PathRenderPhase.MultisampleStencilProducer,
            ) && coverPath.phase in setOf(
                PathRenderPhase.SingleSampleStencilColorCover,
                PathRenderPhase.MultisampleStencilColorCover,
            )) { "W4e inverse pair requires producer/cover path phases" }
            require(producerPath.commandIdValue == coverPath.commandIdValue &&
                producerPath.atomicGroupId != null && producerPath.atomicGroupId == coverPath.atomicGroupId &&
                producerPath.targetResourceId == coverPath.targetResourceId &&
                producerPath.depthStencilResourceId != null &&
                producerPath.depthStencilResourceId == coverPath.depthStencilResourceId &&
                producerPath.sample == coverPath.sample &&
                producerPath.vertexResourceId == coverPath.vertexResourceId &&
                producerPath.indexResourceId == coverPath.indexResourceId &&
                producerPath.uniformResourceId == coverPath.uniformResourceId &&
                producerConsumer.domain == coverConsumer.domain &&
                producerConsumer.interiorCoverage is GPUW4ePreparedInverseInteriorCoverage.Geometry &&
                coverConsumer.interiorCoverage is GPUW4ePreparedInverseInteriorCoverage.Geometry &&
                producerPath.depthStencilLoadStore == PlanDepthStencilLoadStore.ClearZeroStore &&
                coverPath.depthStencilLoadStore == PlanDepthStencilLoadStore.LoadStoreTestReset &&
                producerPath.resolveTargetResourceId == null
            ) { "W4e inverse pair diverged from its graph-sealed producer/cover contract" }
            val interior = requireNotNull(nativePayload.geometrySlice(
                producerPath.passId,
                W4eNativePayloadPlan.INVERSE_DOMAIN_INTERIOR,
            )) { "W4e inverse pair producer lacks its sealed interior V/I slice" }
            val uniform = requireNotNull(nativePayload.uniformSlice(
                coverPath.passId,
                W4eNativePayloadPlan.INVERSE_DOMAIN_UNIFORM,
            )) { "W4e inverse pair cover lacks its sealed inverse uniform slice" }
            val finalResolveOwner = coverPath.resolveTargetResourceId?.let { coverPath.passId }
            val producerInterior = producerConsumer.interiorCoverage as
                GPUW4ePreparedInverseInteriorCoverage.Geometry
            val interiorGeometry = producerInterior.copyGeometryF32()
            val interiorIsDirectTriangle = interiorGeometry.copyDirectTriangleF32OrNull() != null
            val interiorUsesParity = interiorGeometry.fillRule.let { fillRule ->
                    fillRule == org.graphiks.math.geometry.FillRule.EVEN_ODD ||
                        fillRule == org.graphiks.math.geometry.FillRule.INVERSE_EVEN_ODD
                }
            return listOf(
                GPUW4ePreparedInversePairOperation(
                    producerPacket, producerPath, producerConsumer, coverPacket, coverPath, coverConsumer,
                    Role.Producer, listOf(CommandOperand.Pipeline, CommandOperand.Vertex, CommandOperand.Index),
                    producerConsumer.domain, interiorIsDirectTriangle, interiorUsesParity, producerPath.targetResourceId,
                    requireNotNull(producerPath.depthStencilResourceId), producerPath.sample,
                    producerPath.load, producerPath.store,
                    requireNotNull(producerPath.depthStencilLoadStore), interior, null, finalResolveOwner,
                ),
                GPUW4ePreparedInversePairOperation(
                    coverPacket, coverPath, coverConsumer, producerPacket, producerPath, producerConsumer,
                    Role.Cover, listOf(CommandOperand.Pipeline, CommandOperand.BindGroup),
                    coverConsumer.domain, interiorIsDirectTriangle, interiorUsesParity, coverPath.targetResourceId,
                    requireNotNull(coverPath.depthStencilResourceId), coverPath.sample,
                    coverPath.load, coverPath.store,
                    requireNotNull(coverPath.depthStencilLoadStore), null, uniform, finalResolveOwner,
                ),
            )
        }
    }
}
