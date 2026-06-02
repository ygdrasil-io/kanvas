# RC-MEP Kadre Runtime Slice

Report id: `rc-mep-kadre-runtime-slice-v1`

Scope: `FOR-180`, `FOR-181`, `FOR-182`.

This report promotes the existing M83 complex display-list scene as the PM native Kadre scene, and keeps `frame.kadre-windowed` as an explicit candidate/reporting performance lane. It does not claim release-grade FPS.

## FOR-180 Native Window And Lifecycle

- PM native command: `./gradlew --no-daemon :kadre-runtime:runRcMepKadreNativePmDemo`
- Default PM duration: `3600` frames with `120` warmup frames.
- Selected scene: `m83-display-list-pm-scene-v1`
- Native status: `native-display-list-produced`
- Native presented: `true`
- Presented frames in checked-in evidence: `180`
- Close policy: `Window close exits the event loop after resource dispose.`
- Resize policy: `Positive-size resize reconfigures the WGPU surface.`
- Scale-factor policy: `Scale-factor changes reconfigure from current inner size.`
- Warning policy: `Warnings are non-blocking unless status is blocked or error is non-null.`

Operational note: the long PM command is intentionally manual because it opens a native window. Automated evidence generation uses the shorter checked-in M83/M84 artifacts.

## FOR-181 Complex Scene Promotion

- Scene contract: `m83-display-list-pm-scene-v1`
- Dashboard row: `reports/wgsl-pipeline/scenes/generated/results.json#m83-display-list-pm-scene`
- CPU route: `cpu.display-list.replay-oracle.bounded`
- GPU/native route: `webgpu.kadre.native.display-list-replay.bounded`
- Pipeline key: `source=kanvasDisplayList commands=clear+clipRect+linearGradient+bitmapRect+alphaRect state=[blendMode=kSrcOver]`
- Readback image: `reports/wgsl-pipeline/m83-display-list-replay/native-demo-readback.png`
- Readback nontransparent pixels: `268800`

Supported command mix:

| Command | Count | PM meaning |
|---|---:|---|
| `clear` | `1` | background clear establishes a deterministic frame base |
| `clipRect` | `1` | bounded clip is applied before drawing scene content |
| `linearGradient` | `1` | gradient panel proves non-solid shader-like paint |
| `bitmapRect` | `1` | deterministic fixture-backed image sampling |
| `alpha overlay` | `3` | SrcOver partial-alpha composition |

Non-claims:

- This is a bounded scene contract, not broad SkCanvas/display-list replay.
- Text, image-filter DAGs, unregistered runtime effects and arbitrary shader inputs remain explicit unsupported/refusal paths.
- The image artifact is a native offscreen WGPU readback for the selected scene, not a system screenshot of the window surface.

## FOR-182 `frame.kadre-windowed` Candidate Gate

- Lane: `frame.kadre-windowed`
- Gate status: `candidate-reporting-only`
- Gate phase: `candidate-reporting-only`
- Release blocking: `false`
- Counted as measured gate: `false`
- Warmup frames: `60`
- Measured samples: `120`
- p50 / p95 / worst: `16.6495 ms` / `18.1140 ms` / `18.3875 ms`
- Host: `Mac OS X 26.5 aarch64`, Java `25.0.1`
- Adapter family: `apple-silicon`

Proposed budgets for candidate observation:

| Metric | Candidate budget | Current evidence | Status |
|---|---:|---:|---|
| `measuredSampleCount` | `>= 120` | `120` | `pass` |
| `p50Ms` | `<= 18.0 ms` | `16.6495 ms` | `pass` |
| `p95Ms` | `<= 22.0 ms` | `18.1140 ms` | `pass` |
| `worstMs` | `<= 30.0 ms` | `18.3875 ms` | `pass` |

Gate decision: keep `frame.kadre-windowed` as `candidate-reporting-only`. It can become release-blocking only after adapter/JDK variance is accepted and the native smoke can be run reproducibly in the target release environment.

Quarantine/reporting reasons:

- `m84.reporting-only-until-owner-accepts-variance`

## Artifacts

- `reports/wgsl-pipeline/m83-display-list-replay/evidence.json`
- `reports/wgsl-pipeline/m83-display-list-replay/native-demo.json`
- `reports/wgsl-pipeline/m83-display-list-replay/native-demo-readback.png`
- `reports/wgsl-pipeline/m84-native-frame-timing/evidence.json`
- `reports/wgsl-pipeline/m84-native-frame-timing/negative-fixture.json`
- `reports/wgsl-pipeline/2026-06-02-rc-mep-kadre-runtime-slice.md`

## Validation

```bash
./gradlew --no-daemon :kadre-runtime:pipelineM83DisplayListReplay :kadre-runtime:pipelineM84NativeFrameTimingCandidate :kadre-runtime:pipelineRcMepKadreRuntimeSlice
python3 -m json.tool reports/wgsl-pipeline/m83-display-list-replay/evidence.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m83-display-list-replay/native-demo.json >/dev/null
python3 -m json.tool reports/wgsl-pipeline/m84-native-frame-timing/evidence.json >/dev/null
# Manual native PM window:
./gradlew --no-daemon :kadre-runtime:runRcMepKadreNativePmDemo
```
