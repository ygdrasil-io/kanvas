# M69 Kanvas/Kadre Host Adapter Smoke

Status: `headless-bridge`

M69 replaces the M68 generic native-launch blocker with a concrete route decision. Kadre source APIs are audited from `external/poc-koreos`, Kanvas headless/offscreen pixels are reused from M65 as bridge evidence, and native presentation remains explicitly unclaimed until a Kadre-produced frame exists.

## Route Decision

- Route status: `headless-bridge`
- Reason: `m69.kadre-contract-ready-m65-headless-pixel-proof`
- Native presented: `False`
- Native presentation reason: `m69.native-kanvas-kadre-present-not-implemented`
- Headless pixel proof: `True`
- Kadre source audit ready: `True`
- Native presentation source ready: `False`

## Host Contract

| Capability | Kadre evidence | Evidence readiness | Kanvas adapter status | Native presented |
|---|---|---|---|---:|
| `surfaceCreation` | `available-in-window-api-and-wgpu4k-sample` | `ready` | `route-contract-defined` | `False` |
| `resize` | `available-in-window-and-event-api` | `ready` | `route-contract-defined` | `False` |
| `present` | `available-in-wgpu4k-sample` | `ready` | `blocked-native-present-not-wired` | `False` |
| `input` | `available-in-event-api` | `ready` | `route-contract-defined` | `False` |
| `frameClock` | `available-as-redraw-loop-and-frame-tracer` | `ready-with-optional-gap` | `route-contract-defined` | `False` |
| `diagnostics` | `partial-host-diagnostics-available` | `ready` | `route-contract-defined` | `False` |
| `export` | `not-a-kadre-core-feature` | `ready` | `headless-export-ready-native-export-pending` | `False` |
| `nativePresentation` | `host-capable-via-sample` | `blocked-native-present` | `blocked-native-kanvas-frame-loop-not-implemented` | `False` |

## First Scene Route

- Scene id: `m69-first-kanvas-kadre-host-adapter-scene`
- Claim level: `headless-bridge`
- Native presented: `False`
- Source headless frame count: `120`

| Feature | Status | Artifact coverage |
|---|---|---:|
| animated transform | `source-evidence-ready` | 3/3 |
| gradient or bitmap | `source-evidence-ready` | 4/4 |
| simple shape/path | `source-evidence-ready` | 3/3 |

## Telemetry

- Native frame timing claim: `none`
- Headless slot count: `3`
- Headless median frame min/max: `8.9599` / `12.9778` ms
- Headless p95 max: `13.249` ms

## Artifacts

- `reports/wgsl-pipeline/m69-kadre-host-adapter/contract.json`
- `reports/wgsl-pipeline/m69-kadre-host-adapter/route-status.json`
- `reports/wgsl-pipeline/m69-kadre-host-adapter/scene-route.json`
- `reports/wgsl-pipeline/m69-kadre-host-adapter/telemetry.json`
- `reports/wgsl-pipeline/m69-kadre-host-adapter/bridge-smoke.json`

## Non-Claims

- No native Kadre screenshot or frame PNG is claimed.
- No native present timing is claimed.
- `headless-bridge` means the route is auditable and has M65 pixel proof, not that Kadre is presenting Kanvas yet.

## Validation

```bash
rtk ./gradlew --no-daemon pipelineM69KadreHostAdapterSmoke
```
