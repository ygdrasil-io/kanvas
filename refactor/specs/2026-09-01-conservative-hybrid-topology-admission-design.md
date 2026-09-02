# Conservative hybrid topology admission

## Status

Validated in conversation on 2026-09-01. This document defines a short-term
stabilization contract for Task 3. It narrows the set of operations that may
succeed, without changing the long-term hybrid F64/F32 target described in
`2026-08-31-hybrid-f64-f32-path-topology-design.md`.

The implementation baseline is commit `59f4899cd`; review tracking through
`85ee016b3` is retained. The uncommitted round-5 experiment was discarded
before this design was written.

## 1. Purpose

Deliver a functional but deliberately incomplete path-operations engine in one
bounded implementation/review loop. Every successful operation must be backed
by a complete proof already present in the exact source topology or by the
strict endpoint-only local projected-contact proof accepted below. Every other
projection topology is rejected before aliases, physical cuts, DCEL state or
output can be published.

This stabilization is fail-closed. It optimizes for predictable behavior and a
clear support boundary, not for the final near-ISO Skia success rate.

## 2. Why the current Task-3 loop stops

The current implementation distributes source identity, projected
coincidence, collapsed winding, event accounting and output provenance across
five production files totalling more than 13,000 lines. Four correction rounds
made individual public reproductions green, but each new authority mechanism
introduced another false acceptance, false rejection or non-canonical budget.

The stabilization therefore adds no new general `full-cover`, proxy authority,
collapsed equivalence or physical-cut rule. It makes the support boundary
explicit and prevents the unproved branches from deciding public output.

## 3. Scope

### 3.1 In scope

- Public `PathOpsF32` binary operations, `simplify` and `asWinding`.
- Internal admission decisions in `:math:geometry`.
- Exact F64 source events and exact source overlaps.
- Endpoint-only local projected coincidences whose bounds already have exact
  source vertex identities.
- Deterministic public errors, work limits and JVM/JS parity.
- Public behavioral, geometric and numeric tests in `commonTest`.
- Documentation of supported and rejected topology families under `refactor`.

### 3.2 Out of scope

- Font and codec behavior.
- GM-specific filters or new GM exclusions.
- A new proof engine or a Skia PathOps port.
- Physical strict-interior projected cuts.
- Deferred-contact equivalence, collapsed XOR algebra and cross-operand
  `full-cover` authority.
- Task-4 writer replacement and Task-5 independent global budget oracle.
- Tests that inspect private types, source files, packages, imports or internal
  collection shapes.

## 4. Supported domain

An operation may proceed to the existing hybrid arrangement only when all of
the following hold.

1. Inputs and limits are valid and finite.
2. Source topology contains no duplicate self-closed curve primitive that
   would require equal-carrier proxy compression.
3. Every F32 contact is one of:
   - the projection of an exact F64 point event;
   - the projection of an exact F64 overlap covering both source intervals;
   - an endpoint-only local `PathProjectedCoincidenceF32` supported by one
     direct exact witness, where every bound resolves to an existing exact
     `PathVertexIdentityF64`.
4. No accepted relation requires a new parametric cut or projected structural
   identity.
5. No deferred endpoint contact remains.
6. No projected carrier collapses to an incidence. The stabilization does not
   attempt to prove that a collapsed incidence is irrelevant.
7. Public intersection counts contain only canonical source events. No proxy
   membership, adaptive-flattening joint or repeated occurrence creates an
   event.

This domain retains ordinary path operations, exact intersections, exact
overlaps, source n-way contacts and the already-proved endpoint-only local
projected contacts.

## 5. Rejected domain

The following families return
`IllegalStateException("path-f32-projection-collapse")` after their admission
scan completes:

- any deferred F32 contact without a direct exact witness;
- duplicate self-closed curve primitives that could activate equal-carrier
  proxy semantics;
