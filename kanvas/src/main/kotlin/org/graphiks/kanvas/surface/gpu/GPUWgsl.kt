package org.graphiks.kanvas.surface.gpu

import org.graphiks.kanvas.geometry.FillType
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilCoverConfig
import org.graphiks.kanvas.gpu.renderer.execution.GPUBackendStencilFillRule

internal val SOLID_RECT_WGSL: String = """
    struct Uniforms {
        color: vec4f,
    };

    @group(0) @binding(0) var<uniform> uniforms: Uniforms;

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main() -> @location(0) vec4f {
        return uniforms.color;
    }
""".trimIndent()

internal val RECT_AA_WGSL: String = """
    struct Uniforms {
        bounds: vec4f,
        color: vec4f,
        antiAlias: u32,
    };

    @group(0) @binding(0) var<uniform> uniforms: Uniforms;

    fn rect_cov(coord: vec2f, bounds: vec4f) -> f32 {
        let pixelMin = coord - vec2f(0.5);
        let pixelMax = coord + vec2f(0.5);
        let overlap = max(min(pixelMax, bounds.zw) - max(pixelMin, bounds.xy), vec2f(0.0));
        return min(overlap.x, 1.0) * min(overlap.y, 1.0);
    }

    fn srgb_to_linear(channel: f32) -> f32 {
        if (channel <= 0.04045) {
            return channel / 12.92;
        }
        return pow((channel + 0.055) / 1.055, 2.4);
    }

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let hardCov = select(0.0, 1.0,
            coord.x >= uniforms.bounds.x && coord.x < uniforms.bounds.z &&
            coord.y >= uniforms.bounds.y && coord.y < uniforms.bounds.w);
        let cov = select(rect_cov(coord.xy, uniforms.bounds), hardCov, uniforms.antiAlias == 0u);
        return vec4f(uniforms.color.rgb * srgb_to_linear(cov), uniforms.color.a * cov);
    }
""".trimIndent()

internal val LINEAR_GRADIENT_WGSL: String = """
    struct Uniforms {
        start: vec2f,
        end: vec2f,
        startColor: vec4f,
        endColor: vec4f,
    };

    @group(0) @binding(0) var<uniform> uniforms: Uniforms;

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let dir = uniforms.end - uniforms.start;
        let lenSq = dot(dir, dir);
        var t = -1.0e30;
        if (lenSq >= 1.0e-12) {
            t = dot(coord.xy - uniforms.start, dir) / lenSq;
        }
        let tClamped = clamp(t, 0.0, 1.0);
        let startSRGB = vec4f(pow(uniforms.startColor.rgb, vec3f(1.0 / 2.2)), uniforms.startColor.a);
        let endSRGB = vec4f(pow(uniforms.endColor.rgb, vec3f(1.0 / 2.2)), uniforms.endColor.a);
        let mixedSRGB = mix(startSRGB, endSRGB, tClamped);
        return vec4f(pow(mixedSRGB.rgb, vec3f(2.2)), mixedSRGB.a);
    }
""".trimIndent()

internal val RRECT_WGSL: String = """
    struct Uniforms {
        bounds: vec4f,
        radii: vec4f,
        color: vec4f,
        antiAlias: u32,
    };

    @group(0) @binding(0) var<uniform> uniforms: Uniforms;

    fn rrect_cov(p: vec2f, bounds: vec4f, rx_in: f32, ry_in: f32) -> f32 {
        let centre = vec2f(0.5 * (bounds.x + bounds.z), 0.5 * (bounds.y + bounds.w));
        let half = vec2f(0.5 * (bounds.z - bounds.x), 0.5 * (bounds.w - bounds.y));
        let rx = max(rx_in, 1e-4);
        let ry = max(ry_in, 1e-4);
        let q_abs = abs(p - centre);
        let q = q_abs - (half - vec2f(rx, ry));
        let inner_rect_sdf = max(q.x, q.y);
        let outer_rect_sdf = max(q_abs.x - half.x, q_abs.y - half.y);
        let qm = max(q, vec2f(0.0, 0.0));
        let n = vec2f(qm.x / rx, qm.y / ry);
        let nl = length(n);
        let nl_safe = max(nl, 1e-6);
        let dir = n / nl_safe;
        let effective_r = length(vec2f(rx * dir.x, ry * dir.y));
        let corner_sdf = (nl - 1.0) * effective_r;
        let in_corner_band = step(0.0, q.x) * step(0.0, q.y);
        let band_sdf = mix(outer_rect_sdf, corner_sdf, in_corner_band);
        let final_sdf = band_sdf;
        return clamp(0.5 - final_sdf, 0.0, 1.0);
    }

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let aaCov = rrect_cov(coord.xy, uniforms.bounds, uniforms.radii.x, uniforms.radii.y);
        let hardCov = step(0.5, aaCov);
        let cov = select(aaCov, hardCov, uniforms.antiAlias == 0u);
        return vec4f(uniforms.color.rgb * cov, uniforms.color.a * cov);
    }
""".trimIndent()

