# W4e Task 2 report

Base reviewed: `eaf479d73`.

## Implementation

- Added F64 `RectF64`/`RRectF64`, immutable input snapshots, typed F32 clip snapshots, and ordered `Intersect`/`Difference` metadata in `:math:geometry`.
- Added `ClipPreparationLimitsI32`, `ClipPreparationLimitsI64`, and private per-module transactional ledgers. Each debit validates entry, stack, and frame totals before mutating or publishing a result.
- Added geometry stack preparation with conservative target-domain scissors, independent AA/inverse-fill facts, typed Rect/RRect preservation, and PathFill-based path snapshots.
- Added matrix-only transform orchestration. Identity/axis-aligned Rect/RRect remain typed; general affine and perspective become device Path inputs. Source `PathF32` perspective preparation delegates to W4d projective preparation. Projection costs are carried as per-entry snapshots and as stack/frame before-values into geometry.
- Added public behavior tests for ordered metadata, inverse fill, AA, conservative scissor, affine/perspective shape typing, empty operations, defensive snapshots, entry/frame attempted-edge limits, vertex/index/snapshot limits, and a projection-plus-geometry entry budget refusal.

## TDD evidence

1. Wrote `ClipStackPreparationF64Test` and `ClipTransformsF64Test` before production sources.
2. Ran the requested RED command. It failed at compile time only because every requested new public type/function was unresolved; no production implementation existed.
3. Added the production implementation and re-ran the focused command successfully.

## Commands and results

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*ClipStackPreparationF64Test' :math:matrix:jvmTest --tests '*ClipTransformsF64Test'
RED: BUILD FAILED; unresolved references for the not-yet-created clip API.

rtk ./gradlew :math:geometry:jvmTest --tests '*ClipStackPreparationF64Test' :math:matrix:jvmTest --tests '*ClipTransformsF64Test'
GREEN: BUILD SUCCESSFUL; 6 geometry tests and 2 matrix tests passed.

rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest
BUILD SUCCESSFUL in 29s.

rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest
Final fresh gate: BUILD SUCCESSFUL in 3s.

rtk git diff --check
No output; success.

rtk git add math/geometry/src math/matrix/src && rtk git diff --cached --check
Clean; staged 10 requested math source/test files.

rtk git commit -m "feat(math): prepare transformed clip stacks"
Committed as `9ed90e1`.
```

## Self-review

- Confirmed `:math:geometry` has no `Matrix3x3F64` import and no renderer dependency.
- Confirmed matrix orchestration is confined to `ClipTransformsF64.kt`; no renderer classes were added to math.
- Confirmed all F32/F64/I32/I64 public quantities use the requested suffix nomenclature.
- Confirmed public tests assert observable outputs and resource refusals; they do not inspect source shape, private state, reflection, call count, or test infrastructure.
- Confirmed axis-aligned RRect transforms preserve typed geometry and remap corner ownership on axis reflection.
- `rtk git diff --check` is clean after staging the requested math sources.

## Concerns

None known.

## Fix round 1 — review findings 1–8

- Replaced non-axis RRect conversion by `PathBuilder.addRRect`, retained `ArcTo` through the authoritative affine path mapper, and routed perspective Rect/RRect/Path through W4d projective preparation.
- Added preflight validation of the maximum final path payload before `PathFillGeometryF32` can allocate final arrays. Projection uses the received entry/frame snapshots and a policy translated from clip limits; actual W4d after-usage is debited into the matrix entry/stack/frame ledger and then passed to geometry.
- Inverse paths now conservatively cover the entire target domain even when empty. Empty paths debit their observed attempted-edge count.
- Replaced the mutable runtime list exposure with an array-backed read-only `AbstractList` snapshot.
- Added portable checked F64→F32 range validation using `Float.MAX_VALUE`, including Kotlin/JS.
- Added public behavior/mutation tests for both inverse fill rules, empty attempted work across stacks, read-only list behavior, F64 overflow, RRect/path snapshots, exact reflected RRect radii, affine RRect arc bounds, and perspective horizon refusals for a curve and RRect.

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*ClipStackPreparationF64Test' :math:matrix:jvmTest --tests '*ClipTransformsF64Test'
RED: the new affine RRect bounds regression initially failed (the prior conversion dropped rounded-corner geometry).

rtk ./gradlew :math:geometry:jvmTest --tests '*ClipStackPreparationF64Test' :math:matrix:jvmTest --tests '*ClipTransformsF64Test'
BUILD SUCCESSFUL in 2s after the fixes; 11 geometry tests and 5 matrix tests passed.

rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest
First run: JS caught F64→F32 overflow validation differing from JVM.

rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest
Final run: BUILD SUCCESSFUL in 26s.

rtk git add math/geometry/src math/matrix/src && rtk git diff --cached --check
Clean; staged five corrected math source/test files.

rtk git commit -m "fix(math): harden transformed clip preparation"
Committed as `b120a73`.
```

Round-1 self-review: confirmed geometry remains free of `Matrix3x3F64`/renderer imports; transform orchestration remains in `:math:matrix`; tests exercise public outputs and mutations only. The deferred diagnostic-minor finding was intentionally left unchanged.

## Fix round 2 — review findings 1–5

- Preserved non-axis `RectF64` and `RRectF64` input until transform completion.  Canonical F64 Rect/RRect path construction now lives in `:math:geometry`, including normalized F64 rounded-rectangle arc radii; matrix only orchestrates the transform.
- Added the F64-input projective preparation path and relayed each actual projective debit directly into the matrix entry/stack/frame ledger.  This removes max-entry reservation and admits work from the real incremental projection cost.
- Replaced path maximum-payload preflight and estimate/delta bookkeeping with a transactional PathFill debit hook: each observed flattened attempt is charged before retention, and the exact final vertex/index/byte cost is charged immediately before final arrays are created.  Empty paths and repeated `Close` no longer create a negative delta.
- Delayed Rect/RRect F32 object/radii construction until after the exact non-empty clip debit.
- Added public output-only regression tests for F64 precision collapse, F64-outside-F32 scaled into device range, projective frame-before admission, empty real-work admission, and repeated `Close` accounting.

```text
rtk ./gradlew :math:geometry:compileKotlinJvm
BUILD SUCCESSFUL in 4s.

rtk ./gradlew :math:matrix:compileKotlinJvm
BUILD SUCCESSFUL in 4s.

rtk ./gradlew :math:geometry:jvmTest --tests '*ClipStackPreparationF64Test' :math:matrix:jvmTest --tests '*ClipTransformsF64Test'
RED: BUILD FAILED; the new precision/range tests exposed their initial overly-tight scissor expectations (production behavior was otherwise ready).

rtk ./gradlew :math:geometry:jvmTest --tests '*ClipStackPreparationF64Test' :math:matrix:jvmTest --tests '*ClipTransformsF64Test'
BUILD SUCCESSFUL in 1s; 13 geometry tests and 8 matrix tests passed.

rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest :math:matrix:jvmTest :math:matrix:jsNodeTest
BUILD SUCCESSFUL in 26s.

rtk ./gradlew :math:geometry:jvmTest --tests '*ClipStackPreparationF64Test' :math:matrix:jvmTest --tests '*ClipTransformsF64Test'
BUILD SUCCESSFUL in 2s after final visibility cleanup; focused tests pass.

rtk git diff --check
No output; success.
```

Round-2 self-review: no matrix import was added to geometry; the new geometry helpers only construct F64 geometry.  Matrix retains transform orchestration only, and no renderer dependency or forbidden test technique was introduced.  The final projective and path-fill callbacks debit before the work/allocation they authorize.  No known concern.
