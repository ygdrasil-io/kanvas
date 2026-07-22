package org.graphiks.kanvas.gpu.renderer.scenes.offscreen

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.uuid.Uuid
import org.graphiks.kanvas.font.atlas.AtlasRegion
import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlan
import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlanner
import org.graphiks.kanvas.font.glyph.A8Bitmap
import org.graphiks.kanvas.font.glyph.A8Rasterizer
import org.graphiks.kanvas.font.glyph.GlyphStrikeKey
import org.graphiks.kanvas.font.scaler.GlyphRepresentation
import org.graphiks.kanvas.font.scaler.GlyphScaler
import org.graphiks.kanvas.font.scaler.ScaledGlyph
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactID
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactKey
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUCapabilities
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.recording.GPUColorGlyphFrameRecorder
import org.graphiks.kanvas.gpu.renderer.recording.GPUColorGlyphFrameRecordingRequest
import org.graphiks.kanvas.gpu.renderer.recording.GPUColorGlyphFrameRecordingResult
import org.graphiks.kanvas.gpu.renderer.recording.GPUResolvedColorGlyphLayer
import org.graphiks.kanvas.gpu.renderer.payloads.GPUDrawSemanticPayload
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.recording.GPURecordingID
import org.graphiks.kanvas.gpu.renderer.recording.GPUTaskList
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.scenes.catalog.GPURendererScene
import org.graphiks.kanvas.gpu.renderer.scenes.commands.SceneCommand

internal sealed interface PreparedColorGlyphSceneFrameResult {
    data class Recorded(
        val semantic: GPUDrawSemanticPayload.ColorGlyph,
        val taskList: GPUTaskList,
        val readbackRequestId: GPUReadbackRequestID?,
        val cpuReferenceRgba: ByteArray,
        val diagnostics: List<String>,
    ) : PreparedColorGlyphSceneFrameResult

    data class Refused(val reason: String) : PreparedColorGlyphSceneFrameResult
}

