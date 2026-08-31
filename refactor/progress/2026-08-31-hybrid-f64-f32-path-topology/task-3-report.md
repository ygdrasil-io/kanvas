# Task 3 — Atomic projected claims and collapsed incidences

## Status

Implemented on 2026-08-31.  The hybrid path now prepares all projected
`PointF64` coincidence proposals as immutable witness transactions before it
can publish a coincidence ID, alias group, canonical vertex, DCEL half-edge,
or output trace.  Collapsed F32 carriers remain observable through winding and
receive an explicit `PathBoundaryDisposition` after boundary selection.

## RED / public-fixture limit

The required public `n way contact is one atomic event` geometry was added as a
behavioral `PathBuilder` / `PathOpsF32` test.  It was already GREEN on the
Task-2 baseline, so it is intentionally recorded as a characterization and
not misreported as a RED.

No new valid public RED can materialize a desired strict-interior projected
claim or a threshold DROP without violating the source-provenance contract.
The allowed three collapse hypotheses were exhausted and then removed rather
than committed:

1. A sub-ULP cubic lobe was pre-flattened, so no `PathCollapsedIncidenceF64F32`
   reached the public hybrid operation.
2. Narrow public strips alone did not establish the large normalization needed
   to distinguish the intended threshold condition.
3. The strips plus a large disjoint component first reached
   `path-f32-projection-collapse`; after the early guard was temporarily
   bypassed for diagnosis, the same public geometry reached a projected
   endpoint contact with no locally proven relay.  Accepting it would violate
   the exact endpoint rule, so it is not a legal DROP fixture.

The previously documented Task-2 three-hypothesis ruling remains applicable to
strict interior projected bounds: source topology atomizes exact endpoints
before this phase, and an unmaterialized interior bound is conservatively
rejected.  No internal fixture, helper, topology call, collection assertion,
or source inspection was committed as a substitute.

The same public ceiling prevents a valid sibling-whole-collapse regression:
the only public candidates either pre-flatten before a collapsed incidence
exists or reach the independently invalid endpoint-relay condition above.
No invalid fixture was retained merely to exercise an internal disposition.

## Production invariants

- Proposals sort by exact F64 witness point, semantic source-span provenance,
  source section, and F64 interval.  A second exact witness at the same
  semantic F64 point rejects rather than becoming an ID/order tie-break.
- Each witness owns one immutable transaction.  Every pair relation is checked
  locally, then the whole source-span claim multiset is swept before deferred
  endpoints, projected coincidence IDs, aliases, half-edges, or output.
- Claim conflicts are owned by `sourceSpanIdI64`, not `inputEdgeIdI32`:
  distinct witnesses cannot overlap strict interval interiors, and every
  shared endpoint must carry the identical `PathVertexIdentityF64`.
- Claims only reuse exact source-registry section endpoints.  Their bounds,
  edge parameters, source provenance, and already-materialized identities are
  revalidated; a new interior cut cannot be synthesized from an F32 point.
  Source topology remains the phase that charges such exact cuts to
  `maxIntersections`.
- Collapsed incidences retain actual neighboring source rays rather than the
  prior synthetic `-direction/+direction` pair.  After face selection, an
  unselected incidence is ignored only at emission; a selected partial
  dependency must be a straight exact continuation or rejects.  A fully
  collapsed contour uses exact normalized double area only after explicit
  whole-contour selection proof: `<= 2^-45` is `DROP`, and a larger area is
  `REJECT` atomically.
- A no-face operation has such a proof only for its sole source contour, from
  the unary/boolean operation itself; a proven unselected sole contour stays
  `KEEP`.  Multiple collapsed contours, or a fully collapsed sibling next to
  retained F32 faces, have no selection provenance.  They `REJECT` before any
  trace is emitted rather than inferring selectedness from a missing half-edge
  or silently applying `DROP`.

## Behavioral coverage

All additions use only public path behavior:

- One n-way junction keeps every sector and leaves both inputs unchanged.
- Two separate contacts on a shared rectangle segment keep both attached
  triangles under contour-order and operand permutations, with input
  immutability checks.
- Existing public coverage remains the characterization for exact overlap
  seams/permutations, source-zero DROP, significant collapse rejection,
  canonical candidate and intersection limits, and public source witnesses.

## Budget / limits

New transaction and disposition work uses checked I64 preflights before the
new maps, lists, sorts, and scans.  The old zero-proposal local-chain and
claim passes consumed fixed debits of `2 + 1`; the transactional empty path
preserves that exact debit, so the existing public 5,316 candidate frontier
does not gain credit.  This task does not alter Task 5's global-budget work.

## Verification

Focused verification completed after the final selection-proof correction:

```text
rtk ./gradlew :math:geometry:compileKotlinJvm --rerun-tasks --console=plain
BUILD SUCCESSFUL

rtk ./gradlew :math:geometry:jvmTest \
  --tests org.graphiks.math.geometry.PathOpsHybridTopologyF32Test \
  --rerun-tasks --console=plain
20 tests completed, 0 failed

rtk ./gradlew :math:geometry:jsNodeTest \
  --tests '*PathOpsHybridTopologyF32Test*' \
  --rerun-tasks --console=plain
BUILD SUCCESSFUL
```

The required full matrix completed after the final selection-proof correction:

```text
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest \
  --rerun-tasks --console=plain
BUILD SUCCESSFUL in 26s
61 actionable tasks: 61 executed
```

The final diff/status checks are recorded immediately before the task commit.

## Files

- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64F32.kt`
- `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt`
