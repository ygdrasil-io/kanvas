# Runtime Effects Layout V2

Derived evidence. `SkRuntimeEffectDescriptorRegistry` and WGSL lowered reflection are the sources.
Status counts: total=3; layout-matched=3; layout-mismatched=0.

Mismatch diagnostic: `runtime-effect.layout-reflection-mismatch`; missing reflection diagnostic: `wgsl.reflection.uniform-member-missing`.
No uniform values enter runtime-effect pipeline cache keys.

| Stable id | WGSL implementation | Status | Uniform block bytes | Diagnostics |
|---|---|---|---:|---|
| runtime.linear_gradient_rt | wgsl/runtime_linear_gradient_rt | layout-matched | 32 | none |
| runtime.simple_rt | wgsl/runtime_simple_rt | layout-matched | 16 | none |
| runtime.spiral_rt | wgsl/runtime_spiral_rt | layout-matched | 48 | none |

## Uniforms

### runtime.linear_gradient_rt

| Name | Descriptor type | Descriptor offset | Descriptor size | WGSL offset | WGSL size | WGSL alignment | Status | Diagnostic |
|---|---|---:|---:|---:|---:|---:|---|---|
| in_colors0 | kFloat4 | 0 | 16 | 0 | 16 | 16 | matched | none |
| in_colors1 | kFloat4 | 16 | 16 | 16 | 16 | 16 | matched | none |

### runtime.simple_rt

| Name | Descriptor type | Descriptor offset | Descriptor size | WGSL offset | WGSL size | WGSL alignment | Status | Diagnostic |
|---|---|---:|---:|---:|---:|---:|---|---|
| gColor | kFloat4 | 0 | 16 | 0 | 16 | 16 | matched | none |

### runtime.spiral_rt

| Name | Descriptor type | Descriptor offset | Descriptor size | WGSL offset | WGSL size | WGSL alignment | Status | Diagnostic |
|---|---|---:|---:|---:|---:|---:|---|---|
| rad_scale | kFloat | 0 | 4 | 0 | 4 | 4 | matched | none |
| in_center | kFloat2 | 8 | 8 | 8 | 8 | 8 | matched | none |
| in_colors0 | kFloat4 | 16 | 16 | 16 | 16 | 16 | matched | none |
| in_colors1 | kFloat4 | 32 | 16 | 32 | 16 | 16 | matched | none |
