# M49 Dashboard Gate Invariants

Date: 2026-05-31
Linear: GRA-288
Parent epic: GRA-287
Milestone: M49 -- MEP Readiness Gate Toward 60%

## Purpose

M49-A defines the exact invariants that M49-B can enforce in a CI-friendly
validation task. The goal is to remove reviewer interpretation from the release
readiness gate: every promoted dashboard row must either satisfy machine-checkable
support evidence requirements or remain explicitly expected unsupported with a
stable fallback reason.

This document is a gate contract, not a score update. A 60% PM readiness score is
impossible from M49-A alone. The 60% target requires merged M49-B, M49-C, M49-D,
M49-E, and M49-F evidence: CI validation, portable PM bundle, adapter-backed
expansion, non-blocking performance trend contract, and release checklist.

## M48 Baseline

M49 starts from the M48 merged dashboard state:

| Signal | Count |
|---|---:|
| Scene rows | 23 |
| `pass` | 18 |
| `expected-unsupported` | 5 |
| `tracked-gap` | 0 |
| `fail` | 0 |
| Generated rows | 21 |
| Static rows | 2 |
| Adapter-backed rows | 2 |

The M49 gate must preserve this quality posture unless a later ticket explicitly
changes the policy and updates this contract.

## Severity Levels

| Severity | Meaning | CI behavior |
|---|---|---|
| `must fail` | Violates a release-readiness invariant or creates an unsafe support claim. | The M49-B validation task must exit non-zero. |
| `must warn` | Evidence is incomplete for score movement but still valid as reporting-only dashboard data. | The M49-B validation task should emit a report warning and keep CI green. |
| `allowed` | Explicitly valid state under the M49 gate. | No warning or failure required. |

## Status Policy

| Invariant | Severity | Rule |
|---|---|---|
| Allowed row statuses | `must fail` | `status` must be one of `pass`, `expected-unsupported`, `tracked-gap`, or `fail`. |
| Promoted MEP dashboard posture | `must fail` | The promoted dashboard must contain 0 `tracked-gap` rows and 0 `fail` rows. |
| `pass` support claim | `must fail` | A `pass` row must have reference, CPU image/diff/stats/route, GPU image/diff/stats/route, top-level stats, and `gpu.route.fallbackReason=none`. |
| `expected-unsupported` planning row | `must fail` | An `expected-unsupported` row must keep `gpu.status=expected-unsupported`, `route.gpu.expected-unsupported`, and a non-empty, non-`none` `gpu.route.fallbackReason`. |
| `tracked-gap` rows | `must fail` | New promoted dashboard exports may not contain `tracked-gap`; gap evidence must be resolved or kept outside the promoted dashboard. |
| `fail` rows | `must fail` | New promoted dashboard exports may not contain `fail`; failing support claims must block merge or be reclassified with explicit refusal policy. |

## Duplicate Id Policy

| Invariant | Severity | Rule |
|---|---|---|
| Unique ids across static and generated rows | `must fail` | After merging `reports/wgsl-pipeline/scenes/data/scenes.json` and `reports/wgsl-pipeline/scenes/generated/results.json`, every `id` must be unique. |
| Id format | `must fail` | Scene ids must be lowercase kebab-case: `[a-z0-9][a-z0-9-]*`. |
| Accidental replacement | `must fail` | A generated row must not reuse a static row id unless a dedicated migration ticket removes or replaces the static row in the same PR and preserves evidence links. |

## Required Fields

| Row kind | Severity | Required fields |
|---|---|---|
| All rows | `must fail` | `id`, `title`, `priority`, `status`, `source`, `reference`, `cpu`, `gpu`, `diffs`, `routeDiagnostics`, `stats`, `tags`. |
| All rows | `must fail` | `priority` must be `P0`, `P1`, or `P2`. |
| `pass` rows | `must fail` | `reference`, `cpu.image`, `cpu.diff`, `gpu.image`, `gpu.diff`, `diffs.cpu`, `diffs.gpu`, `routeDiagnostics.cpu`, `routeDiagnostics.gpu`. |
| `expected-unsupported` rows | `must fail` | `reference`, `cpu.image`, `cpu.diff`, `diffs.cpu`, `routeDiagnostics.cpu`, `routeDiagnostics.gpu`, `gpu.route.fallbackReason`. GPU image/diff are not required. |
| Generated or mixed rows | `must fail` | `generation.mode`, `generation.producer`, `generation.commit`, `generation.artifactRoot`, `generation.schema`, at least one source trace field, and non-empty `evidence[]`. |
| Generated `tracked-gap` rows | `must fail` | Not allowed in the promoted M49 dashboard. If a non-promoted generator uses this state, it must include `generation.missing[]`. |

## Artifact Existence Checks

| Invariant | Severity | Rule |
|---|---|---|
| Referenced artifact paths exist | `must fail` | Every referenced `artifacts/*` path must exist under either `reports/wgsl-pipeline/scenes/` or `build/reports/wgsl-pipeline-generated-scenes/`. |
| Referenced dashboard data paths exist | `must fail` | Every referenced `data/*` path must exist under the static or generated dashboard data root. |
| Referenced report paths exist | `must fail` | Every referenced `reports/*` path must exist in the repository. |
| Generated artifact root exists | `must fail` | `generation.artifactRoot` must resolve to an existing artifact directory after the generated export task runs. |
| PM bundle-only files | `must warn` | Files that exist only in the future M49-C portable bundle may warn until M49-C defines their final location. They must not be required for M49-A. |

## Route Diagnostics And Stats

