# D52-5 DrawMiniBitmapRect dashboard promotion

Ticket source: `global/kanvas/tickets/drafts/brouillon-ticket-d52-5-promouvoir-draw-mini-bitmap-rect-dans-dashboard-apres-preuve-locale`
Source evidence: `reports/wgsl-pipeline/2026-06-06-d52-4-drawminibitmaprect-draw-image-rect-provenance-boundary.md`
Generated dashboard row: `reports/wgsl-pipeline/scenes/generated/results.json`

## Decision

`skia-gm-drawminibitmaprect` is promoted into the generated scene dashboard as
`d52-drawminibitmaprect`.

The promotion is based only on D52 row-specific artifacts. It does not inherit
`m66-bitmap-rect-nearest-skia`, because that row covers `DrawBitmapRectGM`,
not the small-source rotated grid exercised by `DrawMiniBitmapRectGM`.

## Evidence gate

| Check | Value |
|---|---:|
| WebGPU similarity before D52-4 | `94.9305%` |
| WebGPU similarity after D52-4 | `99.9864%` |
| WebGPU promotion threshold | `99.95%` |
| WebGPU fallback after D52-4 | `none` |
| Support claim | `true` |
| Global threshold changed | `false` |
| M66 evidence inherited | `false` |

The D52-4 boundary fix preserved `drawImageRect` source/image intersection at
the `SkCanvas` boundary. User-created `SkBitmapShader(kClamp)` behavior remains
covered separately and is not reclassified as `drawImageRect`.

## Score impact

The dashboard score impact is one additional passing generated inventory row:

| Metric | Before D52-5 | After D52-5 | Delta |
|---|---:|---:|---:|
| Dashboard rows | `93` | `94` | `+1` |
| Passing rows | `70` | `71` | `+1` |
| Expected unsupported rows | `23` | `23` | `0` |
| Pass ratio | `75.27%` | `75.53%` | `+0.26 pts` |

## Non-goals

- No render-path change.
- No WGSL change.
- No `PipelineKey` taxonomy change.
- No fallback-policy change.
- No global threshold or tolerance loosening.
- No promotion of adjacent GM rows.
- No M66 inheritance.

## Validation

```bash
rtk python3 scripts/validate_d52_drawminibitmaprect_dashboard_promotion.py
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk python3 -m json.tool reports/wgsl-pipeline/scenes/generated/results.json
rtk env PYTHONPYCACHEPREFIX=/tmp/kanvas-d52-5-pycache python3 -m py_compile scripts/validate_d52_drawminibitmaprect_dashboard_promotion.py
rtk git diff --check
```