- a projected coincidence requiring a strict-interior cut;
- partial or whole projected collapse, including collapsed XOR/Difference;
- a result that would require cross-operand equivalence or `full-cover` to
  discharge a conflict;
- a projected overlap not covered by one exact F64 overlap over both complete
  intervals;
- any unresolved equal-ray, ownership, orientation or event-count ambiguity.

The rejection is structural. It never depends on a GM name, path equality,
bounding-box size, coordinate-distance heuristic or list of known fixtures.

## 6. Architecture

### 6.1 Source capability gate

The source gate runs after public input validation but before source splitting
or equal-carrier compression.

It scans immutable source primitives and builds an exact geometric key from:

- primitive kind;
- F64 endpoints and controls;
- canonical forward/reversed parameterization.

Operand, contour, source-segment and original-endpoint provenance are retained
as payload beside the key. They never participate in geometric equality.

The key is a lookup aid, not geometric authority. Any candidate duplicate is
revalidated component-by-component. A duplicated non-degenerate self-closed
curve rejects before proxy planning, intersection-registry mutation or source
cut allocation.

Ordinary exact overlaps between different source paths remain supported and do
not use this rejection rule.

### 6.2 Immutable projection observation

Candidate discovery produces an immutable observation before any hybrid
publication. It contains:

- direct exact point/overlap witness references;
- endpoint-only projected proposals;
- whether a bound already has an exact source vertex identity;
- deferred endpoint contacts;
- strict-interior-cut requirements;
- collapsed carrier incidences;
- canonical source-event count.

It contains no alias, new vertex ID, physical cut, DCEL vertex, half-edge or
writer state.

### 6.3 Projection capability gate

The projection gate maps the observation to one of:

```kotlin
internal sealed interface PathHybridAdmissionF64F32 {
    data class Accepted(
        val exactPlanF64F32: PathAcceptedExactPlanF64F32,
    ) : PathHybridAdmissionF64F32

    data object Unsupported : PathHybridAdmissionF64F32
}
```

`PathAcceptedExactPlanF64F32` may contain exact source relations and
endpoint-only local projected relations. Its constructor validates these
invariants:

- every projected relation has one direct local witness;
- every endpoint is an existing exact identity;
- there are no deferred contacts;
- there are no strict-interior cuts;
- there are no collapsed incidences;
- no proxy/full-cover authority is required.

The accepted plan is the only value that may enter the arrangement. Production
guards reject if a later refactor tries to attach a deferred contact, physical
cut or collapsed incidence to it.

### 6.4 Existing arrangement

For accepted plans, the existing hybrid arrangement continues to provide:

- F32 vertex embedding;
- F64 source direction for ray ordering;
- exact winding aggregation for supported exact overlaps;
- face selection and public boundary output;
- final `maxVertices` and `maxHalfEdges` checks.

The complex Task-3 code may remain temporarily in the repository, but rejected
families cannot reach it at runtime. It is not a second authority and must not
be invoked as a fallback after an admission rejection.

## 7. Data flow

```text
PathF32 inputs
  -> validate inputs and limits
  -> debit and run source capability gate
  -> build exact source topology
  -> discover immutable projection observations
  -> debit and run projection capability gate
       Unsupported -> path-f32-projection-collapse
       Accepted    -> exact accepted plan
  -> existing hybrid arrangement
  -> existing trace writer
  -> public PathF32
```

No rejected operation reaches aliases, physical cuts, DCEL allocation or
output construction.

## 8. Identity and determinism

- Geometry remains in `:math:geometry`.
- New names use the established I32/I64/F32/F64 suffixes.
- Source identity uses exact F64 provenance and parameters.
- F32 is used only to observe the projected embedding or collapse.
- Signed zero is canonicalized only for topological comparison; a selected
  original payload is emitted with its local raw bits.
- Hash iteration, operand order, contour order and source IDs never resolve a
  geometric tie.
- Forward and reversed exact semantic keys compare under a documented total
  order.