/** Resolves a real COLRv0 font fixture into the same public recorder used by product callers. */
internal class PreparedColorGlyphSceneFrameRecorder(
    private val frameRecorder: GPUColorGlyphFrameRecorder = GPUColorGlyphFrameRecorder(),
) {
    fun record(
        scene: GPURendererScene<SceneCommand>,
        capabilities: GPUCapabilities,
        deviceGeneration: GPUDeviceGenerationID,
        frameOrdinal: Long,
        withReadback: Boolean,
    ): PreparedColorGlyphSceneFrameResult = runCatching {
        require(frameOrdinal > 0L) { "prepared ColorGlyph frame ordinal must be positive" }
        val command = scene.commands.filterIsInstance<SceneCommand.ColorTextRun>().single()
        require(command.hasColrFontFixture) {
            "prepared ColorGlyph scene requires one real COLRv0 font fixture"
        }
        require(scene.commands.filterNot { it is SceneCommand.Clear || it is SceneCommand.ColorTextRun }.isEmpty()) {
            "prepared ColorGlyph scene currently requires a homogeneous command stream"
        }
        val resource = command.colrFontResource
        val baseGlyphId = command.colrBaseGlyphId
        val fontSize = command.colrFontSize
        val fontBytes = requireNotNull(javaClass.getResourceAsStream(resource)) {
            "COLRv0 font resource is unavailable: $resource"
        }.use { it.readBytes() }
        val scaler = GlyphScaler.fromBytes(fontBytes)
        val representation = scaler.scaleGlyph(baseGlyphId, fontSize).representation as? GlyphRepresentation.ColorLayers
            ?: error("glyph $baseGlyphId from $resource is not a COLRv0 layered glyph")
        val rasterizer = A8Rasterizer()
        val sourceLayers = representation.layers.map { colorLayer ->
            val scaled = scaler.scaleGlyph(colorLayer.glyphId, fontSize)
            val bitmap = requireNotNull(rasterizer.rasterize(scaled)) {
                "COLRv0 layer glyph ${colorLayer.glyphId} has no A8 outline coverage"
            }
            RasterizedLayer(scaled, bitmap, colorLayer.paletteColorArgb)
        }
        val unionLeft = sourceLayers.minOf { floor(it.scaled.bounds.left).toInt() }
        val unionTop = sourceLayers.minOf { floor(it.scaled.bounds.top).toInt() }
        val layers = sourceLayers.map { layer ->
            val left = command.glyphOriginX + floor(layer.scaled.bounds.left).toInt() - unionLeft
            val top = command.glyphOriginY + floor(layer.scaled.bounds.top).toInt() - unionTop
            val right = command.glyphOriginX + ceil(layer.scaled.bounds.right).toInt() - unionLeft
            val bottom = command.glyphOriginY + ceil(layer.scaled.bounds.bottom).toInt() - unionTop
            layer.copy(deviceBounds = GPUPixelBounds(left, top, right, bottom))
        }
        val targetBounds = GPUPixelBounds(0, 0, scene.dimensions.width, scene.dimensions.height)
        require(layers.all { layer ->
            layer.deviceBounds.left >= targetBounds.left &&
                layer.deviceBounds.top >= targetBounds.top &&
                layer.deviceBounds.right <= targetBounds.right &&
                layer.deviceBounds.bottom <= targetBounds.bottom
        }) { "COLRv0 glyph layers must fit inside the scene target" }

        val atlasEntries = layers.map { layer ->
            GlyphStrikeKey(layer.scaled.glyphId, fontSize, subpixelX = 0, subpixelY = 0) to layer.bitmap
        }
        val upload = GlyphAtlasUploadPlanner().plan(atlasEntries) as? GlyphAtlasUploadPlan.Accepted
            ?: error("COLRv0 glyph atlas upload was refused")
        val layerGlyphs = layers.map { it.scaled.glyphId }
        require(upload.placements.map { it.strikeKey.glyphId } == layerGlyphs) {
            "COLRv0 atlas placements do not preserve layer paint order"
        }

        val readbackRequestId = if (withReadback) {
            GPUReadbackRequestID("readback.scene.${scene.sceneId.value}.$frameOrdinal")
        } else {
            null
        }
        val planKey = artifactKey(
            id = PLAN_ARTIFACT_ID,
            generation = 1,
            fingerprint = "colr-plan-${scene.sceneId.value}-$baseGlyphId-${layerGlyphs.joinToString("-")}",
        )
        val atlasKey = artifactKey(
            id = ATLAS_ARTIFACT_ID,
            generation = 1,
            fingerprint = "colr-atlas-${scene.sceneId.value}-${upload.atlasWidth}x${upload.atlasHeight}",
        )
        val recorded = frameRecorder.record(
            GPUColorGlyphFrameRecordingRequest(
                frameId = GPUFrameID(PREPARED_SCENE_FRAME_ID_BASE + frameOrdinal),
                recordingId = GPURecordingID("recording.scene.${scene.sceneId.value}.$frameOrdinal"),
                capabilities = capabilities,
                deviceGeneration = deviceGeneration,
                target = GPUFrameTargetRef("target.scene.${scene.sceneId.value}"),
                commandIdValue = 1,
                planArtifactKey = planKey,
                atlasArtifactKey = atlasKey,
                atlasA8Bytes = upload.atlasBytes,
                atlasWidth = upload.atlasWidth,
                atlasHeight = upload.atlasHeight,
                layers = layers.zip(upload.placements).mapIndexed { layerIndex, (layer, placement) ->
                    GPUResolvedColorGlyphLayer(
                        layerGlyphID = layer.scaled.glyphId.toUInt(),
                        paletteIndex = layerIndex,
                        atlasBounds = placement.region.toPixelBounds(),
                        deviceBounds = layer.deviceBounds,
                        premultipliedRgba = layer.colorArgb.toPremultipliedRgba(),
                        strikeSize = placement.strikeKey.size,
                        strikeSubpixelX = placement.strikeKey.subpixelX,
                        strikeSubpixelY = placement.strikeKey.subpixelY,
                    )
                },
                targetBounds = targetBounds,
                readbackRequestId = readbackRequestId,
            ),
        )
        when (recorded) {
            is GPUColorGlyphFrameRecordingResult.Refused -> error(
                "${recorded.diagnostic.code.value}: ${recorded.diagnostic.message}",
            )
            is GPUColorGlyphFrameRecordingResult.Recorded -> PreparedColorGlyphSceneFrameResult.Recorded(
                semantic = recorded.semantic,
                taskList = recorded.taskList,
                readbackRequestId = readbackRequestId,
                cpuReferenceRgba = composeCpuReference(
                    scene.dimensions.width,
                    scene.dimensions.height,
                    layers,
                ),
                diagnostics = listOf(
                    "colorTextRun:route=prepared-colr-v0",
                    "colorTextRun:fontResource=$resource",
                    "colorTextRun:baseGlyph=$baseGlyphId layerGlyphs=${layerGlyphs.joinToString(",")}",
                    "colorTextRun:atlasFormat=r8unorm atlasSize=${upload.atlasWidth}x${upload.atlasHeight}",
                    "colorTextRun:uniformPack=784-byte-le",
                    "colorTextRun:frameRoute=one-encoder-one-command-buffer-one-submit",
                    "colorTextRun:reference=independent-cpu-source-over",
                    "colorTextRun:nonClaim=no-colrv1-no-shaping-no-emoji",
                ),
            )
        }
    }.getOrElse { failure ->
        PreparedColorGlyphSceneFrameResult.Refused(
            failure.message?.takeIf(String::isNotBlank) ?: failure::class.simpleName.orEmpty(),
        )
    }

    private fun composeCpuReference(
        width: Int,
        height: Int,
        layers: List<RasterizedLayer>,
    ): ByteArray {
        val rgba = FloatArray(width * height * 4)
        repeat(width * height) { pixel -> rgba[pixel * 4 + 3] = 1f }
        layers.forEach { layer ->
            val color = layer.colorArgb.toPremultipliedRgba()
            repeat(layer.bitmap.height) { localY ->
                repeat(layer.bitmap.width) { localX ->
                    val coverage =
                        (layer.bitmap.pixels[localY * layer.bitmap.width + localX].toInt() and 0xff) / 255f
                    val pixel = (
                        (layer.deviceBounds.top + localY) * width + layer.deviceBounds.left + localX
                    ) * 4
                    val sourceAlpha = color[3] * coverage
                    val inverseSourceAlpha = 1f - sourceAlpha
                    rgba[pixel] = color[0] * coverage + rgba[pixel] * inverseSourceAlpha
                    rgba[pixel + 1] = color[1] * coverage + rgba[pixel + 1] * inverseSourceAlpha
                    rgba[pixel + 2] = color[2] * coverage + rgba[pixel + 2] * inverseSourceAlpha
                    rgba[pixel + 3] = sourceAlpha + rgba[pixel + 3] * inverseSourceAlpha
                }
            }
        }
        return ByteArray(rgba.size) { index ->
            (rgba[index].coerceIn(0f, 1f) * 255f).roundToInt().toByte()
        }
    }

    private fun artifactKey(id: String, generation: Int, fingerprint: String) = GPUTextArtifactKey(
        GPUTextArtifactID(Uuid.parse(id)),
        GPUTextArtifactGeneration(generation),
        fingerprint,
    )

    private data class RasterizedLayer(
        val scaled: ScaledGlyph,
        val bitmap: A8Bitmap,
        val colorArgb: Int,
        val deviceBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 0, 0),
    )
}

private fun AtlasRegion.toPixelBounds() = GPUPixelBounds(x, y, x + width, y + height)

private fun Int.toPremultipliedRgba(): List<Float> {
    val alpha = (ushr(24) and 0xff) / 255f
    return listOf(
        ((ushr(16) and 0xff) / 255f) * alpha,
        ((ushr(8) and 0xff) / 255f) * alpha,
        ((this and 0xff) / 255f) * alpha,
        alpha,
    )
}

private const val PREPARED_SCENE_FRAME_ID_BASE = 34_000L
private const val PLAN_ARTIFACT_ID = "550e8400-e29b-41d4-a716-446655440091"
private const val ATLAS_ARTIFACT_ID = "550e8400-e29b-41d4-a716-446655440092"
