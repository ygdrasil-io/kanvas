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

## Fix round 2 — authoritative source sweep and projected carriers

### Review verification and RED/GREEN

The reviewer-local projected-coincidence reproduction was first verified rather than accepted
blindly.  Its original `y = 1 +/- 2^-25` literals rounded to the same input `Float` value before
`PathOpsF32` ran; the resulting JS-only flattened-chain rejection was therefore not a valid
public geometry failure.  The replacement puts the offset at zero, where `+/-2^-25f` are nonzero
and have distinct raw bits.  It is a `PathBuilder`/`PathOpsF32` test: `maxIntersections = 8`
returns `path-intersection-limit`, while `9` succeeds with the two literal membership probes.
Both JVM and JS pass this centered public boundary.

The final fresh verification was:

```text
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest --rerun-tasks
BUILD SUCCESSFUL

rtk git diff --check
exit 0
```

### Production changes closed

- The source registry now consumes raw pair evidence through an exact sorted endpoint event sweep
  and active incidence set.  It emits atomic overlap intervals with their full active incidence
  multiset and direct `(inputEdgeIdI32, parameterBitsI64)` tickets before identities or split
  outputs are allocated.  There is no interval-times-all-edge regrouping or coordinate/ULP
  endpoint recovery.  The public 12-rectangle staggered fixture verifies independent bounds and
  exact `PathF32` equality under operand and contour permutations on both backends.
- Every flattened section stays a hybrid carrier.  The source topology keeps canonical F64
  geometry for source splitting while separately retaining each incidence's evaluated F64 point.
  The hybrid seed names these paths explicitly as `canonicalPointF64` and `incidencePointF64`:
  canonical geometry preserves robust source topology, and representative candidates are
  evaluated at the incidence parameter.  Original representable F32 bits, including signed zero,
  win; any alternative remains confined to the same exact witness.
- Exact source atomization occurs before hybrid aliases.  Therefore an active overlap endpoint is
  already a counted source cut, and the hybrid path only validates/looks up transactional claims;
  it does not rematerialize interior cuts after IDs or aliases have been published.  A true
  conflicting interior-witness case is not reachable through the public registry because it is
  atomized first; the defensive hybrid validator remains conservative.
- Collapsed carriers are consumed explicitly.  Only intrinsic adjacent continuation on one source
  span is admitted; otherwise whole-contour source double area decides `Drop` at `<= 2^-45` or
  atomic `Reject`.  No selected trace is silently omitted.  Existing public whole-contour drop,
  significant-collapse rejection, local/remote witness, backtracking, 15/16-ULP, curve-vs-chord,
  and all tangent-transform tests remain green.
- `maxVertices` and `maxHalfEdges` use checked final canonical DCEL counts before allocation.
  The public identical-rectangle boundary rejects `7` and accepts final count `8`; obsolete
  assertions that applied that public limit to transient source splitting were removed.  The
  source sweep's staggered fixture derives `6 + 5 = 11` exact event groups and verifies `10`
  rejects before hybrid allocation while `11` succeeds.
- Normalization now rejects finite F64 values that would convert above `Float.MAX_VALUE` and uses
  a bounded observable-F32 lattice spacing.  Public extreme finite and subnormal translation
  tests are deterministic on JVM and JS.

### Ledger and test-scope audit

All new sweep, carrier, alias, arrangement, and writer traversals preflight deterministic checked
work before allocation or traversal; sort charges are envelopes based on canonical sizes, never
comparator invocation counts.  Two exact per-incidence F64 evaluations per retained source split
are now charged before those output objects are built.

The current `4_679` reject / `4_680` success rectangle budget is retained only as a public,
backend- and permutation-deterministic regression boundary.  It is **not** an independent global
cost oracle: the earlier round-1 claim that `4_329` was an independently derived total is
superseded and withdrawn.  A complete algebraic accounting of historical source-topology dynamic
debits remains a Task-5 concern; no new comparator- or visit-order-dependent debit was added here.

Task-2 topology tests no longer construct `PathVertexIdentityF64`/`PathInputEdgeF64` or invoke
the former hybrid projection helper.  They build public paths and assert only result membership,
bounds, bits, errors, limits, and immutability; the direct F64 ULP predicate test is retained as
a numeric predicate test.