internal val RADIAL_GRADIENT_WGSL: String = """
    struct Uniforms {
        center: vec2f,
        radius: f32,
        startColor: vec4f,
        endColor: vec4f,
    };

    @group(0) @binding(0) var<uniform> uniforms: Uniforms;

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let dir = coord.xy - uniforms.center;
        let dist = length(dir);
        let t = dist / uniforms.radius;
        let tClamped = clamp(t, 0.0, 1.0);
        let startSRGB = vec4f(pow(uniforms.startColor.rgb, vec3f(1.0 / 2.2)), uniforms.startColor.a);
        let endSRGB = vec4f(pow(uniforms.endColor.rgb, vec3f(1.0 / 2.2)), uniforms.endColor.a);
        let mixedSRGB = mix(startSRGB, endSRGB, tClamped);
        return vec4f(pow(mixedSRGB.rgb, vec3f(2.2)), mixedSRGB.a);
    }
""".trimIndent()

internal val LINEAR_GRADIENT_MULTI_WGSL: String = """
    struct Uniforms {
        start: vec2f,
        end: vec2f,
        stopCount: u32,
        stopData: array<vec4f, 512>,
    };

    @group(0) @binding(0) var<uniform> uniforms: Uniforms;

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let dir = uniforms.end - uniforms.start;
        let lenSq = dot(dir, dir);
        var t = -1.0e30;
        if (lenSq >= 1.0e-12) {
            t = dot(coord.xy - uniforms.start, dir) / lenSq;
        }
        let tClamped = clamp(t, 0.0, 1.0);
        var result = uniforms.stopData[2u * uniforms.stopCount - 1u];
        for (var i = 0u; i < uniforms.stopCount - 1u; i++) {
            let p0 = uniforms.stopData[2u * i].x;
            let p1 = uniforms.stopData[2u * (i + 1u)].x;
            if (tClamped >= p0 && tClamped <= p1) {
                let localT = (tClamped - p0) / max(p1 - p0, 1.0e-10);
                let startSRGB = vec4f(pow(uniforms.stopData[2u * i + 1u].rgb, vec3f(1.0 / 2.2)), uniforms.stopData[2u * i + 1u].a);
                let endSRGB = vec4f(pow(uniforms.stopData[2u * (i + 1u) + 1u].rgb, vec3f(1.0 / 2.2)), uniforms.stopData[2u * (i + 1u) + 1u].a);
                let mixedSRGB = mix(startSRGB, endSRGB, localT);
                result = vec4f(pow(mixedSRGB.rgb, vec3f(2.2)), mixedSRGB.a);
                break;
            }
        }
        return result;
    }
""".trimIndent()

internal val RADIAL_GRADIENT_MULTI_WGSL: String = """
    struct Uniforms {
        center: vec2f,
        radius: f32,
        stopCount: u32,
        stopData: array<vec4f, 512>,
    };

    @group(0) @binding(0) var<uniform> uniforms: Uniforms;

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let dir = coord.xy - uniforms.center;
        let dist = length(dir);
        let t = dist / uniforms.radius;
        let tClamped = clamp(t, 0.0, 1.0);
        var result = uniforms.stopData[2u * uniforms.stopCount - 1u];
        for (var i = 0u; i < uniforms.stopCount - 1u; i++) {
            let p0 = uniforms.stopData[2u * i].x;
            let p1 = uniforms.stopData[2u * (i + 1u)].x;
            if (tClamped >= p0 && tClamped <= p1) {
                let localT = (tClamped - p0) / max(p1 - p0, 1.0e-10);
                let startSRGB = vec4f(pow(uniforms.stopData[2u * i + 1u].rgb, vec3f(1.0 / 2.2)), uniforms.stopData[2u * i + 1u].a);
                let endSRGB = vec4f(pow(uniforms.stopData[2u * (i + 1u) + 1u].rgb, vec3f(1.0 / 2.2)), uniforms.stopData[2u * (i + 1u) + 1u].a);
                let mixedSRGB = mix(startSRGB, endSRGB, localT);
                result = vec4f(pow(mixedSRGB.rgb, vec3f(2.2)), mixedSRGB.a);
                break;
            }
        }
        return result;
    }
""".trimIndent()

