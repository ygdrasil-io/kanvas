package org.graphiks.kanvas.gpu.renderer.wgsl

const val GradientTileWgsl: String = """
fn tile_clamp(t: f32) -> f32 {
    return clamp(t, 0.0, 1.0);
}
fn tile_repeat(t: f32) -> f32 {
    return t - floor(t);
}
fn tile_mirror(t: f32) -> f32 {
    let i = floor(t);
    let f = t - i;
    let even = (i % 2.0) == 0.0;
    return select(1.0 - f, f, even);
}
fn tile_decal_alpha(t: f32) -> f32 {
    return select(0.0, 1.0, t >= 0.0 && t <= 1.0);
}
"""

const val GradientTileSnippetSourceHash: String = "fragment:gradient_tile:v1"
