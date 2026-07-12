# Boundary fix report

Date: 2026-07-12

## Root cause

`geometry/PathTessellator.kt` returned the execution-owned
`GPUBackendTriangleData`. This made the geometry domain import `execution` and
violated the package-boundary dependency matrix.

## Change

- Added the backend-neutral `GeometryTriangleData` in `geometry`.
- `PathTessellator.stencilEdgeFan` now returns that geometry contract.
- `GPUDispatchPath` and `GPUClipCoverage` explicitly construct
  `GPUBackendTriangleData` at their Kanvas execution boundaries.
- Added a focused geometry-to-execution import guard and asserted the vertex
  and index payload observed at both Kanvas boundaries.

## TDD evidence

- RED: `GPURendererPackageBoundaryTest` reported both the existing package
  violation and the focused `Geometry must not import execution contracts`
  assertion.
- GREEN: the focused import guard passes after the change. The full boundary
  class retains only the pre-existing `commands <-> filters` package cycle;
  this task did not modify that baseline.

## Verification

- `GPURendererPackageBoundaryTest`: 10 tests; the focused guard passed. The
  sole remaining failure is the known `commands <-> filters` cycle.
- `PathTessellatorTest`: 19 tests, 0 failures, 0 errors.
- `GPUAlphaImageMaterialTest`: 12 passed.
- `GPUClipCoverageDispatchTest`: 7 passed.

The initial Kanvas run surfaced two compiler warnings from redundant test
`!!` assertions introduced by this change. They were replaced with
`requireNotNull`; the fresh focused rerun completed with the same 12 and 7
passing tests and no such warnings.
