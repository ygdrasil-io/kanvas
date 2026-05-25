package org.skia.tests

import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.core.SkMesh
import org.skia.core.SkMeshSpecification
import org.skia.core.SkMeshes
import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBlender
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorSpace
import org.skia.foundation.SkData
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSurfaces
import org.skia.foundation.SkTileMode
import org.skia.foundation.skcms.SkNamedGamut
import org.skia.foundation.skcms.SkNamedTransferFn
import java.nio.ByteBuffer
import java.nio.ByteOrder

// ---------------------------------------------------------------------------
// Stub ports of Skia's gm/mesh.cpp  (11 GMs)
//
// The narrow CPU subset for "custommesh" is active. Remaining GMs still
// depend on unsupported mesh SkSL, uniforms, children, colour management, or
// picture playback and keep explicit STUB.MESH blockers.
//
// Upstream: /Users/chaos/workspace/kanvas-forge/skia-main/gm/mesh.cpp
// ---------------------------------------------------------------------------

/**
 * Stub for Skia's `MeshGM` ("custommesh", 435×1180).
 *
 * Upstream exercises SkMesh::Make / SkMesh::MakeIndexed with two
 * SkMeshSpecifications (one with per-vertex colour, one without), across
 * every combination of alpha / colours / shader / blender. Relies on
 * SkMeshes::MakeVertexBuffer / MakeIndexBuffer + GrDirectContext GPU copy.
 *
 * CPU subset: the upstream mesh SkSL is mapped onto the currently supported
 * `float2 position` plus optional `ubyte4_unorm color` specification. Shader
 * children, fragment output UVs, and GPU buffer copies remain out of scope,
 * but this still exercises the reviewable custommesh surface:
 * non-indexed/indexed, triangle-strip/triangles, position-only/color, alpha,
 * shader paint, and blender plumbing.
 */
public class CustomMeshGM : GM() {
    override fun getName(): String = "custommesh"
    override fun getISize(): SkISize = SkISize.Make(435, 1180)

    private lateinit var positionSpec: SkMeshSpecification
    private lateinit var positionColorSpec: SkMeshSpecification
    private lateinit var noColorVB: SkMesh.VertexBuffer
    private lateinit var colorVB: SkMesh.VertexBuffer
    private lateinit var noColorIndexedVB: SkMesh.VertexBuffer
    private lateinit var colorIndexedVB: SkMesh.VertexBuffer
    private lateinit var indexBuffer: SkMesh.IndexBuffer

    override fun onOnceBeforeDraw() {
        positionSpec = makeSpec(withColor = false)
        positionColorSpec = makeSpec(withColor = true)
        noColorVB = SkMeshes.MakeVertexBuffer(
            ByteArray(NO_COLOR_OFFSET) + positionBytes(QUAD),
            NO_COLOR_OFFSET + QUAD.size * POSITION_STRIDE,
        )
        colorVB = SkMeshes.MakeVertexBuffer(colorBytes(QUAD), QUAD.size * POSITION_COLOR_STRIDE)
        noColorIndexedVB = SkMeshes.MakeVertexBuffer(
            positionBytes(INDEXED_QUAD),
            INDEXED_QUAD.size * POSITION_STRIDE,
        )
        colorIndexedVB = SkMeshes.MakeVertexBuffer(
            ByteArray(COLOR_INDEXED_OFFSET) + colorBytes(INDEXED_QUAD),
            COLOR_INDEXED_OFFSET + INDEXED_QUAD.size * POSITION_COLOR_STRIDE,
        )
        indexBuffer = SkMeshes.MakeIndexBuffer(ByteArray(INDEX_OFFSET) + shortBytes(INDICES), INDEX_OFFSET + 12)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val shader = SkLinearGradient.Make(
            p0 = SkPoint(20f, 20f),
            p1 = SkPoint(120f, 120f),
            colors = intArrayOf(0xFFFFFFFF.toInt(), 0x00000000),
            positions = null,
            tileMode = SkTileMode.kMirror,
        )

        var i = 0
        for (mode in listOf(SkBlendMode.kDst, SkBlendMode.kSrc, SkBlendMode.kSaturation)) {
            c.save()
            for (alpha in listOf(0xFF, 0x40)) {
                for (colors in listOf(false, true)) {
                    for (useShader in listOf(false, true)) {
                        val mesh = makeMesh(indexed = i and 1 == 1, colors = colors)
                        val paint = SkPaint(0xFF00FF00.toInt()).apply {
                            this.alpha = alpha
                            if (useShader) this.shader = shader
                        }
                        c.drawMesh(mesh, SkBlender.Mode(mode), paint)
                        c.translate(0f, 150f)
                        i++
                    }
                }
            }
            c.restore()
            c.translate(150f, 0f)
        }
    }

