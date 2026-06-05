# FOR-403 M60 F16 direct pass-write hook

## Scope

FOR-403 evaluates whether Kanvas can add a diagnostic-only direct
draw/render-pass write hook for the 16 final residual pixels isolated by
FOR-401 in `non-arc-m60-bounded-stroke-cap-join-target-colorspace-blend`.

The requested diagnostic remains opt-in and disabled by default. This ticket
does not change default rendering, thresholds, support policy, promotion
state, or correction behavior.

## Source evidence reused

- Basic Memory source:
  `global/kanvas/findings/for-402-refuse-le-pass-write-probe-m60-f16-tant-que-le-hook-direct-manque`
- FOR-400 artifact:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-coverage-stencil-contribution-map-for400/m60-f16-coverage-stencil-contribution-map-for400.json`
- FOR-400 classification: `predicate-window-zero-contribution`
- FOR-401 artifact:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json`
- FOR-401 classification: `residual-visible-only-at-final-readback`
- FOR-401 current total residual: `62748`
- FOR-401 mismatch pixels: `1615`
- FOR-401 selected residual total: `1560`
- FOR-401 candidate attribution:
  `writtenByM60AaStencilCover=0`, `writtenByOtherPath=0`,
  `readbackOnlyUnknown=16`
- FOR-402 artifact:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-pass-write-probe-for402/m60-f16-pass-write-probe-for402.json`
- FOR-402 classification: `pass-write-probe-inconclusive`
- FOR-402 direct pass-write instrumentation:
  `directPassWriteInstrumentationAvailable=false`

## FOR-403 result

- Linear: `FOR-403`
- Artifact:
  `reports/wgsl-pipeline/scenes/artifacts/m60-f16-direct-pass-write-hook-for403/m60-f16-direct-pass-write-hook-for403.json`
- Classification: `direct-pass-write-hook-inconclusive`
- Taxonomy:
  `m60-aa-stencil-cover-write-observed`,
  `other-draw-write-observed`,
  `post-draw-pre-readback-boundary-required`,
  `direct-pass-write-hook-inconclusive`
- Direct pass-write hook available: `false`
- M60 AA stencil-cover direct writes observed: `0`
- Other draw direct writes observed: `0`
- Pixels requiring post-draw/pre-readback boundary: `16`
- `supportClaim=false`, `promoted=false`, `correctionAppliedByDefault=false`

## Interpretation

`SkWebGpuDevice.flush()` currently encodes pending draws to the intermediate
texture, adds the final present pass, submits one command buffer, and then
reads back the final target. `encodePendingDrawsToIntermediate()` owns the
draw/render-pass submission path, but it does not expose a stable diagnostic
boundary that samples the framebuffer after each relevant draw/pass with a
draw id and pipeline family.

The only available bounded writer-side evidence remains
`SkWebGpuDevice.m60F16CoverageStencilContributionMapSnapshot()`, which is the
FOR-400 radius-1 M60 AA stencil-cover contribution diagnostic. FOR-400 is
therefore retained only as context. It is not converted into proof of
`m60-aa-stencil-cover-write-observed`.

Because a true direct hook would require broader draw/pass instrumentation
across the `SkWebGpuDevice` render-pass families, FOR-403 records a stable
instrumented refusal instead of inventing attribution. Each of the 16 FOR-401
pixels is classified as `post-draw-pre-readback-boundary-required`.

## Non-goals preserved

- No M60 F16 correction was applied.
- M60 F16 remains unsupported: `supportClaim=false`.
- M60 F16 remains unpromoted: `promoted=false`.
- No similarity threshold or scoring rule changed.
- FOR-380 was not reintroduced.
- No default rendering behavior changed.

## Validation commands

- `rtk python3 scripts/validate_for403_m60_f16_direct_pass_write_hook.py`
- `rtk python3 scripts/validate_for402_m60_f16_pass_write_probe.py`
- `rtk git diff --check`
