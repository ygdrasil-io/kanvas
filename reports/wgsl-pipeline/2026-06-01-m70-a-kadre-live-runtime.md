# M70-A Kadre Live Runtime Evidence

Status: `native-runnable`

M70-A/B/C turn the PM-validated M69 native smoke into a PM-visible live-runtime slice. The demo is still deliberately narrow: it renders one selected Kanvas-owned scene contract in a Kadre native WebGPU window and emits reporting-only telemetry.

## PM Outcome

- Scene: `m70-a-kanvas-owned-kadre-native-scene`
- Mode: `demo`
- Native presented: `True`
- Present-call completed: `True`
- Requested/presented frames: `12` / `12`
- Warmup frames: `3`
- Surface: `640` x `420` `BGRA8Unorm`
- Capture status: `produced`
- Capture reason: `m70.native-offscreen-texture-readback`
- Capture artifact: `reports/wgsl-pipeline/m70-kadre-native/native-demo-readback.png`
- Window-surface readback: `False`
- Surface status summary: success `12`, timeout `0`

## Linear Scope

- Epic: `FOR-60` M70-A Kadre Live Runtime V1.
- Runtime demo task: `FOR-61`.
- Selected Kanvas-owned scene route: `FOR-62`.
- Native capture/readback status: `FOR-63`.
- Runtime telemetry counters: `FOR-64`.
- PM bundle/readiness closeout: `FOR-65`.
- M70-B native surface success and presentation: `FOR-66`, `FOR-68`, `FOR-69`, `FOR-70`.
- M70-C native capture/readback evidence: `FOR-67`, `FOR-71`, `FOR-72`, `FOR-73`.

## Reporting-Only Runtime Telemetry

- Lane: `frame.kadre-windowed`
- Gate phase: `reportingOnly`
- Measured samples: `9`
- p50/p95/worst: `7.9826` / `9.6058` / `9.6058` ms
- Surface status samples: `12`

## Artifacts

- `reports/wgsl-pipeline/m70-kadre-native/native-demo.json`
- `reports/wgsl-pipeline/m70-kadre-native/native-demo-readback.png`
- `reports/wgsl-pipeline/m70-kadre-live-runtime/route-status.json`
- `reports/wgsl-pipeline/2026-06-01-m70-a-kadre-live-runtime.md`

## Non-Claims

- M70-A/B/C prove one selected Kadre native demo route only, with status determined by surface evidence.
- Native presentation is claimed only when the normalized surface status summary contains at least one success.
- Raw Kadre/wgpu4k API status names remain recorded separately when they differ from normalized evidence semantics.
- The capture artifact is a real wgpu4k native offscreen texture readback of the selected scene contract, not a system screenshot or window-surface readback.
- Broad Kanvas display-list replay is not claimed.
- Frame timing is reporting-only and not a release-grade FPS gate.

## Readiness Accounting

Readiness moves from approximately 64% to approximately 65%. The movement is intentionally conservative: M70-B/C confirm normalized native surface success and add a real offscreen native texture readback artifact when capture.realNativeReadback is true; broad display-list replay, input, and a release-grade frame gate are still not claimed.

| Area | Previous | Current | Reason |
|---|---:|---:|---|
| Rendering feature breadth | 60% | 60% | No new rendering-family support/refusal denominator changed. |
| Skia-like fidelity | 50% | 50% | No new selected GM/reference rows landed. |
| Real-time runtime | 72% | 75% | PM-visible Kadre demo task and one selected Kanvas-owned native scene contract now execute present calls in the windowed lane with normalized surface-success evidence and a produced native readback artifact. |
| Performance and cache readiness | 40% | 45% | `frame.kadre-windowed` now has reporting-only warmup/measured telemetry; no release-blocking FPS gate is enabled. |
| PM/demo operability | 100% | 100% | PM bundle includes M70-A/B/C route status, native demo telemetry, and the readback artifact. |

## Validation

```bash
rtk ./gradlew --no-daemon :kadre-runtime:compileKotlin
rtk ./gradlew --no-daemon -PkadreDemoFrames=12 -PkadreDemoWarmupFrames=3 :kadre-runtime:runM70KadreNativeDemo
rtk ./gradlew --no-daemon pipelineM70KadreLiveRuntimeEvidence
rtk ./gradlew --no-daemon pipelinePmBundle
```
