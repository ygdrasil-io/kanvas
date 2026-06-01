# M77 Blend Alpha Replay

Status: `bounded-src-over-alpha-replay-evidence`

M77 makes blend and alpha explicit in the Kadre replay contract for bounded `SrcOver` scenes.
Unsupported blend modes refuse with a stable diagnostic instead of silently falling back.

## PM Outcome

- Pack id: `m77-blend-alpha-replay-v1`
- Scenes: `3`
- Renderable: `2`
- Partial-alpha scenes: `2`
- Expected unsupported: `1`
- Failed: `0`
- Readiness delta: `+0%`

## Scene Summary

| Scene | Status | SrcOver commands | Partial alpha commands | CPU checksum | Reason |
|---|---|---:|---:|---:|---|
| `m77-alpha-srcover-stack-replay-v1` | `renderable` | `2` | `1` | `8888851062735355593` | `m77.replay-scene-src-over-alpha-renderable` |
| `m77-gradient-alpha-srcover-replay-v1` | `renderable` | `2` | `2` | `3620605029146334392` | `m77.replay-scene-src-over-alpha-renderable` |
| `m77-multiply-blend-refusal-v1` | `expected-unsupported` | `0` | `0` | `0` | `m77.unsupported-blend-mode.kMultiply` |

## Non-Claims

- M77 covers bounded `SrcOver` replay scenes only.
- M77 does not add arbitrary blend modes; `kMultiply` is an expected unsupported fixture.
- M77 does not change broad readiness or display-list replay scope.

## Validation

```bash
rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM77BlendAlphaReplay
```
