# Task 3 — Atomic projected claims and collapsed incidences

## Status

Fix round 2 is implemented and locally verified on 2026-09-01.  The task is
still **in progress pending fresh independent spec and quality gates**.  This
report records local evidence only; it does not reclassify either earlier
review as closed.

## Public RED and retained coverage

The round-2 RED is public and behavioral only.  With
`u = Float.fromBits(1f.toRawBits() + 2)`, a one-segment self-closed cubic
combined with the same cubic plus a remote rectangle made
`PathOpsF32.op(..., INTERSECT)` return an empty `PathF32` before the fix.  The
retained test now requires `path-f32-projection-collapse`, as does the stable
no-face XOR variant, on JVM and JS.  It constructs paths exclusively through
`PathBuilder` and observes only `PathOpsF32` results/errors.

`PathOpsF32.simplify` already threw that error before this round.  It remains
in the public fixture as a characterization of the same input, but is not
claimed as a new RED.  No internal topology helper, source assertion, node,
ID, package/import assertion, or test-only production switch was added.

The strengthened public strict-interior limit fixture has the exact boundary
`9` reject / `10` succeed.  Its bounded diagnostic has 618 proposal
occurrences and 1,237 raw endpoint-relation nodes: 1,235 are degree-two,
single-rail-pair flattening continuations, and two are terminals.  They reduce
to one projected structural event key, so the added unit is one distinct
external canonical projected bound rather than a flattened-section, a pairwise
relation, or an arbitrary degree filter.  A degree-two relation joining more
than one rail pair remains an event; propagated/n-way occurrences with the
same event key do not debit again.  Existing public n-way,
staggered-overlap, disjoint-contact, operand-order, and immutability behavior
tests remain the coverage for their respective paths.

## Projected-claim transaction invariants

- Proposals are staged and ordered by semantic Point witness, semantic source
  span, and F64 parameter interval.  Every relation for one witness is
  validated as a transaction, and all inter-transaction interval conflicts are
  resolved before projected IDs, aliases, carrier copies, DCEL state, or output
  are published.
- Strict interior endpoints receive deterministic projected identities from the
  witness and exact source incidence/parameter provenance.  F32 is an
  embedding/validation value only; it is never identity, grouping, or ordering
  authority.  Exact existing identity is the only no-op.
- The immutable cut plan counts the exact source event total from
  `PathSourceTopologyF64` plus canonical projected event groups before final
  carrier allocation.  An endpoint relation with distinct identities is an
  event even without a physical cut; exact common identities do not add one.
  Checked-I64 preflight happens before the bounded staging work and the public
  `maxIntersections` gate.
- A projected event is propagated over its validated n-way coincidence
  component before subdivision.  Final `PathProjectedCoincidence` provenance
  references lists of final sections, not stale section indexes.  The remap
  validates contiguous, exact parameter coverage without a gap or overlap.
- Transaction comparison is strictly lexicographic and total.  Operand,
  contour, and segment labels navigate provenance but cannot settle a
  geometric equality: complete equivalence is validated as a group and every
  other unresolved equality rejects.
- `maxVertices` is applied to canonical vertices after exact alias groups,
  rather than to raw pre-alias projected-cut counts.

## Critical review closures

- **Partial collapse:** literal incidences retain their two actual cyclic
  source-neighbour rays, including span boundaries and seams.  The
  post-selection check rejects only when either local neighbour is selected;
  it does not scan a distant selected edge of the contour.
- **Local cost shape:** each added map, sort, adjacency index, group walk,
  cut de-duplication, carrier scan, and exact-area expansion is preflighted
  with checked I64 work.  There is no retained quadratic source of work except
  the bounded exact-area expansion after collapse provenance makes it needed.
- **Canonical vertex limit:** the materializer uses a transient
  `path-candidate-limit` capacity only; `maxVertices` is evaluated after exact
  aliases are canonicalized in the arrangement.
- **Comparator:** projected transaction ordering is lexicographic over witness,
  semantic spans, and F64 interval.  A zero comparison is subsequently
  validated as an exact duplicate; non-identical semantic ties reject rather
  than being resolved by operand/contour/segment labels.
