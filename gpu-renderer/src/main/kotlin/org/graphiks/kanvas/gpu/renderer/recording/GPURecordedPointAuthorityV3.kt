package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.gpu.plan.BlendPlan
import org.graphiks.kanvas.gpu.plan.MaterialPlanRef
import org.graphiks.kanvas.gpu.plan.MaterialPlanTable
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUDrawPacket
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.payloads.*
import org.graphiks.kanvas.gpu.renderer.planning.W5bBlendPlanLowerer
import org.graphiks.kanvas.render.ir.ClipStackNode

/** Recorder-owned identity, issued before any prepared route rematerializes the packet. */
class GPURecordedPointAuthorityV3 private constructor(
    private val packet: GPUDrawPacket,
    private val sourceRef: MaterialPlanRef,
) {
    internal fun owns(candidate: GPUDrawPacket): Boolean = candidate === packet &&
        candidate.corePrimitivePreparedAuthority == null && candidate.w5aSourceStageV2 == null &&
        candidate.w5aCompositeFrameAuthority == null && candidate.w4ePreparedFrameAuthority == null &&
        candidate.coverageMaskProducerUniformSlabSeal == null && candidate.corePrimitiveClipStencilPreparedCandidate == null

    /** Binds the authentic gatherer's geometry once; materialization may only replace its source/uniform. */
    fun capturePrepared(
        semantic: GPUDrawSemanticPayload.CorePrimitive,
        blend: BlendPlan,
        clip: ClipStackNode?,
        table: MaterialPlanTable,
        ref: MaterialPlanRef,
    ): W5bPreparedPointCaptureV3 {
        require(owns(packet) && semantic.hasCanonicalHashIntegrity())
        require(semantic.sourceFamily == GPUCorePrimitiveSourceFamily.PointLine &&
            (semantic.geometry as? GPUCorePrimitiveGeometry.TriangulatedPath)?.geometryMode == GPUCorePrimitiveGeometryMode.DirectTriangles)
        require(semantic.payloadRef.commandIdValue == packet.commandIdValue &&
            semantic.frameProvenance == packet.frameProvenance &&
            semantic.clipCoveragePlan == packet.clipCoveragePlan &&
            semantic.blendPlanIdentity == packet.blendPlan?.canonicalIdentity())
        require((semantic.material as? GPUCorePrimitiveMaterialPayload.W5aMaterialPlanRefV1)?.ref == sourceRef)
        require(semantic.payloadRef.resourceSlot == null && semantic.payloadRef.resourceBlock == null &&
            semantic.payloadRef.gradientStore == null)
        require(semantic.clipExecutionPlanIdentity?.let { it == packet.clipExecutionPlan?.canonicalIdentity() } != false)
        require(blendMatches(packet.blendPlan, blend)) { "W5b requested blend contradicts its recorder packet" }
        return W5bPreparedPointCaptureV3(this, semantic, blend, clip, table, ref, sourceRef)
    }

    companion object {
        internal fun issue(command: NormalizedDrawCommand, packet: GPUDrawPacket): GPURecordedPointAuthorityV3? {
            if (command !is NormalizedDrawCommand.FillPath ||
                command.source.operation !in setOf("drawPoint", "drawPoints.points")) return null
            val ref = command.w5aMaterialPlanRef ?: return null
            require(command.commandId.value == packet.commandIdValue &&
                command.ordering.paintOrder == packet.originalPaintOrder)
            return GPURecordedPointAuthorityV3(packet, ref)
        }

        private fun blendMatches(recorded: GPUBlendPlan?, requested: BlendPlan): Boolean = when (requested) {
            is BlendPlan.DestinationReadV1 -> recorded is GPUBlendPlan.ShaderBlendWithDstRead &&
                recorded.mode.name == requested.mode.name && recorded.formulaId == requested.formulaIdentity &&
                recorded.sealedW5b == null && requested.snapshotResource == null &&
                requested.requiredDestinationVersion.valueI64 == 0L && requested.compositionAbiI32 == 3 &&
                requested.coverage == org.graphiks.kanvas.gpu.plan.BlendCoverageEncodingV1.FullOrScissor
            is BlendPlan.FixedFunctionV1 -> {
                val lowered = W5bBlendPlanLowerer.lower(requested) as GPUBlendPlan.FixedFunctionBlend
                when (recorded) {
                    is GPUBlendPlan.FixedFunctionBlend -> recorded.mode == lowered.mode &&
                        recorded.state.color == lowered.state.color && recorded.state.alpha == lowered.state.alpha &&
                        recorded.state.writeMask == lowered.state.writeMask &&
                        recorded.sourceCoverageEncoding == lowered.sourceCoverageEncoding
                    // Legacy PLUS records its exact shader formula; W5b may promote full/scissor to fixed ONE/ONE.
                    is GPUBlendPlan.ShaderBlendWithDstRead -> requested.mode.name == "PLUS" &&
                        recorded.mode.name == "PLUS" && recorded.formulaId == "plus_exact@v1" && recorded.sealedW5b == null &&
                        listOf(requested.colorSource, requested.colorDestination, requested.alphaSource, requested.alphaDestination)
                            .all { it == org.graphiks.kanvas.gpu.plan.BlendFactorV1.One } &&
                        requested.coverage == org.graphiks.kanvas.gpu.plan.BlendCoverageEncodingV1.FullOrScissor
                    else -> false
                }
            }
            BlendPlan.LegacySrcOverV1 -> recorded == W5bBlendPlanLowerer.lower(requested)
            BlendPlan.NoOpV1 -> recorded is GPUBlendPlan.NoOp
        }
    }
}

