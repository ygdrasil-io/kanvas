package org.graphiks.kanvas.gpu.renderer.recording

import org.graphiks.kanvas.glyph.gpu.GPUTextA8Instance
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.glyph.gpu.GPUTextFloatRect
import org.graphiks.kanvas.glyph.gpu.GPUTextSourceGlyphIndex
import org.graphiks.kanvas.glyph.gpu.GPU_COLOR_GLYPH_COMPOSITE_MAX_LAYERS
import org.graphiks.kanvas.gpu.renderer.artifacts.buildPreparedColorGlyphR8UploadArtifact
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphAtlasPlacementProofInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphLayerPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedColorGlyphPayloadInput
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult
import org.graphiks.kanvas.gpu.renderer.materials.GPUSolidMaterialDictionary
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance

/**
 * One resolved COLRv0 layer ready for the packed A8 atlas.
 *
 * The caller supplies semantic facts only. Plan/atlas identity proofs, uniform packing, indexed
 * geometry, pipeline authority, and task topology are derived by [GPUColorGlyphFrameRecorder].
 */
data class GPUResolvedColorGlyphLayer(
    val layerGlyphID: UInt,
    val paletteIndex: Int,
    val atlasBounds: GPUPixelBounds,
    val deviceBounds: GPUPixelBounds,
    val premultipliedRgba: List<Float>,
    val strikeSize: Float,
    val strikeSubpixelX: Int = 0,
    val strikeSubpixelY: Int = 0,
    val useForeground: Boolean = false,
    val foregroundResolved: Boolean = true,
)

/** Public, handle-free input for recording one prepared COLRv0 frame. */
data class GPUColorGlyphFrameRecordingRequest(
    val frameId: GPUFrameID,
    val recordingId: GPURecordingID,
    val capabilities: GPUCapabilities,
    val deviceGeneration: GPUDeviceGenerationID,
    val target: GPUFrameTargetRef,
    val commandIdValue: Int,
    val sourceGlyphIndex: GPUTextSourceGlyphIndex,
    val frameProvenance: GPUFrameProvenance,
    val planArtifactKey: GPUTextArtifactKey,
    val atlasArtifactKey: GPUTextArtifactKey,
    val atlasA8Bytes: ByteArray,
    val atlasWidth: Int,
    val atlasHeight: Int,
    val layers: List<GPUResolvedColorGlyphLayer>,
    val targetBounds: GPUPixelBounds,
    val scissorBounds: GPUPixelBounds = targetBounds,
    val readbackRequestId: GPUReadbackRequestID? = null,
    val configuredAggregateBudgetBytes: Long = DEFAULT_COLOR_GLYPH_FRAME_BUDGET_BYTES,
)

/** Public recording outcome. Refusal happens before any native allocation or encoding. */
sealed interface GPUColorGlyphFrameRecordingResult {
    data class Recorded(
        val semantic: GPUDrawSemanticPayload.ColorGlyph,
        val taskList: GPUTaskList,
    ) : GPUColorGlyphFrameRecordingResult

    data class Refused(val diagnostic: GPUDiagnostic) : GPUColorGlyphFrameRecordingResult
}

/**
 * Product handoff from resolved COLRv0 data to the canonical immutable semantic and task list.
 *
 * This is deliberately above preflight and native execution. Consumers cannot choose or substitute
 * render-pipeline hashes, binding-layout hashes, vertex-source labels, load/store state, or native
 * resources through this API.
 */
class GPUColorGlyphFrameRecorder {
    private val payloadGatherer = GPUColorGlyphPayloadGatherer()

