package org.graphiks.kanvas.surface.gpu

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.sqrt
import org.graphiks.kanvas.gpu.renderer.analysis.GPUDrawAnalysisRecord
import org.graphiks.kanvas.gpu.renderer.analysis.matchesCorePrimitiveRectGeometry
import org.graphiks.kanvas.gpu.renderer.analysis.matchesCorePrimitiveRRectGeometry
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipCoveragePlan
import org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformFacts
import org.graphiks.kanvas.gpu.renderer.commands.GPUTransformType
import org.graphiks.kanvas.gpu.renderer.commands.NormalizedDrawCommand
import org.graphiks.kanvas.gpu.renderer.commands.isPositiveUniformScaleTranslateGradientLocalMatrix
import org.graphiks.kanvas.gpu.renderer.commands.isBoundedNativePathHairline
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.geometry.FlattenedPath
import org.graphiks.kanvas.gpu.renderer.geometry.PathTessellator
import org.graphiks.kanvas.gpu.renderer.geometry.Point as GPUPathPoint
import org.graphiks.kanvas.gpu.renderer.passes.GPUCoverageConsumption
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlan
import org.graphiks.kanvas.gpu.renderer.filters.MaskBlurPlanner
import org.graphiks.kanvas.gpu.renderer.filters.NormalizedMaskFilter
import org.graphiks.kanvas.gpu.renderer.filters.blurKernelUniform
import org.graphiks.kanvas.gpu.renderer.payloads.GPUMaskBlurLocalGeometry
import org.graphiks.kanvas.gpu.renderer.payloads.GPUMaskBlurPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveCoverageMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveFillRule
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveGeometryMode
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveMaterialPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitivePayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRectRouteAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveRRectGeometryAuthority
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveSourceFamily
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveStrokeLoweringProof
import org.graphiks.kanvas.gpu.renderer.payloads.GPUCorePrimitiveStrokeStyle
import org.graphiks.kanvas.gpu.renderer.payloads.isBoundedHorizontalDashedPhase
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.sealedDeviceGeometryInput
import org.graphiks.kanvas.gpu.renderer.recording.GPURecording
import org.graphiks.kanvas.gpu.renderer.recording.GPUTask
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.surface.RenderConfig

internal sealed interface GPUCorePrimitiveSemanticGatherResult {
    data class Gathered(val semantics: Map<Int, GPUDrawSemanticPayload>) :
        GPUCorePrimitiveSemanticGatherResult

    data class Refused(
        val code: String,
        val message: String,
        val facts: Map<String, String>,
    ) : GPUCorePrimitiveSemanticGatherResult
}

/** Lossless bridge used by heterogeneous surface gathering without rebuilding core semantics. */
internal fun GPUCorePrimitiveSemanticGatherResult.Gathered.asPreparedSurfaceSemanticMap():
    Map<Int, GPUDrawSemanticPayload> = semantics

internal enum class GPUCorePrimitiveBlendAuthorityPolicy {
    /** Product work requires one exact blend authority from the recorded draw packet. */
    Required,

    /** Diagnostic inventory may lower mapper semantics even when recording was atomically refused. */
    InventoryHarness,
}

internal enum class GPUCorePrimitiveColorTransform {
    Identity,
    SrgbToLinear,
}

data class GPUCorePrimitiveGeometryRefusal(
    val code: String,
    val refusalFacts: Map<String, String>,
)

private class GPUCorePrimitiveGeometryRefusalException(
    val refusal: GPUCorePrimitiveGeometryRefusal,
) : RuntimeException(refusal.code)

/** Production boundary for exact, handle-free core primitive semantic gathering. */
internal object GPUCorePrimitiveSemanticBuilder {
    fun gather(
        visualCommands: List<GPUFramePathVisualCommand>,
        recording: GPURecording,
        targetBounds: GPUPixelBounds,
        blendAuthorityPolicy: GPUCorePrimitiveBlendAuthorityPolicy =
            GPUCorePrimitiveBlendAuthorityPolicy.Required,
        colorTransform: GPUCorePrimitiveColorTransform =
            GPUCorePrimitiveColorTransform.Identity,
    ): GPUCorePrimitiveSemanticGatherResult {
        val gatherer = GPUCorePrimitivePayloadGatherer()
        val semantics = linkedMapOf<Int, GPUDrawSemanticPayload>()
        val analysisRecordsByCommandId = recording.analysis.records
            .groupBy(GPUDrawAnalysisRecord::commandIdValue)
        val recordingPacketsByCommandId = recording.taskList.tasks
            .filterIsInstance<GPUTask.Render>()
            .flatMap(GPUTask.Render::drawPackets)
            .groupBy { packet -> packet.commandIdValue }
        visualCommands.forEach { visual ->
            visual.geometryRefusal?.let { refusal ->
                return refusal.toGatherRefusal(visual)
            }
            val commandIdValue = visual.normalized.commandId.value
            val matchingAnalysisRecords = analysisRecordsByCommandId[commandIdValue].orEmpty()
            if (matchingAnalysisRecords.size != 1) {
                return GPUCorePrimitiveGeometryRefusal(
                    code = "unsupported.core_primitive.analysis_record_bijection",
                    refusalFacts = mapOf(
                        "matchingRecordCount" to matchingAnalysisRecords.size.toString(),
                    ),
                ).toGatherRefusal(visual)
            }
            val analysisRecord = matchingAnalysisRecords.single()
            val recordingBlendPlanIdentity = when (blendAuthorityPolicy) {
                GPUCorePrimitiveBlendAuthorityPolicy.Required -> {
                    val matchingRecordingPackets = recordingPacketsByCommandId[commandIdValue].orEmpty()
                    if (matchingRecordingPackets.size != 1) {
                        return GPUCorePrimitiveGeometryRefusal(
                            code = "unsupported.core_primitive.recording_packet_bijection",
                            refusalFacts = mapOf(
                                "matchingPacketCount" to matchingRecordingPackets.size.toString(),
                            ),
                        ).toGatherRefusal(visual)
                    }
                    matchingRecordingPackets.single().blendPlan
                        ?.canonicalIdentity()
                        ?: return GPUCorePrimitiveGeometryRefusal(
                            code = "unsupported.core_primitive.recording_blend_authority_missing",
                            refusalFacts = mapOf("analysisRecordId" to analysisRecord.recordId),
                        ).toGatherRefusal(visual)
                }
                GPUCorePrimitiveBlendAuthorityPolicy.InventoryHarness ->
                    visual.blendPlan.canonicalIdentity()
            }
            val expectedAnalysisFamily = visual.normalized.analysisCommandFamily()
            if (analysisRecord.commandFamily != expectedAnalysisFamily) {
                return GPUCorePrimitiveGeometryRefusal(
                    code = "unsupported.core_primitive.analysis_command_family_mismatch",
                    refusalFacts = mapOf(
                        "analysisRecordId" to analysisRecord.recordId,
                        "analysisCommandFamily" to analysisRecord.commandFamily,
                        "normalizedCommandFamily" to expectedAnalysisFamily,
                    ),
                ).toGatherRefusal(visual)
            }
            val expectedAnalysisRecordId = visual.normalized.analysisRecordId()
            if (analysisRecord.recordId != expectedAnalysisRecordId) {
                return GPUCorePrimitiveGeometryRefusal(
                    code = "unsupported.core_primitive.analysis_record_id_mismatch",
                    refusalFacts = mapOf(
                        "analysisRecordId" to analysisRecord.recordId,
                        "expectedAnalysisRecordId" to expectedAnalysisRecordId,
                    ),
                ).toGatherRefusal(visual)
            }
            val semantic = if (visual.normalized.maskFilterOrNull() != null) {
                try {
                    visual.gatherMaskBlurSemantic(
                        analysisRecord = analysisRecord,
                        recordingBlendPlanIdentity = recordingBlendPlanIdentity,
                        targetBounds = targetBounds,
                        colorTransform = colorTransform,
                    )
                } catch (failure: GPUCorePrimitiveGeometryRefusalException) {
                    return failure.refusal.toGatherRefusal(visual)
                }
            } else {
                try {
                    gatherer.gatherSemantic(
                        visual.toCorePrimitiveInput(
                            targetBounds = targetBounds,
                            analysisRecord = analysisRecord,
                            recordingBlendPlanIdentity = recordingBlendPlanIdentity,
                            colorTransform = colorTransform,
                        ),
                    )
                } catch (failure: GPUCorePrimitiveGeometryRefusalException) {
                    return failure.refusal.toGatherRefusal(visual)
                } catch (failure: IllegalArgumentException) {
                    val stableCode = failure.message
                        ?.takeIf { message -> message.startsWith("unsupported.core_primitive.") }
                        ?: "unsupported.core_primitive.geometry.invalid"
                    return GPUCorePrimitiveGeometryRefusal(
                        code = stableCode,
                        refusalFacts = mapOf("reason" to (failure.message ?: "invalid_geometry")),
                    ).toGatherRefusal(visual)
                }
            }
            semantics[visual.normalized.commandId.value] = semantic
        }
        return GPUCorePrimitiveSemanticGatherResult.Gathered(semantics)
    }
}

