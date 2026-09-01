# Task 3 — quality rereview round 2

Verdict: **FAIL**.

Reviewed range: `80ac9f78c..f1d3772d4`.

## Critical

The collapsed ledger permits forbidden compensation between sibling losses. It reduces every
contour to `sign(exactArea)`, groups by one F32 point, and can return `KEEP` after summing signs
before checking per-contour collapse tolerance. Public JVM reproduction: ten tiny positive
self-closed cubics reject alone, but adding ten smaller opposite cubics at the same endpoint makes
`simplify` succeed with an empty path. A sibling therefore hides a contour already known to exceed
the tolerance. Evidence: `PathArrangementF64F32.kt:1862`, `:2012-2042`, `:2095-2138`.

## Important

1. Winding multiplicity is lost by reducing a contour to `-1/0/+1`. With `EVEN_ODD`, ten
   traversals of the same tiny lobe have empty coverage but reject instead of simplifying to empty.
2. Canonical F32 point comparison does not break `-0.0f/+0.0f` ties by bits. The union of two
   identical triangles emits the signed zero from the first operand, so reversing operands changes
   `PathF32.equals`. Evidence: `PathArrangementF64F32.kt:807-825`, `:2816-2835`.
3. Budget gaps remain: repeated collapsed-group scans have no budget parameter; `identityCountI64`
   uses `sumOf` before debit; trace canonicalization debits linearly while exact expansion
   accumulation can be quadratic.
4. `maxIntersections` is checked only after proposal cuts, maps, groups, and occurrence graph have
   been allocated, so a small public limit does not bound staging memory.
5. Public behavioral tests do not yet cover a real physical interior cut with n-way propagation,
   partial collapse local neighbours, reversed self-closed spans, signed-zero permutations, or
   `EVEN_ODD` multiplicity.

## Minor

The forced split of a self-closed cubic continues while `depth < 2` before checking
`maxSubdivisionDepth`; a configured maximum of one can still recurse to depth two.

## Round-1 findings

- Interior-cut machinery and list remap exist but lack full public proof.
- Collapsed data survives until post-selection, but joint sector composition is still incorrect.
- Partial neighbours, post-alias `maxVertices`, transaction comparator, labels/seams and the
  `DROP` branch are closed.
- Local complexity accounting and no-face multi-contour semantics remain open.

Fresh targeted and complete JVM/JS tests passed (61 tasks), `git diff --check` passed, and no file
was modified during review.
