# FOR-406 M60 F16 post-pass reference comparison

Date: 2026-06-05

This analytical report compares the FOR-405 post-pass AA stencil-cover
readback values with current GPU final bytes and CPU/Skia references for the
16 FOR-401 residual coordinates. It does not change default rendering, apply a
correction, promote the scene, or use FOR-400 as direct write proof.

## Result

Global classification: `post-pass-already-diverged`.
Conclusion: `divergence-already-visible-in-post-pass`.

The divergence is already visible in the FOR-405 post-pass samples: all 16
post-pass RGBA8 values match the FOR-401 current GPU final bytes and differ
from the byte-identical CPU/Skia reference values at the same coordinates.

## Sources

| Source | Path |
|---|---|
| FOR-405 post-pass readback | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-post-pass-readback-for405/m60-f16-aa-stencil-cover-post-pass-readback-for405.json` |
| FOR-401 selected residual pixels | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-final-residual-origin-map-for401/m60-f16-final-residual-origin-map-for401.json` |
| FOR-404 runtime-hook context | `reports/wgsl-pipeline/scenes/artifacts/m60-f16-aa-stencil-cover-runtime-hook-for404/m60-f16-aa-stencil-cover-runtime-hook-for404.json` |
| CPU PNG | `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/cpu.png` |
| Skia PNG | `reports/wgsl-pipeline/scenes/artifacts/m60-bounded-stroke-cap-join/skia.png` |
| Finding memory | `global/kanvas/findings/for-405-observe-les-couleurs-post-passe-aa-stencil-cover-m60-f16` |
| Ticket draft memory | `global/kanvas/tickets/drafts/brouillon-ticket-for-406-m60-f16-comparer-post-passe-aa-stencil-cover-aux-references-cpu-skia` |

FOR-400 remains context only and is not used as direct writer proof.

## Summary

| Metric | Value |
|---|---:|
| Selected FOR-401 pixels | `16` |
| Post-pass already diverged | `16` |
| Present/readback diverged only | `0` |
| Reference-match inconclusive | `0` |
| Comparison data missing | `0` |
| Post-pass equals current GPU final | `16` |
| CPU equals Skia | `16` |
| Sum delta post-pass -> Skia | `1560` |
| Sum delta final GPU -> Skia | `1560` |
| FOR-401 selected residual total | `1560` |

## Pixel Comparison

| Coordinate | Post-pass observed | Current GPU final | CPU | Skia | Delta post-pass->CPU | Delta post-pass->Skia | Delta final GPU->Skia | Classification |
|---|---|---|---|---|---:|---:|---:|---|
| (92, 75) | `[181, 191, 230, 255]` | `[181, 191, 230, 255]` | `[133, 150, 214, 255]` | `[133, 150, 214, 255]` | `105` | `105` | `105` | `post-pass-already-diverged` |
| (91, 76) | `[181, 191, 230, 255]` | `[181, 191, 230, 255]` | `[133, 150, 214, 255]` | `[133, 150, 214, 255]` | `105` | `105` | `105` | `post-pass-already-diverged` |
| (90, 77) | `[181, 191, 230, 255]` | `[181, 191, 230, 255]` | `[133, 150, 214, 255]` | `[133, 150, 214, 255]` | `105` | `105` | `105` | `post-pass-already-diverged` |
| (89, 78) | `[181, 191, 230, 255]` | `[181, 191, 230, 255]` | `[133, 150, 214, 255]` | `[133, 150, 214, 255]` | `105` | `105` | `105` | `post-pass-already-diverged` |
| (88, 79) | `[181, 191, 230, 255]` | `[181, 191, 230, 255]` | `[133, 150, 214, 255]` | `[133, 150, 214, 255]` | `105` | `105` | `105` | `post-pass-already-diverged` |
| (87, 80) | `[181, 191, 230, 255]` | `[181, 191, 230, 255]` | `[133, 150, 214, 255]` | `[133, 150, 214, 255]` | `105` | `105` | `105` | `post-pass-already-diverged` |
| (101, 37) | `[0, 138, 76, 255]` | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `[68, 121, 68, 255]` | `93` | `93` | `93` | `post-pass-already-diverged` |
| (102, 37) | `[0, 138, 76, 255]` | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `[68, 121, 68, 255]` | `93` | `93` | `93` | `post-pass-already-diverged` |
| (99, 38) | `[0, 138, 76, 255]` | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `[68, 121, 68, 255]` | `93` | `93` | `93` | `post-pass-already-diverged` |
| (100, 38) | `[0, 138, 76, 255]` | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `[68, 121, 68, 255]` | `93` | `93` | `93` | `post-pass-already-diverged` |
| (101, 38) | `[0, 138, 76, 255]` | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `[68, 121, 68, 255]` | `93` | `93` | `93` | `post-pass-already-diverged` |
| (102, 38) | `[0, 138, 76, 255]` | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `[68, 121, 68, 255]` | `93` | `93` | `93` | `post-pass-already-diverged` |
| (103, 38) | `[0, 138, 76, 255]` | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `[68, 121, 68, 255]` | `93` | `93` | `93` | `post-pass-already-diverged` |
| (104, 38) | `[0, 138, 76, 255]` | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `[68, 121, 68, 255]` | `93` | `93` | `93` | `post-pass-already-diverged` |
| (98, 39) | `[0, 138, 76, 255]` | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `[68, 121, 68, 255]` | `93` | `93` | `93` | `post-pass-already-diverged` |
| (99, 39) | `[0, 138, 76, 255]` | `[0, 138, 76, 255]` | `[68, 121, 68, 255]` | `[68, 121, 68, 255]` | `93` | `93` | `93` | `post-pass-already-diverged` |

## Non-Goals Preserved

| Contract | Value |
|---|---|
| supportClaim=false | `false` |
| promoted=false | `false` |
| correctionAppliedByDefault=false | `false` |
| defaultRenderingChanged=false | `false` |
| FOR-400 direct proof | `False` |

## Validations

- `rtk python3 scripts/validate_for406_m60_f16_post_pass_reference_comparison.py`
- `rtk python3 scripts/validate_for405_m60_f16_aa_stencil_cover_post_pass_readback.py`
- `rtk python3 scripts/validate_for404_m60_f16_aa_stencil_cover_runtime_hook.py`
- `rtk python3 scripts/validate_for403_m60_f16_direct_pass_write_hook.py`
- `rtk ./gradlew --no-daemon pipelineSceneDashboardGate`
- `rtk git diff --check`
