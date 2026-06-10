# Runtime Shader Effects V2 Promotion

Derived evidence. Runtime Effect support matrix V2, layout V2, and scene artifacts are the sources.
Status counts: total=3; supported=3; fallback-none=3; layout-matched=3; below-threshold=0; missing-artifacts=0.

| Stable id | Scene | CPU route | GPU route | Similarity | Layout | Fallback |
|---|---|---|---|---:|---|---|
| runtime.linear_gradient_rt | runtime-effect-linear-gradient | cpu.runtime-effect.descriptor.linear_gradient_rt | webgpu.runtime-effect.descriptor.linear_gradient_rt | 100.00% | layout-matched | none |
| runtime.simple_rt | runtime-effect-simple | cpu.runtime-effect.descriptor.simple_rt | webgpu.runtime-effect.descriptor.simple_rt | 100.00% | layout-matched | none |
| runtime.spiral_rt | runtime-effect-spiral | cpu.runtime-effect.descriptor.spiral_rt | webgpu.runtime-effect.descriptor.spiral_rt | 100.00% | layout-matched | none |

## Evidence

### runtime.linear_gradient_rt

- WGSL implementation: `wgsl/runtime_linear_gradient_rt`
- CPU implementation: `kotlin/linear_gradient_rt`
- Threshold: `99.95`; CPU similarity: `100.00`; GPU similarity: `100.00`
- Route CPU: `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-linear-gradient/route-cpu.json`
- Route WebGPU: `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-linear-gradient/route-gpu.json`
- Stats: `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-linear-gradient/stats.json`

### runtime.simple_rt

- WGSL implementation: `wgsl/runtime_simple_rt`
- CPU implementation: `kotlin/simple_rt`
- Threshold: `99.95`; CPU similarity: `100.00`; GPU similarity: `100.00`
- Route CPU: `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/route-cpu.json`
- Route WebGPU: `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/route-gpu.json`
- Stats: `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/stats.json`

### runtime.spiral_rt

- WGSL implementation: `wgsl/runtime_spiral_rt`
- CPU implementation: `kotlin/spiral_rt`
- Threshold: `99.95`; CPU similarity: `100.00`; GPU similarity: `100.00`
- Route CPU: `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-spiral/route-cpu.json`
- Route WebGPU: `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-spiral/route-gpu.json`
- Stats: `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-spiral/stats.json`

## Non-Claims
- No dynamic SkSL compilation.
- No SkSL IR or VM.
- No arbitrary user WGSL input.
- No broad runtime-effect support beyond selected registered descriptors.
- No runtime color-filter, blender, image-filter helper, child shader, or live editor broad claim.
- No global similarity threshold change.