    private fun makeSpec(withColor: Boolean): SkMeshSpecification {
        val attributes = if (withColor) {
            listOf(
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
            )
        } else {
            listOf(
                SkMeshSpecification.Attribute(
                    SkMeshSpecification.Attribute.Type.kFloat2,
                    offset = 0,
                    name = "position",
                ),
            )
        }
        val result = SkMeshSpecification.Make(
            attributes = attributes,
            vertexStride = if (withColor) POSITION_COLOR_STRIDE else POSITION_STRIDE,
            vs = "Varyings main(const Attributes a) { Varyings v; v.position = a.position; return v; }",
            fs = "float4 main(const Varyings v) { return float4(1); }",
        )
        return result.specification ?: error("CustomMeshGM spec creation failed: ${result.error}")
    }

    private fun makeMesh(indexed: Boolean, colors: Boolean): SkMesh {
        val result = if (indexed) {
            SkMesh.MakeIndexed(
                specification = if (colors) positionColorSpec else positionSpec,
                mode = SkMesh.Mode.kTriangles,
                vertexBuffer = if (colors) colorIndexedVB else noColorIndexedVB,
                vertexCount = INDEXED_QUAD.size,
                vertexOffset = if (colors) COLOR_INDEXED_OFFSET else 0,
                indexBuffer = indexBuffer,
                indexCount = INDICES.size,
                indexOffset = INDEX_OFFSET,
                bounds = RECT,
            )
        } else {
            SkMesh.Make(
                specification = if (colors) positionColorSpec else positionSpec,
                mode = SkMesh.Mode.kTriangleStrip,
                vertexBuffer = if (colors) colorVB else noColorVB,
                vertexCount = QUAD.size,
                vertexOffset = if (colors) 0 else NO_COLOR_OFFSET,
                bounds = RECT,
            )
        }
        return result.mesh.takeIf { it.isValid() } ?: error("CustomMeshGM mesh creation failed: ${result.error}")
    }

    private data class MeshVertex(val x: Float, val y: Float, val color: Int = 0xFFFFFFFF.toInt())

    private companion object {
        val RECT: SkRect = SkRect.MakeLTRB(20f, 20f, 120f, 120f)
        private const val POSITION_STRIDE = 8
        private const val POSITION_COLOR_STRIDE = 12
        private const val NO_COLOR_OFFSET = POSITION_STRIDE
        private const val COLOR_INDEXED_OFFSET = POSITION_COLOR_STRIDE * 2
        private const val INDEX_OFFSET = 6

        private val QUAD = arrayOf(
            MeshVertex(RECT.left, RECT.top, 0xFFFFFF00.toInt()),
            MeshVertex(RECT.right, RECT.top, 0xFFFFFFFF.toInt()),
            MeshVertex(RECT.left, RECT.bottom, 0xFFFF00FF.toInt()),
            MeshVertex(RECT.right, RECT.bottom, 0xFF00FFFF.toInt()),
        )
        private val INDEXED_QUAD = arrayOf(
            QUAD[0],
            MeshVertex(100f, 0f, 0x00000000),
            QUAD[1],
            MeshVertex(200f, 10f, 0x00000000),
            QUAD[2],
            QUAD[3],
        )
        private val INDICES = intArrayOf(0, 2, 4, 2, 5, 4)

        private fun positionBytes(vertices: Array<MeshVertex>): ByteArray =
            ByteBuffer.allocate(vertices.size * POSITION_STRIDE)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    vertices.forEach {
                        putFloat(it.x)
                        putFloat(it.y)
                    }
                }
                .array()

