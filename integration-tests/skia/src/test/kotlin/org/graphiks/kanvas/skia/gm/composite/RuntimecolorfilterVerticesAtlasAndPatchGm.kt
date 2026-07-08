package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.Vertices
import org.graphiks.kanvas.types.VertexMode

/**
 * Port of Skia's `gm/runtimecolorfilter.cpp` — vertices, atlas, and patch variant.
 * Tests runtime color filter (Luma) applied to vertices, atlas, and patch draws.
 * @see https://github.com/google/skia/blob/main/gm/runtimecolorfilter.cpp
 */
class RuntimecolorfilterVerticesAtlasAndPatchGm : SkiaGm {
    override val name = "runtimecolorfilter_vertices_atlas_and_patch"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 404
    override val height = 404

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val r = Rect(0f, 0f, 128f, 128f)

        val pos = listOf(
            Point(r.left, r.top),
            Point(r.right, r.top),
            Point(r.right, r.bottom),
            Point(r.left, r.bottom),
        )
        val kColors = listOf(
            Color.BLUE, Color.GREEN,
            Color.fromRGBA(0f, 1f, 1f, 1f),    // cyan
            Color.fromRGBA(1f, 1f, 0f, 1f),    // yellow
        )
        val verts = Vertices(
            mode = VertexMode.TRIANGLE_FAN,
            positions = pos,
            texCoords = pos,
            colors = kColors,
        )

        val surf = Surface(128, 128)
        surf.canvas {
            drawVertices(verts, Paint(blendMode = BlendMode.DST))
        }
        val atlas = surf.makeImageSnapshot()

        val vx = pos[1].x - pos[0].x
        val vy = pos[3].y - pos[0].y
        val vx3 = vx / 3f
        val vy3 = vy / 3f
        val cubics = listOf(
            pos[0], Point(pos[0].x + vx3, pos[0].y), Point(pos[1].x - vx3, pos[1].y),
            pos[1], Point(pos[1].x, pos[1].y + vy3), Point(pos[2].x, pos[2].y - vy3),
            pos[2], Point(pos[2].x - vx3, pos[2].y), Point(pos[3].x + vx3, pos[3].y),
            pos[3], Point(pos[3].x, pos[3].y - vy3), Point(pos[0].x, pos[0].y + vy3),
        )

        val cf: ColorFilter = ColorFilter.Luma

        val size = r.width

        drawVertices(canvas, 0f, verts, cf, useCF = false, useShader = false)
        drawVertices(canvas, size + 10f, verts, cf, useCF = true, useShader = false)
        drawVertices(canvas, 2f * (size + 10f), verts, cf, useCF = true, useShader = true)

        canvas.translate(0f, size + 10f)
        drawAtlas(canvas, 0f, atlas, r, cf, useCF = false)
        drawAtlas(canvas, size + 10f, atlas, r, cf, useCF = true)

        canvas.translate(0f, size + 10f)
        drawPatch(canvas, 0f, cubics, pos, cf, useCF = false)
        drawPatch(canvas, size + 10f, cubics, pos, cf, useCF = true)
    }

    private fun drawVertices(
        canvas: GmCanvas, x: Float, verts: Vertices, cf: ColorFilter,
        useCF: Boolean, useShader: Boolean,
    ) {
        canvas.save()
        canvas.translate(x, 0f)
        val mode = if (useShader) BlendMode.SRC else BlendMode.DST
        val paint = Paint(
            colorFilter = if (useCF) cf else null,
            blendMode = mode,
        )
        canvas.drawVertices(verts, paint)
        canvas.restore()
    }

    private fun drawAtlas(
        canvas: GmCanvas, x: Float, atlas: Image, tex: Rect, cf: ColorFilter,
        useCF: Boolean,
    ) {
        canvas.save()
        canvas.translate(x, 0f)
        val paint = Paint(colorFilter = if (useCF) cf else null)
        canvas.drawAtlas(
            atlas, listOf(Matrix33.identity()), listOf(tex),
            colors = listOf(Color.WHITE), blendMode = BlendMode.MODULATE, paint = paint,
        )
        canvas.restore()
    }

    private fun drawPatch(
        canvas: GmCanvas, x: Float, cubics: List<Point>, pos: List<Point>,
        cf: ColorFilter, useCF: Boolean,
    ) {
        canvas.save()
        canvas.translate(x, 0f)
        val paint = Paint(
            colorFilter = if (useCF) cf else null,
        )
        canvas.drawPatch(cubics, null, pos, BlendMode.MODULATE, paint)
        canvas.restore()
    }
}