## 9. Error and budget precedence

The public order is:

1. invalid input or invalid limit: `IllegalArgumentException`;
2. insufficient work budget during either admission scan:
   `IllegalStateException("path-candidate-limit")`;
3. completed scan identifies an unsupported topology:
   `IllegalStateException("path-f32-projection-collapse")`;
4. admitted canonical source events exceed `maxIntersections`: the existing
   intersection-limit error;
5. final canonical arrangement exceeds vertex/half-edge/output limits: the
   existing limit errors.

Every gate comparison, visit, sort comparison, lookup construction and copy is
preflighted with checked I64 arithmetic before work or allocation. Rejected
families are not exempt from the work budget needed to classify them.

Proxy membership and flattening joints never consume `maxIntersections`.

## 10. Atomicity

- Inputs remain immutable on success and failure.
- Admission observations and accepted plans are immutable.
- No proposal/commit transaction begins before admission succeeds.
- Admission rejection cannot publish aliases, IDs, cuts, half-edges, faces or
  partial output.
- There is no fallback to the old authority after rejection.

## 11. Test strategy

All new or changed tests use public APIs and assert output geometry, membership,
bounds, raw public payloads, exact public errors, limits or input immutability.

### 11.1 Supported-success matrix

- ordinary rectangle and polygon operations for all five boolean operations;
- exact point intersections and exact overlaps;
- exact source n-way contacts and disjoint events;
- endpoint-only direct local projected contacts already accepted by Task 2;
- translation, scale, cyclic rotation, reversal and operand permutation;
- signed-zero provenance and JVM/JS equality;
- adjacent public limit boundaries.

### 11.2 Stable-rejection matrix

- the thin-lens deferred-contact reproduction;
- compact and separate duplicate self-closed carriers with a third-party clip;
- collapsed `C XOR C` and `C DIFFERENCE C`;
- significant and partial collapsed siblings;
- physical strict-interior projected-cut candidates when a public fixture
  reaches that observation; otherwise the missing public fixture remains an
  explicit coverage gap and no synthetic/internal test is introduced;
- distant or transitive projected overlap without direct exact authority;
- overlapping/unowned claims.

Every rejection test asserts
`IllegalStateException("path-f32-projection-collapse")`, checks both operand
orders where applicable and verifies source paths are unchanged.

### 11.3 Verification

1. focused public JVM tests;
2. focused public JS tests;
3. complete `:math:geometry:jvmTest` and `:math:geometry:jsNodeTest`;
4. `git diff --check`;
5. independent Sol spec review;
6. independent Sol quality review.

No infrastructure, source-shape, import, package or private-collection test is
allowed.

## 12. Delivery and stopping rule

The implementation is one bounded stabilization task followed by at most two
correction rounds.

- A first gate failure receives one focused correction round.
- A second gate failure receives one final focused correction round.
- A third failure stops implementation. The finding is recorded as a ruling;
  no new authority mechanism or special-case shortcut is added.

The stabilization is complete only when both independent gates pass against
this deliberately narrow contract. It must not be described as final Skia ISO
parity.

## 13. Tracking and dashboard semantics

Follow-up reports distinguish:

- **rendered**: operation was admitted and produced output;
- **excluded**: GM remains outside the agreed denominator;
- **topology rejected**: the engine intentionally rejected a topology outside
  the stabilized capability domain.

No GM identifier participates in the admission decision.

## 14. Later proof-engine migration

After Task 4 and the rendable-GM baseline are stable, unsupported families may
be reintroduced one proof class at a time. Each class must define:

- its exact source authority;
- its immutable proof obligation;
- its canonical event count;
- its selection-aware collapse disposition;
- its public metamorphic tests;
- its budget derivation.

The first later class should be strict-interior projected cuts. Cross-operand
full-cover and collapsed algebraic equivalence remain separate classes and
must never be used as implicit authority for one another.
