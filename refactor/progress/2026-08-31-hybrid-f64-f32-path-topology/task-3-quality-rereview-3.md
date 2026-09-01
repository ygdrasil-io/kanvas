# Task 3 — quality rereview round 3

Verdict: **FAIL**.

Reviewed range: `f1d3772d4..98a280385`.

## Critical

1. Equal-carrier compression loses third-party interior cuts. Two identical self-closed cubics in
   one contour plus a clip reject for every boolean operation; the equivalent two-contour encoding
   succeeds. The star overlap reconnects endpoints only and never copies later leader crossings
   to removed members. Evidence: `PathIntersectionsF64.kt:502-526`, `:647-753`, `:840-859`, then
   `PathHybridTopologyF64F32.kt:469-508`.
2. A fully collapsed under-threshold loop simplifies to empty alone, but rejects when nested in a
   filled rectangle. The face locator counts the two reverse half-face cycles as two containing
   faces and treats the result as ambiguous. Evidence: `PathArrangementF64F32.kt:2456-2479`,
   `:2750-2777`.

## Important

- Equivalent equal-carrier encodings have different `maxIntersections` frontiers: `3414` for two
  contours versus `6827` for two primitives in one contour.
- Global signed-zero rewriting lets an unselected distant operand change selected output bits.
- Exact `A XOR A` bypasses all budget work, while a cyclically rotated equivalent `A` rejects at
  `maxCandidateProbes=1`; the budget frontier is not rotation-invariant.
- Equal-carrier grouping underdebits its `IntArray.sort()`.
- The physical strict-cut branch and its remap remain without a public behavioral proof.

## Round-2 closure

Closed: representable self-closed cubic, sibling compensation, `EVEN_ODD` multiplicity, direct
signed-zero operand permutation, quadratic area preflight, two-phase staging, post-alias vertex
limit, depth limit, partial neighbours, reversed intervals and transaction comparator.

Open/partial: general XOR semantics/budget, local signed-zero provenance, equal-carrier event
propagation/counting, nested-face collapsed `DROP`, and physical-cut proof.

Fresh complete JVM/JS tests passed (61 tasks); worktree and diff-check were clean. No file was
modified during review.
