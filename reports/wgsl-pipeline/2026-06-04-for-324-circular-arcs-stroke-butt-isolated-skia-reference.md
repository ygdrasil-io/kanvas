# FOR-324 CircularArcsStrokeButt Isolated Skia Reference

Linear: `FOR-324`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-circular-arcs-stroke-butt-isolated-selected-cell-skia-reference-harness-ticket`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_ISOLATED_SKIA_REFERENCE_HARNESS_MISSING`

## Result

FOR-324 checked whether this repository currently has a true headless,
isolated upstream Skia reference-generation path for the FOR-319
`CircularArcsStrokeButtGM` selected cell. The decision is
`CIRCULAR_ARCS_STROKE_BUTT_ISOLATED_SKIA_REFERENCE_HARNESS_MISSING`. `skia.png` ready: `False`.

No `skia.png` is produced by this ticket. The repository has a bounded
selected-cell Kanvas harness from FOR-322 and a full-GM Skia PNG, but no
repo-owned command/API that renders only this 80x80 selected cell through
upstream Skia and writes strict provenance.

Missing API/command:

`missing repo-owned headless upstream Skia selected-cell render API/command: no Gradle task, CLI flag, Kotlin helper, or checked-in executable renders only the FOR-319 CircularArcsStrokeButt selected cell through upstream Skia and writes an 80x80 skia.png plus skia-reference-provenance.json; existing TestUtils.runGmTest, RasterSinkF16, DmMain/DmCli, and the FOR-322 capture test are Kanvas CPU/DM/test paths, not upstream Skia reference generation`

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
| drawArc calls | `2` |

The target cell is exactly `start=0`, `sweep=90`, `complement=-270`,
`useCenter=false`, `aa=true`, `strokeWidth=15`, `strokeCap=kButt_Cap`,
alpha `100`, with two arcs.

## Candidate `skia.png`

| Field | Value |
|---|---|
| path | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-isolated-skia-reference-for324/skia.png` |
| provenance path | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-isolated-skia-reference-for324/skia-reference-provenance.json` |
| present | `False` |
| accepted | `False` |
| status | `blocked-missing` |

Blocked reasons:

- no skia.png is present under the FOR-324 artifact directory
- missing repo-owned headless upstream Skia selected-cell render API/command: no Gradle task, CLI flag, Kotlin helper, or checked-in executable renders only the FOR-319 CircularArcsStrokeButt selected cell through upstream Skia and writes an 80x80 skia.png plus skia-reference-provenance.json; existing TestUtils.runGmTest, RasterSinkF16, DmMain/DmCli, and the FOR-322 capture test are Kanvas CPU/DM/test paths, not upstream Skia reference generation

## Audited Entry Points

| Path | Symbol | Accepted as Skia reference | Reason |
|---|---|---:|---|
| `cpu-raster/src/main/kotlin/org/skia/testing/TestUtils.kt` | `TestUtils.runGmTest` | `False` | renders through Kanvas RasterSinkF16, not upstream Skia |
| `cpu-raster/src/main/kotlin/org/skia/dm/DmCli.kt` | `DmCli` | `False` | parses Kanvas DM config/match/skip only; --writePath is documented out of scope |
| `cpu-raster/src/main/kotlin/org/skia/dm/DmMain.kt` | `DmMain.runFromArgs` | `False` | returns a Kanvas DM JSON report for an explicit Kotlin GM list and has no standalone image-generation command |
| `cpu-raster/src/main/kotlin/org/skia/dm/Runner.kt` | `Runner` | `False` | hashes encoded Kanvas sink output for reports; it does not write selected-cell reference PNGs |
| `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/CircularArcsStrokeButtSelectedCellCaptureTest.kt` | `CircularArcsStrokeButtSelectedCellCaptureTest` | `False` | bounded selected-cell harness writes Kanvas CPU output and explicitly deletes skia.png |

## Rejected Substitutions

| Source | Accepted | Reason |
|---|---:|---|
| full-GM PNG `skia-integration-tests/src/test/resources/original-888/circular_arcs_stroke_butt.png` | `False` | full-GM PNG is not an isolated selected-cell render |
| full-GM crop `[120, 500, 200, 580]` | `False` | FOR-323 proved the crop is contaminated by neighboring stroke margins |
| FOR-322 `cpu.png` `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/cpu.png` | `False` | FOR-322 cpu.png is Kanvas CPU output, not Skia |
| full-GM scores | `False` | full-GM similarity scores are not selected-cell reference pixels |

## Strict Provenance Required For A Future Ready Decision

If a future ticket creates `skia.png`, it must be 80x80 and accompanied by
`skia-reference-provenance.json` with:

- `sourceType=isolated-skia-selected-cell-render`
- `fixtureId=circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true`
- source GM `CircularArcsStrokeButtGM`
- a concrete headless upstream Skia render command
- `fullGmCrop=false`
- `fullGmSubstitutionAccepted=false`
- `cpuKanvasOutputAcceptedAsSkia=false`
- the selected-cell geometry above

The validator fails if `skia.png` is the full-GM PNG, a crop, a full-GM
score substitute, FOR-322 `cpu.png`, or lacks strict provenance.

## Preserved Contracts

| Field | Value |
|---|---|
| support status | `not-supported` |
| full-GM substitution accepted | `False` |
| full-GM score evidence accepted | `False` |
| CPU Kanvas output accepted as Skia | `False` |
| GPU route status | `expected-unsupported` |
| GPU refusal reason | `coverage.stroke-cap-join-visual-parity-below-threshold` |
| readiness movement | `False` |
| release gate changed | `False` |
| production renderer changed | `False` |
| WGSL changed | `False` |
| threshold changed | `False` |
| fallback policy changed | `False` |
| Kadre/native dependency added | `False` |

## Validation

- `rtk python3 scripts/validate_for324_circular_arcs_stroke_butt_isolated_skia_reference.py`
- `no true headless isolated Skia generation command exists in the repo for this cell`
- `rtk python3 scripts/validate_for323_circular_arcs_stroke_butt_selected_cell_skia_reference.py`
- `rtk python3 scripts/validate_for322_circular_arcs_stroke_butt_selected_cell_harness.py`
- `rtk python3 scripts/validate_for321_circular_arcs_stroke_butt_selected_cell_artifacts.py`
- `rtk python3 scripts/validate_for320_circular_arcs_stroke_butt_micro_fixture_proof.py`
- `rtk python3 scripts/validate_for319_circular_arcs_stroke_butt_micro_fixture.py`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-isolated-skia-reference-for324/circular-arcs-stroke-butt-selected-cell-isolated-skia-reference-for324.json >/dev/null`
- `rtk git diff --check origin/master...HEAD`