        private fun colorBytes(vertices: Array<MeshVertex>): ByteArray =
            ByteBuffer.allocate(vertices.size * POSITION_COLOR_STRIDE)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    vertices.forEach {
                        putFloat(it.x)
                        putFloat(it.y)
                        put(((it.color ushr 16) and 0xFF).toByte())
                        put(((it.color ushr 8) and 0xFF).toByte())
                        put((it.color and 0xFF).toByte())
                        put(((it.color ushr 24) and 0xFF).toByte())
                    }
                }
                .array()

        private fun shortBytes(values: IntArray): ByteArray =
            ByteBuffer.allocate(values.size * 2)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply { values.forEach { putShort(it.toShort()) } }
                .array()
    }
}

/**
 * Stub for Skia's `MeshColorSpaceGM` ("custommesh_cs", 468×258).
 *
 * Upstream creates four SkMeshSpecifications varying alpha-type (premul /
 * unpremul) and colour-space (sRGB / colour-spun). Forces an intermediate
 * sRGB surface when the canvas is in legacy mode.
 *
 * CPU subset: exercises mesh color-space and alpha-type conversion for
 * position + float4 color attributes. The upstream SkSL varying handoff is
 * represented by direct per-vertex color lowering in SkCanvas.drawMesh.
 */
public class CustomMeshCsGM : GM() {
    override fun getName(): String = "custommesh_cs"
    override fun getISize(): SkISize = SkISize.Make(468, 258)

    private lateinit var specs: Array<SkMeshSpecification>
    private lateinit var premulVB: SkMesh.VertexBuffer
    private lateinit var unpremulVB: SkMesh.VertexBuffer

    override fun onOnceBeforeDraw() {
        specs = Array(4) { index ->
            val unpremul = index >= 2
            val spin = index % 2 == 1
            val cs = if (spin) SkColorSpace.makeSRGB().makeColorSpin() else SkColorSpace.makeSRGB()
            val result = SkMeshSpecification.Make(
                attributes = listOf(
                    SkMeshSpecification.Attribute(
                        SkMeshSpecification.Attribute.Type.kFloat2,
                        offset = 0,
                        name = "pos",
                    ),
                    SkMeshSpecification.Attribute(
                        SkMeshSpecification.Attribute.Type.kFloat4,
                        offset = 8,
                        name = "color",
                    ),
                ),
                vertexStride = COLOR_SPACE_STRIDE,
                vs = "Varyings main(const Attributes a) { Varyings v; v.position = a.pos; return v; }",
                fs = "float2 main(const Varyings v, out half4 color) { return v.position; }",
                cs = cs,
                at = if (unpremul) SkAlphaType.kUnpremul else SkAlphaType.kPremul,
            )
            result.specification ?: error("CustomMeshCsGM spec creation failed: ${result.error}")
        }
        premulVB = SkMeshes.MakeVertexBuffer(colorSpaceVertexBytes(premul = true), 4 * COLOR_SPACE_STRIDE)
        unpremulVB = SkMeshes.MakeVertexBuffer(colorSpaceVertexBytes(premul = false), 4 * COLOR_SPACE_STRIDE)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val shader = SkLinearGradient.Make(
            p0 = SkPoint(COLOR_SPACE_RECT.left, 0f),
            p1 = SkPoint(COLOR_SPACE_RECT.centerX(), 0f),
            colors = intArrayOf(0xFFFFFFFF.toInt(), 0x00000000),
            positions = null,
            tileMode = SkTileMode.kMirror,
        )

        for (useShader in listOf(false, true)) {
            for (unpremul in listOf(false, true)) {
                c.save()
                for (spin in listOf(false, true)) {
                    val mesh = SkMesh.Make(
                        specification = specs[colorSpaceSpecIndex(unpremul, spin)],
                        mode = SkMesh.Mode.kTriangleStrip,
                        vertexBuffer = if (unpremul) unpremulVB else premulVB,
                        vertexCount = 4,
                        vertexOffset = 0,
                        bounds = COLOR_SPACE_RECT,
                    ).mesh.takeIf { it.isValid() } ?: error("CustomMeshCsGM mesh creation failed")
                    val paint = SkPaint().apply {
                        if (useShader) this.shader = shader
                    }
                    c.drawMesh(mesh, paint)
                    c.translate(0f, COLOR_SPACE_RECT.height() + 10f)
                }
                c.restore()
                c.translate(COLOR_SPACE_RECT.width() + 10f, 0f)
            }
        }
    }

