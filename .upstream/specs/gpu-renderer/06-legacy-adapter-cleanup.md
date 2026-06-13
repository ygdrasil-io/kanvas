# Legacy Adapter Cleanup

Status: Draft
Date: 2026-06-13

## Purpose

Define the cleanup policy before and during migration from the current
`gpu-raster` path into the new GPU renderer module.

The cleanup direction is conservative: no rendering behavior change during the
cleanup phase. The goal is to isolate the legacy path and create a safe adapter
boundary, not to rewrite rendering logic in place.

## Current Pressure

The current WebGPU path concentrates routing, resources, pipelines, diagnostics,
coverage, glyphs, readback, and submission behavior in large legacy files such
as `gpu-raster/src/main/kotlin/org/skia/gpu/webgpu/SkWebGpuDevice.kt`.

That shape makes direct in-place Graphite-like refactoring risky. Cleanup must
create an adapter boundary before new GPU renderer behavior becomes default.

## Cleanup Strategy

Use a facade-and-adapter strategy:

```text
legacy Skia-like calls
  -> existing gpu-raster behavior
  -> adapter extracts normalized commands for shadow/evidence
  -> new gpu-renderer route remains opt-in until promoted
```

The old path remains the default during cleanup. The new module may be built
and tested beside it, but it must not change pixels until an implementation
ticket explicitly promotes a route.

## Allowed Cleanup Changes

Cleanup-only tickets may:

- add inventory and diagnostics around existing route decisions;
- add non-rendering wrappers around legacy entry points;
- add normalized command shadow dumps;
- add tests that prove old behavior is unchanged;
- move constants or diagnostic formatting when output remains compatible;
- introduce adapter interfaces used only by tests or shadow mode;
- document ownership boundaries.

## Forbidden Cleanup Changes

Cleanup-only tickets must not:

- change default rendering output;
- change fallback/refusal behavior;
- enable a new GPU route by default;
- silently route to CPU when GPU support is missing;
- move large rendering blocks without targeted tests;
- rename diagnostics without compatibility evidence;
- delete legacy behavior before the replacement route is promoted.

## Legacy Adapter Contract

The adapter owns translation from legacy state to `NormalizedDrawCommand`.

It must:

- resolve transform, clip, layer, material, bounds, and ordering state;
- strip direct `Sk*` dependencies before commands enter the new core;
- preserve legacy provenance in diagnostics;
- emit stable unsupported reasons when normalization cannot preserve behavior;
- allow shadow comparison between legacy output and new route plans.

The adapter is not a permanent rendering architecture. It is a migration tool.

## `KanvasPipelineIR` During Migration

Existing `KanvasPipelineIR` paths remain valid evidence and compatibility code.
They should not be deleted during cleanup.

For new GPU renderer work:

- adapters may translate old `KanvasPipelineIR` facts into material or route
  facts when useful;
- the new core must not require `KanvasPipelineIR` as its central input;
- future tickets must name whether they are preserving, adapting, or retiring a
  `KanvasPipelineIR` behavior.

## Migration Gates

Before any new route becomes default:

- legacy behavior must have a baseline test or evidence artifact;
- new route diagnostics must be stable;
- old and new route outputs must be compared when a CPU/GPU oracle exists;
- unsupported differences must be explicit refusals;
- PM/report output must show route counts;
- rollback to legacy route must remain possible for that slice.

## Retirement Rules

Legacy code may be removed only when:

- the replacement route is promoted;
- conformance evidence covers support and refusal cases;
- diagnostics have stable names;
- old-path usage counters show the slice no longer depends on legacy behavior;
- the removal is scoped to that slice.

No broad deletion is allowed just because the new module exists.

## Non-Goals

- Do not perform a cosmetic large-file split without behavioral evidence.
- Do not make `SkWebGpuDevice.kt` the new architecture center.
- Do not introduce a second permanent renderer path with no retirement plan.
- Do not update target documents silently inside cleanup-only tickets.
