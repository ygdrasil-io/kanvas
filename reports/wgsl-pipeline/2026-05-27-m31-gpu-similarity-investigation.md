# M31 GPU Similarity Regression Investigation

Date: 2026-05-27  
Linear: GRA-76  
Scope: classify similarity-floor failures from the reactivated GPU inventory lane.

## Inputs

- `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.json`
- `gpu-raster/build/reports/gpu-inventory/gpu-inventory-failure-classification.md`
- `gpu-raster/build/test-results/test/TEST-*.xml`

Run provenance:

- `rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest`

Observed similarity bucket count: `4` (`expected-unsupported-diagnostic=50`, `unsupported-image-filter=2`, `adapter-skip=48` unchanged).

## Per-Failure Classification

| Fixture family | Failing tests | Actual | Floor | Evidence artifact | Classification | Action |
|---|---|---:|---:|---|---|---|
| `DrawBitmapRect3` | `DrawBitmapRect3WebGpuTest`, `DrawBitmapRect3CrossBackendTest` | `97.15` | `99.95` | `gpu-raster/build/debug-images/3x3bitmaprect-{raster,gpu,diff}.png` | Rendered output exists but is below floor; treat as implementation regression candidate (not unsupported-path). | Keep failing inventory signal. No floor change in this ticket. |
| `DrawBitmapRectSkbug4734` | `DrawBitmapRectSkbug4734WebGpuTest`, `DrawBitmapRectSkbug4734CrossBackendTest` | `91.02` | `99.95` | `gpu-raster/build/debug-images/draw_bitmap_rect_skbug4734-{raster,gpu,diff}.png` | Rendered output exists but is below floor; treat as implementation regression candidate (not unsupported-path). | Keep failing inventory signal. No floor change in this ticket. |

## Decision Summary

- Decision class used for all four failures: **implementation regression candidate**.
- Rejected in this ticket:
  - blanket floor reduction;
  - reclassification as expected unsupported;
  - suppression from inventory.
- Smoke gate impact:
  - required smoke lane (`:gpu-raster:gpuSmokeTest`) remains stable and excludes these fixtures.
  - these regressions remain inventory-only blocking signals until fixed or explicitly rebaselined with visual justification.

## Follow-up

- Promotion policy ticket `GRA-78` must keep similarity regressions out of smoke promotion until evidence-backed resolution.
