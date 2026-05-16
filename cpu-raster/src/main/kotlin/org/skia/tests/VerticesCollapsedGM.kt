package org.skia.tests

import org.skia.core.SkBitmapDevice
import org.skia.core.SkCanvas
import org.skia.foundation.SK_ColorGREEN
import org.skia.foundation.SK_ColorWHITE
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkPaint
import org.skia.foundation.SkVertices
import org.skia.math.SkISize
import org.skia.math.SkPoint

/**
 * Port of Skia's `gm/vertices.cpp::vertices_collapsed`
 * (`DEF_SIMPLE_GM_BG(vertices_collapsed, canvas, 50, 50, SK_ColorWHITE)`).
 *
 * Stress test for `drawVertices` with **collapsed** texture
 * coordinates : the 4 vertex UVs all share `(0, 0)`, so every
 * triangle samples the shader at the same texel. Upstream's
 * regression reference (`b/40044794`) checks that the rasterizer
 * doesn't try to project / divide by a degenerate triangle UV
 * footprint.
 *
 * Setup (per upstream) :
 *  1. Build a 1×1 raster surface, clear it green ; this is the
 *     shader source.
 *  2. Build an `SkVertices` with `(verts = 4 corners of a 40×40
 *     rect, texCoords = (0,0) ×4, indices = 2 triangles)`.
 *  3. Draw with `SkBlendMode.kDst` so the texture sample wins
 *     unconditionally — the result is a green 40×40 quad on a
 *     white background.
 */
public class VerticesCollapsedGM : GM() {

    override fun getName(): String = "vertices_collapsed"
    override fun getISize(): SkISize = SkISize.Make(50, 50)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.drawColor(SK_ColorWHITE)

        // Build the 1×1 green source.
        val src = SkBitmap(1, 1)
        src.eraseColor(SK_ColorGREEN)
        val shader = src.asImage().makeShader()

        val verts = arrayOf(SkPoint(5f, 5f), SkPoint(45f, 5f), SkPoint(45f, 45f), SkPoint(5f, 45f))
        val texs = arrayOf(SkPoint(0f, 0f), SkPoint(0f, 0f), SkPoint(0f, 0f), SkPoint(0f, 0f))
        val indices = shortArrayOf(0, 1, 2, 2, 3, 0)

        val v = SkVertices.MakeCopy(
            SkVertices.VertexMode.kTriangles,
            verts,
            texCoords = texs,
            indices = indices,
        )
        val paint = SkPaint().apply { this.shader = shader }
        c.drawVertices(v, SkBlendMode.kDst, paint)
    }
}
