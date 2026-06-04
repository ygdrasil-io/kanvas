# FOR-307 Path AA Edge-Budget Candidate Selection

Linear: `FOR-307`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-path-aa-edge-budget-candidate-selection-ticket`

Decision: `PATH_AA_EDGE_BUDGET_CANDIDATE_SELECTED`

## Result

FOR-307 selects `arc-stroke-hairline-subdivision-scout` as the next Path AA
edge-budget candidate, in candidate-only status. This ticket does not promote a
scene, change the renderer, change shader behavior, adjust thresholds, increase
the WebGPU edge budget, or relabel any fallback.

The selection is based on the existing M37 ranking plus the M44 evidence that
the first-ranked primitive stroke family is already promoted as
`path-aa-stroke-primitive`. The next bounded family is therefore arc
stroke/hairline, but it remains expected unsupported until a future
implementation ticket produces row-local rendered proof.

## Family Ranking

| Rank | Family | Disposition | Reason |
|---:|---|---|---|
| 1 | Stroke rectangle/circle | `already-promoted` | M44 promoted path-aa-stroke-primitive for StrokeRectGM and StrokeCircleGM. |
| 2 | Arc stroke/hairline | `selected-candidate` | M37 ranks arc stroke/hairline directly after primitive strokes; it is narrower than dash/fill/composition packs but still needs curve-subdivision evidence. |
| 3 | General stroke/dash | `rejected-for-this-ticket` | Dash/cap/join expansion combines unrelated behavior and must be split first. |
| 4 | Fill/convex/path pack | `rejected-for-this-ticket` | Broad GM packs include many shapes, convexity cases, and fill rules. |
| 5 | Filter/shader over path | `rejected-for-this-ticket` | Composition must wait for a stable base path coverage route. |
| 6 | Benchmark stress | `rejected-for-this-ticket` | Stress rows are performance signals, not support conversion candidates. |

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

## Policy Cases

| Case | Decision | Allowed | Reason |
|---|---|---:|---|
| FOR-307 selection-only arc stroke/hairline candidate | `candidate-only` | True | Candidate selection is allowed because it preserves all current refusals and claims no support. |
| Global edge-budget increase is forbidden | `forbidden` | False | Policy changes to budget, threshold, fallback, or scene status are forbidden here. |
| Broad convex/fill/dash pack selection is forbidden | `forbidden` | False | Broad Path AA packs cannot be selected before a narrower route is proven. |
| Arc selection before primitive promotion is ambiguous | `ambiguous` | False | Arc stroke/hairline is only next after primitive stroke promotion is verified. |
| Rendered proof remains future implementation scope | `future-promotion-candidate` | True | Complete rendered proof belongs in a future implementation ticket, not this selection ticket. |

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

## Non-Changes

- No renderer or shader code changed.
- No scene status changed.
- No fallback reason changed.
- No edge budget changed.
- No visual threshold changed.
- No broad GM pack is claimed as supported.

## Validation

- `rtk python3 scripts/validate_for307_path_aa_edge_budget_candidate_selection.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/path-aa-edge-budget-candidate-selection-for307/path-aa-edge-budget-candidate-selection-for307.json`
- `rtk git diff --check origin/master...HEAD`
