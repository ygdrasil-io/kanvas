# Post-MVP Conformance Backlog

Status: Proposed
Date: 2026-05-28
Target: `.upstream/target/rendering-conformance-performance-target.md`
Input: `reports/wgsl-pipeline/2026-05-28-m40-performance-regression-closeout.md`

## Purpose

This backlog starts after the WGSL/WebGPU MVP and M36-M40 static dashboard
closeout. It should move Kanvas from static evidence toward generated
conformance, adapter-backed captures, measured performance, and carefully
promoted rendering families.

## Baseline

Current M40 dashboard state:

| Signal | Count | Interpretation |
|---|---:|---|
| Scene rows | 11 | P0, M37, M38, and M39 selected P1 rows. |
| `pass` | 7 | CPU/GPU/reference evidence exists for selected rows. |
| `tracked-gap` | 2 | P0 route evidence exists, but GPU capture is not attached. |
| `expected-unsupported` | 2 | Path AA breadth remains explicit and scoped. |
| `fail` | 0 | No current dashboard row is a failing support claim. |
| CPU perf `estimated` | 5 | Static informational metrics, not benchmark gates. |
| GPU perf `estimated` | 5 | Static informational metrics, not benchmark gates. |

## Milestones

| Milestone | Goal | Definition of Done |
|---|---|---|
| M41 Generated Conformance Dashboard | Generate dashboard rows from test outputs. | At least three scenes are emitted by a test/report task with reference, CPU, GPU or expected-unsupported, route JSON, diff, and stats artifacts. Static-only rows remain labelled as such. |
| M42 Adapter-Backed P0 GPU Captures | Close current P0 tracked gaps. | `solid-rect` and `analytic-aa-convex` either have adapter-backed GPU captures and diffs or a documented reason they remain tracked-gap with follow-up tickets. |
| M43 Real Benchmark Harness | Replace selected estimated metrics with measured benchmark output. | At least two CPU and two GPU rows have measured host/JDK/backend/adapter metadata, warm/cold context, baseline name, and regression label. Estimated fields remain non-gating. |
| M44 First Real Path AA Family Promotion | Promote one narrow Path AA family to rendered support. | One selected family has CPU/GPU/reference artifacts, route diagnostics, passing thresholds, and reduced expected-unsupported inventory without weakening fallback diagnostics. |
| M45 Image-Filter DAG Subset V1 | Extend image-filter support beyond the selected M38 pre-pass. | A bounded multi-node or multi-family image-filter subset renders through explicit pre-pass/layer contracts with dashboard evidence and stable out-of-scope diagnostics. |
| M46 Generated Evidence Expansion | Convert high-value static pass rows to generated evidence. | At least five additional dashboard rows are generated from test/report outputs, merged export remains 0 tracked-gap / 0 fail, and remaining static rows are explicitly listed with owners. |
| M47 Remaining Static Evidence Hardening | Convert remaining static pass rows and keep Path AA policy rows visible. | Remaining static pass rows are generated evidence, expected-unsupported Path AA rows remain explicit policy sentinels, and the merged export remains 0 tracked-gap / 0 fail. |
| M48 Skia Scene Coverage Expansion | Expand from clean dashboard evidence to a representative MEP scene pack. | Add 8-12 selected P0/P1 rows across multiple Skia-relevant families, keep unsupported breadth explicit, and update PM readiness without introducing tracked gaps or failing support claims. |
| M49 MEP Readiness Gate Toward 60% | Promote the dashboard from local evidence into release-oriented readiness gates and PM packaging. | Add gate invariant spec, CI validation task, portable PM artifact bundle, adapter-backed expansion to at least six rows, non-blocking performance trend gate contract, MEP release checklist, and sprint review. The 60% readiness score may be claimed only if all lanes land with merged evidence. |
| M50 MEP Readiness Acceleration Toward 80% | Convert M49 gate candidate and front/font specs into executable release evidence. | Required CI ownership, front/browser/accessibility gates, at least 14 adapter-backed rows, first generated font/text evidence pack, automated warning-only performance trends, and sprint review score recalculation. The 80% score may be claimed only if every lane lands with artifacts. |
| M51 Skia GM Inventory Coverage | Done: full Skia GM/sample surface is visible before broad scene promotion. | Generated deterministic inventory JSON/Markdown for 437 upstream GM C++ files and 751 Kotlin GM sources, classified 802 inventory rows, exposed inventory in the PM bundle, added inventory validation, and produced a 34-row M52+ promotion candidate backlog without changing support claims. |
| M52 GM Inventory Promotion Pack | Done: selected M51 candidates become generated dashboard evidence. | Promoted 10 inventory-derived generated rows, documented selected/promoted/rejected candidates, kept 0 tracked-gap and 0 fail, exposed M52 counters in the PM bundle, and raised readiness to 85% without broad Skia GM support claims. |
| M53 GM Feature Promotion Pack v2 | Done: a second selected GM feature pack becomes generated dashboard evidence. | Promoted 12 inventory-derived generated rows across five visual families, documented selected/promoted/rejected candidates, kept 0 tracked-gap and 0 fail, exposed M53 counters in the PM bundle, and raised readiness to 90% without broad Skia GM support claims. |
| M54 Hard Feature Depth Pack | Done: selected hard feature rows deepen generated evidence. | Promoted 10 inventory-derived generated rows across bounded image-filter v2, Path AA / clip depth, and runtime / paint composition, documented selected/promoted/rejected candidates, attached 2 warning-only measured performance payloads, kept 0 tracked-gap and 0 fail, exposed M54 counters in the PM bundle, and raised readiness to 93% without broad support claims. |
| M55 Performance Gate Candidate | Done: warning-only performance evidence becomes release-readable candidate evidence. | Selected 7 representative rows, emitted non-blocking pass/deferred/warn/fail-candidate candidate output, documented baseline payload decisions plus quarantine/rebaseline/rollback policy, exposed M55 counters in the PM bundle, kept dashboard support counters unchanged, and raised readiness to 95% without enabling a release-blocking performance gate. |
| M56 Unsupported-to-Pass Feature Scene Pack | Partial: one prior expected-unsupported row becomes generated pass evidence. | Promoted `m53-sweep-gradient-clamp` by correcting it to `skia-gm-sweepgradient`, kept two-point conical, image-filter DAG, and Path AA/clip blockers explicit, exposed M56 evidence in the PM bundle, and raised readiness to 96% instead of the 97% stretch target. |
| M57 Path AA / Clip Micro-Promotion | Done: one bounded AA clip subcase becomes generated pass evidence. | Promoted `m57-aaclip-bounded-grid` as a generated `skia-gm-aaclip` bounded-grid support row, kept edge-budget, dash, hairline, stroke-outline, and complex-clip blockers explicit, exposed M57 evidence in the PM bundle, and raised readiness to 98% without broad Path AA claims. |
| M58 Performance Release Gate | Done for measured lanes: selected measured performance evidence becomes release-blocking. | Added `pipelinePerformanceReleaseGate`, selected the four measured M55 rows for CPU and GPU/cache thresholds, reported three not-measured rows without blocking, exposed M58 counters in the PM bundle, and raised readiness to 99% without promoting estimated or missing metrics. |

