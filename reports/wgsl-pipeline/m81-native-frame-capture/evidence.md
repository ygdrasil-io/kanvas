# M81 Native Frame Artifact Capture

Status: `offscreen-texture-readback-produced`

M81 packages the current M69/M70 Kadre/WebGPU native evidence into one PM-visible frame capture artifact set.
The produced PNG is a real `wgpu4k` native offscreen texture readback. It is not a window-surface screenshot or readback.

## PM Outcome

- Capture status: `offscreen-texture-readback-produced`
- Presented frames: `180` / `180`
- Native/window-surface readback: `false`
- Offscreen texture readback: `true`
- Refusal reason: `m81.window-surface-readback-not-implemented`
- Refusal reasons: `m81.window-surface-readback-not-implemented`
- Adapter/backend: `AdapterInfo(architecture=, description=, device=Apple M2 Max, subgroupMaxSize=0, subgroupMinSize=0, vendor=, isFallbackAdapter=false)` / `wgpu4k-native`
- Surface: `640` x `420` `BGRA8Unorm`
- Capture image: `reports/wgsl-pipeline/m70-kadre-native/native-demo-readback.png`
- Capture format: `RGBA8Unorm`
- Capture checksum/nontransparent pixels: `-1532881463102611126` / `268800`

## Linear Scope

- `FOR-97`
- `FOR-139`
- `FOR-140`
- `FOR-141`
- `FOR-142`
- `FOR-143`

## Artifacts

- `reports/wgsl-pipeline/m70-kadre-native/native-demo-readback.png`
- `reports/wgsl-pipeline/m70-kadre-native/native-demo.json`
- `reports/wgsl-pipeline/m70-kadre-live-runtime/route-status.json`
- `reports/wgsl-pipeline/m69-kadre-native/native-smoke.json`
- `reports/wgsl-pipeline/m81-native-frame-capture/evidence.json`
- `reports/wgsl-pipeline/m81-native-frame-capture/evidence.md`

## Non-Claims

- No system screenshot capture is claimed.
- No window-surface readback is claimed.
- No new release-grade frame/FPS gate is claimed.

## Validation

```bash
rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM81NativeFrameCapture
python3 -m json.tool reports/wgsl-pipeline/m81-native-frame-capture/evidence.json >/dev/null
```