private fun GPUCorePrimitiveGeometryRefusal.toGatherRefusal(
    visual: GPUFramePathVisualCommand,
): GPUCorePrimitiveSemanticGatherResult.Refused = GPUCorePrimitiveSemanticGatherResult.Refused(
    code = code,
    message = "Core primitive geometry cannot be lowered exactly by the current canonical route.",
    facts = refusalFacts + mapOf(
        "commandId" to visual.normalized.commandId.value.toString(),
        "source" to visual.normalized.source.operation,
    ),
)

/** Returns the normalized mask filter of one core draw command, or null for others. */
private fun NormalizedDrawCommand.maskFilterOrNull(): NormalizedMaskFilter? = when (this) {
    is NormalizedDrawCommand.FillRect -> maskFilter
    is NormalizedDrawCommand.FillRRect -> maskFilter
    is NormalizedDrawCommand.FillPath -> maskFilter
    else -> null
}

/** Gathers the closed prepared top-level mask blur semantic for one blur draw. */
private fun GPUFramePathVisualCommand.gatherMaskBlurSemantic(
    targetBounds: GPUPixelBounds,
    analysisRecord: GPUDrawAnalysisRecord,
    recordingBlendPlanIdentity: String,
    colorTransform: GPUCorePrimitiveColorTransform,
): GPUDrawSemanticPayload {
    val normalized = normalized
    val normalizedMaterial = normalized.material as? GPUMaterialDescriptor.SolidColor
        ?: refuseGeometry(
            "unsupported.core_primitive.mask_blur.material_non_solid",
            mapOf(
                "materialKind" to normalized.material?.let { it::class.simpleName }.orEmpty(),
            ),
        )
    val blur = normalized.maskFilterOrNull() as? NormalizedMaskFilter.Blur
        ?: refuseGeometry(
            "unsupported.core_primitive.mask_blur.kind",
            mapOf("maskFilterKind" to normalized.maskFilterOrNull()?.let { it::class.simpleName }.orEmpty()),
        )
    normalized.maskBlurPreflightRefusalReasonOrNull()?.let { reason ->
        refuseGeometry(
            "unsupported.core_primitive.mask_blur.$reason",
            mapOf("reason" to reason),
        )
    }
    val alpha = normalizedMaterial.a
    val request = normalized.toMaskBlurRequest(
        targetWidth = targetBounds.width,
        targetHeight = targetBounds.height,
        maxTextureDimension2D = MAX_MASK_BLUR_TEXTURE_DIMENSION,
        config = RenderConfig.DEFAULT,
    )
    val plan = when (val planned = MaskBlurPlanner.plan(request)) {
        is MaskBlurPlan.Ready -> planned
        MaskBlurPlan.Identity -> refuseGeometry(
            "unsupported.core_primitive.mask_blur.zero_sigma",
            mapOf("sigma" to blur.sigma.toString()),
        )
        is MaskBlurPlan.Refused -> refuseGeometry(
            planned.code,
            mapOf("sigma" to blur.sigma.toString()),
        )
    }
    val localCommand = normalized.toLocalMaskCommand(plan)
    val localGeometry = when (localCommand) {
        is NormalizedDrawCommand.FillRect -> GPUMaskBlurLocalGeometry.Rect(
            localCommand.rect.left,
            localCommand.rect.top,
            localCommand.rect.right,
            localCommand.rect.bottom,
        )
        is NormalizedDrawCommand.FillRRect -> {
            val rrect = localCommand.rrect
            GPUMaskBlurLocalGeometry.RRect(
                left = rrect.rect.left,
                top = rrect.rect.top,
                right = rrect.rect.right,
                bottom = rrect.rect.bottom,
                radii = listOf(
                    rrect.topLeft.x, rrect.topLeft.y,
                    rrect.topRight.x, rrect.topRight.y,
                    rrect.bottomRight.x, rrect.bottomRight.y,
                    rrect.bottomLeft.x, rrect.bottomLeft.y,
                ),
            )
        }
        is NormalizedDrawCommand.FillPath -> GPUMaskBlurLocalGeometry.Path(
                vertices = localCommand.tessellatedVertices,
                contourStarts = localCommand.contourStarts,
                fillRule = localCommand.pathDescriptor.fillRule,
                inverseFill = localCommand.pathDescriptor.inverseFill,
            )
        else -> refuseGeometry(
            "unsupported.core_primitive.mask_blur.command",
            mapOf("commandKind" to localCommand::class.simpleName.orEmpty()),
        )
    }
    val sourceFamily = when (normalized) {
        is NormalizedDrawCommand.FillRect -> "FillRect"
        is NormalizedDrawCommand.FillRRect -> "FillRRect"
        is NormalizedDrawCommand.FillPath -> "FillPath"
        else -> refuseGeometry(
            "unsupported.core_primitive.mask_blur.command",
            mapOf("commandKind" to normalized::class.simpleName.orEmpty()),
        )
    }
    val kernel = blurKernelUniform(plan)
    val scissor = clipCoverage.toPreparedScissorBounds(
        targetBounds = targetBounds,
        nonScissorClipRetainedSeparately = true,
    ) ?: refuseGeometry("unsupported.core_primitive.clip.scissor_empty", emptyMap())
    return GPUMaskBlurPayloadGatherer().gatherSemantic(
        commandIdValue = normalized.commandId.value,
        sourceFamily = sourceFamily,
        deviceBounds = plan.deviceBounds,
        localWidth = plan.localWidth,
        localHeight = plan.localHeight,
        scale = plan.scale,
        style = plan.style,
        effectiveSigma = plan.effectiveSigma,
        tapCount = kernel.tapCount,
        weights = kernel.weights,
        localGeometry = localGeometry,
        premultipliedRgba = floatArrayOf(
            colorTransform.apply(normalizedMaterial.r) * alpha,
            colorTransform.apply(normalizedMaterial.g) * alpha,
            colorTransform.apply(normalizedMaterial.b) * alpha,
            alpha,
        ),
        targetBounds = targetBounds,
        scissorBounds = scissor,
        clipCoveragePlan = clipCoverage,
        clipExecutionPlanIdentity = null,
        blendPlanIdentity = recordingBlendPlanIdentity,
    )
}

private const val MAX_MASK_BLUR_TEXTURE_DIMENSION = 4096

