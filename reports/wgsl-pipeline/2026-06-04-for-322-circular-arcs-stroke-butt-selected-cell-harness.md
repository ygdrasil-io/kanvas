# FOR-322 CircularArcsStrokeButt Selected-Cell Harness

Linear: `FOR-322`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-circular-arcs-stroke-butt-selected-cell-harness-ticket`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_HARNESS_READY`

## Result

FOR-322 adds a headless bounded capture harness for the exact FOR-319
`CircularArcsStrokeButtGM` selected cell. The harness renders only the
selected cell and emits route/stat diagnostics under
`reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/`.

The selected cell and `CircularArcsStrokeButtGM` remain `not-supported`.
This ticket does not change renderer behavior, WGSL shaders, thresholds,
fallback policy, scene status, readiness score, or release gates.

## Harness

- test: `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/CircularArcsStrokeButtSelectedCellCaptureTest.kt`
- command: `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.CircularArcsStrokeButtSelectedCellCaptureTest`
- artifact JSON: `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/circular-arcs-stroke-butt-selected-cell-harness-for322.json`
- headless: `True`
- bounded: `True`

## Selected Cell

| Field | Value |
|---|---|
| fixture id | `circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true` |
| source GM | `CircularArcsStrokeButtGM` |
| source row | `circular-arcs-stroke-butt-webgpu` |
| row / column | `0` / `2` |
| start | `0` |
| sweep | `90` |
| complement | `-270` |
| useCenter | `False` |
| aa | `True` |
| stroke width | `15` |
| stroke cap | `kButt_Cap` |
| full-GM rect | `[140, 520, 180, 560]` |
| bounded rect | `[20, 20, 60, 60]` |
| drawArc calls | `2` |

The selected cell is exactly `start=0`, `sweep=90`, `complement=-270`,
`useCenter=false`, `aa=true`, `strokeWidth=15`, `strokeCap=kButt_Cap`, with
two `drawArc` calls.

## Artifact Slots

| Artifact | Status | Complete | Selected-cell path | Blocked reason |
|---|---|---:|---|---|
| `skia.png` | `blocked` | `False` | `None` | `No checked-in upstream Skia selected-cell reference exists; the harness does not substitute the full-GM PNG.` |
| `cpu.png` | `available` | `True` | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/cpu.png` | `None` |
| `gpu.png` | `blocked` | `False` | `None` | `WebGPU selected-cell PNG unavailable: coverage.stroke-cap-join-visual-parity-below-threshold` |
| `cpu-diff.png` | `available` | `True` | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/cpu-diff.png` | `None` |
| `gpu-diff.png` | `blocked` | `False` | `None` | `WebGPU selected-cell diff unavailable: coverage.stroke-cap-join-visual-parity-below-threshold` |
| `route-cpu.json` | `available` | `True` | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/route-cpu.json` | `None` |
| `route-gpu.json` | `available` | `True` | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/route-gpu.json` | `None` |
| `stats.json` | `available` | `True` | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/stats.json` | `None` |

Coverage complete: `False`

Blocked artifacts: `skia.png, gpu.png, gpu-diff.png`

Full-GM substitution rejected: `True`

## Route Diagnostics

| Route | Backend | Status | Fallback/refusal | Edge-count | Budget | Draw kind |
|---|---|---|---|---|---:|---|
| `route-cpu.json` | `CPU` | `capture-available-not-promoting` | `none` / `none` | `None` (test-only selected-cell harness does not expose post-stroke edge-count diagnostics) | `256` | `CircularArcsStrokeButtSelectedCell` |
| `route-gpu.json` | `WebGPU` | `expected-unsupported` | `coverage.stroke-cap-join-visual-parity-below-threshold` / `coverage.stroke-cap-join-visual-parity-below-threshold` | `66` (None) | `256` | `CircularArcsStrokeButtSelectedCell` |

## Support Guard

| Field | Value |
|---|---|
| support status | `not-supported` |
| support claim | `none` |
| readiness movement | `False` |
| release gate changed | `False` |
| full-GM substitution accepted | `False` |

## Non-Goals And Non-Changes

- no production renderer behavior changed
- no WGSL shader changed
- no support threshold changed
- no fallback policy changed
- no scene support status changed
- no CircularArcsStrokeButtGM support promotion
- no selected-cell support promotion
- no full-GM PNG or score substitution

## Validation

- `rtk ./gradlew --no-daemon -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.CircularArcsStrokeButtSelectedCellCaptureTest`
- `rtk python3 scripts/validate_for322_circular_arcs_stroke_butt_selected_cell_harness.py`
- `rtk python3 scripts/validate_for321_circular_arcs_stroke_butt_selected_cell_artifacts.py`
- `rtk python3 scripts/validate_for320_circular_arcs_stroke_butt_micro_fixture_proof.py`
- `rtk python3 scripts/validate_for319_circular_arcs_stroke_butt_micro_fixture.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/circular-arcs-stroke-butt-selected-cell-harness-for322.json >/dev/null`
- `rtk git diff --check origin/master...HEAD`
