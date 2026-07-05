# Coverage Compute Shader for Stroke Anti-Aliasing

2026-07-05

## Problem

Stencil-cover pipeline produces binary coverage (ON/OFF per sample). Even with MSAA 4x and distance-field fragment shader, anti-aliasing is limited to ~4 coverage levels at triangle edges. Skia reference uses 256-level coverage, producing smooth anti-aliased edges.

## Design

Replace stencil-cover for simple strokes (2-point lines, BUTT caps) with a compute shader that writes per-pixel coverage (0-255) into an R8Unorm texture, then a fragment shader samples the coverage as alpha with SRC_OVER blending.

**Architecture:**

```
Stroke segments → Compute Shader → Coverage Texture (R8) → Fragment Shader (alpha = coverage) → Framebuffer (SRC_OVER)
       ↑ uniform buffer              ↑ imageStore                    ↑ textureSample
```

**Scope (POC):**
- 2-point line strokes with BUTT caps only
- No dash, no complex joins, no multi-segment paths
- Single coverage texture per draw (no atlas)
- Compute shader uses 8×8 workgroups

## Components

### 1. COVERAGE_STROKE_WGSL (compute shader)

```
@group(0) @binding(0) var<uniform> params: StrokeParams;  // p0, p1, halfWidth, aaWidth
@group(0) @binding(1) var<storage, read_write> coverage: texture_storage_2d<r8unorm, write>;

@compute @workgroup_size(8, 8)
fn main(@builtin(global_invocation_id) gid: vec3u) {
    let p = vec2f(gid.xy);
    let v = params.p1 - params.p0;
    let w = p - params.p0;
    let t = clamp(dot(w, v) / max(dot(v, v), 0.001), 0.0, 1.0);
    let closest = params.p0 + t * v;
    let dist = length(p - closest);
    let inner = params.halfWidth;
    let outer = inner + params.aaWidth;
    let alpha = 1.0 - smoothstep(inner, outer, dist);
    textureStore(coverage, vec2u(gid.xy), vec4f(alpha, 0.0, 0.0, 0.0));
}
```

### 2. COVERAGE_FILL_WGSL (fragment shader)

```
@group(0) @binding(0) var<uniform> color: vec4f;
@group(0) @binding(1) var covTex: texture_2d<f32>;
@group(0) @binding(2) var covSampler: sampler;

@fragment
fn fs_main(@builtin(position) pos: vec4f) -> @location(0) vec4f {
    let uv = pos.xy / vec2f(textureDimensions(covTex, 0));
    let coverage = textureSample(covTex, covSampler, uv).r;
    return vec4f(color.rgb * coverage, coverage);
}
```

### 3. dispatchCoverageStroke (new function in GPUDispatchPath.kt)

```kotlin
fun dispatchCoverageStroke(cmd, strokeVertices, ...) {
    // 1. Create coverage texture (R8, bounds of stroke)
    val covTex = createCoverageTexture(bounds)
    
    // 2. Compute pass: rasterize stroke into coverage
    recordComputePass(
        wgsl = COVERAGE_STROKE_WGSL,
        uniforms = StrokeParams(p0, p1, halfWidth, aaWidth),
        coverageTexture = covTex,
        workgroups = (bounds.width/8, bounds.height/8)
    )
    
    // 3. Render pass: fill with coverage as alpha
    drawFullscreenPass(
        wgsl = COVERAGE_FILL_WGSL,
        texture = covTex,
        color = cmd.material,
        scissor = bounds
    )
}
```

### 4. Pipeline additions to GPUBackendRuntimeWgpu.kt

- `createComputePipeline(wgsl)` → compiles compute shader, creates bind group layout with storage texture
- `recordComputePass(pipeline, bindGroup, workgroups)` → begins compute pass, dispatches workgroups
- `recordCoverageFillPass(...)` → render pass that samples coverage texture + blends SRC_OVER

## Files Changed

| File | Change |
|------|--------|
| `GPUWgsl.kt` | + `COVERAGE_STROKE_WGSL`, + `COVERAGE_FILL_WGSL` |
| `GPUDispatchPath.kt` | + `dispatchCoverageStroke()`, branch for `cmd.stroke && flat.size == 2` |
| `GPUBackendRuntimeWgpu.kt` | + compute pipeline creation, + `recordComputePass`, + `recordCoverageFillPass` |
| `GPUBackendRuntimeContracts.kt` | + `dispatchCompute(draws, coverageTexture)` |

## Out of Scope

- Coverage atlas (single texture per draw, no reuse)
- Multi-segment strokes, joins, dash patterns
- Complex paths (fills, non-stroke geometry)
- MSAA integration (coverage replaces MSAA for strokes)

## Validation

- `renderCtmp` with sampleCount=1 → green stroke with smooth edges
- Similarity score improvement vs reference
- No regression on non-stroke GMs (stencil-cover unchanged)
