# FOR-315 Headless WebGPU Cache Counter Evidence

Linear issue: `FOR-315`.
Source memory: `global/kanvas/ticket-drafts/draft-for-next-headless-web-gpu-cache-counter-evidence-ticket`.

## Summary

`GeneratedSolidRectMigrationTest` captured cold and warm `SkWebGpuDevice.cacheTelemetrySnapshot()` values from an adapter-backed headless WebGPU run.
The generated solid-rect path stayed selected, cold pipeline cache misses were present, and warm pipeline cache hits increased.
This is Kanvas-owned headless WebGPU evidence, not a broad Kadre/wgpu4k native callback claim.

## Observed Counters

| Counter | Cold | Warm |
|---|---:|---:|
| `shaderModuleCacheHits` | 0 | 0 |
| `shaderModuleCacheMisses` | 28 | 28 |
| `pipelineCacheHits` | 0 | 1 |
| `pipelineCacheMisses` | 1 | 1 |
| `resourceCacheHits` | 0 | 0 |
| `resourceCacheMisses` | 0 | 0 |
| `pipelineCreations` | 1 | 1 |
| `shaderModuleCount` | 30 | 30 |
| `pipelineCacheEntryCount` | 1 | 1 |
| `resourceCacheEntryCount` | 0 | 0 |

## Scope

- Source class: `kanvas-headless-webgpu-observed`.
- Source API: `SkWebGpuDevice.cacheTelemetrySnapshot()`.
- Adapter: `Apple M2 Max`.
- Generated path: `generated` for `Rect + SolidColor + SrcOver`.
- Kadre/wgpu4k callback claim: none.
- Readiness, release gates, renderer behavior, Gradle wiring, shaders, thresholds, scene status, fallback policy, and Kadre native behavior: unchanged.

## Validation

- `rtk ./gradlew --no-daemon --rerun-tasks -Dkanvas.sceneEvidence.write=true :gpu-raster:test --tests org.skia.gpu.webgpu.GeneratedSolidRectMigrationTest`
- `rtk python3 scripts/validate_for315_headless_webgpu_cache_counters.py`
- `rtk python3 -m json.tool reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json >/dev/null`
- `rtk ./gradlew --no-daemon validateMepNextRuntimeInteractive`
- `rtk ./gradlew pipelineSceneDashboardGate`
- `rtk git diff --check`
