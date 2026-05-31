# M53 Sprint Review

Date: 2026-05-31
Linear epic: GRA-309
Tickets: GRA-310 through GRA-316

## Capability

M53 turns the selected GM Feature Promotion Pack v2 into generated dashboard
evidence with PM-visible feature breadth across gradients, bitmap/image,
blend/color-filter, clip/transform/saveLayer, and bounded image-filter scenes.

## Before And After

| Signal | Before M53 | After M53 |
|---|---:|---:|
| Scene rows | 38 | 50 |
| `pass` | 28 | 37 |
| `expected-unsupported` | 10 | 13 |
| `tracked-gap` | 0 | 0 |
| `fail` | 0 | 0 |
| Generated evidence rows | 36 | 48 |
| Static policy rows | 2 | 2 |
| Adapter-backed rows | 24 | 33 |
| Inventory-derived generated rows | 10 | 22 |
| PM readiness | 85% | 90% |

## Completed Work

- GRA-310 selected 12 M53 candidates and documented rejected/deferred rows.
- GRA-311 added the M53 promotion contract and materialization task.
- GRA-312 promoted gradient and bitmap/image rows: 3 pass rows plus 1 explicit
  gradient boundary refusal.
- GRA-313 promoted blend/color-filter and clip rows: 5 pass rows plus 1
  complex clip boundary refusal.
- GRA-314 promoted one bounded image-filter pass row and one stable cropped
  image-filter refusal.
- GRA-315 added M53 PM bundle counters and metadata validation.
- GRA-316 updated PM score evidence and closeout reporting.

## Promoted Scenes

Gradient and bitmap/image:

- `m53-degenerate-gradient-linear`
- `m53-sweep-gradient-clamp`
- `m53-bitmap-premul-alpha`
- `m53-bitmap-filter-linear-subset`

Blend/color-filter and clip/transform/saveLayer:

- `m53-arithmode-bounded-blend`
- `m53-mode-color-filter-screen`
- `m53-badpaint-sanitized-state`
- `m53-clipshader-rect-subset`
- `m53-convex-poly-clip`
- `m53-complexclip-boundary-refusal`

Bounded image-filter:

- `m53-imageblur-bounded-prepass`
- `m53-imagefilters-cropped-boundary`

## Evidence

- Selection:
  `reports/wgsl-pipeline/2026-05-31-m53-gm-feature-promotion-pack-v2-selection.md`
- Promotion contract:
  `reports/wgsl-pipeline/scenes/generated/m53-inventory-promotion-pack.json`
- Promotion evidence:
  `reports/wgsl-pipeline/2026-05-31-m53-inventory-promotion-pack.md`
- PM report:
  `reports/wgsl-pipeline/2026-05-31-m53-pm-report.md`
- PM bundle:
  `build/reports/wgsl-pipeline-pm-bundle/manifest.json#m53InventoryPromotion`

## Non-Claims

- No broad Skia GM parity.
- No broad image-filter DAG support.
- No broad Path AA edge-budget expansion.
- No Ganesh/Graphite port.
- No SkSL compiler, IR, or VM rebuild.
- No temporary font/codec/emoji/shaping/SDF/LCD/glyph-mask substitutes.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSkiaGmInventory pipelineSkiaGmInventoryGate pipelineSceneDashboard pipelineSceneDashboardGate pipelinePmBundle
```

Result: pass.