### Residual risk carried forward

No known Task-2 correctness failure remains after the fresh JVM/JS run.  The residual audit is
deliberately narrow: Task 5 must replace the inherited global source-topology dynamic ledger with
an independently derived end-to-end budget model.  The source registry atomization invariant also
makes a separately public, conflicting strict-interior hybrid-cut fixture unreachable without
bypassing production; its conservative validation is retained rather than asserted through an
internal graph fixture.

## Fix round 3 — source-F64 bundle sweep and overlap authority index

### Scope correction and ruling

This round verified the three public projected-interior-cut hypotheses documented in
[`task-2-round-3-ruling.md`](task-2-round-3-ruling.md).  They establish that source atomization
already makes every reachable authoritative rail endpoint a direct `0/1` section endpoint; no
valid public fixture reaches a unique, local, strict-interior projected claim without bypassing
the production registry.  Accordingly, the earlier round-1/round-2 wording that implied Task 2
closed projected cut materialization or the complete `KEEP`/`DROP`/`REJECT` boundary disposition
is superseded.  The validated plan assigns proposal/commit of those claims, their
`maxIntersections` accounting, and full collapsed disposition to Task 3 steps 3--4.  Task 2 keeps
its conservative rejection path and does not silently omit a selected trace.

### Production changes

- `PathArrangementF64F32` now derives an outgoing source ray from every carrier section's
  `startIncidencePointF64`/`endIncidencePointF64`, not from its canonical topology endpoints.
  It flattens all rays at a vertex into tagged angular events, performs one exact F64 angular
  sort, and runs a cyclic validation: each F32 embedding bundle occupies one run and the sequence
  of runs equals the F32 embedding sequence up to rotation, never reversal.  Equal exact rays use
  only the established F32 embedding position, never raw source labels or half-edge IDs.
- The source sweep is `O(M log M + M)`: `2B` units are charged before the bundle/event count,
  `3M + 2B` before event storage and construction, the deterministic sort envelope before its
  comparator, and `2M + 3B` before run storage, scan, and membership bitmap.  The sweep stores
  only O(M+B) local tags/positions; it no longer allocates per-vertex arrays sized by every DCEL
  half-edge.
- `PathHybridTopologyF64F32` materializes its overlap authority index once per input edge, with
  lists ordered by exact registry `witnessIdI64`.  IDs are lookup keys for source-canonical atomic
  intervals, not geometry tie-breakers.  A projected authority query performs a two-pointer join
  of those two ordered lists and checks direct interval coverage.  It no longer forms a Cartesian
  incidence product; a non-covering equal witness advances both cursors so a later atomic witness
  remains reachable.
- The overlap index charges each phase before it works: witness count before counting references,
  checked `2R + W` reference-count build, checked `2E + R` exact-capacity edge lists and entries,
  deterministic per-list sort envelopes, then a checked duplicate scan.  A query charges its two
  registry lookups before reading them and `R1 + R2 + 4` before its bounded two-pointer join.
  All totals use checked I64 arithmetic.  No new `map`, `filter`, `sumOf`, comparator callback,
  or allocation is hidden ahead of these local preflights.

### Public evidence

- Added a six-sector high-valence `PathBuilder`/`PathOpsF32` union fixture.  Its literal
  boundary-distant probes and exact `PathF32` equality cover contour and operand permutations;
  the focused tangent suite remains green.  It is a normal public stress of the cyclic sweep, not
  a fabricated internal inversion fixture: public F32 input cannot directly inject inconsistent
  hidden per-incidence F64 directions.  The production sweep rejects such an inversion before
  DCEL construction.
- The public staggered n-way and long staggered overlap fixtures remain green under relabeling
  and operand permutation after the two-pointer change.  The initial two-pointer implementation
  REDded all three by returning false at the first same-witness non-covering interval; advancing
  both sorted cursors is the minimal exact atomic-interval correction.
- Fresh focused verification:

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --tests '*PathOpsF32Test*' --rerun-tasks
90 tests completed, 0 failed

rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest --rerun-tasks
BUILD SUCCESSFUL
```

The captured global rectangle budget regression is now `4_986` reject / `4_987` success, with
identical forward/reverse result.  This recalibration includes the explicitly charged exact index
lookups and is intentionally **not** an independent global budget oracle; Task 5 still owns that
end-to-end ledger model.

### Remaining assigned work

No independently reproducible Task-2 arrangement blocker remains.  Task 3 owns the deferred
projected interior proposal/commit and full collapsed disposition described above; Task 5 owns
the inherited global source-topology ledger conversion.  No font, codec, or GM scope changed.

## Fix round 4 — reject unresolved equal source rays and debit local work

### Exact angular authority

The source angular sweep now runs before F32 outgoing-order sorting or face construction.  Its
comparator orders only exact F64 ray direction; it no longer uses an F32 embedding position to
break a geometric tie.  A contiguous equal-ray run is scanned immediately after that exact sort:
repeated rays within one already aggregated bundle remain valid, while the same exact ray in two
distinct bundles rejects with `path-f32-projection-collapse`.  Only after this guard succeeds may
the code check the source/F32-direction dot product and map the source runs onto the established
F32 cyclic order.  Thus neither F32, a label, an ID, nor face traversal can resolve an unresolved
geometric equality.

The public-input search was deliberately capped at three hypotheses.  The existing public
six-sector high-valence paths, the centered `+/-2^-25f` quadratic point-witness paths, and the
translated tangent ovals all exercise normal hybrid paths but cannot inject two distinct F32
carrier bundles with one inconsistent hidden F64 source ray.  No exact public fixture is therefore
constructible through `PathBuilder`/`PathOpsF32` after those three attempts, and no internal-shape
test was added.  The reviewer reproduction remains the design diagnostic: F32 embedding rays
`(1,0)`, `(1,0.5)`, `(1,1)` paired with source rays `(1,0.2)`, `(1,0.2)`, `(1,1)` previously
accepted; the negative `(1,0.4)`, `(1,0.2)`, `(1,1)` rejected.  The new source-only guard rejects
the unresolved equal case at the required earlier point.

### Local checked debits

- `buildOverlapWitnessIndexF64F32` now reserves all three `witnessesF64` visits.  In particular,
  the preflight before the final capacity/list materialization pass includes `W`, so a lone
  `PointF64` with `E = R = 0` cannot execute an uncharged third visit.
- The angular path has checked-I64 preflights before its bundle/event counts, event allocation and
  construction, exact sort, equal-ray scan, dot validation, F32 run mapping, run storage,
  seen/order checks, and both post-validation adjacent-ray predicates.  The former
  `zipWithNext()` allocations were replaced by explicit, preflighted adjacent-pair loops.
- The overlap authority lookup preflights its two registry reads and a checked linear
  `8 * (R1 + R2)` envelope before the two-pointer join.  That envelope covers every potential
  pair's reference reads, witness/edge comparisons, and both interval-coverage chains, including
  arbitrarily many common non-covering witnesses before a covering one.

This is local accounting only.  It deliberately does not claim or introduce the independent
end-to-end budget model assigned to Task 5.

### TDD and verification evidence

The new public `PathBuilder`/`PathOpsF32` rectangle test was written first.  Before the production
debits, `maxCandidateProbes = 4_988` unexpectedly succeeded, so its expected
`path-candidate-limit` assertion REDded.  With the local debits in place it is GREEN on both
backends.  The paired public boundary is now `5_315` reject / `5_316` success; it is a
deterministic local non-regression boundary, not an independent global oracle.

Focused checks were run before the full matrix:

```text
rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks --console=plain
18 tests completed, 0 failed

rtk ./gradlew :math:geometry:jsNodeTest --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks --console=plain
18 tests completed, 0 failed
```

The required complete matrix also passed:

```text
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest --rerun-tasks --console=plain
BUILD SUCCESSFUL
61 actionable tasks: 61 executed
```

`git diff --check` is recorded with the committing verification for this round.

### Scope retained

No projected interior-cut materialization or collapsed-disposition change was made: both remain
Task 3 work.  No Task-5 global-budget model, font, codec, GM, rendering, score, or exclusion file
changed.
