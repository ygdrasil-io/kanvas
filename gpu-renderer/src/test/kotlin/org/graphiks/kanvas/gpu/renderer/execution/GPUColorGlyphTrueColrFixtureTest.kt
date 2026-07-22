package org.graphiks.kanvas.gpu.renderer.execution

import java.util.concurrent.TimeUnit
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue
import org.graphiks.kanvas.font.atlas.GlyphAtlasPlacement
import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlan
import org.graphiks.kanvas.font.atlas.GlyphAtlasUploadPlanner
import org.graphiks.kanvas.font.glyph.A8Bitmap
import org.graphiks.kanvas.font.glyph.A8Rasterizer
import org.graphiks.kanvas.font.glyph.GlyphStrikeKey
import org.graphiks.kanvas.font.scaler.ColorLayerEntry
import org.graphiks.kanvas.font.scaler.GlyphRepresentation
import org.graphiks.kanvas.font.scaler.GlyphScaler
import org.graphiks.kanvas.font.scaler.ScaledGlyph
import org.graphiks.kanvas.gpu.renderer.coordinates.GPUPixelBounds
import org.graphiks.kanvas.gpu.renderer.recording.GPUReadbackRequestID
import org.graphiks.kanvas.gpu.renderer.resources.GPUFrameTargetRef
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameStructuralOutcome
import org.graphiks.kanvas.gpu.renderer.text.GlyphAtlasTexture
import org.junit.jupiter.api.Assumptions.assumeTrue

class GPUColorGlyphTrueColrFixtureTest {
    @Test
    fun `real Skia COLRv0 glyph matches an independent CPU source-over oracle`() {
        val fontBytes = assertNotNull(
            javaClass.getResourceAsStream(COLR_FONT_RESOURCE),
            "The Skia colr.ttf fixture must be a gpu-renderer test resource",
        ).readBytes()
        val scaler = GlyphScaler.fromBytes(fontBytes)
        val representation = assertIs<GlyphRepresentation.ColorLayers>(
            scaler.scaleGlyph(BASE_GLYPH_ID, FONT_SIZE).representation,
        )
        assertEquals(
            listOf(
                ColorLayerEntry(glyphId = 7, paletteColorArgb = 0xFFFF2A2A.toInt()),
                ColorLayerEntry(glyphId = 8, paletteColorArgb = 0xFF000000.toInt()),
            ),
            representation.layers,
        )

        val rasterizer = A8Rasterizer()
        val sourceLayers = representation.layers.map { colorLayer ->
            val scaled = scaler.scaleGlyph(colorLayer.glyphId, FONT_SIZE)
            val bitmap = assertNotNull(
                rasterizer.rasterize(scaled),
                "COLRv0 layer glyph ${colorLayer.glyphId} must have rasterizable outline coverage",
            )
            RasterizedColorLayer(scaled, bitmap, colorLayer.paletteColorArgb)
        }
        val unionLeft = sourceLayers.minOf { floor(it.scaled.bounds.left).toInt() }
        val unionTop = sourceLayers.minOf { floor(it.scaled.bounds.top).toInt() }
        val translatedLayers = sourceLayers.map { layer ->
            val left = GLYPH_OFFSET_X + floor(layer.scaled.bounds.left).toInt() - unionLeft
            val top = GLYPH_OFFSET_Y + floor(layer.scaled.bounds.top).toInt() - unionTop
            val right = GLYPH_OFFSET_X + ceil(layer.scaled.bounds.right).toInt() - unionLeft
            val bottom = GLYPH_OFFSET_Y + ceil(layer.scaled.bounds.bottom).toInt() - unionTop
            val deviceBounds = GPUPixelBounds(left, top, right, bottom)
            assertEquals(layer.bitmap.width, deviceBounds.width, "layer ${layer.scaled.glyphId} width")
            assertEquals(layer.bitmap.height, deviceBounds.height, "layer ${layer.scaled.glyphId} height")
            layer.copy(deviceBounds = deviceBounds)
        }
        val targetWidth = translatedLayers.maxOf { it.deviceBounds.right } + GLYPH_OFFSET_X
        val targetHeight = translatedLayers.maxOf { it.deviceBounds.bottom } + GLYPH_OFFSET_Y

        val entries = translatedLayers.map { layer ->
            GlyphStrikeKey(layer.scaled.glyphId, FONT_SIZE, subpixelX = 0, subpixelY = 0) to layer.bitmap
        }
        val upload = assertIs<GlyphAtlasUploadPlan.Accepted>(GlyphAtlasUploadPlanner().plan(entries))
        assertEquals(listOf(7, 8), upload.placements.map { it.strikeKey.glyphId })
        val atlas = GlyphAtlasTexture(
            a8Bytes = upload.atlasBytes,
            width = upload.atlasWidth,
            height = upload.atlasHeight,
            glyphCount = upload.placements.size,
            fontFamily = "Skia colr.ttf",
            evidenceDumpLines = listOf("fixture=$COLR_FONT_RESOURCE", "baseGlyph=$BASE_GLYPH_ID"),
            placements = upload.placements,
        )

        val expected = composeCpuSourceOver(
            width = targetWidth,
            height = targetHeight,
            layers = translatedLayers,
        )
        val bottomLayerOnly = composeCpuSourceOver(
            width = targetWidth,
            height = targetHeight,
            layers = translatedLayers.take(1),
        )
        assertFalse(
            expected.contentEquals(bottomLayerOnly),
            "The top black COLRv0 layer must affect the reference image",
        )
        assertTrue(expected.hasOpaqueRgb(255, 42, 42), "The CPAL red layer must be visible")

        val backend = GPUBackendRuntimeNativeFactory.createOrNull()
        assumeTrue(backend != null, "GPU backend unavailable in current environment")
        backend!!
        val capabilities = requireNotNull(backend.capabilities)
        val deviceGeneration = backend.deviceGeneration
        val requestId = GPUReadbackRequestID("readback.color-glyph.true-colr-fixture")
        val preparedLayers = translatedLayers.zip(upload.placements) { layer, placement ->
            GPUPreparedColorGlyphTestLayer(
                placement = placement,
                deviceBounds = layer.deviceBounds,
                premultipliedRgba = layer.colorArgb.toPremultipliedRgba(),
            )
        }
        val taskList = buildPreparedColorGlyphTestTaskList(
            capabilities = capabilities,
            deviceGeneration = deviceGeneration,
            atlas = atlas,
            layers = preparedLayers,
            targetWidth = targetWidth,
            targetHeight = targetHeight,
            frameId = 10_534L,
            commandId = 54,
            target = GPUFrameTargetRef("target.color-glyph.true-colr-fixture"),
            requestId = requestId,
        )
        val session = backend.prepareSceneFrameSession(
            GPUOffscreenTargetRequest(targetWidth, targetHeight, "rgba8unorm"),
        )
        try {
            val terminal = session.renderFrame(
                taskList,
                GPUSceneFrameOutputRequest.ReadbackRgba(requestId),
            ).completion.toCompletableFuture().get(10, TimeUnit.SECONDS)

            assertEquals(
                GPUFrameStructuralOutcome.Succeeded,
                terminal.outcome,
                "${terminal.diagnostic?.code?.value}: ${terminal.diagnostic?.message}",
            )
            val actual = assertIs<GPUSceneFrameOutput.ReadbackRgba>(terminal.output).bytes
            assertContentEquals(expected, actual)
            assertEquals(1L, session.nativeCounters().encoders)
            assertEquals(1L, session.nativeCounters().submits)
        } finally {
            try {
                session.close()
            } finally {
                GPUBackendRuntimeNativeFactory.dispose()
            }
        }
    }