private fun GPUMaterialDescriptor?.toCorePrimitiveMaterial(
    colorTransform: GPUCorePrimitiveColorTransform,
    deviceGradientTransform: GPUTransformFacts? = null,
): Pair<GPUCorePrimitiveMaterialPayload, List<Float>> = when (this) {
    is GPUMaterialDescriptor.SolidColor -> {
        val alpha = a
        val premultipliedRgba = listOf(
            colorTransform.apply(r) * alpha,
            colorTransform.apply(g) * alpha,
            colorTransform.apply(b) * alpha,
            alpha,
        )
        GPUCorePrimitiveMaterialPayload.SolidColor(premultipliedRgba) to premultipliedRgba
    }
    is GPUMaterialDescriptor.LinearGradient -> {
        val facts = this@toCorePrimitiveMaterial.corePrimitiveMaterialFacts()
        if (tileMode !in setOf("clamp", "repeat")) {
            refuseCoreMaterial("unsupported.core_primitive.material.tile_mode", facts)
        }
        if (interpolation != "srgb") {
            refuseCoreMaterial("unsupported.core_primitive.material.interpolation", facts)
        }
        if (!startX.isFinite() || !startY.isFinite() || !endX.isFinite() || !endY.isFinite()) {
            refuseCoreMaterial("unsupported.core_primitive.material.non_finite", facts)
        }
        val dx = endX - startX
        val dy = endY - startY
        if (!dx.isFinite() || !dy.isFinite() || (dx == 0f && dy == 0f)) {
            refuseCoreMaterial("unsupported.core_primitive.material.linear.axis", facts)
        }
        val axisLengthSquared = dx * dx + dy * dy
        if (!axisLengthSquared.isFinite() || axisLengthSquared <= 0f) {
            refuseCoreMaterial("unsupported.core_primitive.material.linear.axis", facts)
        }
        if (!listOf(startR, startG, startB, startA, endR, endG, endB, endA).isNormalizedFinite()) {
            refuseCoreMaterial("unsupported.core_primitive.material.stops", facts)
        }
        if (localMatrix.size != 9 || localMatrix.any { !it.isFinite() }) {
            refuseCoreMaterial("unsupported.core_primitive.material.matrix", facts)
        }
        if (localMatrix != listOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f)) {
            refuseCoreMaterial("unsupported.core_primitive.material.matrix", facts)
        }
        val positions = allStopPositions?.toList() ?: listOf(0f, 1f)
        val colors = allStopColors?.toList() ?: listOf(
            startR, startG, startB, startA,
            endR, endG, endB, endA,
        )
        if (!positions.isValidCoreGradientPositions() ||
            colors.size != positions.size * 4 || !colors.isNormalizedFinite()
        ) {
            refuseCoreMaterial("unsupported.core_primitive.material.stops", facts)
        }
        val deviceStart = deviceGradientTransform?.map(startX, startY) ?: (startX to startY)
        val deviceEnd = deviceGradientTransform?.map(endX, endY) ?: (endX to endY)
        GPUCorePrimitiveMaterialPayload.LinearGradient(
            startX = deviceStart.first,
            startY = deviceStart.second,
            endX = deviceEnd.first,
            endY = deviceEnd.second,
            localMatrix = localMatrix,
            interpolation = interpolation,
            tileMode = tileMode,
            positions = positions,
            colors = colors,
        ) to listOf(0f, 0f, 0f, 0f)
    }
    is GPUMaterialDescriptor.RadialGradient -> {
        val facts = this@toCorePrimitiveMaterial.corePrimitiveMaterialFacts()
        if (tileMode != "clamp") {
            refuseCoreMaterial("unsupported.core_primitive.material.tile_mode", facts)
        }
        if (interpolation != "srgb") {
            refuseCoreMaterial("unsupported.core_primitive.material.interpolation", facts)
        }
        if (!centerX.isFinite() || !centerY.isFinite() || !radius.isFinite()) {
            refuseCoreMaterial("unsupported.core_primitive.material.non_finite", facts)
        }
        if (radius <= 0f) {
            refuseCoreMaterial("unsupported.core_primitive.material.radial.radius", facts)
        }
        if (!listOf(startR, startG, startB, startA, endR, endG, endB, endA).isNormalizedFinite()) {
            refuseCoreMaterial("unsupported.core_primitive.material.stops", facts)
        }
        if (localMatrix.size != 9 || localMatrix.any { !it.isFinite() }) {
            refuseCoreMaterial("unsupported.core_primitive.material.matrix", facts)
        }
        val positions = allStopPositions?.toList() ?: listOf(0f, 1f)
        val colors = allStopColors?.toList() ?: listOf(
            startR, startG, startB, startA,
            endR, endG, endB, endA,
        )
        if (!positions.isValidCoreGradientPositions() ||
            colors.size != positions.size * 4 ||
            !colors.isNormalizedFinite()
        ) {
            refuseCoreMaterial("unsupported.core_primitive.material.stops", facts)
        }
        val deviceCenter = deviceGradientTransform?.map(centerX, centerY) ?: (centerX to centerY)
        val deviceRadius = deviceGradientTransform?.let { transform ->
            val center = deviceCenter
            val edge = transform.map(centerX + radius, centerY)
            val dx = edge.first - center.first
            val dy = edge.second - center.second
            (dx * dx + dy * dy).takeIf { it.isFinite() && it > 0f }?.let(::sqrt)
                ?: refuseCoreMaterial("unsupported.core_primitive.material.radial.radius", facts)
        } ?: radius
        val payload = GPUCorePrimitiveMaterialPayload.RadialGradient(
            centerX = deviceCenter.first,
            centerY = deviceCenter.second,
            radius = deviceRadius,
            localMatrix = localMatrix,
            interpolation = interpolation,
            tileMode = tileMode,
            positions = positions,
            colors = colors,
        )
        // Gradients carry their own stop colors; the legacy solid color field is only a
        // validated compatibility slot and must not become a representative gradient color.
        payload to listOf(0f, 0f, 0f, 0f)
    }
    is GPUMaterialDescriptor.SweepGradient -> {
        val angleOffset = deviceGradientTransform?.rebasedSweepAngleOffsetDegrees() ?: 0f
        val facts = this@toCorePrimitiveMaterial.corePrimitiveMaterialFacts()
        if (tileMode != "clamp") {
            refuseCoreMaterial("unsupported.core_primitive.material.tile_mode", facts)
        }
        if (interpolation != "srgb") {
            refuseCoreMaterial("unsupported.core_primitive.material.interpolation", facts)
        }
        if (!centerX.isFinite() || !centerY.isFinite() ||
            !startAngle.isFinite() || !endAngle.isFinite()
        ) {
            refuseCoreMaterial("unsupported.core_primitive.material.non_finite", facts)
        }
        val sweepSpan = endAngle - startAngle
        // atan2 exposes one principal revolution; unwrapped multi-turn sweeps are not representable.
        if (endAngle <= startAngle || !sweepSpan.isFinite() || sweepSpan > 360f) {
            refuseCoreMaterial("unsupported.core_primitive.material.sweep.range", facts)
        }
        if (!listOf(startR, startG, startB, startA, endR, endG, endB, endA).isNormalizedFinite()) {
            refuseCoreMaterial("unsupported.core_primitive.material.stops", facts)
        }
        if (localMatrix.size != 9 || localMatrix.any { !it.isFinite() }) {
            refuseCoreMaterial("unsupported.core_primitive.material.matrix", facts)
        }
        val positions = allStopPositions?.toList() ?: listOf(0f, 1f)
        val colors = allStopColors?.toList() ?: listOf(
            startR, startG, startB, startA,
            endR, endG, endB, endA,
        )
        if (!positions.isValidCoreGradientPositions() ||
            colors.size != positions.size * 4 ||
            !colors.isNormalizedFinite()
        ) {
            refuseCoreMaterial("unsupported.core_primitive.material.stops", facts)
        }
        val deviceCenter = deviceGradientTransform?.map(centerX, centerY) ?: (centerX to centerY)
        val (rebasedStartAngle, rebasedEndAngle) = rebaseSweepAnglesWithFullTurnOffset(
            startAngle = startAngle,
            endAngle = endAngle,
            angleOffset = angleOffset,
            sweepSpan = sweepSpan,
        )
        val payload = GPUCorePrimitiveMaterialPayload.SweepGradient(
            centerX = deviceCenter.first,
            centerY = deviceCenter.second,
            startAngle = rebasedStartAngle,
            endAngle = rebasedEndAngle,
            localMatrix = localMatrix,
            interpolation = interpolation,
            tileMode = tileMode,
            positions = positions,
            colors = colors,
        )
        payload to listOf(0f, 0f, 0f, 0f)
    }
    else -> refuseCoreMaterial(
        "unsupported.core_primitive.material.non_solid",
        corePrimitiveMaterialFacts(),
    )
}

private fun GPUMaterialDescriptor?.corePrimitiveMaterialFacts(): Map<String, String> = mapOf(
    "materialKind" to (this?.kind?.name.orEmpty()),
    "tileMode" to when (this) {
        is GPUMaterialDescriptor.LinearGradient -> tileMode
        is GPUMaterialDescriptor.RadialGradient -> tileMode
        is GPUMaterialDescriptor.SweepGradient -> tileMode
        else -> "none"
    },
    "interpolation" to when (this) {
        is GPUMaterialDescriptor.LinearGradient -> interpolation
        is GPUMaterialDescriptor.RadialGradient -> interpolation
        is GPUMaterialDescriptor.SweepGradient -> interpolation
        else -> "none"
    },
    "materialHash" to "descriptor:${this?.let { Integer.toUnsignedString(it.hashCode(), 16) } ?: "none"}",
)

private fun refuseCoreMaterial(code: String, facts: Map<String, String>): Nothing =
    refuseGeometry(code, facts)

private fun List<Float>.isNormalizedFinite(): Boolean =
    all { value -> value.isFinite() && value in 0f..1f }

private fun List<Float>.isValidCoreGradientPositions(): Boolean =
    size in 1..16 &&
        all { value -> value.isFinite() && value in 0f..1f } &&
        zipWithNext().all { (left, right) -> left <= right }