internal val SWEEP_GRADIENT_WGSL: String = """
    struct Uniforms {
        center: vec2f,
        angles: vec2f,
        startColor: vec4f,
        endColor: vec4f,
    };

    @group(0) @binding(0) var<uniform> uniforms: Uniforms;

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let dir = coord.xy - uniforms.center;
        let angle = atan2(dir.y, dir.x);
        let range = uniforms.angles.y - uniforms.angles.x;
        let t = select((angle - uniforms.angles.x) / range, 0.0, range < 1.0e-10);
        let tClamped = clamp(t, 0.0, 1.0);
        let startSRGB = vec4f(pow(uniforms.startColor.rgb, vec3f(1.0 / 2.2)), uniforms.startColor.a);
        let endSRGB = vec4f(pow(uniforms.endColor.rgb, vec3f(1.0 / 2.2)), uniforms.endColor.a);
        let mixedSRGB = mix(startSRGB, endSRGB, tClamped);
        return vec4f(pow(mixedSRGB.rgb, vec3f(2.2)), mixedSRGB.a);
    }
""".trimIndent()

internal val SWEEP_GRADIENT_MULTI_WGSL: String = """
    struct Uniforms {
        center: vec2f,
        angles: vec2f,
        stopCount: u32,
        stopData: array<vec4f, 512>,
    };

    @group(0) @binding(0) var<uniform> uniforms: Uniforms;

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let dir = coord.xy - uniforms.center;
        let angle = atan2(dir.y, dir.x);
        let range = uniforms.angles.y - uniforms.angles.x;
        let t = select((angle - uniforms.angles.x) / range, 0.0, range < 1.0e-10);
        let tClamped = clamp(t, 0.0, 1.0);
        var result = uniforms.stopData[2u * uniforms.stopCount - 1u];
        for (var i = 0u; i < uniforms.stopCount - 1u; i++) {
            let p0 = uniforms.stopData[2u * i].x;
            let p1 = uniforms.stopData[2u * (i + 1u)].x;
            if (tClamped >= p0 && tClamped <= p1) {
                let localT = (tClamped - p0) / max(p1 - p0, 1.0e-10);
                let startSRGB = vec4f(pow(uniforms.stopData[2u * i + 1u].rgb, vec3f(1.0 / 2.2)), uniforms.stopData[2u * i + 1u].a);
                let endSRGB = vec4f(pow(uniforms.stopData[2u * (i + 1u) + 1u].rgb, vec3f(1.0 / 2.2)), uniforms.stopData[2u * (i + 1u) + 1u].a);
                let mixedSRGB = mix(startSRGB, endSRGB, localT);
                result = vec4f(pow(mixedSRGB.rgb, vec3f(2.2)), mixedSRGB.a);
                break;
            }
        }
        return result;
    }
""".trimIndent()

internal val COPY_WGSL: String = """
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

/**
 * Static horizontal mask-blur pass. Sigma controls only the reflected uniform
 * payload: a fixed loop ignores padded weights after `tapCount`.
 */
internal val MASK_BLUR_HORIZONTAL_WGSL: String = """
    struct MaskBlurUniforms {
        tapCount: u32,
        _pad0: u32,
        targetSize: vec2f,
        _pad1: vec2f,
        _pad2: vec2f,
        weights: array<vec4f, 7>,
    };

    @group(0) @binding(0) var<uniform> u: MaskBlurUniforms;
    @group(1) @binding(1) var inputTex: texture_2d<f32>;
    @group(1) @binding(2) var inputSam: sampler;

    fn sampleMaskDecal(uv: vec2f) -> vec4f {
        if (any(uv < vec2f(0.0)) || any(uv >= vec2f(1.0))) {
            return vec4f(0.0);
        }
        return textureSample(inputTex, inputSam, uv);
    }

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let size = max(u.targetSize, vec2f(1.0, 1.0));
        let uv = coord.xy / size;
        let half = u.tapCount / 2u;
        var result = vec4f(0.0);
        for (var i = 0u; i < 25u; i = i + 1u) {
            if (i >= u.tapCount) {
                break;
            }
            let packedWeights = u.weights[i / 4u];
            let weight = packedWeights[i % 4u];
            let sampleOffset = vec2f(f32(i) - f32(half), 0.0) / size;
            result += weight * sampleMaskDecal(uv + sampleOffset);
        }
        return result;
    }