    private data class RasterizedColorLayer(
        val scaled: ScaledGlyph,
        val bitmap: A8Bitmap,
        val colorArgb: Int,
        val deviceBounds: GPUPixelBounds = GPUPixelBounds(0, 0, 0, 0),
    )

    private fun composeCpuSourceOver(
        width: Int,
        height: Int,
        layers: List<RasterizedColorLayer>,
    ): ByteArray {
        val rgba = FloatArray(width * height * 4)
        repeat(width * height) { pixel -> rgba[pixel * 4 + 3] = 1f }

        layers.forEach { layer ->
            val color = layer.colorArgb.toPremultipliedRgba()
            repeat(layer.bitmap.height) { localY ->
                repeat(layer.bitmap.width) { localX ->
                    val coverage = (layer.bitmap.pixels[localY * layer.bitmap.width + localX].toInt() and 0xff) / 255f
                    val pixel = ((layer.deviceBounds.top + localY) * width + layer.deviceBounds.left + localX) * 4
                    val srcAlpha = color[3] * coverage
                    val inverseSourceAlpha = 1f - srcAlpha
                    rgba[pixel] = color[0] * coverage + rgba[pixel] * inverseSourceAlpha
                    rgba[pixel + 1] = color[1] * coverage + rgba[pixel + 1] * inverseSourceAlpha
                    rgba[pixel + 2] = color[2] * coverage + rgba[pixel + 2] * inverseSourceAlpha
                    rgba[pixel + 3] = srcAlpha + rgba[pixel + 3] * inverseSourceAlpha
                }
            }
        }

        return ByteArray(rgba.size) { index ->
            (rgba[index].coerceIn(0f, 1f) * 255f).roundToInt().toByte()
        }
    }

    private fun Int.toPremultipliedRgba(): FloatArray {
        val alpha = (ushr(24) and 0xff) / 255f
        return floatArrayOf(
            ((ushr(16) and 0xff) / 255f) * alpha,
            ((ushr(8) and 0xff) / 255f) * alpha,
            ((this and 0xff) / 255f) * alpha,
            alpha,
        )
    }

    private fun ByteArray.hasOpaqueRgb(red: Int, green: Int, blue: Int): Boolean =
        indices.step(4).any { index ->
            (this[index].toInt() and 0xff) == red &&
                (this[index + 1].toInt() and 0xff) == green &&
                (this[index + 2].toInt() and 0xff) == blue &&
                (this[index + 3].toInt() and 0xff) == 255
        }

    private companion object {
        const val COLR_FONT_RESOURCE = "/fonts/skia/colr.ttf"
        const val BASE_GLYPH_ID = 2
        const val FONT_SIZE = 96f
        const val GLYPH_OFFSET_X = 12
        const val GLYPH_OFFSET_Y = 9
    }
}
