# M56 Sprint Review

Result: partial pass.

M56 targeted a 97% PM readiness score by converting at least two
`expected-unsupported` rows into real `pass` rows. The sprint produced one
defensible promotion and rejected two tempting but unsafe shortcuts.

## Before / After

| Signal | M55 baseline | M56 result |
|---|---:|---:|
| Dashboard rows | 60 | 60 |
| `pass` | 45 | 46 |
| `expected-unsupported` | 15 | 14 |
| `tracked-gap` | 0 | 0 |
| `fail` | 0 | 0 |
| Generated rows | 58 | 58 |
| Static policy rows | 2 | 2 |
| Adapter-backed rows | 41 | 42 |
| Inventory-derived rows | 32 | 32 |

## Promoted Row

| Row | Previous claim | M56 result |
|---|---|---|
| `m53-sweep-gradient-clamp` | `expected-unsupported` conical-gradient proxy | `pass` sweep-gradient kClamp path subset |

The prior row mixed a real `sweep-gradient-path-clamp` artifact source with the
wrong inventory target, `skia-gm-gradients2ptconical`. M56 corrects the mapping
to `skia-gm-sweepgradient`, keeps two-point conical gradients rejected, and
uses the existing CPU/GPU/reference/diff/stats artifacts with
`fallbackReason=none`.

## Rejected Shortcuts

| Area | Decision | Reason |
|---|---|---|
| Image-filter | No promotion | The supported `crop-image-filter-nonnull-prepass` row proves a narrow subcase only. Copying its GPU artifact to broader cropped/graph rows would over-claim DAG or picture-prepass support. |
| Path AA / clip | No promotion | Current boundary rows lack row-specific GPU image/diff/stats with `fallbackReason=none`. Edge budget, dash, and complex clip refusals stay valid. |

## Linear Scope

| Ticket | Result |
|---|---|
| `GRA-333` | Selection report produced. |
| `GRA-334` | Image-filter promotion rejected with blocker evidence. |
| `GRA-335` | Sweep-gradient row promoted to `pass`. |
| `GRA-336` | Path AA / clip promotion rejected with blocker evidence. |
| `GRA-337` | No new measured performance rows; promoted row uses existing non-performance visual artifacts. |
| `GRA-338` | PM/docs/readiness closeout prepared. |

## Score

M56 should move PM readiness from 95% to 96%, not 97%.

The sprint improved visible rendering evidence by one adapter-backed pass row,
but missed the explicit 97% condition of at least two real
`expected-unsupported` to `pass` promotions.

## Non-Claims

- No two-point conical gradient support.
- No arbitrary image-filter DAG or picture-prepass support.
- No broad Path AA, dash, stroke, or complex clip support.
- No fake GPU artifacts.
- No dashboard UX change.
- No release-blocking performance gate.

## Validation

- `rtk git diff --check`
- `rtk ./gradlew --no-daemon pipelineSceneDashboard pipelineSceneDashboardGate pipelinePmBundle`
- `rtk ./gradlew --no-daemon pipelineSkiaGmInventory pipelineSkiaGmInventoryGate`
- `rtk ./gradlew --no-daemon pipelinePerformanceTrendWarnings`
