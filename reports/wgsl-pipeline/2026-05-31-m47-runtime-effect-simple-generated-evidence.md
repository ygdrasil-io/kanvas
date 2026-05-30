# M47 Runtime Effect Simple Generated Evidence

Date: 2026-05-31
Issue: GRA-274

## Outcome

`runtime-effect-simple` was converted from static dashboard evidence to
generated evidence through `pipelineGeneratedSceneExport` while preserving the
registered `SimpleRT` runtime-effect compatibility boundary.

The static row was removed from:

```text
reports/wgsl-pipeline/scenes/data/scenes.json
```

The generated row was added to:

```text
reports/wgsl-pipeline/scenes/generated/results.json
```

The scene id remains `runtime-effect-simple`, so the merged dashboard keeps the
same public row identity without duplicate scene ids.

## Preserved Support Semantics

| Field | Value |
|---|---|
| Status | `pass` |
| Priority | `P1` |
| Reference kind | `test-oracle` |
| CPU route | `cpu.runtime-effect.descriptor.simple_rt` |
| GPU route | `webgpu.runtime-effect.descriptor.simple_rt` |
| GPU coverage strategy | `webgpu.coverage.analytic-rect` |
| GPU pipeline key | `runtimeEffect=SimpleRT descriptor=runtime_simple_rt.wgsl state=[blendMode=kSrcOver]` |
| CPU fallback reason | `none` |
| GPU fallback reason | `none` |
| Threshold | `99.95` |
| CPU/GPU similarity | `100.0%` |
| Matching pixels | `4096 / 4096` |
| Max channel delta | `0` |

Tags changed from `source.static` / `maturity.static-evidence` to
`source.generated` / `maturity.generated-evidence`. Existing runtime-effect,
analytic-rect, route, reference, and risk tags were preserved.

## Runtime Effect Descriptor Evidence

This row is limited to one registered runtime-effect descriptor:

| Fact | Evidence |
|---|---|
| Runtime source | `SkBuiltinShaderEffectsSimple.SIMPLE_RT_SKSL` |
| CPU implementation id | `simple_rt` |
| WGSL implementation | `src/main/resources/shaders/runtime_simple_rt.wgsl` |
| Uniform layout | `gColor` at offset `0` |
| Uniform payload | four 32-bit floats, 16 bytes |
| Children | none |
| Parser/reflection evidence | `RuntimeEffectDescriptorWebGpuTest#runtime SimpleRT descriptor WGSL parses and reflects uniforms` |
| Missing descriptor policy | unregistered runtime shader fails with stable diagnostic `runtime effect descriptor missing` |

The support matrix evidence remains
`reports/wgsl-pipeline/2026-05-27-m23-runtime-effect-support-matrix.md`, where
`runtime.simple_rt` is descriptor-backed with CPU id `supported:kotlin/simple_rt`
and WGSL id `supported:wgsl/runtime_simple_rt`.

## Not Broad Runtime Effect Or SkSL Support

This conversion does not add arbitrary runtime-effect or SkSL support. Kanvas
continues to keep `SkRuntimeEffect` as a compatibility facade backed by
registered Kotlin/WGSL implementations. Runtime effects without a registered
WGSL descriptor retain explicit refusal diagnostics.

## Artifacts

Canonical artifacts remain under:

```text
reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/
```

Key files:

- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/skia.png`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/cpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/gpu.png`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/cpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/gpu-diff.png`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/route-cpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/route-gpu.json`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/stats.json`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/cpu-performance.json`
- `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/gpu-performance.json`

## Generation Command

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.RuntimeEffectDescriptorWebGpuTest
```

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-raster:test --tests org.skia.gpu.webgpu.RuntimeEffectDescriptorWebGpuTest
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

All commands passed.
