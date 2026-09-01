# Task 3 — spec rereview round 2

Verdict: **FAIL**.

Reviewed range: `80ac9f78c..f1d3772d4`.

## Critical

1. `SelfClosedSourceSegmentF64F32` proves exact source provenance and `[0,1]` coverage, but does
   not prove that the projected rails actually collapsed. A normal one-segment closed cubic such
   as `moveTo(1,1).cubicTo(2,1,1,2,1,1).close()` is therefore routed through the collapsed ledger
   and rejected even though it has a representable F32 loop and contains `(1.2,1.2)`.
   Evidence: `PathArrangementF64F32.kt:1723-1804`, `:1993-2007`, `:2193-2203`.

2. The round-1 no-face/XOR finding remains open. `classifyCollapsedSectorsWithoutFacesF64F32`
   rejects before composition when candidates are not all `AllCarrierSectionsF32`, and the new
   public test codifies `C XOR C` as a rejection instead of the exact empty result.
   Evidence: `PathArrangementF64F32.kt:2079-2084`, `:2142-2155` and
   `PathOpsHybridTopologyF32Test.kt:503-509`.

## Important

- Public coverage still does not force the physical strict-interior cut branch
  (`endpointIdentityF64 == null`) followed by n-way propagation and contiguous-list remapping.
  Current strict fixture proves one canonical external endpoint; staggered fixtures use exact
  source overlaps.
- `collapsedGroupBoundaryDispositionF64F32` scans the contour group repeatedly without receiving
  the budget; the no-face caller debits only a constant eight units.

## Round-1 findings

- Canonical endpoint/n-way event count: closed functionally; public `9/10` boundary passes.
- `maxIntersections` before staging allocation: partial; the gate is after proposal/group graph
  allocation, though before final carriers.
- N-way propagation/list remap: implemented, durable public proof still missing.
- F32 identity/order authority: closed; F32 is validation/embedding.
- Mandatory collapsed sibling INTERSECT repro: closed, but the broader collapsed semantics above
  remain incorrect.
- Partial-collapse real neighbours, post-alias `maxVertices`, and total transaction comparator:
  closed.
- Local budgeting: quadratic exact-area debit closed; new linear group scans remain open.

Fresh JVM+JS suite passed (61 tasks), `git diff --check` passed, and the worktree was clean during
review. No font, codec, GM, exclusion, or non-math geometry change was found.
