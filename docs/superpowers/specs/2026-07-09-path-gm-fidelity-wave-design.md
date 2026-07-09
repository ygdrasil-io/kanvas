# Path GM Fidelity Wave Design

Date: 2026-07-09
Branch: `codex/path-gm-fidelity-wave`
Base: `origin/master`

## Purpose

Increase Skia GM `PATH` fidelity with a sequence of focused fixes that produce
visible dashboard movement without hiding unsupported behavior. The wave targets
high-impact `PATH` rows first, then continues by shared renderer mechanism.

This is part of the Skia-like renderer breadth/fidelity target. It does not
claim broad Skia parity, does not port Ganesh or Graphite, and keeps WebGPU as
the GPU backend.

## Current Evidence

The current dashboard identifies these high-value `PATH` rows by weighted
unmatched pixels:

- `convexpaths`: `0.0%`, very high potential. The generated render has a white
  background while the reference is black; a simulated black background raises
  exact-pixel similarity to about `97.18%`.
- `nonclosedpaths`: `64.84%`, very large surface area and strong stroke/cap/join
  signal.
- `filltypespersp`: `11.61%`, large surface area, likely fill/clip/transform
  interaction.
- `manycircles`: `0.0%`, full-canvas mismatch, likely deterministic Skia random
  and/or color generation mismatch.
- `trimpatheffect`: about `79%`, likely GM port issue because trim offsets are
  defined but not applied in the current port.
- `polygons`, `points`, `strokes_round`, `widebuttcaps`,
  `drawlines_with_local_matrix`, `linepath`, `quadpath`, and `cubicpath` are
  secondary high-leverage rows tied to the same mechanisms.

Rows with `noReference` or `renderFailed` remain visible inventory. They are not
counted as renderer wins unless a later fix produces real render/reference
evidence.

## Approach

Use a hybrid strategy:

1. Take low-risk quick wins that remove obvious GM-port mismatches.
2. Move into shared renderer mechanisms so each fix improves several GMs.
3. Keep generated PNG and score updates separated from behavioral fixes when
   practical.
4. Run targeted GM generation per group and full dashboard generation at
   milestones.

This avoids a monolithic PR while still allowing a week-long burn-down in one
conversation.

## Work Groups

### Group 1: GM Port Parity And Determinism

Target rows:

- `convexpaths`
- `manycircles`
- `polygons`
- `strokes_round`
- `widebuttcaps`

Planned work:

- Fix `convexpaths` background parity first.
- Investigate whether current Kotlin `Random` usage diverges from Skia's
  `SkRandom` stream for path GMs.
- If confirmed, add a small test-local Skia-compatible deterministic random
  helper under the integration-test GM support area, then migrate only the
  targeted GMs that need Skia reference parity.

Acceptance:

- `convexpaths` moves from `0.0%` to a high score without threshold masking.
- Randomized GM changes are justified by before/after dashboard evidence.

### Group 2: Open Strokes, Caps, Joins, And Stroke-And-Fill

Target rows:

- `nonclosedpaths`
- `linepath`
- `quadpath`
- `quadclosepath`
- `cubicpath`
- `cubicclosepath`
- `widebuttcaps`
- `strokedline_caps`
- `polygons`

Planned work:

- Audit `PaintStyle.STROKE_AND_FILL` handling. The current GPU predicate treats
  only `PaintStyle.STROKE` as stroke, so stroke-and-fill may not emit both
  coverage paths.
- Preserve open-path cap behavior for `BUTT`, `ROUND`, and `SQUARE`.
- Improve join handling only where tests and GM evidence demonstrate the root
  cause.

Acceptance:

- Add renderer-level tests for `STROKE_AND_FILL` and open stroke geometry before
  changing behavior.
- Targeted GM scores improve without regressing `dashcircle`, `zerolinedash`,
  or `thin_aa_dash_lines`.

### Group 3: DrawPoints And Local Shader Lines

Target rows:

- `points`
- `drawlines_with_local_matrix`

Planned work:

- Verify `PointMode.POINTS`, `LINES`, and `POLYGON` behavior against the GM
  reference.
- Handle round point caps and zero-length point/line cases consistently with
  path stroke behavior.
- Check shader application for line strokes used by
  `drawlines_with_local_matrix`.

Acceptance:

- Renderer tests cover point modes and cap-dependent geometry.
- Targeted GM generation shows real pixel movement, not only score updates.

### Group 4: Fill Rules, Inverse Fill, Clips, And Transforms

Target rows:

- `filltypespersp`
- `preservefillrule_big`
- `preservefillrule_little`
- inverse-fill cells inside `quadpath` and `cubicpath`

Planned work:

- Trace fill-type propagation from GM path construction through normalized GPU
  commands and dispatch.
- Verify scissor/clip interaction for transformed filled paths.
- Keep inverse fill support bounded to tested cases; unsupported broad behavior
  must produce stable diagnostics rather than silent claims.

Acceptance:

- Tests isolate fill-type and clip behavior.
- GM evidence includes before/after dashboard scores and visible diffs.

### Group 5: PathEffect Trim And Dash Residuals

Target rows:

- `trimpatheffect`
- `dashing5_aa`
- residual dash rows such as `dashcircle`, `dashing`, and
  `thin_aa_dash_lines`

Planned work:

- First determine whether the GM port applies the declared `PathEffect.Trim`
  ranges. If the port is incomplete, fix the port separately from renderer
  behavior.
- For dashes, continue from the existing round/square cap fixes and focus on
  long-line clipping, phase handling, and anti-aliased coverage only when
  diagnostics prove the root cause.

Acceptance:

- No dash fix is accepted without a targeted reproduction and an image/score
  comparison.
- Existing passed dash rows remain passing.

## Commit And PR Discipline

Each group can produce multiple commits:

- `Fix <gm> GM port parity` for GM source corrections.
- `Fix <mechanism> path rendering` for renderer behavior changes.
- `Regenerate path GM renders and scores` for PNG and score artifacts.

Generated PNGs and `test-similarity-scores.properties` should be committed only
after the related targeted generation passes. Full dashboard regeneration should
happen at group boundaries or before PR review.

## Verification Plan

Per group:

- Run focused unit tests for touched renderer or GM helper code.
- Run `:integration-tests:skia:generateSkiaRendersFor -Pgm.name=<name>` for
  each targeted GM.
- Read `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`
  or targeted comparison output for exact scores.

At milestones:

- Run `:integration-tests:skia:generateSkiaDashboard`.
- Run `:integration-tests:skia:test` when score persistence is required.
- Run `git diff --check`.
- Request an external review for broad renderer changes.

## Non-Goals

- Do not lower thresholds to claim progress.
- Do not hide `noReference`, `renderFailed`, or unsupported rows.
- Do not claim broad Skia path parity from selected GM improvements.
- Do not add unrelated renderer refactors outside the current root cause.
- Do not port Ganesh, Graphite, or SkSL compiler behavior.

## First Implementation Plan Candidate

Start with Group 1:

1. Fix `convexpaths` background parity and add a small GM-level regression test
   if practical.
2. Regenerate only `convexpaths` and record score movement.
3. Investigate `SkRandom` parity using one randomized GM with a high signal,
   likely `manycircles`.
4. Decide whether a shared test-local `SkRandom` helper is warranted before
   migrating other randomized path GMs.
