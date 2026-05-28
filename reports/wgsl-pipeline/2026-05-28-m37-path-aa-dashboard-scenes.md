# M37 Path AA Dashboard Scene Rows

Date: 2026-05-28
Linear: GRA-179

## Outcome

The scene dashboard now includes two M37 Path AA rows:

- `path-aa-stroke-outline-fallback`: the selected GRA-178 fallback strategy for `StrokeRectGM` / `StrokeCircleGM`, with stable fallback reason `coverage.stroke-outline-edge-count-exceeded`.
- `path-aa-edge-budget-boundary`: the remaining broad Path AA boundary, preserving stable fallback reason `coverage.edge-count-exceeded`.

Both rows are expected-unsupported because M37 promoted a narrower fallback diagnostic, not adapter-backed rendered support.

## Evidence

- Selection: `reports/wgsl-pipeline/2026-05-28-m37-path-aa-target-selection.md`
- Implementation: `reports/wgsl-pipeline/2026-05-28-m37-path-aa-stroke-fallback.md`
- Inventory command: `rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest`
- Dashboard export: `build/reports/wgsl-pipeline-scenes/index.html`

## Validation

```text
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```
