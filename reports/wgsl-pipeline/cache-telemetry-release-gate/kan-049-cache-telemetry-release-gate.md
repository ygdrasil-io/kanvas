# KAN-049 Cache Telemetry Release-Gate Criteria

Status: `pass`

KAN-049 classifies cache/resource telemetry counters and freezes release-gate
promotion rules without adding a release-blocking cache telemetry gate.

## Summary

| Metric | Value |
| --- | ---: |
| Counter rows | `35` |
| Observed rows | `10` |
| Observed-partial rows | `7` |
| Derived rows | `12` |
| Unavailable rows | `6` |
| Release-blocking rows | `0` |
| M85 derived rows counted observed | `0` |

## Counter Classification

| Counter | Class | Lane | Gate treatment | Source |
| --- | --- | --- | --- | --- |
| headless-webgpu.shaderModuleCacheHits | `observed` | frame.headless-webgpu | reporting-only; eligible for a future candidate only through cacheTelemetry.observed-counter.candidate criteria | reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json |
| headless-webgpu.shaderModuleCacheMisses | `observed` | frame.headless-webgpu | reporting-only; eligible for a future candidate only through cacheTelemetry.observed-counter.candidate criteria | reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json |
| headless-webgpu.pipelineCacheHits | `observed` | frame.headless-webgpu | reporting-only; eligible for a future candidate only through cacheTelemetry.observed-counter.candidate criteria | reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json |
| headless-webgpu.pipelineCacheMisses | `observed` | frame.headless-webgpu | reporting-only; eligible for a future candidate only through cacheTelemetry.observed-counter.candidate criteria | reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json |
| headless-webgpu.resourceCacheHits | `observed` | frame.headless-webgpu | reporting-only; eligible for a future candidate only through cacheTelemetry.observed-counter.candidate criteria | reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json |
| headless-webgpu.resourceCacheMisses | `observed` | frame.headless-webgpu | reporting-only; eligible for a future candidate only through cacheTelemetry.observed-counter.candidate criteria | reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json |
| headless-webgpu.pipelineCreations | `observed` | frame.headless-webgpu | reporting-only; eligible for a future candidate only through cacheTelemetry.observed-counter.candidate criteria | reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json |
| headless-webgpu.shaderModuleCount | `observed` | frame.headless-webgpu | reporting-only; eligible for a future candidate only through cacheTelemetry.observed-counter.candidate criteria | reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json |
| headless-webgpu.pipelineCacheEntryCount | `observed` | frame.headless-webgpu | reporting-only; eligible for a future candidate only through cacheTelemetry.observed-counter.candidate criteria | reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json |
| headless-webgpu.resourceCacheEntryCount | `observed` | frame.headless-webgpu | reporting-only; eligible for a future candidate only through cacheTelemetry.observed-counter.candidate criteria | reports/wgsl-pipeline/headless-webgpu-cache-counters-for315.json |
| native-route.pipelineCacheHits | `observed-partial` | frame.kadre-windowed | reporting-only; not eligible until full observed cache hit/miss counters exist | reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json |
| native-route.pipelineCacheMisses | `observed-partial` | frame.kadre-windowed | reporting-only; not eligible until full observed cache hit/miss counters exist | reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json |
| native-route.shaderModuleCreates | `observed-partial` | frame.kadre-windowed | reporting-only; not eligible until full observed cache hit/miss counters exist | reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json |
| native-route.pipelineCreates | `observed-partial` | frame.kadre-windowed | reporting-only; not eligible until full observed cache hit/miss counters exist | reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json |
| native-route.bindGroupCreates | `observed-partial` | frame.kadre-windowed | reporting-only; not eligible until full observed cache hit/miss counters exist | reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json |
| native-route.textureUploads | `observed-partial` | frame.kadre-windowed | reporting-only; not eligible until full observed cache hit/miss counters exist | reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json |
| native-route.intermediateTextureBytes | `observed-partial` | frame.kadre-windowed | reporting-only; not eligible until full observed cache hit/miss counters exist | reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json |
| m85-derived.frameCount | `derived` | frame.kadre-windowed | PM diagnostics only; cannot count as observed cache readiness | reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json |
| m85-derived.pipelineCacheHits | `derived` | frame.kadre-windowed | PM diagnostics only; cannot count as observed cache readiness | reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json |
| m85-derived.pipelineCacheMisses | `derived` | frame.kadre-windowed | PM diagnostics only; cannot count as observed cache readiness | reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json |
| m85-derived.shaderModuleCount | `derived` | frame.kadre-windowed | PM diagnostics only; cannot count as observed cache readiness | reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json |
| m85-derived.pipelineCount | `derived` | frame.kadre-windowed | PM diagnostics only; cannot count as observed cache readiness | reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json |
| m85-derived.bindGroupCount | `derived` | frame.kadre-windowed | PM diagnostics only; cannot count as observed cache readiness | reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json |
| m85-derived.textureCount | `derived` | frame.kadre-windowed | PM diagnostics only; cannot count as observed cache readiness | reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json |
| m85-derived.textureUploadBytes | `derived` | frame.kadre-windowed | PM diagnostics only; cannot count as observed cache readiness | reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json |
| m85-derived.intermediateTextureBytes | `derived` | frame.kadre-windowed | PM diagnostics only; cannot count as observed cache readiness | reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json |
| m85-derived.bindGroupChurn | `derived` | frame.kadre-windowed | PM diagnostics only; cannot count as observed cache readiness | reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json |
| m85-derived.resourceGenerationCount | `derived` | frame.kadre-windowed | PM diagnostics only; cannot count as observed cache readiness | reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json |
| m85-derived.invalidResourceReuseCount | `derived` | frame.kadre-windowed | PM diagnostics only; cannot count as observed cache readiness | reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json |
| native-callbacks.broadWebGpuCacheHitCallbacks | `unavailable` | frame.kadre-windowed | blocked until the missing counter is observable from a named source artifact | reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json |
| native-callbacks.bindGroupCacheCallbacks | `unavailable` | frame.kadre-windowed | blocked until the missing counter is observable from a named source artifact | reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json |
| native-callbacks.nativeResourceFreeCallbacks | `unavailable` | frame.kadre-windowed | blocked until the missing counter is observable from a named source artifact | reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json |
| native-callbacks.adapterOwnedMemorySnapshots | `unavailable` | frame.kadre-windowed | blocked until the missing counter is observable from a named source artifact | reports/wgsl-pipeline/m92-kadre-runtime-rc/telemetry-classification.json |
| native-route.commandEncoderCount | `unavailable` | frame.headless-webgpu-or-frame.kadre-windowed | unavailable; must not be synthesized from code comments or render-pass diagnostics | .upstream/specs/skia-like-realtime/02-realtime-runtime-architecture.md |
| native-route.renderPassCount | `unavailable` | frame.headless-webgpu-or-frame.kadre-windowed | unavailable; must not be synthesized from code comments or render-pass diagnostics | .upstream/specs/skia-like-realtime/02-realtime-runtime-architecture.md |

