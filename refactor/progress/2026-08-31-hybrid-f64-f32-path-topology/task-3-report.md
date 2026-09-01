# Task 3 — Atomic projected claims and collapsed incidences

## Status

Fix round 4 is locally implemented and verified on 2026-09-01, but Task 3
remains **in progress pending fresh independent spec and quality gates**. This
report records local evidence only. It does not claim that an unproved public
physical strict-interior-cut fixture has been closed.

## Public behavioral coverage

Every round-3 test addition uses only public `PathBuilder`, `PathOpsF32`,
`PathAnalysisF32`, and `PathOpsLimitsI32` behavior: emitted geometry/membership,
exact public errors, deterministic limits, and operand/contour permutations.
The round adds no topology, node, identity, source-registry, or internal-collection
assertion.

The rereview findings were checked against public fixtures and are retained as
GREEN coverage:

- The ordinary self-closed cubic
  `moveTo(1,1).cubicTo(2,1,1,2,1,1).close()` remains a normal representable
  loop: `simplify` and distant `DIFFERENCE` retain `(1.2,1.2)`, while distant
  `INTERSECT` is an ordinary empty path rather than a collapse rejection.
- Ten positive tiny lobes still reject with `path-f32-projection-collapse` when
  a smaller opposite sibling is added; the significant contour cannot be
  compensated away.
- The same ten repetitions with `EVEN_ODD` simplify to an empty path, preserving
  winding multiplicity rather than reducing the contour to `sign(area)`.
- Exact `C XOR C` for two identical collapsed loops returns the public empty
  path, rather than rejecting a no-face identity operation.
- `UNION` of geometrically identical `-0.0f` and `+0.0f` triangles is
  bit-structurally equal under operand swap.

The only newly discovered TDD RED in this round was a distant `INTERSECT` with
a collapsed loop: an operand-local loss collector incorrectly treated the
`MoveTo -> beginSegment(t=0)` handoff as a collapsed interval and threw
`path-f32-projection-collapse`. The collector now skips only that exact source
handoff. The retained test observed the error before the patch and the public
empty result after it, on JVM and JS. Existing green baseline tests are not
relabelled as REDs.

## Projected-claim transaction and cut invariants

- Proposals are sorted by semantic Point witness, semantic source span, and F64
  interval. All relations for a witness are validated as a transaction, and
  inter-transaction conflict checks complete before projected IDs, aliases,
  carrier subdivisions, DCEL state, or output are published.
- Strict interior endpoints receive deterministic projected identities from the
  exact witness, source incidence, and source parameter. F32 is only the final
  embedding validation; it is never an identity, grouping, or ordering key.
  An exact pre-existing identity is the only no-op.
- The materializer is two phase. Its first, candidate-work-bounded scan keeps
  only canonical structural cut identities and occurrence counts. It derives
  the exact source-event total from `PathSourceTopologyF64`, canonicalizes the
  n-way projected event keys, and gates `maxIntersections` before allocating
  physical cuts, per-identity validation groups, carrier partitions, aliases,
  or DCEL state. The bounded relation index is explicitly debited separately;
  it is not mistaken for a final cut allocation.
- Endpoint-only relations between distinct structural identities are events even
  when they insert no physical cut. Repeated n-way occurrences of the same
  event key debit once. A continuation is free only for the proven
  degree-two/single-rail-pair case.
- A validated event is propagated across its coincidence component before
  subdivision. Each `PathProjectedCoincidence` is remapped to a list of final
  sections, whose exact parameter intervals are checked for ordered contiguous
  coverage with neither gap nor overlap.
- Transaction comparison is total and lexicographic. Operand, contour, and
  segment labels navigate provenance but never settle a geometric equality;
  a complete semantic tie is validated as a group and unresolved disagreement
  rejects.
- `maxVertices` remains a final canonical-alias/DCEL limit. Temporary
  materializer capacity is bounded only by the candidate-work limit and is not
  substituted for that public vertex count.

## Collapsed-boundary dispositions

- `PathBoundaryDisposition { KEEP, DROP, REJECT }` is explicit. Collapsed
  incidences and their sector/winding ledger survive provisional aliases and
  are classified after face selection, before empty/cancellation/trace output.
  A missing half-edge is never evidence that a contour was unselected.
- Literal partial collapse carries the two real cyclic source-neighbour rays,
  including source-span seams. It rejects only if a selected adjacent carrier
  overlaps its exact parameter interval; it does not scan a distant selected
  edge from the same contour. Malformed or unresolved adjacency rejects.
