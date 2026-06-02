# M87 Runtime Effect Live Editing

Status: `pass`

M87 promotes a selected registered runtime effect, `runtime.simple_rt`, into live-edit evidence with reflected uniform layout, bounded parameter metadata, telemetry rows, and CPU/GPU parity artifacts for two edited states.

## PM Outcome

- Effect: `runtime.simple_rt`
- Editable parameter: `gColor.b` in `[0.0, 1.0]`
- Frame updates: `2`
- Invalid values: clamped with `m87.runtime-effect.parameter-out-of-range`
- PipelineKey stable across uniform edits: `true`
- Arbitrary SkSL remains: `runtime-effect.arbitrary-sksl-unsupported`

## Reflection

- Shader: `gpu-raster/src/main/resources/shaders/runtime_simple_rt.wgsl`
- Binding: `uniforms@group=0,binding=0`
- Uniform offset: declared `0`, reflected `0`
- Verified: `true`
- Parser evidence: `RuntimeEffectDescriptorWebGpuTest#runtime SimpleRT descriptor WGSL parses and reflects uniforms`

## Edited State Parity

| State | gColor.b | Similarity | Max channel delta | Artifacts |
|---|---:|---:|---:|---|
| `m87-simple-rt-blue-low` | `0.25` | `100.00%` | `0` | `reports/wgsl-pipeline/m87-runtime-effect-live-editing/states/m87-simple-rt-blue-low/` |
| `m87-simple-rt-blue-high` | `0.82` | `100.00%` | `0` | `reports/wgsl-pipeline/m87-runtime-effect-live-editing/states/m87-simple-rt-blue-high/` |

## Validation

```bash
rtk ./gradlew --no-daemon :kadre-runtime:test :kadre-runtime:pipelineM87RuntimeEffectLiveEditing :gpu-raster:test --tests org.skia.gpu.webgpu.RuntimeEffectDescriptorWebGpuTest pipelinePmBundle
python3 -m json.tool reports/wgsl-pipeline/m87-runtime-effect-live-editing/evidence.json >/dev/null
```
