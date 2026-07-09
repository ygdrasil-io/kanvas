# GM Fidelity Wave 3 Score-First Design

Date: 2026-07-09
Branch: `codex/gm-fidelity-wave-3-score-first`
Stacked base: `codex/master-after-path-gm` / PR #2027

## Purpose

Launch a broad score-first GM fidelity wave after Path GM Wave 2. The goal is
to increase visible Skia GM dashboard quality by selecting the largest
remaining unmatched-pixel opportunities across several families, while keeping
each work group independently reviewable.

This wave is ambitious by scope but conservative by evidence. It does not
modify Skia reference images, does not lower thresholds, does not hide
unsupported rows, and does not claim broad Skia parity from selected fixes.

## Current Context

Wave 2 improved selected `PATH` rows and produced PR #2027. The current
dashboard still contains large unmatched-pixel candidates outside and inside
`PATH`:

- `IMAGE` / bitmap / shader sampling rows such as `pictureshader*`,
  `imageshader_tinyscale`, `bmp_filter_quality_repeat`, `tilemode_decal`,
  `coordclampshader`, and `scaled_tilemodes`.
- `COMPOSITE` / color filter / blend rows such as `transparency_check`,
  `draw_image_set_rect_to_rect`, `tablecolorfilter`, `filterfastbounds`,
  `hslcolorfilter`, `xfermodes`, `aaxfermodes`, `modecolorfilters`, and
  `srgb_colorfilter`.
- `CLIP` and `PATH` residual rows such as `complexclip4_aa`,
  `complexclip4_bw`, `convex_poly_clip`, `complexclip_*`, `nonclosedpaths`,
  `filltypespersp`, `dashing5_aa`, and `trimpatheffect`.
- `RUNTIME_EFFECT` rows including current dashboard fail rows
  `rtif_unsharp`, `runtimefunctions`, and `unsharp_rt`, plus selected 0%
  registered-effect candidates.

Rows with `noReference`, `renderFailed`, or `sizeMismatch` remain visible
inventory. They are not counted as visual fixes unless a later change produces
real reference/render/diff evidence.

## Strategy

Use a score-first multi-family strategy with independent "strike team" groups:

1. Rank candidates by unmatched pixels, dashboard status, PM value, and root
   cause clarity.
2. Start with the highest expected impact groups, but limit each group to 3-8
   GMs before expanding.
3. Prefer reusable renderer mechanisms when evidence shows a shared root cause.
4. Keep fix commits, regression tests, generated PNGs, and score updates
   separate when practical.
5. Treat failed hypotheses as useful diagnostics, not permission to broaden
   scope indefinitely.

The wave may produce multiple commits or several PRs. Each group must be able
to stand alone with evidence and a rollback boundary.

## Work Group A: Image, Bitmap, And Shader Sampling

Target rows:

- `pictureshader`
- `pictureshader_alpha`
- `pictureshader_localwrapper`
- `imageshader_tinyscale`
- `bmp_filter_quality_repeat`
- `tilemode_decal`
- `coordclampshader`
- `scaled_tilemodes`

Hypotheses:

- image shader tile modes (`clamp`, `repeat`, `mirror`, `decal`) are missing,
  incomplete, or not propagated through GPU material descriptors;
- shader local matrices or picture-shader local wrappers are lost in lowering;
- nearest/linear sampling and half-pixel coordinate semantics differ from Skia;
- picture shader rows are falling back to a placeholder path instead of a
  bounded picture or image sampling route.

Acceptance:

- at least one selected image/sampling row improves with before/after evidence,
  or the group emits stable diagnostics for unsupported picture shader and
  sampler scopes;
- tests isolate the changed sampling, matrix, or tile-mode behavior;
- no codec/font/dependency substitute is introduced just to clear a GM row.

## Work Group B: Composite, Color Filters, And Blend

Target rows:

- `transparency_check`
- `draw_image_set_rect_to_rect`
- `tablecolorfilter`
- `filterfastbounds`
- `hslcolorfilter`
- `xfermodes`
- `aaxfermodes`
- `modecolorfilters`
- `srgb_colorfilter`

Hypotheses:

- premul/unpremul handling differs between CPU and GPU routes;
- alpha compositing or `SrcOver` edge cases are incomplete;
- table color filters, mode color filters, HSL, or sRGB conversions are
  represented but not applied with Skia-compatible math;
- `drawImageSet` rect mapping, clipping, or fast-bounds handling drops pixels.

Acceptance:

- behavior changes are covered by narrow unit tests before global blend or
  color-filter semantics change;
- improvements do not regress high-confidence composite rows already above
  threshold;
- unsupported advanced blend/color modes remain visible with stable refusal
  reasons.

## Work Group C: Clip And Path Residuals

Target rows:

- `complexclip4_aa`
- `complexclip4_bw`
- `convex_poly_clip`
- `complexclip_*`
- `nonclosedpaths`
- `filltypespersp`
- `dashing5_aa`
- `trimpatheffect`

Hypotheses:

- complex clip stacks need stable bounded support or explicit refusal by root
  cause, not accidental partial rendering;
- AA clip coverage and convex clip lowering are incomplete;
- perspective path fill/clip requires a separate bounded path because current
  prepared fill routes correctly refuse perspective;
- residual dash/trim path-effect gaps are either GM-port omissions or bounded
  renderer path-effect behavior.

Acceptance:

- complex clip work may end in diagnostics/refusals if full support would imply
  broad clip-stack parity;
- path-effect changes require targeted reproduction and before/after images;
- existing passing dash/path rows remain passing unless a measured tradeoff is
  explicitly approved.

## Work Group D: Runtime Effect Cleanup

Target rows:

- `rtif_unsharp`
- `runtimefunctions`
- `unsharp_rt`
- `runtimecolorfilter`
- `spiral_rt`
- `rippleshader`

Hypotheses:

- the three current dashboard fail rows may be near-threshold numerical or
  descriptor mismatches;
- selected registered descriptors may lack CPU/WGSL parity or reflected uniform
  handling;
- arbitrary Skia/SkSL runtime shader rows must be refused explicitly rather
  than treated as supported.

Acceptance:

- only registered Kanvas descriptors with Kotlin CPU behavior and
  parser-validated WGSL GPU implementations can become support claims;
- arbitrary SkSL input remains out of scope and must get a stable diagnostic;
- fixing a fail row must not weaken the runtime-effect architecture rules.

## Evidence Flow

Each group follows the same flow:

1. Extract baseline rows from
   `integration-tests/skia/build/reports/skia-gm-dashboard/data/gms.json`.
2. Capture target row screenshots or diff artifacts where useful.
3. Add focused tests before changing shared renderer semantics when practical.
4. Implement one mechanism-scoped fix or one explicit diagnostic boundary.
5. Regenerate targeted renders for changed GMs.
6. Run full dashboard at group boundaries or before review.
7. Update `test-similarity-scores.properties` only after the score task
   justifies it.
8. Request external review for shared renderer mechanisms before PR.

## Commit Discipline

Preferred commit shapes:

- `Diagnose <family> GM candidates`
- `Add <mechanism> regression tests`
- `Fix <mechanism> rendering`
- `Document <family> unsupported diagnostics`
- `Regenerate <family> GM renders and scores`

Generated PNGs and score updates should not be mixed into the first behavioral
commit unless the local workflow makes separation impractical.

## Guardrails

- Do not modify `integration-tests/skia/src/test/resources/reference/**`.
- Do not lower `minSimilarity` thresholds to claim progress.
- Do not hide `noReference`, `renderFailed`, `sizeMismatch`, or unsupported
  rows.
- Do not port Ganesh or Graphite.
- Do not rebuild or embed a dynamic SkSL compiler, IR, or VM.
- Keep WebGPU as the GPU backend.
- Keep `SkRuntimeEffect` as a compatibility facade backed only by registered
  Kotlin/WGSL implementations.
- Do not introduce font/codec/dependency substitutes to clear historical rows.
- Do not mix unrelated renderer refactors into this wave.

## Stop Rules

- If a group does not produce score movement after one or two tested
  hypotheses, stop and document the diagnostic.
- If a shared fix regresses unrelated high-confidence rows, isolate or revert
  that sub-change before continuing.
- If evidence points to a deeper architecture gap, split it into a new design
  instead of expanding this wave.
- If any Skia reference image changes, treat it as an immediate blocker.

## Verification Plan

Per group:

- focused unit or integration tests for touched renderer or GM code;
- targeted render generation for changed GMs;
- dashboard row extraction for exact before/after scores;
- `git diff --name-only -- integration-tests/skia/src/test/resources/reference`
  must be empty;
- `git diff --check` must be clean.

At PR boundary:

- full relevant renderer test set;
- `./gradlew :integration-tests:skia:generateSkiaDashboard`;
- `./gradlew :integration-tests:skia:test`;
- external code review;
- PR body with before/after table, unchanged-reference guardrail, and residual
  diagnostics.

## First Implementation Plan Candidate

Start with Work Group A because it has the largest unmatched-pixel candidates
and likely reusable mechanisms:

1. Diagnose `pictureshader*`, `imageshader_tinyscale`,
   `bmp_filter_quality_repeat`, and `tilemode_decal`.
2. Determine whether the first fix should target tile-mode propagation, local
   matrix handling, sampling coordinates, or explicit picture-shader refusal.
3. Add the smallest renderer or GM tests that prove that root cause.
4. Implement one bounded fix, regenerate targeted renders, then decide whether
   to continue inside Group A or move to Group B.