## M41 Seed Tickets

- Define generated scene result schema compatible with
  `reports/wgsl-pipeline/scenes/data/scenes.json`.
- Add an exporter that materializes image/diff/route/stats artifacts from test
  outputs.
- Convert `bitmap-rect-nearest`, `crop-image-filter-nonnull-prepass`, and one
  M39 P1 row to generated evidence.
- Keep the static dashboard readable when generated artifacts are absent.

## M42 Seed Tickets

- Capture adapter-backed GPU output for `solid-rect`.
- Capture adapter-backed GPU output for `analytic-aa-convex`.
- Update P0 closeout evidence so tracked gaps either close or remain explicitly
  justified.

## M43 Seed Tickets

- Define benchmark JSON payload and environment metadata.
- Add CPU measured metrics for two stable P1 rows.
- Add GPU measured metrics for two stable P1 rows.
- Add regression label policy and non-gating CI budget note.

## M44 Seed Tickets

- Select one Path AA family from the M37 ranking.
- Implement or route the selected family without raising the global edge budget
  by default.
- Add dashboard row and inventory before/after report.
- Keep broad Path AA suites expected unsupported until scoped.

## M45 Seed Tickets

- Specify the bounded image-filter DAG subset.
- Implement explicit intermediate texture/layer ownership.
- Add CPU/GPU/reference dashboard rows.
- Keep unsupported DAG shapes visible through stable diagnostics.

## M46 Seed Tickets

- Convert `solid-rect` to generated evidence using the adapter-backed P0
  capture path.
- Convert `analytic-aa-convex` to generated evidence while preserving the
  composited `SrcOver` AA oracle contract.
- Convert `path-aa-stroke-primitive` to generated evidence and preserve the M44
  inventory delta evidence.
- Convert `image-filter-compose-cf-matrix-transform` to generated evidence and
  preserve pre-pass/intermediate texture route diagnostics.
- Convert one measured-performance scene, preferably `src-over-stack` or
  `bitmap-shader-local-matrix`, without losing M43 measured payload links.
- Publish a sprint review that reports generated/static counts, remaining
  static rows, tag aggregates, support status counts, and validation commands.

## Execution Rules

- Do not reopen MVP acceptance criteria.
- Do not treat static seed metrics as measured performance.
- Do not mark a scene pass without rendered artifacts and route diagnostics.
- Do not hide expected unsupported rows from dashboard or inventory evidence.
- Keep Linear tickets linked to specs, reports, validation commands, and raw
  artifacts.

## M41 Outcome

Closed on 2026-05-28 by `reports/wgsl-pipeline/2026-05-28-m41-generated-dashboard-closeout.md`.

Final M41 dashboard evidence:

| Signal | Count |
|---|---:|
| Total scene rows | 11 |
| Generated rows | 3 |
| Static rows | 8 |
| `pass` | 7 |
| `tracked-gap` | 2 |
| `expected-unsupported` | 2 |
| `fail` | 0 |

Generated rows:

- `bitmap-rect-nearest`
- `crop-image-filter-nonnull-prepass`
- `linear-gradient-rect`

M42 starts from two explicit P0 tracked gaps: `solid-rect` and `analytic-aa-convex`.

## M42 Outcome

Closed on 2026-05-28 by `reports/wgsl-pipeline/2026-05-28-m42-adapter-backed-p0-capture-closeout.md`.

M42 attempted both P0 adapter-backed captures:

| Scene | M41/M42 start | M42 result | Follow-up |
|---|---|---|---|
| `solid-rect` | `tracked-gap` | `pass` with adapter-backed WebGPU capture on Apple M2 Max, 100.0% similarity, `fallbackReason=none`. | None. |
| `analytic-aa-convex` | `tracked-gap` | Initially `tracked-gap` with adapter-backed WebGPU capture on Apple M2 Max, 90.6% similarity, `fallbackReason=none`, `edgeBudgetReason=not coverage.edge-count-exceeded`; resolved by GRA-222 to `pass` after regenerating the CPU oracle as composited `SrcOver` AA edge pixels with 100.0% similarity. | None. |

