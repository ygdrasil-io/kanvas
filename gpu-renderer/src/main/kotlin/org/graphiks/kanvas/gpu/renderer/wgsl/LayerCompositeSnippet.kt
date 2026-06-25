package org.graphiks.kanvas.gpu.renderer.wgsl

const val LayerCompositeWgsl: String = """
@group(1) @binding(1) var layer_texture: texture_2d<f32>;
@group(1) @binding(2) var layer_sampler: sampler;

fn layer_composite(uv: vec2<f32>, src_color: vec4<f32>, group_alpha: f32) -> vec4<f32> {
    let sampled = textureSample(layer_texture, layer_sampler, uv);
    // sampled from the offscreen render pass is premultiplied. Children render into
    // the secondary at full opacity; the WHOLE isolated layer is then faded by the
    // saveLayer group alpha here (a premultiplied scale of the sampled layer).
    let layer_color = sampled * group_alpha;
    // src_color (uniform) is packed straight-alpha; premultiply it before blending.
    let src_premul = vec4<f32>(src_color.rgb * src_color.a, src_color.a);
    // srcOver: faded layer over tint-color
    return vec4<f32>(
        layer_color.rgb + src_premul.rgb * (1.0 - layer_color.a),
        layer_color.a + src_premul.a * (1.0 - layer_color.a),
    );
}
"""

const val LayerCompositeSnippetSourceHash: String = "fragment:layer_composite:v1"
const val LayerCompositeEntryPoint: String = "layer_composite"
