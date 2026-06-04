# FOR-317 Runtime Cache Telemetry Closeout

Linear: `FOR-317`

Source memory: `global/kanvas/ticket-drafts/draft-for-next-runtime-cache-telemetry-closeout-ticket`

Decision: `RUNTIME_CACHE_TELEMETRY_CLOSEOUT_APPLIED`

## Result

FOR-317 closes the reporting chain for FOR-310 through FOR-316. It consolidates
the existing runtime cache telemetry artifacts without changing renderer code,
runtime behavior, Gradle gates, Kadre native execution, shader output, fallback
policy, release gate status, score, or readiness.

The claim boundary is narrow: FOR-315 provides one observed Kanvas headless
WebGPU cache counter artifact from `SkWebGpuDevice.cacheTelemetrySnapshot()`. M85 and M90 cache hit/miss
values remain derived selected-scene evidence, M90/M92 selected native create
rows remain observed-partial, and broad Kadre/wgpu4k cache callbacks remain
not-observable.

## Evidence Chain

| Issue | PR | Commits | Decision | Artifacts | Impact |
|---|---|---|---|---|---|
| `FOR-310` | [#1402](https://github.com/ygdrasil-io/kanvas/pull/1402) | `d32631641913`, `9c3ed8c192a2` | `KEEP_M90_RUNTIME_TELEMETRY_BOUNDED_AND_REPORTING_ONLY` | `reports/wgsl-pipeline/2026-06-04-for-310-m90-runtime-telemetry-classification-guard.md`<br>`reports/wgsl-pipeline/m90-runtime-interactive/m90-runtime-telemetry-classification-guard-for310.json`<br>`scripts/validate_for310_m90_runtime_telemetry_classification_guard.py` | Protected M90 as bounded reporting evidence: native frame timing observed, selected-route allocations observed-partial, cache hits/misses derived, broad callbacks unavailable. |
| `FOR-311` | [#1403](https://github.com/ygdrasil-io/kanvas/pull/1403) | `c2eab384cb06`, `22765e247785` | `KEEP_M92_RC_REQUIRED_VALIDATION_HEADLESS_AND_CHECKED_IN` | `reports/wgsl-pipeline/2026-06-04-for-311-m92-rc-headless-kadre-dependency-policy.md`<br>`reports/wgsl-pipeline/m92-kadre-runtime-rc/m92-rc-headless-kadre-dependency-policy-for311.json`<br>`scripts/validate_for311_m92_rc_headless_kadre_dependency_policy.py` | Kept required RC validation headless and checked-in; native Kadre refresh remains optional/provisioned. |
| `FOR-312` | [#1404](https://github.com/ygdrasil-io/kanvas/pull/1404) | `cde6f3a4a285`, `bb041e795f02` | `PM_BUNDLE_RUNTIME_HEADLESS_MANIFEST_AUDIT_APPLIED` | `reports/wgsl-pipeline/2026-06-04-for-312-pm-bundle-runtime-headless-manifest.md`<br>`reports/wgsl-pipeline/pm-bundle-runtime-headless-manifest-for312.json`<br>`scripts/validate_for312_pm_bundle_runtime_headless_manifest.py` | Proved the PM package carries the FOR-311 headless/optional-native boundary. |
| `FOR-313` | [#1405](https://github.com/ygdrasil-io/kanvas/pull/1405) | `9385f438c5a6`, `7fad651b616b` | `M90_RUNTIME_DOCS_KADRE_HEADLESS_POLICY_AUDIT_APPLIED` | `reports/wgsl-pipeline/2026-06-04-for-313-runtime-docs-kadre-headless-policy.md`<br>`reports/wgsl-pipeline/runtime-docs-kadre-headless-policy-for313.json`<br>`scripts/validate_for313_runtime_docs_kadre_headless_policy.py` | Aligned M90/MEP-NEXT docs and generated Markdown wording with required headless validation and optional/provisioned Kadre refresh. |
| `FOR-314` | [#1406](https://github.com/ygdrasil-io/kanvas/pull/1406) | `1bf5ff69e464`, `a5a5d3bcc7c0` | `runtime-cache-counter-source-map-applied` | `reports/wgsl-pipeline/2026-06-04-for-314-runtime-cache-counter-source-map.md`<br>`reports/wgsl-pipeline/runtime-cache-counter-source-map-for314.json`<br>`scripts/validate_for314_runtime_cache_counter_source_map.py` | Mapped observed candidate, derived ledger, observed-partial selected route counters, and not-observable Kadre/wgpu4k blockers. |
| `FOR-315` | [#1407](https://github.com/ygdrasil-io/kanvas/pull/1407) | `970eff34eeb6`, `a7ac5eb34b0b` | `headless-webgpu-cache-counter-evidence-applied` | `reports/wgsl-pipeline/2026-06-04-for-315-headless-webgpu-cache-counters.md`<br>`reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json`<br>`scripts/validate_for315_headless_webgpu_cache_counters.py` | Published the named observed Kanvas headless WebGPU artifact from cacheTelemetrySnapshot(). |
| `FOR-316` | [#1408](https://github.com/ygdrasil-io/kanvas/pull/1408) | `c67433d37ed4`, `e90157d73f17` | `RUNTIME_CACHE_COUNTER_EVIDENCE_BRIDGE_APPLIED` | `reports/wgsl-pipeline/2026-06-04-for-316-runtime-cache-counter-evidence-bridge.md`<br>`reports/wgsl-pipeline/runtime-cache-counter-source-map-for314.json`<br>`scripts/validate_for316_runtime_cache_counter_evidence_bridge.py` | Linked the FOR-315 observed artifact into the FOR-314 source map via observedEvidenceBridge while preserving M90/M92 boundaries. |

## Classification Boundary

- Observed: `for315.kanvas-headless-webgpu-cache-telemetry-snapshot` from `reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json`; source class `kanvas-headless-webgpu-observed`; cold misses `1`; warm hits `1`.
- Derived: M85 selected-scene ledger and M90 cache hit/miss counters remain derived, not observed broad callback telemetry.
- Observed-partial: M90 native route allocations and M92 selected-route shader/pipeline create rows remain selected-route creation/churn evidence.

Not observable rows:

- `broad-webgpu-cache-hit-callbacks`: `not-observable`, blocker `kadre-runtime.native-cache-counter-unavailable`, releaseGate `False`.
- `bind-group-cache-callbacks`: `not-observable`, blocker `kadre-runtime.native-cache-counter-unavailable`, releaseGate `False`.
- `native-resource-free-callbacks`: `not-observable`, blocker `kadre-runtime.resource-lifetime-observation-partial`, releaseGate `False`.
- `adapter-owned-memory-snapshots`: `not-observable`, blocker `kadre-runtime.resource-lifetime-observation-partial`, releaseGate `False`.

## Non-Changes

- readiness: `unchanged`.
- score: `unchanged`.
- releaseGateStatus: `unchanged`.
- rendererBehavior: `unchanged`.
- runtimeBehavior: `unchanged`.
- gradle: `unchanged`.
- shaders: `unchanged`.
- thresholds: `unchanged`.
- sceneStatus: `unchanged`.
- fallbacks: `unchanged`.
- kadreNativeBehavior: `unchanged`.
- kadreProvisioning: `unchanged`.
- supportClaims: `no-new-support-or-rendering-claim`.
- instrumentation: `no-new-runtime-instrumentation`.

## Conditions Before Broad Native Cache Callback Promotion

- `kadre-runtime.native-cache-counter-unavailable` (Kadre/wgpu4k native integration): Expose observed shader, pipeline, bind-group, and cache hit/miss callbacks from the native route.
- `kadre-runtime.resource-lifetime-observation-partial` (Kadre/wgpu4k native integration): Expose allocation/free/resource lifetime snapshots for surface resize and scene switch transitions.
- `kanvas-wide-cache-callback-promotion-evidence` (Kanvas follow-up): Add a new narrow ticket with observed native callback artifacts, source paths, headless validation policy, and unchanged renderer/gate boundaries before any broad promotion.

## Validation

- `rtk python3 scripts/validate_for317_runtime_cache_telemetry_closeout.py`
- `rtk python3 scripts/validate_for316_runtime_cache_counter_evidence_bridge.py`
- `rtk python3 scripts/validate_for315_headless_webgpu_cache_counters.py`
- `rtk python3 scripts/validate_for314_runtime_cache_counter_source_map.py`
- `rtk python3 -m json.tool reports/wgsl-pipeline/runtime-cache-telemetry-closeout-for317.json >/dev/null`
- `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check`
