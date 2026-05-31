# M52 Sprint Review: GM Inventory Promotion Pack

Date: 2026-05-31
Status: Closed
Linear epic: GRA-302
Tickets: GRA-303 through GRA-308
Target: `archives/target-closeout-2026-05-31/rendering-conformance-performance-target.md`

## Outcome

M52 converted a small selected pack from the M51 GM inventory into generated
dashboard evidence. The pack promotes 10 inventory-derived rows: 7 generated
`pass` rows and 3 generated `expected-unsupported` rows. Every promoted row has
an `inventoryId`, source report, reference/CPU/GPU or refusal route,
diff/stat artifacts, tags, and stable fallback semantics.

Inventory rows remain planning evidence unless they are represented by a
generated dashboard row. M52 does not claim broad Skia GM support.

## Ticket Closeout

| Ticket | Result |
|---|---|
| GRA-303 | Selected 10 M51 candidates across paint/blend, bitmap/image, gradients, clip/transform, Path AA, image filters, and text/font boundaries. |
| GRA-304 | Defined each row's evidence contract in `2026-05-31-m52-inventory-promotion-pack.md`. |
| GRA-305 | Added `pipelineM52InventoryPromotionPack`, which verifies each selected base generated dashboard row, carries forward its real generation trace, and materializes M52 dashboard rows plus artifacts under `build/reports/wgsl-pipeline-m52-generated/`. |
| GRA-306 | Integrated dashboard counters and PM bundle `m52InventoryPromotion` metadata. |
| GRA-307 | Strengthened gates for inventory-derived rows: `inventoryId`, matching `generation.inventoryId`, `sourceReport`, and `source.generated` are now required. |
| GRA-308 | Updated sprint review, PM report, README, target, and backlog score sync. |

## Dashboard Counters

| Signal | Before M52 | After M52 |
|---|---:|---:|
| Scene rows | 28 | 38 |
| `pass` | 21 | 28 |
| `expected-unsupported` | 7 | 10 |
| `tracked-gap` | 0 | 0 |
| `fail` | 0 | 0 |
| Generated evidence rows | 26 | 36 |
| Static policy rows | 2 | 2 |
| Adapter-backed rows | 17 | 24 |
| Inventory-derived generated rows | 0 | 10 |

## Promoted Rows

| Scene id | Inventory id | Status |
|---|---|---|
| `m52-aa-rect-modes-tight-aa` | `skia-gm-aarectmodes` | `pass` |
| `m52-android-blend-src-over-screen` | `skia-gm-androidblendmodes` | `pass` |
| `m52-fillrect-gradient-linear` | `skia-gm-fillrectgradient` | `pass` |
| `m52-hardstop-gradient-linear` | `skia-gm-hardstopgradients` | `pass` |
| `m52-clipped-bitmap-shader-rect` | `skia-gm-clippedbitmapshaders` | `pass` |
| `m52-bitmap-image-basic` | `skia-gm-bitmapimage` | `pass` |
| `m52-bitmap-rect-test-nearest` | `skia-gm-bitmaprecttest` | `pass` |
| `m52-closed-capped-hairlines-edge-budget` | `skia-gm-closedcappedhairlines` | `expected-unsupported` |
| `m52-big-tile-image-filter-dag-refusal` | `skia-gm-bigtileimagefilter` | `expected-unsupported` |
| `m52-color-emoji-blendmodes-refusal` | `skia-gm-coloremojiblendmodes` | `expected-unsupported` |

## Validation

```bash
git diff --check
./gradlew --no-daemon pipelineSkiaGmInventory pipelineSkiaGmInventoryGate
./gradlew --no-daemon pipelineSceneDashboard pipelineSceneDashboardGate
./gradlew --no-daemon pipelinePmBundle
```

Result: pass.

## Score Sync

Post-MVP readiness moves from 82% to 85%.

| Area | M51 | M52 | Reason |
|---|---:|---:|---|
| Evidence foundation | 100% | 100% | The generated dashboard remains deterministic with 0 tracked-gap and 0 fail. |
| Skia integration coverage | 70% | 82% | 10 selected M51 inventory candidates now have generated dashboard evidence or stable generated refusals. |
| CI and release gates | 85% | 85% | Gate checks are additive and require inventory-derived row invariants. |
| Performance readiness | 60% | 60% | No release-blocking performance threshold change. |
| PM demo and reporting workflow | 88% | 90% | PM bundle now reports selected/promoted/rejected M52 inventory promotion rows. |

Weighted score: 85%.

## Limits And Non-Claims

- M52 does not claim broad Skia GM support.
- M52 does not convert M51 inventory status into support.
- M52 does not add hundreds of dashboard rows.
- M52 does not introduce `tracked-gap` or `fail`.
- M52 does not clear font/codec/emoji/shaping/SDF/LCD/glyph-mask, arbitrary
  image-filter DAG, arbitrary SkSL, or broad Path AA gaps.
