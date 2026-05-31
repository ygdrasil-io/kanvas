# Target: Rendering Conformance And Performance Platform

Date: 2026-05-28
Status: Proposed
Parent target: `.upstream/target/high-performance-wgsl-pipeline-target.md`
Input evidence:

- `reports/wgsl-pipeline/scenes/data/scenes.json`
- `reports/wgsl-pipeline/2026-05-28-m40-performance-regression-closeout.md`
- `.upstream/specs/wgsl-pipeline/10-scene-evidence-dashboard.md`

## Purpose

The WGSL/WebGPU MVP proved that Kanvas can expose CPU/GPU scene evidence with
references, diffs, route diagnostics, fallback reasons, and early performance
fields. The next big target is to turn that static evidence into a conformance
and performance platform.

The target is not just more dashboard rows. It is a generated evidence system
that makes every rendering support claim reviewable by product, engineering,
and release owners.

## Big Target

Kanvas should provide a CPU/WebGPU rendering conformance and performance
platform where promoted scenes are generated from tests, compared against
Skia/upstream or CPU-oracle references, annotated with route diagnostics, and
tracked over time for correctness and performance regressions.

The platform must answer five questions for every promoted scene:

- what reference is being compared;
- what CPU route produced the oracle or CPU lane;
- what GPU route rendered or refused the scene;
- what visual/statistical result was produced;
- whether the result is support, a tracked gap, or expected unsupported scope.

## PM Readiness

Current Post-MVP Big Target readiness for MEP: 80%.

This is a PM readiness score for the full target, not an effort estimate and not
the completion state of the latest Linear sprint. M41-M47 completed the evidence
foundation, M48 expanded representative Skia integration breadth, M49 turned the
dashboard into a release-oriented readiness gate candidate, and M50 converted
that candidate plus the front/font specs into executable evidence. The platform
is still not complete MEP scope because release-blocking performance thresholds,
broad Skia parity, broad font/text coverage, and dependency-gated codec gaps
remain outside the selected evidence rows.

| Area | Weight | Current state | Progress |
|---|---:|---|---:|
| Evidence foundation | 25% | M41-M50 complete: generated dashboard, 26 generated rows, 0 tracked-gap, 0 fail, and a release gate report. | 100% |
| Skia integration coverage | 25% | M50 raises adapter-backed proof to 17 rows and adds first selected font/text evidence without broad support claims. | 65% |
| CI and release gates | 20% | `wgsl_scene_dashboard_release_gate` runs `pipelineSceneDashboardGate`, warning-only performance output, and PM bundle generation with archived reports. | 85% |
| Performance readiness | 15% | `pipelinePerformanceTrendWarnings` emits owner, baseline, environment, variance, quarantine, and rollback policy; thresholds are not release gates. | 60% |
| PM demo and reporting workflow | 15% | `pipelinePmBundle` includes dashboard, data, artifacts, limitations, gate output, front QA, screenshot paths, and performance warnings. | 85% |

The resulting weighted readiness is 80%. Evidence-hardening through M47, M48
coverage expansion, M49 readiness gating, and M50 acceleration are complete for
their selected evidence sets. These are still only parts of the larger MEP
target.

| Area | Weight | M50 target | Required movement |
|---|---:|---:|---|
| Evidence foundation | 25% | 100% | Preserve generated dashboard semantics, zero `tracked-gap`, zero `fail`, and stable fallback policy. |
| Skia integration coverage | 25% | 65% | Reach at least 14 adapter-backed rows and add first generated font/text evidence without broad unsupported claims. |
| CI and release gates | 20% | 85% | Make `pipelineSceneDashboardGate`, PM bundle validation, and non-blocking inventory ownership release-visible. |
| Performance readiness | 15% | 60% | Emit automated warning-only trend evidence with baseline owner, variance, quarantine, and rollback policy. |
| PM demo and reporting workflow | 15% | 85% | Add accepted front/browser/accessibility evidence, image inspection, route/reference notices, and portable PM bundle links. |

This target mix has landed for M50 and is the current score.

Before MEP, Kanvas still needs:

