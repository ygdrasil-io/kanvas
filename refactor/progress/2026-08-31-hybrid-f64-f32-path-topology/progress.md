# Hybrid F64/F32 path topology progress

## Task 1 — Preserve source spans

Completed 2026-08-31. Source segment/parameter provenance now traverses flattening and split edges; the transitional source-topology model and legacy adapter are present.  Projection no longer applies unsafe late compaction to synthetic F64 contours with multiple potential witnesses, and permitted collapse is represented by `Drop`.

Fix round 1: production now constructs the source topology before the legacy arrangement, retains coincident source locations/seams, and carries exact-cut boundaries through source spans.

Fix round 2: removed the legacy raw-split side channel; the transitional adapter emits all legacy edges from authoritative spans and flattened sections.

The Task 1 regression suite and focused JVM/complete JS verification passed before commit. See `task-1-report.md` for commands and evidence.

Fix round 5: le registre exact unique alimente désormais spans, sections et le pont de provenance minimal vers `PathArrangementF64`; le même budget débite cette transition avant toute sortie. La projection n'a plus de compactor, de chord ni de fallback permissif : chaque contour est `Keep`, `Drop` ou `Reject` atomique, et le ledger temporaire valide les claims exacts `(witness, span, intervalle)` avant émission. Les régressions publiques couvrent la subdivision collinéaire, `PointF64` → `OverlapF32`, claims disjoints sous permutation/relabeling et immutabilité, claims chevauchants, et le seuil de budget.

Ruling de transition Task 1 documenté : une preuve locale de claims disjoints conserve les trois régions; des claims chevauchants, ou un `PointF64` promu en `OverlapF32`, rejettent avec `path-f32-projection-collapse`. Les cinq tangences publiques concernées restent donc des rejets conservateurs jusqu'à Task 2. Dette de suivi : Task 2 pourra restaurer ces succès uniquement avec une `PathProjectedCoincidenceF32` locale, bornée et prouvée; aucun alias F32, matching de coordonnées source ni second DCEL hybride n'est introduit ici.

Breaker Task 1 : les gates Sol finaux restent en échec après cinq rounds. Les
findings ne sont pas masqués : neuf rulings explicites sont consignés dans
`task-1-breaker-rulings.md`. Les prérequis load-bearing (porteurs pour la suite)
sur l'overlap canonique, les IDs, l'ancrage local des point-witnesses, les paires
adjacentes, les tests full-pipeline et le budget sont transférés à Task 2; les
claims/endpoints et le `Drop` de contour complet à Task 3; la frontière globale
de complexité/budget à Task 5. Task 1 est donc close procéduralement avec findings
parqués, pas approuvée comme état final autonome.

## Task 2 — Hybrid F64/F32 projected contacts and arrangement

Completed 2026-08-31. `PathOpsF32` binary and unary paths now use the authoritative route
`source topology -> hybrid topology -> PathArrangementF64F32 -> hybrid trace writer`; the old
legacy arrangement adapter has no common-main caller. The exact registry exports canonical n-way
overlap components with strict interior incidences, point authority is local to its exact witness,
and unsupported adjacent/backtracking projected relations reject. The single hybrid DCEL embeds
on lifted F32 representatives while ordering rays in source F64 and aggregating operand winding.

Task-2 tests cover all tangent-operation transforms with literal probes, local/absent/distant
point witness cases, backtracking, n-way relabeling, signed zero, arrangement authority and the
candidate-budget boundary/permutation. Focused JVM verification passed 85 tests; complete JS
verification passed 299 tests; `git diff --check` passed. New hybrid maps, arrays, pair work,
sorts, containment, writer work and immutable conversion preflight deterministically. The
historical source-topology debit audit remains explicitly carried to Task 5 under breaker ruling
9; details and exact lines are in `task-2-report.md`.

Fix round 1: closed the durable carrier/atomic-overlap/authority/limit/budget findings. The
hybrid flattener now derives a bounded tolerance from the observable denormalized F32 lattice,
so translated tangent ovals no longer manufacture an unwitnessed micro-carrier contact; all five
operations × three transforms are green on JVM and JS without relaxing the exact witness guard.
The direct endpoint ticket registry replaces source-topology ±16-ULP recovery, staggered n-way
overlaps remain atomic across operands, and the exact `4_328` reject / `4_329` success frontier
is identical under permutations on both targets. Round-1 focused JVM verification passed 89
tests and complete JS passed; details, RED evidence and the Task-5 historical-debit carry are in
`task-2-report.md`.