- A nonliteral self-closed candidate is added only after the DCEL proves that
  every carrier of its exact source provenance is absent, independently of face
  selection. The proof requires one original segment, a uniform directed
  partition of `[0,1]`, and a shared exact endpoint identity. A representable
  but unselected boundary therefore stays on the ordinary path. There is no
  size/bounding-box heuristic and no F32 identity fallback.
- Fully collapsed siblings are evaluated as a connected exact-incidence group.
  Each contour's local selectedness and whole-contour/tolerance eligibility is
  tested before aggregation, then exact oriented source winding deltas are
  composed through the operation truth table. Ambiguity rejects. This keeps
  `EVEN_ODD` multiplicity, makes `C XOR C` empty algebraically, and prevents a
  significant contour from being hidden by an under-threshold opposite sibling.
- `DROP` is possible only for a proven selected, whole literal collapse where
  every required contour has exact normalized double area `<= 2^-45`. A
  significant, partial, structurally absent, or ambiguous required contour
  rejects atomically. Unselected ledger data is ignored only at emission.

## Local accounting and source splitting

- All new scans, maps, sorts, adjacency indexes, grouping passes, area work,
  and materializer allocations receive checked-I64 debit/preflight before work.
  The former `sumOf` count is replaced by an explicit checked scan.
- Exact source-area expansion counts and preflights its conservative quadratic
  expansion work before allocation/predicate evaluation. Pairwise reduction
  avoids repeated accumulator copying without weakening that debit.
- Repeated self-closed duplicate rails use a source-proven, kernel-revalidated
  n-way star rather than redundant pair expansion. The grouping key is only a
  candidate hint; complete exact overlap and structural provenance are checked
  before any pair is suppressed, so input hash/order cannot create equality.
- Forced self-closed splitting checks `maxSubdivisionDepth` before every forced
  recursive descent; a limit of one cannot recurse to depth two.
- The measured local regression frontier is **5,989 reject / 5,990 succeed**
  in both operand orders. It reflects the currently executed local ledger
  after redundant duplicate-pair work was removed; no artificial debit was
  restored to preserve an earlier threshold. It is not Task 5's independent
  global budget oracle.

## Physical strict-interior-cut public-fixture ruling

The physical branch (`endpointIdentityF64 == null`) remains implemented and is
covered by its structural validation, n-way propagation, and contiguous remap
in production, but it has no retained public fixture in this round. This is a
known gate concern, not a claim of full behavioral proof.

Three bounded public `PathBuilder` explorations were tried with temporary local
instrumentation, then completely removed:

1. The literal analytic cubic/rail closure caused the closing `up` rail to
   overlap the cubic's initial projected rail, which correctly rejected before
   the materializer.
2. Moving that closure away and retaining monotone cubic controls made
   `UNION`/`INTERSECT` succeed, but adaptive flattening exposed only the
   `t≈.125/.5` joints; no projected proposal and no strict physical endpoint
   occurred.
3. Forcing the intended `.25/.75` rails with nonmonotone x controls did expose
   those projected rails, but first created an intra-cubic overlap without an
   exact witness, so the conservative projection guard rejected before the
   materializer.

No stable geometric public assertion could therefore prove this implementation
branch without retaining an invalid fixture. No temporary instrumentation,
scratch test, debug output, or test-only production switch remains.

## Round 4 — equal carriers, no-face XOR, local zero payload and face locator

### REDs observed before the production edits

The round-3 public failures were reproduced on JVM and Kotlin/JS before the
fixes:

- compressing equal self-closed carriers retained only a broad-phase leader;
  later point cuts from a third-party clip did not reach the removed source
  members, so compact and separate encodings diverged;
- an under-threshold tiny self-closed loop dropped alone but rejected when
  nested in a filled rectangle because both reverse DCEL boundary cycles were
  counted as containing faces;
- `XOR` used a raw structural `first == second` escape hatch, so a cyclic or
  signed-zero-equivalent operand reached a different budget/topology path;
- signed-zero output was globally rewritten from all input payloads, allowing
  an unselected distant `-0.0f` to change a selected `+0.0f` boundary;
- the equal-carrier source-segment `IntArray.sort()` had only a linear debit.

### Causal remediation

- An equal-carrier group remains only a broad-phase proxy.  Every leader point
  or overlap event is expanded over the Cartesian product of its exact member
  ranges *before* the registry creates components, identities or source
  splits.  The source-overlap star still proves the group, but no longer
  substitutes for third-party cuts.  The dispatch work and its checked
  `memberCount × memberCount` cost are preflighted before registry mutation.
  The structural source-segment sort now reserves its deterministic
  `O(N log N)` comparison cost before `IntArray.sort()`.
