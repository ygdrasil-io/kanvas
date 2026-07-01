package org.graphiks.kanvas.gpu.renderer.filters

import org.graphiks.kanvas.gpu.renderer.commands.NormalizedBlurStyle
import kotlin.math.exp

/** Identity copy WGSL used when sigma is near-zero. */
val BLUR_PASS_COPY_WGSL: String = """
struct Uniforms {
    _pad: vec4f,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(1) @binding(1) var inputTex: texture_2d<f32>;
@group(1) @binding(2) var inputSam: sampler;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
    let dims = textureDimensions(inputTex);
    let uv = vec2f(coord.x / f32(dims.x), coord.y / f32(dims.y));
    return textureSample(inputTex, inputSam, uv);
}
""".trimIndent()

/** Parameters for a separable gaussian blur filter pass. */
data class BlurFilterParams(
    val sigmaX: Float,
    val sigmaY: Float,
    val separable: Boolean = true,
)

/** Result of executing a blur filter pass. */
data class BlurFilterResult(
    val passCount: Int,
    val kernelSize: Int,
    val accepted: Boolean,
)

/** Applies separable gaussian blur via horizontal and vertical passes.
 *  Delegates actual kernel computation to [GPUSeparableBlurPlanner]. */
class GaussianBlurFilter(
    private val maxPassCount: Int = 2,
) {
    /** Executes the blur for the given parameters and returns pass/kernel stats. */
    fun execute(params: BlurFilterParams): BlurFilterResult {
        val planner = GPUSeparableBlurPlanner()
        val plan = planner.plan(
            sigmaX = params.sigmaX,
            sigmaY = params.sigmaY,
            qualityTier = SeparableBlurQualityTier.NORMAL,
        )
        if (plan.passes.isEmpty() || plan.diagnostics.any { it.terminal }) {
            return BlurFilterResult(passCount = 0, kernelSize = 0, accepted = false)
        }
        return BlurFilterResult(
            passCount = plan.passes.size,
            kernelSize = plan.passes.first().kernelTaps,
            accepted = true,
        )
    }

    companion object {
        fun kernelSigmaToTaps(sigma: Float): Int =
            if (sigma < 0.5f) 1 else (sigma * 2f + 1f).toInt()
    }
}

/**
 * Generates a WGSL fragment shader for a separable gaussian blur pass
 * (horizontal or vertical). Kernel weights are baked into the shader source.
 */
fun generateBlurPassWgsl(
    axis: BlurAxis,
    sigma: Float,
    qualityTier: SeparableBlurQualityTier = SeparableBlurQualityTier.NORMAL,
): String {
    val taps = qualityTier.tapCount(sigma)
    if (taps <= 1) return BLUR_PASS_COPY_WGSL

    val effectiveSigma = qualityTier.effectiveSigma(sigma)
    val half = taps / 2
    val weights = FloatArray(taps) { i ->
        val x = (i - half).toFloat()
        exp(-(x * x) / (2f * effectiveSigma * effectiveSigma))
    }
    val sum = weights.sum()
    if (sum > 0f) {
        for (i in weights.indices) weights[i] /= sum
    }

    val pixDir = when (axis) {
        BlurAxis.HORIZONTAL -> "vec2f(1.0 / f32(dims.x), 0.0)"
        BlurAxis.VERTICAL -> "vec2f(0.0, 1.0 / f32(dims.y))"
    }

    val samples = buildString {
        for (i in 0 until taps) {
            val w = weights[i]
            if (kotlin.math.abs(w) < 0.0001f) continue
            val offset = i - half
            if (offset == 0) {
                appendLine("        result += ${w}f * textureSample(inputTex, inputSam, uv);")
            } else {
                val sign = if (offset > 0) "+$offset" else "$offset"
                appendLine("        result += ${w}f * textureSample(inputTex, inputSam, uv $sign * $pixDir);")
            }
        }
    }

    return """
struct Uniforms {
    _pad: vec4f,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(1) @binding(1) var inputTex: texture_2d<f32>;
@group(1) @binding(2) var inputSam: sampler;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
    let dims = textureDimensions(inputTex);
    let uv = vec2f(coord.x / f32(dims.x), coord.y / f32(dims.y));
    var result = vec4f(0.0);
$samples    return result;
}
""".trimIndent()
}

/**
 * Generates a WGSL compositing shader for the given [BlurStyle].
 * Reads src (blurred) and dst (original) textures and applies the style formula.
 */
fun generateBlurStyleWgsl(style: NormalizedBlurStyle): String {
    val body = when (style) {
        NormalizedBlurStyle.NORMAL -> """    return textureSample(srcTexture, srcSampler, uv);"""
        NormalizedBlurStyle.SOLID -> """    let s = textureSample(srcTexture, srcSampler, uv);
    let d = textureSample(dstTexture, dstSampler, uv);
    return vec4f(max(s.rgb, d.rgb), max(s.a, d.a));"""
        NormalizedBlurStyle.OUTER -> """    let s = textureSample(srcTexture, srcSampler, uv);
    let d = textureSample(dstTexture, dstSampler, uv);
    return s * (1.0 - d.a);"""
        NormalizedBlurStyle.INNER -> """    let s = textureSample(srcTexture, srcSampler, uv);
    let d = textureSample(dstTexture, dstSampler, uv);
    return s * d.a;"""
    }
    return """
struct Uniforms {
    _pad: vec4f,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(1) @binding(1) var srcTexture: texture_2d<f32>;
@group(1) @binding(2) var srcSampler: sampler;
@group(1) @binding(3) var dstTexture: texture_2d<f32>;
@group(1) @binding(4) var dstSampler: sampler;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
    let dims = textureDimensions(srcTexture);
    let uv = vec2f(coord.x / f32(dims.x), coord.y / f32(dims.y));
$body
}
""".trimIndent()
}
