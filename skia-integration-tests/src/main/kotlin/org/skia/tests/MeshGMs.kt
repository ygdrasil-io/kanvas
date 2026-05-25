package org.skia.tests

import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.core.SkCanvas
import org.skia.core.SkMesh
import org.skia.core.SkMeshSpecification
import org.skia.core.SkMeshes
import org.skia.foundation.SkBlender
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
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
 * TODO("STUB.MESH") — SkMesh / SkMeshSpecification not implemented.
 */
public class CustomMeshCsGM : GM() {
    override fun getName(): String = "custommesh_cs"
    override fun getISize(): SkISize = SkISize.Make(468, 258)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
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
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
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
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
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
 * TODO("STUB.MESH") — SkMesh / SkMeshSpecification not implemented.
 */
public class CustomMeshCsUniformsGM : GM() {
    override fun getName(): String = "custommesh_cs_uniforms"
    override fun getISize(): SkISize = SkISize.Make(200, 900)
    override fun onDraw(canvas: SkCanvas?) {
        TODO("STUB.MESH")
    }
}