Final M42 dashboard state after GRA-222 follow-up resolution:

| Signal | Count |
|---|---:|
| `pass` | 6 static rows, plus generated rows merged at export time |
| `tracked-gap` | 0 |
| `expected-unsupported` | 2 |
| `fail` | 0 |

GRA-222 resolved the `analytic-aa-convex` oracle/policy gap by regenerating the
CPU oracle as composited `SrcOver` AA edge pixels. The row remains distinct from
broad Path AA edge-budget refusals: `edgeBudgetReason=not
coverage.edge-count-exceeded`.

## M43 Policy Note

GRA-209 defines the M43 baseline and regression policy in
`reports/wgsl-pipeline/2026-05-28-m43-baseline-regression-policy.md` and
`.upstream/specs/wgsl-pipeline/12-benchmark-harness-and-performance-gates.md`.

The measured CPU and GPU/cache rows created by GRA-207 and GRA-208 remain
`reporting-only`. No required CI performance gate exists until a follow-up ticket
adds explicit budget, host/JDK/backend/adapter eligibility, variance threshold,
flake/quarantine handling, rollback rules, and a baseline owner.

## M43 Outcome

Closed on 2026-05-28 by `reports/wgsl-pipeline/2026-05-28-m43-real-benchmark-harness-closeout.md`.

M43 produced measured benchmark payloads for two stable rows:

| Scene | CPU | GPU/cache |
|---|---|---|
| `src-over-stack` | `measured`, baseline `m43-cpu-measured-local` | `measured`, baseline `m43-gpu-cache-measured-local`, adapter `Apple M2 Max` |
| `bitmap-shader-local-matrix` | `measured`, baseline `m43-cpu-measured-local` | `measured`, baseline `m43-gpu-cache-measured-local`, adapter `Apple M2 Max` |

All M43 measured metrics remain `reporting-only`; no required CI performance
gate was added. Future gate activation requires explicit CI budget,
host/JDK/backend/adapter eligibility, variance threshold, flake/quarantine
handling, rollback rules, and a baseline owner.

## M44 Selection Note

GRA-211 selects `StrokeRectGM` and `StrokeCircleGM` as the first real Path AA
family promotion target. Expected inventory effect is four rows removed from
`coverage.edge-count-exceeded` if the bounded primitive-stroke route succeeds;
all broad Path AA suites remain expected unsupported until separately scoped.

## M44 Inventory Note

GRA-214 confirms the M44 primitive-stroke promotion reduced Path AA expected
unsupported inventory from 50 to 46 rows. The selected `StrokeRectGM` and
`StrokeCircleGM` WebGPU/cross-backend rows now render through
`webgpu.coverage.path-aa-stroke-primitive`; broad Path AA suites remain visible
as `coverage.edge-count-exceeded` with zero unexpected exceptions and zero
similarity regressions.

## M44 Outcome

Closed on 2026-05-28 by `reports/wgsl-pipeline/2026-05-28-m44-path-aa-family-promotion-closeout.md`.

M44 promoted the selected primitive-stroke Path AA family (`StrokeRectGM` and
`StrokeCircleGM`) through `webgpu.coverage.path-aa-stroke-primitive` with
adapter-backed rendered evidence and dashboard scene `path-aa-stroke-primitive`.
The expected unsupported Path AA inventory moved from 50 to 46 rows with zero
unexpected exceptions and zero similarity regressions. Remaining broad Path AA
families stay explicitly expected unsupported until separately scoped.

## M45 Selection Note

GRA-216 selects `Compose(ColorFilter(Matrix|Blend), MatrixTransform(affine))` as
M45's bounded image-filter DAG subset. The planned dashboard scene is
`image-filter-compose-cf-matrix-transform`; it requires one MatrixTransform
materialise scratch followed by a final ColorFilter composite. Broader DAG
shapes remain explicitly out of scope until separately scoped.

## M45 Outcome

Closed on 2026-05-28 by `reports/wgsl-pipeline/2026-05-28-m45-image-filter-dag-subset-closeout.md`.

M45 promoted the bounded two-node image-filter DAG subset
`Compose(ColorFilter(Matrix|Blend), MatrixTransform(affine))` with dashboard
scene `image-filter-compose-cf-matrix-transform`. The route materialises the
affine MatrixTransform into `LayerCompositeDraw.materializeTargetTexture`, then
applies the ColorFilter during final composite. No full Skia image-filter DAG
support is claimed; broader DAG shapes remain explicitly scoped with stable
refusal policy.

## M46 Plan

M46 should harden evidence for already-supported rows before adding new feature
scope. Starting point after M45/GRA-221:

| Signal | Count |
|---|---:|
| Merged scene rows | 13 |
| `pass` | 11 |
| `tracked-gap` | 0 |
| `expected-unsupported` | 2 |
| `fail` | 0 |
| `maturity.generated-evidence` | 3 |
| `maturity.static-evidence` | 10 |
| `maturity.adapter-backed` | 2 |

M46 target:

| Signal | Target |
|---|---:|
| `maturity.generated-evidence` | >= 8 |
| `maturity.static-evidence` | <= 5 |
| `tracked-gap` | 0 |
| `fail` | 0 |

M46 should not change runtime support claims unless the generated artifacts
prove the same route, threshold, fallback, and tag semantics as the static row
being replaced.