    private companion object {
        private const val COLOR_SPACE_STRIDE = 24
        private val COLOR_SPACE_RECT: SkRect = SkRect.MakeLTRB(20f, 20f, 120f, 120f)
        private val COLOR_SPACE_COLORS = arrayOf(
            floatArrayOf(1f, 0f, 0f, 1f),
            floatArrayOf(0f, 1f, 0f, 0f),
            floatArrayOf(1f, 1f, 0f, 0f),
            floatArrayOf(0f, 0f, 1f, 1f),
        )
        private val COLOR_SPACE_POINTS = arrayOf(
            SkPoint(COLOR_SPACE_RECT.left, COLOR_SPACE_RECT.top),
            SkPoint(COLOR_SPACE_RECT.right, COLOR_SPACE_RECT.top),
            SkPoint(COLOR_SPACE_RECT.left, COLOR_SPACE_RECT.bottom),
            SkPoint(COLOR_SPACE_RECT.right, COLOR_SPACE_RECT.bottom),
        )

        private fun colorSpaceSpecIndex(unpremul: Boolean, spin: Boolean): Int =
            (if (unpremul) 2 else 0) + if (spin) 1 else 0

        private fun colorSpaceVertexBytes(premul: Boolean): ByteArray =
            ByteBuffer.allocate(4 * COLOR_SPACE_STRIDE)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    for (i in COLOR_SPACE_POINTS.indices) {
                        val p = COLOR_SPACE_POINTS[i]
                        val c = COLOR_SPACE_COLORS[i]
                        val a = c[3]
                        putFloat(p.fX)
                        putFloat(p.fY)
                        putFloat(if (premul) c[0] * a else c[0])
                        putFloat(if (premul) c[1] * a else c[1])
                        putFloat(if (premul) c[2] * a else c[2])
                        putFloat(a)
                    }
                }
                .array()
    }
}

/**
 * Stub for Skia's `MeshUniformsGM` ("custommesh_uniforms", 140×250).
 *
 * Upstream packs three typed uniforms (float[2] t, half3×3 m, half4 color)
 * into an SkData blob and drives a radial-gradient shader through the mesh
 * fragment stage. Also implements onAnimate (sine-wave colour animation).
 *
 * TODO("STUB.MESH") — SkMesh / SkMeshSpecification not implemented.
 */
public class CustomMeshUniformsGM : GM() {
    override fun getName(): String = "custommesh_uniforms"
    override fun getISize(): SkISize = SkISize.Make(140, 250)
    private lateinit var spec: SkMeshSpecification
    private lateinit var vb: SkMesh.VertexBuffer

