package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRSXform
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkVertices
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of `gm/drawatlas.cpp::compare_atlas_vertices`
 * (DEF_SIMPLE_GM, 560 × 585).
 *
 * Produces pairs of panels `(drawAtlas | drawVertices)` for every
 * combination of alpha × colorFilter × blendMode. Each pair should
 * look the same, exercising the invariant that `drawAtlas` and
 * `drawVertices` produce equivalent results when fed matching geometry,
 * texture coordinates, per-vertex colours, and blend mode.
 *
 * **Implementation notes**:
 *  - `SkRect.toQuad()` (upstream returns TL, TR, BR, BL corners as an
 *    `std::array<SkPoint, 4>`) is inlined as [rectToQuad].
 *  - `SkVertices::MakeCopy(kTriangleFan, 4, pos, pos, colors)` passes
 *    the same corner array as both vertex positions and texture
 *    coordinates (the image shader samples at the same UV as the vertex
 *    position).
 *  - The `drawAtlas` + `drawVertices` pair is split over 145-px columns;
 *    128 px atlas + 17 px gap matches the upstream layout.
 */
public class CompareAtlasVerticesGM : GM() {

    init { setBGColor(0xFFCCCCCC.toInt()) }

    override fun getName(): String = "compare_atlas_vertices"
    override fun getISize(): SkISize = SkISize.Make(560, 585)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val tex = SkRect.MakeWH(128f, 128f)
        val xform = SkRSXform.Make(1f, 0f, 0f, 0f)
        val color = 0x884488CC.toInt()

        val image = ToolUtils.GetResourceAsImage("images/mandrill_128.png") ?: return
        val verts = makeVertices(tex, color)

        val filters = arrayOf<org.skia.foundation.SkColorFilter?>(
            null,
            SkColorFilters.Blend(0xFF00FF88.toInt(), SkBlendMode.kModulate),
        )
        val modes = arrayOf(SkBlendMode.kSrcOver, SkBlendMode.kPlus)

        c.translate(10f, 10f)
        val paint = SkPaint()
        for (mode in modes) {
            for (alpha in floatArrayOf(1.0f, 0.5f)) {
                paint.alphaf = alpha
                c.save()
                for (cf in filters) {
                    paint.colorFilter = cf
                    // drawAtlas panel
                    c.drawAtlas(
                        image = image,
                        xform = arrayOf(xform),
                        src = arrayOf(tex),
                        colors = intArrayOf(color),
                        blendMode = mode,
                        sampling = SkSamplingOptions.Default,
                        cullRect = tex,
                        paint = paint,
                    )
                    c.translate(128f, 0f)
                    // drawVertices panel (same image as shader)
                    val vertPaint = paint.copy().apply {
                        shader = image.makeShader(SkSamplingOptions.Default)
                    }
                    c.drawVertices(verts, mode, vertPaint)
                    c.translate(145f, 0f)
                }
                c.restore()
                c.translate(0f, 145f)
            }
        }
    }

    /**
     * Mirrors `make_vertices` from the cpp — builds a kTriangleFan
     * quad with the rect corners used as both positions and texture
     * coordinates, carrying a uniform per-vertex [color].
     */
    private fun makeVertices(r: SkRect, color: Int): SkVertices {
        val pos = rectToQuad(r)
        val colors = IntArray(4) { color }
        return SkVertices.MakeCopy(
            mode = SkVertices.VertexMode.kTriangleFan,
            positions = pos,
            texCoords = pos,
            colors = colors,
        )
    }

    /**
     * Mirrors `SkRect::toQuad()` — four corners in Skia's order:
     * top-left, top-right, bottom-right, bottom-left.
     */
    private fun rectToQuad(r: SkRect): Array<SkPoint> = arrayOf(
        r.TL(), r.TR(), r.BR(), r.BL(),
    )
}