""".trimIndent()

/**
 * Static vertical mask-blur pass. Its uniform layout exactly matches
 * [MASK_BLUR_HORIZONTAL_WGSL] so pipeline topology is independent of sigma.
 */
internal val MASK_BLUR_VERTICAL_WGSL: String = """
    struct MaskBlurUniforms {
        tapCount: u32,
        _pad0: u32,
        targetSize: vec2f,
        _pad1: vec2f,
        _pad2: vec2f,
        weights: array<vec4f, 7>,
    };

    @group(0) @binding(0) var<uniform> u: MaskBlurUniforms;
    @group(1) @binding(1) var inputTex: texture_2d<f32>;
    @group(1) @binding(2) var inputSam: sampler;

    fn sampleMaskDecal(uv: vec2f) -> vec4f {
        if (any(uv < vec2f(0.0)) || any(uv >= vec2f(1.0))) {
            return vec4f(0.0);
        }
        return textureSample(inputTex, inputSam, uv);
    }

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let size = max(u.targetSize, vec2f(1.0, 1.0));
        let uv = coord.xy / size;
        let half = u.tapCount / 2u;
        var result = vec4f(0.0);
        for (var i = 0u; i < 25u; i = i + 1u) {
            if (i >= u.tapCount) {
                break;
            }
            let packedWeights = u.weights[i / 4u];
            let weight = packedWeights[i % 4u];
            let sampleOffset = vec2f(0.0, f32(i) - f32(half)) / size;
            result += weight * sampleMaskDecal(uv + sampleOffset);
        }
        return result;
    }
""".trimIndent()

internal val MASK_BLUR_STYLE_WGSL: String = """
    struct Uniforms {
        style: u32,
    };

    @group(0) @binding(0) var<uniform> u: Uniforms;
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
        let blurred = textureSample(srcTexture, srcSampler, uv).a;
        let original = textureSample(dstTexture, dstSampler, uv).a;
        var coverage = blurred;
        switch (u.style) {
            case 0u: { coverage = blurred; }
            case 1u: { coverage = max(original, blurred); }
            case 2u: { coverage = blurred * (1.0 - original); }
            default: { coverage = blurred * original; }
        }
        return vec4f(coverage, coverage, coverage, coverage);
    }
""".trimIndent()

internal val MASK_BLUR_SOLID_COMPOSITE_WGSL: String = """
    struct Uniforms {
        deviceBounds: vec4f,
        color: vec4f,
    };

    @group(0) @binding(0) var<uniform> u: Uniforms;
    @group(1) @binding(1) var maskTexture: texture_2d<f32>;
    @group(1) @binding(2) var maskSampler: sampler;

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let localSize = max(u.deviceBounds.zw - u.deviceBounds.xy, vec2f(1.0, 1.0));
        let uv = (coord.xy - u.deviceBounds.xy) / localSize;
        let coverage = textureSample(maskTexture, maskSampler, uv).a;
        return u.color * coverage;
    }
