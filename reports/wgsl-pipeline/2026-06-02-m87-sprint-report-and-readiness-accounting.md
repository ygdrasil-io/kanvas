# M87 Sprint Report - Runtime Effect Live Editing

Date: 2026-06-02

## Scope

M87 promotes selected registered runtime-effect live editing for
`runtime.simple_rt`. The edited parameter is `gColor.b`; arbitrary SkSL and
registered effects without a WGSL descriptor remain expected-unsupported.

## Evidence

- `reports/wgsl-pipeline/m87-runtime-effect-live-editing/evidence.json`
- `reports/wgsl-pipeline/m87-runtime-effect-live-editing/evidence.md`
- `reports/wgsl-pipeline/m87-runtime-effect-live-editing/edited-states.json`
- `reports/wgsl-pipeline/m87-runtime-effect-live-editing/states/m87-simple-rt-blue-low/`
- `reports/wgsl-pipeline/m87-runtime-effect-live-editing/states/m87-simple-rt-blue-high/`

## PM Outcome

- one selected registered runtime effect has live parameter metadata;
- two edited parameter states have CPU/GPU/diff PNG artifacts and route JSON;
- WGSL layout evidence verifies `gColor` offset `0`;
- parameter edits keep uniform values out of `PipelineKey`;
- invalid values clamp with `m87.runtime-effect.parameter-out-of-range`;
- arbitrary SkSL refuses with `runtime-effect.arbitrary-sksl-unsupported`;
- missing WGSL descriptors refuse with `runtime-effect.wgsl-descriptor-missing`.

## Readiness Accounting

Readiness remains `67.75%`.

M87 is a selected runtime-effect operability slice. It does not add a new broad
runtime-effect family denominator, Skia-comparable GM denominator, observed
runtime cache gate, or release-blocking performance gate.

## Validation

```bash
./gradlew --no-daemon :kadre-runtime:test --tests org.skia.kadre.runtime.M87RuntimeEffectLiveEditingTest :kadre-runtime:pipelineM87RuntimeEffectLiveEditing
python3 -m json.tool reports/wgsl-pipeline/m87-runtime-effect-live-editing/evidence.json >/dev/null
```

Broader validation expected before merge:

```bash
./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.RuntimeEffectDescriptorWebGpuTest pipelinePmBundle
```
