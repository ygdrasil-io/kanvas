// Phase G-saveLayer-fast -- direct GPU-to-GPU layer composite shader.
//
// Replaces the G-saveLayer scaffolding flush+readback+re-upload round-
// trip with a single fullscreen-quad render pass that samples the
// child device's `intermediateTexture` directly and writes onto the
// parent's `intermediateTexture`. Both textures share the same WebGPU
// adapter / queue (the child device was built via
// `SkWebGpuDevice.makeLayerDevice`, see its kdoc), so no copy or
// readback is needed.
//
// The fragment stage uses `textureLoad` with integer pixel coords
// (1:1 layer-pixel -> device-pixel grid alignment ; the layer device
// is sized to the integer-rounded layer bounds and the dstOrigin is
// an integer). This matches the present-pass identity copy pattern
// in `present_identity.wgsl` -- no filter, no sampler, no UV
// normalisation. It also makes the shader format-agnostic : the
// intermediate format can be `RGBA16Float` (G6.2 default) or
// `RGBA8Unorm` and the load behaves identically.
//
// The layer's pixels are already premul (the layer's draws hit its
// `intermediateTexture` via the same blend hardware as the parent's
// draws), so the fragment output is also premul. Paint alpha + colour
// modulation fold into a single uniform `paintColor` scale that
// multiplies the loaded RGBA. Blend mode is handled via the pipeline's
// blend state -- matches the natively-blendable subset enforced at
// dispatch (kClear / kSrc / kSrcOver / kDstOver).
//
// Scissor-rect tightening (clip-intersected integer dst rect) is set
// on the render pass by the host -- fragments outside the visible
// composite region never reach this shader.
//
// ASCII strict -- WGSL parser truncates on non-ASCII in wgpu4k 0.2.0.

struct Uniforms {
    // dstOrigin : layer's top-left in parent-device pixel coords
    // (integer-valued floats so the fragment stage's int conversion is
    // exact). `.zw` is the layer's (w, h) in source pixels.
    dstOriginSize: vec4f,    // offset  0
    // Per-draw paint scale folded into the loaded colour. Premul
    // (1, 1, 1, 1) when paint is null ; alpha = 128 paint scales by
    // (a, a, a, a) since the layer pixels are premul.
    paintColor:    vec4f,    // offset 16
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var layer_texture: texture_2d<f32>;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    // pos.xy is the destination pixel center (column p -> p + 0.5).
    // Floor to integer dst pixel coords, subtract dstOrigin to get the
    // matching layer-pixel coords. textureLoad with lod = 0 returns the
    // exact texel (no filter), so 1:1 grid alignment is exact.
    let dst_px = vec2i(i32(floor(pos.x)), i32(floor(pos.y)));
    let origin_px = vec2i(i32(uniforms.dstOriginSize.x), i32(uniforms.dstOriginSize.y));
    let layer_px = dst_px - origin_px;

    // Guard rail : if the scissor failed and the fragment maps outside
    // the layer extent, return transparent. The scissor on the render
    // pass should make this branch dead, but defensively returning
    // zero is cheaper than a UB-inducing out-of-bounds load.
    let layer_w = i32(uniforms.dstOriginSize.z);
    let layer_h = i32(uniforms.dstOriginSize.w);
    if (layer_px.x < 0 || layer_px.x >= layer_w ||
        layer_px.y < 0 || layer_px.y >= layer_h) {
        return vec4f(0.0, 0.0, 0.0, 0.0);
    }

    let sampled = textureLoad(layer_texture, layer_px, 0);
    return vec4f(
        sampled.r * uniforms.paintColor.r,
        sampled.g * uniforms.paintColor.g,
        sampled.b * uniforms.paintColor.b,
        sampled.a * uniforms.paintColor.a,
    );
}
