# M56 Unsupported-to-Pass Selection

Result: selected.

M56 targets visible feature/scene progress. It is not a dashboard UX sprint and
does not broaden Skia GM parity claims. A 97% PM readiness score is allowed only
if at least two rows that were `expected-unsupported` after M55 become real
`pass` rows with CPU/GPU/reference/diff/stats artifacts and clean dashboard
gates.

## M55 Baseline

| Signal | Count |
|---|---:|
| Dashboard rows | 60 |
| `pass` | 45 |
| `expected-unsupported` | 15 |
| `tracked-gap` | 0 |
| `fail` | 0 |
| Generated rows | 58 |
| Adapter-backed rows | 41 |
| PM readiness | 95% |

## Candidate Assessment

| Row | Family | M55 status | M56 decision | Reason |
|---|---|---|---|---|
| `m53-sweep-gradient-clamp` | sweep gradient boundary | `expected-unsupported` | selected for promotion | The row was mapped too conservatively to two-point conical coverage; the existing `sweep-gradient-path-clamp` artifacts prove a real sweep-gradient WebGPU route. |
| `m53-imagefilters-cropped-boundary` | bounded image-filter | `expected-unsupported` | rejected for M56 | The existing `crop-image-filter-nonnull-prepass` row proves only a narrow crop prepass subcase; copying it to this broader cropped DAG row would over-claim support. |
| `m54-imagefilters-graph-boundary` | bounded image-filter v2 | `expected-unsupported` | rejected for M56 | The row represents broader graph/picture prepass shapes; existing bounded matrix-transform artifacts are not row-specific evidence for this boundary. |
| `m54-dash-circle-boundary` | Path AA / dash | `expected-unsupported` | deferred | Dash circle still exceeds the current coverage edge budget. |
| `m53-complexclip-boundary-refusal` | complex clip | `expected-unsupported` | deferred | Complex path clipping remains outside the bounded rect/rrect/convex coverage routes. |
| `m52-big-tile-image-filter-dag-refusal` | image-filter DAG | `expected-unsupported` | deferred | Big tile DAG remains broader than the bounded prepass routes selected for M56. |
| `font-emoji-color-glyph-refusal` | font/color glyph | `expected-unsupported` | dependency-gated | Color glyph and emoji support stays dependency-gated. |
| `font-complex-shaping-refusal` | font/shaping | `expected-unsupported` | dependency-gated | Complex shaping stays dependency-gated. |
| `m52-color-emoji-blendmodes-refusal` | font/color glyph | `expected-unsupported` | dependency-gated | Combines emoji/color glyph dependency work with blend evidence. |

## Selected Promotions

| Row | Source artifact scene | CPU route | GPU route | Non-claim |
|---|---|---|---|---|
| `m53-sweep-gradient-clamp` | `sweep-gradient-path-clamp` | `cpu.shader.sweep-gradient.path-aa-oracle` | `webgpu.generated.sweep-gradient.path-aa` | Does not claim two-point conical gradient support. `skia-gm-gradients2ptconical` remains rejected/deferred. |

Expected artifact shape for the promoted row:

- `skia.png`;
- `cpu.png`;
- `cpu-diff.png`;
- `gpu.png`;
- `gpu-diff.png`;
- `route-cpu.json`;
- `route-gpu.json`;
- `stats.json`.

## Readiness Rule

M56 can move PM readiness from 95% to 97% only if:

- at least two selected rows are generated as `pass`;
- those rows have adapter-backed GPU artifacts;
- `pipelineSceneDashboardGate` reports 0 failures;
- `pipelinePmBundle` exposes final M56 counters;
- README, target, and backlog state exact before/after counters.

With only the sweep-gradient row promoted, M56 must stay below the 97% target
and document the blocker for image-filter and Path AA/clip rows.