private fun GPUFramePathVisualCommand.toCorePrimitiveInput(
    targetBounds: GPUPixelBounds,
    analysisRecord: GPUDrawAnalysisRecord,
    recordingBlendPlanIdentity: String,
    colorTransform: GPUCorePrimitiveColorTransform,
): GPUCorePrimitivePayloadInput {
    val normalizedMaterial = normalized.material
    if (geometryCoverage == GPUCoverageConsumption.StencilCoverage1x &&
        ((normalizedMaterial is GPUMaterialDescriptor.LinearGradient &&
            !isExactHardPathClipStrokeLinearGradientCandidate()) ||
            (normalizedMaterial is GPUMaterialDescriptor.RadialGradient &&
                !isExactHardPathClipStrokeRadialGradientCandidate()) ||
            (normalizedMaterial is GPUMaterialDescriptor.SweepGradient &&
                !isExactHardPathClipStrokeSweepGradientCandidate()))
    ) {
        refuseGeometry("unsupported.core_primitive.material.path_stencil", normalizedMaterial.corePrimitiveMaterialFacts())
    }
    val (material, premultipliedRgba) = normalizedMaterial.toCorePrimitiveMaterial(
        colorTransform = colorTransform,
        deviceGradientTransform = nativeHardPathClipGradientTransformOrNull(),
    )
    val sourceFamily = normalized.toCoreSourceFamily()
    val rectRouteAuthority: GPUCorePrimitiveRectRouteAuthority?
    val rectGeometryAuthority: GPUCorePrimitiveRectGeometryAuthority?
    val rrectGeometryAuthority: GPUCorePrimitiveRRectGeometryAuthority?
    val drrectOuterGeometryAuthority: GPUCorePrimitiveRRectGeometryAuthority?
    val drrectInnerGeometryAuthority: GPUCorePrimitiveRRectGeometryAuthority?
    val sealedGeometry: GPUCorePrimitiveGeometryInput?
    when (sourceFamily) {
        GPUCorePrimitiveSourceFamily.Rect -> {
            val fillRect = normalized as NormalizedDrawCommand.FillRect
            if (analysisRecord.corePrimitiveRRectGeometryAuthority != null) {
                refuseGeometry(
                    "unsupported.core_primitive.rrect.analysis_authority_forbidden",
                    mapOf(
                        "analysisRecordId" to analysisRecord.recordId,
                        "sourceFamily" to sourceFamily.name,
                    ),
                )
            }
            rectRouteAuthority = analysisRecord.corePrimitiveRectRouteAuthority
                ?: refuseGeometry(
                    "unsupported.core_primitive.rect.analysis_authority_missing",
                    mapOf("analysisRecordId" to analysisRecord.recordId),
                )
            rectGeometryAuthority = analysisRecord.corePrimitiveRectGeometryAuthority?.also { authority ->
                if (!authority.matchesCorePrimitiveRectGeometry(fillRect.rect, fillRect.transform)) {
                    refuseGeometry(
                        "unsupported.core_primitive.rect.geometry_authority_mismatch",
                        mapOf(
                            "analysisRecordId" to analysisRecord.recordId,
                            "analysisGeometryAuthority" to authority.toString(),
                        ),
                    )
                }
            } ?: refuseGeometry(
                "unsupported.core_primitive.rect.geometry_authority_mismatch",
                mapOf(
                    "analysisRecordId" to analysisRecord.recordId,
                    "analysisGeometryAuthority" to "missing",
                ),
            )
            rrectGeometryAuthority = null
            drrectOuterGeometryAuthority = null
            drrectInnerGeometryAuthority = null
            sealedGeometry = null
        }
        GPUCorePrimitiveSourceFamily.RRect -> {
            val fillRRect = normalized as NormalizedDrawCommand.FillRRect
            if (analysisRecord.corePrimitiveRectRouteAuthority != null ||
                analysisRecord.corePrimitiveRectGeometryAuthority != null
            ) {
                refuseGeometry(
                    "unsupported.core_primitive.rect.analysis_authority_forbidden",
                    mapOf(
                        "analysisRecordId" to analysisRecord.recordId,
                        "sourceFamily" to sourceFamily.name,
                    ),
                )
            }
            val authority = analysisRecord.corePrimitiveRRectGeometryAuthority
                ?: refuseGeometry(
                    "unsupported.core_primitive.rrect.analysis_authority_missing",
                    mapOf("analysisRecordId" to analysisRecord.recordId),
                )
            if (!authority.matchesCorePrimitiveRRectGeometry(fillRRect.rrect, fillRRect.transform)) {
                refuseGeometry(
                    "unsupported.core_primitive.rrect.geometry_authority_mismatch",
                    mapOf(
                        "analysisRecordId" to analysisRecord.recordId,
                        "analysisGeometryAuthority" to authority.toString(),
                    ),
                )
            }
            rectRouteAuthority = null
            rectGeometryAuthority = null
            rrectGeometryAuthority = authority
            drrectOuterGeometryAuthority = null
            drrectInnerGeometryAuthority = null
            sealedGeometry = authority.sealedDeviceGeometryInput()
        }
        GPUCorePrimitiveSourceFamily.DRRect -> {
            val fillDRRect = normalized as NormalizedDrawCommand.FillDRRect
            val outerAuthority = analysisRecord.corePrimitiveDRRectOuterGeometryAuthority
                ?: refuseGeometry("unsupported.core_primitive.drrect.outer_analysis_authority_missing", mapOf("analysisRecordId" to analysisRecord.recordId))
            val innerAuthority = analysisRecord.corePrimitiveDRRectInnerGeometryAuthority
                ?: refuseGeometry("unsupported.core_primitive.drrect.inner_analysis_authority_missing", mapOf("analysisRecordId" to analysisRecord.recordId))
            if (!outerAuthority.matchesCorePrimitiveRRectGeometry(fillDRRect.outer, fillDRRect.transform) ||
                !innerAuthority.matchesCorePrimitiveRRectGeometry(fillDRRect.inner, fillDRRect.transform)
            ) {
                refuseGeometry("unsupported.core_primitive.drrect.geometry_authority_mismatch", mapOf("analysisRecordId" to analysisRecord.recordId))
            }
            rectRouteAuthority = null
            rectGeometryAuthority = null
            rrectGeometryAuthority = null
            drrectOuterGeometryAuthority = outerAuthority
            drrectInnerGeometryAuthority = innerAuthority
            sealedGeometry = GPUCorePrimitiveGeometryInput.DRRect(
                outerAuthority.sealedDeviceGeometryInput(),
                innerAuthority.sealedDeviceGeometryInput(),
            )
        }
        else -> {
            if (analysisRecord.corePrimitiveRectRouteAuthority != null ||
                analysisRecord.corePrimitiveRectGeometryAuthority != null
            ) {
                refuseGeometry(
                    "unsupported.core_primitive.rect.analysis_authority_forbidden",
                    mapOf(
                        "analysisRecordId" to analysisRecord.recordId,
                        "sourceFamily" to sourceFamily.name,
                    ),
                )
            }
            if (analysisRecord.corePrimitiveRRectGeometryAuthority != null) {
                refuseGeometry(
                    "unsupported.core_primitive.rrect.analysis_authority_forbidden",
                    mapOf(
                        "analysisRecordId" to analysisRecord.recordId,
                        "sourceFamily" to sourceFamily.name,
                    ),
                )
            }
            rectRouteAuthority = null
            rectGeometryAuthority = null
            rrectGeometryAuthority = null
            drrectOuterGeometryAuthority = null
            drrectInnerGeometryAuthority = null
            sealedGeometry = null
        }
    }
    val geometry = sealedGeometry ?: normalized.toDeviceGeometry(targetBounds)
    val scissor = clipCoverage.toPreparedScissorBounds(
        targetBounds = targetBounds,
        nonScissorClipRetainedSeparately = true,
    )
        ?: refuseGeometry("unsupported.core_primitive.clip.scissor_empty", emptyMap())
    val directStrokeUnderHardPathClip = clipExecutionPlan is GPUClipExecutionPlan.StencilCoverage &&
        geometry is GPUCorePrimitiveGeometryInput.TriangulatedPath &&
        geometry.geometryMode == GPUCorePrimitiveGeometryMode.DirectTriangles &&
        geometry.vertices.size == 8 &&
        geometry.indices == listOf(0, 1, 2, 0, 2, 3) &&
        geometry.sourceContourStarts == listOf(0) &&
        geometry.sourceVertexCount == 2 &&
        geometry.fillRule == GPUCorePrimitiveFillRule.Winding &&
        !geometry.inverseFill &&
        geometry.strokeStyle?.let { style ->
            style.join == "miter" && when (style.cap) {
                "butt" -> style.loweringProof == GPUCorePrimitiveStrokeLoweringProof.SingleSegmentButtV1
                "square" -> style.loweringProof == GPUCorePrimitiveStrokeLoweringProof.SingleSegmentSquareV1
                else -> false
            }
        } == true
    return GPUCorePrimitivePayloadInput(
        commandIdValue = normalized.commandId.value,
        sourceFamily = sourceFamily,
        geometry = geometry,
        premultipliedRgba = premultipliedRgba,
        material = material,
        targetBounds = targetBounds,
        scissorBounds = scissor,
        clipCoveragePlan = clipCoverage,
        blendPlanIdentity = recordingBlendPlanIdentity,
        frameProvenance = provenance,
        // The canonical hairline point square is hard DirectTriangles geometry, so its
        // coverage is full-or-scissor even though the FillPath command derives stencil
        // coverage for general path fills.
        coverageMode = if (directStrokeUnderHardPathClip ||
            normalized is NormalizedDrawCommand.FillPath && normalized.isHairlinePointCommand()
        ) {
            GPUCorePrimitiveCoverageMode.FullOrScissor
        } else {
            coverageMode()
        },
        analysisRecordId = analysisRecord.recordId.takeIf {
            sourceFamily == GPUCorePrimitiveSourceFamily.Rect ||
                sourceFamily == GPUCorePrimitiveSourceFamily.RRect ||
                sourceFamily == GPUCorePrimitiveSourceFamily.DRRect
        },
        analysisCommandFamily = analysisRecord.commandFamily.takeIf {
            sourceFamily == GPUCorePrimitiveSourceFamily.Rect ||
                sourceFamily == GPUCorePrimitiveSourceFamily.RRect ||
                sourceFamily == GPUCorePrimitiveSourceFamily.DRRect
        },
        rectRouteAuthority = rectRouteAuthority,
        rectGeometryAuthority = rectGeometryAuthority,
        rrectGeometryAuthority = rrectGeometryAuthority,
        drrectOuterGeometryAuthority = drrectOuterGeometryAuthority,
        drrectInnerGeometryAuthority = drrectInnerGeometryAuthority,
    )
}

/**
 * Keeps the direct gradient device-coordinate conversion local to the bounded hard-path-clip
 * route. Other material classes, tile modes, and transforms retain their existing refusals.
 */