M46 closed on 2026-05-30 by
`reports/wgsl-pipeline/2026-05-30-m46-generated-evidence-expansion-closeout.md`.
Final counters: 13 rows, 11 pass, 0 tracked-gap, 2 expected-unsupported, 0
fail, 8 generated-evidence rows, and 5 static-evidence rows. Remaining static
rows are `runtime-effect-simple`, `clip-rect-difference`,
`bitmap-shader-local-matrix`, `path-aa-stroke-outline-fallback`, and
`path-aa-edge-budget-boundary`.


## M47 Closeout

M47 closed on 2026-05-31 by
`reports/wgsl-pipeline/2026-05-31-m47-sprint-review.md`.
Final counters: 13 rows, 11 pass, 0 tracked-gap, 2 expected-unsupported, 0
fail, 11 generated-evidence rows, and 2 static-evidence rows. Remaining static
rows are `path-aa-stroke-outline-fallback` and `path-aa-edge-budget-boundary`;
both are intentional Path AA policy sentinels with stable fallback reasons.

## M48 Closeout

M48 closed on 2026-05-31 by
`reports/wgsl-pipeline/2026-05-31-m48-sprint-review.md`.
Final counters: 23 rows, 18 pass, 0 tracked-gap, 5 expected-unsupported, 0
fail, 21 generated-evidence rows, and 2 static-evidence rows.

M48 added 10 selected P0/P1 rows:

- 7 generated support rows across paint, clip, transform, bitmap, gradient,
  blend, and Path AA-adjacent coverage;
- 3 generated expected-unsupported breadth rows for hard Path AA and
  image-filter planning boundaries.

The Post-MVP Big Target readiness moved from 35% to 40%. The Skia integration
coverage sub-score moved from 15% to 35% because M48 broadened representative
scene evidence while preserving 0 tracked-gap and 0 fail. The score does not
move higher because CI/release gates, performance thresholds, broader
adapter-backed coverage, text/font/codec coverage, and deployable PM reporting
remain outside M48.

Recommended next milestone: M49 should focus on CI and release gates for the
generated scene dashboard, including required invariants, CI-friendly validation,
portable PM artifact bundles, release readiness checklist, and performance gate
design.

## M49 Closeout

M49 started from the M48 dashboard state: 23 rows, 18 pass, 5
expected-unsupported, 0 tracked-gap, 0 fail, 21 generated-evidence rows, 2
static policy rows, and 2 adapter-backed rows.

M49 moved Post-MVP Big Target readiness from 40% to 60% because all required
release-relevant lanes landed with merged evidence:

| PM area | Start | M49 target | Required evidence |
|---|---:|---:|---|
| Evidence foundation | 100% | 100% | Preserved 0 tracked-gap / 0 fail and stable generated dashboard semantics. |
| Skia integration coverage | 35% | 45% | Added adapter-backed proof for selected high-value pass rows, reaching 7 adapter-backed rows. |
| CI and release gates | 10% | 60% | Added CI-friendly dashboard validation and release-gate policy through `pipelineSceneDashboardGate`. |
| Performance readiness | 15% | 35% | Defined a non-blocking measured-performance trend gate contract. |
| PM demo and reporting workflow | 15% | 45% | Generated a portable PM bundle with manifest, counters, artifacts, known limitations, and serve instructions. |

Completed tickets:

- M49-A gate invariant spec;
- M49-B CI validation task;
- M49-C portable PM artifact bundle;
- M49-D adapter-backed expansion;
- M49-E performance trend gate contract;
- M49-F MEP release readiness checklist;
- M49-G sprint review and score update.

Final counters: 23 rows, 18 pass, 5 expected-unsupported, 0 tracked-gap, 0 fail,
21 generated-evidence rows, 2 static policy rows, 7 adapter-backed rows, and 0
unavailable references in the portable PM bundle manifest.

The detailed sprint plan and closeout are:

- `reports/wgsl-pipeline/2026-05-31-m49-60-readiness-sprint-plan.md`;
- `reports/wgsl-pipeline/2026-05-31-m49-sprint-review.md`.

Remaining MEP work after M49:

- wire the dashboard gate into required CI ownership where appropriate;
- keep non-blocking GPU inventory reporting stable and owned;
- approve performance baselines, variance policy, environment eligibility, and
  rollback behavior before any release-blocking performance threshold;
- expand scene families only with adapter-backed captures and stable fallback
  diagnostics;
- accept and implement the front spec gates for dashboard UX, PM reporting,
  image inspection, browser checks, and accessibility;
- keep text/font/glyph/emoji/codec gaps dependency-gated until real deliveries
  land, then promote font scenes only with generated CPU/GPU/refusal evidence.

## Front And Font Spec Split

After M49, two draft spec packs were added:

- `.upstream/specs/front/` for dashboard UX, PM reporting workflow,
  accessibility, image/artifact browsing, and front quality gates;
- `.upstream/specs/font/` for pure Kotlin OpenType, simple text,
  explicit shaping, glyph rendering, glyph-mask handoff, color fonts, emoji,
  and font validation.

These specs were planning and ownership evidence until M50. M50 added selected
implementation evidence and moved the Post-MVP Big Target readiness score to
80%; broad front hosting, font/text, emoji, shaping, SDF, LCD, glyph-mask, and
codec support remain outside the selected rows.

## M50 Outcome

M50 targeted and reached 80% Post-MVP readiness because the sprint produced
release-visible executable evidence from the M49 dashboard gate candidate and
the draft front/font spec packs.

Target score formula:

| PM area | Current | M50 target | Evidence required |
|---|---:|---:|---|
| Evidence foundation | 100% | 100% | Dashboard has 28 rows, 0 `tracked-gap`, 0 `fail`, stable fallback policy, and deterministic scene export. |
| Skia integration coverage | 45% | 65% | Adapter-backed rows rose from 7 to 17 and first generated font/text scene evidence landed without broad unsupported claims. |
| CI and release gates | 60% | 85% | `wgsl_scene_dashboard_release_gate` archives dashboard, gate, front QA, performance warning, and PM bundle reports. |
| Performance readiness | 35% | 60% | `pipelinePerformanceTrendWarnings` emits warning-only trend evidence with baseline owner, environment metadata, variance policy, quarantine, and rollback notes. |
| PM demo and reporting workflow | 45% | 85% | PM bundle includes front QA, image inspection, filters, route/reference notices, screenshot paths, and performance warning output. |

Weighted result: 80%.

M50 completed lanes:

- M50-A Required CI ownership for dashboard gate and inventory reporting.
  Definition of Done: release path runs `pipelineSceneDashboardGate`, archives
  or links the output, keeps non-blocking inventory visible with an owner, and
  updates the release checklist with exact job/report paths.
- M50-B Front evidence gate.
  Definition of Done: dashboard has in-page image inspection, two-column
  desktop layout, single-column mobile layout, collapsed artifact lists, filters
  for status/priority/reference/maturity/adapter/fallback reason,
  route/reference notices, desktop/mobile screenshots, accessibility report
  with no critical issue, and PM bundle inclusion.
- M50-C Adapter-backed scene expansion V2.
  Definition of Done: at least 14 adapter-backed rows across at least four
  scene families, every new pass row has reference/CPU/GPU/diff/stats/route
  diagnostics/adapter metadata, `fallbackReason=none`, and the merged dashboard
  stays 0 `tracked-gap` / 0 `fail`.
- M50-D First font/text evidence pack.
  Definition of Done: at least three generated pass scenes from the existing
  pure Kotlin OpenType/simple text path, at least two generated
  expected-unsupported font/text rows with stable fallback reasons, font source
  and glyph diagnostics recorded, and no external font shortcut.
- M50-E Performance warning gate.
  Definition of Done: refreshed measured rows include host/OS/JDK/backend/
  adapter/warmup/sample/baseline/variance metadata; warning-only trend output is
  emitted by CI or release automation; owner, quarantine, and rollback policy
  are documented.
- M50-F Closeout and score update.
  Result: sprint review links generated dashboard, PM bundle, screenshots,
  performance output, font evidence, known limitations, and README/target docs
  now use the justified 80% score.

