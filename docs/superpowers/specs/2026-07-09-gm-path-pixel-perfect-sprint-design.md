# GM PATH Pixel-Perfect Sprint Design

Date: 2026-07-09
Branch: `codex/gm-path-pixel-perfect-sprint`
Base: `origin/master`

## Purpose

Improve Skia GM PATH fidelity in an isolated sprint worktree, starting from the
PATH rows that are already close to pixel-perfect and then working through the
90-95% band. The sprint is exhaustive for the requested 15 initial targets, but
fixes still land as small root-cause commits rather than one broad dashboard
rewrite.

The work must preserve the active Kanvas architecture decisions: WebGPU remains
the GPU backend, Ganesh and Graphite are not ported, SkSL is not dynamically
compiled, and support claims require visible reference/generated/diff evidence
or explicit diagnostics.

## Initial Targets

The requested target set is:

- `zerolinedash` - supplied score 99.986%
- `tinyanglearcs` - supplied score 99.845%
- `ctmpatheffect` - supplied score 99.54%
- `convexpaths` - supplied score 99.45%
- `teenyStrokes` - supplied score 98.22%
- `zero_control_stroke` - supplied score 97.86%
- `zeroPath` - supplied score 97.40%
- `thinconcavepaths` - supplied score 96.93%
- `dashing` - supplied score 96.63%
- `concavepaths` - supplied score 95.39%
- `strokedline_caps` - supplied score 94.81%
- `circle_sizes` - supplied score 92.07%
- `thinrects` - supplied score 91.82%
- `thinroundrects` - supplied score 91.13%
- `dashtextcaps` - supplied score 90.10%

The first sprint action after this spec is to regenerate or read the current
dashboard in the dedicated worktree. The supplied scores are planning input, not
the final baseline, because local score files observed before worktree creation
did not exactly match them.

## Worktree And Baseline

The sprint worktree is created from `origin/master` on
`codex/gm-path-pixel-perfect-sprint`. The previous worktree remains untouched.

Baseline capture uses the existing Skia integration tools:

- targeted iteration:
  `rtk ./gradlew --no-build-cache :integration-tests:skia:generateSkiaRendersFor -Pgm.family=PATH -Pgm.name=<gm> -Pgm.includeBlocking=true`
- full dashboard:
  `rtk ./gradlew --no-build-cache :integration-tests:skia:generateSkiaDashboard`
- persisted scores, when needed:
  `rtk ./gradlew --no-build-cache :integration-tests:skia:test`

Diagnostic runs may use:

`rtk ./gradlew --no-build-cache :integration-tests:skia:test -Dkanvas.gm.includeBlocking=true -Dkanvas.render.debugLevel=PIXEL`

or `TRACE` when op-level data is needed.

## Diagnostic Strategy

Use subagents for independent read-only diagnostics, then integrate fixes
centrally in the sprint worktree. Subagents should inspect artifacts and code,
identify likely root causes, and return compact evidence. They should not make
competing edits unless the main implementation plan later assigns a disjoint
write scope explicitly.

Diagnostic groups:

1. Dash and path effects:
   `zerolinedash`, `dashing`, `ctmpatheffect`, `dashtextcaps`
2. Zero-length and degenerate strokes:
   `zero_control_stroke`, `zeroPath`, `teenyStrokes`, `zerolinedash`
3. Caps and joins:
   `strokedline_caps`, `dashtextcaps`, dash stroke edge cases
4. Tiny arcs:
   `tinyanglearcs`, arc flattening and tiny sweep behavior
5. Thin rects and round rects:
   `thinrects`, `thinroundrects`, `circle_sizes`
6. Convex and concave fill coverage:
   `convexpaths`, `concavepaths`, `thinconcavepaths`

For each group, inspect reference/generated/diff first, then diagnostic
manifest data when the visual diff is not enough. The expected output from a
diagnostic pass is: affected GMs, visual symptom, likely renderer or GM-port
root cause, touched code area, suggested focused test, and risk of cross-family
regression.

## Correction Rules

Each fix should follow this flow:

1. Reproduce the current output for the target GM or group.
2. Inspect the reference, generated image, and diff.
3. Form a concrete root-cause hypothesis.
4. Add a focused unit or integration test when the behavior can be isolated
   outside a full screenshot comparison.
5. Patch renderer behavior, geometry/stroke lowering, dash expansion,
   tessellation, or the GM port only when the port itself is the confirmed
   source of the mismatch.
6. Regenerate only the affected generated PNGs during iteration.
7. Commit the stable change as a root-cause commit.

Commits should stay small and readable. Preferred commit groups are dash/path
effects, degenerate strokes, caps/joins, tiny arcs, thin rect/rrect coverage,
and convex/concave fill coverage. Do not mix unrelated renderer fixes with
dashboard-only output churn.

## Guardrails

- Do not modify `integration-tests/skia/src/test/resources/reference/**`.
- Do not hide `noReference`, `renderFailed`, or `sizeMismatch`.
- Do not turn a simplified or non-comparable GM into a false PASS.
- Generated render PNG changes are allowed only when they reflect a real
  renderer or verified GM-port correction.
- If a correction regresses a target GM, revert or rework it unless the final
  PR explains the trade-off with clear net gains.
- A modest threshold decrease is allowed only when it is targeted,
  documented in the commit and PR, and paired with visible or measured gains on
  the PATH targets. It must not mask missing references, render failures, size
  mismatches, or non-comparable rows.

## Data Flow

The sprint data flow is:

```text
Skia GM source/port
  -> SkiaGmRenderer
  -> generated-renders/path/<gm>.png
  -> reference/<gm>.png comparison
  -> dashboard data and diff images
  -> root-cause diagnosis
  -> renderer/GM-port patch
  -> targeted regenerate
  -> full dashboard/test validation
```

The final dashboard is the review artifact for visual state. The persisted
`integration-tests/skia/test-similarity-scores.properties` file is updated only
through the Skia GM test task, not by hand.

## Error Handling

If a target GM renders incorrectly because the Kanvas renderer lacks a real
capability, the sprint should expose that limitation directly rather than
claiming support through thresholds or simplified ports.

If `generateSkiaDashboard` or a targeted render fails, preserve the failure
state for diagnosis and fix the underlying failure before counting score
movement. If a subagent report conflicts with local evidence, prefer the
artifact-backed result from the sprint worktree.

If a suspected `wgsl4k` parser or generator behavior is ambiguous or surprising,
do not add a hidden workaround in Kanvas. Reduce the evidence and treat it as a
separate dependency issue.

## Final Validation

Before pushing and opening the draft PR:

1. Run:
   `rtk ./gradlew --no-build-cache :integration-tests:skia:generateSkiaDashboard`
2. If scores are expected in the PR, run:
   `rtk ./gradlew --no-build-cache :integration-tests:skia:test`
3. Verify reference images were not changed:
   `rtk git status --short -- integration-tests/skia/src/test/resources/reference`
4. Inspect the 15 target scores and summarize improvements, regressions,
   threshold changes, and remaining limitations.
5. Push `codex/gm-path-pixel-perfect-sprint`.
6. Create a draft PR with artifact and score notes.

## Non-Goals

- No Ganesh or Graphite port.
- No dynamic SkSL compiler, IR, or VM.
- No broad rewrite of the renderer outside the PATH root causes encountered.
- No reference PNG updates.
- No dashboard-only cosmetic work unless needed to inspect or report the PATH
  sprint evidence.