private fun GPUFramePathVisualCommand.nativeHardPathClipGradientTransformOrNull(): GPUTransformFacts? {
    val gradient = normalized.material
    if (gradient !is GPUMaterialDescriptor.LinearGradient &&
        gradient !is GPUMaterialDescriptor.RadialGradient &&
        gradient !is GPUMaterialDescriptor.SweepGradient
    ) return null
    val stencilClip = clipExecutionPlan as? org.graphiks.kanvas.gpu.renderer.clips.GPUClipExecutionPlan.StencilCoverage
        ?: return null
    val eligibleConsumer = when (normalized) {
        is NormalizedDrawCommand.FillRect -> gradient is GPUMaterialDescriptor.LinearGradient
        is NormalizedDrawCommand.FillPath ->
            (gradient is GPUMaterialDescriptor.LinearGradient &&
                geometryCoverage == GPUCoverageConsumption.FullOrScissor) ||
            isExactHardPathClipStrokeLinearGradientCandidate() ||
            isExactHardPathClipStrokeRadialGradientCandidate() ||
            isExactHardPathClipStrokeSweepGradientCandidate()
        else -> false
    }
    val clampGradient = when (gradient) {
        is GPUMaterialDescriptor.LinearGradient -> gradient.tileMode == "clamp"
        is GPUMaterialDescriptor.RadialGradient -> gradient.tileMode == "clamp"
        is GPUMaterialDescriptor.SweepGradient -> gradient.tileMode == "clamp"
        else -> false
    }
    if (!clampGradient || normalized.antiAlias() ||
        !eligibleConsumer ||
        stencilClip.sampleCount != 1 ||
        stencilClip.pathTransformClass !in HARD_PATH_CLIP_GRADIENT_TRANSFORM_CLASSES
    ) return null
    val transformAccepted = when (gradient) {
        is GPUMaterialDescriptor.LinearGradient,
        is GPUMaterialDescriptor.RadialGradient,
        -> normalized.transform.isNativeHardPathClipGradientTransform()
        is GPUMaterialDescriptor.SweepGradient ->
            normalized.transform.isNativeHardPathClipSweepGradientTransform()
        else -> false
    }
    return normalized.transform.takeIf { transformAccepted }
}

/**
 * Closed admission predicate for the direct triangle stroke + hard path clip gradient lane.
 * This deliberately mirrors the exact stroke facts consumed by [strokeDeviceGeometry] and does
 * not admit edge-fan, round-cap, multi-segment, dashed, filtered, or anti-aliased paths.
 */
private fun GPUFramePathVisualCommand.isExactHardPathClipStrokeLinearGradientCandidate(): Boolean {
    val path = normalized as? NormalizedDrawCommand.FillPath ?: return false
    val gradient = path.material as? GPUMaterialDescriptor.LinearGradient ?: return false
    val stencilClip = clipExecutionPlan as? GPUClipExecutionPlan.StencilCoverage ?: return false
    return path.stroke &&
        !path.antiAlias &&
        path.maskFilter == null &&
        path.contourStarts == listOf(0) &&
        path.tessellatedVertices.size == 4 &&
        path.strokeWidth.isFinite() && path.strokeWidth in 0.5f..64f &&
        path.strokeCap in setOf("butt", "square") &&
        path.strokeJoin == "miter" &&
        path.strokeMiterLimit.isFinite() && path.strokeMiterLimit >= 1f &&
        path.pathEffectKind == null &&
        (path.dashIntervals?.isEmpty() ?: true) &&
        path.pathDescriptor.fillRule in setOf("NonZero", "winding") &&
        !path.pathDescriptor.inverseFill &&
        gradient.tileMode == "clamp" &&
        (gradient.allStopPositions?.size ?: 2) == 2 &&
        (gradient.allStopColors?.size ?: 8) == 8 &&
        gradient.interpolation == "srgb" &&
        stencilClip.sampleCount == 1 &&
        stencilClip.pathTransformClass in HARD_PATH_CLIP_GRADIENT_TRANSFORM_CLASSES &&
        path.transform.isNativeHardPathClipGradientTransform()
}

/**
 * Closed admission predicate for the direct radial-gradient stroke lane.
 * The CTM is absorbed into the device-space center/radius; local-matrix composition stays
 * isolated to the identity-CTM lane proven by W136.
 */
private fun GPUFramePathVisualCommand.isExactHardPathClipStrokeRadialGradientCandidate(): Boolean {
    val path = normalized as? NormalizedDrawCommand.FillPath ?: return false
    val gradient = path.material as? GPUMaterialDescriptor.RadialGradient ?: return false
    val stencilClip = clipExecutionPlan as? GPUClipExecutionPlan.StencilCoverage ?: return false
    val localMatrixAccepted = if (path.transform.type == GPUTransformType.Identity) {
        gradient.localMatrix.isPositiveUniformScaleTranslateGradientLocalMatrix()
    } else {
        gradient.localMatrix == listOf(
            1f, 0f, 0f,
            0f, 1f, 0f,
            0f, 0f, 1f,
        )
    }
    return path.stroke &&
        !path.antiAlias &&
        path.maskFilter == null &&
        path.contourStarts == listOf(0) &&
        path.tessellatedVertices.size == 4 &&
        path.strokeWidth.isFinite() && path.strokeWidth in 0.5f..64f &&
        path.strokeCap in setOf("butt", "square") &&
        path.strokeJoin == "miter" &&
        path.strokeMiterLimit.isFinite() && path.strokeMiterLimit >= 1f &&
        path.pathEffectKind == null &&
        (path.dashIntervals?.isEmpty() ?: true) &&
        path.pathDescriptor.fillRule in setOf("NonZero", "winding") &&
        !path.pathDescriptor.inverseFill &&
        path.transform.isNativeHardPathClipGradientTransform() &&
        gradient.tileMode == "clamp" &&
        gradient.interpolation == "srgb" &&
        (gradient.allStopPositions?.size ?: 2) == 2 &&
        (gradient.allStopColors?.size ?: 8) == 8 &&
        localMatrixAccepted &&
        stencilClip.sampleCount == 1
}

/**
 * Closed admission predicate for the direct sweep-gradient stroke lane.
 * Identity, translation, and positive uniform scale/translation are admitted because the
 * materializer maps the sweep center into device space; rotations remain refused until their
 * sweep angles are transformed as well.
 */
private fun GPUFramePathVisualCommand.isExactHardPathClipStrokeSweepGradientCandidate(): Boolean {
    val path = normalized as? NormalizedDrawCommand.FillPath ?: return false
    val gradient = path.material as? GPUMaterialDescriptor.SweepGradient ?: return false
    val stencilClip = clipExecutionPlan as? GPUClipExecutionPlan.StencilCoverage ?: return false
    val sweepSpan = gradient.endAngle - gradient.startAngle
    return path.stroke &&
        !path.antiAlias &&
        path.maskFilter == null &&
        path.contourStarts == listOf(0) &&
        path.tessellatedVertices.size == 4 &&
        path.strokeWidth.isFinite() && path.strokeWidth in 0.5f..64f &&
        path.strokeCap in setOf("butt", "square") &&
        path.strokeJoin == "miter" &&
        path.strokeMiterLimit.isFinite() && path.strokeMiterLimit >= 1f &&
        path.pathEffectKind == null &&
        (path.dashIntervals?.isEmpty() ?: true) &&
        path.pathDescriptor.fillRule in setOf("NonZero", "winding") &&
        !path.pathDescriptor.inverseFill &&
        path.transform.isNativeHardPathClipSweepGradientTransform() &&
        gradient.tileMode == "clamp" &&
        gradient.interpolation == "srgb" &&
        gradient.startAngle.isFinite() && gradient.endAngle.isFinite() &&
        sweepSpan.isFinite() && sweepSpan > 0f && sweepSpan <= 360f &&
        (gradient.allStopPositions?.size ?: 2) == 2 &&
        (gradient.allStopColors?.size ?: 8) == 8 &&
        gradient.localMatrix.isPositiveUniformScaleTranslateGradientLocalMatrix() &&
        stencilClip.sampleCount == 1
}

private fun GPUTransformFacts.isNativeHardPathClipGradientTransform(): Boolean = when (type) {
    GPUTransformType.Identity,
    GPUTransformType.Translate,
    -> skewX == 0f && skewY == 0f && scaleX == 1f && scaleY == 1f
    GPUTransformType.Scale,
    GPUTransformType.Affine,
    -> (skewX == 0f && skewY == 0f && scaleX > 0f && scaleX == scaleY) ||
        isExactQuarterTurnHardPathClipGradientRotation()
    GPUTransformType.Perspective,
    GPUTransformType.Singular,
    -> false
}

/** Sweep gradients only have an authenticated center mapping in this lane. */
private fun GPUTransformFacts.isNativeHardPathClipSweepGradientTransform(): Boolean = when (type) {
    GPUTransformType.Identity,
    GPUTransformType.Translate,
    GPUTransformType.Scale,
    GPUTransformType.Affine,
    -> skewX == 0f && skewY == 0f && scaleX > 0f && scaleX == scaleY
        || isExactQuarterTurnHardPathClipGradientRotation()
    GPUTransformType.Perspective,
    GPUTransformType.Singular,
    -> false
}

private fun GPUTransformFacts.rebasedSweepAngleOffsetDegrees(): Float =
    if (isExactQuarterTurnHardPathClipGradientRotation()) 90f else 0f

private fun normalizeSweepAngleRebase(angleDegrees: Float): Float {
    val clamped = angleDegrees % 360f
    return if (clamped < 0f) clamped + 360f else clamped
}

private fun rebaseSweepAnglesWithFullTurnOffset(
    startAngle: Float,
    endAngle: Float,
    angleOffset: Float,
    sweepSpan: Float,
): Pair<Float, Float> {
    if (angleOffset == 0f) return startAngle to endAngle
    val rebasedStart = normalizeSweepAngleRebase(startAngle + angleOffset)
    val rebasedEnd = rebasedStart + sweepSpan
    return rebasedStart to rebasedEnd
}