Validation baseline:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk ./gradlew --no-daemon pipelinePmBundle
```

Font/text tickets must also run the focused owning tests, starting with:

```bash
rtk ./gradlew --no-daemon :kanvas-skia:test --tests 'org.skia.foundation.opentype.*'
```

M50 does not claim complete MEP, broad Skia parity, broad font/emoji/shaping/
SDF/LCD/glyph-mask support, or release-blocking performance thresholds.

Detailed evidence:

- `reports/wgsl-pipeline/2026-05-31-m50-80-readiness-sprint-plan.md`.
- `reports/wgsl-pipeline/2026-05-31-m50-sprint-review.md`.
- `reports/wgsl-pipeline/2026-05-31-m50-verification-and-linear-sync.md`.

## M51 Outcome

M51 prepared broad Skia sample convergence by inventorying the full GM surface.
It did not add hundreds of support rows just to look complete.

Planning baseline:

| Source | Count | Note |
|---|---:|---|
| Upstream Skia `gm/*.cpp` | 437 | Local C++ GM files under `/Users/chaos/workspace/kanvas-forge/skia-main/gm`. |
| Kotlin `*GM.kt` sources | 751 | Local files under `skia-integration-tests/src/main/kotlin/org/skia/tests/`. |
| M50 dashboard rows | 28 | Current scene evidence rows; not equivalent to full Skia inventory. |
| Generated inventory rows | 802 | Merged planning rows with explicit source presence and status. |

M51 final score:

| PM area | M50 | M51 target | Evidence required |
|---|---:|---:|---|
| Evidence foundation | 100% | 100% | Existing dashboard gate remains green and deterministic. |
| Skia integration coverage | 65% | 70% | Full GM inventory is visible and classified, but support only moves for generated scene evidence. |
| CI and release gates | 85% | 85% | Inventory validation is additive and does not weaken the M50 release gate. |
| Performance readiness | 60% | 60% | No performance threshold change in M51. |
| PM demo and reporting workflow | 85% | 88% | PM bundle exposes inventory, counters, filters, gate output, and M52 candidate backlog. |

Weighted final score: 82%.

M51 delivered:

- M51-A Inventory schema and scanner: `pipelineSkiaGmInventory` writes
  deterministic JSON/Markdown with stable ids, source paths, display names,
  family tags, status, reason, and mismatch reports.
- M51-B Classification taxonomy: every row has exactly one allowed status; every
  non-`not-triaged` row has a reason.
- M51-C PM inventory view: `pipelinePmBundle` includes inventory JSON/Markdown,
  inventory gate output, counters, limitations, and dashboard-to-inventory links
  where possible.
- M51-D M52 promotion candidate backlog: the inventory selects 34 candidates
  across paint/blend, bitmap/image, gradients, clip/transform, Path AA, image
  filters, runtime effects, text/font, and codec/image decode boundaries.
- M51-E Inventory gate: `pipelineSkiaGmInventoryGate` fails duplicate ids and
  missing required source/status/family fields, and reports mismatch snapshots.
- M51-F Sprint review and score sync: the sprint review reports final inventory
  counts, validation commands, artifacts, limits/non-claims, and the 82% score.

Validation baseline:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
rtk ./gradlew --no-daemon pipelinePmBundle
```

M51 inventory-specific validation:

```bash
rtk ./gradlew --no-daemon pipelineSkiaGmInventory
rtk ./gradlew --no-daemon pipelineSkiaGmInventoryGate
```

## M52 Outcome

Closed on 2026-05-31 by
`reports/wgsl-pipeline/2026-05-31-m52-sprint-review.md`.

M52 promoted a bounded 10-row pack from the M51 candidate backlog into generated
dashboard evidence:

| Signal | Count |
|---|---:|
| Selected inventory candidates | 10 |
| Promoted generated dashboard rows | 10 |
| Generated `pass` rows | 7 |
| Generated `expected-unsupported` rows | 3 |
| Rejected/deferred candidates documented | 8 |
| `tracked-gap` | 0 |
| `fail` | 0 |

Final dashboard after M52:

| Signal | Count |
|---|---:|
| Scene rows | 38 |
| `pass` | 28 |
| `expected-unsupported` | 10 |
| `tracked-gap` | 0 |
| `fail` | 0 |
| Generated evidence rows | 36 |
| Static policy rows | 2 |
| Adapter-backed rows | 24 |
| Inventory-derived generated rows | 10 |

M52 final score:

| PM area | M51 | M52 | Reason |
|---|---:|---:|---|
| Evidence foundation | 100% | 100% | Dashboard generation and gates remain green with 0 tracked-gap and 0 fail. |
| Skia integration coverage | 70% | 82% | 10 selected M51 inventory candidates now have generated evidence or stable generated refusals. |
| CI and release gates | 85% | 85% | Existing gates stay required; inventory-derived row invariants are additive. |
| Performance readiness | 60% | 60% | No performance threshold change. |
| PM demo and reporting workflow | 88% | 90% | PM bundle exposes selected/promoted/rejected M52 counters and limitations. |

Weighted final score: 85%.

M52 does not claim broad Skia GM support, does not clear unpromoted inventory
rows, and does not clear dependency-gated font, codec, emoji, shaping, SDF, LCD,
glyph-mask, arbitrary image-filter DAG, arbitrary SkSL, or broad Path AA gaps.

The final task names are recorded in the closeout.

Detailed reports:

- `reports/wgsl-pipeline/2026-05-31-m51-skia-gm-inventory-sprint-plan.md`.
- `reports/wgsl-pipeline/2026-05-31-m51-sprint-review.md`.
- `reports/wgsl-pipeline/2026-05-31-m51-pm-report.md`.

## M53 Outcome

Closed on 2026-05-31 by
`reports/wgsl-pipeline/2026-05-31-m53-sprint-review.md`.

M53 promoted a second bounded 12-row GM feature pack into generated dashboard
evidence:

| Signal | Count |
|---|---:|
| Selected inventory candidates | 12 |
| Promoted generated dashboard rows | 12 |
| Generated `pass` rows | 9 |
| Generated `expected-unsupported` rows | 3 |
| Rejected/deferred candidates documented | 6 |
| `tracked-gap` | 0 |
| `fail` | 0 |

Final dashboard after M53:

| Signal | Count |
|---|---:|
| Scene rows | 50 |
| `pass` | 37 |
| `expected-unsupported` | 13 |
| `tracked-gap` | 0 |
| `fail` | 0 |
| Generated evidence rows | 48 |
| Static policy rows | 2 |
| Adapter-backed rows | 33 |
| Inventory-derived generated rows | 22 |

M53 final score:

| PM area | M52 | M53 | Reason |
|---|---:|---:|---|
| Evidence foundation | 100% | 100% | Dashboard generation and gates remain green with 0 tracked-gap and 0 fail. |
| Skia integration coverage | 82% | 94% | 12 more selected GM candidates now have generated evidence or stable generated refusals across five families. |
| CI and release gates | 85% | 90% | M53 metadata validation and expected-unsupported policy entries are additive. |
| Performance readiness | 60% | 60% | No performance threshold change. |
| PM demo and reporting workflow | 90% | 95% | PM bundle exposes selected/promoted/rejected M52 and M53 counters and limitations. |

Weighted final score: 90%.

M53 does not claim broad Skia GM support, does not clear unpromoted inventory
rows, and does not clear dependency-gated font, codec, emoji, shaping, SDF, LCD,
glyph-mask, arbitrary image-filter DAG, arbitrary SkSL, or broad Path AA gaps.

Detailed reports:

- `reports/wgsl-pipeline/2026-05-31-m53-gm-feature-promotion-pack-v2-selection.md`.
- `reports/wgsl-pipeline/2026-05-31-m53-inventory-promotion-pack.md`.
- `reports/wgsl-pipeline/2026-05-31-m53-sprint-review.md`.
- `reports/wgsl-pipeline/2026-05-31-m53-pm-report.md`.

## M54 Outcome

Closed on 2026-05-31 by
`reports/wgsl-pipeline/2026-05-31-m54-sprint-review.md`.

M54 promoted a bounded 10-row hard feature depth pack into generated dashboard
evidence:

| Signal | Count |
|---|---:|
| Selected candidates | 13 |
| Promoted generated dashboard rows | 10 |
| Generated `pass` rows | 8 |
| Generated `expected-unsupported` rows | 2 |
| Rejected/deferred candidates documented | 12 |
| Warning-only measured performance rows | 2 |
| `tracked-gap` | 0 |
| `fail` | 0 |

Final dashboard after M54:

| Signal | Count |
|---|---:|
| Scene rows | 60 |
| `pass` | 45 |
| `expected-unsupported` | 15 |
| `tracked-gap` | 0 |
| `fail` | 0 |
| Generated evidence rows | 58 |
| Static policy rows | 2 |
| Adapter-backed rows | 41 |
| Inventory-derived generated rows | 32 |

M54 final score:

| PM area | M53 | M54 | Reason |
|---|---:|---:|---|
| Evidence foundation | 100% | 100% | Dashboard generation and gates remain green with 0 tracked-gap and 0 fail. |
| Skia integration coverage | 94% | 98% | 10 selected hard feature candidates now have generated evidence or stable generated refusals across three hard families. |
| CI and release gates | 90% | 95% | M54 metadata validation, gate family counters, and expected-unsupported policy entries are additive. |
| Performance readiness | 60% | 65% | Two M54 rows carry measured warning-only payloads; thresholds remain non-blocking. |
| PM demo and reporting workflow | 95% | 98% | PM bundle exposes selected/promoted/rejected M52, M53, and M54 counters and limitations. |

Weighted final score: 93%.

M54 does not claim broad Skia GM support, does not clear unpromoted inventory
rows, and does not clear dependency-gated font, codec, emoji, shaping, SDF, LCD,
glyph-mask, arbitrary image-filter DAG, arbitrary SkSL, broad Path AA, or
release-blocking performance gate gaps.

Detailed reports:

- `reports/wgsl-pipeline/2026-05-31-m54-hard-feature-depth-selection.md`.
- `reports/wgsl-pipeline/2026-05-31-m54-hard-feature-depth-pack.md`.
- `reports/wgsl-pipeline/2026-05-31-m54-warning-only-performance-evidence.md`.
- `reports/wgsl-pipeline/2026-05-31-m54-pm-bundle-counters-and-gates.md`.
- `reports/wgsl-pipeline/2026-05-31-m54-sprint-review.md`.
- `reports/wgsl-pipeline/2026-05-31-m54-pm-report.md`.

## M55 Outcome

Closed on 2026-05-31 by
`reports/wgsl-pipeline/2026-05-31-m55-sprint-review.md`.

M55 added a non-blocking performance gate candidate without changing dashboard
support counters:

| Signal | Count |
|---|---:|
| Selected candidate rows | 7 |
| Excluded rows | 2 |
| Candidate `pass` rows | 4 |
| Candidate `deferred` rows | 3 |
| Candidate `warn` rows | 0 |
| Candidate `fail-candidate` rows | 0 |
| Candidate pass lanes | 8 |
| Candidate deferred lanes | 6 |

Final dashboard after M55:

| Signal | Count |
|---|---:|
| Scene rows | 60 |
| `pass` | 45 |
| `expected-unsupported` | 15 |
| `tracked-gap` | 0 |
| `fail` | 0 |
| Generated evidence rows | 58 |
| Static policy rows | 2 |
| Adapter-backed rows | 41 |
| Inventory-derived generated rows | 32 |

M55 final score:

| PM area | M54 | M55 | Reason |
|---|---:|---:|---|
| Evidence foundation | 100% | 100% | Dashboard support evidence remains generated, clean, and unchanged. |
| Skia integration coverage | 98% | 98% | No rendering support expansion in M55. |
| CI and release gates | 95% | 97% | Performance candidate output is deterministic, generated by Gradle, and included in PM bundle. |
| Performance readiness | 65% | 80% | Seven selected rows have candidate decisions; four have measured CPU/GPU payloads and three are explicitly deferred. |
| PM demo and reporting workflow | 98% | 99% | PM bundle exposes M55 candidate counters, reports, JSON, and non-blocking limitations. |

Weighted final score: 95%.

M55 does not claim rendering support expansion, does not promote estimated
metrics to measured, and does not enable a release-blocking performance gate.
Missing host or adapter lanes remain visible as `deferred` or `warn`.

Detailed reports:

- `reports/wgsl-pipeline/2026-05-31-m55-performance-gate-candidate-selection.md`.
- `reports/wgsl-pipeline/2026-05-31-m55-official-performance-baseline-payloads.md`.
- `reports/wgsl-pipeline/2026-05-31-m55-quarantine-rebaseline-rollback-policy.md`.
- `reports/wgsl-pipeline/2026-05-31-m55-sprint-review.md`.

## M56 Outcome

Closed on 2026-05-31 by
`reports/wgsl-pipeline/2026-05-31-m56-sprint-review.md`.

M56 attempted an unsupported-to-pass feature sprint with a 97% stretch target.
It landed one safe promotion and rejected two unsafe promotion shortcuts.

Final dashboard after M56:

| Signal | Count |
|---|---:|
| Scene rows | 60 |
| `pass` | 46 |
| `expected-unsupported` | 14 |
| `tracked-gap` | 0 |
| `fail` | 0 |
| Generated evidence rows | 58 |
| Static policy rows | 2 |
| Adapter-backed rows | 42 |
| Inventory-derived generated rows | 32 |

M56 final score:

| PM area | M55 | M56 | Reason |
|---|---:|---:|---|
| Evidence foundation | 100% | 100% | Dashboard evidence remains generated, clean, and unchanged. |
| Skia integration coverage | 98% | 99% | One previous expected-unsupported sweep-gradient row becomes adapter-backed pass with real artifacts. |
| CI and release gates | 97% | 98% | The scene gate allowlist and PM bundle expose the corrected support claim. |
| Performance readiness | 80% | 80% | No release-blocking performance change and no new measured performance lane. |
| PM demo and reporting workflow | 99% | 99% | PM bundle exposes M56 counters, reports, and limitation evidence. |

Weighted final score: 96%.

M56 does not claim two-point conical gradients, arbitrary image-filter DAGs,
picture prepass support, broad Path AA, dash, stroke, or complex clip support.
The 97% stretch target remains open until at least one more current
`expected-unsupported` row becomes a real adapter-backed `pass` row.

Detailed reports:

- `reports/wgsl-pipeline/2026-05-31-m56-unsupported-to-pass-selection.md`.
- `reports/wgsl-pipeline/2026-05-31-m56-gra334-image-filter-promotion-decision.md`.
- `reports/wgsl-pipeline/2026-05-31-gra-336-path-aa-clip-budget-review.md`.
- `reports/wgsl-pipeline/2026-05-31-m56-sprint-review.md`.
- `reports/wgsl-pipeline/2026-05-31-m56-pm-report.md`.

## M57 Outcome

Closed on 2026-05-31 by
`reports/wgsl-pipeline/2026-05-31-m57-sprint-review.md`.

M57 targeted a bounded Path AA / clip micro-promotion without converting any
existing broad refusal row. It landed one `skia-gm-aaclip` bounded-grid slice as
generated adapter-backed pass evidence.

Final dashboard after M57:

| Signal | Count |
|---|---:|
| Scene rows | 61 |
| `pass` | 47 |
| `expected-unsupported` | 14 |
| `tracked-gap` | 0 |
| `fail` | 0 |
| Generated evidence rows | 59 |
| Static policy rows | 2 |
| Adapter-backed rows | 43 |
| Inventory-derived generated rows | 33 |

M57 final score:

| PM area | M56 | M57 | Reason |
|---|---:|---:|---|
| Evidence foundation | 100% | 100% | Dashboard evidence remains generated, clean, and validated. |
| Skia integration coverage | 99% | 100% | One bounded AA clip inventory-derived support row is added. |
| CI and release gates | 98% | 99% | The scene gate and PM bundle expose M57 generated evidence and policy preservation. |
| Performance readiness | 80% | 80% | No release-blocking performance change. |
| PM demo and reporting workflow | 99% | 100% | PM bundle exposes M57 counters, reports, and non-claims. |

Weighted final score: 98%.

M57 does not claim broad `aaclip`, broad Path AA, dash/cap/join,
stroke-outline, complex clip, large clipped path, or a WebGPU edge-budget
increase.

Detailed reports:

- `reports/wgsl-pipeline/2026-05-31-m57-path-aa-clip-micro-slice-selection.md`.
- `reports/wgsl-pipeline/2026-05-31-m57-path-aa-clip-micro-promotion.md`.
- `reports/wgsl-pipeline/2026-05-31-m57-sprint-review.md`.
- `reports/wgsl-pipeline/2026-05-31-m57-pm-report.md`.
- `reports/wgsl-pipeline/2026-05-31-gra-336-path-aa-clip-budget-review.md`.
- `reports/wgsl-pipeline/2026-05-31-m56-sprint-review.md`.
- `reports/wgsl-pipeline/2026-05-31-m56-pm-report.md`.
- `reports/wgsl-pipeline/2026-05-31-m55-pm-report.md`.
- `reports/wgsl-pipeline/performance/m55-performance-gate-candidates.json`.

## M58 Outcome

Closed on 2026-05-31 by
`reports/wgsl-pipeline/2026-05-31-m58-sprint-review.md`.

M58 converted the M55 candidate into a measured-row-only release gate. It did
not add rendering support rows or broaden any unsupported family.

Final dashboard after M58:

| Signal | Count |
|---|---:|
| Scene rows | 61 |
| `pass` | 47 |
| `expected-unsupported` | 14 |
| `tracked-gap` | 0 |
| `fail` | 0 |
| Generated evidence rows | 59 |
| Static policy rows | 2 |
| Adapter-backed rows | 43 |
| Inventory-derived generated rows | 33 |

M58 performance gate:

| Signal | Count |
|---|---:|
| Selected rows | 7 |
| Measured pass rows | 4 |
| Not-measured rows | 3 |
| Measured release-blocking lanes | 8 |
| Not-measured lanes | 6 |
| Blocking failures | 0 |

M58 final score:

| PM area | M57 | M58 | Reason |
|---|---:|---:|---|
| Evidence foundation | 100% | 100% | Dashboard evidence remains generated, clean, and validated. |
| Skia integration coverage | 100% | 100% | No rendering support expansion is claimed. |
| CI and release gates | 99% | 100% | `pipelinePerformanceReleaseGate` blocks selected measured lanes with missing required metadata or threshold breaches. |
| Performance readiness | 80% | 95% | Four measured rows have CPU/GPU release thresholds; estimated and missing rows stay non-claims and remain the final readiness gap. |
| PM demo and reporting workflow | 100% | 100% | PM bundle exposes M58 counters, reports, JSON, and non-claims. |

Weighted final score: 99%.

Detailed reports:

- `reports/wgsl-pipeline/2026-05-31-m58-performance-release-gate-selection.md`.
- `reports/wgsl-pipeline/2026-05-31-m58-performance-threshold-policy.md`.
- `reports/wgsl-pipeline/2026-05-31-m58-sprint-review.md`.
- `reports/wgsl-pipeline/2026-05-31-m58-pm-report.md`.
- `reports/wgsl-pipeline/2026-05-31-m58-non-claims.md`.
- `reports/wgsl-pipeline/performance/m58-performance-release-gate.json`.
