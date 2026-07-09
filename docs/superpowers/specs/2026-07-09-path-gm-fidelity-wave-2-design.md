# Path GM Fidelity Wave 2 Design

Date: 2026-07-09
Branch: `codex/master-after-path-gm`
Base: `origin/master`

## Purpose

Maximize Skia GM `PATH` dashboard movement while fixing reusable renderer
mechanisms. Wave 1 handled GM-port parity and deterministic random streams.
Wave 2 targets the next high-impact rows where shared path behavior is likely
to improve multiple GMs.

This wave remains evidence-driven. It does not modify reference images, does
not lower thresholds, does not hide unsupported rows, and does not claim broad
Skia path parity from selected GM improvements.

## Current Baseline

The post-Wave-1 dashboard on `origin/master` reports:

| GM | Similarity | Threshold | Matching / Total |
|---|---:|---:|---:|
| `filltypespersp` | `11.96036498431708` | `0.0` | `83890 / 701400` |
| `dashing5_aa` | `0.0` | `0.0` | `0 / 80000` |
| `drawlines_with_local_matrix` | `54.901199999999996` | `0.0` | `137253 / 250000` |
| `cubicclosepath` | `70.83974358974359` | `0.0` | `342581 / 483600` |
| `cubicpath` | `70.89805624483044` | `0.0` | `342863 / 483600` |
| `linepath` | `77.09015715467329` | `0.0` | `372808 / 483600` |
| `dashcircle` | `81.72749999999999` | `36.5` | `882657 / 1080000` |
| `dashing` | `89.80974264705883` | `84.8` | `195426 / 217600` |
| `convexpaths` | `99.45257575757576` | `0.0` | `1312774 / 1320000` |

Rows with `noReference`, `renderFailed`, or `sizeMismatch` remain visible
inventory. They are not counted as wins unless a later group produces real
render/reference evidence.

## Strategy

Use a combined score-first and mechanism-first strategy:

1. Rank candidates by expected dashboard movement and unmatched pixels.
2. Prefer mechanisms that can move several `PATH` rows at once.
3. Keep each mechanism isolated enough to review and revert independently.
4. Generate targeted renders after each accepted mechanism.
5. Use full dashboard generation at group boundaries and before PR review.

The wave may be implemented as several commits or several PRs. Each group
should stand on its own with source changes, tests, render artifacts, and
dashboard evidence.

## Work Groups

### Group 1: DrawPoints And Local Matrix Lines

Target rows:

- `drawlines_with_local_matrix`
- `points`

Planned work:

- Audit `GmCanvas.drawPoints`, `Canvas.drawPoints`, and GPU lowering for
  `PointMode.LINES`, `PointMode.POINTS`, and `PointMode.POLYGON`.
- Verify whether line stroke geometry receives the same shader local matrix as
  equivalent path strokes.
- Add renderer-level tests for point modes and shader/local-matrix propagation.
- Regenerate targeted renders only after tests and score evidence justify the
  change.

Acceptance:

- `drawlines_with_local_matrix` improves materially or yields a documented
  diagnostic explaining why the root cause is outside `drawPoints`.
- Existing passed stroke/dash rows do not materially regress.

### Group 2: Stroke-And-Fill And Open Path Geometry

Target rows:

- `linepath`
- `quadpath`
- `quadclosepath`
- `cubicpath`
- `cubicclosepath`
- relevant `polygons` cells if shared behavior applies

Planned work:

- Audit `PaintStyle.STROKE_AND_FILL` handling through display ops, CPU/GPU
  lowering, and coverage generation.
- Verify open-path cap behavior for `BUTT`, `ROUND`, and `SQUARE`.
- Add tests that prove stroke-and-fill emits both fill and stroke coverage where
  expected.
- Keep join/cap changes bounded to reproduced failures.

Acceptance:

- At least one of `linepath`, `quadpath`, `cubicpath`, or close variants moves
  upward without breaking `dashcircle`, `dashing`, `thin_aa_dash_lines`, or
  `zerolinedash`.
