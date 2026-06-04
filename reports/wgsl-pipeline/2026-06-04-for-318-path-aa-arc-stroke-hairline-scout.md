# FOR-318 Path AA Arc Stroke/Hairline Scout

Linear: `FOR-318`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-path-aa-arc-stroke-hairline-subdivision-scout-ticket`

Decision: `PATH_AA_ARC_STROKE_HAIRLINE_SCOUT_APPLIED`

## Result

FOR-318 converts the FOR-307 `arc-stroke-hairline-subdivision-scout` selection into a
reporting-only scout dossier. It selects one future micro-target source,
`CircularArcsStrokeButtGM`, but it does not promote support, change renderer
behavior, change shader behavior, adjust thresholds, increase the WebGPU edge
budget, relabel fallbacks, move readiness, or change release gates.

The selected future micro-target is `future-circular-arcs-stroke-butt-nonhairline-subdivision-probe`. It is scoped to a
single bounded non-hairline butt-cap circular arc probe derived from
`CircularArcsStrokeButtGM`. It remains `not-supported` and blocked until the
full proof list below exists. If the row cannot be isolated under the existing
256-edge budget, the stable decision is to keep `coverage.edge-count-exceeded`.

## Source Linkage

- FOR-307 artifact: `reports/wgsl-pipeline/scenes/artifacts/path-aa-edge-budget-candidate-selection-for307/path-aa-edge-budget-candidate-selection-for307.json`
- FOR-307 report: `reports/wgsl-pipeline/2026-06-04-for-307-path-aa-edge-budget-candidate-selection.md`
- M37 audit: `reports/wgsl-pipeline/2026-05-28-m37-path-aa-breadth-audit.md`
- M60 feature spec: `.upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md`
- Fidelity spec: `.upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md`

## Arc/Hairline Candidate Rows

| Row id | GM | Status | Fallback | Scout disposition |
|---|---|---|---|---|
| `addarc-webgpu` | `AddArcGM` | `expected-unsupported` | `coverage.edge-count-exceeded` | `blocked-broad-gm-row` |
| `circular-arcs-hairline-webgpu` | `CircularArcsHairlineGM` | `expected-unsupported` | `coverage.edge-count-exceeded` | `blocked-by-m60-stroke-width-floor` |
| `circular-arcs-stroke-butt-webgpu` | `CircularArcsStrokeButtGM` | `expected-unsupported` | `coverage.edge-count-exceeded` | `selected-future-micro-target-source` |
| `circular-arcs-stroke-round-webgpu` | `CircularArcsStrokeRoundGM` | `expected-unsupported` | `coverage.edge-count-exceeded` | `defer-cap-join-risk` |
| `circular-arcs-stroke-square-webgpu` | `CircularArcsStrokeSquareGM` | `expected-unsupported` | `coverage.edge-count-exceeded` | `defer-cap-join-risk` |
| `crbug1472747-webgpu` | `Crbug1472747GM` | `expected-unsupported` | `coverage.edge-count-exceeded` | `blocked-regression-fixture` |
| `addarc-crossbackend` | `AddArcGM` | `expected-unsupported` | `coverage.edge-count-exceeded` | `blocked-broad-gm-row` |
| `circular-arcs-hairline-crossbackend` | `CircularArcsHairlineGM` | `expected-unsupported` | `coverage.edge-count-exceeded` | `blocked-by-m60-stroke-width-floor` |
| `crbug1472747-crossbackend` | `Crbug1472747GM` | `expected-unsupported` | `coverage.edge-count-exceeded` | `blocked-regression-fixture` |

## Risk Classification

| Classification | Decision | Rows | Reason |
|---|---|---:|---|
| arc subdivision | `requires-isolated-subdivision-bound` | 9 | Every M37 arc/hairline row exceeds the current edge budget through arc subdivision breadth. |
| cap/join behavior | `split-before-round-or-square-cap-promotion` | 5 | Butt, round, and square cap rows must not be promoted as one broad cap/join claim. |
| hairline strokeWidth=0 | `blocked-by-current-m60-stroke-width-budget` | 2 | M60 starts at strokeWidth >= 0.5 px, so hairline remains explicit future scope. |
| edge budget | `preserve-256-edge-budget-and-current-refusals` | 9 | FOR-318 is reporting-only and cannot increase the WebGPU AA edge budget. |
| reference provenance | `requires-skia-reference-plus-cpu-and-adapter-backed-gpu-proof` | 9 | Skia-like fidelity movement requires row-local reference, CPU, GPU, diff, stats, and route artifacts. |

## Future Micro-Target

| Field | Value |
|---|---|
| id | `future-circular-arcs-stroke-butt-nonhairline-subdivision-probe` |
| source row | `circular-arcs-stroke-butt-webgpu` |
| source GM | `CircularArcsStrokeButtGM` |
| status | `selected-future-micro-target` |
| support status | `not-supported` |
| stable fallback if not isolated | `coverage.edge-count-exceeded` |

## Required Future Promotion Proof

- row-specific geometry
- Skia/reference artifact
- CPU artifact
- adapter-backed GPU artifact
- CPU/GPU diff and stats
- route diagnostics with edge-count and fallback fields
- fallback policy preserving refusals outside the selected row
- no global edge-budget increase
- no threshold weakening

## Preserved Fallback Rows

| Scene id | Status | Fallback reason | Source |
|---|---|---|---|
| `path-aa-stroke-outline-fallback` | `expected-unsupported` | `coverage.stroke-outline-edge-count-exceeded` | `reports/wgsl-pipeline/scenes/data/scenes.json` |
| `path-aa-edge-budget-boundary` | `expected-unsupported` | `coverage.edge-count-exceeded` | `reports/wgsl-pipeline/scenes/data/scenes.json` |
| `path-aa-convexpaths-edge-budget` | `expected-unsupported` | `coverage.edge-count-exceeded` | `reports/wgsl-pipeline/scenes/generated/*.json` |
| `path-aa-dashing-edge-budget` | `expected-unsupported` | `coverage.edge-count-exceeded` | `reports/wgsl-pipeline/scenes/generated/*.json` |
| `m52-closed-capped-hairlines-edge-budget` | `expected-unsupported` | `coverage.edge-count-exceeded` | `reports/wgsl-pipeline/scenes/generated/*.json` |
| `m54-dash-circle-boundary` | `expected-unsupported` | `coverage.edge-count-exceeded` | `reports/wgsl-pipeline/scenes/generated/*.json` |
| `m66-path-aa-dashing-edge-budget-refusal` | `expected-unsupported` | `coverage.edge-count-exceeded` | `reports/wgsl-pipeline/scenes/generated/*.json` |

## Non-Changes

- no renderer or shader code changed
- no scene status changed
- no fallback reason changed
- no edge budget changed
- no visual threshold changed
- no readiness score changed
- no release gate changed
- no arc/hairline support claimed

## Validation

- `rtk python3 scripts/validate_for318_path_aa_arc_stroke_hairline_scout.py`
- `rtk python3 scripts/validate_for307_path_aa_edge_budget_candidate_selection.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/path-aa-arc-stroke-hairline-scout-for318/path-aa-arc-stroke-hairline-scout-for318.json >/dev/null`
- `rtk git diff --check`
