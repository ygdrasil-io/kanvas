# M80 Shared Replay CPU Oracle

Status: `shared-replay-cpu-oracle-hardening`

M80 moves bounded replay CPU interpretation behind `org.skia.kadre.runtime.ReplayCpuOracle` so native smoke, tests, and M75-M79 evidence consume one typed reference result.
Readiness stays at `67.75%` because this is reference hardening, not new rendering breadth.

## PM Outcome

- Pack id: `m80-shared-replay-oracle-v1`
- Scenes covered: `15`
- Renderable scenes: `11`
- Expected unsupported scenes: `4`
- Failed scenes: `0`
- Failed validation rows: `0`
- Shared result fields: `deviceWidth`, `deviceHeight`, `sampledChecksum`, `nonTransparentPixels`, `bitmapSampledPixels`, `unsupportedReasons`, `commandFamilies`

## Supported Families

- `backgroundClear`
- `bitmapRect`
- `bitmapRectNearest`
- `clipRect`
- `fillRect`
- `linearGradientColorFilterPlus`
- `linearGradientRect`

## Validation Rows

| Row | Status | Source | Assertion |
|---|---|---|---|
| `fillrect-src-over-alpha` | `pass` | `ReplaySceneRegistryTest` | FillRect and SrcOver alpha facts are asserted through typed oracle fields. |
| `cliprect-intersection` | `pass` | `ReplaySceneRegistryTest` | ClipRect intersection changes sampled checksum/nontransparent facts and preserves clipped bitmap sample counts. |
| `bitmap-nearest` | `pass` | `ReplaySceneRegistryTest` | Nearest fixture-backed BitmapRect replay exposes deterministic checksum and sampled pixel facts. |
| `bitmap-linear-alpha` | `pass` | `ReplaySceneRegistryTest` | Linear bitmap sampling with alpha remains covered by deterministic oracle facts. |
| `bitmap-under-cliprect` | `pass` | `ReplaySceneRegistryTest` | BitmapRect under ClipRect reports fewer sampled pixels than the unclipped nearest fixture scene. |
| `expected-unsupported` | `pass` | `ReplaySceneRegistryTest` | Unsupported blend, clip, and bitmap sampler rows keep stable refusal reasons. |
| `invalid-fixture-and-bounds` | `pass` | `ReplaySceneRegistryTest` | Invalid fixture ids and malformed rect/bitmap bounds fail with stable messages before evidence generation. |

## Evidence Paths

- `reports/wgsl-pipeline/m75-kadre-replay-pack/evidence.json`
- `reports/wgsl-pipeline/m76-generated-metadata-replay/evidence.json`
- `reports/wgsl-pipeline/m77-blend-alpha-replay/evidence.json`
- `reports/wgsl-pipeline/m78-clip-replay/evidence.json`
- `reports/wgsl-pipeline/m79-bitmap-replay/evidence.json`

## Non-Claims

- M80 is not broad SkCanvas/display-list replay.
- M80 does not add arbitrary image/filter/path/saveLayer/text/runtime-effect execution.
- Expected-unsupported rows remain stable refusal evidence.

## Validation

```bash
rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM80SharedReplayOracle
python3 -m json.tool reports/wgsl-pipeline/m80-shared-replay-oracle/evidence.json >/dev/null
```