- broader Skia integration scene coverage;
- generated evidence as the default path for new support claims;
- performance trends with approved release thresholds;
- hosted or release-owned PM reporting beyond the portable local bundle;
- broader dependency-gated text/font/glyph/emoji/codec deliveries, not substitutes,
  followed by generated font scene evidence.

## Evidence Levels

| Level | Meaning | May claim support? |
|---|---|---|
| Static registry | Hand-authored or checked-in `scenes.json` plus artifacts. | No, unless backed by linked test evidence. |
| Generated conformance | Test run generates image, diff, route, and stats artifacts. | Yes, for the exact scene contract. |
| Adapter-backed GPU | GPU artifacts were produced on a named adapter/backend. | Yes, for GPU support when thresholds pass. |
| Measured performance | Benchmark harness writes host/JDK/adapter/baseline timing. | Yes, for performance trend claims. |
| Estimated performance | Static seed metrics used for dashboard shape. | No, informational only. |

## Support Claim Rules

A scene may be `pass` only when:

- reference artifact exists;
- CPU evidence exists;
- GPU evidence exists when the scene is GPU-eligible;
- CPU/GPU route diagnostics exist;
- diff and stats artifacts exist;
- fallback reason is `none` for supported GPU rows.

A scene must be `tracked-gap` when:

- at least one route is known, but a required capture, diff, metric, or adapter
  artifact is missing;
- the limitation is temporary and has a concrete closure path.

A scene must be `expected-unsupported` when:

- GPU intentionally refuses the scene;
- the refusal has a stable fallback reason;
- the unsupported breadth remains useful evidence for future planning.

Do not mark a rendering family supported from route diagnostics alone. A support
claim needs rendered evidence or a documented CPU-only non-goal.

## Non-Goals

- Do not port Ganesh or Graphite.
- Do not rebuild Skia's SkSL compiler, IR, or VM.
- Do not turn estimated metrics into performance gates.
- Do not hide unsupported rows by removing them from inventory or dashboard
  evidence.
- Do not broaden required GPU smoke without adapter-backed evidence.
- Do not add short-lived font/codec substitutes for dependency-gated gaps.

## Proposed Milestones

| Milestone | Name | Target outcome |
|---|---|---|
| M41 | Generated Conformance Dashboard | Dashboard rows are produced from test outputs instead of only static registry artifacts. |
| M42 | Adapter-Backed P0 GPU Captures | Existing P0 `tracked-gap` rows become pass or intentionally scoped expected unsupported with adapter-backed evidence. |
| M43 | Real Benchmark Harness | CPU/GPU `performanceTrend` fields are written by benchmark runs with host/JDK/adapter metadata. |
| M44 | First Real Path AA Family Promotion | One narrow Path AA family moves from expected unsupported to rendered GPU support with CPU/GPU/reference evidence. |
| M45 | Image-Filter DAG Subset V1 | A bounded image-filter DAG subset renders through explicit pre-pass/layer contracts and dashboard evidence. |
| M46 | Generated Evidence Expansion | Convert the next high-value static dashboard rows to generated evidence while keeping zero tracked gaps and zero failing support claims. |
| M47 | Remaining Static Evidence Hardening | Convert remaining static pass rows to generated evidence and keep Path AA expected-unsupported rows explicit as policy evidence. |
| M48 | MEP Scene Coverage Expansion | Add representative P0/P1 Skia scene breadth across paint, clip, transform, bitmap, gradient, Path AA, and image-filter planning rows. |
| M49 | MEP Readiness Gate Toward 60% | Completed: promoted the generated dashboard into a CI gate candidate, added a portable PM artifact bundle, defined non-blocking performance trend gates, and broadened adapter-backed proof enough to justify a 60% PM readiness score. |
| Spec split | Front Evidence Experience | Draft spec pack added for dashboard UX, image inspection, PM bundle workflow, accessibility, and quality gates. Spec-only; no score movement. |
| Spec split | Font And Text Evidence | Draft spec pack added for pure Kotlin OpenType, shaping, glyph rendering, color fonts, emoji, and font conformance. Spec-only; dependency-gated rows remain gated. |
| M50 | MEP Readiness Acceleration Toward 80% | Completed: converted M49 gate candidate and front/font specs into required CI ownership, front QA evidence, broader adapter-backed captures, first generated font/text scene pack, performance warning automation, and score recalculation. |

