# M64 Sprint Report and Readiness Accounting

Date: 2026-06-01
Linear: FOR-25, FOR-26, FOR-27, FOR-28, FOR-29, FOR-30

## PM Summary

M64 adds registered runtime-effect evidence to the Skia-like real-time renderer
target. The sprint promotes one descriptor-backed runtime-effect pass row and
two explicit refusal rows:

- supported `SimpleRT` descriptor-backed runtime shader;
- stable refusal for registered `SpiralRT` without a WGSL descriptor;
- stable refusal for arbitrary user SkSL runtime effects.

This keeps the product story honest: Kanvas can run known registered effects,
but it is not pretending to compile arbitrary SkSL.
The refusal rows use generated policy-only artifacts instead of copying
SimpleRT render images.

## Evidence Added

| Evidence | Path |
|---|---|
| Audit | `reports/wgsl-pipeline/2026-06-01-m64-runtime-effect-audit.md` |
| Contract | `reports/wgsl-pipeline/scenes/generated/m64-registered-runtime-effects-pack.json` |
| Generator task | `pipelineM64RegisteredRuntimeEffectsPack` |
| Gate counter | `m64Rows` and `m64.family.registered runtime effects` |
| PM bundle contract link | `m64GeneratedContractJson` |

## Readiness Movement

Readiness moves from approximately 35% to approximately 39%.

| Area | Weight | Count after M64 | Progress after M64 | Movement |
|---|---:|---:|---:|---|
| Rendering feature breadth | 30% | 6/10 | 60% | M64 adds a selected registered runtime-effect family contract. |
| Skia-like fidelity | 20% | 31/100 | 31% | One supported descriptor-backed runtime-effect row adds reference/CPU/GPU/diff evidence. |
| Real-time runtime | 20% | 1/10 | 10% | Registered runtime-effect parameter metadata is now available for the future live Kadre lane, but no Kadre/frame-loop capability landed in M64. |
| Performance and cache readiness | 15% | 7/20 | 35% | M64 keeps the existing SimpleRT estimated performance visible but does not copy it into new measured evidence or add a release gate denominator. |
| PM/demo operability | 15% | 11/20 | 55% | PM dashboard and bundle expose the M64 contract, parser/reflection evidence, and stable refusal boundaries. |

Weighted PM readiness after M64: **39%**.

## Residual Scope

Still open for later milestones:

- GPU WGSL descriptors for `runtime.spiral_rt` and
  `runtime.linear_gradient_rt`;
- runtime-effect color filters, blenders, and image-filter helpers;
- live runtime-effect parameter editing in the Kadre-hosted runtime;
- arbitrary SkSL remains out of scope by architecture decision.

## Validation

Required before merge:

```bash
rtk ./gradlew --no-daemon pipelineSceneDashboardGate pipelinePmBundle
rtk ./gradlew --no-daemon :gpu-raster:pipelineConformanceTest
rtk git diff --check
```