    override fun onOnceBeforeDraw() {
        val result = SkMeshSpecification.Make(
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
            vs = "uniform float4 uColor; Varyings main(const Attributes a) { Varyings v; v.position = a.position; return v; }",
            fs = "float4 main(const Varyings v, float4 meshColor) { return meshColor * uColor; }",
        )
        spec = result.specification ?: error("CustomMeshUniformsGM spec creation failed: ${result.error}")
        vb = SkMeshes.MakeVertexBuffer(vertexBytes(), 4 * 12)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val mesh = SkMesh.Make(
            specification = spec,
            mode = SkMesh.Mode.kTriangleStrip,
            vertexBuffer = vb,
            vertexCount = 4,
            vertexOffset = 0,
            uniforms = SkData.MakeWithCopy(
                ByteBuffer.allocate(16)
                    .order(ByteOrder.LITTLE_ENDIAN)
                    .putFloat(0.8f)
                    .putFloat(0.35f)
                    .putFloat(0.9f)
                    .putFloat(0.85f)
                    .array(),
            ),
            bounds = SkRect.MakeLTRB(20f, 20f, 120f, 220f),
        ).mesh
        c.drawMesh(mesh, SkPaint(0xFFFFFFFF.toInt()))
    }

    private fun vertexBytes(): ByteArray =
        ByteBuffer.allocate(4 * 12)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putVertex(20f, 20f, 255, 255, 255, 255)
                putVertex(120f, 20f, 0, 0, 255, 255)
                putVertex(20f, 220f, 0, 255, 0, 255)
                putVertex(120f, 220f, 255, 0, 0, 255)
            }
            .array()

    private fun ByteBuffer.putVertex(x: Float, y: Float, r: Int, g: Int, b: Int, a: Int) {
        putFloat(x)
        putFloat(y)
        put(r.toByte())
        put(g.toByte())
        put(b.toByte())
        put(a.toByte())
    }
}

/**
 * Stub for Skia's `MeshUpdateGM` ("mesh_updates", 270×490).
 *
 * Upstream tests SkMesh::VertexBuffer::update() and
 * SkMesh::IndexBuffer::update() for both CPU-backed and GPU-backed
 * buffers, with wrapping offsets (kVBRects / kIBRects). Skips on GPU
 * recording contexts (only direct contexts).
 *
 * TODO("STUB.MESH") — SkMesh / SkMeshSpecification not implemented.
 */
public class MeshUpdateGM : GM() {
    override fun getName(): String = "mesh_updates"
    override fun getISize(): SkISize = SkISize.Make(270, 490)

    private lateinit var spec: SkMeshSpecification

    override fun onOnceBeforeDraw() {
        val result = SkMeshSpecification.Make(
            attributes = listOf(
                SkMeshSpecification.Attribute(
                    SkMeshSpecification.Attribute.Type.kFloat2,
                    offset = 0,
                    name = "position",
                ),
            ),
            vertexStride = 8,
            vs = "Varyings main(const Attributes a) { Varyings v; v.position = a.position; return v; }",
            fs = "float4 main(const Varyings v) { return float4(1); }",
        )
        spec = result.specification ?: error("MeshUpdateGM spec creation failed: ${result.error}")
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        drawStrip(
            canvas = c,
            yOffset = 20f,
            color = 0xFF0055FF.toInt(),
            leftStart = 15f,
            rightStart = 95f,
            leftUpdated = 45f,
            rightUpdated = 125f,
            useIndexed = false,
        )
        drawStrip(
            canvas = c,
            yOffset = 170f,
            color = 0xFFFF6A00.toInt(),
            leftStart = 15f,
            rightStart = 95f,
            leftUpdated = 45f,
            rightUpdated = 125f,
            useIndexed = true,
        )
        drawStrip(
            canvas = c,
            yOffset = 320f,
            color = 0xFF0A9F43.toInt(),
            leftStart = 15f,
            rightStart = 95f,
            leftUpdated = 45f,
            rightUpdated = 125f,
            useIndexed = true,
            indexSwap = true,
        )
    }

