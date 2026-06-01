# M82 Kadre Input And Resize Runtime Loop

Status: `deterministic-kadre-runtime-event-model-and-telemetry`

M82 adds a bounded Kadre-backed input/resize runtime event model with deterministic CI evidence. The fixture mirrors the Kadre event families already used by the native route and records truthful non-claims because CI does not inject real desktop OS events.

## PM Outcome

- Pack id: `m82-kadre-input-resize-runtime-loop-v1`
- Runtime route: `deterministic.kadre-runtime.fixture`
- Events processed: `12`
- Pointer events: `2`
- Keyboard events: `3`
- Resize events: `1`
- Scale-factor events: `1`
- Surface reconfigures: `2`
- Dropped frames reported: `1`
- Unsupported diagnostics: `4`

## Controls

| Input | Runtime effect |
|---|---|
| Pointer move | Updates pointer position and normalized scene parameter. |
| `Space` | Toggles play/pause. |
| `O` | Toggles the route/debug overlay. |
| `R` | Resets animation phase and increments the reset counter. |
| Close request | Marks the runtime loop for shutdown. |

## Surface Reconfigure Evidence

| Sequence | Reason | Old | New | Invalidates resources |
|---:|---|---|---|---|
| `6` | `m82.surface-reconfigured.resize` | `640x420@1.0` | `800x500@1.0` | `true` |
| `7` | `m82.surface-reconfigured.scale-factor` | `800x500@1.0` | `800x500@2.0` | `true` |

## Non-Claims

- CI does not synthesize real OS pointer, keyboard, resize, or close events.
- Resize/scale evidence records deterministic reconfiguration behavior and resource generation changes, not full window-manager coverage.
- Dropped-frame telemetry is reporting-only, not a release-grade FPS gate.
- Unsupported Kadre event families use stable reason `m82.kadre-event-family-unsupported`.

## Validation

```bash
rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM82InputResizeRuntimeLoop
python3 -m json.tool reports/wgsl-pipeline/m82-kadre-input-resize-runtime-loop/evidence.json >/dev/null
```