| Invariant | Severity | Rule |
|---|---|---|
| CPU route selector | `must fail` | `cpu.route` must include `selectedRoute` or `coverageStrategy`, plus `fallbackReason`. |
| GPU route selector | `must fail` | `gpu.route` must include `selectedRoute` or `coverageStrategy`, plus `fallbackReason`. |
| Supported GPU fallback | `must fail` | A support row with `gpu.status=pass` must use `gpu.route.fallbackReason=none`. |
| Unsupported GPU fallback | `must fail` | An unsupported row must use a stable non-`none` fallback reason. |
| CPU stats | `must fail` | `cpu.stats` must include `pixels`, `matchingPixels`, `maxChannelDelta`, `threshold`, `backend`, and `command`. |
| GPU stats for pass rows | `must fail` | `gpu.stats` must include `pixels`, `matchingPixels`, `maxChannelDelta`, `threshold`, `backend`, and `command`. |
| Top-level stats | `must fail` | `stats` must include `pixels`, `matchingPixels`, `maxChannelDelta`, and `threshold`. |
| Adapter-backed metadata | `must fail` | Rows tagged `maturity.adapter-backed` must include `gpu.stats.adapter`. |

## Stable Fallback Reason Policy

| Invariant | Severity | Rule |
|---|---|---|
| `none` for support | `must fail` | Supported GPU rows must not carry a non-`none` fallback reason. |
| Non-`none` for refusal | `must fail` | Expected-unsupported GPU rows must not use `none`, blank, or missing fallback reasons. |
| Known refusal codes | `must fail` | Existing Path AA and image-filter refusal rows must keep their current stable reason codes unless a dedicated policy ticket updates the reports and tests. |
| Unknown new refusal code | `must warn` | A new expected-unsupported fallback reason may warn until its policy report and owning Linear ticket are linked. M49-B may tighten this to `must fail` if it adds an allowlist. |

Current stable expected-unsupported reasons:

| Scene id | Stable fallback reason |
|---|---|
| `path-aa-stroke-outline-fallback` | `coverage.stroke-outline-edge-count-exceeded` |
| `path-aa-edge-budget-boundary` | `coverage.edge-count-exceeded` |
| `path-aa-convexpaths-edge-budget` | `coverage.edge-count-exceeded` |
| `path-aa-dashing-edge-budget` | `coverage.edge-count-exceeded` |
| `image-filter-crop-nonnull-prepass-required` | `image-filter.crop-input-nonnull-prepass-required` |

## Tag Parseability Policy

| Invariant | Severity | Rule |
|---|---|---|
| Tags required | `must fail` | Every row must include non-empty `tags[]`. |
| Tag format | `must fail` | Tags must be lowercase dot/kebab tokens matching `[a-z0-9][a-z0-9.-]*`; whitespace and slash are invalid. |
| Duplicate row tags | `must fail` | A row must not contain duplicate tags. |
| Generated tag namespaces | `must fail` | Generated or mixed rows must include at least one `source.*`, `feature.*`, `route.*`, `reference.*`, and `maturity.*` tag. |
| Expected-unsupported tag | `must fail` | Expected-unsupported GPU rows must include `route.gpu.expected-unsupported` and `risk.expected-unsupported`. |
| Risk tag for support rows | `allowed` | Support rows may use `risk.none` when no known scoped risk applies. |
| Additional PM filter tags | `allowed` | New `feature.*`, `risk.*`, or `maturity.*` tags are allowed if parseable and documented by the owning report. |

## Static Path AA Policy Sentinels

The two remaining static rows are intentional policy sentinels:

- `path-aa-stroke-outline-fallback`
- `path-aa-edge-budget-boundary`

| Invariant | Severity | Rule |
|---|---|---|
| Static sentinel presence | `allowed` | These two rows may remain `source.static` / `maturity.static-evidence`. They are not conversion debt. |
| Static sentinel status | `must fail` | They must remain `expected-unsupported` until a dedicated Path AA implementation ticket proves support. |
| Static sentinel fallback | `must fail` | They must preserve `coverage.stroke-outline-edge-count-exceeded` and `coverage.edge-count-exceeded` respectively. |
| Static sentinel support relabel | `must fail` | Relabelling either static sentinel to `pass` without rendered CPU/GPU/reference evidence and a linked implementation report must fail. |

## Performance Trend Policy

| Invariant | Severity | Rule |
|---|---|---|
| Missing performance trend | `allowed` | Performance trend fields remain optional for dashboard rows. |
| `performanceTrend.status=unavailable` | `must fail` | Must include `reason`. |
| `performanceTrend.status=measured` | `must fail` | Must include `sampleCount`, `timing.medianMs`, `timing.p95Ms`, non-empty `counters`, `baseline.name`, `baseline.commit`, and `regression.label`. |
| `performanceTrend.status=estimated` | `must warn` | Estimated metrics are informational only and cannot support PM performance score movement. |
| Measured performance regression | `must warn` | Until M49-E defines a blocking threshold policy, `regression.label=regressed` must warn, not fail. |
| Blocking performance threshold | `allowed` | Only M49-E or a later accepted policy may promote trend regressions to `must fail`. |

## M49-B Implementation Notes

M49-B should implement these checks as a CI-friendly task that runs after
`pipelineGeneratedSceneExport` and validates the merged static/generated scene
set. It should write a machine-readable and PM-readable report with:

- counters by status and maturity;
- failures grouped by invariant id;
- warnings grouped by invariant id;
- the list of allowed expected-unsupported rows and fallback reasons;
- the adapter-backed row count;
- the performance trend warning summary.

The existing `pipelineSceneDashboard` task already covers many structural checks.
M49-B should either reuse that validation logic or factor out a stricter release
gate so the local dashboard export remains useful while CI gets explicit
fail/warn reporting.

## Validation

Commands for this invariant spec:

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```
