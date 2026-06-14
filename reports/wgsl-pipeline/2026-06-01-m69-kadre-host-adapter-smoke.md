# M69 Kanvas/Kadre Host Adapter Smoke

Status: `native-runnable`

M69 replaces the M68 generic native-launch blocker with a concrete route decision. Kadre source APIs are audited from `external/poc-koreos`, Kanvas headless/offscreen pixels are reused from M65 as bridge evidence, and the native lane now runs a bounded standalone Kadre/AppKit/Metal WebGPU present loop when host-local evidence is available.

## Route Decision

- Route status: `native-runnable`
- Reason: `m69.kadre-native-presented-frames`
- Native presented: `True`
- Native presentation reason: `none`
- Native presented frames: `3`
- Native claim: `bounded standalone WGSL present loop; Kanvas display-list replay is not claimed`
- Headless pixel proof: `True`
- Kadre source audit ready: `False`
- Native presentation source ready: `False`

## Host Contract

| Capability | Kadre evidence | Evidence readiness | Kanvas adapter status | Native presented |
|---|---|---|---|---:|
| `surfaceCreation` | `available-in-window-api-and-wgpu4k-sample` | `native-runnable` | `native-kadre-standalone-present-loop-implemented` | `True` |
| `resize` | `available-in-window-and-event-api` | `partial` | `route-contract-defined` | `False` |
| `present` | `available-in-wgpu4k-sample` | `native-runnable` | `native-kadre-standalone-present-loop-implemented` | `True` |
| `input` | `available-in-event-api` | `partial` | `route-contract-defined` | `False` |
| `frameClock` | `available-as-redraw-loop-and-frame-tracer` | `partial` | `route-contract-defined` | `False` |
| `diagnostics` | `partial-host-diagnostics-available` | `native-runnable` | `native-kadre-standalone-present-loop-implemented` | `True` |
| `export` | `not-a-kadre-core-feature` | `ready` | `headless-export-ready-native-export-pending` | `False` |
| `nativePresentation` | `host-capable-via-sample` | `native-runnable` | `native-kadre-standalone-present-loop-implemented` | `True` |

## First Scene Route

- Scene id: `m69-first-kanvas-kadre-host-adapter-scene`
- Claim level: `native-runnable`
- Native presented: `True`
- Native presentation reason: `m69.standalone-wgsl-present-loop`
- Source headless frame count: `120`

| Feature | Status | Artifact coverage |
|---|---|---:|
| animated transform | `source-evidence-ready` | 3/3 |
| gradient or bitmap | `source-evidence-ready` | 4/4 |
| simple shape/path | `source-evidence-ready` | 3/3 |

## Telemetry

- Native frame timing claim: `present-call-duration-only`
- Native surface: `640` x `420` `BGRA8Unorm`
- Native first/average present duration: `35.1017` / `14.9912` ms
- Headless slot count: `3`
- Headless median frame min/max: `9.2756` / `13.202` ms
- Headless p95 max: `26.0364` ms

## Artifacts

- `reports/wgsl-pipeline/m69-kadre-host-adapter/contract.json`
- `reports/wgsl-pipeline/m69-kadre-host-adapter/route-status.json`
- `reports/wgsl-pipeline/m69-kadre-host-adapter/scene-route.json`
- `reports/wgsl-pipeline/m69-kadre-host-adapter/telemetry.json`
- `reports/wgsl-pipeline/m69-kadre-host-adapter/bridge-smoke.json`
- `reports/wgsl-pipeline/m69-kadre-native/native-smoke.json`

## Non-Claims

- No native screenshot or frame PNG is claimed yet.
- Native timing is present-call duration only, not a release-grade FPS claim.
- The native smoke presents standalone generated WGSL, not Kanvas display-list replay.
- Input-driven interaction and broad Kanvas display-list replay remain outside M69.

## Validation

```bash
rtk ./gradlew --no-daemon :kadre-runtime:runM69KadreNativeSmoke
rtk ./gradlew --no-daemon pipelineM69KadreHostAdapterSmoke
```
