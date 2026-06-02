# MEP-NEXT Runtime Interactive Kadre Slice

Status: `pass`

Scope: `FOR-193`, `FOR-194`, `FOR-195`, `FOR-196`.

This slice packages bounded interactive Kadre runtime evidence for the next MEP runtime lane: durable autonomous loop semantics, PM scene switching, bounded input controls, and live/cache-resource telemetry with observed vs derived sources kept separate.

## Modes

| Mode | Opens native window | Command | Purpose |
|---|---:|---|---|
| demo | yes, opt-in | `rtk ./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeInteractive` | PM manual window that stays alive until close or configured cap. |
| benchmark | yes, opt-in | `rtk ./gradlew --no-daemon :kadre-runtime:runMepNextKadreNativeBenchmark -PkadreMepNextFrames=300 -PkadreMepNextWarmupFrames=120` | Reporting-only native timing sample. |
| CI evidence | no | `rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive` | Headless JSON/Markdown proof. |

## FOR-193 Durable Loop

- Frame clock: `kadre.appkit.control-flow-poll`
- Autonomous frame clock: `true`
- Frame samples: `3`
- Dropped frames: `1`
- p50 / p95: `17.1000 ms` / `34.4000 ms`
- Native close policy: window close exits and releases resources; CI evidence uses a bounded close fixture.

## FOR-194 Scene Switching

- Candidate scenes: `5`
- Renderable scenes: `4`
- Unsupported scenes: `1`
- Switch events: `4`
- Last fallback reason: `nested-rrect-difference-clip`

## FOR-195 Input Controls

- Pointer events: `2`
- Keyboard events: `4`
- Last close event count: `1`
- Visible state changes: pointer control, play/pause, and selected scene id.
- Real OS event injection claimed: `false` (`mep-next.real-os-event-injection-not-claimed`).

## FOR-196 Resource/Cache Telemetry

| Counter family | Classification | Evidence |
|---|---|---|
| Native frame timing and surface statuses | observed | `reports/wgsl-pipeline/m84-native-frame-timing/evidence.json` |
| Native shader/pipeline creates in selected route | observed-partial | `M69KadreNativeSmoke` creates a module/pipeline per rendered frame. |
| Cache hits/misses, resource generations, invalid reuse | derived ledger | `reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json` |
| Broad WebGPU cache callbacks | unavailable | `mep-next.native-cache-counter-unavailable` |

## Artifacts

- `reports/wgsl-pipeline/m90-runtime-interactive/evidence.json`
- `reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json`
- `reports/wgsl-pipeline/m90-runtime-interactive/scene-switching.json`
- `reports/wgsl-pipeline/m90-runtime-interactive/pm-report.md`
- `reports/wgsl-pipeline/2026-06-02-mep-next-runtime-interactive.md`

## Non-Claims

- No broad SkCanvas/display-list replay claim.
- No CI real OS event injection/window-manager coverage claim.
- No release-grade `frame.kadre-windowed` FPS gate.
- No broad observed WebGPU cache telemetry claim.

## Validation

```bash
rtk ./gradlew --no-daemon :kadre-runtime:pipelineMepNextRuntimeInteractive
python3 -m json.tool reports/wgsl-pipeline/m90-runtime-interactive/evidence.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m90-runtime-interactive/telemetry-live.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m90-runtime-interactive/scene-switching.json >/dev/null
rtk git diff --check
```
