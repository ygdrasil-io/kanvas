package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.effects.SkBuiltinColorFilterEffects
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkColorFilters
import org.skia.math.SkColorGetA
import org.skia.math.SkColorGetB
import org.skia.math.SkColorGetG
import org.skia.math.SkColorGetR
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkLumaColorFilter
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPerlinNoiseShader
import org.skia.foundation.SkSweepGradient
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkImageFilters
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of upstream Skia's `gm/composecolorfilter.cpp` GMs.
 *
 * Upstream ships two `DEF_SIMPLE_GM` :
 *
 *  - **`composeCF`** : draws the same scene twice — once with
 *    `outer.makeComposed(inner)` (no SkSL), once with
 *    `SkRuntimeEffect::MakeForColorFilter` (SkSL). **D2.4.a wires
 *    the SkSL branch** via the registered
 *    [SkBuiltinColorFilterEffects.ComposeChildrenImpl] (the
 *    `outer.eval(inner.eval(c))` runtime effect). Both columns
 *    render now ; the SkSL column should be pixel-iso with the
 *    non-SkSL one.
 *  - **`composeCFIF`** : draws `SkImageFilters::Compose` of a
 *    Shader-image-filter and a ColorFilter-image-filter. **No
 *    `SkRuntimeEffect` involvement** — fully portable.
 *
 * **Iso-fidelity caveat** : the Perlin-noise turbulence used by
 * `composeCFIF` is Skia's `SkPerlinNoiseShader.MakeTurbulence` ;
 * iso-pixel parity depends on the noise generator matching
 * upstream's seed semantics. Likely off-by-some-pixels but
 * visually similar.
 */
public class ComposeColorFilterGM : GM() {

    override fun getName(): String = "composeCF"
    override fun getISize(): SkISize = SkISize.Make(200, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Sweep gradient as the shader.
        val gradColors = intArrayOf(
            SkColorSetARGB(0xFF, 0xFF, 0, 0),         // red
            SkColorSetARGB(0xFF, 0, 0xFF, 0),         // green
            SkColorSetARGB(0xFF, 0, 0, 0xFF),         // blue
            SkColorSetARGB(0xFF, 0xFF, 0, 0),         // red (close the sweep)
        )
        val sweep = SkSweepGradient.Make(
            SkPoint(50f, 50f),
            startAngle = 0f, endAngle = 360f,
            colors = gradColors,
            positions = null,
            tileMode = SkTileMode.kClamp,
        )

        val paint = SkPaint().apply { shader = sweep }

        // Both columns of upstream's `useSkSL` loop : false (top row,
        // direct makeComposed) and true (bottom row, SkSL via
        // SkRuntimeEffect.MakeForColorFilter).
        for (useSkSL in listOf(false, true)) {
            c.save()
            val cf0 = makeTintColorFilter(0xFF300000.toInt(), 0xFFA00000.toInt(), useSkSL)
            val cf1 = makeTintColorFilter(0xFF003000.toInt(), 0xFF00A000.toInt(), useSkSL)

            paint.colorFilter = cf0
            c.drawRect(SkRect.MakeLTRB(0f, 0f, 100f, 100f), paint)
            c.translate(100f, 0f)
            paint.colorFilter = cf1
            c.drawRect(SkRect.MakeLTRB(0f, 0f, 100f, 100f), paint)
            c.restore()

            c.translate(0f, 100f)
        }
    }

