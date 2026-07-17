package org.graphiks.kanvas.gpu.renderer.pipelines

/**
 * Handle-free WGSL program authority for canonical premultiplied blend formulas.
 *
 * It deliberately accepts stable labels rather than semantic planner types so native execution can
 * consume a prepared formula identity without depending on the materials domain package.
 */
object GPUBlendFormulaProgramLibrary {
    fun selectedFullCoverageFunctionWgsl(
        modeLabel: String,
        formulaId: String,
        functionName: String = "kanvasBlendPremul",
    ): String? {
        val expectedFormulaId = if (modeLabel == "plus") "plus_exact@v1" else "$modeLabel@v1"
        if (formulaId != expectedFormulaId) return null
        return selectedBlendFunctionWgsl(modeLabel, functionName)
    }

    fun selectedBlendFunctionWgsl(
        modeLabel: String,
        functionName: String = "kanvasBlendPremul",
    ): String? {
        val selectedReturn = selectedReturns[modeLabel] ?: return null
        return buildString {
            if (modeLabel in advancedModeLabels) appendLine(advancedHelpersWgsl)
            appendLine("fn $functionName(src: vec4f, dst: vec4f) -> vec4f {")
            appendLine("    $selectedReturn")
            appendLine("}")
        }.trim()
    }

    val advancedHelpersWgsl: String = """
        fn kanvasUnpremul(color: vec4f) -> vec3f {
            if (color.a == 0.0) { return vec3f(0.0); }
            return color.rgb / color.a;
        }

        fn kanvasLum(c: vec3f) -> f32 { return dot(c, vec3f(0.3, 0.59, 0.11)); }
        fn kanvasSat(c: vec3f) -> f32 {
            return max(max(c.r, c.g), c.b) - min(min(c.r, c.g), c.b);
        }
        fn kanvasColorDodge(cb: vec3f, cs: vec3f) -> vec3f {
            let dodged = select(
                min(vec3f(1.0), cb / (vec3f(1.0) - cs)),
                vec3f(1.0),
                cs == vec3f(1.0),
            );
            return select(dodged, vec3f(0.0), cb == vec3f(0.0));
        }
        fn kanvasColorBurn(cb: vec3f, cs: vec3f) -> vec3f {
            let burned = select(
                vec3f(1.0) - min(vec3f(1.0), (vec3f(1.0) - cb) / cs),
                vec3f(0.0),
                cs == vec3f(0.0),
            );
            return select(burned, vec3f(1.0), cb == vec3f(1.0));
        }
        fn kanvasHardLight(cb: vec3f, cs: vec3f) -> vec3f {
            let multiply = 2.0 * cs * cb;
            let screen = 1.0 - 2.0 * (1.0 - cs) * (1.0 - cb);
            return select(screen, multiply, cs <= vec3f(0.5));
        }
        fn kanvasSoftLight(cb: vec3f, cs: vec3f) -> vec3f {
            let d = select(
                sqrt(cb),
                ((16.0 * cb - 12.0) * cb + 4.0) * cb,
                cb <= vec3f(0.25),
            );
            let low = cb - (1.0 - 2.0 * cs) * cb * (1.0 - cb);
            let high = cb + (2.0 * cs - 1.0) * (d - cb);
            return select(high, low, cs <= vec3f(0.5));
        }
        fn kanvasClipColor(c: vec3f) -> vec3f {
            let l = kanvasLum(c);
            let n = min(min(c.r, c.g), c.b);
            let x = max(max(c.r, c.g), c.b);
            var result = c;
            if (n < 0.0 && l != n) {
                result = vec3f(l) + (result - vec3f(l)) * l / (l - n);
            }
            if (x > 1.0 && x != l) {
                result = vec3f(l) + (result - vec3f(l)) * (1.0 - l) / (x - l);
            }
            return result;
        }
        fn kanvasSetLum(c: vec3f, l: f32) -> vec3f {
            return kanvasClipColor(c + vec3f(l - kanvasLum(c)));
        }
        fn kanvasSetSat(c: vec3f, s: f32) -> vec3f {
            let n = min(min(c.r, c.g), c.b);
            let x = max(max(c.r, c.g), c.b);
            let range = x - n;
            let scaled = (c - vec3f(n)) * s / max(range, 1.0e-10);
            return select(vec3f(0.0), scaled, range > 0.0);
        }
        fn kanvasBlendHue(cb: vec3f, cs: vec3f) -> vec3f {
            return kanvasSetLum(kanvasSetSat(cs, kanvasSat(cb)), kanvasLum(cb));
        }
        fn kanvasBlendSaturation(cb: vec3f, cs: vec3f) -> vec3f {
            return kanvasSetLum(kanvasSetSat(cb, kanvasSat(cs)), kanvasLum(cb));
        }
        fn kanvasBlendColorMode(cb: vec3f, cs: vec3f) -> vec3f {
            return kanvasSetLum(cs, kanvasLum(cb));
        }
        fn kanvasBlendLuminosity(cb: vec3f, cs: vec3f) -> vec3f {
            return kanvasSetLum(cb, kanvasLum(cs));
        }
        fn kanvasBlendAdvancedColor(src: vec3f, dst: vec3f, blendMode: u32) -> vec3f {
            switch blendMode {
                case 0u: { return src * dst; }
                case 1u: { return src + dst - src * dst; }
                case 2u: {
                    let multiply = 2.0 * src * dst;
                    let screen = 1.0 - 2.0 * (1.0 - src) * (1.0 - dst);
                    return select(screen, multiply, dst <= vec3f(0.5));
                }
                case 3u: { return min(src, dst); }
                case 4u: { return max(src, dst); }
                case 5u: { return abs(dst - src); }
                case 6u: { return src + dst - 2.0 * src * dst; }
                case 7u: { return kanvasColorDodge(dst, src); }
                case 8u: { return kanvasColorBurn(dst, src); }
                case 9u: { return kanvasHardLight(dst, src); }
                case 10u: { return kanvasSoftLight(dst, src); }
                case 11u: { return kanvasBlendHue(dst, src); }
                case 12u: { return kanvasBlendSaturation(dst, src); }
                case 13u: { return kanvasBlendColorMode(dst, src); }
                case 14u: { return kanvasBlendLuminosity(dst, src); }
                default: { return src; }
            }
        }
        fn kanvasBlendAdvancedPremul(src: vec4f, dst: vec4f, blendMode: u32) -> vec4f {
            if (src.a == 0.0) { return dst; }
            let srcColor = kanvasUnpremul(src);
            let dstColor = kanvasUnpremul(dst);
            let blended = kanvasBlendAdvancedColor(srcColor, dstColor, blendMode);
            let rgb = src.rgb * (1.0 - dst.a) +
                dst.rgb * (1.0 - src.a) +
                src.a * dst.a * blended;
            return vec4f(rgb, src.a + dst.a * (1.0 - src.a));
        }
    """.trimIndent()

