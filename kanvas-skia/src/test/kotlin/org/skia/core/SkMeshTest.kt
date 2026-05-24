package org.skia.core

import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkPaint
import java.nio.ByteBuffer
import java.nio.ByteOrder

class SkMeshTest {
    @Test
    fun `specification requires cpu position attribute`() {
        val missingPosition = SkMeshSpecification.Make(
            attributes = listOf(
                SkMeshSpecification.Attribute(
                    SkMeshSpecification.Attribute.Type.kFloat2,
                    offset = 0,
                    name = "uv",
                ),
            ),
            vertexStride = 8,
            vs = "Varyings main(const Attributes a) { return Varyings(); }",
            fs = "float2 main(const Varyings v) { return v.position; }",
        )

        assertEquals(null, missingPosition.specification)
        assertTrue(missingPosition.error.contains("position"))

        val spec = positionSpec()
        assertNotNull(spec.specification)
        assertEquals("", spec.error)
        assertEquals(8, spec.specification!!.stride())
    }

    @Test
    fun `mesh validates vertex and index buffer ranges`() {
        val spec = positionSpec().specification!!
        val vb = SkMeshes.MakeVertexBuffer(floatBytes(0f, 0f, 10f, 0f), 16)

        val tooManyVertices = SkMesh.Make(
            specification = spec,
            mode = SkMesh.Mode.kTriangles,
            vertexBuffer = vb,
            vertexCount = 3,
            vertexOffset = 0,
            bounds = SkRect.MakeLTRB(0f, 0f, 10f, 10f),
        )
        assertFalse(tooManyVertices.mesh.isValid())
        assertTrue(tooManyVertices.error.contains("vertex buffer"))

        val vb3 = SkMeshes.MakeVertexBuffer(floatBytes(0f, 0f, 10f, 0f, 0f, 10f), 24)
        val ib = SkMeshes.MakeIndexBuffer(shortBytes(0, 1), 4)
        val tooFewIndices = SkMesh.MakeIndexed(
            specification = spec,
            mode = SkMesh.Mode.kTriangles,
            vertexBuffer = vb3,
            vertexCount = 3,
            vertexOffset = 0,
            indexBuffer = ib,
            indexCount = 2,
            indexOffset = 0,
            bounds = SkRect.MakeLTRB(0f, 0f, 10f, 10f),
        )
        assertFalse(tooFewIndices.mesh.isValid())
        assertTrue(tooFewIndices.error.contains("indexCount"))
    }

    @Test
    fun `mesh buffers are cpu backed and update with alignment checks`() {
        val vb = SkMeshes.MakeVertexBuffer(null, 16)
        assertEquals(16, vb.size())
        assertTrue(vb.update(floatBytes(1f, 2f), offset = 0, size = 8))
        assertFalse(vb.update(byteArrayOf(1, 2, 3, 4), offset = 2, size = 4))
    }

    @Test
    fun `drawMesh fills a non indexed triangle through cpu raster path`() {
        val bm = whiteBitmap()
        val canvas = SkCanvas(bm)
        val mesh = SkMesh.Make(
            specification = positionSpec().specification!!,
            mode = SkMesh.Mode.kTriangles,
            vertexBuffer = SkMeshes.MakeVertexBuffer(
                floatBytes(
                    5f, 5f,
                    25f, 5f,
                    5f, 25f,
                ),
                24,
            ),
            vertexCount = 3,
            vertexOffset = 0,
            bounds = SkRect.MakeLTRB(5f, 5f, 25f, 25f),
        ).mesh

        canvas.drawMesh(mesh, SkPaint(0xFF00AAFF.toInt()))

        assertEquals(0xFF00AAFF.toInt(), bm.getPixel(10, 10))
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(29, 29))
    }

    @Test
    fun `drawMesh honors uint16 indices`() {
        val bm = whiteBitmap()
        val canvas = SkCanvas(bm)
        val mesh = SkMesh.MakeIndexed(
            specification = positionSpec().specification!!,
            mode = SkMesh.Mode.kTriangles,
            vertexBuffer = SkMeshes.MakeVertexBuffer(
                floatBytes(
                    5f, 5f,
                    25f, 5f,
                    5f, 25f,
                    25f, 25f,
                ),
                32,
            ),
            vertexCount = 4,
            vertexOffset = 0,
            indexBuffer = SkMeshes.MakeIndexBuffer(shortBytes(0, 1, 2, 1, 3, 2), 12),
            indexCount = 6,
            indexOffset = 0,
            bounds = SkRect.MakeLTRB(5f, 5f, 25f, 25f),
        ).mesh

        canvas.drawMesh(mesh, null, SkPaint(0xFFFF5500.toInt()))

        assertEquals(0xFFFF5500.toInt(), bm.getPixel(15, 15))
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(2, 2))
    }

    private fun positionSpec(): SkMeshSpecification.Result =
        SkMeshSpecification.Make(
            attributes = listOf(
                SkMeshSpecification.Attribute(
                    SkMeshSpecification.Attribute.Type.kFloat2,
                    offset = 0,
                    name = "position",
                ),
            ),
            vertexStride = 8,
            vs = "Varyings main(const Attributes a) { Varyings v; v.position = a.position; return v; }",
            fs = "float2 main(const Varyings v) { return v.position; }",
        )

    private fun whiteBitmap(): SkBitmap =
        SkBitmap(30, 30).also { it.eraseColor(0xFFFFFFFF.toInt()) }

    private fun floatBytes(vararg values: Float): ByteArray =
        ByteBuffer.allocate(values.size * 4)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { values.forEach { putFloat(it) } }
            .array()

    private fun shortBytes(vararg values: Int): ByteArray =
        ByteBuffer.allocate(values.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { values.forEach { putShort(it.toShort()) } }
            .array()
}