## Current Baseline

The M40 static dashboard started with:

- 11 scene rows;
- 7 pass;
- 2 tracked-gap;
- 2 expected-unsupported;
- 0 fail;
- 5 CPU performance rows marked `estimated`;
- 5 GPU/cache performance rows marked `estimated`.

The two current `tracked-gap` P0 rows are:

- `solid-rect`;
- `analytic-aa-convex`.

Both have route evidence but are missing adapter-backed GPU render captures in
the current dashboard evidence.

After M50, the merged dashboard export has:

- 28 scene rows;
- 21 pass;
- 0 tracked-gap;
- 7 expected-unsupported;
- 0 fail;
- 26 generated evidence rows;
- 2 static evidence rows;
- 17 adapter-backed rows;
- tag aggregates for `feature.*`, `maturity.*`, and `risk.*`.

M48-M50 support, refusal, and readiness evidence is linked from:

- `reports/wgsl-pipeline/2026-05-31-m48-mep-skia-scene-taxonomy.md`;
- `reports/wgsl-pipeline/2026-05-31-m48-p0-p1-scene-pack-selection.md`;
- `reports/wgsl-pipeline/2026-05-31-m48-paint-blend-transform-generated-evidence.md`;
- `reports/wgsl-pipeline/2026-05-31-m48-bitmap-gradient-generated-evidence.md`;
- `reports/wgsl-pipeline/2026-05-31-m48-expected-unsupported-breadth-evidence.md`;
- `reports/wgsl-pipeline/2026-05-31-m49-dashboard-gate-invariants.md`;
- `reports/wgsl-pipeline/2026-05-31-m49-ci-dashboard-validation-task.md`;
- `reports/wgsl-pipeline/2026-05-31-m49-portable-pm-bundle.md`;
- `reports/wgsl-pipeline/2026-05-31-m49-adapter-backed-expansion.md`;
- `reports/wgsl-pipeline/2026-05-31-m49-performance-trend-gate-contract.md`;
- `reports/wgsl-pipeline/2026-05-31-m49-mep-release-readiness-checklist.md`;
- `reports/wgsl-pipeline/2026-05-31-m49-sprint-review.md`.
- `reports/wgsl-pipeline/2026-05-31-m50-ci-release-gate.md`;
- `reports/wgsl-pipeline/2026-05-31-m50-front-evidence-gate.md`;
- `reports/wgsl-pipeline/2026-05-31-m50-adapter-backed-expansion-v2.md`;
- `reports/wgsl-pipeline/2026-05-31-m50-font-text-evidence-pack.md`;
- `reports/wgsl-pipeline/2026-05-31-m50-performance-warning-gate.md`;
- `reports/wgsl-pipeline/2026-05-31-m50-mep-release-readiness-checklist.md`;
- `reports/wgsl-pipeline/2026-05-31-m50-sprint-review.md`.

Draft follow-up spec packs:

- `.upstream/specs/front/README.md`;
- `.upstream/specs/font/README.md`.
- `reports/wgsl-pipeline/2026-05-31-m50-80-readiness-sprint-plan.md`.

The two static rows remain deliberate Path AA policy sentinels, not unowned
conversion debt. M48 adds three generated expected-unsupported breadth rows so
future planning sees the high-value unsupported surface area instead of hiding
it.

The current expected unsupported rows are:

- `path-aa-stroke-outline-fallback` with
  `coverage.stroke-outline-edge-count-exceeded`;
- `path-aa-edge-budget-boundary` with `coverage.edge-count-exceeded`.
- `path-aa-convexpaths-edge-budget` with `coverage.edge-count-exceeded`;
- `path-aa-dashing-edge-budget` with `coverage.edge-count-exceeded`;
- `image-filter-crop-nonnull-prepass-required` with
  `image-filter.crop-input-nonnull-prepass-required`.