private fun GPUTransformFacts.isExactQuarterTurnHardPathClipGradientRotation(): Boolean =
    type == GPUTransformType.Affine &&
        scaleX == 0f && scaleY == 0f && skewX == -1f && skewY == 1f

private val HARD_PATH_CLIP_GRADIENT_TRANSFORM_CLASSES = setOf(
    "identity",
    "translate",
    "uniform-positive-scale-translate",
    "right-angle-rotation",
)

private fun GPUCorePrimitiveColorTransform.apply(channel: Float): Float = when (this) {
    GPUCorePrimitiveColorTransform.Identity -> channel
    GPUCorePrimitiveColorTransform.SrgbToLinear -> if (channel <= 0.04045f) {
        channel / 12.92f
    } else {
        ((channel + 0.055f) / 1.055f).pow(2.4f)
    }
}

private fun GPUFramePathVisualCommand.coverageMode(): GPUCorePrimitiveCoverageMode = when (geometryCoverage) {
    GPUCoverageConsumption.FullOrScissor -> GPUCorePrimitiveCoverageMode.FullOrScissor
    GPUCoverageConsumption.ScalarCoverage -> GPUCorePrimitiveCoverageMode.ScalarAA
    GPUCoverageConsumption.StencilCoverage1x -> if (normalized.antiAlias()) {
        GPUCorePrimitiveCoverageMode.StencilAA
    } else {
        GPUCorePrimitiveCoverageMode.Stencil1x
    }
    GPUCoverageConsumption.MultisampleAttachmentCoverage,
    GPUCoverageConsumption.LCDCoverage,
    -> error("unsupported.core_primitive.coverage_mode:${geometryCoverage.name}")
}

private fun NormalizedDrawCommand.antiAlias(): Boolean = when (this) {
    is NormalizedDrawCommand.FillRect -> antiAlias
    is NormalizedDrawCommand.FillRRect -> antiAlias
    is NormalizedDrawCommand.FillDRRect -> antiAlias
    is NormalizedDrawCommand.FillPath -> antiAlias
    else -> false
}

private fun NormalizedDrawCommand.toDeviceGeometry(
    targetBounds: GPUPixelBounds,
): GPUCorePrimitiveGeometryInput = when (this) {
    is NormalizedDrawCommand.FillRect -> {
        val corners = listOf(
            transform.map(rect.left, rect.top),
            transform.map(rect.right, rect.top),
            transform.map(rect.right, rect.bottom),
            transform.map(rect.left, rect.bottom),
        )
        if (transform.skewX == 0f && transform.skewY == 0f) {
            GPUCorePrimitiveGeometryInput.Rect(
                corners.minOf { it.first },
                corners.minOf { it.second },
                corners.maxOf { it.first },
                corners.maxOf { it.second },
            )
        } else {
            GPUCorePrimitiveGeometryInput.TriangulatedPath(
                vertices = corners.flatMap { listOf(it.first, it.second) },
                indices = listOf(0, 1, 2, 0, 2, 3),
                sourceContourStarts = listOf(0),
                sourceVertexCount = 4,
                coverBounds = corners.toPixelCoverBounds(targetBounds),
                geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
                fillRule = GPUCorePrimitiveFillRule.Winding,
                inverseFill = false,
            )
        }
    }
    is NormalizedDrawCommand.FillRRect -> error(
        "unsupported.core_primitive.rrect.analysis_authority_missing",
    )
    is NormalizedDrawCommand.FillPath -> pathDeviceGeometry(targetBounds)
    else -> error("Non-core command reached Slice 12A semantic gathering")
}

private fun NormalizedDrawCommand.FillPath.pathDeviceGeometry(
    targetBounds: GPUPixelBounds,
): GPUCorePrimitiveGeometryInput {
    if (source.operation == "drawPoint" || source.operation == "drawPoints.points") {
        val refusalCode = when {
            dashIntervals?.isNotEmpty() == true -> "unsupported.core_primitive.point.path_effect_exact_lowering"
            !strokeWidth.isFinite() || strokeWidth < 0f -> "unsupported.core_primitive.point.invalid_width"
            strokeCap == "round" -> "unsupported.core_primitive.point.round_cap_exact_lowering"
            else -> null
        }
        if (refusalCode != null) {
            refuseGeometry(
                refusalCode,
                mapOf(
                    "width" to strokeWidth.toString(),
                    "cap" to strokeCap,
                    "dashIntervals" to dashIntervals?.joinToString(",").orEmpty(),
                ),
            )
        }
        if (strokeWidth == 0f) return hairlinePointDeviceGeometry(targetBounds)
    }
    if (stroke) return strokeDeviceGeometry(targetBounds)
    if (tessellatedVertices.isEmpty()) {
        refuseGeometry(
            code = if (pathDescriptor.inverseFill) {
                "unsupported.core_primitive.inverse_empty_path"
            } else {
                "unsupported.core_primitive.empty_path"
            },
            facts = mapOf(
                "fillRule" to pathDescriptor.fillRule,
                "inverseFill" to "true",
            ),
        )
    }
    val transformed = tessellatedVertices.chunked(2).map { pair -> transform.map(pair[0], pair[1]) }
    directTriangleDeviceGeometryOrNull(transformed, targetBounds)?.let { return it }
    val edgeFan = PathTessellator().stencilEdgeFan(
        FlattenedPath(
            points = transformed.map { (x, y) -> GPUPathPoint(x, y) },
            contourStarts = contourStarts,
        ),
    )
    return GPUCorePrimitiveGeometryInput.TriangulatedPath(
        vertices = edgeFan.vertices.toList(),
        indices = edgeFan.indices.toList(),
        sourceContourStarts = contourStarts,
        sourceVertexCount = transformed.size,
        coverBounds = if (pathDescriptor.inverseFill) targetBounds else transformed.toPixelCoverBounds(targetBounds),
        geometryMode = GPUCorePrimitiveGeometryMode.StencilEdgeFan,
        fillRule = pathDescriptor.fillRule.toCoreFillRule(),
        inverseFill = pathDescriptor.inverseFill,
        sourceAuthority = pathDescriptor.sourceAuthority,
    )
}

/**
 * The hard clip-stencil consumer shares the clip's complete stencil byte and therefore may not
 * write path coverage itself. A single non-AA winding triangle is the only FillPath shape we
 * can currently lower to direct indexed geometry without needing a stencil edge fan.
 */
private fun NormalizedDrawCommand.FillPath.directTriangleDeviceGeometryOrNull(
    transformed: List<Pair<Float, Float>>,
    targetBounds: GPUPixelBounds,
): GPUCorePrimitiveGeometryInput.TriangulatedPath? {
    if (!isBoundedDirectTriangleFill()) return null
    val triangle = when {
        transformed.size == 4 && transformed.first() == transformed.last() -> transformed.dropLast(1)
        transformed.size == 3 -> transformed
        else -> return null
    }
    if (triangle.any { (x, y) -> !x.isFinite() || !y.isFinite() }) return null
    val (first, second, third) = triangle
    val twiceArea = (second.first - first.first) * (third.second - first.second) -
        (second.second - first.second) * (third.first - first.first)
    if (!twiceArea.isFinite() || twiceArea == 0f) return null
    return GPUCorePrimitiveGeometryInput.TriangulatedPath(
        vertices = triangle.flatMap { (x, y) -> listOf(x, y) },
        indices = listOf(0, 1, 2),
        sourceContourStarts = listOf(0),
        sourceVertexCount = 3,
        coverBounds = triangle.toPixelCoverBounds(targetBounds),
        geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
        fillRule = GPUCorePrimitiveFillRule.Winding,
        inverseFill = false,
        sourceAuthority = pathDescriptor.sourceAuthority,
    )
}

private fun NormalizedDrawCommand.FillPath.isHairlinePointCommand(): Boolean =
    (source.operation == "drawPoint" || source.operation == "drawPoints.points") && strokeWidth == 0f

/**
 * Canonical hairline point lowering: every hairline `drawPoint`/`drawPoints.points` point
 * becomes the one device pixel that contains it — the pixel-aligned unit square
 * `[floor(dx), floor(dx)+1] x [floor(dy), floor(dy)+1]` for the device-space point. This is
 * exactly the non-AA Skia rasterization of a 1-px point after rounding, it keeps the CPU
 * oracle's assumption (a hairline point fully covers its pixel) exact, and the square is hard
 * DirectTriangles geometry with full-or-scissor coverage: no stencil, no stroke expansion.
 */
