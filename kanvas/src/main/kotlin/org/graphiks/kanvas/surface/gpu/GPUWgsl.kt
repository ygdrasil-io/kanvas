package org.graphiks.kanvas.surface.gpu

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
        let cov = select(rect_cov(coord.xy, uniforms.bounds), 1.0, uniforms.antiAlias == 0u);
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
        let cov = select(rrect_cov(coord.xy, uniforms.bounds, uniforms.radii.x, uniforms.radii.y), 1.0, uniforms.antiAlias == 0u);
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

    fn blendMultiply(src: vec4f, dst: vec4f) -> vec4f {
        return vec4f(src.rgb * dst.rgb + dst.rgb * (1.0 - src.a) + src.rgb * (1.0 - dst.a), src.a + dst.a * (1.0 - src.a));
    }

    fn blendScreen(src: vec4f, dst: vec4f) -> vec4f {
        return vec4f(src.rgb + dst.rgb - src.rgb * dst.rgb, src.a + dst.a * (1.0 - src.a));
    }

    fn blendOverlay(src: vec4f, dst: vec4f) -> vec4f {
        let mul = 2.0 * src.rgb * dst.rgb;
        let scrn = 1.0 - 2.0 * (1.0 - src.rgb) * (1.0 - dst.rgb);
        let cond = step(dst.rgb, vec3f(0.5));
        return vec4f(mix(scrn, mul, cond), src.a + dst.a * (1.0 - src.a));
    }

    fn blendDarken(src: vec4f, dst: vec4f) -> vec4f {
        return vec4f(min(src.rgb, dst.rgb), src.a + dst.a * (1.0 - src.a));
    }

    fn blendLighten(src: vec4f, dst: vec4f) -> vec4f {
        return vec4f(max(src.rgb, dst.rgb), src.a + dst.a * (1.0 - src.a));
    }

    fn blendDifference(src: vec4f, dst: vec4f) -> vec4f {
        return vec4f(abs(dst.rgb - src.rgb), src.a + dst.a * (1.0 - src.a));
    }

    fn blendExclusion(src: vec4f, dst: vec4f) -> vec4f {
        return vec4f(src.rgb + dst.rgb - 2.0 * src.rgb * dst.rgb, src.a + dst.a * (1.0 - src.a));
    }

    @fragment
    fn fs_main(@builtin(position) coord: vec4f) -> @location(0) vec4f {
        let srcDims = textureDimensions(srcTexture);
        let uv = vec2f(coord.x / f32(srcDims.x), coord.y / f32(srcDims.y));
        let src = textureSample(srcTexture, srcSampler, uv);
        let dst = textureSample(dstTexture, dstSampler, uv);
        switch uniforms.blendMode {
            case 0u: { return blendMultiply(src, dst); }
            case 1u: { return blendScreen(src, dst); }
            case 2u: { return blendOverlay(src, dst); }
            case 3u: { return blendDarken(src, dst); }
            case 4u: { return blendLighten(src, dst); }
            case 5u: { return blendDifference(src, dst); }
            case 6u: { return blendExclusion(src, dst); }
            default: { return src; }
        }
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

internal fun stencilWriteWgsl(width: Int, height: Int): String = """
struct VertexInput {
    @location(0) position: vec2f,
};

@vertex
fn vs_main(in: VertexInput) -> @builtin(position) vec4f {
    let hw = f32($width) / 2.0;
    let hh = f32($height) / 2.0;
    return vec4f(in.position.x / hw - 1.0, 1.0 - in.position.y / hh, 0.0, 1.0);
}

@fragment
fn fs_main() -> @location(0) vec4f {
    return vec4f(0.0, 0.0, 0.0, 0.0);
}
""".trimIndent()
