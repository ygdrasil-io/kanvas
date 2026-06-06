# D50 GM Dashboard Candidate Inventory

Date: 2026-06-06
Linear: FOR-461
JSON: `reports/wgsl-pipeline/scenes/generated/d50-gm-dashboard-candidate-inventory.json`

## Scope

This report inventories 25 Skia GM candidates that can push the selected
dashboard set beyond 50 rows after future generated evidence lands. It does not
promote rows, change dashboard statuses, weaken thresholds, alter scoring, add
fallbacks, or change `PipelineKey`.

Inventory status is not support status. A candidate only becomes a selected
dashboard row after row-specific reference, CPU, GPU or refusal, diff/stat, and
route artifacts exist.

## Sources Read

- `reports/wgsl-pipeline/scenes/data/scenes.json`
- `reports/wgsl-pipeline/scenes/generated/results.json`
- `reports/wgsl-pipeline/scenes/generated/*.json`
- `build/reports/wgsl-pipeline-skia-gm-inventory/inventory.json`
- `build/reports/wgsl-pipeline-skia-gm-inventory-gate/inventory-gate.md`
- `reports/upstream-rebaseline/`
- `.upstream/source/map/`
- `.upstream/target/skia-like-realtime-renderer-target.md`
- `.upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md`

## Current Counters

Local materialized dashboard evidence has 28 rows when
`reports/wgsl-pipeline/scenes/generated/results.json` is combined with the two
static rows in `reports/wgsl-pipeline/scenes/data/scenes.json`.

| Counter | Value |
|---|---:|
| Local materialized rows | 28 |
| Supported `pass` rows | 21 |
| Expected-unsupported rows | 7 |
| Diagnostic-only rows | 0 |
| `skia-upstream` rows | 5 |
| `test-oracle` rows | 11 |
| `cpu-oracle` rows | 12 |

The local Skia GM inventory gate reports:

| Counter | Value |
|---|---:|
| Inventory rows | 802 |
| Dashboard-promoted rows | 27 |
| Promotion-candidate rows | 28 |
| Expected-unsupported inventory rows | 27 |
| Dependency-gated rows | 37 |
| Not-triaged rows | 619 |

## Target Counters

The D50 inventory selects 25 candidates. From the local 28-row dashboard base,
23 additional selected rows are enough to exceed 50; the full 25-row inventory
projects to 53 selected rows if every candidate later lands with valid evidence.

| Counter | Value |
|---|---:|
| Candidate rows inventoried | 25 |
| Recommended lot 1 rows | 12 |
| Selected rows needed to exceed 50 | 23 |
| Projected selected rows if all candidates land | 53 |
| Support claims added now | 0 |
| Skia-comparable claims added now | 0 |
| Intended support candidates requiring evidence | 21 |
| Expected-unsupported boundary candidates | 2 |
| Diagnostic-only candidates | 2 |

This deliberately keeps selected rows, Skia-comparable rows, supported rows,
expected-unsupported rows, and diagnostic-only rows separate. The 25 candidates
are planning capacity only; current supported rows remain 21 until future
promotion evidence changes the dashboard.

## Family Strategy

The first pass favors visible, low-surprise rows:

- bitmap/image: rect draws, small rect draws, premul alpha, static image source,
  and local-matrix image shader cells;
- image filters: offset, affine matrix, small bounded blur, transformed image
  filters, and make-with-filter when bounds are explicit;
- clip/path: simple AA clip, bounded path fill, convex/rect polygon clips, and
  visible complex-clip refusal;
- paint/blend: arithmetic blend, sanitized paint, and mode color filters;
- gradients: bounded degenerate linear-gradient cells only;
- runtime/text: diagnostic-only rows until registered descriptors and font
  evidence exist.

| Family | Candidates | Lot 1 | Intended support | Expected unsupported | Diagnostic-only |
|---|---:|---:|---:|---:|---:|
| Bitmap/image | 6 | 6 | 6 | 0 | 0 |
| Image filters | 7 | 3 | 6 | 1 | 0 |
| Clip/transform/Path AA | 6 | 2 | 5 | 1 | 0 |
| Paint/blend | 3 | 0 | 3 | 0 | 0 |
| Gradients | 1 | 1 | 1 | 0 | 0 |
| Runtime effects | 1 | 0 | 0 | 0 | 1 |
| Text/font | 1 | 0 | 0 | 0 | 1 |

## Lot 1 Recommendation

Lot 1 should promote 12 rows first:

