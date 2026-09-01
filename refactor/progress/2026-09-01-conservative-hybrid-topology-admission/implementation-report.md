# Conservative hybrid topology admission — implementation report

## Baseline

Implementation started from `db2c12784cc1311185280c694c3cc7d1e8611f15` with a clean worktree (the controller-owned SDD progress file is ignored).  This is a functional stabilization with a deliberately reduced capability domain, not a claim of final Skia ISO parity.

## Delivered gates

`PathHybridAdmissionF64F32.kt` now owns immutable input/admission types and two fail-closed gates.

- Before flattening or proxy planning, the source gate scans immutable `PathF32` commands for duplicate, non-degenerate, complete self-closed quadratic/cubic/arc primitives.  It canonicalizes normalized F64 geometry (including topological `-0.0`), preflights scan/copy/sort work, never debits a comparator, and finishes the bounded indexed adjacent-key scan before rejecting; it does not materialize an eager `zipWithNext()` pair list.
- After the projected broad phase, an immutable observation records exact witnesses, endpoint-only claims, deferred endpoint observations, strict-cut requirements, collapsed incidences, and unsupported projected contacts.  Its full validation completes before returning `Unsupported`.
- Only transaction-validated endpoint-only relations enter `PathAcceptedExactPlanF64F32`; aliases are built solely from that plan.  Deferred, collapsed, strict-cut, unowned, and malformed projected findings fail closed with `path-f32-projection-collapse` before aliases, cuts, or DCEL work.
- Deferred endpoint resolution now derives a checked-I64 capacity and debits the complete deferred buffer/copy bound before allocating the capacity-sized result list.  The later immutable observation copy remains separately preflighted, so no allocation is uncovered or charged twice.
- The projected candidate-edge view now derives `6N` from `projectedSpansF64F32.size` and debits it before its `map` traversal/allocation.  The post-plan endpoint guard separately reserves checked `5P` (one full proposal visit plus four nullable identity tests), evaluates all four tests for every proposal without early exit, then rejects only after the complete scan; this does not double-debit the plan factory copy or transaction validation.

## Supported public families

The public matrix retains ordinary rectangle boolean operations, a representable self-closed cubic boundary, exact point crossings, exact overlaps, exact n-way junctions, disjoint events, signed-zero inputs, cyclic/reversed/transformed contour cases, and the locally witnessed endpoint-only projected relation under operand swap.  That endpoint-only success snapshots both operands and proves the forward and swapped results agree.  Unary `asWinding` retains its ordinary polygon success path and passes through the same capability gates as `simplify`.

## Stable rejected families

Public operations now reject with the exact `path-f32-projection-collapse` error while retaining every input snapshot for duplicate self-closed carriers, repeated tiny/collapsed loops, collapsed XOR and difference, significant/partial collapsed siblings, and the thin-lens overlapping projected-claim family.  The unowned no-adjacent-witness case is covered in both operand orders with both inputs immutable.  The collapsed-sibling intersection branches and the thin-lens `path-candidate-limit` precedence branch also snapshot their inputs.  The thin-lens family is covered for `simplify`, `C UNION C`, `C INTERSECT C`, and union with a distant third rectangle in both operand orders.

## Limits and precedence

Duplicate-source and thin-lens public checks confirm that a depleted candidate budget reports `path-candidate-limit` before a topology rejection.  Projection admission runs before the public intersection limit, so an inadequate `maxIntersections` cannot mask an unsupported projection.  Source topology temporarily bypasses its splitter intersection gate; only the accepted plan's canonical source-event count is charged and compared to `maxIntersections`.  Therefore an endpoint-only relation without a strict cut no longer creates an extra public intersection event.

## Public coverage gap

There is still no public `PathF32` fixture that physically reaches a strict-interior projected cut.  That case is intentionally not synthesized through internal test hooks.  The production observation and post-plan guard reject it fail-closed, while public unowned and overlapping projected claims remain covered independently.

## Verification

RED was observed before the source gate: the full hybrid topology class left each of the twenty duplicate `count=2` cases successful, producing assertion failures because `path-f32-projection-collapse` was absent.  A second RED pass showed the former collapsed/distant successes and the old projected endpoint limit boundary no longer matched the conservative public contract.  The correction-1 public-only immutability extensions were immediately green because they expose already established public behavior; the correction-1 allocation ordering and correction-2 ledger ordering defects have no independent public RED oracle and are deliberately not tested by prohibited private/source-shape/infrastructure assertions.

Focused JVM and JS hybrid-topology runs, followed by public `PathOpsF32Test` plus hybrid-topology runs, were green.  The complete fresh JVM/JS verification was then run with:

```
rtk ./gradlew :math:geometry:jvmTest :math:geometry:jsNodeTest --rerun-tasks
```

It completed successfully for both backends with 61 actionable Gradle tasks executed.  For correction 1, the focused `PathOpsHybridTopologyF32Test` JVM and JS commands were rerun after the preflight changes (20 and 53 actionable tasks respectively), followed by the same complete 61-task JVM/JS command.  `rtk git diff --check` and the pre-commit status were also inspected after the report was written.

## GM accounting

Rendered means an operation was admitted and produced output.  Excluded means a GM is outside the agreed denominator.  Topology rejected means a deliberate capability-domain rejection.  This task added or removed no GM, render, dashboard, score, or exclusion; font and codec modules were untouched.
