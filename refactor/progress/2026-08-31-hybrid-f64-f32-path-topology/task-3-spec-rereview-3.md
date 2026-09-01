# Task 3 — spec rereview round 3

Verdict: **FAIL**.

Reviewed range: `f1d3772d4..98a280385`.

## Critical

1. Equal-carrier n-way compression replaces identical self-closed carriers by one broad-phase
   leader, but later third-party point events are not propagated to the removed members. One loop
   plus a clipping rectangle succeeds; two or three identical loops in one contour reject, while
   equivalent separate contours succeed. Evidence: `PathIntersectionsF64.kt:445-447`,
   `:502-525`, `:647-753`, `:840-859`, `:1923-1940`, `:2034-2060`.
2. No-face `C XOR C` is only bypassed when `PathF32.equals` is true. Geometrically identical
   collapsed loops differing only by `+0.0f/-0.0f` still reject instead of returning empty.
   Evidence: `PathOpsF32.kt:33-37`, `PathHybridTopologyF64F32.kt:546-554`.

## Important

- Signed-zero policy is global: an irrelevant distant `-0.0f` operand rewrites selected output
  vertices that only have `+0.0f` local provenance. Evidence: `PathOpsF32.kt:29-31`, `:270-272`,
  `:309-322`, `:1256-1284`.
- The physical strict-cut branch is structurally coherent and model-reachable by inspection, but
  remains without a public fixture. Its propagation/remap and overlapping-claim rejection are not
  behaviorally proved.
- Equal-carrier grouping executes `IntArray.sort()` after only a linear preflight; the
  `O(N log N)` comparison work is not debited. Evidence: `PathIntersectionsF64.kt:660-664`, `:786`.

## Round-2 closure

Closed: representable self-closed cubic; significant sibling compensation; `EVEN_ODD`
multiplicity; group scans; `sumOf`/exact-area accounting; two-phase physical allocation;
`maxSubdivisionDepth`; local collapse neighbours; post-alias limits; reversed old `u+2` case.

Open/partial: general no-face XOR, physical cut/overlapping claim public proof, local signed-zero
provenance. The new equal-carrier propagation and sort-budget findings are open.

Fresh public JVM/JS suites passed (61 tasks); Git status and diff-check were clean. No font, codec,
GM/exclusion, infrastructure-test, or non-math geometry change was found.
