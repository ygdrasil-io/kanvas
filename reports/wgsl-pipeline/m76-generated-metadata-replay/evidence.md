# M76 Generated Metadata Replay

Status: `selected-metadata-replay-evidence`

M76 bridges selected generated dashboard metadata into typed Kadre replay contracts.
The contract is intentionally narrow: only known bounded scene metadata maps; unsupported metadata refuses with stable reasons.

## PM Outcome

- Pack id: `m76-generated-metadata-replay-v1`
- Source manifest: `reports/wgsl-pipeline/scenes/generated/results.json`
- Metadata rows: `6`
- Mapped replay rows: `4`
- Refused metadata rows: `2`
- Failed: `0`
- Readiness delta: `+0%`

## Source To Replay Routes

| Source scene | Mapping status | Reason | Replay scene | CPU route | GPU route |
|---|---|---|---|---|---|
| `solid-rect` | `metadata-mapped` | `m76.metadata.mapped-replay-contract` | `m76-solid-rect-metadata-replay-v1` | `cpu.descriptor.coverage-plan.solid-rect` | `webgpu.coverage.analytic-rect` |
| `linear-gradient-rect` | `metadata-mapped` | `m76.metadata.mapped-replay-contract` | `m76-linear-gradient-rect-metadata-replay-v1` | `cpu.shader.linear-gradient.rect` | `webgpu.generated.linear-gradient.rect` |
| `bitmap-rect-nearest` | `metadata-mapped` | `m76.metadata.mapped-replay-contract` | `m76-bitmap-rect-nearest-metadata-replay-v1` | `cpu.image-rect.strict-nearest` | `webgpu.image-rect.strict-nearest` |
| `gradient-color-filter-linear-kplus` | `metadata-mapped` | `m76.metadata.mapped-replay-contract` | `m76-gradient-color-filter-linear-kplus-metadata-replay-v1` | `cpu.shader.linear-gradient.color-filter.blend-kplus-oracle` | `webgpu.generated.linear-gradient.color-filter.blend-kplus` |
| `path-aa-convexpaths-edge-budget` | `expected-unsupported` | `m76.metadata.source-status-not-pass` | `` | `cpu.coverage.path-aa-oracle` | `webgpu.coverage.path-aa.expected-unsupported` |
| `runtime-effect-simple` | `expected-unsupported` | `m76.metadata.unsupported-route-family` | `` | `cpu.runtime-effect.simple-registered` | `webgpu.runtime-effect.simple-registered` |

## Mapping Rules

- Source status must be `pass`.
- Source metadata must match a known bounded replay template.
- Supported mapped families are current rect, linear-gradient, bitmap-nearest, and linear-gradient color-filter replay templates.
- Path AA, image-filter DAG, text/font, runtime-effect, complex clip, and unknown metadata refuse until separate replay commands exist.

## Non-Claims

- M76 does not add arbitrary generated scene replay.
- M76 does not add broad SkCanvas/display-list replay.
- M76 refusals are expected boundary evidence, not failures.

## Validation

```bash
rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM76GeneratedMetadataReplay
```
