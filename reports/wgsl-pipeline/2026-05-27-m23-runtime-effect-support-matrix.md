# Runtime Effect Descriptor Support Matrix

Derived evidence. The descriptor registry is the source of truth.

Status counts: total=3; descriptor-backed=3; dispatch-only/missing-descriptor=0; CPU-only=1; GPU-backed=2.

| Stable id | Canonical hash | Kind | Uniforms | Children | Flags | CPU support | GPU support | Descriptor status | Missing reason |
|---|---:|---|---|---|---:|---|---|---|---|
| runtime.linear_gradient_rt | -705551463242925998 | kShader | in_colors0:kFloat4, in_colors1:kFloat4 | - | 36 | supported:kotlin/linear_gradient_rt | supported:wgsl/runtime_linear_gradient_rt | descriptor-backed | none |
| runtime.simple_rt | 3617365546103039931 | kShader | gColor:kFloat4 | - | 4 | supported:kotlin/simple_rt | supported:wgsl/runtime_simple_rt | descriptor-backed | none |
| runtime.spiral_rt | -4648126568593740980 | kShader | rad_scale:kFloat, in_center:kFloat2, in_colors0:kFloat4, in_colors1:kFloat4 | - | 4 | supported:kotlin/spiral_rt | unsupported: WGSL implementation id not promoted: wgsl/runtime_spiral_rt | descriptor-backed | none |
