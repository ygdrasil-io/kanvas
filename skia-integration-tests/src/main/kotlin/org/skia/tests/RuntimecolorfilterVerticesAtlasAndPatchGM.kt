package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.effects.SkBuiltinColorFilterEffects
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorCYAN
import org.graphiks.math.SK_ColorGREEN
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SK_ColorYELLOW
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRSXform
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkVertices
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/runtimecolorfilter.cpp`
 * (`DEF_SIMPLE_GM(runtimecolorfilter_vertices_atlas_and_patch, canvas, 404, 404)`).
 *
 * Exercises a runtime color-filter (the `gLumaSrc` luma→alpha shader)
 * applied across three drawing primitives — [SkCanvas.drawVertices],
 * [SkCanvas.drawAtlas], and [SkCanvas.drawPatch] — each shown with and
 * without the filter so the filter's effect is clearly visible:
 *
 * Layout (404 × 404):
 * ```
 * Row 0 (y=0): drawVertices — no CF | CF+vertex-colors | CF+image-shader
 * Row 1 (y=138): drawAtlas — no CF | CF
 * Row 2 (y=276): drawPatch — no CF | CF
 * ```
 * Each cell is 128×128 with a 10-px gap between cells.
 *
 * **Adaptation notes**:
 * - The upstream atlas surface uses `canvas->imageInfo().refColorSpace()` for
 *   its colour space — we pass `MakeN32Premul(128, 128)` which defaults to sRGB
 *   (identical for the raster path).
 * - The `kModulate` blend mode in `drawAtlas` and `drawPatch` is accepted by
 *   [SkCanvas.drawAtlas] / [SkCanvas.drawPatch] but the current `:kanvas-skia`
 *   implementation passes it through to [SkCanvas.drawVertices]; the actual
 *   colour-modulate interaction with per-sprite `colors` is a Phase I5.3.b
 *   deferred item — results will deviate from upstream for the atlas rows.
 */
public class RuntimecolorfilterVerticesAtlasAndPatchGM : GM() {

    override fun getName(): String = "runtimecolorfilter_vertices_atlas_and_patch"
    override fun getISize(): SkISize = SkISize.Make(404, 404)

    // ─── upstream constants ────────────────────────────────────────────
    // r = SkRect::MakeWH(128, 128)
    private val r = SkRect.MakeWH(128f, 128f)

    // pos = r.toQuad() — upstream order: TL, TR, BR, BL
    private val pos: Array<SkPoint> = arrayOf(
        SkPoint(r.left,  r.top),    // [0] TL
        SkPoint(r.right, r.top),    // [1] TR
        SkPoint(r.right, r.bottom), // [2] BR
        SkPoint(r.left,  r.bottom), // [3] BL
    )

    // per-vertex colors matching upstream kColors[]
    private val kColors: IntArray = intArrayOf(
        SK_ColorBLUE,
        SK_ColorGREEN,
        SK_ColorCYAN,
        SK_ColorYELLOW,
    )

    // RSXform xform = SkRSXform::Make(1, 0, 0, 0)  (identity, no translate)
    private val xform = SkRSXform.Make(1f, 0f, 0f, 0f)

    // Lazily compiled gLumaSrc color filter
    private val colorFilter: SkColorFilter by lazy {
        @Suppress("UNUSED_VARIABLE")
        SkBuiltinColorFilterEffects // trigger registration
        val result = SkRuntimeEffect.MakeForColorFilter(LUMA_SRC_SKSL)
        result.effect?.makeColorFilter(uniforms = null)
            ?: error("runtimecolorfilter_vertices_atlas_and_patch: gLumaSrc compile failed: ${result.errorText}")
    }

    // Triangle-fan vertices (SkVertices::MakeCopy)
    private val verts: SkVertices by lazy {
        SkVertices.MakeCopy(
            mode      = SkVertices.VertexMode.kTriangleFan,
            positions = pos,
            texCoords = pos,   // upstream passes pos.data() for both positions and texs
            colors    = kColors,
        )
    }

    // Atlas image built from drawing `verts` onto a surface (SkBlendMode::kDst)
    private val atlas: SkImage by lazy {
        val info = SkImageInfo.MakeN32Premul(128, 128)
        val surf = SkSurface.MakeRaster(info)
        surf.canvas.drawVertices(verts, SkBlendMode.kDst, SkPaint())
        surf.makeImageSnapshot()
    }

    // Patch cubics — same derivation as upstream:
    //   vx = (pos[1]-pos[0]) scaled to 1/3 length
    //   vy = (pos[3]-pos[0]) scaled to 1/3 length
    //   cubics[12] = { pos[0], pos[0]+vx, pos[1]-vx,
    //                  pos[1], pos[1]+vy, pos[2]-vy,
    //                  pos[2], pos[2]-vx, pos[3]+vx,
    //                  pos[3], pos[3]-vy, pos[0]+vy }
    private val cubics: Array<SkPoint> by lazy {
        val vx = run {
            val d = pos[1] - pos[0]
            val len = kotlin.math.sqrt(d.fX * d.fX + d.fY * d.fY)
            val scale = len / 3f / len
            SkPoint(d.fX * scale, d.fY * scale)
        }
        val vy = run {
            val d = pos[3] - pos[0]
            val len = kotlin.math.sqrt(d.fX * d.fX + d.fY * d.fY)
            val scale = len / 3f / len
            SkPoint(d.fX * scale, d.fY * scale)
        }
        arrayOf(
            pos[0],                   pos[0] + vx,           pos[1] - vx,
            pos[1],                   pos[1] + vy,           pos[2] - vy,
            pos[2],                   pos[2] - vx,           pos[3] + vx,
            pos[3],                   pos[3] - vy,           pos[0] + vy,
        )
    }

    // ─── helpers ──────────────────────────────────────────────────────

    private fun makePaint(useCF: Boolean, useShader: Boolean): SkPaint = SkPaint().apply {
        colorFilter = if (useCF) this@RuntimecolorfilterVerticesAtlasAndPatchGM.colorFilter else null
        shader      = if (useShader) atlas.makeShader(SkSamplingOptions(SkFilterMode.kNearest)) else null
    }

    // ─── onDraw ───────────────────────────────────────────────────────

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Row 0: drawVertices — (no CF + vertex-colors), (CF + vertex-colors), (CF + image-shader)
        drawVerticesRow(c, x = 0f,                  useCF = false, useShader = false)
        drawVerticesRow(c, x = r.width() + 10f,     useCF = true,  useShader = false)
        drawVerticesRow(c, x = 2f * (r.width() + 10f), useCF = true,  useShader = true)

        c.translate(0f, r.height() + 10f)

        // Row 1: drawAtlas — (no CF), (CF)
        drawAtlasRow(c, x = 0f,             useCF = false)
        drawAtlasRow(c, x = r.width() + 10f, useCF = true)

        c.translate(0f, r.height() + 10f)

        // Row 2: drawPatch — (no CF), (CF)
        drawPatchRow(c, x = 0f,             useCF = false)
        drawPatchRow(c, x = r.width() + 10f, useCF = true)
    }

    private fun drawVerticesRow(c: SkCanvas, x: Float, useCF: Boolean, useShader: Boolean) {
        val saved = c.save()
        c.translate(x, 0f)
        val mode = if (useShader) SkBlendMode.kSrc else SkBlendMode.kDst
        c.drawVertices(verts, mode, makePaint(useCF, useShader))
        c.restoreToCount(saved)
    }

    private fun drawAtlasRow(c: SkCanvas, x: Float, useCF: Boolean) {
        val saved = c.save()
        c.translate(x, 0f)
        val paint = makePaint(useCF, useShader = false)
        c.drawAtlas(
            image     = atlas,
            xform     = arrayOf(xform),
            src       = arrayOf(r),
            colors    = intArrayOf(SK_ColorWHITE),
            blendMode = SkBlendMode.kModulate,
            sampling  = SkSamplingOptions(SkFilterMode.kNearest),
            cullRect  = null,
            paint     = paint,
        )
        c.restoreToCount(saved)
    }

    private fun drawPatchRow(c: SkCanvas, x: Float, useCF: Boolean) {
        val saved = c.save()
        c.translate(x, 0f)
        val paint = makePaint(useCF, useShader = true)
        c.drawPatch(
            cubics    = cubics,
            colors    = null,
            texCoords = pos,
            blendMode = SkBlendMode.kModulate,
            paint     = paint,
        )
        c.restoreToCount(saved)
    }

    private companion object {
        // gLumaSrc from gm/runtimecolorfilter.cpp (verbatim)
        val LUMA_SRC_SKSL: String = SkBuiltinColorFilterEffects.LUMA_SRC_SKSL
    }
}
