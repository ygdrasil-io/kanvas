# M85 Runtime Resource Lifetime And Cache Hardening

Status: `pass`

M85 makes the selected Kadre/WebGPU realtime route resource and cache ledger auditable without promoting unsupported recovery claims or claiming observed WebGPU cache telemetry.

## PM Outcome

- Lane: `frame.kadre-windowed`
- Scene contract: `m83-display-list-pm-scene-v1`
- Frames sampled: `180`
- Pipeline cache hits/misses: `179` / `1`
- Texture upload bytes: `1075200`
- Intermediate texture bytes: `0`
- Bind group churn: `0`
- Resource generations: `3`
- Invalid resource reuse count: `0`
- Observed runtime counters: `false`
- Counter source: `derived-selected-scene-resource-ledger`

## Resize / Surface Invalidation

- Reconfigure count from M82: `2`
- Reconfigure failures from M82: `0`
- Resource generations: `[1, 2]`
- Generations strictly advance: `true`
- Generation sequence monotonic: `true`
- Stable failure reason if reuse appears: `m85.invalid-resource-generation-reuse`

## Device Loss

- Status: `expected-unsupported`
- Reason: `m85.device-loss-recreate-observation-unsupported`
- Recreate claimed: `false`

## Cache Key Spaces

| Cache | Max distinct keys | Basis |
|---|---:|---|
| `shaderModule` | `1` | selected scene contract id + generated WGSL source id |
| `pipeline` | `1` | layout + shader entry point + color target + blend state |
| `bindGroup` | `1` | layout + uniform/storage/texture resource binding shape |
| `texture` | `2` | selected native offscreen readback texture + bitmap fixture handles |
| `intermediateTexture` | `0` | image-filter intermediate DAG nodes |
| `glyphAtlas` | `0` | glyph atlas pages |

## Validation

```bash
rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM85ResourceLifetimeCacheHardening
python3 -m json.tool reports/wgsl-pipeline/m85-resource-lifetime-cache/evidence.json >/dev/null
```