""".trimIndent()

private val DESTINATION_READ_BLEND_FORMULA_WGSL: String = """
    fn unpremul(color: vec4f) -> vec3f {
        if (color.a == 0.0) {
            return vec3f(0.0);
        }
        return color.rgb / color.a;
    }

    fn lum(c: vec3f) -> f32 { return dot(c, vec3f(0.3, 0.59, 0.11)); }
    fn sat(c: vec3f) -> f32 {
        return max(max(c.r, c.g), c.b) - min(min(c.r, c.g), c.b);
    }
    fn colorDodge(cb: vec3f, cs: vec3f) -> vec3f {
        return select(min(vec3f(1.0), cb / (vec3f(1.0) - cs)), vec3f(1.0), cs == vec3f(1.0));
    }
    fn colorBurn(cb: vec3f, cs: vec3f) -> vec3f {
        return select(vec3f(1.0) - min(vec3f(1.0), (vec3f(1.0) - cb) / cs), vec3f(0.0), cs == vec3f(0.0));
    }

    fn hardLight(cb: vec3f, cs: vec3f) -> vec3f {
        let multiply = 2.0 * cs * cb;
        let screen = 1.0 - 2.0 * (1.0 - cs) * (1.0 - cb);
        return select(screen, multiply, cs <= vec3f(0.5));
    }

    fn softLight(cb: vec3f, cs: vec3f) -> vec3f {
        let d = select(
            sqrt(cb),
            ((16.0 * cb - 12.0) * cb + 4.0) * cb,
            cb <= vec3f(0.25),
        );
        let low = cb - (1.0 - 2.0 * cs) * cb * (1.0 - cb);
        let high = cb + (2.0 * cs - 1.0) * (d - cb);
        return select(high, low, cs <= vec3f(0.5));
    }

    fn clipColor(c: vec3f) -> vec3f {
        let l = lum(c);
        let n = min(min(c.r, c.g), c.b);
        let x = max(max(c.r, c.g), c.b);
        var result = c;
        if (n < 0.0) {
            result = vec3f(l) + (result - vec3f(l)) * l / (l - n);
        }
        if (x > 1.0) {
            result = vec3f(l) + (result - vec3f(l)) * (1.0 - l) / (x - l);
        }
        return result;
    }

    fn setLum(c: vec3f, l: f32) -> vec3f {
        return clipColor(c + vec3f(l - lum(c)));
    }

    fn setSat(c: vec3f, s: f32) -> vec3f {
        let n = min(min(c.r, c.g), c.b);
        let x = max(max(c.r, c.g), c.b);
        let range = x - n;
        let scaled = (c - vec3f(n)) * s / max(range, 1.0e-10);
        return select(vec3f(0.0), scaled, range > 0.0);
    }

    fn blendHue(cb: vec3f, cs: vec3f) -> vec3f {
        return setLum(setSat(cs, sat(cb)), lum(cb));
    }

    fn blendSaturation(cb: vec3f, cs: vec3f) -> vec3f {
        return setLum(setSat(cb, sat(cs)), lum(cb));
    }

    fn blendColorMode(cb: vec3f, cs: vec3f) -> vec3f {
        return setLum(cs, lum(cb));
    }

    fn blendLuminosity(cb: vec3f, cs: vec3f) -> vec3f {
        return setLum(cb, lum(cs));
    }

    fn blendColor(src: vec3f, dst: vec3f, blendMode: u32) -> vec3f {
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
            case 7u: { return colorDodge(dst, src); }
            case 8u: { return colorBurn(dst, src); }
            case 9u: { return hardLight(dst, src); }
            case 10u: { return softLight(dst, src); }
            case 11u: { return blendHue(dst, src); }
            case 12u: { return blendSaturation(dst, src); }
            case 13u: { return blendColorMode(dst, src); }
            case 14u: { return blendLuminosity(dst, src); }
            default: { return src; }
        }
    }

    fn blendPremul(src: vec4f, dst: vec4f, blendMode: u32) -> vec4f {
        if (src.a == 0.0) {
            return dst;
        }
        let srcColor = unpremul(src);
        let dstColor = unpremul(dst);
        let blended = blendColor(srcColor, dstColor, blendMode);
        let rgb = src.rgb * (1.0 - dst.a) +
            dst.rgb * (1.0 - src.a) +
            src.a * dst.a * blended;
        return vec4f(rgb, src.a + dst.a * (1.0 - src.a));
    }
""".trimIndent()

internal val BLEND_FORMULA_WGSL: String = """
    struct Uniforms {
        blendMode: u32,
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

    $DESTINATION_READ_BLEND_FORMULA_WGSL

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let srcDims = textureDimensions(srcTexture);
        let uv = vec2f(coord.x / f32(srcDims.x), coord.y / f32(srcDims.y));
        let src = textureSample(srcTexture, srcSampler, uv);
        let dst = textureSample(dstTexture, dstSampler, uv);
        return blendPremul(src, dst, uniforms.blendMode);
    }