    private fun drawStrip(
        canvas: SkCanvas,
        yOffset: Float,
        color: Int,
        leftStart: Float,
        rightStart: Float,
        leftUpdated: Float,
        rightUpdated: Float,
        useIndexed: Boolean,
        indexSwap: Boolean = false,
    ) {
        val initialVertices = quadVertices(leftStart, yOffset, rightStart, yOffset + 110f)
        val vertexBuffer = SkMeshes.MakeVertexBuffer(initialVertices, initialVertices.size)

        val updateBytes = ByteBuffer.allocate(16)
            .order(ByteOrder.LITTLE_ENDIAN)
            .putFloat(leftUpdated)
            .putFloat(yOffset)
            .putFloat(rightUpdated)
            .putFloat(yOffset)
            .array()
        check(vertexBuffer.update(updateBytes, offset = 0, size = updateBytes.size))

        val mesh = if (useIndexed) {
            val initialIndices = shortBytes(shortArrayOf(0, 1, 2, 1, 3, 2))
            val indexBuffer = SkMeshes.MakeIndexBuffer(initialIndices, initialIndices.size)
            if (indexSwap) {
                val swapped = shortBytes(shortArrayOf(0, 2, 1, 1, 2, 3))
                check(indexBuffer.update(swapped, offset = 0, size = swapped.size))
            }
            SkMesh.MakeIndexed(
                specification = spec,
                mode = SkMesh.Mode.kTriangles,
                vertexBuffer = vertexBuffer,
                vertexCount = 4,
                vertexOffset = 0,
                indexBuffer = indexBuffer,
                indexCount = 6,
                indexOffset = 0,
                bounds = SkRect.MakeLTRB(leftUpdated, yOffset, rightUpdated, yOffset + 110f),
            ).mesh
        } else {
            SkMesh.Make(
                specification = spec,
                mode = SkMesh.Mode.kTriangleStrip,
                vertexBuffer = vertexBuffer,
                vertexCount = 4,
                vertexOffset = 0,
                bounds = SkRect.MakeLTRB(leftUpdated, yOffset, rightUpdated, yOffset + 110f),
            ).mesh
        }

        canvas.drawMesh(mesh, SkPaint(color))
    }

    private fun quadVertices(left: Float, top: Float, right: Float, bottom: Float): ByteArray =
        ByteBuffer.allocate(32)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply {
                putFloat(left)
                putFloat(top)
                putFloat(right)
                putFloat(top)
                putFloat(left)
                putFloat(bottom)
                putFloat(right)
                putFloat(bottom)
            }
            .array()

    private fun shortBytes(values: ShortArray): ByteArray =
        ByteBuffer.allocate(values.size * 2)
            .order(ByteOrder.LITTLE_ENDIAN)
            .apply { values.forEach { putShort(it) } }
            .array()
}

/**
 * Stub for Skia's `MeshZeroInitGM` ("mesh_zero_init", 90×30).
 *
 * Upstream verifies that freshly-allocated GPU vertex/index buffers are
 * zero-initialised: uses the zeroed region as the first index, then
 * re-uses (recycles) the buffer to check the recycled block is also zeroed.
 *
 * TODO("STUB.MESH") — SkMesh / SkMeshSpecification not implemented.
 */
public class MeshZeroInitGM : GM() {
    override fun getName(): String = "mesh_zero_init"
    override fun getISize(): SkISize = SkISize.Make(90, 30)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}

/**
 * Stub for Skia's `PictureMesh` ("picture_mesh", 390×90).
 *
 * Upstream records an SkMesh draw into an SkPicture and plays it back,
 * exercising the picture→GPU path. Tests four draw modes (triangles /
 * triangleStrip × non-indexed / indexed) with a sweep-gradient paint.
 *
 * TODO("STUB.MESH") — SkMesh / SkMeshSpecification not implemented.
 */
