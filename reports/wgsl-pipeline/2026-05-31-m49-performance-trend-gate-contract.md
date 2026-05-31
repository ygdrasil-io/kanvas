# M49 Non-Blocking Performance Trend Gate Contract

Date: 2026-05-31
Linear: GRA-292
Parent epic: GRA-287
Depends on: GRA-290

## Outcome

M49-E promotes M43 measured performance payloads from ad hoc report evidence to a
non-blocking trend gate contract. It does not add a required release-blocking
performance threshold.

Performance readiness can move from 15% to 35% if the M49 closeout lands this
contract with the PM bundle and release checklist, because Kanvas then has a
reviewable trend policy. It must not move higher until CI-owned baselines,
rollback, quarantine, and required-gate ownership are implemented.

## Measured Rows Covered

The initial non-blocking trend set remains the two M43 measured rows:

| Scene id | CPU raw metrics | GPU raw metrics | GPU adapter | Current gate |
|---|---|---|---|---|
| `src-over-stack` | `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/cpu-performance.json` | `reports/wgsl-pipeline/scenes/artifacts/src-over-stack/gpu-performance.json` | `Apple M2 Max` | `reporting-only` |
| `bitmap-shader-local-matrix` | `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/cpu-performance.json` | `reports/wgsl-pipeline/scenes/artifacts/bitmap-shader-local-matrix/gpu-performance.json` | `Apple M2 Max` | `reporting-only` |

Current M43 baselines:

| Lane | Baseline | Status |
|---|---|---|
| CPU | `m43-cpu-measured-local` | Local milestone evidence, not CI-owned. |
| GPU/cache | `m43-gpu-cache-measured-local` | Local adapter evidence on `Apple M2 Max`, not CI-owned. |

## Eligible Environments

M49 non-blocking trend reporting may display measurements only for these eligible
classes:

| Lane | Host/JDK/backend/adapter eligibility | Behavior |
|---|---|---|
| CPU scalar dashboard benchmark | macOS arm64 or Linux x86_64, JDK 25, backend `CPU scalar Kotlin dashboard benchmark` | Report trend as non-blocking when sample policy is satisfied. |
| GPU/cache dashboard benchmark | macOS arm64, JDK 25, backend `WebGPU cache/timing dashboard benchmark`, adapter `Apple M2 Max` for the current baseline | Report trend as non-blocking when adapter matches baseline. |
| Adapter missing | Any host where WebGPU adapter identity is absent | Emit `status=unavailable`, `reason=gpu.adapter-missing`; do not compare. |
| Different adapter/backend/JDK | Any environment outside the baseline eligibility | Emit or display `regression.label=unknown`; do not compare as pass/fail. |

## Cold/Warm Measurement Policy

| Rule | Severity | Policy |
|---|---|---|
| Warm measurements | Required for trend display | The dashboard trend should use warm steady-state samples for `medianMs` and `p95Ms`. |
| Cold measurements | Optional | Cold-start samples may be shown separately but must not be mixed into warm trend comparisons. |
| Mixed phase | Warning | `phase=mixed` is allowed only when the raw payload explains how cold and warm samples were separated. |
| Missing phase | Warning | Existing M43 payloads may remain visible, but new M49+ measured rows should include `phase=warm`, `cold`, or `mixed`. |

## Sample Count And Variance Policy

| Rule | Non-blocking gate behavior |
|---|---|
| Minimum samples | Display trend only when `sampleCount >= 30` for M43/M49 measured rows. |
| Median and p95 | Both `timing.medianMs` and `timing.p95Ms` must be numeric. |
| Variance warning | If `p95Ms / medianMs > 2.5`, emit a warning that the lane is noisy and should not update baseline. |
| Regression labels | `none`, `improved`, `regressed`, and `unknown` remain labels, not CI decisions. |
| Estimated metrics | Never part of performance trend gates; PM bundle may display them as informational only. |

## Baseline Owner And Update Process

M49 defines the process but does not name a permanent baseline owner yet.

A future required gate must add all of the following before any performance
regression can fail CI:

1. Named code owner for baseline updates and quarantine decisions.
2. CI lane and timeout budget for each benchmark task.
3. Host/JDK/backend/adapter eligibility matrix.
4. Minimum sample count and allowed variance threshold.
5. Baseline update PR template with before/after raw metric links.
6. Quarantine rule for noisy or infrastructure-affected lanes.
7. Rollback rule for removing or relaxing a threshold that blocks unrelated work.
8. Evidence from at least three consecutive stable CI runs.

Until those exist, baseline names starting with `m43-` remain local milestone
evidence and cannot be promoted to release-blocking gates.

## Warn / Fail / Quarantine Behavior

| Condition | M49 behavior | Future required gate behavior |
|---|---|---|
| Missing measured payload | Warn in PM bundle if the row was expected to report trend. | Fail only after required gate policy names the row. |
| `status=estimated` | Warn/informational only. | Never a release gate. |
| `status=unavailable` with reason | Allowed and visible. | Quarantine or skip if reason matches approved infrastructure condition. |
| `regression.label=unknown` | Warn; no score movement from that row. | Fail only if the required lane should have a comparable baseline. |
| `regression.label=regressed` | Warn; no merge block. | May fail after owner, rollback, threshold, and stable CI evidence exist. |
| Noisy variance | Warn and prevent baseline update. | Quarantine if repeated across stable CI lanes. |

## PM Bundle Display Contract

The M49-C PM bundle should display performance trend state as:

- row id;
- CPU/GPU lane;
- `performanceTrend.status`;
- median and p95 when measured;
- baseline name and commit;
- regression label;
- gate status, currently `reporting-only`;
- raw metrics path;
- warning summary for estimated, unavailable, unknown, or noisy rows.

The bundle must not present estimated or reporting-only values as release gates.

## What Remains Reporting-Only After M49

- All M43 measured rows remain non-blocking.
- All estimated dashboard performance fields remain informational only.
- Adapter-missing GPU performance remains `unavailable`, not failure.
- Regression labels remain PM/engineering trend signals, not merge blockers.
- No performance readiness above 35% is justified without CI-owned baselines and rollback/quarantine ownership.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
rtk ./gradlew --no-daemon pipelineSceneDashboardGate
```
