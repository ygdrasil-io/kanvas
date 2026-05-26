# ADR 0005: Start WebGPU AA Edge Budget At 256

Status: Proposed
Date: 2026-05-26

## Context

The current WebGPU path uses fixed edge arrays for AA polygon and
stencil-cover shaders. `SkWebGpuDevice.MAX_AA_EDGES` is 256. The spec needs a
reviewable budget instead of local policy hidden in `SkWebGpuDevice`.

## Decision

Use 256 as the first contractual WebGPU AA edge budget.

When a path exceeds the budget and no mask/atlas strategy is enabled, WebGPU
must emit `coverage.edge-count-exceeded` with `backend=GPU`. It must not drop
AA, silently scissor, or switch to a CPU readback path without an explicit
fallback action.

## Consequences

Positive:

- The first implementation matches the existing shader layout.
- Overflow behavior is testable and PM-visible.
- Future budget changes require a focused benchmark/ADR instead of hidden
  shader edits.

Negative:

- Some path-heavy AA scenes remain unsupported until mask/atlas or another
  strategy lands.
- A larger fixed budget may have uniform/storage costs and must be justified
  separately.

## Revisit Trigger

Revisit when benchmark evidence shows the 256-edge budget is the main blocker
for accepted path-heavy scenes.
