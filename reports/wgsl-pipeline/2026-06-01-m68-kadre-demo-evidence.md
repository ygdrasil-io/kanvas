# M68 Kadre Native Demo Evidence

Status: `bridge-smoke-blocked-native-launch`

M68 now has Kadre source evidence through the `external/poc-koreos` submodule, but it does not yet have a Kanvas host adapter capable of presenting Kanvas-rendered frames in a Kadre window. This report therefore records the exact bridge status and refuses the native launch claim with `m68.kadre-host-adapter-not-implemented`.

## Host Contract Audit

- Kadre source build present: `True`
- Kadre submodule declared: `True`
- Kadre submodule commit: `950622b9941ab81f11a229bba183aa1c4bfbfe09`
- Native Kanvas launch status: `blocked`
- Native launch reason: `m68.kadre-host-adapter-not-implemented`

| Capability | Kadre status | Kanvas status |
|---|---|---|
| `surfaceCreation` | `available-in-kadre-sample` | `blocked-no-host-adapter` |
| `resize` | `available-in-kadre-api` | `blocked-no-host-adapter` |
| `present` | `available-in-wgpu4k-sample` | `blocked-no-host-adapter` |
| `input` | `available-in-kadre-api` | `blocked-no-runtime-control-binding` |
| `frameClock` | `available-as-redraw-loop-and-tracer` | `blocked-no-host-adapter` |
| `diagnostics` | `partial` | `needs-kanvas-telemetry-mapping` |
| `skikoOrWgpu4Hosting` | `wgpu4k-sample-ready` | `wgpu4k-host-contract-not-wired-to-kanvas-device` |

## Flagship Scene Readiness

These rows are source/evidence inputs for the future native scene, not a native rendered screenshot.

| Feature | Status | Native presented | Artifact coverage |
|---|---|---:|---:|
| animated transform | `bridge-smoke` | `False` | 3/3 |
| Path AA / stroke / clip | `source-evidence-ready` | `False` | 4/4 |
| image / bitmap sampling | `source-evidence-ready` | `False` | 4/4 |
| image-filter DAG | `source-evidence-ready` | `False` | 3/3 |
| text / glyph | `source-evidence-ready` | `False` | 4/4 |
| blend / color filter | `source-evidence-ready` | `False` | 3/3 |
| runtime-effect controls | `source-evidence-ready` | `False` | 2/2 |

## Telemetry Separation

- Measured source: `python evidence generator overlay planning loop`
- Measured frames: `120`
- p50 overlay planning: `0.0017 ms`
- p95 overlay planning: `0.0021 ms`
- Native frame timing claim: `none`
- Static host metadata: `Kadre` / `wgpu4k/WebGPU through future Kanvas host adapter`

## Artifacts

- `reports/wgsl-pipeline/m68-kadre-demo/kadre-host-audit.json`
- `reports/wgsl-pipeline/m68-kadre-demo/bridge-smoke.json`
- `reports/wgsl-pipeline/m68-kadre-demo/flagship-scene-evidence.json`
- `reports/wgsl-pipeline/m68-kadre-demo/route-summary.json`
- `reports/wgsl-pipeline/m68-kadre-demo/telemetry-overlay-sample.json`

## Blockers And Non-Claims

- No native Kanvas/Kadre windowed frame is claimed.
- No native screenshot/frame PNG is claimed.
- Existing dashboard rows remain source evidence until the Kanvas host adapter presents them through Kadre.
- Runtime-effect controls are source metadata only; live editing is not wired.

## Validation Issues

- Kadre file missing: readme -> external/poc-koreos/README.md
- Kadre file missing: settings -> external/poc-koreos/settings.gradle.kts
- Kadre file missing: facadeBuild -> external/poc-koreos/kadre/build.gradle.kts
- Kadre file missing: eventLoop -> external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/EventLoop.kt
- Kadre file missing: activeEventLoop -> external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/ActiveEventLoop.kt
- Kadre file missing: window -> external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Window.kt
- Kadre file missing: events -> external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/Events.kt
- Kadre file missing: handler -> external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/ApplicationHandler.kt
- Kadre file missing: rawHandles -> external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/RawHandles.kt
- Kadre file missing: frameTracer -> external/poc-koreos/kadre-core/src/commonMain/kotlin/org/graphiks/kadre/core/FrameTimingTracer.kt
- Kadre file missing: helloWindow -> external/poc-koreos/samples/hello-window/src/commonMain/kotlin/org/graphiks/kadre/samples/hellowindow/HelloApp.kt
- Kadre file missing: helloTriangle -> external/poc-koreos/samples/hello-triangle/src/main/kotlin/org/graphiks/kadre/samples/hellotriangle/Main.kt

## Validation

```bash
rtk ./gradlew --no-daemon pipelineM68KadreDemoEvidence
```
