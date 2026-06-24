package org.graphiks.kanvas.gpu.renderer.wgsl

const val LayerCompositeWgsl: String = """
@group(1) @binding(1) var layer_texture: texture_2d<f32>;
@group(1) @binding(2) var layer_sampler: sampler;

fn layer_composite(uv: vec2<f32>, src_color: vec4<f32>) -> vec4<f32> {
    let layer_color = textureSample(layer_texture, layer_sampler, uv);
    // srcOver blend
    return vec4<f32>(
        layer_color.rgb * layer_color.a + src_color.rgb * (1.0 - layer_color.a),
        layer_color.a + src_color.a * (1.0 - layer_color.a),
    );
}
"""

const val LayerCompositeSnippetSourceHash: String = "fragment:layer_composite:v1"
const val LayerCompositeEntryPoint: String = "layer_composite"
