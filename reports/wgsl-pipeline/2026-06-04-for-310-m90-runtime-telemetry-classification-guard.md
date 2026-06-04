# FOR-310 M90 Runtime Telemetry Classification Guard

Linear: `FOR-310`

Source memory:
`global/kanvas/ticket-drafts/draft-for-next-m90-runtime-telemetry-classification-guard-ticket`

Decision: `M90_RUNTIME_TELEMETRY_CLASSIFICATION_GUARD_APPLIED`

## Result

FOR-310 applies a classification guard to the M90 / MEP-NEXT Runtime Interactive
evidence. It keeps M90 useful as bounded Kadre runtime evidence while rejecting
unsafe claims that derived cache counters, observed-partial allocation counters,
or reporting-only frame timing are release-grade observed telemetry.

No renderer, shader, runtime, Kadre native behavior, cache implementation,
timing, visual threshold, scene status, fallback, or readiness score changed.

## Claim Scope

- Status: `pass`
- Claim level: `bounded-kadre-runtime-interactive-evidence`
- Linear scope: `FOR-193, FOR-194, FOR-195, FOR-196`
- Claim decision: `KEEP_M90_RUNTIME_TELEMETRY_BOUNDED_AND_REPORTING_ONLY`

## Mode Boundaries

| Mode | Native window | Opt-in | Release/CI boundary |
|---|---:|---:|---|
| demo | True | True | opens long window in CI: `False` |
| benchmark | True | True | `candidate-reporting-only`, release blocking: `False` |
| CI evidence | False | False | Checked-in validator: `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`; Kadre native submodule required: `False` |
| Optional direct refresh | False | True | `rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive`; CI gate: `False` |

## Telemetry Classification

| Counter family | Classification |
|---|---|
| `nativeFrameTiming` | `observed` |
| `nativeRouteAllocations` | `observed-partial` |
| `cacheHitsMisses` | `derived` |
| `resizeInvalidation` | `deterministic-derived` |

## Resource Policy

- Observed native route source: `mep-next.cache-observed-partial-native-route`
- Observed allocation/churn limitation count: `2`
- Derived ledger source: `reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json`
- Derived pipeline cache hits/misses: `179` / `1`
- Live route classification: `observed-partial`
- Missing broad cache-counter reason: `mep-next.native-cache-counter-unavailable`
- Live route pipeline cache hits/misses: `0` / `180`
- Resize/switch bounded growth: `True`, invalid reuse: `0`

## Performance Lane

- Lane: `frame.kadre-windowed`
- Gate phase: `candidate-reporting-only`
- Release blocking: `False`
- Samples: `3` frames, dropped: `1`
- p50 / p95: `17.1 ms` / `34.4 ms`

## Input And Scene Boundaries

- Real OS event injection claimed: `False`
- Real OS event reason: `mep-next.real-os-event-injection-not-claimed`
- Scene candidates: `5`
- Renderable scenes: `4`
- Unsupported scenes: `1`
- Unsupported fallback reason: `mep-next.scene-switch.expected-unsupported`
- Nested-rrect fallback: `nested-rrect-difference-clip`

## Policy Cases

| Case | Decision | Allowed | Reason |
|---|---|---:|---|
| Current M90 classification is allowed | `allowed-guarded` | True | M90 claims stay bounded by observed, observed-partial, derived, and unavailable sources. |
| Derived cache hits claimed as observed is forbidden | `forbidden` | False | Cache hits/misses are derived from M85, not fully observed on the native route. |
| Native window required in CI is forbidden | `forbidden` | False | CI evidence must stay headless and must not require a native window. |
| M90 reporting lane as release blocking is forbidden | `forbidden` | False | M90 frame.kadre-windowed timing remains candidate/reporting-only. |
| Real OS event injection claim is forbidden | `forbidden` | False | M90 does not claim real OS/window-manager event injection. |
| Dropping unsupported scene boundary is forbidden | `forbidden` | False | Scene switching must preserve the expected unsupported nested-rrect fallback. |

## Validation

- `rtk python3 scripts/validate_for310_m90_runtime_telemetry_classification_guard.py`
- `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk python3 -m json.tool reports/wgsl-pipeline/m90-runtime-interactive/m90-runtime-telemetry-classification-guard-for310.json`
- `rtk git diff --check origin/master...HEAD`

Optional/provisioned refresh:

- `rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive`
- Required for this guard: `False`
- Reason: The checked-in guard validates existing M90 evidence; direct regeneration may require Kadre local source substitution or unpublished artifacts.
