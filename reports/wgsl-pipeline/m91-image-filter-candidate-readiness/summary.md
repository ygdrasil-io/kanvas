# M91 Image Filter Candidate Readiness

Status: generated evidence

This report evaluates the M91-IF-3 image-filter candidates for readiness only. It does not promote rendering support because every candidate still lacks required row-specific graph, ownership, route, render, diff/stat, or performance evidence.

## Counters

- Candidate rows: `3`
- Ready for promotion rows: `0`
- Blocked by missing evidence rows: `3`
- New support claims: `0`
- Readiness delta: `0.0`
- Dashboard promotions: `0`
- Threshold changes: `0`

## Required Promotion Evidence

- `row-specific graph dump`
- `intermediate texture ownership`
- `row-specific Skia/reference artifact`
- `CPU route evidence with fallbackReason=none`
- `WebGPU route evidence with fallbackReason=none`
- `CPU/GPU render artifacts`
- `CPU/GPU diff/stat artifacts`
- `performance impact evidence`

## Ranking Policy

- Source: `scripts/m91_image_filter_candidate_readiness.py`
- Contract: M91-IF-3 readiness ranking is a local PM ordering over selected image-filter refusal rows; it does not change registry support status, route status, dashboard counters, or selection membership.
- Row order: `skia-gm-offsetimagefilter, skia-gm-imagemakewithfilter, image-filter-crop-nonnull-prepass-required`

## Candidate Ranking

### 1. skia-gm-offsetimagefilter

- Source GM: `OffsetImageFilterGM`
- Candidate kind: `offset-image-filter-gm`
- Ready for promotion: `False`
- Status: `expected-unsupported`
- Fallback: `image-filter.offset.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-offsetimagefilter/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-offsetimagefilter/route-gpu.json`
- Promotion ticket: `M91-IF-3A`
- Reason: Smallest policy-only image-filter GM candidate, but still lacks row-specific graph/reference/render/diff/perf evidence.
- Present evidence:
  - `row-specific refusal link`
  - `CPU expected-unsupported route diagnostic`
  - `WebGPU expected-unsupported route diagnostic`
- Missing evidence:
  - `row-specific graph dump`
  - `intermediate texture ownership`
  - `row-specific Skia/reference artifact`
  - `CPU route evidence with fallbackReason=none`
  - `WebGPU route evidence with fallbackReason=none`
  - `CPU/GPU render artifacts`
  - `CPU/GPU diff/stat artifacts`
  - `performance impact evidence`

### 2. skia-gm-imagemakewithfilter

- Source GM: `ImageMakeWithFilterGM`
- Candidate kind: `image-make-with-filter-gm`
- Ready for promotion: `False`
- Status: `expected-unsupported`
- Fallback: `image-filter.imagemakewithfilter.row-specific-artifacts-required`
- CPU route: `expected-unsupported`
- GPU route: `expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-imagemakewithfilter/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/m91-image-filter-route-diagnostics/routes/skia-gm-imagemakewithfilter/route-gpu.json`
- Promotion ticket: `M91-IF-3B`
- Reason: Broader source-image ownership case; must follow explicit graph ownership and route evidence.
- Present evidence:
  - `row-specific refusal link`
  - `CPU expected-unsupported route diagnostic`
  - `WebGPU expected-unsupported route diagnostic`
- Missing evidence:
  - `row-specific graph dump`
  - `intermediate texture ownership`
  - `row-specific Skia/reference artifact`
  - `CPU route evidence with fallbackReason=none`
  - `WebGPU route evidence with fallbackReason=none`
  - `CPU/GPU render artifacts`
  - `CPU/GPU diff/stat artifacts`
  - `performance impact evidence`

### 3. image-filter-crop-nonnull-prepass-required

- Source GM: `Crop(input=nonNull)`
- Candidate kind: `crop-nonnull-prepass-gate`
- Ready for promotion: `False`
- Status: `expected-unsupported`
- Fallback: `image-filter.crop-input-nonnull-prepass-required`
- CPU route: `pass`
- GPU route: `expected-unsupported`
- CPU diagnostic: `reports/wgsl-pipeline/scenes/artifacts/image-filter-crop-nonnull-prepass-required/route-cpu.json`
- GPU diagnostic: `reports/wgsl-pipeline/scenes/artifacts/image-filter-crop-nonnull-prepass-required/route-gpu.json`
- Promotion ticket: `M91-IF-3C`
- Reason: Has bounded sibling evidence but remains an out-of-scope prepass gate for non-selected graph shapes.
- Present evidence:
  - `CPU oracle route with fallbackReason=none`
  - `static reference/CPU image artifacts`
  - `bounded M38 sibling prepass boundary`
  - `prepass gate proof`
- Missing evidence:
  - `row-specific graph dump`
  - `intermediate texture ownership`
  - `WebGPU route evidence with fallbackReason=none`
  - `CPU/GPU render artifacts`
  - `CPU/GPU diff/stat artifacts`
  - `performance impact evidence`

## Next Recommended Ticket

- ID: `M91-IF-3A`
- Row: `skia-gm-offsetimagefilter`
- Scope: Collect row-specific graph dump, intermediate ownership, Skia/reference artifacts, CPU/WebGPU fallbackReason=none routes, render/diff/stat artifacts, and performance impact for OffsetImageFilterGM before any support evaluation.
- Support claim allowed: `False`
- Promotion allowed without evidence: `False`

## Support Guard

- supportClaimAdded: `False`
- readinessMoved: `False`
- policyOnlyPromoted: `False`
- prepassGatePromoted: `False`
- thresholdChanged: `False`
- dashboardPromoted: `False`
- belowThresholdCountedAsProductionGap: `False`
- requiredSmokeCandidateAllowed: `False`
- generalImageFilterDagSupport: `False`
- genericImageFilterDagCompiler: `False`
- cpuReadbackFallbackAdded: `False`
- arbitraryLayerPrepass: `False`
- recursiveCropPrepass: `False`
- ganeshPort: `False`
- graphitePort: `False`
- dynamicSkSLCompiler: `False`
- dynamicSkSLIR: `False`
- dynamicSkSLVM: `False`

## Validation Commands

- `rtk python3 scripts/m91_image_filter_candidate_readiness.py`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterCandidateReadiness`
- `rtk git diff --check`
