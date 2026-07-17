package org.graphiks.kanvas.gpu.renderer.recording

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnostic
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticCode
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticDomain
import org.graphiks.kanvas.gpu.renderer.diagnostics.GPUDiagnosticSeverity
import org.graphiks.kanvas.gpu.renderer.payloads.COLOR_GLYPH_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphAtlasPlacementProofInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphLayerPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef

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
        if (request.layers.size !in 1..MAX_COLOR_GLYPH_FRAME_LAYERS) {
            return refused(
                code = "invalid.recording.color_glyph_input",
                message = "ColorGlyph recording requires 1..$MAX_COLOR_GLYPH_FRAME_LAYERS resolved layers.",
            )
        }
        if (request.configuredAggregateBudgetBytes <= 0L) {
            return refused(
                code = "invalid.recording.color_glyph_budget",
                message = "ColorGlyph configured aggregate budget must be positive.",
            )
        }

        val semantic = try {
            payloadGatherer.gatherSemantic(
                commandIdValue = request.commandIdValue,
                renderStepIdentity = COLOR_GLYPH_RENDER_STEP_IDENTITY,
                planArtifactKey = request.planArtifactKey,
                atlasArtifactKey = request.atlasArtifactKey,
                atlasA8Bytes = request.atlasA8Bytes,
                atlasWidth = request.atlasWidth,
                atlasHeight = request.atlasHeight,
                atlasFormat = COLOR_GLYPH_ATLAS_FORMAT,
                atlasGeneration = request.atlasArtifactKey.generation.value.toLong(),
                layers = request.layers.map { layer -> layer.toPayloadInput(request) },
                vertexData = canonicalTargetQuad(request.targetBounds),
                indexData = COLOR_GLYPH_CANONICAL_INDICES.copyOf(),
                uniformBytes = packUniforms(request),
                targetBounds = request.targetBounds,
                scissorBounds = request.scissorBounds,
            )
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
    )

    private fun packUniforms(request: GPUColorGlyphFrameRecordingRequest): ByteArray =
        ByteBuffer.allocate(COLOR_GLYPH_FRAME_UNIFORM_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
            putFloat(request.targetBounds.width.toFloat())
            putFloat(request.targetBounds.height.toFloat())
            putInt(request.layers.size)
            putInt(0)
            repeat(MAX_COLOR_GLYPH_FRAME_LAYERS) { index ->
                val color = request.layers.getOrNull(index)?.premultipliedRgba ?: ZERO_COLOR_GLYPH_FIELD
                repeat(4) { component -> putFloat(color.getOrElse(component) { Float.NaN }) }
            }
            repeat(MAX_COLOR_GLYPH_FRAME_LAYERS) { index ->
                val bounds = request.layers.getOrNull(index)?.atlasBounds
                if (bounds == null) {
                    ZERO_COLOR_GLYPH_FIELD.forEach(::putFloat)
                } else {
                    putFloat(bounds.left / request.atlasWidth.toFloat())
                    putFloat(bounds.top / request.atlasHeight.toFloat())
                    putFloat(bounds.width / request.atlasWidth.toFloat())
                    putFloat(bounds.height / request.atlasHeight.toFloat())
                }
            }
            repeat(MAX_COLOR_GLYPH_FRAME_LAYERS) { index ->
                val bounds = request.layers.getOrNull(index)?.deviceBounds
                if (bounds == null) {
                    ZERO_COLOR_GLYPH_FIELD.forEach(::putFloat)
                } else {
                    putFloat(bounds.left.toFloat())
                    putFloat(bounds.top.toFloat())
                    putFloat(bounds.width.toFloat())
                    putFloat(bounds.height.toFloat())
                }
            }
        }.array()

    private fun canonicalTargetQuad(bounds: GPUPixelBounds): FloatArray = floatArrayOf(
        bounds.left.toFloat(), bounds.top.toFloat(), 0f, 0f,
        bounds.right.toFloat(), bounds.top.toFloat(), 1f, 0f,
        bounds.right.toFloat(), bounds.bottom.toFloat(), 1f, 1f,
        bounds.left.toFloat(), bounds.bottom.toFloat(), 0f, 1f,
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

private const val COLOR_GLYPH_ATLAS_FORMAT = "r8unorm"
private const val MAX_COLOR_GLYPH_FRAME_LAYERS = 16
private const val COLOR_GLYPH_FRAME_UNIFORM_BYTES = 784
private const val DEFAULT_COLOR_GLYPH_FRAME_BUDGET_BYTES = 1L shl 30
private val COLOR_GLYPH_CANONICAL_INDICES = intArrayOf(0, 1, 2, 0, 2, 3)
private val ZERO_COLOR_GLYPH_FIELD = listOf(0f, 0f, 0f, 0f)
