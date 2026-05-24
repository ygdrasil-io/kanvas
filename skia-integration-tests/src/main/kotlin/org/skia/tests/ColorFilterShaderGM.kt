package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColorFilter
import org.skia.foundation.SkColorFilters
import org.skia.foundation.SkConicalGradient
import org.skia.foundation.SkLinearGradient
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkShader
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkColor4f
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/colorfilterimagefilter.cpp::DEF_SIMPLE_GM(colorfiltershader, …, 610, 610)`.
 *
 * Draws a 4-row × 4-column grid of 120 × 120 rects. Each row uses one of
 * four shaders (two linear gradients, an image shader from mandrill_128.png,
 * and a two-point conical gradient). For each shader the first column
 * renders it raw (no colour filter), and the next three columns apply
 * brightness, grayscale, and blue-colorize filters via
 * `shader->makeWithColorFilter(filter)`.
 *
 * [SkShader.makeWithColorFilter] is exercised by the filtered columns ;
 * the first column draws each raw shader directly, matching the upstream
 * null-filter case.
 *
 * C++ original :
 * ```cpp
 * DEF_SIMPLE_GM(colorfiltershader, canvas, 610, 610) {
 *     TArray<sk_sp<SkColorFilter>> filters;
 *     sk_gm_get_colorfilters(&filters);
 *     TArray<sk_sp<SkShader>> shaders;
 *     sk_gm_get_shaders(&shaders);
 *     const SkColor4f colors[] = { SkColors::kRed, SkColors::kBlue };
 *     shaders.push_back(SkShaders::TwoPointConicalGradient(
 *                              {0, 0}, 50, {0, 0}, 150,
 *                              {{colors, {}, SkTileMode::kClamp}, {}}));
 *     SkPaint paint;
 *     SkRect r = SkRect::MakeWH(120, 120);
 *     canvas->translate(20, 20);
 *     for (int y = 0; y < SkToInt(shaders.size()); ++y) {
 *         SkShader* shader = shaders[y].get();
 *         canvas->save();
 *         for (int x = -1; x < filters.size(); ++x) {
 *             sk_sp<SkColorFilter> filter = x >= 0 ? filters[x] : nullptr;
 *             paint.setShader(shader->makeWithColorFilter(filter));
 *             canvas->drawRect(r, paint);
 *             canvas->translate(150, 0);
 *         }
 *         canvas->restore();
 *         canvas->translate(0, 150);
 *     }
 * }
 * ```
 */
public class ColorFilterShaderGM : GM() {

    override fun getName(): String = "colorfiltershader"

    override fun getISize(): SkISize = SkISize.Make(610, 610)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val filters: List<SkColorFilter?> = listOf(
            cfMakeBrightness(0.5f),
            cfMakeGrayscale(),
            cfMakeColorize(SK_ColorBLUE),
        )

        val shaders: MutableList<SkShader> = mutableListOf()
        shMakeLinearGradient0()?.let { shaders += it }
        shMakeLinearGradient1()?.let { shaders += it }
        shMakeImage()?.let { shaders += it }

        // Two-point conical gradient : start=(0,0) r=50, end=(0,0) r=150, kClamp.
        // Mirrors the cpp's `SkShaders::TwoPointConicalGradient(...)` call.
        val conicalColors = intArrayOf(SK_ColorRED, SK_ColorBLUE)
        SkConicalGradient.Make(
            start = SkPoint(0f, 0f), startRadius = 50f,
            end = SkPoint(0f, 0f), endRadius = 150f,
            colors = conicalColors, positions = null,
            tileMode = SkTileMode.kClamp,
        )?.let { shaders += it }

        val paint = SkPaint()
        val r = SkRect.MakeWH(120f, 120f)

        c.translate(20f, 20f)
        for (shader in shaders) {
            c.save()
            // x == -1 : raw shader (null filter → makeWithColorFilter(null) in cpp,
            // which returns the shader unchanged upstream). Here we set the shader
            // directly (no filter wrap needed for the null case).
            paint.shader = shader
            c.drawRect(r, paint)
            c.translate(150f, 0f)

            for (filter in filters) {
                paint.shader = shader.makeWithColorFilter(filter!!)
                c.drawRect(r, paint)
                c.translate(150f, 0f)
            }
            c.restore()
            c.translate(0f, 150f)
        }
    }

    // -- Colour filter helpers --------------------------------------------------

    private fun cfMakeBrightness(brightness: Float): SkColorFilter {
        val matrix = floatArrayOf(
            1f, 0f, 0f, 0f, brightness,
            0f, 1f, 0f, 0f, brightness,
            0f, 0f, 1f, 0f, brightness,
            0f, 0f, 0f, 1f, 0f,
        )
        return SkColorFilters.Matrix(matrix)
    }

    private fun cfMakeGrayscale(): SkColorFilter {
        val matrix = FloatArray(20)
        matrix[0] = 0.2126f; matrix[1] = 0.7152f; matrix[2] = 0.0722f
        matrix[5] = 0.2126f; matrix[6] = 0.7152f; matrix[7] = 0.0722f
        matrix[10] = 0.2126f; matrix[11] = 0.7152f; matrix[12] = 0.0722f
        matrix[18] = 1.0f
        return SkColorFilters.Matrix(matrix)
    }

    private fun cfMakeColorize(color: Int): SkColorFilter =
        SkColorFilters.Blend(color, SkBlendMode.kSrc)

    // -- Shader helpers ---------------------------------------------------------

    /** Red-to-green-to-blue linear gradient (fully opaque stops). */
    private fun shMakeLinearGradient0(): SkShader? {
        val p0 = SkPoint(0f, 0f)
        val p1 = SkPoint(100f, 100f)
        val colors = intArrayOf(
            0xFFFF0000.toInt(), // red
            0xFF00FF00.toInt(), // green
            0xFF0000FF.toInt(), // blue
        )
        return SkLinearGradient.Make(p0, p1, colors, null, SkTileMode.kRepeat)
    }

    /**
     * Red-to-transparent-green-to-blue linear gradient (middle stop has
     * `alpha = 0`). Mirrors cpp's `{0, 1, 0, 0}` SkColor4f.
     */
    private fun shMakeLinearGradient1(): SkShader? {
        val p0 = SkPoint(0f, 0f)
        val p1 = SkPoint(100f, 100f)
        val colors = intArrayOf(
            0xFFFF0000.toInt(), // red (fully opaque)
            0x0000FF00.toInt(), // transparent green
            0xFF0000FF.toInt(), // blue (fully opaque)
        )
        return SkLinearGradient.Make(p0, p1, colors, null, SkTileMode.kRepeat)
    }

    /**
     * Image shader from `images/mandrill_128.png`, tiled with kRepeat.
     * Returns `null` when the resource is absent.
     */
    private fun shMakeImage(): SkShader? {
        val image = ToolUtils.GetResourceAsImage("images/mandrill_128.png") ?: return null
        return image.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat, SkSamplingOptions.Default)
    }
}
