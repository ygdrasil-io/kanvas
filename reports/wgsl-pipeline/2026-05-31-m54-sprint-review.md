# M54 Sprint Review

Date: 2026-05-31
Linear epic: GRA-317
Tickets: GRA-318 through GRA-324
Merged PR: https://github.com/ygdrasil-io/kanvas/pull/1277
Verified merge commit: `6f174de1`

## Capability

M54 turns the selected Hard Feature Depth Pack into generated dashboard evidence
across bounded image-filter v2, Path AA / clip depth, and runtime / paint
composition. The pack adds warning-only performance payloads only where measured
source rows already exist.

## Before And After

| Signal | Before M54 | After M54 |
|---|---:|---:|
| Scene rows | 50 | 60 |
| `pass` | 37 | 45 |
| `expected-unsupported` | 13 | 15 |
| `tracked-gap` | 0 | 0 |
| `fail` | 0 | 0 |
| Generated evidence rows | 48 | 58 |
| Static policy rows | 2 | 2 |
| Adapter-backed rows | 33 | 41 |
| Inventory-derived generated rows | 22 | 32 |
| PM readiness | 90% | 93% |

## Completed Work

- GRA-318 selected 13 hard feature depth candidates and documented concrete
  rejected/deferred rows.
- GRA-319 promoted bounded image-filter v2 rows: 2 pass rows plus 1 stable
  image-filter graph refusal.
- GRA-320 promoted Path AA / clip depth rows: 2 pass rows plus 1 stable
  edge-budget refusal.
- GRA-321 promoted runtime / paint composition rows: 4 pass rows.
- GRA-322 attached warning-only measured performance payloads to 2 M54 rows.
- GRA-323 added M54 PM bundle counters and dashboard gate family counters.
- GRA-324 synchronized readiness evidence and closeout reporting.

## Promoted Scenes

Bounded image-filter v2:

- `m54-imagefilter-transformed-affine`
- `m54-matrix-imagefilter-affine`
- `m54-imagefilters-graph-boundary`

Path AA / clip depth:

- `m54-simple-aa-clip`
- `m54-rrect-clip-drawpaint`
- `m54-dash-circle-boundary`

Runtime / paint composition:

- `m54-runtime-imagefilter-descriptor`
- `m54-compose-colorfilter-paint`
- `m54-src-over-composition-depth`
- `m54-local-matrix-blend-composition`

## Evidence

- Selection:
  `reports/wgsl-pipeline/2026-05-31-m54-hard-feature-depth-selection.md`
- Promotion contract:
  `reports/wgsl-pipeline/scenes/generated/m54-hard-feature-depth-pack.json`
- Promotion evidence:
  `reports/wgsl-pipeline/2026-05-31-m54-hard-feature-depth-pack.md`
- Warning-only performance:
  `reports/wgsl-pipeline/2026-05-31-m54-warning-only-performance-evidence.md`
- PM bundle counters and gates:
  `reports/wgsl-pipeline/2026-05-31-m54-pm-bundle-counters-and-gates.md`
- PM report:
  `reports/wgsl-pipeline/2026-05-31-m54-pm-report.md`
- PM bundle:
  `build/reports/wgsl-pipeline-pm-bundle/manifest.json#m54HardFeatureDepth`

## Non-Claims

- No broad Skia GM parity.
- No broad arbitrary image-filter DAG support.
- No broad Path AA edge-budget expansion.
- No Ganesh/Graphite port.
- No SkSL compiler, IR, or VM rebuild.
- No temporary font/codec/emoji/shaping/SDF/LCD/glyph-mask substitutes.
- No release-blocking performance gate.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSkiaGmInventory pipelineSkiaGmInventoryGate pipelineSceneDashboard pipelineSceneDashboardGate pipelinePmBundle
```

Result: pass.

Post-merge verification on `master` reran the same validation and confirmed the
generated dashboard, PM bundle, and Linear closeout counters match this review.