    /**
     * Mirrors upstream's `MakeTintColorFilter(lo, hi, useSkSL)`.
     * Composes a [SkLumaColorFilter] (inner) with a tint matrix
     * (outer) that linearly interpolates between `lo` and `hi`
     * based on the input's luminance (stored in α after Luma's
     * pass).
     *
     * When [useSkSL] is `true`, composes via the runtime-effect
     * registered as
     * [SkBuiltinColorFilterEffects.ComposeChildrenImpl]
     * (`outer.eval(inner.eval(c))`) — proves the SkSL path is
     * pixel-iso with the direct `Compose` path.
     */
    private fun makeTintColorFilter(lo: Int, hi: Int, useSkSL: Boolean): SkColorFilter {
        val rLo = SkColorGetR(lo); val gLo = SkColorGetG(lo)
        val bLo = SkColorGetB(lo); val aLo = SkColorGetA(lo)
        val rHi = SkColorGetR(hi); val gHi = SkColorGetG(hi)
        val bHi = SkColorGetB(hi); val aHi = SkColorGetA(hi)
        val tint = floatArrayOf(
            0f, 0f, 0f, (rHi - rLo) / 255f, rLo / 255f,
            0f, 0f, 0f, (gHi - gLo) / 255f, gLo / 255f,
            0f, 0f, 0f, (bHi - bLo) / 255f, bLo / 255f,
            0f, 0f, 0f, (aHi - aLo) / 255f, aLo / 255f,
        )
        val inner = SkLumaColorFilter.Make()
        val outer = SkColorFilters.Matrix(tint)
        if (!useSkSL) {
            // Upstream calls `outer->makeComposed(inner)` ; our
            // Compose factory has the same `Compose(outer, inner)`
            // semantic.
            return SkColorFilters.Compose(outer, inner)
        }
        // SkSL branch — register the 2-child runtime effect, then
        // bind inner / outer as children.
        val effect = SkRuntimeEffect.MakeForColorFilter(
            SkBuiltinColorFilterEffects.COMPOSE_CF_SKSL
        ).effect ?: error("Compose-CF runtime effect failed to compile")
        return effect.makeColorFilter(
            uniforms = null,
            children = arrayOf<SkColorFilter?>(inner, outer),
        ) ?: error("Compose-CF runtime effect failed to instantiate")
    }
}

/**
 * Port of upstream's `gm/composecolorfilter.cpp::composeCFIF`
 * (`DEF_SIMPLE_GM(composeCFIF, canvas, 604, 200)`).
 *
 * This GM exercises `SkImageFilters.Compose` with two paths : (a)
 * a directly-composed filter chain ; (b) an indirectly-composed
 * chain via `SkImageFilters.Compose`. Both should produce
 * identical output. **No `SkRuntimeEffect` involvement** — fully
 * portable today.
 */
public class ComposeCFIFGM : GM() {

    override fun getName(): String = "composeCFIF"
    override fun getISize(): SkISize = SkISize.Make(604, 200)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Tint color filter (non-SkSL branch — same as ComposeCF).
        val cf = makeTintColorFilter(0xFF300000.toInt(), 0xFFA00000.toInt())
        val turbulence = SkPerlinNoiseShader.MakeTurbulence(
            baseFrequencyX = 0.01f, baseFrequencyY = 0.01f,
            numOctaves = 2, seed = 0f,
        )

        val shaderIF = SkImageFilters.Shader(turbulence)
        val directCompose = SkImageFilters.ColorFilter(cf, shaderIF)
        val indirectCompose = SkImageFilters.Compose(
            outer = SkImageFilters.ColorFilter(cf, null),
            inner = shaderIF,
        )

        // Cell 1 — direct shader + color filter on a paint.
        c.save()
        c.clipRect(SkRect.MakeWH(200f, 200f))
        val p1 = SkPaint().apply {
            shader = turbulence
            colorFilter = cf
        }
        c.drawPaint(p1)
        c.restore()
        c.translate(202f, 0f)

        // Cell 2 — directly-composed image filter.
        c.save()
        c.clipRect(SkRect.MakeWH(200f, 200f))
        val p2 = SkPaint().apply { imageFilter = directCompose }
        c.drawPaint(p2)
        c.restore()
        c.translate(202f, 0f)

        // Cell 3 — indirectly-composed image filter (should match cell 2).
        c.save()
        c.clipRect(SkRect.MakeWH(200f, 200f))
        val p3 = SkPaint().apply { imageFilter = indirectCompose }
        c.drawPaint(p3)
        c.restore()
    }

    private fun makeTintColorFilter(lo: Int, hi: Int): SkColorFilter {
        val rLo = SkColorGetR(lo); val gLo = SkColorGetG(lo)
        val bLo = SkColorGetB(lo); val aLo = SkColorGetA(lo)
        val rHi = SkColorGetR(hi); val gHi = SkColorGetG(hi)
        val bHi = SkColorGetB(hi); val aHi = SkColorGetA(hi)
        val tint = floatArrayOf(
            0f, 0f, 0f, (rHi - rLo) / 255f, rLo / 255f,
            0f, 0f, 0f, (gHi - gLo) / 255f, gLo / 255f,
            0f, 0f, 0f, (bHi - bLo) / 255f, bLo / 255f,
            0f, 0f, 0f, (aHi - aLo) / 255f, aLo / 255f,
        )
        return SkColorFilters.Compose(SkColorFilters.Matrix(tint), SkLumaColorFilter.Make())
    }
}
