package org.graphiks.kanvas.skia.gm.mesh

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Blender
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.VertexMode
import kotlin.math.sin

/** Port of Skia's `gm/mesh.cpp` (effects variant).
 *  Renders a gradient mesh with color filters and blenders applied.
 *  @see https://github.com/google/skia/blob/main/gm/mesh.cpp
 */
class MeshWithEffectsGm : SkiaGm {
    override val name = "mesh_with_effects"
    override val renderFamily = RenderFamily.MESH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 320
    override val height = 320

    private var mandrillShader: Shader? = null
    private var time = 0.0
    private var positions: List<Point> = emptyList()
    private var uvs: List<Point> = emptyList()
    private val indices: List<Int> = buildIndices()

    override fun onOnceBeforeDraw(canvas: GmCanvas) {
        val bytes = loadResource("images/mandrill_128.png")
            ?: error("Resource not found: images/mandrill_128.png")
        val image = Image.decode(bytes)
        mandrillShader = image.makeShader(sampling = SamplingOptions.LINEAR)
        updateVertices()
    }

    override fun onAnimate(deltaMs: Long): Boolean {
        time += deltaMs / 1000.0
        updateVertices()
        return true
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f)
        val verts = Vertices(
            mode = VertexMode.TRIANGLES,
            positions = positions,
            texCoords = uvs,
            indices = indices,
        )
        val paint = Paint(
            shader = mandrillShader,
            colorFilter = ColorFilter.Table(UByteArray(256) { (255 - it).toUByte() }),
            blender = Blender.Mode(BlendMode.DST_OVER),
        )
        canvas.drawVertices(verts, paint)
    }

    private fun updateVertices() {
        val periodic = (time % 4.0) / 4.0 * 2.0 * Math.PI
        val xOff = DoubleArray(kMeshSize) { sin(periodic + it * 0.8) * kRippleSize }
        val yOff = DoubleArray(kMeshSize) { sin(periodic + 10.0 + it * 0.8) * kRippleSize }
        positions = (0 until kMeshSize).flatMap { y ->
            val yf = y.toFloat() / (kMeshSize - 1)
            (0 until kMeshSize).map { x ->
                val xf = x.toFloat() / (kMeshSize - 1)
                Point(
                    kRect.left + xf * kRect.width + xOff[y].toFloat(),
                    kRect.top + yf * kRect.height + yOff[x].toFloat())
            }
        }
        uvs = (0 until kMeshSize).flatMap { y ->
            val yf = y.toFloat() / (kMeshSize - 1)
            (0 until kMeshSize).map { x ->
                val xf = x.toFloat() / (kMeshSize - 1)
                Point(kUv.left + xf * kUv.width, kUv.top + yf * kUv.height)
            }
        }
    }

    private fun loadResource(path: String): ByteArray? {
        return this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
    }

    private companion object {
        private val kRect = Rect(20f, 20f, 300f, 300f)
        private val kUv = Rect(0f, 0f, 128f, 128f)
        private const val kMeshSize = 16
        private const val kRippleSize = 6.0

        private fun buildIndices(): List<Int> {
            val idx = mutableListOf<Int>()
            for (y in 0 until kMeshSize - 1)
                for (x in 0 until kMeshSize - 1) {
                    val tl = y * kMeshSize + x; val tr = tl + 1
                    val bl = (y + 1) * kMeshSize + x; val br = bl + 1
                    idx.addAll(listOf(tl, tr, bl, br, bl, tr))
                }
            return idx
        }
    }
}
