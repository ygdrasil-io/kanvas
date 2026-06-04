# FOR-319 CircularArcsStrokeButt Micro-Fixture Preflight

Linear: `FOR-319`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-circular-arcs-stroke-butt-micro-fixture-preflight-ticket`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_MICRO_FIXTURE_PREFLIGHT_APPLIED`

## Result

FOR-319 defines a reporting-only micro-fixture contract for one deterministic
`CircularArcsStrokeButtGM` cell. It follows the FOR-318 future target
`future-circular-arcs-stroke-butt-nonhairline-subdivision-probe` and keeps that target `not-supported`.

No renderer, shader, threshold, fallback, scene status, readiness score, or
release gate changes are made. The fixture is a preflight selection only: it
does not generate reference/CPU/GPU images, route JSON, diff stats, or support
evidence.

## Source Linkage

- FOR-318 artifact: `reports/wgsl-pipeline/scenes/artifacts/path-aa-arc-stroke-hairline-scout-for318/path-aa-arc-stroke-hairline-scout-for318.json`
- FOR-318 report: `reports/wgsl-pipeline/2026-06-04-for-318-path-aa-arc-stroke-hairline-scout.md`
- M37 audit: `reports/wgsl-pipeline/2026-05-28-m37-path-aa-breadth-audit.md`
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
| fixture kind | `single-gm-cell-reporting-only` |
| cell count | `1` |
| quadrant | `bottom-left` |
| row / column | `0` / `2` |
| start | `0` |
| sweep | `90` |
| complement sweep | `-270` |
| useCenter | `False` |
| AA | `True` |
| style | `kStroke_Style` |
| stroke width | `15` |
| stroke cap | `kButt_Cap` |
| local rect | `[0, 0, 40, 40]` |
| canvas rect | `[140, 520, 180, 560]` |
| drawArc calls | `2` |

The selected cell is `start=0`, `sweep=90`, `useCenter=false`, `aa=true`,
`strokeWidth=15`, `strokeCap=kButt_Cap`. It is faithful to the GM cell shape,
so the cell contains two draws: the red 90-degree arc and the blue -270-degree
complement arc.

## Budget Guard

| Field | Value |
|---|---|
| WebGPU AA edge budget | `256` |
| edge budget may increase | `False` |
| measured edge count known | `False` |
| measured edge count required before support | `True` |
| fallback if measured over budget | `coverage.edge-count-exceeded` |
| full GM drawArc calls | `512` |
| selected drawArc calls | `2` |
| drawArc reduction factor vs full GM | `256` |
| stroke width within M60 budget | `True` |
| hairline excluded by M60 budget | `True` |

## Support Guard

- Status: `reporting-only`
- Support status: `not-supported`
- Current support claim: `none`
- Readiness movement: `False`
- Release gate changed: `False`
- Unsafe decision: `CIRCULAR_ARCS_STROKE_BUTT_UNSAFE_SUPPORT_CLAIM_FOUND`

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

## Non-Goals And Non-Changes

- no CPU/GPU/reference images generated or modified
- no renderer or shader code changed
- no existing tests changed
- no scene status changed
- no fallback reason changed
- no edge budget changed
- no visual threshold changed
- no readiness score changed
- no release gate changed
- no support claim for CircularArcsStrokeButtGM or the micro-fixture
- no hairline, round cap, square cap, fill, dash, or full-GM scope

## Validation

- `rtk python3 scripts/validate_for319_circular_arcs_stroke_butt_micro_fixture.py`
- `rtk python3 scripts/validate_for318_path_aa_arc_stroke_hairline_scout.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-micro-fixture-for319/circular-arcs-stroke-butt-micro-fixture-for319.json >/dev/null`
- `rtk git diff --check`
