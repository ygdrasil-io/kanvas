# M83 Kanvas Display-List Replay Through Kadre

Status: `native-display-list-produced`

M83 routes one bounded Kanvas display-list scene through the typed Kadre replay contract and the existing native WebGPU demo path.

## PM Outcome

- Pack id: `m83-display-list-replay-through-kadre-v1`
- Native display-list pixels produced: `true`
- Native scene contract: `m83-display-list-pm-scene-v1`
- Presented frames: `180`
- Capture nontransparent pixels: `268800`
- Scene count: `2`
- Renderable scenes: `1`
- Expected unsupported scenes: `1`
- Support-state mismatches: `0`

## Native Artifacts

- `reports/wgsl-pipeline/m83-display-list-replay/native-demo.json`
- `reports/wgsl-pipeline/m83-display-list-replay/native-demo-readback.png`

## Scene Summary

| Scene | Status | Commands | CPU checksum | Native route |
|---|---|---:|---:|---|
| `m83-display-list-pm-scene-v1` | `renderable` | `5` | `8858584405967304209` | `native-display-list-produced` |
| `m83-display-list-placeholder-refusal-v1` | `expected-unsupported` | `4` | `640574846146112387` | `expected-unsupported` |

## Stable Refusals

- `m83.text.placeholder-glyph-run-not-routed`
- `m83.filter.placeholder-dag-not-routed`
- `m83.runtime-effect.placeholder-descriptor-not-registered`

## Non-Claims

- M83 is not broad SkCanvas/display-list replay.
- Text, image-filter DAG, and arbitrary runtime-effect nodes are not routed by this sprint.
- Native timing remains reporting-only.

## Validation

```bash
rtk ./gradlew --no-daemon :kadre-runtime:runM70KadreNativeDemo -PkadreReplaySceneId=m83-display-list-pm-scene-v1 -PkadreDemoOutput=reports/wgsl-pipeline/m83-display-list-replay/native-demo.json -PkadreDemoCaptureOutput=reports/wgsl-pipeline/m83-display-list-replay/native-demo-readback.png
rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM83DisplayListReplay
python3 -m json.tool reports/wgsl-pipeline/m83-display-list-replay/evidence.json >/dev/null
```
