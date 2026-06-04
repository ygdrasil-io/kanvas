# FOR-314 Runtime Cache Counter Source Map

Linear issue: `FOR-314`.
Source memory: `global/kanvas/ticket-drafts/draft-for-next-runtime-cache-counter-source-map-ticket`.

## Summary

This report maps the cache counter sources that already exist in Kanvas against the M85, M90, and M92 runtime evidence boundaries.
The readiness/score/release gate/renderer/shaders/Kadre native behavior do not change.
No renderer behavior, Gradle task, shader, threshold, scene status, fallback policy, readiness denominator, or Kadre provisioning changes are made.

## Source Classes

| Bucket | Class | Source artifact | Gate meaning |
|---|---|---|---|
| Kanvas headless WebGPU | `kanvas-headless-webgpu-observed-candidate` bridged by `kanvas-headless-webgpu-observed` | `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt`, `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/GeneratedSolidRectMigrationTest.kt`, and `reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json` | FOR-315 names checked-in observed Kanvas headless WebGPU evidence; not a Kadre native callback claim. |
| M85 ledger | `derived` | `reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json` | Deterministic selected-scene ledger only; not observed WebGPU runtime cache telemetry. |
| M90 native route | `observed-partial-native-route-with-derived-ledger` | `reports/wgsl-pipeline/m90-runtime-interactive/evidence.json`, `reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json` | Native route creation/churn is observed-partial; cache hits/misses stay derived from M85. |
| M92 Kadre blockers | `not-observable-kadre-blockers-with-observed-partial-creation-rows` | `reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json` | Broad Kadre/wgpu4k callbacks and native resource lifetime snapshots remain blocked. |

## Kanvas Headless Candidate And FOR-315 Bridge

- Snapshot API: `SkWebGpuDevice.GpuCacheTelemetrySnapshot` via `SkWebGpuDevice.cacheTelemetrySnapshot()`.
- Counter families: shader module, pipeline, resource, creation, and entry-count counters.
- Warm reuse source: `generated solid color rect reuses warm pipeline cache` in `gpu-raster/src/test/kotlin/org/skia/gpu/webgpu/GeneratedSolidRectMigrationTest.kt`.
- Observed bridge artifact: `reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json` with source class `kanvas-headless-webgpu-observed` and source API `SkWebGpuDevice.cacheTelemetrySnapshot()`.
- Observed bridge counters: cold `pipelineCacheMisses=1`, cold `pipelineCacheHits=0`, warm `pipelineCacheHits=1`.
- Non-claim: the FOR-315 bridge is Kanvas headless WebGPU evidence, not a broad Kadre/wgpu4k native cache callback claim.

## Existing Runtime Evidence

- M85 counter source: `derived-selected-scene-resource-ledger`; cache readiness gate counted: `False`.
- M90 native route allocations: `observed-partial`; M90 cache hits/misses: `derived`.
- M92 blocker count in this map: `2`.
- Derived and observed-partial counters are not promoted to observed unless the source map can point to a real artifact path.

## Non-Changes

- Readiness: unchanged.
- Score: unchanged.
- Release gate status: unchanged.
- Renderer behavior: unchanged.
- Shaders: unchanged.
- Kadre native behavior: unchanged.
- Gradle, thresholds, scene status, fallbacks, and Kadre provisioning: unchanged.

## Validation

- `rtk python3 scripts/validate_for314_runtime_cache_counter_source_map.py`
- `rtk python3 -m json.tool reports/wgsl-pipeline/runtime-cache-counter-source-map-for314.json >/dev/null`
- `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check origin/master...HEAD`
- `rtk git diff --check`
