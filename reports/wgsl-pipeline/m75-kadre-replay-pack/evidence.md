# M75 Kadre Replay Pack Evidence

Status: `multi-scene-evidence`

M75 turns the M73/M74 replay registry into a deterministic pack evidence lane.
It keeps the renderer claim narrow: the report summarizes typed replay contracts and does not claim broad SkCanvas/display-list replay.

## PM Outcome

- Pack id: `m75-kadre-replay-pack-evidence-v1`
- Source pack: `m73-kadre-replay-pack-v1`
- Scenes: `5`
- Renderable: `4`
- Expected unsupported: `1`
- Failed: `0`
- Readiness delta: `+0%`

## Scene Summary

| Scene | Status | CPU checksum | CPU nontransparent | Native evidence |
|---|---|---:|---:|---|
| `m72-solid-rect-replay-v1` | `renderable` | `6278179505048423371` | `5520` | `not-generated` |
| `m73-linear-gradient-rect-replay-v1` | `renderable` | `766127960160601107` | `5520` | `available` |
| `m73-bitmap-rect-nearest-replay-v1` | `renderable` | `-7802753964421501890` | `5520` | `available` |
| `m73-gradient-color-filter-kplus-replay-v1` | `renderable` | `-1850474044133108053` | `5520` | `not-generated` |
| `m73-nested-rrect-clip-refusal-v1` | `expected-unsupported` | `-5166315637198820477` | `5520` | `expected-unsupported` |

## Non-Claims

- M75 aggregates existing typed replay contracts; it does not add arbitrary SkCanvas op replay.
- Native/readback facts are surfaced only where a selected route produced artifacts.
- Expected-unsupported rows are healthy refusal evidence, not failures.

## Validation

```bash
rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM75ReplayPackEvidence
```