private fun NormalizedDrawCommand.FillPath.hairlinePointDeviceGeometry(
    targetBounds: GPUPixelBounds,
): GPUCorePrimitiveGeometryInput.TriangulatedPath {
    // A hairline point path flattens each degenerate point rect to exactly one vertex at the
    // point (every line-to coincides with the rect start), so the flattened path is one vertex
    // per point in command order.
    val deviceSquares = tessellatedVertices.chunked(2).mapNotNull { vertex ->
        val point = transform.map(vertex[0], vertex[1])
        val left = floor(point.first).toInt()
        val top = floor(point.second).toInt()
        val clampedLeft = left.coerceIn(targetBounds.left, targetBounds.right)
        val clampedTop = top.coerceIn(targetBounds.top, targetBounds.bottom)
        val clampedRight = (left + 1).coerceIn(targetBounds.left, targetBounds.right)
        val clampedBottom = (top + 1).coerceIn(targetBounds.top, targetBounds.bottom)
        if (clampedRight <= clampedLeft || clampedBottom <= clampedTop) null
        else GPUPixelBounds(clampedLeft, clampedTop, clampedRight, clampedBottom)
    }
    if (deviceSquares.isEmpty()) {
        refuseGeometry(
            code = "unsupported.core_primitive.empty_path",
            facts = mapOf("source" to source.operation),
        )
    }
    val vertices = deviceSquares.flatMap { square ->
        listOf(
            square.left.toFloat(), square.top.toFloat(),
            square.right.toFloat(), square.top.toFloat(),
            square.right.toFloat(), square.bottom.toFloat(),
            square.left.toFloat(), square.bottom.toFloat(),
        )
    }
    val indices = deviceSquares.indices.flatMap { squareIndex ->
        val base = squareIndex * 4
        listOf(base, base + 1, base + 2, base, base + 2, base + 3)
    }
    return GPUCorePrimitiveGeometryInput.TriangulatedPath(
        vertices = vertices,
        indices = indices,
        sourceContourStarts = deviceSquares.indices.map { it * 4 },
        sourceVertexCount = deviceSquares.size * 4,
        coverBounds = GPUPixelBounds(
            deviceSquares.minOf { it.left },
            deviceSquares.minOf { it.top },
            deviceSquares.maxOf { it.right },
            deviceSquares.maxOf { it.bottom },
        ),
        geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
        sourceAuthority = pathDescriptor.sourceAuthority,
    )
}

private fun NormalizedDrawCommand.FillPath.strokeDeviceGeometry(
    targetBounds: GPUPixelBounds,
): GPUCorePrimitiveGeometryInput {
    val pointCount = tessellatedVertices.size / 2
    val exactSingleSegment = contourStarts == listOf(0) && pointCount == 2
    val horizontalDashed = matchesHorizontalDashedButtMiterV1()
    val verticalDashed = matchesVerticalDashedButtMiterV1()
    val axisAlignedDashed = horizontalDashed || verticalDashed
    val boundedMultiSegment = contourStarts == listOf(0) && pointCount in 3..8 &&
        edgeCount in (pointCount - 1)..pointCount && strokeCap == "butt" && strokeJoin == "miter" &&
        tessellatedVertices.chunked(2).zipWithNext().all { (a, b) ->
            val dx = b[0] - a[0]
            val dy = b[1] - a[1]
            dx.isFinite() && dy.isFinite() && (dx != 0f || dy != 0f)
        } && tessellatedVertices.chunked(2).let { points -> points.first() != points.last() }
    if (strokeWidth == 0f && isBoundedNativePathHairline()) {
        val start = transform.map(tessellatedVertices[0], tessellatedVertices[1])
        val end = transform.map(tessellatedVertices[2], tessellatedVertices[3])
        val horizontal = start.second == end.second
        val (lo, hi) = if (horizontal) {
            minOf(start.first, end.first) to maxOf(start.first, end.first)
        } else {
            minOf(start.second, end.second) to maxOf(start.second, end.second)
        }
        val vertices = if (horizontal) {
            listOf(lo, start.second - 0.5f, hi, start.second - 0.5f,
                hi, start.second + 0.5f, lo, start.second + 0.5f)
        } else {
            listOf(start.first - 0.5f, lo, start.first + 0.5f, lo,
                start.first + 0.5f, hi, start.first - 0.5f, hi)
        }
        return GPUCorePrimitiveGeometryInput.TriangulatedPath(
            vertices = vertices,
            indices = listOf(0, 1, 2, 0, 2, 3),
            sourceContourStarts = listOf(0),
            sourceVertexCount = 2,
            coverBounds = vertices.chunked(2).map { it[0] to it[1] }.toPixelCoverBounds(targetBounds),
            geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
            fillRule = GPUCorePrimitiveFillRule.Winding,
            inverseFill = false,
            sourceAuthority = pathDescriptor.sourceAuthority,
        )
    }
    val refusalCode = when {
        !strokeWidth.isFinite() -> "unsupported.core_primitive.stroke.width_nonfinite"
        strokeWidth == 0f -> "unsupported.core_primitive.stroke.hairline_exact_lowering"
        strokeWidth < 0.5f || strokeWidth > 64f -> "unsupported.core_primitive.stroke.width_budget"
        (pathEffectKind == "Dash" || dashIntervals?.isNotEmpty() == true) && !axisAlignedDashed ->
            "unsupported.core_primitive.stroke.dash_exact_lowering"
        pathEffectKind != null && !axisAlignedDashed -> "unsupported.core_primitive.stroke.path_effect_exact_lowering"
        strokeCap !in setOf("butt", "square", "round") -> "unsupported.core_primitive.stroke.cap_exact_lowering"
        !exactSingleSegment && !boundedMultiSegment -> "unsupported.core_primitive.stroke.complex_exact_lowering"
        strokeCap == "round" && !matchesPixelExactRoundCapR2HorizontalV1() ->
            "unsupported.core_primitive.stroke.round_cap_pixel_exact_lowering"
        strokeJoin != "miter" -> "unsupported.core_primitive.stroke.join_exact_lowering"
        !strokeMiterLimit.isFinite() || strokeMiterLimit < 1f ->
            "unsupported.core_primitive.stroke.miter_exact_lowering"
        else -> null
    }
    if (refusalCode != null) {
        refuseGeometry(
            code = refusalCode,
            facts = mapOf(
                "width" to strokeWidth.toString(),
                "cap" to strokeCap,
                "join" to strokeJoin,
                "miterLimit" to strokeMiterLimit.toString(),
                "dashIntervals" to dashIntervals?.joinToString(",").orEmpty(),
                "dashPhase" to dashPhase.toString(),
                "pathEffect" to pathEffectKind.orEmpty(),
                "contourCount" to contourStarts.size.toString(),
                "pointCount" to pointCount.toString(),
            ),
        )
    }
    val cap = when (strokeCap) {
        "round" -> StrokeCap.ROUND
        "square" -> StrokeCap.SQUARE
        else -> StrokeCap.BUTT
    }
    val join = when (strokeJoin) {
        "round" -> StrokeJoin.ROUND
        "bevel" -> StrokeJoin.BEVEL
        else -> StrokeJoin.MITER
    }
    val outline = strokeToFillGeometry(
        contourVertices = tessellatedVertices,
        contourStarts = contourStarts,
        strokeWidth = strokeWidth,
        dashArray = dashIntervals,
        dashPhase = dashPhase,
        capStyle = cap,
        joinStyle = join,
        miterLimit = strokeMiterLimit,
        transform = transform,
    )
    check(outline.coordinateSpace == StrokeGeometryCoordinateSpace.DEVICE)
    val devicePoints = outline.vertices.chunked(2).map { pair ->
        pair[0] to pair[1]
    }
    val directStrokeVertices = when {
        strokeCap == "butt" && outline.vertices.size == 8 &&
            outline.contourStarts == listOf(0, 4) -> outline.vertices
        // GPUStroke emits the exact square outline as three adjacent quads. For
        // the bounded one-segment hard-clip proof, collapse those quads to their
        // four outer corners without changing their device-space geometry.
        strokeCap == "square" && outline.vertices.size == 24 &&
            outline.contourStarts == listOf(0, 4, 8, 12) ->
            outline.vertices.subList(8, 12) + outline.vertices.subList(20, 24)
        else -> null
    }
    if (
        !antiAlias && clip.executionPlan is GPUClipExecutionPlan.StencilCoverage &&
            exactSingleSegment &&
            strokeCap in setOf("butt", "square") &&
            strokeJoin == "miter" &&
            directStrokeVertices != null
    ) {
        return GPUCorePrimitiveGeometryInput.TriangulatedPath(
            vertices = directStrokeVertices,
            indices = listOf(0, 1, 2, 0, 2, 3),
            sourceContourStarts = listOf(0),
            sourceVertexCount = 2,
            coverBounds = devicePoints.toPixelCoverBounds(targetBounds),
            geometryMode = GPUCorePrimitiveGeometryMode.DirectTriangles,
            fillRule = GPUCorePrimitiveFillRule.Winding,
            inverseFill = false,
            strokeStyle = GPUCorePrimitiveStrokeStyle(
                width = strokeWidth,
                cap = strokeCap,
                join = strokeJoin,
                miterLimit = strokeMiterLimit,
                dashIntervals = dashIntervals?.toList().orEmpty(),
                dashPhase = dashPhase,
                loweringProof = when (strokeCap) {
                    "square" -> GPUCorePrimitiveStrokeLoweringProof.SingleSegmentSquareV1
                    else -> GPUCorePrimitiveStrokeLoweringProof.SingleSegmentButtV1
                },
            ),
            sourceAuthority = pathDescriptor.sourceAuthority,
        )
    }
    val transformedContourStarts = outline.contourStarts
        .filter { it < devicePoints.size }
        .distinct()
        .ifEmpty { listOf(0) }
    val edgeFan = PathTessellator().stencilEdgeFan(
        FlattenedPath(
            points = devicePoints.map { (x, y) -> GPUPathPoint(x, y) },
            contourStarts = transformedContourStarts,
        ),
    )
    return GPUCorePrimitiveGeometryInput.TriangulatedPath(
        vertices = edgeFan.vertices.toList(),
        indices = edgeFan.indices.toList(),
        sourceContourStarts = listOf(0),
        sourceVertexCount = if (boundedMultiSegment) pointCount else 2,
        coverBounds = devicePoints.toPixelCoverBounds(targetBounds),
        geometryMode = GPUCorePrimitiveGeometryMode.StrokeStencilEdgeFan,
        fillRule = GPUCorePrimitiveFillRule.Winding,
        inverseFill = false,
        strokeStyle = GPUCorePrimitiveStrokeStyle(
            width = strokeWidth,
            cap = strokeCap,
            join = strokeJoin,
            miterLimit = strokeMiterLimit,
            dashIntervals = dashIntervals?.toList().orEmpty(),
            dashPhase = dashPhase,
            loweringProof = when {
                boundedMultiSegment -> GPUCorePrimitiveStrokeLoweringProof.MultiSegmentButtMiterV1
                horizontalDashed -> GPUCorePrimitiveStrokeLoweringProof.HorizontalDashedButtMiterV1
                verticalDashed -> GPUCorePrimitiveStrokeLoweringProof.VerticalDashedButtMiterV1
                strokeCap == "square" -> GPUCorePrimitiveStrokeLoweringProof.SingleSegmentSquareV1
                strokeCap == "round" -> GPUCorePrimitiveStrokeLoweringProof.SingleSegmentRoundPixelExactR2HorizontalV1
                else -> GPUCorePrimitiveStrokeLoweringProof.SingleSegmentButtV1
            },
        ),
        sourceAuthority = pathDescriptor.sourceAuthority,
    )
}