- If a broad renderer fix would regress existing high-confidence rows, revert
  that sub-change and document the root cause.

### Group 3: PathEffect Trim And Dash Residuals

Target rows:

- `dashing5_aa`
- `trimpatheffect`
- residual dash rows such as `dashcircle`, `dashing`, and
  `thin_aa_dash_lines`

Planned work:

- First determine whether each failure is a GM-port mismatch or renderer
  behavior.
- For trim, verify that declared trim ranges are actually applied by the GM and
  by renderer path-effect handling.
- For dash residuals, focus on phase handling, long-line clipping,
  zero/near-zero intervals, and anti-aliased coverage.
- Preserve rows already above threshold unless a measured improvement outweighs
  a clearly understood tradeoff.

Acceptance:

- `dashing5_aa` or `trimpatheffect` improves from a reproducible change, or the
  group produces a precise diagnostic and stops.
- Existing dash rows stay passing.

### Group 4: Fill Rules, Perspective, And Clips

Target rows:

- `filltypespersp`
- `preservefillrule_big`
- `preservefillrule_little`
- inverse-fill cells inside path GMs

Planned work:

- Trace fill-type propagation from path construction through display ops,
  normalized GPU commands, and dispatch.
- Verify inverse fill and clip/scissor behavior under transforms.
- Treat perspective-specific gaps as renderer work only after a minimized
  reproduction proves that the GM port is correct.

Acceptance:

- `filltypespersp` improves materially, or diagnostics identify a deeper
  perspective/coverage limitation that should be split into a separate plan.
- No reference images or thresholds are changed.

## Evidence And Commit Discipline

Each group uses the same evidence flow:

1. Extract baseline rows from
   `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.
2. Add focused tests before changing shared behavior where practical.
3. Make source changes in mechanism-scoped commits.
4. Run targeted render generation for changed GMs.
5. Run full dashboard at group boundaries.
6. Request external review before PR or before moving past a broad renderer
   change.

Commit examples:

- `Add drawPoints local matrix regression tests`
- `Fix drawPoints line shader local matrix`
- `Regenerate drawPoints path GM renders`
- `Add stroke-and-fill path coverage tests`
- `Fix stroke-and-fill path rendering`
- `Regenerate stroke-and-fill path GM renders`

`test-similarity-scores.properties` must not be committed as evidence for
blocking GMs when the test task skips those rows. Blocking GM evidence should be
reported from dashboard `gms.json`.

## Guardrails

- Do not modify Skia reference images. Files under
  `integration-tests/skia/src/test/resources/reference/**` are fixed upstream
  evidence, not rebaseline targets.
- Do not modify `integration-tests/skia/src/test/resources/reference/**`.
- Do not lower `minSimilarity` thresholds.
- Do not hide `noReference`, `renderFailed`, or `sizeMismatch` rows.
- Do not port Ganesh, Graphite, or SkSL compiler behavior.
- Do not mix unrelated renderer refactors into these groups.
- Do not keep a migration that materially regresses an already-passing GM unless
  the tradeoff is explicit, measured, and approved.

## Stop Rules

- If a group does not produce score movement after one or two tested hypotheses,
  stop and document the diagnostic rather than continuing to guess.
- If a shared fix regresses unrelated GMs, revert only that sub-change and keep
  independent improvements.
- If evidence points to a deeper architectural gap, split that gap into a new
  design/spec instead of expanding this wave.

## Verification Plan

Per group:

- Focused unit or integration tests for touched renderer or GM code.
- `:integration-tests:skia:generateSkiaRendersFor -Pgm.name=<name>
  -Pgm.includeBlocking=true` for each targeted blocking GM.
- Dashboard row extraction from `gms.json` for exact before/after scores.
- `git diff --name-only -- integration-tests/skia/src/test/resources/reference`
  must be empty.
- `git diff --check` must be clean.

At PR boundary:

- `:integration-tests:skia:test --tests ...` for focused tests.
- `:integration-tests:skia:generateSkiaDashboard`.
- External code review.
