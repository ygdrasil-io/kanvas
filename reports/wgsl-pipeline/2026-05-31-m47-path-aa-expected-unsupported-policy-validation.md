# M47 Path AA Expected-Unsupported Policy Validation

Date: 2026-05-31
Linear: GRA-277
Parent epic: GRA-272

## Decision

`path-aa-stroke-outline-fallback` and `path-aa-edge-budget-boundary` remain static expected-unsupported policy rows.

They should not move to generated refusal evidence in M47 because the rows represent stable WebGPU Path AA refusal policy and inventory boundaries, not a generated rendered scene result. Keeping them static makes the intentional non-support visible in the dashboard while avoiding a misleading generated support signal for Path AA breadth that remains outside the MVP boundary.

No broad Path AA support claim is introduced by this decision.

## Row Validation

| Row | Status | CPU/reference evidence | GPU refusal route | Stable fallback reason | Disposition |
|---|---|---|---|---|---|
| `path-aa-stroke-outline-fallback` | `expected-unsupported` | CPU oracle row with `cpu.path-coverage.stroke-outline-oracle` and 100% CPU/reference similarity | `webgpu.coverage.refuse`; pipeline key `coverageKind=pathStrokeOutlineOverflow,pathFillRule=winding,topology=triangleList` | `coverage.stroke-outline-edge-count-exceeded` | Keep static policy row |
| `path-aa-edge-budget-boundary` | `expected-unsupported` | CPU oracle/inventory boundary row with `cpu.path-coverage.raster-oracle` | `webgpu.coverage.refuse`; pipeline key `coverageKind=pathCoverageUnsupported,pathFillRule=winding,topology=triangleList` | `coverage.edge-count-exceeded` | Keep static policy row |

Both rows retain `risk.edge-budget`, `route.gpu.expected-unsupported`, `risk.expected-unsupported`, `source.static`, and `maturity.static-evidence` tags.

## Why Static Is Preferable Here

The generated scene schema permits generated `expected-unsupported` rows when a generated task owns the refusal artifact. These two Path AA rows are different:

- `path-aa-stroke-outline-fallback` summarizes the M37 bounded stroke-outline fallback policy where four inventory rows moved from the generic edge-budget diagnostic to `coverage.stroke-outline-edge-count-exceeded`.
- `path-aa-edge-budget-boundary` summarizes the remaining broad Path AA edge-budget boundary where 46 inventory rows stay under `coverage.edge-count-exceeded`.
- Both rows are policy/inventory sentinels for the M33/M37 Path AA boundary, not individual adapter-backed rendered scene promotions.
- A generated refusal row would imply task-owned generated scene output, but M47 did not add a new Path AA generator, fixture owner, or broader WebGPU AA implementation.

## Acceptance References

- `.upstream/specs/geometry-coverage/08-path-aa-mvp-boundary.md`: edge-budget refusals are explicit expected-unsupported inventory and must not enter required GPU smoke.
- `.upstream/specs/geometry-coverage/adr/0005-webgpu-aa-edge-budget.md`: the WebGPU AA edge budget remains 256 and over-budget paths must emit stable refusal diagnostics.
- `.upstream/specs/wgsl-pipeline/11-conformance-dashboard-generation.md`: `expected-unsupported` rows require a GPU section, stable fallback reason, CPU/reference evidence when possible, and inventory/spec references.
- `reports/wgsl-pipeline/2026-05-28-m37-path-aa-dashboard-scenes.md`: both dashboard rows were intentionally added as expected-unsupported because M37 promoted diagnostics, not rendered support.
- `reports/wgsl-pipeline/2026-05-28-m37-path-aa-stroke-fallback.md`: records 4 stroke-outline rows under `coverage.stroke-outline-edge-count-exceeded` and 46 broad rows under `coverage.edge-count-exceeded`.

## Dashboard Impact

M47 keeps the dashboard failure posture unchanged:

- `fail`: 0
- `tracked-gap`: 0
- Path AA static policy rows: 2
- No new required GPU smoke candidate depends on `coverage.edge-count-exceeded` or `coverage.stroke-outline-edge-count-exceeded`.

## Validation

```text
rtk git diff --check
rtk ./gradlew --no-daemon pipelineSceneDashboard
```

`rtk ./gradlew --no-daemon :gpu-raster:gpuInventoryTest` was not required for this patch because no inventory classifier or refusal policy changed. The existing M37 inventory evidence remains the owning source for the 4 / 46 diagnostic split.
