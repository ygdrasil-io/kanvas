# M78 Clip Replay

Status: `bounded-cliprect-intersect-replay-evidence`

M78 adds a typed `ClipRect` intersect replay command for simple axis-aligned rect clips.
Complex nested, rounded, and difference clips remain explicit expected-unsupported rows.

## PM Outcome

- Pack id: `m78-clip-replay-v1`
- Scenes: `3`
- Renderable: `2`
- ClipRect commands: `3`
- Clip intersect commands: `3`
- Expected unsupported: `1`
- Failed: `0`
- Readiness delta: `+0%`

## Scene Summary

| Scene | Status | ClipRect commands | FillRect commands | CPU checksum | Reason |
|---|---|---:|---:|---:|---|
| `m78-clipped-solid-rect-replay-v1` | `renderable` | `1` | `1` | `7657149549507766923` | `m78.replay-scene-cliprect-intersect-renderable` |
| `m78-clipped-alpha-gradient-replay-v1` | `renderable` | `2` | `2` | `-2941497501745922321` | `m78.replay-scene-cliprect-intersect-renderable` |
| `m78-complex-clip-refusal-v1` | `expected-unsupported` | `0` | `0` | `0` | `m78.clip.unsupported-complex-clip` |

## Non-Claims

- M78 covers bounded `ClipRect` intersect replay scenes only.
- M78 does not add broad clip-stack semantics, rounded clips, path clips, or difference clips.
- M78 does not change broad readiness or display-list replay scope.

## Validation

```bash
rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM78ClipReplay
```
