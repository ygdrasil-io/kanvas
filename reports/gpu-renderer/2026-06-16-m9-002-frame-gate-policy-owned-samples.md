# M9-002 Frame Gate Policy With Owned Samples

## Ticket

- `KGPU-M9-002 - Add release-blocking frame gate policy`

## Status

Implemented. The gate policy, negative fixtures, skipped diagnostics, and an
owned WebGPU adapter raw-sample artifact are present. The observed lane remains
non-release-blocking because current timing evidence is a candidate offscreen
wall-clock lane, not release authority.

## Files

- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/FrameGatePolicyContracts.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/telemetry/GPUFrameGatePolicyTest.kt`
- `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/OffscreenFrameSampleReport.kt`
- `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/OffscreenFrameSampler.kt`
- `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/offscreen/RenderGpuRendererSceneFrameSamplesMain.kt`
- `gpu-renderer-scenes/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/windowed/RunGpuRendererSceneKadreMain.kt`
- `gpu-renderer-scenes/src/kadre/kotlin/org/graphiks/kanvas/gpu/renderer/scenes/windowed/KadreWindowedSceneRunner.kt`
- `gpu-renderer-scenes/build.gradle.kts`
- `reports/gpu-renderer-scenes/frame-samples/frame-gate-blocker-board/frame-samples.json`

## Evidence Added

- `GPUFrameGateWarmupPolicy` records warmup frames, stable frames, metric
  source, threshold, coefficient-of-variation cap, quarantine rule, and
  rebaseline rule.
- `GPUFrameSampleProvenance` ties each lane to a named artifact, source kind,
  hash, scene id, adapter label, and raw/warmup/stable sample counts.
- `GPUFrameGatePolicyEvaluator` classifies candidate, release-blocking,
  reporting-only, quarantined, skipped, threshold-failed, and
  variance-exceeded lanes. Non-passing lanes never count as release-blocking.
- `GPUFrameGatePolicyTest` includes reporting-only, quarantine, skipped
  diagnostics, and negative threshold fixtures with `releaseBlocking=false`,
  `readinessDelta=0.0`, and `productRouteActivated=false`.
- `sampleGpuRendererSceneFrames` runs repeated WebGPU offscreen render+readback
  frames against one scene and writes raw wall-clock samples.
- The Kadre windowed session report can now carry raw wall-clock samples when
  Kadre is available. In this checkout the Kadre run stayed unavailable because
  `org.graphiks.kadre:*:1.0.0` artifacts were not resolvable.

## Raw Sample Artifact

- Artifact:
  `reports/gpu-renderer-scenes/frame-samples/frame-gate-blocker-board/frame-samples.json`
- SHA-256:
  `sha256:aacd64f3f65ae87feeaca7600434e2425ff44a7d3e0ddde5a1c66de57021530a`
- Diagnostics SHA-256:
  `sha256:3c0833d9261b03671f8f10ea5a725e3e70f086781da4cd8d97d73301501d07fa`
- Backend: `webgpu-offscreen`
- Adapter: `Apple M2 Max`
- Metric: `frame-time-ms`
- Metric source: `wall-clock-offscreen-render-readback`
- Raw samples: `60`
- Warmup frames: `3`
- Stable frames: `57`
- Stable mean: `2.5852ms`
- Stable stdev: `0.3806ms`
- Stable coefficient of variation: `0.1472`
- Stable min/max: `1.9839ms` / `3.4598ms`

The `0.1472` stable coefficient of variation keeps this as candidate evidence.
It is useful raw provenance, not a release-blocking performance pass.

## Representative Dump Lines

```text
frame-gate-policy id=m9-frame-gate-policy lanes=4 warmupFrames=3 stableFrames=4 metric=frame-time-ms source=wall-clock thresholdMs=16.6700 maxCov=0.0500 quarantineRule=known-env-or-adapter-issue-only rebaselineRule=versioned-artifact-required releaseBlocking=false productRouteActivated=false readinessDelta=0.0
frame-gate-lane id=owned-adapter-candidate state=candidate classification=candidate source=fixtures/m9-frame-gate-owned-samples.json kind=owned-adapter-frame-samples hash=sha256:test-owned-frame-samples scene=frame-gate-blocker-board adapter=apple-m2-max rawSamples=7 warmup=3 stable=4 meanMs=10.5000 cov=0.0000 thresholdStatus=within-threshold countsRelease=false skip=- quarantine=-
frame-gate-lane id=timestamp-query-missing state=release-blocking classification=skipped source=missing:timestamp-query-samples kind=owned-adapter-frame-samples hash=none scene=frame-gate-blocker-board adapter=none rawSamples=0 warmup=0 stable=0 meanMs=none cov=none thresholdStatus=not-evaluated countsRelease=false skip=missing-adapter-label,missing-source-hash,timestamp-query-unavailable quarantine=-
pm:gpu-renderer.frame-gate-policy classification=PolicyGated candidate=1 releaseBlocking=0 reportingOnly=1 quarantined=1 skipped=1 thresholdFailed=0 varianceExceeded=0 readinessDelta=0.0 releaseBlocking=false
nonclaim:no-release-blocking-gate no-readiness-delta no-product-activation no-correctness-claim no-derived-timings
```

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.telemetry.GPUFrameGatePolicyTest
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test --tests org.graphiks.kanvas.gpu.renderer.scenes.offscreen.RenderGpuRendererSceneOffscreenMainTest
rtk ./gradlew --no-daemon :gpu-renderer-scenes:test --tests org.graphiks.kanvas.gpu.renderer.scenes.windowed.RunGpuRendererSceneKadreMainTest
rtk ./gradlew --no-daemon :gpu-renderer-scenes:sampleGpuRendererSceneFrames -PsceneId=frame-gate-blocker-board -Pframes=60 -PsceneOutput=reports/gpu-renderer-scenes/frame-samples
```

## Non-Claims

- No release-blocking performance gate is added.
- No readiness delta is claimed.
- No product route is activated.
- No correctness support claim is made.
- Offscreen wall-clock render+readback samples are not GPU timestamp-query
  samples and do not replace a future Kadre/windowed lane.
