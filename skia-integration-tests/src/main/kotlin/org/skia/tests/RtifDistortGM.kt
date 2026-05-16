package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectBuilder
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsRtifImageFilters
import org.skia.math.SK_ColorBLACK
import org.skia.foundation.SkFont
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.effects.runtime.SkRuntimeImageFilters
import org.skia.foundation.SkPaint
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.tools.SkRandom
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/runtimeimagefilter.cpp::rtif_distort` (500 × 750).
 *
 * Six 250×250 panels, each rendering 25 random-position random-size
 * coloured strings ("The quick brown fox jumped over the lazy dog.")
 * through a `saveLayer` whose paint carries a runtime-shader image
 * filter that warps the layer's pixels by a sine of the y-coordinate :
 * `coord.x += sin(coord.y / 3) * 4`.
 *
 * Each panel applies a different CTM transform before drawing :
 *  1. Identity.
 *  2. Scale 0.5.
 *  3. Rotate 45° around (125, 125).
 *  4. Scale 0.5 × rotate 45°.
 *  5. Skew (-0.5, 0).
 *  6. Perspective matrix with `(persp0, persp1) = (0.0015, -0.0015)`.
 *
 * **Asset** : uses [ToolUtils.DefaultPortableFont] (Liberation Sans
 * via [org.skia.foundation.LiberationFontMgr]) ; no external image is
 * required.
 *
 * **Runtime-effect impl** :
 * [SkBuiltinShaderEffectsRtifImageFilters.RtifDistortImpl] auto-
 * registers under the upstream SkSL source.
 *
 * C++ original — see `gm/runtimeimagefilter.cpp:44-79`.
 */
public class RtifDistortGM : GM() {

    init {
        setBGColor(SK_ColorBLACK)
        // Force class-load so the effect is registered before any
        // MakeForShader call ; auto-registration via init {} also
        // covers this but the touch makes intent explicit.
        SkBuiltinShaderEffectsRtifImageFilters
    }

    override fun getName(): String = "rtif_distort"
    override fun getISize(): SkISize = SkISize.Make(500, 750)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val clip = SkRect.MakeWH(250f, 250f)
        val filterPaint = SkPaint().apply { imageFilter = makeFilter() }

        // Panel 1 — identity.
        drawLayer(c, 0f, 0f, SkMatrix.Identity, clip, filterPaint)
        // Panel 2 — scale 0.5.
        drawLayer(c, 250f, 0f, SkMatrix.MakeScale(0.5f, 0.5f), clip, filterPaint)
        // Panel 3 — rotate 45 about (125, 125).
        drawLayer(c, 0f, 250f, SkMatrix.MakeRotate(45f, 125f, 125f), clip, filterPaint)
        // Panel 4 — scale(0.5) * rotate(45 around 125,125).
        drawLayer(
            c, 250f, 250f,
            SkMatrix.MakeScale(0.5f, 0.5f) * SkMatrix.MakeRotate(45f, 125f, 125f),
            clip, filterPaint,
        )
        // Panel 5 — skew(-0.5, 0).
        drawLayer(c, 0f, 500f, SkMatrix.MakeSkew(-0.5f, 0f), clip, filterPaint)
        // Panel 6 — perspective {persp0=0.0015, persp1=-0.0015}.
        val p = SkMatrix.MakePerspective(0.0015f, -0.0015f)
        drawLayer(c, 250f, 500f, p, clip, filterPaint)
    }

    private fun drawLayer(
        c: SkCanvas, tx: Float, ty: Float, m: SkMatrix,
        clip: SkRect, filterPaint: SkPaint,
    ) {
        c.save()
        c.translate(tx, ty)
        c.clipRect(clip)
        c.concat(m)
        c.saveLayer(null, filterPaint)

        val str = "The quick brown fox jumped over the lazy dog."
        val rand = SkRandom()
        val font = ToolUtils.DefaultPortableFont()
        repeat(25) {
            val x = rand.nextULessThan(500)
            val y = rand.nextULessThan(500)
            val paint = SkPaint().apply {
                color = ToolUtils.colorTo565(rand.nextBits(24) or 0xFF000000.toInt())
            }
            font.size = rand.nextRangeScalar(0f, 300f)
            c.drawString(str, x.toFloat(), y.toFloat(), font, paint)
        }
        c.restore()
        c.restore()
    }

    private fun makeFilter(): SkImageFilter {
        val effect = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsRtifImageFilters.RTIF_DISTORT_SKSL,
        ).effect ?: error("rtif_distort effect failed to compile")
        val builder = SkRuntimeEffectBuilder(effect)
        // Upstream passes childShaderName="" because the SkSL declares
        // a single child. Our API requires the explicit name ; the
        // SkSL declares `uniform shader child`.
        return SkRuntimeImageFilters.RuntimeShader(
            builder,
            sampleRadius = 4f,
            childShaderName = "child",
            input = null,
        )
    }
}
