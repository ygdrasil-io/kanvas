# M79 Bitmap Replay V1

Status: `bounded-fixture-backed-bitmap-replay-evidence`

M79 adds typed `BitmapRect` replay commands backed by deterministic in-repo fixtures.
Mipmap and out-of-scope bitmap sampler behavior remains an explicit expected-unsupported row.

## PM Outcome

- Pack id: `m79-bitmap-replay-v1`
- Scenes: `4`
- Renderable: `3`
- Fixture-backed bitmap commands: `3`
- Nearest sampler commands: `2`
- Linear sampler commands: `1`
- ClipRect intersect commands: `1`
- SrcOver commands: `4`
- Partial-alpha commands: `2`
- Expected unsupported: `1`
- Failed: `0`
- Readiness delta: `+0%`

## Scene Summary

| Scene | Status | Fixture-backed bitmap commands | ClipRect | Nearest | Linear | SrcOver | Partial alpha | CPU checksum | Sampled pixels | Reason |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| `m79-bitmap-fixture-nearest-replay-v1` | `renderable` | `1` | `0` | `1` | `0` | `1` | `0` | `2208507294891662972` | `75428` | `m79.replay-scene-fixture-bitmap-renderable` |
| `m79-bitmap-fixture-linear-alpha-replay-v1` | `renderable` | `1` | `0` | `0` | `1` | `2` | `1` | `-8675548398312149882` | `71603` | `m79.replay-scene-fixture-bitmap-renderable` |
| `m79-bitmap-fixture-clipped-nearest-replay-v1` | `renderable` | `1` | `1` | `1` | `0` | `1` | `1` | `-2224348050358112279` | `27468` | `m79.replay-scene-fixture-bitmap-renderable` |
| `m79-bitmap-mipmap-sampler-refusal-v1` | `expected-unsupported` | `0` | `0` | `0` | `0` | `0` | `0` | `0` | `0` | `m79.bitmap.unsupported-sampler.mipmap` |

## Non-Claims

- M79 covers owned fixture-backed `BitmapRect` replay scenes only.
- M79 does not add arbitrary texture upload, mipmap, tile modes, codec decode, or color-managed image decode.
- Expected-unsupported bitmap sampler rows are refusal evidence, not failures.

## Validation

```bash
rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM79BitmapReplay
python3 -m json.tool reports/wgsl-pipeline/m79-bitmap-replay/evidence.json >/dev/null
```
