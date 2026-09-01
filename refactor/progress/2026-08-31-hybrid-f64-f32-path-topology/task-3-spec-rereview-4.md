# Task 3 — spec rereview round 4

Verdict: **FAIL**.

Reviewed range: `98a280385..59f4899cd`.

## Critical

1. The full-cover planner grants authority at contour membership level rather than for the exact
   reciprocal carrier/parameter relation. A thin lens plus an unrelated distant self-closed cubic
   rejects under `simplify`, but `C UNION C` and `C INTERSECT C` succeed. The distant primitive
   activates the global planner and absolves an unrelated deferred endpoint contact.
   Evidence: `PathHybridTopologyF64F32.kt:276`, `:4143`.
2. `maxIntersections` still counts adaptive flattening subdivisions as public events. The public
   equal-carrier fixture needs 6 events for n=1 but 216 for n=2/3; the extra 210 are internal
   duplicate-carrier joints. Evidence: `PathIntersectionsF64.kt:535`, `:873` and the test constant
   in `PathOpsHybridTopologyF32Test.kt:72`.

## Important

- The `maxCandidateProbes` first-success frontier differs for direct/reversed equivalent XOR:
  `976041` versus `977363`.
- Physical strict cuts and overlapping-claim rejection remain without a public behavioral proof.

## Round-3 closure

Functionally closed: equal-carrier external cuts for covered n=1..3 cases; nested collapsed loop
with face/hole/inverse/boundary; significant siblings; `EVEN_ODD`; removal of structural XOR
shortcut; no-face XOR results for covered cases; local signed-zero provenance; raw F32 bounds on
JVM/JS; sort/dispatch debit.

Open: exact full-cover authority, canonical event counting, reversed budget equivalence and the
physical strict-cut public proof.

Fresh full JVM/JS verification passed 61 tasks; Git status/diff-check were clean. Scope and public
test constraints were respected.
