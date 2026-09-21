package org.graphiks.kanvas.gpu.plan

/** Shared, handle-free authority for the existing rounded premultiplied blend schedules. */
public object BlendFormulaProgramV1 {
    public const val REVISION_I32: Int = 1

    /** Facts carried by the selected W5 formula, before any backend chooses a lowering. */
    public fun finalCompositionFacts(
        modeLabel: String,
        readsPriorDevice: Boolean,
        writesParentDevice: Boolean,
    ): FinalBlendCompositionFactsV1? = selectedReturns[modeLabel]?.let { selected ->
        FinalBlendCompositionFactsV1(readsPriorDevice, selected.affectsTransparentBlack, writesParentDevice)
    }
    internal fun colorOperations(modeLabel: String, src: List<ColorOperationGraphV1.Scalar>,
        dst: List<ColorOperationGraphV1.Scalar>): ColorOperationGraphV1 {
        val source = requireNotNull(selectedBlendFunctionWgsl(modeLabel))
        return requireNotNull(BlendFormulaOperationGraphV1.read(source)).colorOperations("kanvasBlendPremul",src,dst)
    }
    /** Source and final blend may coexist in one shader; only helper identifiers differ. */
    public fun selectedAtlasSourceWgsl(modeLabel: String): String? =
        selectedBlendFunctionWgsl(modeLabel, "w5e_atlas_blend")?.replace("kanvas", "w5eAtlas")
    public fun selectedBlendFunctionWgsl(
        modeLabel: String,
        functionName: String = "kanvasBlendPremul",
    ): String? {
        val selectedReturn = selectedReturns[modeLabel] ?: return null
        return buildString {
            if (modeLabel in advancedModeLabels) appendLine(advancedHelpersWgsl)
            appendLine("fn $functionName(src: vec4f, dst: vec4f) -> vec4f {")
            appendLine("    ${selectedReturn.wgsl}")
            appendLine("}")
        }.trim()
    }

    public val advancedHelpersWgsl: String = """
        fn kanvasUnpremul(color: vec4f) -> vec3f {
            if (color.a == 0.0) { return vec3f(0.0); }
            if (color.a == 1.0) { return color.rgb; }
            return color.rgb / color.a;
        }

        fn kanvasLum(c: vec3f) -> f32 { return dot(c, vec3f(0.3, 0.59, 0.11)); }
        fn kanvasSat(c: vec3f) -> f32 {
            return max(max(c.r, c.g), c.b) - min(min(c.r, c.g), c.b);
        }
        fn kanvasColorDodgeChannel(cb: f32, cs: f32) -> f32 {
            if (cb == 0.0) { return 0.0; }
            if (cs == 1.0) { return 1.0; }
            return min(1.0, cb / (1.0 - cs));
        }
        fn kanvasColorDodge(cb: vec3f, cs: vec3f) -> vec3f {
            return vec3f(kanvasColorDodgeChannel(cb.r, cs.r),
                kanvasColorDodgeChannel(cb.g, cs.g), kanvasColorDodgeChannel(cb.b, cs.b));
        }
        fn kanvasColorBurnChannel(cb: f32, cs: f32) -> f32 {
            if (cb == 1.0) { return 1.0; }
            if (cs == 0.0) { return 0.0; }
            return 1.0 - min(1.0, (1.0 - cb) / cs);
        }
        fn kanvasColorBurn(cb: vec3f, cs: vec3f) -> vec3f {
            return vec3f(kanvasColorBurnChannel(cb.r, cs.r),
                kanvasColorBurnChannel(cb.g, cs.g), kanvasColorBurnChannel(cb.b, cs.b));
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
            if (dst.a == 0.0 && dst.r == 0.0 && dst.g == 0.0 && dst.b == 0.0) { return src; }
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

    private data class SelectedReturn(val wgsl: String, val affectsTransparentBlack: Boolean)

    private val selectedReturns = mapOf(
        "clear" to SelectedReturn("return vec4f(0.0);", true),
        "src_over" to SelectedReturn("return src + dst * (1.0 - src.a);", false),
        "src" to SelectedReturn("return src;", true),
        "dst" to SelectedReturn("return dst;", false),
        "dst_over" to SelectedReturn("return dst + src * (1.0 - dst.a);", false),
        "src_in" to SelectedReturn("return src * dst.a;", true),
        "dst_in" to SelectedReturn("return dst * src.a;", true),
        "src_out" to SelectedReturn("return src * (1.0 - dst.a);", true),
        "dst_out" to SelectedReturn("return dst * (1.0 - src.a);", false),
        "src_atop" to SelectedReturn("return src * dst.a + dst * (1.0 - src.a);", false),
        "dst_atop" to SelectedReturn("return dst * src.a + src * (1.0 - dst.a);", true),
        "xor" to SelectedReturn("return src * (1.0 - dst.a) + dst * (1.0 - src.a);", false),
        "plus" to SelectedReturn("return min(vec4f(1.0), src + dst);", false),
        "modulate" to SelectedReturn("return src * dst;", true),
        "multiply" to SelectedReturn("return kanvasBlendAdvancedPremul(src, dst, 0u);", false),
        "screen" to SelectedReturn("return kanvasBlendAdvancedPremul(src, dst, 1u);", false),
        "overlay" to SelectedReturn("return kanvasBlendAdvancedPremul(src, dst, 2u);", false),
        "darken" to SelectedReturn("return kanvasBlendAdvancedPremul(src, dst, 3u);", false),
        "lighten" to SelectedReturn("return kanvasBlendAdvancedPremul(src, dst, 4u);", false),
        "color_dodge" to SelectedReturn("return kanvasBlendAdvancedPremul(src, dst, 7u);", false),
        "color_burn" to SelectedReturn("return kanvasBlendAdvancedPremul(src, dst, 8u);", false),
        "hard_light" to SelectedReturn("return kanvasBlendAdvancedPremul(src, dst, 9u);", false),
        "soft_light" to SelectedReturn("return kanvasBlendAdvancedPremul(src, dst, 10u);", false),
        "difference" to SelectedReturn("return kanvasBlendAdvancedPremul(src, dst, 5u);", false),
        "exclusion" to SelectedReturn("return kanvasBlendAdvancedPremul(src, dst, 6u);", false),
        "hue" to SelectedReturn("return kanvasBlendAdvancedPremul(src, dst, 11u);", false),
        "saturation" to SelectedReturn("return kanvasBlendAdvancedPremul(src, dst, 12u);", false),
        "color" to SelectedReturn("return kanvasBlendAdvancedPremul(src, dst, 13u);", false),
        "luminosity" to SelectedReturn("return kanvasBlendAdvancedPremul(src, dst, 14u);", false),
    )
}
