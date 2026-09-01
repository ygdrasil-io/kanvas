# Conservative hybrid topology admission — quality review

Verdict: PASS

Reviewed range: `790bb74dccef66f00c8fc2f97e53c9ec23e4a109..7f66bc5724f1f1fd70dd48b86f4e2648108d682c`

Reviewer: independent read-only Sol agent `/root/conservative_admission_quality_review`.

## Verified commands

- Targeted hybrid JVM tests — `BUILD SUCCESSFUL`, 20 tasks.
- Targeted hybrid JS tests — `BUILD SUCCESSFUL`, 53 tasks.
- The initial quality pass also ran the full JVM/JS suites — `BUILD SUCCESSFUL`, 61 tasks.
- Final-range `rtk git diff --check 790bb74dc..7f66bc572` — clean.
- Legacy caller search — the six legacy functions are private definitions only with no caller.
- Public-test search — no internal admission type or private infrastructure contract is referenced.
- Final `rtk git status --short` — only the two parent-owned review documents were untracked.

## Closure of the previous Important findings

- The checked `6N` projected-edge cost is consumed before the unique `map`, so both traversal and mapped-list allocation occur only after budget admission. The AABB walker continues to debit only its actual visits/candidates; the `6N` view cost is not duplicated.
- The checked `5P` post-plan guard cost is consumed before scanning accepted proposals. Each proposal evaluates four nullable-identity comparisons into separate booleans, every proposal is visited, and rejection occurs only after the complete scan.
- The `5P` guard debit is distinct from the non-consuming transaction upper-bound check, the transaction’s own debits, and the factory’s `P` immutable-copy debit.

## Strengths

- Source rejection precedes flattening/proxy planning; projection admission and its final guard precede coincidences, aliases, cuts, and DCEL.
- The accepted-plan constructor remains private and consumes only transaction-validated relations.
- `maxIntersections` remains source-only after admission, including the public endpoint-only 8/9 boundary.
- Deferred/full-cover/strict-cut/collapsed/materialization legacy routes remain disconnected from the public call graph.
- Tests are public and behavioral, and JVM/JS, operand-order, signed-zero, exact open-overlap, and immutability coverage remain intact.
- Scope is limited to `:math:geometry` and `refactor`; no font, codec, GM, render, dashboard, score, or exclusion changed.

## Findings

### Critical

None.

### Important

None.

### Minor

None.

## Residual risk

No independent public fixture reaches a physical strict-interior projected cut. This gap is explicitly documented; the path remains fail-closed and no internal infrastructure test was added.
