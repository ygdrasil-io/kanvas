# FOR-320 CircularArcsStrokeButt Micro-Fixture Proof Bundle

Linear: `FOR-320`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-circular-arcs-stroke-butt-micro-fixture-proof-bundle-ticket`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_MICRO_FIXTURE_PROOF_BLOCKED`

## Result

FOR-320 packages the available proof state for the single FOR-319
`CircularArcsStrokeButtGM` cell `circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true`. The decision is blocked,
not applied: the repository does not contain row-specific Skia/reference, CPU,
adapter-backed GPU, CPU/GPU diff/stat, or route diagnostics artifacts for this
selected cell.

The FOR-318/FOR-319 target remains `not-supported`. No renderer, shader,
threshold, fallback, scene status, readiness score, or release gate changes are
made.

## Source Linkage

- FOR-319 artifact: `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-micro-fixture-for319/circular-arcs-stroke-butt-micro-fixture-for319.json`
- FOR-319 report: `reports/wgsl-pipeline/2026-06-04-for-319-circular-arcs-stroke-butt-micro-fixture.md`
- FOR-318 artifact: `reports/wgsl-pipeline/scenes/artifacts/path-aa-arc-stroke-hairline-scout-for318/path-aa-arc-stroke-hairline-scout-for318.json`
- FOR-318 report: `reports/wgsl-pipeline/2026-06-04-for-318-path-aa-arc-stroke-hairline-scout.md`
- M60 feature spec: `.upstream/specs/skia-like-realtime/01-rendering-feature-expansion.md`
- Fidelity spec: `.upstream/specs/skia-like-realtime/03-skia-fidelity-and-gm-promotion.md`
- GM source: `skia-integration-tests/src/main/kotlin/org/skia/tests/CircularArcsGM.kt`
- Existing WebGPU test: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/CircularArcsStrokeButtWebGpuTest.kt`

## Selected Micro-Fixture

| Field | Value |
|---|---|
| id | `circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true` |
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

## Proof Checklist

| Proof | Status | Complete | Justification |
|---|---|---:|---|
| `row-specific geometry` | `captured` | `True` | FOR-319 defines the single GM cell and drawArc pair. |
| `Skia/reference artifact` | `blocked` | `False` | The repository has only the full 512-arc GM reference PNG; no row-specific Skia/reference artifact exists for the selected cell. |
| `CPU artifact` | `blocked` | `False` | The available CPU evidence is full-GM similarity output, not a selected-cell CPU render artifact. |
| `adapter-backed GPU artifact` | `blocked` | `False` | The existing adapter-backed test covers the full GM and does not emit a selected-cell GPU artifact. |
| `CPU/GPU diff and stats` | `blocked` | `False` | No selected-cell CPU/GPU diff image or stats payload exists. |
| `route diagnostics with edge-count and fallback fields` | `blocked` | `False` | No selected-cell route JSON exposes edge-count and fallback fields. |
| `fallback policy preserving refusals outside the selected row` | `captured` | `True` | Existing Path AA expected-unsupported rows retain their fallback reasons. |
| `no global edge-budget increase` | `captured` | `True` | The FOR-320 artifact keeps the WebGPU AA edge budget at 256. |
| `no threshold weakening` | `captured` | `True` | The FOR-320 artifact does not change visual thresholds. |

## Blocking Evidence

The proof bundle is incomplete until these row-specific items exist:

- Skia/reference artifact
- CPU artifact
- adapter-backed GPU artifact
- CPU/GPU diff and stats
- route diagnostics with edge-count and fallback fields

Existing full-GM evidence is recorded but is not accepted as selected-cell
promotion proof:

- full-GM reference PNG: `skia-integration-tests/src/test/resources/original-888/circular_arcs_stroke_butt.png`
- full-GM WebGPU score: `CircularArcsStrokeButtGM=96.87`
- full-GM CPU score: `CircularArcsStrokeButtGM=45.6605`

## Budget And Support Guard

| Field | Value |
|---|---|
| WebGPU AA edge budget | `256` |
| edge budget may increase | `False` |
| threshold weakening allowed | `False` |
| fallback weakening allowed | `False` |
| hairline strokeWidth=0 supported | `False` |
| renderer or shader change allowed | `False` |
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
- no support claim for CircularArcsStrokeButtGM or the micro-fixture
- no hairline strokeWidth=0
- no round cap, square cap, fill, dash, useCenter=true, or full-GM scope

## Validation

- `rtk python3 scripts/validate_for320_circular_arcs_stroke_butt_micro_fixture_proof.py`
- `rtk python3 scripts/validate_for319_circular_arcs_stroke_butt_micro_fixture.py`
- `rtk python3 scripts/validate_for318_path_aa_arc_stroke_hairline_scout.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-micro-fixture-proof-for320/circular-arcs-stroke-butt-micro-fixture-proof-for320.json >/dev/null`
- `rtk git diff --check`
