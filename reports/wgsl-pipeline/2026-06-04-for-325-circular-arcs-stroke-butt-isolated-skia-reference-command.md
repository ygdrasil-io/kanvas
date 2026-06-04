# FOR-325 CircularArcsStrokeButt Isolated Skia Reference Command

Linear: `FOR-325`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-provision-repo-owned-isolated-skia-selected-cell-reference-command-ticket`

Decision: `CIRCULAR_ARCS_STROKE_BUTT_ISOLATED_SKIA_REFERENCE_COMMAND_PROVISIONING_MISSING`

## Result

FOR-325 attempted to provision a repo-owned, headless upstream Skia command
that renders only the FOR-319 `CircularArcsStrokeButtGM` selected cell. The
decision is `CIRCULAR_ARCS_STROKE_BUTT_ISOLATED_SKIA_REFERENCE_COMMAND_PROVISIONING_MISSING`. `skia.png` ready: `False`.

No `skia.png` is produced by this ticket. A Skia checkout and upstream `dm`
binary may be present in the local environment, but the available upstream
entry point writes the full 1000x1000 GM. FOR-325 needs a selected-cell
80x80 upstream Skia render command with strict provenance, and full-GM output,
crops, scores, Kanvas CPU output, and Kanvas test harnesses remain rejected.

Missing dependency:

`missing repo-owned headless upstream Skia selected-cell render command or checked-in upstream Skia tool source that draws only the FOR-319 CircularArcsStrokeButt selected cell and writes an 80x80 skia.png plus skia-reference-provenance.json; the available upstream Skia dm binary and gm/circulararcs.cpp render the full 1000x1000 GM, and full-GM output or crops are forbidden`

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
| path | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-isolated-skia-reference-for325/skia.png` |
| provenance path | `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-isolated-skia-reference-for325/skia-reference-provenance.json` |
| present | `False` |
| accepted | `False` |
| status | `blocked-provisioning-missing` |

Blocked reasons:

- no skia.png is present under the FOR-325 artifact directory
- missing repo-owned headless upstream Skia selected-cell render command or checked-in upstream Skia tool source that draws only the FOR-319 CircularArcsStrokeButt selected cell and writes an 80x80 skia.png plus skia-reference-provenance.json; the available upstream Skia dm binary and gm/circulararcs.cpp render the full 1000x1000 GM, and full-GM output or crops are forbidden

## Upstream Skia Dependency Audit

| Item | Present | Accepted | Reason |
|---|---:|---:|---|
| source `/Users/chaos/workspace/kanvas-forge/skia-main/gm/circulararcs.cpp` | `True` | `False` | source defines the full 1000x1000 GM grid, not a checked-in headless selected-cell renderer |
| binary `/Users/chaos/workspace/kanvas-forge/skia-main/out/Release/dm` | `True` | `False` | dm --writePath can emit the full 1000x1000 GM circular_arcs_stroke_butt; FOR-325 requires only the 80x80 FOR-319 cell and forbids crop/full-GM substitution |

Rejected upstream full-GM command:

`rtk /Users/chaos/workspace/kanvas-forge/skia-main/out/Release/dm --src gm --config 8888 --match ^circular_arcs_stroke_butt$ --writePath <dir>`

## Repo Entry Point Audit

| Path | Symbol | Accepted as Skia reference | Reason |
|---|---|---:|---|
| `cpu-raster/src/main/kotlin/org/skia/testing/TestUtils.kt` | `TestUtils.runGmTest` | `False` | renders through Kanvas RasterSinkF16, not upstream Skia |
| `cpu-raster/src/main/kotlin/org/skia/dm/DmCli.kt` | `DmCli` | `False` | Kanvas DM config parser; --writePath is explicitly out of scope here |
| `cpu-raster/src/main/kotlin/org/skia/dm/DmMain.kt` | `DmMain.runFromArgs` | `False` | returns a Kanvas DM JSON report and has no standalone image-generation command |
| `cpu-raster/src/main/kotlin/org/skia/dm/Runner.kt` | `Runner` | `False` | hashes encoded Kanvas sink output for reports; it does not write selected-cell reference PNGs |
| `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/CircularArcsStrokeButtSelectedCellCaptureTest.kt` | `CircularArcsStrokeButtSelectedCellCaptureTest` | `False` | bounded selected-cell harness writes Kanvas CPU output and explicitly deletes skia.png |

## Rejected Substitutions

| Source | Accepted | Reason |
|---|---:|---|
| full-GM PNG `skia-integration-tests/src/test/resources/original-888/circular_arcs_stroke_butt.png` | `False` | full-GM PNG is not an isolated selected-cell render |
| full-GM crop `[120, 500, 200, 580]` | `False` | FOR-323 proved the crop is contaminated by neighboring stroke margins |
| FOR-322 `cpu.png` `reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-harness-for322/cpu.png` | `False` | FOR-322 cpu.png is Kanvas CPU output, not Skia |
| full-GM scores | `False` | full-GM similarity scores are not selected-cell reference pixels |
| upstream `dm --writePath` full-GM output | `False` | upstream dm writes the whole GM; FOR-325 needs an isolated 80x80 selected-cell renderer |

## Strict Provenance Required For A Future Ready Decision

If a future patch creates `skia.png`, it must be 80x80 and accompanied by
`skia-reference-provenance.json` with:

- `sourceType=isolated-skia-selected-cell-render`
- `fixtureId=circular-arcs-stroke-butt-start0-sweep90-usecenter-false-aa-true`
- `sourceGm=CircularArcsStrokeButtGM`
- `sourceRowId=circular-arcs-stroke-butt-webgpu`
- a concrete headless upstream Skia selected-cell render command
- `sourceImplementation` naming the upstream Skia implementation
- upstream Skia source/version evidence
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

- `rtk python3 scripts/validate_for325_circular_arcs_stroke_butt_isolated_skia_reference_command.py`
- `no true repo-owned headless upstream Skia selected-cell generation command is provisionable for this cell`
- `rtk python3 scripts/validate_for324_circular_arcs_stroke_butt_isolated_skia_reference.py`
- `rtk python3 scripts/validate_for323_circular_arcs_stroke_butt_selected_cell_skia_reference.py`
- `rtk python3 scripts/validate_for322_circular_arcs_stroke_butt_selected_cell_harness.py`
- `rtk python3 scripts/validate_for321_circular_arcs_stroke_butt_selected_cell_artifacts.py`
- `rtk python3 scripts/validate_for320_circular_arcs_stroke_butt_micro_fixture_proof.py`
- `rtk python3 scripts/validate_for319_circular_arcs_stroke_butt_micro_fixture.py`
- `rtk python3 -m json.tool reports/wgsl-pipeline/scenes/artifacts/circular-arcs-stroke-butt-selected-cell-isolated-skia-reference-for325/circular-arcs-stroke-butt-selected-cell-isolated-skia-reference-for325.json >/dev/null`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check origin/master...HEAD`
