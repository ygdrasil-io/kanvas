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

## Fix round 1

The production arrangement now calls `splitPathSourceTopologyF64` and reaches the legacy arrangement only through its transitional adapter. Input-edge construction retains distinct coincident source locations, including the `t=0.0` segment location and the implicit seam. Exact-cut flags prevent span merging through an exact event while allowing contiguous flattening subdivisions to share a span. `PathInputEdgeF64` now uses the required F32/F64/I32 property names with mandatory provenance fields; all callers were adapted.

The first focused run after routing the production flow through topology was RED: `PathOpsF32Test` reported `Key 0 is missing in the map.` from `inputEdgesF64`, proving that discarded coincident locations left a detached vertex. The corrected construction initializes the vertex incidence map while retaining both locations.

GREEN evidence:

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsF32Test*' --rerun-tasks
BUILD SUCCESSFUL — all 73 PathOpsF32Test tests passed

rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --tests '*PathFlatteningF64Test*' --tests '*PathIntersectionsF64Test*' --rerun-tasks
BUILD SUCCESSFUL
```

The historical JS RED artifact was not retained verbatim. The JVM result above preserves the exact observable message for the round-1 correction; the earlier report's JS wording is therefore only a summary, not a quoted log.

## Fix round 2

The temporary arrangement adapter now materializes every legacy split edge solely from the ordered source spans and their flattened sections. It derives only internal-section identities from the canonical span/section order; source endpoint identities remain attached to the span locations. The parallel raw-split side channel was removed. Input closing edges now select the destination segment as authority and force a seam or segment transition to `[0.0, 1.0]`, retaining both coincident locations.

RED during this round: before initializing unreferenced coincident locations, the production-routing mutation failed `PathOpsF32Test` with the exact JVM error `java.util.NoSuchElementException: Key 0 is missing in the map.` from `inputEdgesF64`. GREEN followed after preserving the locations while initializing their incidence map.

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --tests '*PathFlatteningF64Test*' --tests '*PathIntersectionsF64Test*' --rerun-tasks
BUILD SUCCESSFUL
```

Self-review: the adapter no longer transports raw split edges. Remaining work is limited to the follow-on hybrid DCEL: the existing legacy projection compactor is still isolated behind its prior compatibility branch and exact overlap witness materialization requires the registry export scheduled for the next topology step.
