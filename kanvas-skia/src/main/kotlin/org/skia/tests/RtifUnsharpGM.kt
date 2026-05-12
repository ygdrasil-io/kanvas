package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.effects.runtime.SkRuntimeEffect
import org.skia.effects.runtime.SkRuntimeEffectBuilder
import org.skia.effects.runtime.effects.SkBuiltinShaderEffectsRtifImageFilters
import org.skia.foundation.SkImageFilter
import org.skia.foundation.SkImageFilters
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.math.SkISize
import org.skia.math.SkRect
import org.skia.tools.ToolUtils

/**
 * Port of Skia's `gm/runtimeimagefilter.cpp::rtif_unsharp` (512 × 256).
 *
 * Draws `mandrill_256.png` twice — once raw at (0, 0), once at
 * (256, 0) through a runtime-shader image filter that implements an
 * unsharp-mask using the filter DAG :
 *
 *  - `content` : the raw layer source (filter input = null).
 *  - `blurred` : the layer source through `SkImageFilters::Blur(1, 1)`.
 *  - SkSL : `c + (c - b) * 4`.
 *
 * **Asset** : uses `images/mandrill_256.png` (already on master).
 *
 * **Runtime-effect impl** :
 * [SkBuiltinShaderEffectsRtifImageFilters.RtifUnsharpImpl] auto-
 * registers under the upstream SkSL source.
 *
 * C++ original — see `gm/runtimeimagefilter.cpp:81-111`.
 */
public class RtifUnsharpGM : GM() {

    init {
        // Force class-load so the effect is registered before any
        // MakeForShader call.
        SkBuiltinShaderEffectsRtifImageFilters
    }

    override fun getName(): String = "rtif_unsharp"
    override fun getISize(): SkISize = SkISize.Make(512, 256)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val effect = SkRuntimeEffect.MakeForShader(
            SkBuiltinShaderEffectsRtifImageFilters.RTIF_UNSHARP_SKSL,
        ).effect ?: error("rtif_unsharp effect failed to compile")
        val builder = SkRuntimeEffectBuilder(effect)

        val image = ToolUtils.GetResourceAsImage("images/mandrill_256.png")
            ?: error("Missing test resource images/mandrill_256.png")

        // Children : `content` = null (= source), `blurred` =
        // SkImageFilters.Blur(1, 1, null).
        val blurredSrc = SkImageFilters.Blur(1f, 1f, input = null)
        val childNames = arrayOf("content", "blurred")
        val childNodes = arrayOf<SkImageFilter?>(null, blurredSrc)
        val sharpened = SkImageFilters.RuntimeShader(builder, childNames, childNodes)

        // Raw image at (0, 0), then sharpened image at (256, 0) via
        // saveLayer{paint{imageFilter}} containing the source image.
        c.drawImage(image, 0f, 0f, SkSamplingOptions.Default, null)
        c.translate(256f, 0f)

        val paint = SkPaint().apply { imageFilter = sharpened }
        c.saveLayer(SkRect.MakeLTRB(0f, 0f, 256f, 256f), paint)
        c.drawImage(image, 0f, 0f, SkSamplingOptions.Default, null)
        c.restore()
    }
}