    private val advancedModeLabels = setOf(
        "multiply", "screen", "overlay", "darken", "lighten", "color_dodge", "color_burn",
        "hard_light", "soft_light", "difference", "exclusion", "hue", "saturation", "color",
        "luminosity",
    )

    private val selectedReturns = mapOf(
        "clear" to "return vec4f(0.0);",
        "src_over" to "return src + dst * (1.0 - src.a);",
        "src" to "return src;",
        "dst" to "return dst;",
        "dst_over" to "return dst + src * (1.0 - dst.a);",
        "src_in" to "return src * dst.a;",
        "dst_in" to "return dst * src.a;",
        "src_out" to "return src * (1.0 - dst.a);",
        "dst_out" to "return dst * (1.0 - src.a);",
        "src_atop" to "return src * dst.a + dst * (1.0 - src.a);",
        "dst_atop" to "return dst * src.a + src * (1.0 - dst.a);",
        "xor" to "return src * (1.0 - dst.a) + dst * (1.0 - src.a);",
        "plus" to "return min(vec4f(1.0), src + dst);",
        "modulate" to "return src * dst;",
        "multiply" to "return kanvasBlendAdvancedPremul(src, dst, 0u);",
        "screen" to "return kanvasBlendAdvancedPremul(src, dst, 1u);",
        "overlay" to "return kanvasBlendAdvancedPremul(src, dst, 2u);",
        "darken" to "return kanvasBlendAdvancedPremul(src, dst, 3u);",
        "lighten" to "return kanvasBlendAdvancedPremul(src, dst, 4u);",
        "color_dodge" to "return kanvasBlendAdvancedPremul(src, dst, 7u);",
        "color_burn" to "return kanvasBlendAdvancedPremul(src, dst, 8u);",
        "hard_light" to "return kanvasBlendAdvancedPremul(src, dst, 9u);",
        "soft_light" to "return kanvasBlendAdvancedPremul(src, dst, 10u);",
        "difference" to "return kanvasBlendAdvancedPremul(src, dst, 5u);",
        "exclusion" to "return kanvasBlendAdvancedPremul(src, dst, 6u);",
        "hue" to "return kanvasBlendAdvancedPremul(src, dst, 11u);",
        "saturation" to "return kanvasBlendAdvancedPremul(src, dst, 12u);",
        "color" to "return kanvasBlendAdvancedPremul(src, dst, 13u);",
        "luminosity" to "return kanvasBlendAdvancedPremul(src, dst, 14u);",
    )
}
