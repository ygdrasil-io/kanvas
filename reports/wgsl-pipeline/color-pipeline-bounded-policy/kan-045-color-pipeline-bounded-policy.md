# KAN-045 Color Pipeline Bounded Policy

KAN-045 packages the bounded color pipeline policy from existing support and
refusal evidence. It keeps support limited to selected sRGB/premul rows and
keeps wide-gamut/F16 policy rows explicit refusals without changing renderer
behavior or thresholds.

## Summary

| Metric | Count |
|---|---:|
| Total rows | 4 |
| Support rows | 2 |
| Expected unsupported rows | 2 |
| Rows missing reference/CPU/GPU/diff/stat/route | 0 |
| Rows with semantic op mismatch | 0 |
| Rows with threshold changes | 0 |
| Rows with silent approximation | 0 |

## Policy Rows

| Row | Status | Category | CPU route | WebGPU route | Reason |
|---|---|---|---|---|---|
| `paint.src-over-alpha.rect-stack.v1` | `pass` | `supportable-bounded` | `cpu.paint.src-over.partial-alpha.rect-stack` | `webgpu.blend.src-over.partial-alpha.fixed-function` | `none` |
| `paint.color-filter.blend-kplus.rect.v1` | `pass` | `supportable-bounded` | `cpu.paint.color-filter.blend-kplus.direct-rect` | `webgpu.paint.color-filter.blend-kplus.solid-color` | `none` |
| `m63-wide-gamut-color-space-refusal` | `expected-unsupported` | `wide-gamut-color-space` | `cpu.paint.draw-paint.full-clip-oracle` | `webgpu.color.refuse.wide-gamut-color-space` | `color.color-space-wide-gamut-unsupported` |
| `non-arc-rec2020-f16-src-over-rect` | `expected-unsupported` | `f16-policy-candidate-refusal` | `current-kanvas-kotlin-cpu-rec2020-f16-src-over-samples` | `not-promoted-policy-evidence-only` | `color.f16-policy-candidate-worsens-reference` |

## Claim Guard

| Guard | Value |
|---|---|
| rowsMissingReferenceCpuGpuDiffStatsRoute | `[]` |
| rowsWithSemanticOpMismatch | `[]` |
| rowsWithThresholdChanges | `[]` |
| rowsWithColorPolicyChanges | `[]` |
| rowsWithSilentApproximation | `[]` |
| wideGamutRowsClaimingSupport | `[]` |
| f16RowsClaimingGlobalPolicyChange | `[]` |
| broadBlendOrColorClaims | `[]` |
| ganeshGraphiteClaims | `[]` |
| skslCompilerClaims | `[]` |

## Required Validation

- `validateKan045ColorPipelineBoundedPolicy`
- `:gpu-raster:pipelineConformanceTest -- includes SimpleSrcOverAlphaSceneEvidenceTest and SimpleColorFilterSceneEvidenceTest`
- `:gpu-raster:wgslValidateStrict -- generated/registered WGSL parser validation`
- `pipelinePmBundle`

## Validation

| Check | Status | Evidence |
|---|---|---|
| `bounded-srgb-premul-support` | `pass` | KAN-015 and KAN-016 have reference/CPU/GPU/diff/stat/routes, matching semantic ops, and no fallback. |
| `wide-gamut-refusal-visible` | `pass` | M63 wide-gamut color-space row remains expected-unsupported via color.color-space-wide-gamut-unsupported. |
| `f16-policy-candidate-refusal-visible` | `pass` | FOR-345 Rec.2020 kRGBA_F16Norm row rejects the straight-sRGB candidate because it worsens covered samples. |
| `no-threshold-or-policy-weakening` | `pass` | All guards report no threshold, color policy, renderer, shader, or fallback change. |

## Non-Claims

- KAN-045 does not add renderer, shader, selector, PipelineKey, threshold, or budget changes.
- KAN-045 does not claim wide-gamut general support, HDR, gainmap, all blend modes, or broad color management.
- KAN-045 does not silently approximate unsupported color-space or F16 policy rows.
- KAN-045 does not port Ganesh, Graphite, SkSL compiler, SkSL IR, or SkSL VM.