""".trimIndent()

internal val CLIP_BLEND_FORMULA_WGSL: String = """
    struct Uniforms {
        blendMode: u32,
    };

    @group(0) @binding(0) var<uniform> uniforms: Uniforms;
    @group(1) @binding(1) var srcTexture: texture_2d<f32>;
    @group(1) @binding(2) var srcSampler: sampler;
    @group(1) @binding(3) var dstTexture: texture_2d<f32>;
    @group(1) @binding(4) var dstSampler: sampler;
    @group(1) @binding(5) var clipTexture: texture_2d<f32>;
    @group(1) @binding(6) var clipSampler: sampler;

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    $DESTINATION_READ_BLEND_FORMULA_WGSL

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let srcDims = textureDimensions(srcTexture);
        let uv = vec2f(coord.x / f32(srcDims.x), coord.y / f32(srcDims.y));
        let clipAlpha = textureSample(clipTexture, clipSampler, uv).a;
        let src = textureSample(srcTexture, srcSampler, uv) * clipAlpha;
        let dst = textureSample(dstTexture, dstSampler, uv);
        return blendPremul(src, dst, uniforms.blendMode);
    }
""".trimIndent()

/** Destination-read blend formula with a device-rect clip applied at final composition. */
internal val SCISSOR_CLIP_BLEND_FORMULA_WGSL: String = """
    struct Uniforms {
        blendMode: u32,
        _pad0: u32,
        _pad1: vec2u,
        clipBounds: vec4f,
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

    $DESTINATION_READ_BLEND_FORMULA_WGSL

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let dims = textureDimensions(srcTexture);
        let uv = coord.xy / vec2f(dims);
        let inside = coord.x >= uniforms.clipBounds.x && coord.x < uniforms.clipBounds.z &&
            coord.y >= uniforms.clipBounds.y && coord.y < uniforms.clipBounds.w;
        let clipAlpha = select(0.0, 1.0, inside);
        let src = textureSample(srcTexture, srcSampler, uv) * clipAlpha;
        let dst = textureSample(dstTexture, dstSampler, uv);
        return blendPremul(src, dst, uniforms.blendMode);
    }
""".trimIndent()

internal val IMAGE_TEXTURE_WGSL: String = """
    struct Uniforms {
        dstRect: vec4f,
        uvScale: vec2f,
        uvOffset: vec2f,
        tintColor: vec4f,
    };

    @group(0) @binding(0) var<uniform> uniforms: Uniforms;
    @group(1) @binding(1) var imageTex: texture_2d<f32>;
    @group(1) @binding(2) var imageSam: sampler;

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let dstSize = max(uniforms.dstRect.zw - uniforms.dstRect.xy, vec2f(1.0, 1.0));
        let uv = vec2f(
            uniforms.uvOffset.x + (coord.x - uniforms.dstRect.x) / dstSize.x * uniforms.uvScale.x,
            uniforms.uvOffset.y + (coord.y - uniforms.dstRect.y) / dstSize.y * uniforms.uvScale.y,
        );
        let color = textureSample(imageTex, imageSam, uv);
        return vec4f(color.rgb * uniforms.tintColor.rgb * uniforms.tintColor.a, color.a * uniforms.tintColor.a);
    }
""".trimIndent()

/** Image-filter source pass: samples outside [dstRect] clamp to the command crop. */
internal val FILTERED_IMAGE_SOURCE_WGSL: String = """
    struct Uniforms {
        dstRect: vec4f,
        uvScale: vec2f,
        uvOffset: vec2f,
        tintColor: vec4f,
    };

    @group(0) @binding(0) var<uniform> uniforms: Uniforms;
    @group(1) @binding(1) var imageTex: texture_2d<f32>;
    @group(1) @binding(2) var imageSam: sampler;

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let dstSize = max(uniforms.dstRect.zw - uniforms.dstRect.xy, vec2f(1.0, 1.0));
        let unclampedUv = uniforms.uvOffset +
            (coord.xy - uniforms.dstRect.xy) / dstSize * uniforms.uvScale;
        let cropEnd = uniforms.uvOffset + uniforms.uvScale;
        let halfTexel = 0.5 / vec2f(textureDimensions(imageTex));
        let cropMin = min(uniforms.uvOffset, cropEnd) + halfTexel;
        let cropMax = max(uniforms.uvOffset, cropEnd) - halfTexel;
        let uv = clamp(unclampedUv, cropMin, cropMax);
        let color = textureSample(imageTex, imageSam, uv);
        return vec4f(color.rgb * uniforms.tintColor.rgb * uniforms.tintColor.a, color.a * uniforms.tintColor.a);
    }
