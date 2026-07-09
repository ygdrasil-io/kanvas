package org.graphiks.kanvas.surface.gpu

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import org.graphiks.kanvas.text.GpuTextBlob
import org.graphiks.kanvas.text.KanvasGlyphRun
import org.graphiks.kanvas.text.TextBlob
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

class GPUTextAtlasGeometryTest {
    @Test
    fun `raster scale normalization preserves baseline-relative offsets`() {
        val gpuBlob = gpuBlob(
            positions = listOf(Point(0f, 0f), Point(10f, 0f)),
            glyphRects = listOf(
                Rect.fromLTRB(-4f, -8f, 6f, 2f),
                Rect.fromLTRB(0f, 0f, 0f, 0f),
            ),
        )

        val normalized = gpuBlob.normalizeGlyphRects(2f)

        assertEquals(Rect.fromLTRB(-2f, -4f, 3f, 1f), normalized.glyphRects[0])
        assertEquals(Rect.fromLTRB(0f, 0f, 0f, 0f), normalized.glyphRects[1])
    }

    @Test
    fun `atlas mesh applies draw origin glyph position rect offsets and CTM once`() {
        val gpuBlob = gpuBlob(
            positions = listOf(Point(10f, 20f)),
            glyphRects = listOf(Rect.fromLTRB(-2f, -8f, 5f, 2f)),
            glyphUvs = listOf(Rect.fromLTRB(0.1f, 0.2f, 0.6f, 0.9f)),
        )

        val mesh = buildTextAtlasMesh(
            gpuBlob = gpuBlob,
            drawOriginX = 3f,
            drawOriginY = 4f,
            transform = Matrix33.scale(2f, 3f),
        )

        assertEquals(
            listOf(
                22f, 48f, 0.1f, 0.2f,
                36f, 48f, 0.6f, 0.2f,
                36f, 78f, 0.6f, 0.9f,
                22f, 78f, 0.1f, 0.9f,
            ),
            mesh.vertexData.toList(),
        )
        assertEquals(listOf(0, 1, 2, 0, 2, 3), mesh.indexData.toList())
    }

    @Test
    fun `atlas mesh skips empty glyph without shifting drawable UV indices`() {
        val gpuBlob = gpuBlob(
            positions = listOf(Point(11f, 12f), Point(13f, 14f), Point(15f, 16f)),
            glyphRects = listOf(
                Rect.fromLTRB(-1f, -2f, 2f, 3f),
                Rect.fromLTRB(0f, 0f, 0f, 0f),
                Rect.fromLTRB(4f, -5f, 8f, 1f),
            ),
            glyphUvs = listOf(
                Rect.fromLTRB(0.1f, 0.2f, 0.3f, 0.4f),
                Rect.fromLTRB(0f, 0f, 0f, 0f),
                Rect.fromLTRB(0.7f, 0.8f, 0.9f, 1f),
            ),
        )

        val mesh = buildTextAtlasMesh(gpuBlob)

        assertEquals(32, mesh.vertexData.size)
        assertEquals(listOf(0, 1, 2, 0, 2, 3, 4, 5, 6, 4, 6, 7), mesh.indexData.toList())
        assertEquals(listOf(0.1f, 0.2f), mesh.vertexData.slice(2..3))
        assertEquals(listOf(0.7f, 0.8f), mesh.vertexData.slice(18..19))
        val emittedUvs = mesh.vertexData.toList().chunked(4).map { vertex -> vertex[2] to vertex[3] }
        assertFalse(emittedUvs.any { it == (0f to 0f) }, "empty glyph UVs must not reach the atlas mesh")
    }

    private fun gpuBlob(
        positions: List<Point>,
        glyphRects: List<Rect>,
        glyphUvs: List<Rect> = List(glyphRects.size) { Rect.fromLTRB(0.1f, 0.2f, 0.9f, 1f) },
    ): GpuTextBlob {
        val run = KanvasGlyphRun(
            glyphs = positions.indices.map { (it + 1).toUShort() },
            positions = positions,
            fontSize = 12f,
        )
        return GpuTextBlob(
            textBlob = TextBlob(glyphRuns = listOf(run), fontSize = 12f),
            atlasRgba = byteArrayOf(0x7f),
            atlasWidth = 1,
            atlasHeight = 1,
            glyphUvData = glyphUvs,
            glyphRects = glyphRects,
        )
    }
}