private fun NormalizedDrawCommand.FillPath.matchesHorizontalDashedButtMiterV1(): Boolean {
    if (!stroke || antiAlias || pathEffectKind != "Dash" ||
        dashIntervals?.toList() != listOf(8f, 4f) || !isBoundedHorizontalDashedPhase(dashPhase) ||
        contourStarts != listOf(0) || tessellatedVertices.size != 4 ||
        strokeWidth != 4f || strokeCap != "butt" || strokeJoin != "miter" ||
        !strokeMiterLimit.isFinite() || strokeMiterLimit < 1f ||
        transform.type !in setOf(GPUTransformType.Identity, GPUTransformType.Translate)
    ) return false
    val start = transform.map(tessellatedVertices[0], tessellatedVertices[1])
    val end = transform.map(tessellatedVertices[2], tessellatedVertices[3])
    return start.first.isIntegralDeviceCoordinate() && start.second.isIntegralDeviceCoordinate() &&
        end.first.isIntegralDeviceCoordinate() && end.second.isIntegralDeviceCoordinate() &&
        start.second == end.second && kotlin.math.abs(end.first - start.first) >= 12f
}

private fun NormalizedDrawCommand.FillPath.matchesVerticalDashedButtMiterV1(): Boolean {
    if (!stroke || antiAlias || pathEffectKind != "Dash" ||
        dashIntervals?.toList() != listOf(8f, 4f) || !isBoundedHorizontalDashedPhase(dashPhase) ||
        contourStarts != listOf(0) || tessellatedVertices.size != 4 ||
        strokeWidth != 4f || strokeCap != "butt" || strokeJoin != "miter" ||
        !strokeMiterLimit.isFinite() || strokeMiterLimit < 1f ||
        transform.type !in setOf(GPUTransformType.Identity, GPUTransformType.Translate)
    ) return false
    val start = transform.map(tessellatedVertices[0], tessellatedVertices[1])
    val end = transform.map(tessellatedVertices[2], tessellatedVertices[3])
    return start.first.isIntegralDeviceCoordinate() && start.second.isIntegralDeviceCoordinate() &&
        end.first.isIntegralDeviceCoordinate() && end.second.isIntegralDeviceCoordinate() &&
        start.first == end.first && kotlin.math.abs(end.second - start.second) >= 12f
}

/**
 * `GPUStroke` approximates a semicircular cap with five 36-degree chords. For a non-AA
 * radius-two cap on the integral pixel grid, that polygon and the mathematical disk classify
 * the same pixel centres: the fringe centres are the half-integer offsets `(±1.5, ±0.5)`
 * (inside both) and `(±1.5, ±1.5)` (outside both). Keeping the segment horizontal, at least
 * one stroke-width long and on the integral device grid prevents rotation, fractional samples
 * and overlapping caps from invalidating that proof.
 */
private fun NormalizedDrawCommand.FillPath.matchesPixelExactRoundCapR2HorizontalV1(): Boolean {
    if (strokeWidth != 4f || transform.type !in setOf(GPUTransformType.Identity, GPUTransformType.Translate) ||
        tessellatedVertices.size != 4
    ) return false
    val start = transform.map(tessellatedVertices[0], tessellatedVertices[1])
    val end = transform.map(tessellatedVertices[2], tessellatedVertices[3])
    return start.first.isIntegralDeviceCoordinate() && start.second.isIntegralDeviceCoordinate() &&
        end.first.isIntegralDeviceCoordinate() && end.second.isIntegralDeviceCoordinate() &&
        start.second == end.second && end.first - start.first >= strokeWidth
}

private fun Float.isIntegralDeviceCoordinate(): Boolean = isFinite() && floor(this) == this

private fun org.graphiks.kanvas.gpu.renderer.commands.GPUBounds.toPixelCoverBounds(
    target: GPUPixelBounds,
): GPUPixelBounds = GPUPixelBounds(
    floor(left).toInt().coerceIn(target.left, target.right),
    floor(top).toInt().coerceIn(target.top, target.bottom),
    ceil(right).toInt().coerceIn(target.left, target.right),
    ceil(bottom).toInt().coerceIn(target.top, target.bottom),
)

private fun List<Pair<Float, Float>>.toPixelCoverBounds(target: GPUPixelBounds): GPUPixelBounds =
    GPUPixelBounds(
        floor(minOf { it.first }).toInt().coerceIn(target.left, target.right),
        floor(minOf { it.second }).toInt().coerceIn(target.top, target.bottom),
        ceil(maxOf { it.first }).toInt().coerceIn(target.left, target.right),
        ceil(maxOf { it.second }).toInt().coerceIn(target.top, target.bottom),
    )

private fun String.toCoreFillRule(): GPUCorePrimitiveFillRule = when (this) {
    "NonZero", "winding" -> GPUCorePrimitiveFillRule.Winding
    "EvenOdd", "even_odd" -> GPUCorePrimitiveFillRule.EvenOdd
    else -> refuseGeometry("unsupported.core_primitive.fill_rule", mapOf("fillRule" to this))
}

private fun GPUTransformFacts.map(x: Float, y: Float): Pair<Float, Float> {
    if (type == GPUTransformType.Perspective || type == GPUTransformType.Singular) {
        refuseGeometry(
            "unsupported.core_primitive.geometry.non_affine_transform",
            mapOf("transformType" to type.name),
        )
    }
    val mapped = Pair(
        scaleX * x + skewX * y + translateX,
        skewY * x + scaleY * y + translateY,
    )
    if (!mapped.first.isFinite() || !mapped.second.isFinite()) {
        refuseGeometry(
            "unsupported.core_primitive.geometry.non_finite_transform",
            mapOf("transformType" to type.name),
        )
    }
    return mapped
}

private fun refuseGeometry(code: String, facts: Map<String, String>): Nothing =
    throw GPUCorePrimitiveGeometryRefusalException(GPUCorePrimitiveGeometryRefusal(code, facts))

private fun NormalizedDrawCommand.analysisCommandFamily(): String = when (this) {
    is NormalizedDrawCommand.FillRect -> "FillRect"
    is NormalizedDrawCommand.FillRRect -> "FillRRect"
    is NormalizedDrawCommand.FillDRRect -> "FillDRRect"
    is NormalizedDrawCommand.FillPath -> "FillPath"
    else -> error("Non-core command reached Slice 12A semantic gathering")
}

private fun NormalizedDrawCommand.analysisRecordId(): String = when (this) {
    is NormalizedDrawCommand.FillRect -> "analysis.fill_rect.${commandId.value}"
    is NormalizedDrawCommand.FillRRect -> "analysis.fill_rrect.${commandId.value}"
    is NormalizedDrawCommand.FillDRRect -> "analysis.fill_drrect.${commandId.value}"
    is NormalizedDrawCommand.FillPath -> "analysis.fill_path.${commandId.value}"
    else -> error("Non-core command reached Slice 12A semantic gathering")
}

private fun NormalizedDrawCommand.toCoreSourceFamily(): GPUCorePrimitiveSourceFamily = when (this) {
    is NormalizedDrawCommand.FillRect -> GPUCorePrimitiveSourceFamily.Rect
    is NormalizedDrawCommand.FillRRect -> GPUCorePrimitiveSourceFamily.RRect
    is NormalizedDrawCommand.FillDRRect -> GPUCorePrimitiveSourceFamily.DRRect
    is NormalizedDrawCommand.FillPath -> when {
        source.operation.startsWith("drawPoint") || source.operation.startsWith("drawPoints") ->
            GPUCorePrimitiveSourceFamily.PointLine
        else -> GPUCorePrimitiveSourceFamily.Path
    }
    else -> error("Non-core command reached Slice 12A semantic gathering")
}