- The public compact/separate n=1..3 matrix uses one physical clipping
  rectangle and discriminant membership probes for all five boolean operations.
  It has 30 independent test cases so Kotlin/JS keeps its two-second test
  limit.  A temporary public bisection (removed after derivation) found the
  exact n=2 `maxIntersections` frontier `215` reject / `216` success for
  compact, separate, and both operand orders.  The four permanent static
  boundary tests assert that same frontier; this does not recalibrate any
  existing limit.
- The face locator now retains only canonical CCW left-face cycles and chooses
  the innermost exact positive-area cycle.  Its reverse twin is not a second
  candidate face.  The public nested-loop case covers ordinary fill, hole,
  reversed outer contour, inverse winding and a boundary ambiguity rejection.
- The raw XOR identity bypass is removed.  Deferred projection endpoint
  contacts can be discharged only by a complete reciprocal exact-overlap cover
  of both source contours: every carrier has one full-interval opposite-
  operand match, the match is reciprocal, and its orientation is uniform
  (`+1` or `-1`).  This cover is source-only and creates neither an alias nor a
  cut.  `C XOR C`, signed-zero cyclic rotations, and geometric reversal now use
  the same validated ledger; at `maxCandidateProbes = 1` the direct and rotated
  forms both report `path-candidate-limit`.
- Signed-zero choice is local to the exact incident component.  Its semantic
  coordinate order has a deterministic raw-bit tie-break, while the writer
  may use only the already-selected carrier's matching original payload.
  There is no global signed-zero policy or post-topology rewrite.
- Kotlin/JS can retain a Float-shaped analytic bounds value as a JavaScript
  number at the F32/F64 normalization boundary.  Normalization reconstructs
  the raw F32 payload before choosing its shared F64 envelope.  The public tiny
  loop test now also covers translation and scale.  Removing that reconstruction
  temporarily produced the expected Kotlin/JS RED (`IllegalStateException`
  from the arrangement); restoring it is green on JVM and JS.

### Scope and performance ruling

The existing public 10-tiny-lobe and 20-sibling-lobe JavaScript tests remain
green (`0.001 s` and `0.0 s` in the Node XML report); the slowest new n=3
case is `0.558 s`, below the two-second per-test limit.  No new 10/20
equal-carrier giant-loop stress test is retained: the observed 20k-carrier
scaling concern is assigned to Task 5's independent global budget/performance
work rather than disguised as a Task-3 fixture.

The physical strict-interior-cut/remap public-fixture gap above is unchanged.
No additional derived fixture was retained because it would not be an honest
proof of that physical branch.

## Self-review against round-2 rereviews

- **Representable self-closed cubic:** closed by post-DCEL carrier-absence proof;
  the public simplify/distant-operation test is green.
- **Sibling compensation and multiplicity:** closed by per-contour eligibility
  before group aggregation and exact signed winding composition; positive/opposite
  sibling and `EVEN_ODD` public tests are green.
- **No-face XOR:** closed algebraically; the public `C XOR C` empty test is green.
- **Signed zero:** closed by deterministic raw-bit total point tie-break; public
  operand-swap equality is green.
- **Group/area/staging budgets:** closed locally by group debit, explicit count
  scan, quadratic exact-area preflight, and the two-phase candidate-bounded
  materializer. Task 5's independent global oracle remains out of scope.
- **Depth bound:** closed by the forced-split guard.
- **Physical strict cut/remap public proof:** not closed; the bounded exploration
  above is the sole remaining concern for fresh gates.

## Verification

```text
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest \
  --tests 'org.graphiks.math.geometry.PathOpsHybridTopologyF32Test' \
  --rerun-tasks --console=plain
BUILD SUCCESSFUL
61 actionable tasks: 61 executed

rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest \
  --rerun-tasks --console=plain
BUILD SUCCESSFUL in 26s
61 actionable tasks: 61 executed

rtk git diff --check
no output (success after final verification)
```

## Files

- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathArrangementF64F32.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathFlatteningF64.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathHybridTopologyF64F32.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathIntersectionsF64.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathOpsF32.kt`
- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/PathSourceTopologyF64.kt`
- `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/PathOpsHybridTopologyF32Test.kt`

## Remaining concerns for review

- Public behavioral proof of a real strict physical projected cut plus n-way
  final-section remap is absent, as ruled above. The branch is retained but
  not claimed as conformant solely from internal reasoning.
- Some ambiguous collapsed-sector configurations remain conservative `REJECT`s;
  this is deliberate to prevent partial output. Task 4 writer migration/deletion
  and Task 5's independent global budget oracle remain out of scope.