Fix round 2: reviews were verified with public `PathBuilder`/`PathOpsF32` fixtures before the
implementation was changed. Exact overlap evidence now enters source topology through an event
sweep and direct parameter-bit tickets, so every active endpoint is source-atomized and counted
before hybrid aliases/DCEL allocation. The hybrid preserves both canonical split geometry and
per-incidence F64 evaluation for representative choice; all flattened sections remain carriers.
Public staggered overlaps, final-DCEL `maxHalfEdges`, source-event `maxIntersections`, whole-
contour collapse disposition, extreme normalization, signed zero, and centered local-witness
boundaries are green on JVM and JS. The centered fixture uses nonzero `+/-2^-25f` input bits;
its `8` reject / `9` success boundary replaces an invalid y=1 fixture that rounded before the
pipeline. Fresh `:math:geometry:jvmTest :math:geometry:jsNodeTest --rerun-tasks` and
`git diff --check` passed. The old `4_329` independent-budget claim is superseded: `4_679`
reject / `4_680` success is a deterministic regression boundary only, while the independent
global source-topology ledger audit remains explicitly assigned to Task 5.

Fix round 3: the validated source-atomization ruling is recorded in
`task-2-round-3-ruling.md`.  Task 2 therefore does not add an unreachable hybrid strict-interior
cut materializer or claim completion of full collapsed `KEEP`/`DROP`/`REJECT` disposition; those
proposal/commit semantics and their `maxIntersections` accounting belong to Task 3 steps 3--4.
The independently reproducible arrangement work is closed: outgoing source directions now use
per-incidence F64 points; a single exact angular event sweep proves cyclic bundle contiguity and
F32 embedding order; and exact overlap authority uses a sorted atomic-witness two-pointer join
rather than a Cartesian product.  All new sweep/index allocations, lookup, scans and deterministic
sorts preflight checked I64 work before execution.  The public high-valence, tangent,
staggered-overlap and permutation tests are green; the captured (non-oracle, Task-5) global
budget regression is now `4_986` reject / `4_987` success.  Fresh JVM/JS verification and the
diff check are recorded in `task-2-report.md`.

## Task 3 — Atomic projected cuts and collapsed disposition

Fix round 2 is committed as `f1d3772d4`, but both fresh gates fail. The implementation now has
canonical projected event grouping, n-way propagation, final-section-list remapping, post-alias
vertex limits, local-neighbour partial-collapse checks, and the mandatory collapsed sibling
`INTERSECT` repro. Fresh JVM/JS verification passes 61 tasks.

The round-2 gates found that the self-closed provenance is too broad for ordinary representable
closed cubics, sibling areas can still compensate a significant loss, winding multiplicity is
reduced to its sign, exact no-face `C XOR C` is rejected, signed-zero output depends on operand
order, and several local scans/staging allocations are not bounded at the required gate. A public
physical strict-interior cut/remap proof is also still missing. Task 3 remains in progress; see
`task-3-spec-rereview-2.md` and `task-3-quality-rereview-2.md`.

Fix round 3 is committed as `98a280385` and closes the round-2 public reproductions, local work
debits, depth limit, operand-local collapse intervals and two-phase physical-cut staging. Its fresh
JVM/JS matrix passes 61 tasks, but both round-3 gates fail. Equal-carrier compression loses
third-party interior cuts and changes event counts; exact XOR is only short-circuited by structural
equality; signed-zero provenance is global; an under-threshold collapsed loop nested in a filled
face falsely rejects; and the compression sort is underdebited. The physical strict-cut branch is
coherent by inspection but still lacks a public fixture. Task 3 remains in progress; see
`task-3-spec-rereview-3.md` and `task-3-quality-rereview-3.md`.

Fix round 4 is locally complete pending fresh independent gates. A canonical equal-carrier proxy
now propagates every third-party leader point/overlap event to every exact member before source
components and splits; the public n=1..3 × five-operation compact/separate matrix is green on
JVM/JS, and the n=2 `maxIntersections` frontier is identically `215` reject / `216` success under
compact/separate encodings and operand swap. The proxy sort and propagation dispatch preflight
their checked work. The locator uses only canonical CCW left-face cycles, closing the nested tiny
loop DROP through holes, inverse/reversed fill and boundary ambiguity coverage. The raw XOR
equality bypass and global signed-zero rewrite are removed; exact reciprocal full-contour cover
permits no-face algebra only for proven cross-operand components, including geometric reversal.
Signed-zero payload selection is local to incident provenance. Kotlin/JS normalization now
reconstructs the logical F32 bounds payload; its public translated/scaled tiny-loop RED was
reproduced by temporarily removing that reconstruction and is green after restoration. Full
`:math:geometry:jvmTest :math:geometry:jsNodeTest --rerun-tasks --console=plain` passed in 26s
with 61 actionable tasks. The physical strict-interior-cut public-fixture gap remains explicitly
open; 20k equal-carrier stress scaling is assigned to Task 5, and no false fixture is retained.