## Gate Criteria

- Candidate name: `cacheTelemetry.observed-counter.candidate`.
- Release-blocking name: `cacheTelemetry.observed-counter.release-blocking`.
- Candidate promotion only allows `observed` counters with named source
  artifacts, metadata, variance policy, owner, quarantine rationale, and a
  negative fixture.
- Derived M85 ledgers, observed-partial native churn, and unavailable counters
  remain non-gating.

## Negative Fixture

`reports/wgsl-pipeline/cache-telemetry-release-gate/kan-049-cache-telemetry-negative-fixture.json` records expected-fail
promotion attempts for derived, observed-partial, unavailable, and unowned
observed cache telemetry.

## Gate-Freeze Delta

`reports/wgsl-pipeline/cache-telemetry-release-gate/kan-049-cache-telemetry-gate-freeze-delta.json` records no changed
release-blocking gates and no promoted cache telemetry gates.

## Non-Claims

- KAN-049 does not add a release-blocking cache telemetry gate.
- KAN-049 does not claim arbitrary scene cache readiness.
- KAN-049 does not claim device-lost recovery.
- KAN-049 does not promote frame.kadre-windowed FPS to release-grade.
- KAN-049 does not count M85 derived ledgers as observed WebGPU cache telemetry.
- KAN-049 does not count observed-partial native route creation/churn as broad cache hit/miss telemetry.
