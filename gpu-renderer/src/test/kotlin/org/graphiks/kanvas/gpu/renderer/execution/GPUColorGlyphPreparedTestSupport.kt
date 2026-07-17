package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.gpu.renderer.recording.GPUColorGlyphPreparedTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.GPUColorGlyphPreparedTaskListRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUColorGlyphPreparedTaskListResult

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.font.atlas.GlyphAtlasPlacement
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.payloads.COLOR_GLYPH_RENDER_STEP_IDENTITY
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphAtlasPlacementProofInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphLayerPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.text.GlyphAtlasTexture
import kotlin.uuid.Uuid

internal data class GPUPreparedColorGlyphTestLayer(
    val placement: GlyphAtlasPlacement,
    val deviceBounds: GPUPixelBounds,
    val premultipliedRgba: FloatArray,
)

internal fun buildPreparedColorGlyphTestTaskList(
    capabilities: GPUCapabilities,
    deviceGeneration: GPUDeviceGenerationID,
    atlas: GlyphAtlasTexture,
    layers: List<GPUPreparedColorGlyphTestLayer>,
    targetWidth: Int,
    targetHeight: Int,
    frameId: Long,
    commandId: Int,
    target: GPUFrameTargetRef,
    requestId: GPUReadbackRequestID,
): GPUTaskList {
    require(layers.isNotEmpty())
    val planKey = GPUTextArtifactKey(
        GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440072")),
        GPUTextArtifactGeneration(PREPARED_TEST_PLAN_GENERATION.toInt()),
        "prepared-test-plan-${layers.joinToString(".") { it.placement.strikeKey.glyphId.toString() }}",
    )
    val atlasKey = GPUTextArtifactKey(
        GPUTextArtifactID(Uuid.parse("550e8400-e29b-41d4-a716-446655440073")),
        GPUTextArtifactGeneration(PREPARED_TEST_ATLAS_GENERATION.toInt()),
        "prepared-test-atlas-${atlas.width}x${atlas.height}-${atlas.a8Bytes.contentHashCode()}",
    )
    val payloadLayers = layers.map { layer ->
        val placement = layer.placement
        GPUColorGlyphLayerPayloadInput(
            planArtifactKey = planKey,
            layerGlyphID = placement.strikeKey.glyphId.toUInt(),
            paletteIndex = 0,
            atlasBounds = placement.region.toPixelBounds(),
            deviceBounds = layer.deviceBounds,
            premultipliedRgba = layer.premultipliedRgba.copyOf(),
            useForeground = false,
            foregroundResolved = true,
            placementProof = GPUColorGlyphAtlasPlacementProofInput(
                atlasArtifactKey = atlasKey,
                strikeGlyphId = placement.strikeKey.glyphId,
                strikeSize = placement.strikeKey.size,
                strikeSubpixelX = placement.strikeKey.subpixelX,
                strikeSubpixelY = placement.strikeKey.subpixelY,
                atlasBounds = placement.region.toPixelBounds(),
            ),
        )
    }
    val uniformBytes = ByteBuffer.allocate(COLOR_GLYPH_UNIFORM_BYTES).order(ByteOrder.LITTLE_ENDIAN).apply {
        putFloat(targetWidth.toFloat())
        putFloat(targetHeight.toFloat())
        putInt(payloadLayers.size)
        putInt(0)
        payloadLayers.forEach { layer -> layer.premultipliedRgba.forEach(::putFloat) }
        repeat(MAX_COLOR_GLYPH_LAYERS - payloadLayers.size) { repeat(4) { putFloat(0f) } }
        payloadLayers.forEach { layer ->
            putFloat(layer.atlasBounds.left / atlas.width.toFloat())
            putFloat(layer.atlasBounds.top / atlas.height.toFloat())
            putFloat(layer.atlasBounds.width / atlas.width.toFloat())
            putFloat(layer.atlasBounds.height / atlas.height.toFloat())
        }
        repeat(MAX_COLOR_GLYPH_LAYERS - payloadLayers.size) { repeat(4) { putFloat(0f) } }
        payloadLayers.forEach { layer ->
            putFloat(layer.deviceBounds.left.toFloat())
            putFloat(layer.deviceBounds.top.toFloat())
            putFloat(layer.deviceBounds.width.toFloat())
            putFloat(layer.deviceBounds.height.toFloat())
        }
        repeat(MAX_COLOR_GLYPH_LAYERS - payloadLayers.size) { repeat(4) { putFloat(0f) } }
    }.array()
    val semantic = GPUColorGlyphPayloadGatherer().gatherSemantic(
        commandIdValue = commandId,
        renderStepIdentity = COLOR_GLYPH_RENDER_STEP_IDENTITY,
        planArtifactKey = planKey,
        atlasArtifactKey = atlasKey,
        atlasA8Bytes = atlas.a8Bytes,
        atlasWidth = atlas.width,
        atlasHeight = atlas.height,
        atlasFormat = "r8unorm",
        atlasGeneration = PREPARED_TEST_ATLAS_GENERATION,
        layers = payloadLayers,
        vertexData = floatArrayOf(
            0f, 0f, 0f, 0f,
            targetWidth.toFloat(), 0f, 1f, 0f,
            targetWidth.toFloat(), targetHeight.toFloat(), 1f, 1f,
            0f, targetHeight.toFloat(), 0f, 1f,
        ),
        indexData = intArrayOf(0, 1, 2, 0, 2, 3),
        uniformBytes = uniformBytes,
        targetBounds = GPUPixelBounds(0, 0, targetWidth, targetHeight),
        scissorBounds = GPUPixelBounds(0, 0, targetWidth, targetHeight),
    )
    val result = GPUColorGlyphPreparedTaskListBuilder().build(
        GPUColorGlyphPreparedTaskListRequest(
            frameId = GPUFrameID(frameId),
            recordingId = GPURecordingID("recording.color-glyph.prepared-test.$frameId"),
            capabilities = capabilities,
            deviceGeneration = deviceGeneration,
            target = target,
            semantic = semantic,
            readbackRequestId = requestId,
        ),
    )
    return when (result) {
        is GPUColorGlyphPreparedTaskListResult.Recorded -> result.taskList
        is GPUColorGlyphPreparedTaskListResult.Refused -> error(
            "${result.diagnostic.code.value}: ${result.diagnostic.message}",
        )
    }
}

private fun org.graphiks.kanvas.font.atlas.AtlasRegion.toPixelBounds() =
    GPUPixelBounds(x, y, x + width, y + height)

private const val MAX_COLOR_GLYPH_LAYERS = 16
private const val COLOR_GLYPH_UNIFORM_BYTES = 784
private const val PREPARED_TEST_PLAN_GENERATION = 7L
private const val PREPARED_TEST_ATLAS_GENERATION = 2L
