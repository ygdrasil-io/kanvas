# Task 1 report — Preserve source spans and close unsafe-compaction regressions

## Implementation

- Renamed flattened source fields to `sourceSegmentIndexI32` and `parameterF64`; implicit fill-close edges retain seam segment `-1` and the original endpoint.
- Extended input/split edges with source segment and source parameter intervals. Split cuts derive the source parameter by interpolation, not coordinate reevaluation.
- Added `PathSourceTopologyF64`, source spans/sections/locations, exact contact-witness model, and a marked transitional legacy adapter.
- Replaced late synthetic-F64 witness compaction with conservative rejection for projected witness runs. Existing original-`PathF32` provenance retains the temporary legacy branch until the hybrid DCEL writer lands in Task 3.
- Replaced the nullable permitted-collapse path in uncanonical projection with explicit `ProjectedContourResultF32.Drop` propagation.

## Files

- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathSourceTopologyF64.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFlatteningF64.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathIntersectionsF64.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathMeasureF32.kt` (mechanical consumer rename)
- `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt`
- `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathFlatteningF64Test.kt` (mechanical consumer rename)

## TDD evidence

RED, before production edits:

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
3 tests completed, 3 failed
single source witness cannot erase either significant region: AssertionFailedError
distinct witnesses cannot consume one another: AssertionFailedError
under threshold collapse never leaks a generic Kotlin error: IllegalStateException
```

```text
rtk ./gradlew :math:geometry:jsNodeTest --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
3 tests completed, 3 failed
the first two failed membership assertions; the third failed in Kotlin Preconditions
```

Reason: the old late compaction erased significant witness-supported regions and dereferenced a permitted dropped contour.

GREEN:

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
BUILD SUCCESSFUL — 3 tests passed
```

## Verification

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --tests '*PathFlatteningF64Test*' --tests '*PathIntersectionsF64Test*' --rerun-tasks
BUILD SUCCESSFUL

rtk ./gradlew :math:geometry:jsNodeTest --rerun-tasks
BUILD SUCCESSFUL — 290 tests completed

rtk git diff --check
exit 0
```

The existing Gradle restricted-native-access, deprecation, repository-preference, and Node `DEP0169` messages remain baseline toolchain noise; no new test failures or compiler warnings were introduced.

## Self-review and concerns

Reviewed source provenance, deterministic ordering, public-only regression assertions, the shared work-budget call sites, and the absence of GM/font/codec changes. The legacy compaction is explicitly transitional and only retained for original `PathF32` provenance so existing path-operation semantics remain stable; Task 3 must remove that branch when the hybrid DCEL writer consumes the topology directly.