""".trimIndent()

internal val FILTERED_IMAGE_COMPOSITE_WGSL: String = """
    struct Uniforms {
        dstRect: vec4f,
        localSize: vec2f,
        _pad: vec2f,
    };

    @group(0) @binding(0) var<uniform> uniforms: Uniforms;
    @group(1) @binding(1) var imageTex: texture_2d<f32>;
    @group(1) @binding(2) var imageSam: sampler;

    @vertex
    fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
        let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
        let y = f32(idx & 2u) * 2.0 - 1.0;
        return vec4f(x, y, 0.0, 1.0);
    }

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let local = coord.xy - uniforms.dstRect.xy;
        let uv = local / max(uniforms.localSize, vec2f(1.0, 1.0));
        return textureSample(imageTex, imageSam, uv);
    }
""".trimIndent()

/** Static stencil-write module. Target dimensions are supplied as a reflected uniform. */
internal val CLIP_STENCIL_WRITE_WGSL: String = """
struct StencilUniforms {
    targetSize: vec2f,
};

@group(0) @binding(0) var<uniform> uniforms: StencilUniforms;

struct VertexInput {
    @location(0) position: vec2f,
};

@vertex
fn vs_main(in: VertexInput) -> @builtin(position) vec4f {
    let safeTargetSize = max(uniforms.targetSize, vec2f(1.0, 1.0));
    return vec4f(
        in.position.x / safeTargetSize.x * 2.0 - 1.0,
        1.0 - in.position.y / safeTargetSize.y * 2.0,
        0.0,
        1.0,
    );
}

@fragment
fn fs_main() -> @location(0) vec4f {
    return vec4f(0.0, 0.0, 0.0, 0.0);
}
""".trimIndent()

/**
 * Static white-source shader used by the stencil-tested AlphaMask composition.
 * Its single vec4 uniform has a reflected 16-byte layout and is deliberately
 * independent of a clip stack's values, which remain draw data rather than
 * shader identity.
 */
internal val CLIP_MASK_COVER_WGSL: String = """
struct ClipMaskUniforms {
    color: vec4f,
};

@group(0) @binding(0) var<uniform> uniforms: ClipMaskUniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main() -> @location(0) vec4f {
    return uniforms.color;
}
""".trimIndent()

/** Static source-times-mask compositor with two parser-reflected texture pairs. */
internal val CLIP_MASK_COMPOSITE_WGSL: String = """
struct ClipMaskCompositeUniforms {
    _pad: vec4f,
};

@group(0) @binding(0) var<uniform> uniforms: ClipMaskCompositeUniforms;
@group(1) @binding(1) var sourceTexture: texture_2d<f32>;
@group(1) @binding(2) var sourceSampler: sampler;
@group(1) @binding(3) var maskTexture: texture_2d<f32>;
@group(1) @binding(4) var maskSampler: sampler;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
    let dims = textureDimensions(sourceTexture);
    let uv = vec2f(coord.x / f32(dims.x), coord.y / f32(dims.y));
    return textureSample(sourceTexture, sourceSampler, uv) * textureSample(maskTexture, maskSampler, uv).a;
}
""".trimIndent()

/** Converts the compatibility [FillType] to explicit native stencil pipeline state. */
internal fun stencilConfig(fillType: FillType): GPUBackendStencilCoverConfig = when (fillType) {
    FillType.WINDING -> GPUBackendStencilCoverConfig(GPUBackendStencilFillRule.NonZero, inverse = false)
    FillType.INVERSE_WINDING -> GPUBackendStencilCoverConfig(GPUBackendStencilFillRule.NonZero, inverse = true)
    FillType.EVEN_ODD -> GPUBackendStencilCoverConfig(GPUBackendStencilFillRule.EvenOdd, inverse = false)
    FillType.INVERSE_EVEN_ODD -> GPUBackendStencilCoverConfig(GPUBackendStencilFillRule.EvenOdd, inverse = true)
}