- `font-emoji-color-glyph-refusal` with
  `font.color-glyph-emoji-unsupported`;
- `font-complex-shaping-refusal` with
  `font.complex-shaping-requires-explicit-shaper`.

## M49 Closeout

M49 was not treated as "more rows only". Its purpose was to make the current
dashboard usable as a MEP readiness gate.

M49 moved Post-MVP Big Target readiness from 40% to 60% by improving all of
these areas:

- CI and release gates moved from 10% to 60% through a validation task
  that fails on support-claim regressions, duplicate ids, missing generated
  artifacts, unsupported rows without stable fallback reasons, and accidental
  `tracked-gap` / `fail` rows;
- PM demo and reporting workflow moved from 15% to 45% through a
  portable bundle with dashboard HTML, scene JSON, generated result JSON,
  artifacts, manifest, known limitations, and a repeatable serve command;
- performance readiness moved from 15% to 35% through a non-blocking
  trend gate contract for measured payloads, including host/JDK/backend/adapter
  eligibility and variance policy;
- Skia integration coverage moved from 35% to 45% through additional
  adapter-backed proof for selected high-value pass rows, not broad new family
  claims.

The supporting sprint plan and closeout are:

- `reports/wgsl-pipeline/2026-05-31-m49-60-readiness-sprint-plan.md`;
- `reports/wgsl-pipeline/2026-05-31-m49-sprint-review.md`.

## Front And Font Spec Split

After M49, the target gained two draft spec packs:

- `.upstream/specs/front/`, covering the evidence dashboard UX, PM bundle,
  image inspection, filters, accessibility, and front quality gates;
- `.upstream/specs/font/`, covering portable OpenType, `SkFont`,
  `SkTypeface`, `SkFontMgr`, explicit `SkShaper`, glyph rendering, color
  fonts, emoji, and font conformance.

These specs now have selected M50 implementation evidence. They still do not
claim broad front-hosting, font, text, emoji, shaping, SDF, LCD, glyph-mask, or
codec completion beyond the generated rows and QA artifacts named in the M50
reports.

## M50 Closeout

M50 moved the target from 60% to 80% by landing five executable lanes:

- required CI ownership for `pipelineSceneDashboardGate`, PM bundle validation,
  and non-blocking inventory visibility;
- front evidence gates for image inspection, filters, route/reference notices,
  desktop/mobile browser screenshots, accessibility, and PM bundle attachment;
- adapter-backed scene expansion from 7 rows to at least 14 rows across
  multiple Skia-relevant families;
- first generated font/text evidence pack, with selected pass rows from the
  existing pure Kotlin OpenType/simple text path and explicit
  expected-unsupported rows for unsupported glyph/text surfaces;
- automated warning-only performance trends with baseline owner, variance
  policy, quarantine, rollback notes, and refreshed measured payloads.

M50 does not claim complete MEP, broad Skia parity, broad font/emoji/shaping
support, or release-blocking performance thresholds.

## Agent Execution Policy

Agents working on M41+ must:

- read `.upstream/specs/wgsl-pipeline/11-conformance-dashboard-generation.md`
  before modifying dashboard generation or scene evidence;
- read `.upstream/specs/wgsl-pipeline/12-benchmark-harness-and-performance-gates.md`
  before modifying performance fields, benchmark output, or regression gates;
- read `.upstream/specs/front/README.md` before modifying dashboard UX,
  artifact browsing, PM bundle behavior, or front quality gates;
- read `.upstream/specs/font/README.md` before modifying font, text, glyph,
  shaping, color-font, emoji, or font conformance behavior;
- keep `reports/wgsl-pipeline/scenes/data/scenes.json` machine-readable and
  deterministic;
- keep fallback reasons stable and visible;
- report whether evidence is static, generated, adapter-backed, measured, or
  estimated;
- link Linear issues, PRs, commands, and generated artifacts in closeout
  comments.

## Validation

Any milestone that changes dashboard generation or scene evidence must run:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

Milestones that claim runtime support or performance must also run the owning
test or benchmark task and link the generated raw artifacts.
