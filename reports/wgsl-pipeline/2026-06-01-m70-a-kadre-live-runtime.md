# M70-A Kadre Live Runtime Evidence

Status: `degraded`

M70-A turns the PM-validated M69 native smoke into a PM-visible live-runtime slice. The demo is still deliberately narrow: it renders one selected Kanvas-owned scene contract in a Kadre native WebGPU window and emits reporting-only telemetry.

## PM Outcome

- Scene: `m70-a-kanvas-owned-kadre-native-scene`
- Mode: `demo`
- Native presented: `False`
- Present-call completed: `True`
- Requested/presented frames: `12` / `12`
- Warmup frames: `3`
- Surface: `640` x `420` `BGRA8Unorm`
- Capture status: `unavailable`
- Capture reason: `m70.native-readback-not-available`
- Surface status summary: success `0`, timeout `12`

## Linear Scope

- Epic: `FOR-60` M70-A Kadre Live Runtime V1.
- Runtime demo task: `FOR-61`.
- Selected Kanvas-owned scene route: `FOR-62`.
- Native capture/readback status: `FOR-63`.
- Runtime telemetry counters: `FOR-64`.
- PM bundle/readiness closeout: `FOR-65`.

## Reporting-Only Runtime Telemetry

- Lane: `frame.kadre-windowed`
- Gate phase: `reportingOnly`
- Measured samples: `9`
- p50/p95/worst: `8.0323` / `9.7476` / `9.7476` ms
- Surface status samples: `12`

## Artifacts

- `reports/wgsl-pipeline/m70-kadre-native/native-demo.json`
- `reports/wgsl-pipeline/m70-kadre-live-runtime/route-status.json`
- `reports/wgsl-pipeline/2026-06-01-m70-a-kadre-live-runtime.md`

## Non-Claims

- M70-A proves one selected Kadre native demo route only, with status determined by surface evidence.
- If every surface status is timeout, M70-A proves present-call completion only, not confirmed native presentation.
- Broad Kanvas display-list replay is not claimed.
- Native capture remains unavailable unless capture.realNativeReadback is true.
- Frame timing is reporting-only and not a release-grade FPS gate.

## Readiness Accounting

Readiness moves from approximately 62% to approximately 64%. The movement is intentionally conservative: M70-A adds a PM-visible native demo command, one selected Kanvas-owned scene contract, and reporting-only `frame.kadre-windowed` telemetry, but the checked-in sample is degraded because every surface status is `timeout`; native capture remains unavailable and broad display-list replay is still not claimed.

| Area | Previous | Current | Reason |
|---|---:|---:|---|
| Rendering feature breadth | 60% | 60% | No new rendering-family support/refusal denominator changed. |
| Skia-like fidelity | 50% | 50% | No new selected GM/reference rows landed. |
| Real-time runtime | 65% | 72% | PM-visible Kadre demo task and one selected Kanvas-owned native scene contract now execute present calls in the windowed lane; checked-in surface statuses are timeout-only, so native presentation is not confirmed. |
| Performance and cache readiness | 40% | 45% | `frame.kadre-windowed` now has reporting-only warmup/measured telemetry; no release-blocking FPS gate is enabled. |
| PM/demo operability | 100% | 100% | PM bundle includes M70-A route status and native demo telemetry. |

## Validation

```bash
rtk ./gradlew --no-daemon :kadre-runtime:compileKotlin
rtk ./gradlew --no-daemon -PkadreDemoFrames=12 -PkadreDemoWarmupFrames=3 :kadre-runtime:runM70KadreNativeDemo
rtk ./gradlew --no-daemon pipelineM70KadreLiveRuntimeEvidence
rtk ./gradlew --no-daemon pipelinePmBundle
```