/** Immutable join of recorder identity, original Point geometry and the frame's material/clip plans. */
class W5bPreparedPointCaptureV3 internal constructor(
    private val recorded: GPURecordedPointAuthorityV3,
    private val original: GPUDrawSemanticPayload.CorePrimitive,
    private val blend: BlendPlan,
    private val clip: ClipStackNode?,
    private val table: MaterialPlanTable,
    private val ref: MaterialPlanRef,
    private val sourceRef: MaterialPlanRef,
) {
    internal fun validates(packet: GPUDrawPacket, semantic: GPUDrawSemanticPayload.CorePrimitive,
        requestedBlend: BlendPlan?, requestedClip: ClipStackNode?): Boolean {
        val material = (semantic.material as? GPUCorePrimitiveMaterialPayload.SolidColor)?.w5aAuthority ?: return false
        return recorded.owns(packet) && requestedBlend == blend && requestedClip === clip &&
            material.validates(packet.commandIdValue) && material.sourcePlanTable === table &&
            material.ref == ref && material.sourceRef == sourceRef &&
            semantic.hasCanonicalHashIntegrity() && original.hasCanonicalHashIntegrity() &&
            semantic.payloadRef.commandIdValue == original.payloadRef.commandIdValue &&
            semantic.payloadRef.resourceSlot == original.payloadRef.resourceSlot &&
            semantic.payloadRef.resourceBlock == original.payloadRef.resourceBlock &&
            semantic.payloadRef.gradientStore == original.payloadRef.gradientStore &&
            semantic.sourceFamily == original.sourceFamily && semantic.geometry === original.geometry &&
            semantic.targetBounds == original.targetBounds && semantic.scissorBounds == original.scissorBounds &&
            semantic.clipCoveragePlan == original.clipCoveragePlan &&
            semantic.clipExecutionPlanIdentity == original.clipExecutionPlanIdentity &&
            semantic.blendPlanIdentity == original.blendPlanIdentity && semantic.frameProvenance == original.frameProvenance &&
            semantic.coverageMode == original.coverageMode && semantic.analysisRecordId == original.analysisRecordId &&
            semantic.analysisCommandFamily == original.analysisCommandFamily &&
            semantic.rectRouteAuthority == original.rectRouteAuthority && semantic.rectGeometryAuthority == original.rectGeometryAuthority &&
            semantic.rrectGeometryAuthority == original.rrectGeometryAuthority &&
            semantic.drrectOuterGeometryAuthority == original.drrectOuterGeometryAuthority &&
            semantic.drrectInnerGeometryAuthority == original.drrectInnerGeometryAuthority
    }
}
