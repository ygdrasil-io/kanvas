struct VertexInput {
    @location(0) position: vec2<f32>,
    @location(1) texCoord: vec2<f32>,
}

struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) texCoord: vec2<f32>,
}

struct TextParams {
    atlasScale: vec2<f32>,
    maskGamma: f32,
}

@group(0) @binding(0) var<uniform> textParams: TextParams;
@group(1) @binding(1) var glyphAtlas: texture_2d<f32>;
@group(1) @binding(2) var glyphSampler: sampler;

@vertex
fn vertexMain(in: VertexInput) -> VertexOutput {
    var out: VertexOutput;
    out.position = vec4<f32>(in.position, 0.0, 1.0);
    out.texCoord = in.texCoord;
    return out;
}

@fragment
fn fragmentMain(@location(0) texCoord: vec2<f32>) -> @location(0) vec4<f32> {
    let a8 = textureSample(glyphAtlas, glyphSampler, texCoord).r;
    let alpha = pow(a8, textParams.maskGamma);
    return vec4<f32>(1.0, 1.0, 1.0, alpha);
}