    fun record(request: GPUColorGlyphFrameRecordingRequest): GPUColorGlyphFrameRecordingResult {
        if (request.layers.size !in 1..GPU_COLOR_GLYPH_COMPOSITE_MAX_LAYERS) {
            return refused(
                code = "invalid.recording.color_glyph_input",
                message = "ColorGlyph recording requires " +
                    "1..$GPU_COLOR_GLYPH_COMPOSITE_MAX_LAYERS resolved layers.",
            )
        }
        if (request.configuredAggregateBudgetBytes <= 0L) {
            return refused(
                code = "invalid.recording.color_glyph_budget",
                message = "ColorGlyph configured aggregate budget must be positive.",
            )
        }

        val material = when (
            val result = GPUPreparedMaterialProgramCompiler.compile(
                descriptor = GPUMaterialDescriptor.SolidColor(1f, 1f, 1f, 1f),
                paintAlpha = 1f,
                context = GPUMaterialLoweringContext(
                    capabilityClass = request.capabilities.canonicalSnapshotHash(),
                    targetFormatClass = "rgba8unorm",
                    dictionaryVersion = GPUSolidMaterialDictionary.DictionaryVersion,
                ),
            )
        ) {
            is GPUPreparedMaterialProgramResult.Ready -> result.program
            is GPUPreparedMaterialProgramResult.Refused ->
                return refused(result.code, result.message)
        }
        val semantic = try {
            val atlas = buildPreparedColorGlyphR8UploadArtifact(
                artifactKey = request.atlasArtifactKey,
                width = request.atlasWidth,
                height = request.atlasHeight,
                bytes = request.atlasA8Bytes,
            )
            val layers = request.layers.mapIndexed { index, layer ->
                layer.toPayloadInput(request, colorLayerIndex = index)
            }
            payloadGatherer.gatherPreparedSemantic(
                GPUPreparedColorGlyphPayloadInput(
                commandIdValue = request.commandIdValue,
                planArtifactKey = request.planArtifactKey,
                atlasArtifactKey = request.atlasArtifactKey,
                atlas = atlas,
                instances = request.layers.mapIndexed { index, layer ->
                    GPUTextA8Instance.create(
                        glyphId = layer.layerGlyphID.toInt(),
                        sourceGlyphIndex = request.sourceGlyphIndex,
                        deviceQuad = layer.deviceBounds.deviceQuad(),
                        uvRect = layer.atlasBounds.normalizedUvRect(
                            request.atlasWidth,
                            request.atlasHeight,
                        ),
                        pageIndex = 0,
                        colorLayerIndex = index,
                    )
                },
                layers = layers,
                material = material,
                targetBounds = request.targetBounds,
                scissorBounds = request.scissorBounds,
                clipIdentity = colorGlyphScissorAuthority(request.scissorBounds),
                blendPlanIdentity = preparedColorGlyphBlendPlan().canonicalIdentity(),
                capabilitySnapshotHash = request.capabilities.canonicalSnapshotHash(),
                frameProvenance = request.frameProvenance,
            ))
        } catch (failure: IllegalArgumentException) {
            return refused(
                code = "invalid.recording.color_glyph_input",
                message = failure.message ?: "ColorGlyph resolved input is invalid.",
            )
        }

        return when (
            val result = GPUColorGlyphPreparedTaskListBuilder().build(
                GPUColorGlyphPreparedTaskListRequest(
                    frameId = request.frameId,
                    recordingId = request.recordingId,
                    capabilities = request.capabilities,
                    deviceGeneration = request.deviceGeneration,
                    target = request.target,
                    semantic = semantic,
                    readbackRequestId = request.readbackRequestId,
                    configuredAggregateBudgetBytes = request.configuredAggregateBudgetBytes,
                ),
            )
        ) {
            is GPUColorGlyphPreparedTaskListResult.Recorded ->
                GPUColorGlyphFrameRecordingResult.Recorded(semantic, result.taskList)

            is GPUColorGlyphPreparedTaskListResult.Refused ->
                GPUColorGlyphFrameRecordingResult.Refused(result.diagnostic)
        }
    }

    private fun GPUResolvedColorGlyphLayer.toPayloadInput(
        request: GPUColorGlyphFrameRecordingRequest,
        colorLayerIndex: Int,
    ) = GPUColorGlyphLayerPayloadInput(
        planArtifactKey = request.planArtifactKey,
        layerGlyphID = layerGlyphID,
        paletteIndex = paletteIndex,
        atlasBounds = atlasBounds,
        deviceBounds = deviceBounds,
        premultipliedRgba = premultipliedRgba.toFloatArray(),
        useForeground = useForeground,
        foregroundResolved = foregroundResolved,
        placementProof = GPUColorGlyphAtlasPlacementProofInput(
            atlasArtifactKey = request.atlasArtifactKey,
            strikeGlyphId = layerGlyphID.toInt(),
            strikeSize = strikeSize,
            strikeSubpixelX = strikeSubpixelX,
            strikeSubpixelY = strikeSubpixelY,
            atlasBounds = atlasBounds,
        ),
        colorLayerIndex = colorLayerIndex,
    )

    private fun refused(code: String, message: String) = GPUColorGlyphFrameRecordingResult.Refused(
        GPUDiagnostic(
            code = GPUDiagnosticCode(code),
            domain = GPUDiagnosticDomain.Recording,
            severity = GPUDiagnosticSeverity.Error,
            message = message,
        ),
    )
}

private const val DEFAULT_COLOR_GLYPH_FRAME_BUDGET_BYTES = 1L shl 30

private fun GPUPixelBounds.deviceQuad(): List<Float> = listOf(
    left.toFloat(),
    top.toFloat(),
    right.toFloat(),
    top.toFloat(),
    right.toFloat(),
    bottom.toFloat(),
    left.toFloat(),
    bottom.toFloat(),
)

private fun GPUPixelBounds.normalizedUvRect(width: Int, height: Int): GPUTextFloatRect =
    GPUTextFloatRect(
        left / width.toFloat(),
        top / height.toFloat(),
        right / width.toFloat(),
        bottom / height.toFloat(),
    )
