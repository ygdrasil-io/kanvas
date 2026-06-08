# M91 Image-Filter Backlog Slice

Status: generated evidence

This slice turns the M89 registry closeout M91 recommendation into an image-filter backlog contract. It does not promote support, add a generic DAG compiler, add CPU/readback fallback, weaken thresholds, or change render paths.

## Counters

- Image-filter rows: `5`
- Existing pass baseline rows: `2`
- Policy-only row-specific refusal rows: `2`
- Prepass gate refusal rows: `1`
- New support claims: `0`
- Readiness delta: `0.0`
- Dashboard promotions: `0`
- Threshold changes: `0`

## Clusters

### existingBoundedSupportBaseline

- `crop-image-filter-nonnull-prepass`: `pass`, fallback `none`, supportClaim `True`
- `image-filter-compose-cf-matrix-transform`: `pass`, fallback `none`, supportClaim `True`

### policyOnlyRowSpecificRefusals

- `skia-gm-imagemakewithfilter`: `expected-unsupported`, fallback `image-filter.imagemakewithfilter.row-specific-artifacts-required`, supportClaim `False`
- `skia-gm-offsetimagefilter`: `expected-unsupported`, fallback `image-filter.offset.row-specific-artifacts-required`, supportClaim `False`

### prepassGateRefusals

- `image-filter-crop-nonnull-prepass-required`: `expected-unsupported`, fallback `image-filter.crop-input-nonnull-prepass-required`, supportClaim `False`

## Next Tickets

### M91-IF-1

- Type: `policy-visibility`
- Scope: Keep ImageMakeWithFilterGM and OffsetImageFilterGM visible as row-specific expected-unsupported evidence until reference, CPU/GPU route, render, diff/stat, and graph ownership artifacts exist.
- Rows: `skia-gm-imagemakewithfilter`, `skia-gm-offsetimagefilter`
- Support claim allowed: `False`

### M91-IF-2

- Type: `dependency-gate-proof`
- Scope: Keep Crop(input=nonNull) blocked on explicit prepass/layer ownership evidence; do not add generic DAG compiler or CPU/readback fallback.
- Rows: `image-filter-crop-nonnull-prepass-required`
- Support claim allowed: `False`

### M91-IF-3

- Type: `bounded-promotion-candidate`
- Scope: Only after graph dump, intermediate texture ownership, CPU/GPU/reference/diff artifacts, route diagnostics, and performance impact exist, evaluate one bounded image-filter DAG candidate.
- Rows: `skia-gm-imagemakewithfilter`, `skia-gm-offsetimagefilter`, `image-filter-crop-nonnull-prepass-required`
- Support claim allowed: `False`

## Support Guard

- supportClaimsChanged: `False`
- dashboardPromotions: `False`
- thresholdsChanged: `False`
- policyOnlyRowsPromoted: `False`
- belowThresholdCountedAsProductionGap: `False`
- generalImageFilterDagSupportClaimed: `False`
- cpuReadbackFallbackAdded: `False`
- ganeshPort: `False`
- graphitePort: `False`
- dynamicSkSLCompiler: `False`
- dynamicSkSLIR: `False`
- dynamicSkSLVM: `False`

## Validation Commands

- `rtk python3 scripts/m91_image_filter_slice.py`
- `rtk ./gradlew --no-daemon pipelineM91ImageFilterSlice`
- `rtk git diff --check`
