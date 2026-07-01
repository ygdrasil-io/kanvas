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
        let half = vec2f(0.5 * (bounds.z - bounds.x), 0.5 * (bounds.w - bounds.y));
        let centre = vec2f(0.5 * (bounds.x + bounds.z), 0.5 * (bounds.y + bounds.w));
        let d = abs(coord - centre) - half;
        let sdf = max(d.x, d.y);
        return clamp(0.5 - sdf, 0.0, 1.0);
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
        return vec4f(uniforms.color.rgb * cov, uniforms.color.a * cov);
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
        return mix(uniforms.startColor, uniforms.endColor, tClamped);
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
        return mix(uniforms.startColor, uniforms.endColor, tClamped);
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
        return mix(uniforms.startColor, uniforms.endColor, tClamped);
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
