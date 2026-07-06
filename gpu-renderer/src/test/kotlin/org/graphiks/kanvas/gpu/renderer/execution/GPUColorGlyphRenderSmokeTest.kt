package org.graphiks.kanvas.gpu.renderer.execution

import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.graphiks.kanvas.font.atlas.AtlasRegion
import org.graphiks.kanvas.font.atlas.GlyphAtlasPlacement
import org.graphiks.kanvas.gpu.renderer.text.GlyphAtlasTextureBuilder
import org.graphiks.kanvas.gpu.renderer.text.GlyphAtlasTextureResult
import org.junit.jupiter.api.Assumptions.assumeTrue
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GPUColorGlyphRenderSmokeTest {
    @Test
    fun `drawColorGlyphPass renders two-layer COLRv0 composite with red and blue layers`() {
        val atlasResult = GlyphAtlasTextureBuilder().build("AB", fontSize = 48f)
        assumeTrue(
            atlasResult is GlyphAtlasTextureResult.Built,
            "glyph atlas unavailable in current environment: ${(atlasResult as? GlyphAtlasTextureResult.Refused)?.reason}",
        )
        val built = assertIs<GlyphAtlasTextureResult.Built>(atlasResult)
        val atlas = built.atlas
        val placements = atlas.placements
        assertTrue(placements.size >= 2, "need at least 2 glyph placements for 'A' and 'B'")

        val runtime = GPUBackendRuntimeFactory.createOrNull()
        assumeTrue(runtime != null, "GPU backend unavailable in current environment")

        val targetW = 64
        val targetH = 64
        val uniform = ByteBuffer.allocate(528).order(ByteOrder.LITTLE_ENDIAN)

        uniform.putFloat(targetW.toFloat())
        uniform.putFloat(targetH.toFloat())
        uniform.putInt(2)
        uniform.putInt(0)

        putPremulColor(uniform, 1f, 0f, 0f, 1f)
        putPremulColor(uniform, 0f, 0f, 1f, 1f)
        repeat(14) { putPremulColor(uniform, 0f, 0f, 0f, 0f) }

        val aw = atlas.width.toFloat()
        val ah = atlas.height.toFloat()
        putAtlasRect(uniform, placements[0], aw, ah)
        putAtlasRect(uniform, placements[1], aw, ah)
        repeat(14) { putAtlasRect(uniform, AtlasRegion(0, 0, 0, 0), aw, ah) }

        val uniformBytes = uniform.array()

        val vertexData = floatArrayOf(
            0f, 0f, 0f, 0f,
            64f, 0f, 1f, 0f,
            64f, 64f, 1f, 1f,
            0f, 64f, 0f, 1f,
        )
        val indexData = intArrayOf(0, 1, 2, 0, 2, 3)

        runtime!!.use { session ->
            session.createOffscreenTarget(
                GPUOffscreenTargetRequest(width = targetW, height = targetH, colorFormat = "rgba8unorm"),
            ).use { target ->
                target.encode(
                    clearColor = GPUClearColor(red = 0.0, green = 0.0, blue = 0.0, alpha = 1.0),
                ) {
                    drawColorGlyphPass(
                        atlasRgba = atlas.a8Bytes,
                        atlasWidth = atlas.width,
                        atlasHeight = atlas.height,
                        atlasFormat = "r8unorm",
                        vertexData = vertexData,
                        indexData = indexData,
                        draws = listOf(
                            GPUBackendRawUniformDraw(
                                uniformBytes = uniformBytes,
                                scissorX = 0,
                                scissorY = 0,
                                scissorWidth = targetW,
                                scissorHeight = targetH,
                            ),
                        ),
                    )
                }

                val rgba = target.readRgba()
                assertEquals(targetW * targetH * 4, rgba.size, "RGBA buffer size mismatch")

                var redCount = 0
                var blueCount = 0
                for (i in 0 until rgba.size step 4) {
                    val r = rgba[i].toInt() and 0xFF
                    val b = rgba[i + 2].toInt() and 0xFF
                    if (r > 0x80 && b < 0x40) redCount++
                    if (b > 0x80 && r < 0x40) blueCount++
                }

                assertTrue(redCount > 0, "expected some red-dominant pixels from layer 0 (A), found 0")
                assertTrue(blueCount > 0, "expected some blue-dominant pixels from layer 1 (B), found 0")
            }
        }
    }

    private fun putPremulColor(buf: ByteBuffer, r: Float, g: Float, b: Float, a: Float) {
        buf.putFloat(r)
        buf.putFloat(g)
        buf.putFloat(b)
        buf.putFloat(a)
    }

    private fun putAtlasRect(buf: ByteBuffer, placement: GlyphAtlasPlacement, aw: Float, ah: Float) {
        buf.putFloat(placement.region.x / aw)
        buf.putFloat(placement.region.y / ah)
        buf.putFloat(placement.region.width / aw)
        buf.putFloat(placement.region.height / ah)
    }

    private fun putAtlasRect(buf: ByteBuffer, region: AtlasRegion, aw: Float, ah: Float) {
        buf.putFloat(region.x / aw)
        buf.putFloat(region.y / ah)
        buf.putFloat(region.width / aw)
        buf.putFloat(region.height / ah)
    }
}
