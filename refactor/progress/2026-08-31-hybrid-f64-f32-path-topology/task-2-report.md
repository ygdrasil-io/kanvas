# Task 2 — Hybrid F64/F32 projected contacts and arrangement

## Status

Completed 2026-08-31. `PathOpsF32` now routes both binary and unary operations through one
authoritative pipeline:

```text
inputEdgesF64 -> splitPathSourceTopologyF64 -> buildPathHybridTopologyF64F32
-> PathArrangementF64F32 -> writeHybridBoundaryTracesF64F32 -> PathF32
```

`PathArrangementF64` and `toPathSplitEdgesF64ForLegacyArrangement` are not reachable from that
pipeline. The only remaining occurrence of the latter in common production sources is its
temporary adapter definition in `PathSourceTopologyF64.kt`; it has no common-main caller.
The Task-2 writer serializes already selected immutable hybrid boundary traces and neither
rediscovers contacts nor rewrites runs.

## Implementation

- Replaced the pairwise overlap export with canonical exact overlap components containing all
  carrier incidences. Source topology resolves endpoint identities through its exact
  `(inputEdgeIdI32, parameterBitsI64)` index, and overlap traversal uses strict interval
  interiors only.
- Assigned semantic source-span IDs after canonical multiset grouping; geometric comparators in
  the hybrid topology and arrangement do not use operand, contour, raw source labels, or IDs to
  resolve a geometric equality.
- Added witness-scoped F32 representatives, local `PointF64` authorization, projected-pair
  classification (including adjacent/backtracking rails), canonical projected coincidences, and
  alias groups limited to authorized source spans.
- Added the single hybrid DCEL. Face embedding and area tests lift selected F32 representatives;
  outgoing rays and boundary extraction use source F64 directions. Equal unresolved rays reject.
  Winding contributions aggregate on shared F32 rails while each half-edge retains source span
  IDs and its selected boundary trace retains a `PathSourceSpanF64`.
- Added deterministic preflights before hybrid maps, lists, arrays, broad-phase pair work,
  witness/claim scans, sort calls, containment/winding work, immutable output conversion, and
  writer serialization. Sort comparators are pure on JVM and JS.

## TDD evidence

RED, before the hybrid production path:

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --tests '*PathOpsF32Test*' --rerun-tasks
78 tests completed, 4 failed
```

The failures were `tangent ovals preserve UNION at translation instead of rejecting their local
point witness`, its large-scale variant, and the corresponding two `XOR` variants. They still
reached the conservative Task-1 projection path and threw `path-f32-projection-collapse`. A
focused local-point-witness fixture also failed before the hybrid DCEL could transport its
coincidence.

```text
rtk ./gradlew :math:geometry:jsNodeTest --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks
78 tests completed, 4 failed
```

The same tangent cases failed on JS. The initial literal tangent probes were then corrected to
hand-checked points away from curve boundaries; they no longer derive an expectation from the
same flattened tessellation used by the operation.

GREEN:

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --tests '*PathOpsF32Test*' --rerun-tasks
BUILD SUCCESSFUL — 85 tests passed

rtk ./gradlew :math:geometry:jsNodeTest --rerun-tasks
BUILD SUCCESSFUL — 299 tests passed

rtk git diff --check
exit 0
```

## Behavioral coverage

- The five operations over all three tangent transforms use literal, boundary-distant expected
  memberships for tangent ovals.
- Exact local point witness succeeds through the full hybrid pipeline; identical projected rails
  without that witness reject; a witness elsewhere on the same spans does not authorize them.
- Adjacent/backtracking projected overlap rejects absent exact overlap proof.
- Exact n-way overlap remains stable under contour relabeling/permutation; signed-zero originals
  survive the public `PathF32` result.
- Tests find the first successful candidate budget, assert one-below rejection and at-boundary
  success, and assert the same boundary/result for forward/reverse canonical inputs on JVM and
  JS. The precise F64 fixture helper builds only numerical source input and calls the single
  production hybrid pipeline; its assertions are public membership, error, and `PathF32` bits.

## Budget audit

The new `PathHybridTopologyF64F32.kt`, `PathArrangementF64F32.kt`, and
`writeHybridBoundaryTracesF64F32` contain no dynamic `candidateWorkBudget.consume()` calls.
Their work is charged by `consumePreflightI64` from canonical sizes before allocation/work; sort
charges use a deterministic `n * ceil(log2(n))` envelope rather than comparator callbacks.

The Task-2 source-topology additions use pure `*WithoutBudgetF64` comparators through
`sortedSourceTopologyF64`, whose preflight occurs before each sort. Exact overlap endpoints now
reach source topology only through the direct registry key `(inputEdgeIdI32, parameterBitsI64)`;
there is no source-topology coordinate or ±16-ULP endpoint recovery. The canonical registry uses
the corresponding `sortedRegistryF64` preflight.

Historical source-topology dynamic debits remain at lines 180, 184, 253, 255, 260, 263, 268,
289, 291, 293, 295, 297, 323, 409, 428, 447, 453, and 470 for source merge/index/contact
construction, which the hybrid path necessarily calls. The legacy-adapter-only debits at 508,
512, 515, 518, 534, 537, 562, 568, 575, and 579 are not reachable from the hybrid path.
The broader end-to-end conversion of these Task-1 historical debits to a globally tight frontier
is explicitly carried to Task 5 by breaker ruling 9; this task verifies a deterministic hybrid
frontier under operand permutation on both backends.

