# Runtime Effects V2 Support Matrix

Derived evidence. `SkRuntimeEffectDescriptorRegistry` is the source of truth.
WGSL is the GPU target; SkSL is only a compatibility/refusal surface.

Status counts: total=7; descriptor-backed=5; CPU-only=1; GPU-backed=4; dependency-gated=0; expected-unsupported=2.

| Stable id | Kind | Descriptor status | Support state | CPU implementation | WGSL implementation | Fallback reason | PM note |
|---|---|---|---|---|---|---|---|
| policy.arbitrary_sksl_input | policy | policy-only | expected-unsupported | - | - | runtime-effect.arbitrary-sksl-unsupported | Kanvas does not dynamically compile SkSL; arbitrary runtime-effect input remains a stable refusal. |
| policy.unregistered_wgsl_descriptor | policy | policy-only | expected-unsupported | - | - | runtime-effect.wgsl-descriptor-missing | Runtime effects without a registered WGSL descriptor remain visible expected-unsupported rows. |
| runtime.color_filter_luma_to_alpha | kColorFilter | descriptor-backed | gpu-backed | kotlin/color_filter_luma_to_alpha | wgsl/runtime_color_filter_luma_to_alpha | none | This row is a registered Kotlin/CPU and parser-validated WGSL implementation; it is not broad runtime-effect or dynamic SkSL support. |
| runtime.linear_gradient_rt | kShader | descriptor-backed | gpu-backed | kotlin/linear_gradient_rt | wgsl/runtime_linear_gradient_rt | none | This row is a registered Kotlin/CPU and parser-validated WGSL implementation; it is not broad runtime-effect or dynamic SkSL support. |
| runtime.simple_rt | kShader | descriptor-backed | gpu-backed | kotlin/simple_rt | wgsl/runtime_simple_rt | none | This row is a registered Kotlin/CPU and parser-validated WGSL implementation; it is not broad runtime-effect or dynamic SkSL support. |
| runtime.spiral_rt | kShader | descriptor-backed | gpu-backed | kotlin/spiral_rt | wgsl/runtime_spiral_rt | none | This row is a registered Kotlin/CPU and parser-validated WGSL implementation; it is not broad runtime-effect or dynamic SkSL support. |
| runtime.unsharp_rt | kShader | descriptor-backed | cpu-only | kotlin/unsharp_rt | - | runtime-effect.wgsl-descriptor-missing | This row has registered Kotlin/CPU behavior but no parser-validated WGSL implementation; GPU remains refused with a stable missing-descriptor reason. |

## Non-Claims
- No dynamic SkSL compilation.
- No SkSL IR or VM.
- No broad runtime-effect support beyond registered descriptors.
- No support for arbitrary user WGSL input.
