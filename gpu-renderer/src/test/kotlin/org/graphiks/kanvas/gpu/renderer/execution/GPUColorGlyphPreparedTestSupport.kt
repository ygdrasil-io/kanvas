package org.graphiks.kanvas.gpu.renderer.execution

import org.graphiks.kanvas.font.atlas.GlyphAtlasPlacement
import org.graphiks.kanvas.glyph.gpu.GPUTextA8Instance
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.glyph.gpu.GPUTextFloatRect
import org.graphiks.kanvas.glyph.gpu.GPUTextSourceGlyphIndex
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.artifacts.buildPreparedColorGlyphR8UploadArtifact
import org.graphiks.kanvas.gpu.renderer.commands.GPUMaterialDescriptor
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.materials.GPUMaterialLoweringContext
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramCompiler
import org.graphiks.kanvas.gpu.renderer.materials.GPUPreparedMaterialProgramResult
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphAtlasPlacementProofInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphLayerPayloadInput
import org.graphiks.kanvas.gpu.renderer.payloads.GPUColorGlyphPayloadGatherer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPreparedColorGlyphPayloadInput
import org.graphiks.kanvas.gpu.renderer.recording.GPUColorGlyphPreparedTaskListBuilder
import org.graphiks.kanvas.gpu.renderer.recording.GPUColorGlyphPreparedTaskListRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUColorGlyphPreparedTaskListResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.recording.canonicalSnapshotHash
import org.graphiks.kanvas.gpu.renderer.recording.preparedColorGlyphBlendPlan
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.runtimeeffects.KanvasPreparedRuntimeEffectResolver
import org.graphiks.kanvas.gpu.renderer.state.GPUFrameProvenance
import org.graphiks.kanvas.gpu.renderer.text.GlyphAtlasTexture
import org.graphiks.kanvas.gpu.renderer.materials.GPUSolidMaterialDictionary
import org.graphiks.kanvas.gpu.renderer.passes.canonicalIdentity
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
    requestId: GPUReadbackRequestID?,
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
    val payloadLayers = layers.mapIndexed { index, layer ->
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
            colorLayerIndex = index,
        )
    }
    val material = when (
        val result = GPUPreparedMaterialProgramCompiler.compile(
            descriptor = GPUMaterialDescriptor.SolidColor(1f, 1f, 1f, 1f),
            paintAlpha = 1f,
            context = GPUMaterialLoweringContext(
                capabilityClass = capabilities.canonicalSnapshotHash(),
                targetFormatClass = "rgba8unorm",
                dictionaryVersion = GPUSolidMaterialDictionary.DictionaryVersion,
                runtimeEffectResolver = KanvasPreparedRuntimeEffectResolver(),
            ),
        )
    ) {
        is GPUPreparedMaterialProgramResult.Ready -> result.program
        is GPUPreparedMaterialProgramResult.Refused ->
            error("${result.code}: ${result.message}")
    }
    val preparedAtlas = buildPreparedColorGlyphR8UploadArtifact(
        artifactKey = atlasKey,
        width = atlas.width,
        height = atlas.height,
        bytes = atlas.a8Bytes,
    )
    val targetBounds = GPUPixelBounds(0, 0, targetWidth, targetHeight)
    val semantic = GPUColorGlyphPayloadGatherer().gatherPreparedSemantic(
        GPUPreparedColorGlyphPayloadInput(
            commandIdValue = commandId,
            planArtifactKey = planKey,
            atlasArtifactKey = atlasKey,
            atlas = preparedAtlas,
            instances = payloadLayers.mapIndexed { index, layer ->
                GPUTextA8Instance.create(
                    glyphId = layer.layerGlyphID.toInt(),
                    sourceGlyphIndex = GPUTextSourceGlyphIndex(index),
                    deviceQuad = layer.deviceBounds.deviceQuad(),
                    uvRect = GPUTextFloatRect(
                        layer.atlasBounds.left / atlas.width.toFloat(),
                        layer.atlasBounds.top / atlas.height.toFloat(),
                        layer.atlasBounds.right / atlas.width.toFloat(),
                        layer.atlasBounds.bottom / atlas.height.toFloat(),
                    ),
                    pageIndex = 0,
                    colorLayerIndex = index,
                )
            },
            layers = payloadLayers,
            material = material,
            targetBounds = targetBounds,
            scissorBounds = targetBounds,
            clipIdentity = "clip:none",
            blendPlanIdentity = preparedColorGlyphBlendPlan().canonicalIdentity(),
            capabilitySnapshotHash = capabilities.canonicalSnapshotHash(),
            frameProvenance = GPUFrameProvenance.GmContent,
        ),
    )
    return when (
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
    ) {
        is GPUColorGlyphPreparedTaskListResult.Recorded -> result.taskList
        is GPUColorGlyphPreparedTaskListResult.Refused ->
            error("${result.diagnostic.code.value}: ${result.diagnostic.message}")
    }
}

private fun org.graphiks.kanvas.font.atlas.AtlasRegion.toPixelBounds() =
    GPUPixelBounds(x, y, x + width, y + height)

private fun GPUPixelBounds.deviceQuad(): List<Float> =
    listOf(
        left.toFloat(), top.toFloat(),
        right.toFloat(), top.toFloat(),
        right.toFloat(), bottom.toFloat(),
        left.toFloat(), bottom.toFloat(),
    )

private const val PREPARED_TEST_PLAN_GENERATION = 7L
private const val PREPARED_TEST_ATLAS_GENERATION = 2L
