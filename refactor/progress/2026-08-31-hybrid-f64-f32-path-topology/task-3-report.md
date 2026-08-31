# Task 3 — Atomic projected claims and collapsed incidences

## Status

Fix round 1 implemented and verified locally on 2026-09-01. The task remains
**in progress pending fresh spec and quality gates**; this report does not
claim the earlier failing reviews are closed until those gates inspect this
commit.

## Review findings checked

The two critical findings were accepted after direct code inspection:

- Strict projected bounds were lookup-only, so a locally authorized Point
  witness could not materialize an interior carrier endpoint or debit the
  combined intersection ceiling.
- A collapsed span in the middle of an otherwise selected contour could be
  ignored because no half-edge belonged to that collapsed span; adjacency also
  stopped at source-span boundaries.

The other findings were also checked rather than accepted mechanically:

- The pre-disposition alias union is necessary to construct the temporary F32
  face graph. It remains local to the arrangement and is not an emitted
  publication. Every collapsed incidence is retained as source-contour data
  through face selection; a zero-F32-dimensional carrier has no independent
  DCEL sector, so its selection is proved from face winding plus source
  winding, or it rejects conservatively.
- `operand`, contour, and segment labels now navigate provenance only. They
  no longer resolve a geometric comparator equality; exact duplicate proposals
  are indifferent and any other equal-semantic proposal group rejects.
- Exact source-area expansion now receives the shared candidate budget and
  performs checked-I64 preflights before its section scans and expansion work.

## Production invariants

- A `PointF64` projected-claim plan is built as immutable local staging from
  the semantic witness, exact source-span/section provenance, and F64 carrier
  parameter. A projected cut identity contains the witness scope and the exact
  input-edge parameter; an F32 point is only a consistency check, never
  identity authority.
- The plan fills exact endpoint identities, canonicalizes n-way endpoint
  groups, and compares `PathSourceTopologyF64.intersectionEventCountI32` plus
  newly materialized groups with `maxIntersections` before carrier insertion,
  source-span copies, projected IDs, aliases, DCEL state, or output.
- After the plan is admitted, only immutable source-span copies are subdivided.
  Claim transactions, inter-witness interval conflicts, endpoint identities,
  and deferred endpoint relays all validate before projected coincidence IDs
  and aliases are created. An exact existing identity is the sole no-op.
- Materialized claims resolve their final carriers by exact endpoint-identity
  provenance, in both traversal orientations, rather than using a stale
  pre-subdivision section index.
- Collapsed incidence adjacency walks the declared contour cyclically across
  source-span seams. A partially collapsed contour that has any selected
  carrier dependency is `REJECT`, even when the collapsed span itself has no
  selected half-edge. A contour with no selected dependency is ignored only
  at emission.
- A fully collapsed sibling is classified after face selection. A proven
  unselected sibling is kept; a proven selected sibling drops only when exact
  normalized double area is `<= 2^-45`, otherwise the whole operation rejects.
  Boundary/ambiguous face location and any missing proof reject atomically;
  no sibling is dropped from absence of a half-edge.
- All new plan, materializer, collapse-adjacency, selection, and exact-area
  paths debit the shared candidate ledger with checked I64 arithmetic before
  scans, sorting, allocation, or predicates. Task 5's independent global
  budget model is not changed.

## Public RED / fixture limit

No internal topology fixture or internal API test was added. The existing
public `PathBuilder` / `PathOpsF32` / `PathAnalysisF32` behavioral tests remain
the committed coverage for n-way contacts, disjoint contacts, overlaps,
permutations, limits, and source-zero collapse.

One bounded additional public strict-interior hypothesis was tried and removed:
a quadratic Point witness with a remote projected rail failed on JVM and JS at
the required local-anchor guard (`path-f32-projection-collapse`). It therefore
does not reach a legal interior materialization and was not retained as a
misleading RED. No valid public partial-collapse or sibling threshold fixture
was constructible without violating the same source-provenance contract; no
baseline-green test is called RED here.

## Verification

```text
rtk ./gradlew :math:geometry:compileKotlinJvm --rerun-tasks --console=plain
BUILD SUCCESSFUL

rtk ./gradlew :math:geometry:jvmTest \
  --tests 'org.graphiks.math.geometry.PathOpsHybridTopologyF32Test' \
  --rerun-tasks --console=plain
20 tests completed, 0 failed

rtk ./gradlew :math:geometry:jsNodeTest \
  --tests 'org.graphiks.math.geometry.PathOpsHybridTopologyF32Test' \
  --rerun-tasks --console=plain
BUILD SUCCESSFUL

rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest \
  --rerun-tasks --console=plain
BUILD SUCCESSFUL in 24s
61 actionable tasks: 61 executed
```

The public local candidate frontier was recalibrated by a bounded binary
search after the required new local debits: `5_772` rejects with
`path-candidate-limit`; `5_773` succeeds, in both operand orders. This is a
behavioral frontier only, not Task 5's global-cost oracle.

`git diff --check` is rerun immediately before the implementation commit.

## Files

- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathSourceTopologyF64.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64F32.kt`
- `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt`

## Remaining concerns for review

- Public construction could not expose the newly reachable strict-interior or
  partial/sibling-collapse branches without an invalid provenance fixture. The
  production guards are deliberately conservative and need review by behavior,
  not by internal-test substitution.
- A full collapsed sibling located on an F32 face boundary is intentionally
  rejected rather than guessed. This favors atomic safety over a partial
  result.
- Task 4's writer migration and Task 5's independent global budget remain out
  of scope.
