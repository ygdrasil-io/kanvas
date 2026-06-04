# FOR-327 CircularArcsStrokeButt Selected-Cell Skia Reference

Linear: `FOR-327`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-build-and-run-skia-selected-cell-source-ticket`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_SELECTED_CELL_SKIA_REFERENCE_READY`

## Result

FOR-327 builds and runs the repo-owned FOR-326 C++ source against an upstream
Skia checkout when available. `skia.png` ready: `True`.

Blocked reasons:

- none

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
| alpha | `100` |
| full-GM rect | `[140, 520, 180, 560]` |
| bounded rect | `[20, 20, 60, 60]` |

## Accepted `skia.png`

| Field | Value |
|---|---|
| path | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for327/skia.png` |
| provenance path | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for327/skia-reference-provenance.json` |
| present | `True` |
| accepted | `True` |
| status | `available-isolated-skia-selected-cell-render` |
| dimensions | `{"width": 80, "height": 80}` |
| sha256 | `0b69bc3d36f34f6c2fc0fd8f67d9d120d632cf64264983e52db9f5f7cd679ef0` |

## Provenance

| Field | Value |
|---|---|
| sourceType | `isolated-skia-selected-cell-render` |
| command | `rtk python3 tools/skia-reference/build_for327_circular_arcs_stroke_butt_selected_cell.py` |
| sourceImplementation | `tools/skia-reference/circular_arcs_stroke_butt_selected_cell.cpp` |
| upstream Skia root | `/Users/chaos/workspace/kanvas-forge/skia-main` |
| upstream Skia revision | `defc3a5a92966c32cb2a6a901e2fa3036a13bb8a` |
| fullGmCrop | `False` |
        | fullGmSubstitutionAccepted | `False` |
        | cpuKanvasOutputAcceptedAsSkia | `False` |

## Residual Risk

The reference is generated from the repo-owned FOR-326 source, but reproduction
depends on the local upstream Skia checkout `/Users/chaos/workspace/kanvas-forge/skia-main`
and its `defc3a5a92966c32cb2a6a901e2fa3036a13bb8a` revision plus `out/Release`
libraries. This is reference evidence for this selected cell only; it does not
promote `CircularArcsStrokeButtGM`, does not change Kanvas scene support, and
does not claim broad Skia parity.

## Rejected Substitutions

| Source | Accepted | Reason |
|---|---:|---|
| full-GM PNG `skia-integration-tests/src/test/resources/original-888/circular_arcs_stroke_butt.png` | `False` | full-GM PNG is not an isolated selected-cell render |
| full-GM crop | `False` | FOR-327 requires executing the FOR-326 source, not slicing a GM image |
| FOR-322 `cpu.png` `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/cpu.png` | `False` | FOR-322 cpu.png is Kanvas CPU output, not upstream Skia |
| full-GM scores | `False` | similarity scores are not selected-cell reference pixels |

## Preserved Contracts

| Field | Value |
|---|---|
| support status | `not-supported` |
| GPU route status | `expected-unsupported` |
| GPU refusal reason | `coverage.stroke-cap-join-visual-parity-below-threshold` |
| production renderer changed | `False` |
| WGSL changed | `False` |
| threshold changed | `False` |
| fallback policy changed | `False` |
| Kadre/native dependency added | `False` |
| scene promotion changed | `False` |

## Validation

- `rtk python3 scripts/validate_for327_circular_arcs_stroke_butt_selected_cell_skia_reference.py`
- `rtk python3 tools/skia-reference/build_for327_circular_arcs_stroke_butt_selected_cell.py`
- `rtk python3 scripts/validate_for326_circular_arcs_stroke_butt_skia_selected_cell_source.py`
- `rtk python3 scripts/validate_for325_circular_arcs_stroke_butt_isolated_skia_reference_command.py`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-skia-reference-for327/circular-arcs-stroke-butt-selected-cell-skia-reference-for327.json >/dev/null`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check origin/master...HEAD`
