// G3.3a polygon shader -- triangle list, solid premul color.
//
// Vertex stage : each vertex is a device-pixel coord (x, y). Convert
// to NDC : [0, viewport.x] -> [-1, 1] on X ; [0, viewport.y] -> [1, -1]
// on Y (WebGPU NDC has Y+ pointing up, while device pixels are
// Y-down). Each draw maps a triangle-list of post-CTM vertices that
// SkWebGpuDevice.drawPath has already transformed.
//
// Fragment stage : solid color from the uniform, premultiplied for
// the SrcOver pipeline (same convention as solid_color.wgsl).
//
// No coverage / AA in this slice -- analytical edge coverage for
// generic polygons is G3.3b. Existing AA-rect path stays on the
// solid_color.wgsl pipeline (drawRect / drawPaint / drawFillRect).
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    color:    vec4f,   // offset  0
    viewport: vec4f,   // offset 16 : (width, height, 0, 0) in device pixels
};

@binding(0) @group(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@location(0) pos: vec2f) -> @builtin(position) vec4f {
    let ndc_x =  pos.x / uniforms.viewport.x * 2.0 - 1.0;
    let ndc_y = -(pos.y / uniforms.viewport.y * 2.0 - 1.0);
    return vec4f(ndc_x, ndc_y, 0.0, 1.0);
}

@fragment
fn fs_main() -> @location(0) vec4f {
    let c = uniforms.color;
    return vec4f(c.rgb * c.a, c.a);
}
