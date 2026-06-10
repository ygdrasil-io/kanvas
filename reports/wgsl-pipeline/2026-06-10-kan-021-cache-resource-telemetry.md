# KAN-021 Cache/Resource Telemetry Selectionnee

KAN-021 selects `m83-display-list-pm-scene-v1` as the PM-visible cache/resource
slice for the selected realtime route.

Performance class: `derived-ledger-reporting`

## Result

The selected evidence is readable by PM and dev reviewers:

- M85 ledger: `derived`
- M90 native route allocations: `observed-partial`
- FOR-315 headless WebGPU snapshot: `observed`
- Broad Kadre/wgpu4k cache callbacks: `not-observable`

| Source | Class | Evidence | Meaning |
|---|---|---|---|
| M85 ledger | `derived` | `reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json` | Deterministic selected-scene ledger for cache hits/misses, resource generations, key spaces, resize invalidation, and invalid reuse. |
| M90 native route allocations | `observed-partial` | `reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json` | Native route shader/pipeline creation churn is observed, but it is not broad cache-hit telemetry. |
| FOR-315 headless WebGPU snapshot | `observed` | `reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json` | One Kanvas headless WebGPU warm-cache snapshot exists through `SkWebGpuDevice.cacheTelemetrySnapshot()`. |
| Broad Kadre/wgpu4k cache callbacks | `not-observable` | `reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json` | Broad native cache callbacks and resource lifetime snapshots remain blockers. |

The KAN-021 JSON lives at
`reports/wgsl-pipeline/m85-resource-lifetime-cache/kan-021-selected-telemetry.json`.

## Selected Counters

| Counter | Value |
|---|---:|
| Frames | `180` |
| Pipeline cache hits | `179` |
| Pipeline cache misses | `1` |
| Shader modules | `1` |
| Pipelines | `1` |
| Bind groups | `1` |
| Textures | `2` |
| Texture upload bytes | `1075200` |
| Intermediate texture bytes | `0` |
| Bind group churn | `0` |
| Resource generations | `3` |
| Invalid resource reuse | `0` |

## Cache And Invalidation Policy

- PipelineKey axes remain `layout-code-resource-pipeline-state-only`.
- Uniform values are not PipelineKey axes.
- The selected scene has six bounded key-space families.
- Resize evidence reports two reconfigurations, zero failures, monotonic
  resource generations, WebGPU resource invalidation, and zero invalid reuse.
- Stable invalid reuse reason remains `m85.invalid-resource-generation-reuse`.
- Device-loss recovery remains `expected-unsupported` with
  `m85.device-loss-recreate-observation-unsupported`.

## Non-Claims

No broad cache, device-loss recovery, cache eviction, or cache-readiness gate claim is added.

KAN-021 does not claim observed WebGPU runtime cache telemetry from the M85
ledger. It also does not promote observed-partial native allocation churn to
observed cache hits.

## Validation

```bash
rtk python3 scripts/validate_kan021_cache_resource_telemetry.py /Users/chaos/.codex/worktrees/7ac1/kanvas
rtk ./gradlew --no-daemon :validateKan021CacheResourceTelemetry
rtk ./gradlew --no-daemon :pipelineSceneDashboardGate :pipelinePmBundle
rtk git diff --check
```
