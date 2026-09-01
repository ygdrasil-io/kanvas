# Task 3 — quality rereview round 4

Verdict: **FAIL**.

Reviewed range: `98a280385..59f4899cd`.

## Critical

`C XOR C` rejects when a contour contains multiple identical exact carriers. The full-cover
planner requires one unique counterpart, marks multiple valid counterparts ambiguous, drops the
relation, and then rejects the deferred contact. One primitive succeeds; 2–4 primitives reject in
compact/separate and forward/reversed encodings. Evidence: `PathHybridTopologyF64F32.kt:3091`,
`:3114`, `:3123`, `:4143`.

## Important

1. `maxIntersections` is still tied to flattened endpoint count: a duplicated normal self-closed
   cubic needs `3414`; the n=2 public fixture codifies `216` instead of a canonical group count.
2. Direct/reversed equivalent XOR first succeeds at different candidate budgets (`976041` versus
   `977363`).
3. The full-cover planner performs its carrier×reference×incidence scan to compute work, then
   preflights, then repeats the join. The first quadratic scan is unbounded. Evidence:
   `PathHybridTopologyF64F32.kt:2904-2931`.
4. Physical strict-cut/remap remains without public proof.

## Round-3 closure

Closed for covered fixtures: proxy Point/Overlap propagation, canonical proxy leader and sort,
face locator, local signed-zero authority, raw F32 normalization and removal of the raw XOR bypass.

Open: multi-carrier XOR equivalence, canonical event count, reversed budget equivalence,
preflight-before-work in the full-cover planner, and physical strict-cut proof.

Targeted JVM/JS tests passed and Git checks were clean. No file was modified during review.