public class PictureMeshGM : GM() {
    override fun getName(): String = "picture_mesh"
    override fun getISize(): SkISize = SkISize.Make(390, 90)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}

/**
 * Shared stub base for Skia's `MeshWithShadersGM` (four registered GMs).
 *
 * Upstream creates a 16×16 ripple-animated mesh grid and draws it with four
 * shader-child configurations passed to the SkMeshSpecification fragment:
 *  - kMeshWithImage      ("mesh_with_image",       320×320)
 *  - kMeshWithPaintColor ("mesh_with_paint_color",  320×320)
 *  - kMeshWithPaintImage ("mesh_with_paint_image",  320×320)
 *  - kMeshWithEffects    ("mesh_with_effects",      320×320)
 *
 * The fragment shader consumes up to four child slots (shader×2,
 * colorFilter×1, blender×1) and blends them together.
 *
 * TODO("STUB.MESH") — SkMesh / SkMeshSpecification not implemented.
 */
public class MeshWithImageGM : GM() {
    override fun getName(): String = "mesh_with_image"
    override fun getISize(): SkISize = SkISize.Make(320, 320)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}

public class MeshWithPaintColorGM : GM() {
    override fun getName(): String = "mesh_with_paint_color"
    override fun getISize(): SkISize = SkISize.Make(320, 320)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}

public class MeshWithPaintImageGM : GM() {
    override fun getName(): String = "mesh_with_paint_image"
    override fun getISize(): SkISize = SkISize.Make(320, 320)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}

public class MeshWithEffectsGM : GM() {
    override fun getName(): String = "mesh_with_effects"
    override fun getISize(): SkISize = SkISize.Make(320, 320)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}

/**
 * Stub for Skia's `custommesh_cs_uniforms` DEF_SIMPLE_GM_CAN_FAIL
 * (200×900).
 *
 * Upstream tests that `layout(color)` uniforms in the fragment shader are
 * correctly colour-managed vs raw (non-managed) uniforms across several
 * (meshCS, surfaceCS) combinations. Skips on CPU-only / recording contexts.
 *
 * CPU subset: covers the color-managed uniform contract for solid half4
 * mesh fragment output. GPU recording requirements from upstream are not
 * required for the portable raster cell.
 */
public class CustomMeshCsUniformsGM : GM() {
    override fun getName(): String = "custommesh_cs_uniforms"
    override fun getISize(): SkISize = SkISize.Make(200, 900)

    private lateinit var vb: SkMesh.VertexBuffer
    private lateinit var uniforms: SkData

    override fun onOnceBeforeDraw() {
        vb = SkMeshes.MakeVertexBuffer(csUniformQuadBytes(), 4 * 8)
        uniforms = SkData.MakeWithCopy(
            ByteBuffer.allocate(16)
                .order(ByteOrder.LITTLE_ENDIAN)
                .putFloat(1f)
                .putFloat(0f)
                .putFloat(0f)
                .putFloat(1f)
                .array(),
        )
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        for (config in CS_UNIFORM_CONFIGS) {
            val surface = makeCsUniformSurface(c, config.surfaceCS)
            val offscreen = surface.canvas
            offscreen.clear(0xFFFFFFFF.toInt())
            val mesh = makeCsUniformMesh(config.managed, config.meshCS)
            offscreen.drawMesh(mesh, SkPaint())
            offscreen.translate(100f, 0f)
            offscreen.drawRect(CS_UNIFORM_RECT, SkPaint(config.expectedColor))
            surface.draw(c, 0f, 0f)
            c.translate(0f, 100f)
        }
    }