| Rank | Inventory id | Family | Intended status | Why first |
|---:|---|---|---|---|
| 1 | `skia-gm-drawbitmaprect` | Bitmap/image | `pass` | Direct bitmap rect sampling with clear nearest/linear cells. |
| 2 | `skia-gm-drawminibitmaprect` | Bitmap/image | `pass` | Small bitmap rect draw catches rounding without widening sampler scope. |
| 3 | `skia-gm-bitmappremul` | Bitmap/image | `pass` | Visible premul-alpha behavior on an existing GM. |
| 4 | `skia-gm-image` | Bitmap/image | `pass` | Static image draw, excluding codec/YUV/animation expansion. |
| 5 | `skia-gm-imagesource` | Bitmap/image | `pass` | Image source provenance can be documented row-by-row. |
| 6 | `skia-gm-localmatriximageshader` | Bitmap/image | `pass` | Extends existing local-matrix bitmap coverage without perspective. |
| 7 | `skia-gm-gradientsdegenerate` | Gradients | `pass` | Bounded degenerate linear-gradient stop cases. |
| 8 | `skia-gm-offsetimagefilter` | Image filters | `pass` | Small bounded image-filter offset with explicit bounds. |
| 9 | `skia-gm-matriximagefilter` | Image filters | `pass` | Affine matrix image-filter subset. |
| 10 | `skia-gm-imageblur` | Image filters | `pass` | Small sigma bounded blur, no large/unbounded blur claim. |
| 11 | `skia-gm-simpleaaclip` | Clip/Path AA | `pass` | Bounded AA clip visible in PM review. |
| 12 | `skia-gm-pathfill` | Clip/Path AA | `pass` | Filled path cells can be edge-budget gated. |

Each lot 1 row must still produce row-specific reference, CPU, GPU or refusal,
diff/stat, and route artifacts. `fallbackReason=none` is required for any future
support row.

## Remaining Candidates

| Rank | Inventory id | Family | Classification | Intended status |
|---:|---|---|---|---|
| 13 | `skia-gm-arithmode` | Paint/blend | Selected candidate | `pass` after bounded coefficient evidence |
| 14 | `skia-gm-badpaint` | Paint/blend | Selected candidate | `pass` for sanitized paint-state cells |
| 15 | `skia-gm-modecolorfilters` | Paint/blend | Selected candidate | `pass` for selected mode color filters |
| 16 | `skia-gm-clipshader` | Clip/Path AA | Selected candidate | `pass` for rectangular clip-shader subset |
| 17 | `skia-gm-convexpolyclip` | Clip/Path AA | Selected candidate | `pass` if edge budget holds |
| 18 | `skia-gm-rectpolystroke` | Clip/Path AA | Selected candidate | `pass` for bounded primitive stroke cells |
| 19 | `skia-gm-bitmapfilters` | Image filters | Selected candidate | `pass` for nearest/linear filter cells |
| 20 | `skia-gm-imagefilterstransformed` | Image filters | Selected candidate | `pass` for affine transformed subset |
| 21 | `skia-gm-imagemakewithfilter` | Image filters | Selected candidate | `pass` when crop/prepass bounds are explicit |
| 22 | `skia-gm-complexclip` | Clip/Path AA | Expected-unsupported boundary | `expected-unsupported` |
| 23 | `skia-gm-imagefilterscropped` | Image filters | Expected-unsupported boundary | `expected-unsupported` unless prepass ownership lands |
| 24 | `skia-gm-runtimeintrinsics` | Runtime effects | Diagnostic-only | `diagnostic-only` until registered descriptor exists |
| 25 | `skia-gm-textblobtransforms` | Text/font | Diagnostic-only | `diagnostic-only` until font/glyph route evidence exists |

## Exclusions

| Inventory id or family | Reason |
|---|---|
| `skia-gm-runtimeimagefilter` | Runtime image filters need registered descriptors; arbitrary SkSL/image-filter input remains refused. |
| `skia-gm-shadertext3` | Crosses text/glyph dependency and runtime shader behavior; not a clean D50 dashboard-expansion row. |
| `skia-gm-gradients2ptconical` | Two-point conical gradients remain outside the bounded linear/sweep gradient envelope. |
| Codec/YUV/animated image rows | Dependency-gated; no short-lived codec substitutes. |
| Dash/hairline broad stroke rows | Edge-budget/cap/join policy work must land before promotion. |

## Non-Claims

- No dashboard status changes are made by this ticket.
- No support row is added by this ticket.
- No Skia-comparable fidelity row is added by this ticket.
- The projected 53 selected rows are a planning projection, not a visual support
  percentage.
- CPU-oracle and diagnostic-only rows do not count as Skia-comparable fidelity.
- No broad Skia GM parity, arbitrary image-filter DAG, arbitrary SkSL, broad
  Path AA, glyph shaping, codec, or YUV support is claimed.
- No global thresholds, scoring logic, fallback policy, or `PipelineKey` axes
  changed.

## Next Evidence Gate

Future promotion work should materialize lot 1 in smaller implementation
tickets. A row may move from candidate to selected only when the dashboard gate
can verify reference provenance, CPU/GPU or stable refusal routes, diff/stat
artifacts, and unchanged threshold/fallback policy.