## Files

- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathIntersectionsF64.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathSourceTopologyF64.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64F32.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt`
- `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathBehaviorTestSupportF32.kt`
- `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathIntersectionsF64Test.kt`
- `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsF32Test.kt`
- `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt`

## Self-review and concerns

Reviewed the six Task-2 prerequisites, all new comparator/tie paths, alias scope, source-F64
ray ordering, writer authority, allocations/preflights, and JVM/JS behavior. No font, codec, or
GM files changed. The residual source-topology debit audit above is intentionally not represented
as a Task-2 success claim; it is the already assigned global Task-5 concern. No Task-2 blocker
remains.

## Fix round 1 — preserve hybrid span geometry

### Findings closed

- Every flattened F64 section remains a carrier through the projected broad phase, DCEL, local
  direction/ray ordering, face area and boundary trace. The writer emits the ordered carrier run
  rather than a source-span endpoint chord; the public quadratic bulge probe stays inside while
  the forbidden chord-only probe stays outside.
- Raw overlap pair evidence is swept into atomic exact intervals with a unique active incidence
  multiset. Endpoint identities are transported by direct registry tickets keyed by edge ID and
  raw parameter bits; neither source topology nor the hybrid alias code re-identifies an endpoint
  from coordinates or an ULP window. Staggered three-way overlaps cover both operands and
  relabel/permutation variants.
- Projected points/rails require exact local coverage. A Point witness is accepted only for the
  two sections whose exact endpoint identity is that witness, while a remote witness on the same
  source span and unsupported endpoint/backtracking relations reject. Claims carry exact partial
  parameter bounds plus endpoint IDs and are validated as one transaction before coincidence IDs
  or aliases are published. Collapsed carriers are explicitly consumed only for the documented
  intrinsic adjacent continuation; all other partial collapses reject.
- Representative selection is per incidence; original F32 bits have priority, including signed
  zero. The strict parameter predicate accepts 15 ULP and rejects 16 ULP on JVM and JS.
- Final canonical vertex and half-edge counts use checked I64 arithmetic before allocation.
  Carrier groups retain every contribution, reject incompatible equal rays, and boundary cycles
  use full-sequence Booth canonicalization. `candidateIndex` was renamed to `candidateIndexI32`.
- The flattening tolerance now derives only from the F32 lattice observable after denormalization:
  `clamp(2 * ulpF32(maxWorldMagnitude) * scale, 2^-23, 2^-12)`. It prevents F64-only micro
  carriers from inventing an unsupported F32 contact after a large translation without reducing
  identity/small-scale precision. The exact witness guard was not relaxed.

### RED/GREEN and verification

RED was reproduced independently on JVM and JS with:

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsF32Test.metamorphic tangent ovals preserve DIFFERENCE at translation*' --rerun-tasks
rtk ./gradlew :math:geometry:jsNodeTest --tests '*PathOpsF32Test.metamorphic tangent ovals preserve DIFFERENCE at translation*' --rerun-tasks
```

Both failed with `path-f32-projection-collapse`. The same projected pair had a common rounded
F32 endpoint near `(3010, 3004.9677734375)`, but distinct F64 endpoints at `x = ±5.118465e-6`
and no local witness. The exact tangency witness was preserved separately at `(3010, 3005)`;
therefore propagating its authority would have violated the local-witness rule. The failure was
caused by `2^-23` normalized flattening being finer than the translated F32 lattice, not by a
JVM/JS rounding divergence.

GREEN:

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsF32Test.*tangent ovals*' --rerun-tasks
15 tests completed, 0 failed

rtk ./gradlew :math:geometry:jsNodeTest --tests '*PathOpsF32Test.*tangent ovals*' --rerun-tasks
BUILD SUCCESSFUL

rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --tests '*PathOpsF32Test*' --rerun-tasks
89 tests completed, 0 failed

rtk ./gradlew :math:geometry:jsNodeTest --rerun-tasks
BUILD SUCCESSFUL
```

The fixed public budget frontier remains `4_329`: for the two overlapping four-edge rectangles,
the independently audited deterministic phases are source registry/index `1_360`, hybrid
projection/claims `1_341`, DCEL `1_186`, and extraction/Booth/writer `442`. Their checked sum is
`1_360 + 1_341 + 1_186 + 442 = 4_329`; `4_328` returns exactly
`path-candidate-limit`, `4_329` succeeds, and forward/reverse canonical inputs produce identical
`PathF32` on both JVM and JS.

### Claim-fixture limit and residual audit

The public precise-F64 fixture reaches the production registry, which atomizes every connected
exact collinear overlap before the hybrid claim validator. Consequently it cannot expose two
different witnesses with truly overlapping interiors without bypassing that registry (forbidden
for these black-box tests). Coverage therefore proves adjacent atomic intervals succeed and
distinct disjoint witnesses cannot consume one another; the transactional strict-interior guard
is exercised by the production implementation and rejects such a state if one is ever proposed.

No Task-2 residual correctness concern is known. The only deferred audit remains the historical
`PathSourceTopologyF64` dynamic `consume()` sites listed above: the hybrid pipeline does not call
the legacy adapter, and Task 5 owns the global conversion of those historical debits.
