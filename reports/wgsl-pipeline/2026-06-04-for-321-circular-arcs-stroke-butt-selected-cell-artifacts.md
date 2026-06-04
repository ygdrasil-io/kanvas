# FOR-321 CircularArcsStrokeButt Selected-Cell Artifacts

Linear: `FOR-321`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-circular-arcs-stroke-butt-selected-cell-artifact-generation-ticket`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_ARTIFACTS_BLOCKED`

## Result

FOR-321 checks whether the exact FOR-319 selected cell can produce a complete
selected-cell artifact bundle. The decision is blocked: the current strict
scope has no generator for row-specific `skia.png`, `cpu.png`, adapter-backed
`gpu.png`, CPU/reference diff and stats, GPU/reference diff and stats, or
`route-cpu.json` / `route-gpu.json` diagnostics with `edge-count` and fallback
fields.

The selected cell and the source GM remain `not-supported`. No renderer,
shader, existing test, threshold, fallback, scene status, readiness score, or
release gate changes are made.

## Source Linkage

- FOR-319 artifact: `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-micro-fixture-for319/circular-arcs-stroke-butt-micro-fixture-for319.json`
- FOR-319 report: `reports/wgsl-pipeline/2026-06-04-for-319-circular-arcs-stroke-butt-micro-fixture.md`
- FOR-320 artifact: `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-micro-fixture-proof-for320/circular-arcs-stroke-butt-micro-fixture-proof-for320.json`
- FOR-320 report: `reports/wgsl-pipeline/2026-06-04-for-320-circular-arcs-stroke-butt-micro-fixture-proof.md`
- FOR-318 artifact: `reports/wgsl-pipeline/scenes/artifacts/path-aa-arc-stroke-hairline-scout-for318/path-aa-arc-stroke-hairline-scout-for318.json`
- FOR-318 report: `reports/wgsl-pipeline/2026-06-04-for-318-path-aa-arc-stroke-hairline-scout.md`
- M60 feature spec: `.upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md`
- Fidelity spec: `.upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md`
- GM source: `skia-integration-tests/src/main/kotlin/org/skia/tests/CircularArcsGM.kt`
- Existing WebGPU test: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/CircularArcsStrokeButtWebGpuTest.kt`

## Selected Cell

| Field | Value |
|---|---|
| fixture id | `circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true` |
| source future target | `future-circular-arcs-stroke-butt-nonhairline-subdivision-probe` |
| source row | `circular-arcs-stroke-butt-webgpu` |
| source GM | `CircularArcsStrokeButtGM` |
| support status | `not-supported` |
| row / column | `0` / `2` |
| start | `0` |
| sweep | `90` |
| complement sweep | `-270` |
| useCenter | `False` |
| AA | `True` |
| style | `kStroke_Style` |
| stroke width | `15` |
| stroke cap | `kButt_Cap` |
| canvas rect | `[140, 520, 180, 560]` |
| drawArc calls | `2` |

The selected cell is exactly
`circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true`:
`start=0`, `sweep=90`, `complement=-270`, `useCenter=false`, `aa=true`,
`strokeWidth=15`, `strokeCap=kButt_Cap`, with two `drawArc` calls. Hairline,
round cap, square cap, fill, dash, `useCenter=true`, and full-GM substitution
remain excluded.

## Artifact Coverage

| Artifact | Status | Complete | Blocked reason | Missing tooling |
|---|---|---:|---|---|
| `skia.png` | `blocked` | `False` | `No selected-cell Skia/reference PNG exists for the FOR-319 cell; the checked-in reference is the full 512-arc GM.` | `No strict-scope command, scene contract, or checked-in harness can render only the selected CircularArcsStrokeButtGM cell as skia.png.` |
| `cpu.png` | `blocked` | `False` | `No selected-cell CPU PNG exists; available CPU evidence is a full-GM similarity score/report.` | `No strict-scope CPU render artifact emitter exists for the selected drawArc pair without adding or changing tests/harness code.` |
| `gpu.png` | `blocked` | `False` | `No selected-cell adapter-backed GPU PNG exists; the existing WebGPU test renders the full GM.` | `No strict-scope adapter-backed WebGPU artifact emitter can target only the FOR-319 cell without modifying test or renderer code.` |
| `cpu-reference-diff-and-stats` | `blocked` | `False` | `No selected-cell CPU/reference diff image or stats payload can be computed because selected-cell skia.png and cpu.png are absent.` | `Missing selected-cell reference/CPU PNG inputs and a strict-scope diff/stat emission path for this fixture.` |
| `gpu-reference-diff-and-stats` | `blocked` | `False` | `No selected-cell GPU/reference diff image or stats payload can be computed because selected-cell skia.png and adapter-backed gpu.png are absent.` | `Missing selected-cell reference/GPU PNG inputs and a strict-scope adapter-backed diff/stat emission path for this fixture.` |
| `route-cpu.json` | `blocked` | `False` | `No selected-cell CPU route JSON exists with edge-count and fallback fields for the two drawArc calls.` | `No strict-scope route diagnostics emitter can isolate the selected cell and serialize CPU edge-count/fallback fields.` |
| `route-gpu.json` | `blocked` | `False` | `No selected-cell GPU route JSON exists with edge-count and fallback fields for the two drawArc calls.` | `No strict-scope adapter-backed route diagnostics emitter can isolate the selected cell and serialize GPU edge-count/fallback fields.` |

Coverage complete: `False`

Blocked artifacts: `skia.png, cpu.png, gpu.png, cpu-reference-diff-and-stats, gpu-reference-diff-and-stats, route-cpu.json, route-gpu.json`

## Full-GM Substitution Rejection

Existing full-GM evidence is recorded but rejected as selected-cell proof:

- full-GM reference PNG: `skia-integration-tests/src/test/resources/original-888/circular_arcs_stroke_butt.png`
- full-GM WebGPU score: `CircularArcsStrokeButtGM=96.87`
- full-GM CPU score: `CircularArcsStrokeButtGM=45.6605`

Reason: Full-GM reference, CPU, and GPU scores cover the 512-arc GM and are not row-specific to the selected FOR-319 cell.

## Budget And Support Guard

| Field | Value |
|---|---|
| WebGPU AA edge budget | `256` |
| edge budget may increase | `False` |
| stroke-width budget | `0.5..64.0` |
| hairline strokeWidth=0 supported | `False` |
| threshold weakening allowed | `False` |
| fallback weakening allowed | `False` |
| renderer or shader change allowed | `False` |
| existing tests change allowed | `False` |
| scene status change allowed | `False` |
| readiness movement | `False` |
| release gate changed | `False` |
| unsafe decision | `CIRCULAR_ARCS_STROKE_BUTT_UNSAFE_SUPPORT_CLAIM_FOUND` |

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

## Non-Goals And Non-Changes

- no renderer or shader code changed
- no existing tests changed
- no scene status changed
- no fallback reason changed
- no edge budget changed
- no visual threshold changed
- no readiness score changed
- no release gate changed
- no support claim for CircularArcsStrokeButtGM or the selected cell
- no full-GM substitution
- no hairline strokeWidth=0
- no round cap, square cap, fill, dash, useCenter=true, or full-GM scope

## Validation

- `rtk python3 scripts/validate_for321_circular_arcs_stroke_butt_selected_cell_artifacts.py`
- `rtk python3 scripts/validate_for320_circular_arcs_stroke_butt_micro_fixture_proof.py`
- `rtk python3 scripts/validate_for319_circular_arcs_stroke_butt_micro_fixture.py`
- `rtk python3 scripts/validate_for318_path_aa_arc_stroke_hairline_scout.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-artifacts-for321/circular-arcs-stroke-butt-selected-cell-artifacts-for321.json >/dev/null`
- `rtk git diff --check`
