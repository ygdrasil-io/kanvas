# KAN-034 Runtime Effects V2 Evidence Bundle

Status: `pass`
Status counts: total=8; descriptor-backed=6; gpu-backed=4; cpu-only=2; expected-unsupported=2; dependency-gated=0; support-claims-with-artifacts=4; missing-artifacts=0.

KAN-034 aggregates the Runtime Effects V2 support/refusal evidence without adding a new rendering capability. WGSL remains the implementation target; SkSL appears only as a refused compatibility surface.

## Rows

| Stable id | Kind | Descriptor | Support state | Fallback | Support claim |
|---|---|---|---|---|---|
| `policy.arbitrary_sksl_input` | `policy` | `policy-only` | `expected-unsupported` | `runtime-effect.arbitrary-sksl-unsupported` | `no` |
| `policy.unregistered_wgsl_descriptor` | `policy` | `policy-only` | `expected-unsupported` | `runtime-effect.wgsl-descriptor-missing` | `no` |
| `runtime.color_filter_luma_to_alpha` | `kColorFilter` | `descriptor-backed` | `gpu-backed` | `none` | `yes` |
| `runtime.invert_blender` | `kBlender` | `descriptor-backed` | `cpu-only` | `runtime-effect.blender-dst-read-unsupported` | `no` |
| `runtime.linear_gradient_rt` | `kShader` | `descriptor-backed` | `gpu-backed` | `none` | `yes` |
| `runtime.simple_rt` | `kShader` | `descriptor-backed` | `gpu-backed` | `none` | `yes` |
| `runtime.spiral_rt` | `kShader` | `descriptor-backed` | `gpu-backed` | `none` | `yes` |
| `runtime.unsharp_rt` | `kShader` | `descriptor-backed` | `cpu-only` | `runtime-effect.wgsl-descriptor-missing` | `no` |

## Stable Refusals

- `runtime-effect.arbitrary-sksl-unsupported`
- `runtime-effect.wgsl-descriptor-missing`
- `runtime-effect.blender-dst-read-unsupported`
- `runtime-effect.child-binding-unsupported`
- `runtime-effect.child-wgsl-layout-missing`
- `runtime-effect.child-resource-axis-unsupported`
- `runtime-effect.color-filter-wgsl-missing`
- `runtime-effect.color-filter-cpu-only`
- `color-filter.color-space-unsupported`
- `blend.shader-layer-required`
- `runtime-effect.blender-wgsl-descriptor-missing`
- `runtime-effect.preview-uniform-out-of-range`
- `runtime-effect.preview-effect-not-registered`

## Source Reports

- `reports/wgsl-pipeline/runtime-effects-v2/support-matrix.json`
- `reports/wgsl-pipeline/runtime-effects-v2/support-matrix.md`
- `reports/wgsl-pipeline/runtime-effects-layout-v2/runtime-effects-layout-v2.json`
- `reports/wgsl-pipeline/runtime-effects-layout-v2/runtime-effects-layout-v2.md`
- `reports/wgsl-pipeline/runtime-shader-effects-v2/runtime-shader-effects-v2-promotion.json`
- `reports/wgsl-pipeline/runtime-shader-effects-v2/runtime-shader-effects-v2-promotion.md`
- `reports/wgsl-pipeline/runtime-child-shader-effect-lane/runtime-child-shader-effect-lane.json`
- `reports/wgsl-pipeline/runtime-child-shader-effect-lane/runtime-child-shader-effect-lane.md`
- `reports/wgsl-pipeline/runtime-color-filter-wgsl/runtime-color-filter-wgsl.json`
- `reports/wgsl-pipeline/runtime-color-filter-wgsl/runtime-color-filter-wgsl.md`
- `reports/wgsl-pipeline/runtime-blender-boundary/runtime-blender-boundary.json`
- `reports/wgsl-pipeline/runtime-blender-boundary/runtime-blender-boundary.md`
- `reports/wgsl-pipeline/runtime-effect-uniform-preview/runtime-effect-uniform-preview.json`
- `reports/wgsl-pipeline/runtime-effect-uniform-preview/runtime-effect-uniform-preview.md`

## Non-Claims

- No dynamic SkSL compilation.
- No SkSL IR or VM.
- No broad runtime-effect support beyond registered descriptors.
- No arbitrary user WGSL input.
- No runtime color-filter, blender, image-filter helper, child shader, or live editor broad claim.
- No global similarity threshold change.
- No GPU child shader support claim.
- No arbitrary runtime-effect DAG support.
- No child shader texture/resource binding allocation claim.
- No uniform values in PipelineKey.
- No broad ColorFilter support.
- No arbitrary runtime ColorFilter WGSL support.
- No runtime ColorFilter uniforms, child ColorFilters, LUTs, or color-space wrappers.
- No shader-input runtime ColorFilter route beyond solid-color direct rect.
- No global threshold or color policy change.
- No support for all blend modes.
- No GPU runtime blender support.
- No implicit destination read.
- No CPU readback fallback.
- No hidden layer compatibility path.
- No live SkSL editor.
- No live controls for unregistered effects.
- No new WGSL generated per uniform value.
- No Kadre native window requirement for headless validation.
- No new rendering capability is introduced by this bundle.
- No Kadre native CI requirement.
