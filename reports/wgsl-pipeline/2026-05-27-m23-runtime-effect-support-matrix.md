# Runtime Effect Descriptor Support Matrix

Derived evidence. The descriptor registry is the source of truth.

| Stable id | Canonical hash | Kind | Uniforms | Children | Flags | CPU support | GPU support | Descriptor status | Missing reason |
|---|---:|---|---|---|---:|---|---|---|---|
| runtime.linear_gradient_rt | -705551463242925998 | kShader | in_colors0:kFloat4, in_colors1:kFloat4 | - | 36 | supported:kotlin/linear_gradient_rt | unsupported: WGSL implementation id missing | dispatch-only; missing descriptor | Runtime effect descriptor missing for dispatch-only effect: -705551463242925998 |
| runtime.simple_rt | 3617365546103039931 | kShader | gColor:kFloat4 | - | 4 | supported:kotlin/simple_rt | supported:wgsl/runtime_simple_rt | descriptor-backed | none |
| runtime.spiral_rt | -4648126568593740980 | kShader | rad_scale:kFloat, in_center:kFloat2, in_colors0:kFloat4, in_colors1:kFloat4 | - | 4 | supported:kotlin/spiral_rt | unsupported: WGSL implementation id missing | dispatch-only; missing descriptor | Runtime effect descriptor missing for dispatch-only effect: -4648126568593740980 |
