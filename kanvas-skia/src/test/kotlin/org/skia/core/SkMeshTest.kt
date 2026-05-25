package org.skia.core

import org.graphiks.math.SkRect
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlender
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkData
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
    fun `specification accepts only position plus optional ubyte color cpu subset`() {
        val colorSpec = positionColorSpec()
        assertNotNull(colorSpec.specification)
        assertEquals("", colorSpec.error)
        assertEquals(12, colorSpec.specification!!.stride())

        val unsupportedAttribute = SkMeshSpecification.Make(
            attributes = listOf(
                SkMeshSpecification.Attribute(
                    SkMeshSpecification.Attribute.Type.kFloat2,
                    offset = 0,
                    name = "position",
                ),
                SkMeshSpecification.Attribute(
                    SkMeshSpecification.Attribute.Type.kFloat2,
                    offset = 8,
                    name = "uv",
                ),
            ),
            vertexStride = 16,
            vs = "Varyings main(const Attributes a) { Varyings v; v.position = a.position; return v; }",
            fs = "float4 main(const Varyings v) { return float4(1); }",
        )
        assertEquals(null, unsupportedAttribute.specification)
        assertTrue(unsupportedAttribute.error.contains("position plus optional"))

        val varyingSpec = SkMeshSpecification.Make(
            attributes = listOf(
                SkMeshSpecification.Attribute(
                    SkMeshSpecification.Attribute.Type.kFloat2,
                    offset = 0,
                    name = "position",
                ),
            ),
            vertexStride = 8,
            varyings = listOf(
                SkMeshSpecification.Varying(
                    SkMeshSpecification.Varying.Type.kFloat4,
                    name = "vcolor",
                ),
            ),
            vs = "Varyings main(const Attributes a) { return Varyings(); }",
            fs = "float4 main(const Varyings v) { return float4(1); }",
        )
        assertEquals(null, varyingSpec.specification)
        assertTrue(varyingSpec.error.contains("varyings"))
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
    fun `mesh validates uniform block size against specification`() {
        val spec = positionSpec().specification!!
        val meshWithoutUniforms = SkMesh.Make(
            specification = spec,
            mode = SkMesh.Mode.kTriangles,
            vertexBuffer = SkMeshes.MakeVertexBuffer(floatBytes(0f, 0f, 10f, 0f, 0f, 10f), 24),
            vertexCount = 3,
            vertexOffset = 0,
            uniforms = SkData.MakeWithCopy(byteArrayOf(1, 2, 3, 4)),
            bounds = SkRect.MakeLTRB(0f, 0f, 10f, 10f),
        )
        assertFalse(meshWithoutUniforms.mesh.isValid())
        assertTrue(meshWithoutUniforms.error.contains("declares none"))

        val uniformSpec = SkMeshSpecification.Make(
            attributes = listOf(
                SkMeshSpecification.Attribute(
                    SkMeshSpecification.Attribute.Type.kFloat2,
                    offset = 0,
                    name = "position",
                ),
            ),
            vertexStride = 8,
            vs = "uniform float4 uColor; Varyings main(const Attributes a) { Varyings v; v.position = a.position; return v; }",
            fs = "float4 main(const Varyings v) { return uColor; }",
        ).specification!!
        val mesh = SkMesh.Make(
            specification = uniformSpec,
            mode = SkMesh.Mode.kTriangles,
            vertexBuffer = SkMeshes.MakeVertexBuffer(floatBytes(0f, 0f, 10f, 0f, 0f, 10f), 24),
            vertexCount = 3,
            vertexOffset = 0,
            uniforms = SkData.MakeWithCopy(byteArrayOf(1, 2, 3, 4)),
            bounds = SkRect.MakeLTRB(0f, 0f, 10f, 10f),
        )

        assertFalse(mesh.mesh.isValid())
        assertTrue(mesh.error.contains("uniform block size"))
    }

    @Test
    fun `specification computes deterministic uniform layout`() {
        val spec = SkMeshSpecification.Make(
            attributes = listOf(
                SkMeshSpecification.Attribute(
                    SkMeshSpecification.Attribute.Type.kFloat2,
                    offset = 0,
                    name = "position",
                ),
            ),
            vertexStride = 8,
            vs = "uniform float2 t; uniform half4 color;",
            fs = "float4 main(const Varyings v) { return color; }",
        ).specification!!

        assertEquals(2, spec.uniforms().size)
        assertEquals("t", spec.uniforms()[0].name)
        assertEquals(0, spec.uniforms()[0].offset)
        assertEquals("color", spec.uniforms()[1].name)
        assertEquals(16, spec.uniforms()[1].offset)
        assertEquals(32, spec.uniformSize())
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

    @Test
    fun `drawMesh lowers ubyte color attribute through cpu vertices`() {
        val bm = whiteBitmap()
        val canvas = SkCanvas(bm)
        val mesh = SkMesh.Make(
            specification = positionColorSpec().specification!!,
            mode = SkMesh.Mode.kTriangles,
            vertexBuffer = SkMeshes.MakeVertexBuffer(
                positionColorBytes(
                    5f, 5f, 255, 0, 0, 255,
                    25f, 5f, 255, 0, 0, 255,
                    5f, 25f, 255, 0, 0, 255,
                ),
                36,
            ),
            vertexCount = 3,
            vertexOffset = 0,
            bounds = SkRect.MakeLTRB(5f, 5f, 25f, 25f),
        ).mesh

        canvas.drawMesh(mesh, SkPaint(0xFFFFFFFF.toInt()))

        assertEquals(0xFFFF0000.toInt(), bm.getPixel(10, 10))
        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(29, 29))
    }

    @Test
    fun `drawMesh applies supplied blender to copied paint`() {
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

        val paint = SkPaint(0xFFFF0000.toInt())
        canvas.drawMesh(mesh, SkBlender.Mode(SkBlendMode.kDst), paint)

        assertEquals(0xFFFFFFFF.toInt(), bm.getPixel(10, 10))
        assertEquals(null, paint.blender, "drawMesh must not mutate the caller paint")
    }

    @Test
    fun `drawMesh executes uniforms fragment subset for solid color`() {
        val bm = whiteBitmap()
        val canvas = SkCanvas(bm)
        val spec = SkMeshSpecification.Make(
            attributes = listOf(
                SkMeshSpecification.Attribute(
                    SkMeshSpecification.Attribute.Type.kFloat2,
                    offset = 0,
                    name = "position",
                ),
            ),
            vertexStride = 8,
            vs = "uniform float4 uColor; Varyings main(const Attributes a) { Varyings v; v.position = a.position; return v; }",
            fs = "float4 main(const Varyings v) { return uColor; }",
        ).specification!!
        val mesh = SkMesh.Make(
            specification = spec,
            mode = SkMesh.Mode.kTriangles,
            vertexBuffer = SkMeshes.MakeVertexBuffer(
                floatBytes(5f, 5f, 25f, 5f, 5f, 25f),
                24,
            ),
            vertexCount = 3,
            vertexOffset = 0,
            uniforms = SkData.MakeWithCopy(
                ByteBuffer.allocate(16)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putFloat(0f)
                    .putFloat(1f)
                    .putFloat(0f)
                    .putFloat(1f)
                    .array(),
            ),
            bounds = SkRect.MakeLTRB(5f, 5f, 25f, 25f),
        ).mesh

        canvas.drawMesh(mesh, SkPaint(0xFFFF0000.toInt()))
        assertEquals(0xFF00FF00.toInt(), bm.getPixel(10, 10))
    }

    @Test
    fun `drawMesh falls back when fragment return is not supported subset`() {
        val bm = whiteBitmap()
        val canvas = SkCanvas(bm)
        val spec = SkMeshSpecification.Make(
            attributes = listOf(
                SkMeshSpecification.Attribute(
                    SkMeshSpecification.Attribute.Type.kFloat2,
                    offset = 0,
                    name = "position",
                ),
            ),
            vertexStride = 8,
            vs = "uniform float4 uColor; Varyings main(const Attributes a) { Varyings v; v.position = a.position; return v; }",
            fs = "float4 main(const Varyings v) { return float4(uColor.rgb, 1.0); }",
        ).specification!!
        val mesh = SkMesh.Make(
            specification = spec,
            mode = SkMesh.Mode.kTriangles,
            vertexBuffer = SkMeshes.MakeVertexBuffer(floatBytes(5f, 5f, 25f, 5f, 5f, 25f), 24),
            vertexCount = 3,
            vertexOffset = 0,
            uniforms = SkData.MakeWithCopy(
                ByteBuffer.allocate(16).order(ByteOrder.LITTLE_ENDIAN)
                    .putFloat(0f).putFloat(1f).putFloat(0f).putFloat(1f)
                    .array(),
            ),
            bounds = SkRect.MakeLTRB(5f, 5f, 25f, 25f),
        ).mesh

        canvas.drawMesh(mesh, SkPaint(0xFFFF0000.toInt()))
        assertEquals(0xFFFF0000.toInt(), bm.getPixel(10, 10))
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

    private fun positionColorSpec(): SkMeshSpecification.Result =
        SkMeshSpecification.Make(
            attributes = listOf(
                SkMeshSpecification.Attribute(
                    SkMeshSpecification.Attribute.Type.kFloat2,
                    offset = 0,
                    name = "position",
                ),
                SkMeshSpecification.Attribute(
                    SkMeshSpecification.Attribute.Type.kUByte4_unorm,
                    offset = 8,
                    name = "color",
                ),
            ),
            vertexStride = 12,
            vs = "Varyings main(const Attributes a) { Varyings v; v.position = a.position; return v; }",
            fs = "float4 main(const Varyings v) { return float4(1); }",
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

    private fun positionColorBytes(vararg values: Number): ByteArray =
        ByteBuffer.allocate(values.size / 6 * 12)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                var i = 0
                while (i < values.size) {
                    putFloat(values[i].toFloat())
                    putFloat(values[i + 1].toFloat())
                    put(values[i + 2].toByte())
                    put(values[i + 3].toByte())
                    put(values[i + 4].toByte())
                    put(values[i + 5].toByte())
                    i += 6
                }
            }
            .array()
}