- **No-half-edge siblings:** grouped signed winding and exact interaction proof
  classify the public collapsed sibling for both `INTERSECT` and no-face
  `XOR`; both reject atomically instead of returning a partial or empty path.

## Collapsed-boundary invariants

- `PathBoundaryDisposition { KEEP, DROP, REJECT }` is explicit.  The
  collapsed-sector ledger is captured before provisional aliases and retained
  until after face selection, so a missing half-edge never means absent winding.
- Literal collapsed incidences carry actual cyclic incoming/outgoing source
  rays across source-span seams.  A partial incidence rejects only when one of
  those local neighbouring carriers is selected; a distant selected carrier of
  the same contour does not cause rejection.  Unresolved adjacency rejects.
- A fully collapsed sector is selected as a group.  The group composes signed
  exact contour orientation deltas from both operands and requires an exact
  incidence-connectivity proof; matching F32 representative coordinates alone
  are ambiguous and reject.  This covers the multi-contour no-half-edge XOR and
  INTERSECT cases without silently KEEPing or dropping a sibling.
- A selected group drops only if every required contour is a whole literal
  carrier collapse and its exact normalized double area is `<= 2^-45`.
  A larger/partial/ambiguous group rejects atomically.  Unselected candidates
  are retained as data and ignored only at emission.
- The new non-literal provenance is structural, not a small-bounds heuristic:
  `SelfClosedSourceSegmentF64F32` requires one original source segment, an
  exact contiguous partition of `[0, 1]`, uniform forward or reverse
  orientation, and identical authoritative F64 endpoint identity at both ends.
  The original F32 endpoint is retained solely to locate the post-selection
  sector.  It is considered only when no literal partial collapsed incidence
  exists; such an incidence instead reaches the local-ray continuation check.
  A structural candidate is deliberately non-droppable; if selected it rejects.
  Ordinary zero-area traces made from noncollapsed carriers do not enter this
  ledger.

## Local budget accounting

All new maps, sorts, cut de-duplication, carrier/section scans, adjacency
indexes, sector-group selection, and exact-area predicates debit checked-I64
candidate work before scanning, allocation, or predicates.  Exact area is
quadratic only after literal or structural collapse provenance makes a contour
a candidate; ordinary tangent/zero-area noncollapsed traces no longer pay that
unnecessary expansion cost.

Consequently the public local candidate frontier for overlapping rectangles was
measured directly from the executed ledger as `6_121` reject /
`6_122` succeed, in both operand orders.  The previous `6_577`/`6_578` boundary
included 456 units of unconditional projected-sector/exact-area work that is
now absent for ordinary contours.  No artificial work was restored to retain
the old value.  This remains a local implementation ledger frontier, not the
independent global cost oracle reserved for Task 5.

## Verification

```text
rtk ./gradlew :math:geometry:compileKotlinJvm :math:geometry:compileKotlinJs \
  --rerun-tasks --console=plain
BUILD SUCCESSFUL

rtk ./gradlew :math:geometry:jvmTest \
  --tests 'org.graphiks.math.geometry.PathOpsHybridTopologyF32Test' \
  --rerun-tasks --console=plain
21 tests completed, 0 failed

rtk ./gradlew :math:geometry:jsNodeTest \
  --tests 'org.graphiks.math.geometry.PathOpsHybridTopologyF32Test' \
  --rerun-tasks --console=plain
BUILD SUCCESSFUL

rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest \
  --rerun-tasks --console=plain
BUILD SUCCESSFUL in 24s
61 actionable tasks: 61 executed

rtk git diff --check
no output (success)
```

## Files

- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFlatteningF64.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64F32.kt`
- `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt`

## Remaining concerns for review

- The structural self-closed proof is intentionally limited to a whole source
  contour made from one source segment.  It never uses an F32 coordinate as an
  identity fallback, and it rejects rather than guesses outside that proof.
- A collapsed sector on an F32 face boundary, multiple containing faces, or
  without exact sibling connectivity rejects conservatively.  That prevents a
  partial result but may reject inputs a future proof could classify.
- Task 4 writer migration/deletion and Task 5's independent global budget
  oracle remain out of scope.
