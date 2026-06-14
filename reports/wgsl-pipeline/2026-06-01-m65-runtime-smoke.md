# M65 Runtime Smoke Lane

Status: reporting-only smoke evidence, not a performance release claim.

## Outputs

- Telemetry: `reports/wgsl-pipeline/m65-runtime-smoke/telemetry.json`
- Slots: `reports/wgsl-pipeline/m65-runtime-smoke/slots.json`
- Artifact root: `reports/wgsl-pipeline/m65-runtime-smoke/artifacts`

## Host Decision

- Kadre remains the selected live/native host.
- This lane is headless/offscreen because Kadre is not wired into Kanvas as a submodule or published dependency in this branch.
- Live Kadre presentation is refused with `m65.kadre-host-not-wired`; M67/M68 can promote it after the host bridge lands.

## Slot Results

| Slot | Status | Runtime route | Refusal | Median frame ms | P95 frame ms | Nonblank |
|---|---|---|---|---:|---:|---|
| `baseline` | `pass` | `headless.offscreen.smoke-renderer.v1` | `none` | 9.2756 | 12.2369 | True |
| `m63` | `expected-unsupported` | `headless.offscreen.slot-preview` | `m65.runtime-display-list-replay-not-wired` | 13.202 | 26.0364 | True |
| `m64` | `expected-unsupported` | `headless.offscreen.slot-preview` | `m65.runtime-display-list-replay-not-wired` | 12.8265 | 17.2254 | True |

## Non-Claims

- No Kadre-hosted frame loop is claimed.
- No WebGPU adapter timing or release FPS gate is claimed.
- M63/M64 slots keep stable runtime-replay refusals while linking their existing generated source artifacts.
