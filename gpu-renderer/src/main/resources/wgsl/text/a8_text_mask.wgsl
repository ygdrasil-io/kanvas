struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) color: vec4<f32>,
}

struct TextParams {
    atlasScale: vec2<f32>,
    maskGamma: f32,
}

@group(2) @binding(0) var glyphAtlas: texture_2d<f32>;
@group(2) @binding(1) var glyphSampler: sampler;
@group(2) @binding(2) var<uniform> textParams: TextParams;

@vertex
fn vertexMain(@builtin(vertex_index) vertexIndex: u32) -> VertexOutput {
    let uv = vec2<f32>(
        f32((vertexIndex << 1u) & 2u),
        f32(vertexIndex & 2u),
    );
    var out: VertexOutput;
    out.position = vec4<f32>(uv * 2.0 - 1.0, 0.0, 1.0);
    out.color = vec4<f32>(1.0, 1.0, 1.0, 1.0);
    return out;
}

@fragment
fn fragmentMain(in: VertexOutput) -> @location(0) vec4<f32> {
    let atlasUV = vec2<f32>(0.5, 0.5) / textParams.atlasScale;
    let a8 = textureSample(glyphAtlas, glyphSampler, atlasUV).r;
    let alpha = pow(a8, textParams.maskGamma);
    return vec4<f32>(in.color.rgb, alpha);
}
