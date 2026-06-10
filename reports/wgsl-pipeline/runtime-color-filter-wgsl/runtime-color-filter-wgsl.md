# Runtime ColorFilter WGSL

Ticket: `KAN-031`

Status counts: selected=1; gpu-backed=1; layout-matched=1; below-threshold=0; non-selected-visible=5.

## Selected Support

| Stable id | Kind | Support | WGSL | Layout | CPU similarity | WebGPU similarity |
|---|---|---|---|---|---:|---:|
| runtime.color_filter_luma_to_alpha | kColorFilter | gpu-backed | wgsl/runtime_color_filter_luma_to_alpha | layout-matched | 100.000000 | 100.000000 |

## Route

- Stage order: `solid-color shader -> runtime color filter -> fixed-function kSrcOver blend -> store`
- Color-space policy: `srgb-unmanaged-runtime-color-filter-oracle`
- Artifacts root: `reports/wgsl-pipeline/scenes/artifacts/kan-031-runtime-color-filter-luma-to-alpha`

## Non-Selected ColorFilters

| Stable id | State | Reason | PM note |
|---|---|---|---|
| runtime.color_filter_noop | not-promoted | runtime-effect.color-filter-cpu-only | CPU behavior exists, but this ticket promotes only LumaToAlpha WGSL. |
| runtime.color_filter_tonemap | not-promoted | runtime-effect.color-filter-cpu-only | Ternary/Ifs/EarlyReturn tone-map variants stay CPU-only until separately scoped. |
| runtime.color_filter_g_channel_splat | dependency-gated | color-filter.color-space-unsupported | AlternateLuma depends on working color-space behavior outside this bounded slice. |
| runtime.color_filter_compose_children | not-promoted | runtime-effect.color-filter-cpu-only | ColorFilter child bindings are not promoted by this direct-rect WGSL route. |
| policy.unregistered_runtime_color_filter_wgsl | expected-unsupported | runtime-effect.color-filter-wgsl-missing | Runtime ColorFilters without a registered descriptor and parser-reflected WGSL stay refused. |

## Non-Claims

- No broad ColorFilter support.
- No dynamic SkSL compilation.
- No SkSL IR or VM.
- No arbitrary runtime ColorFilter WGSL support.
- No runtime ColorFilter uniforms, child ColorFilters, LUTs, or color-space wrappers.
- No shader-input runtime ColorFilter route beyond solid-color direct rect.
- No global threshold or color policy change.
