# M64 Registered Runtime Effect Audit

Date: 2026-06-01
Linear: FOR-25, FOR-26, FOR-27, FOR-28, FOR-29

## Purpose

M64 promotes registered runtime-effect evidence without broadening Kanvas into
an arbitrary SkSL compiler. `SkRuntimeEffect` remains a compatibility facade:
only explicitly registered Kotlin/WGSL descriptors can become GPU-backed pass
rows.

## Current Support Matrix

The descriptor support matrix reports three known runtime effects:

| Stable id | Descriptor status | CPU support | GPU support | Decision |
|---|---|---|---|---|
| `runtime.simple_rt` | descriptor-backed | `supported:kotlin/simple_rt` | `supported:wgsl/runtime_simple_rt` | Promote as supported M64 pass row. |
| `runtime.spiral_rt` | dispatch-only; missing descriptor | `supported:kotlin/spiral_rt` | unsupported, WGSL implementation id missing | Promote as expected-unsupported M64 refusal. |
| `runtime.linear_gradient_rt` | dispatch-only; missing descriptor | `supported:kotlin/linear_gradient_rt` | unsupported, WGSL implementation id missing | Keep as audit nonclaim; it needs a separate row-specific artifact before dashboard promotion. |

Source: `reports/wgsl-pipeline/2026-05-27-m23-runtime-effect-support-matrix.md`.

## Candidate Audit

| Candidate | Status | Reference | CPU route | GPU route | Parser/reflection evidence | Fallback | Decision |
|---|---|---|---|---|---|---|---|
| `runtime-effect-simple` | pass | test-oracle | `cpu.runtime-effect.descriptor.simple_rt` | `webgpu.runtime-effect.descriptor.simple_rt` | `RuntimeEffectDescriptorWebGpuTest#runtime SimpleRT descriptor WGSL parses and reflects uniforms`; `gColor` offset `0` | `none` | Promote as `m64-simple-rt-descriptor-backed`. |
| `runtime.spiral_rt` | unsupported on GPU | support matrix / WebGPU diagnostic | `cpu.runtime-effect.dispatch.spiral_rt` | `webgpu.runtime-effect.refuse.missing-wgsl-descriptor` | none; WGSL descriptor missing | `runtime-effect.wgsl-descriptor-missing` | Promote as `m64-spiral-rt-wgsl-descriptor-refusal`. |
| arbitrary user SkSL | unsupported | policy boundary | `cpu.runtime-effect.refuse.arbitrary-sksl` | `webgpu.runtime-effect.refuse.arbitrary-sksl` | not applicable; no SkSL compiler exists | `runtime-effect.arbitrary-sksl-unsupported` | Promote as `m64-arbitrary-sksl-runtime-effect-refusal`. |
| `runtime.linear_gradient_rt` | unsupported on GPU | support matrix | `cpu.runtime-effect.dispatch.linear_gradient_rt` | missing WGSL descriptor | none; WGSL descriptor missing | `runtime-effect.wgsl-descriptor-missing` | Do not promote in M64: no row-specific artifacts are present. |

## Selected M64 Rows

Supported pass row:

- `m64-simple-rt-descriptor-backed`

Stable expected-unsupported rows:

- `m64-spiral-rt-wgsl-descriptor-refusal`
- `m64-arbitrary-sksl-runtime-effect-refusal`

The expected-unsupported rows use generated policy-only artifacts. They do not
copy `runtime-effect-simple` render images, because the refusal evidence is the
support-matrix and diagnostic boundary rather than a visual SpiralRT or
arbitrary-SkSL render.

## Reflection And Uniform Packing Evidence

`m64-simple-rt-descriptor-backed` is backed by:

- shader module: `gpu-raster/src/main/resources/shaders/runtime_simple_rt.wgsl`;
- parser/reflection test:
  `RuntimeEffectDescriptorWebGpuTest#runtime SimpleRT descriptor WGSL parses and reflects uniforms`;
- reflected uniform layout: `uniforms.gColor` at offset `0`;
- uniform payload: four 32-bit floats, 16 bytes;
- rendering test:
  `RuntimeEffectDescriptorWebGpuTest#SimpleRT runtime shader renders through descriptor-backed WGSL path`;
- route diagnostics:
  `reports/wgsl-pipeline/scenes/artifacts/runtime-effect-simple/route-gpu.json`.

The M64 generated row carries the same metadata in generated route diagnostics.

For unsupported rows, route diagnostics carry `policyOnlyArtifact=true` and
stable fallback reasons. These rows are refusal evidence, not visual parity
evidence.

## Nonclaims

M64 does not claim:

- arbitrary SkSL parsing, compilation, IR, or VM support;
- user-provided WGSL runtime-effect implementations;
- silent approximation for unknown runtime effects;
- runtime-effect color filters, blenders, or image filters beyond explicitly
  registered descriptor-backed rows;
- GPU support for dispatch-only `runtime.spiral_rt` or
  `runtime.linear_gradient_rt`.

## Validation

Required implementation validation:

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelinePmBundle
rtk ./gradlew --no-daemon :gpu-raster:pipelineConformanceTest
rtk git diff --check
```
