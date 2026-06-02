# RC Kadre Runtime Closeout

Date: 2026-06-02

Scope: `FOR-204`..`FOR-213`, `FOR-217`, `FOR-219`, `FOR-220`.

This is a headless closeout package for the Kadre runtime product-like and
observed telemetry sprints. It reuses existing M70, M83, M85 and M90 evidence;
it does not claim a new native window run on 2026-06-02.

## RC Command

Single opt-in native RC command:

```bash
git submodule update --init --recursive external/poc-koreos
rtk ./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeInteractive
```

The command intentionally opens a local Kadre native window and is not wired
into CI, `pipelinePmBundle`, or any headless gate. Kadre is sourced through
`external/poc-koreos`; `settings.gradle.kts` substitutes the
`org.graphiks.kadre:*` coordinates from that source build when the submodule is
provisioned. Do not treat Maven Central Kadre resolution as the RC dependency
source.

Headless validation command:

```bash
python3 scripts/validate_mep_rc_runtime.py .
```

## Product-Like Runtime Evidence

| Issue | Evidence | Closeout |
|---|---|---|
| `FOR-204` | `reports/wgsl-pipeline/m90-runtime-interactive/scene-switching.json` | Four renderable scenes and one expected-unsupported scene are documented for bounded native scene selection. |
| `FOR-205` | `reports/wgsl-pipeline/m90-runtime-interactive/evidence.json` | Pointer and keyboard fixtures update visible runtime state: play/pause, pointer control and selected scene. |
| `FOR-206` | `kadre-runtime/build.gradle.kts` and M90 modes | Demo, benchmark and headless evidence remain split; native modes are opt-in. |
| `FOR-207` | `reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json` | Live telemetry is represented by bounded JSON snapshots with stable schema. |
| `FOR-208` | this report and PM script | PM demo script, commands, limitations and fallback are documented. |

Existing M83 evidence remains the promoted display-list PM scene:

- `reports/wgsl-pipeline/m83-display-list-replay/evidence.json`
- `reports/wgsl-pipeline/m83-display-list-replay/native-demo.json`
- `reports/wgsl-pipeline/m83-display-list-replay/native-demo-readback.png`

## Telemetry Classification

Detailed classification lives in
`reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json`.

| Family | Classification | PM wording |
|---|---|---|
| Native frame timing and surface status | observed | Real selected-route samples exist, but remain candidate/reporting-only. |
| Scene switching and input state | observed | Bounded evidence events and state updates are serialized. |
| Native shader/pipeline creation churn | observed-partial | The selected route records creation/churn; this is not cache-hit telemetry. |
| Cache hit/miss and resource generation ledger | derived | M85 selected-scene ledger keeps resize/cache health auditable. |
| Broad WebGPU cache callbacks | not-observable | Blocked on Kadre/wgpu4k exposing cache counters. |
| Native resource free callbacks and adapter memory | not-observable | Blocked on resource lifetime snapshots from the native route. |
| Nested rrect clip scene | expected-unsupported | Stable refusal remains visible in scene switching evidence. |
| Real OS event injection in CI | expected-unsupported | Headless evidence uses deterministic fixtures, not window-manager injection. |

## Readiness

Readiness delta: `0.00%`.

Reason: this closeout improves evidence quality, PM repeatability and
telemetry provenance, but it does not add a new release-blocking runtime,
performance or cache gate. `frame.kadre-windowed` remains reporting-only, and
WGSL remains the shader implementation target.

## Blockers

- `kadre-runtime.native-cache-counter-unavailable`: Kadre/wgpu4k does not
  expose broad shader, pipeline, bind-group or cache hit/miss callbacks for
  the selected native route.
- `kadre-runtime.resource-lifetime-observation-partial`: native object
  allocation/free and adapter memory snapshots are not observable end-to-end.

These blockers are explicit residual work for observed telemetry; they are not
treated as solved by derived M85 ledgers.

## Validation

```bash
python3 scripts/validate_mep_rc_runtime.py .
python3 -m json.tool reports/wgsl-pipeline/m92-kadre-runtime-rc/evidence.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json >/dev/null
```

Native fallback if the demo machine cannot open Kadre:

```bash
rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive
python3 scripts/validate_mep_rc_runtime.py .
```
