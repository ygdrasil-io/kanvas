# Task 2 Report: Stroke-And-Fill Path Coverage Planning

## Implementation summary

- Added `GPUStrokeAndFillPreparedPlanner` in `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/GeometryContracts.kt`.
- Added targeted stroke-and-fill prepared-route dump lines for `shapeKind == "path-stroke-and-fill"`.
- Added targeted refusal dump lines for `path-stroke-and-fill`.
- Added stroke-and-fill validation helpers and artifact-key generation local to `GeometryContracts.kt`.
- Added contract-level tests in:
  - `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/SimpleStrokePreparedRouteTest.kt`
  - `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/BasicPathFillPreparedRouteTest.kt`
- Did not modify `AnalysisContracts.kt`; the task requirements were satisfied at geometry-contract level.

## RED evidence

### Test additions

- `SimpleStrokePreparedRouteTest`: `stroke and fill emits combined coverage evidence`
- `BasicPathFillPreparedRouteTest`: `inverse fill remains visible in stroke and fill planning`

### RED command

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.SimpleStrokePreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.geometry.BasicPathFillPreparedRouteTest
```

### RED result

- Exit code: `1`
- Failure mode: compile failure
- Expected evidence:
  - `Unresolved reference 'GPUStrokeAndFillPreparedPlanner'` in `BasicPathFillPreparedRouteTest.kt`
  - `Unresolved reference 'GPUStrokeAndFillPreparedPlanner'` in `SimpleStrokePreparedRouteTest.kt`

This was the intended RED state: tests failed because the new planner did not yet exist.

## GREEN evidence

### GREEN command

```bash
rtk ./gradlew :gpu-renderer:test --tests org.graphiks.kanvas.gpu.renderer.geometry.SimpleStrokePreparedRouteTest --tests org.graphiks.kanvas.gpu.renderer.geometry.BasicPathFillPreparedRouteTest
```

### GREEN result

- Exit code: `0`
- Relevant passing tests:
  - `BasicPathFillPreparedRouteTest > inverse fill remains visible in stroke and fill planning() PASSED`
  - `SimpleStrokePreparedRouteTest > stroke and fill emits combined coverage evidence() PASSED`
- Full targeted scope passed:
  - 8 tests passed in the two requested geometry test classes

## Render command results

### Render command

```bash
for gm in linepath quadpath quadclosepath cubicpath cubicclosepath; do
  rtk ./gradlew :integration-tests:skia:generateSkiaRendersFor -Pgm.name="$gm" -Pgm.includeBlocking=true || exit 1
done
```

### Render result summary

- `linepath`: succeeded, generated `generated-renders/path/linepath.png`
  - Render log summary: `dispatch=25, refuse=12`
  - Refusal lines reported `insufficient_vertices:2`
- `quadpath`: succeeded, generated `generated-renders/path/quadpath.png`
  - Render log summary: `dispatch=73, refuse=0`
- `quadclosepath`: succeeded, generated `generated-renders/path/quadclosepath.png`
  - Render log summary: `dispatch=73, refuse=0`
- `cubicpath`: succeeded, generated `generated-renders/path/cubicpath.png`
  - Render log summary: `dispatch=73, refuse=0`
- `cubicclosepath`: succeeded, generated `generated-renders/path/cubicclosepath.png`
  - Render log summary: `dispatch=73, refuse=0`

All five requested render commands completed successfully.

## Reference protection check

### Command

```bash
rtk git diff --name-only -- integration-tests/skia/src/test/resources/reference
```

### Result

- No output
- Confirmed: no files changed under `integration-tests/skia/src/test/resources/reference/**`

## Files changed

- `gpu-renderer/src/main/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/GeometryContracts.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/SimpleStrokePreparedRouteTest.kt`
- `gpu-renderer/src/test/kotlin/org/graphiks/kanvas/gpu/renderer/geometry/BasicPathFillPreparedRouteTest.kt`
- `integration-tests/skia/src/test/resources/generated-renders/path/linepath.png`
- `integration-tests/skia/src/test/resources/generated-renders/path/quadpath.png`
- `integration-tests/skia/src/test/resources/generated-renders/path/quadclosepath.png`
- `integration-tests/skia/src/test/resources/generated-renders/path/cubicpath.png`
- `integration-tests/skia/src/test/resources/generated-renders/path/cubicclosepath.png`

## Self-review

- Kept the implementation scoped to the requested geometry contract file and the two named tests.
- Did not touch Skia reference images or similarity thresholds.
- Preserved existing path-fill and stroke planners; added a dedicated combined planner instead of refactoring unrelated routes.
- Added dedicated dump output for combined coverage evidence so existing stroke dump expectations remain unchanged.
- Reused existing local helpers and naming patterns where practical (`sanitizeForArtifactKey`, `stableLabel`, prepared-route structure).

## Concerns

- `linepath` render generation still reports `refuse=12` with `insufficient_vertices:2` diagnostics while the command itself succeeds and produces output. This appears to be residual runtime behavior in the GM content rather than a failure of this task's contract-level planner work, but it should remain visible as evidence.