    private fun makeCsUniformMesh(managed: Boolean, workingCS: SkColorSpace): SkMesh {
        val result = SkMeshSpecification.Make(
            attributes = listOf(
                SkMeshSpecification.Attribute(
                    SkMeshSpecification.Attribute.Type.kFloat2,
                    offset = 0,
                    name = "pos",
                ),
            ),
            vertexStride = 8,
            vs = "Varyings main(const Attributes a) { Varyings v; v.position = a.pos; return v; }",
            fs = if (managed) {
                "layout(color) uniform half4 color; float2 main(const Varyings v, out half4 c) { c = color; return v.position; }"
            } else {
                "uniform half4 color; float2 main(const Varyings v, out half4 c) { c = color; return v.position; }"
            },
            cs = workingCS,
            at = SkAlphaType.kPremul,
        )
        val spec = result.specification ?: error("CustomMeshCsUniformsGM spec creation failed: ${result.error}")
        return SkMesh.Make(
            specification = spec,
            mode = SkMesh.Mode.kTriangleStrip,
            vertexBuffer = vb,
            vertexCount = 4,
            vertexOffset = 0,
            uniforms = uniforms,
            bounds = CS_UNIFORM_RECT,
        ).mesh.takeIf { it.isValid() } ?: error("CustomMeshCsUniformsGM mesh creation failed")
    }

    private fun makeCsUniformSurface(canvas: SkCanvas, cs: SkColorSpace?): org.skia.core.SkSurface {
        val info = SkImageInfo.MakeN32Premul(200, 100, cs ?: SkColorSpace.makeSRGB())
        return canvas.makeSurface(info)
            ?: SkSurfaces.Raster(info)
            ?: error("CustomMeshCsUniformsGM surface creation failed")
    }

    private companion object {
        private val CS_UNIFORM_RECT: SkRect = SkRect.MakeLTRB(20f, 20f, 80f, 80f)
        private val SRGB_CS: SkColorSpace = SkColorSpace.makeSRGB()
        private val SPIN_CS: SkColorSpace = SkColorSpace.makeSRGB().makeColorSpin()
        private val WIDE_CS: SkColorSpace = SkColorSpace.makeRGB(SkNamedTransferFn.k2Dot2, SkNamedGamut.kRec2020)!!

        private data class CsUniformConfig(
            val meshCS: SkColorSpace,
            val surfaceCS: SkColorSpace?,
            val managed: Boolean,
            val expectedColor: Int = 0xFFFF0000.toInt(),
        )

        private val CS_UNIFORM_CONFIGS = listOf(
            CsUniformConfig(SRGB_CS, null, managed = true),
            CsUniformConfig(SRGB_CS, SRGB_CS, managed = true),
            CsUniformConfig(SRGB_CS, SPIN_CS, managed = true),
            CsUniformConfig(SRGB_CS, WIDE_CS, managed = true),
            CsUniformConfig(SPIN_CS, SRGB_CS, managed = true),
            CsUniformConfig(SPIN_CS, SPIN_CS, managed = true),
            CsUniformConfig(SPIN_CS, WIDE_CS, managed = true),
            CsUniformConfig(SPIN_CS, SRGB_CS, managed = false, expectedColor = 0xFF00FF00.toInt()),
            CsUniformConfig(SPIN_CS, WIDE_CS, managed = false, expectedColor = 0xFF00FF00.toInt()),
        )

        private fun csUniformQuadBytes(): ByteArray =
            ByteBuffer.allocate(4 * 8)
                .order(ByteOrder.LITTLE_ENDIAN)
                .apply {
                    putFloat(CS_UNIFORM_RECT.left)
                    putFloat(CS_UNIFORM_RECT.top)
                    putFloat(CS_UNIFORM_RECT.right)
                    putFloat(CS_UNIFORM_RECT.top)
                    putFloat(CS_UNIFORM_RECT.left)
                    putFloat(CS_UNIFORM_RECT.bottom)
                    putFloat(CS_UNIFORM_RECT.right)
                    putFloat(CS_UNIFORM_RECT.bottom)
                }
                .array()
    }
}
