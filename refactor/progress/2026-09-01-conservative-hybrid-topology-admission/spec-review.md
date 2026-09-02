# Conservative hybrid topology admission — spec review

Verdict: PASS

Reviewed range: `790bb74dccef66f00c8fc2f97e53c9ec23e4a109..7f66bc5724f1f1fd70dd48b86f4e2648108d682c`

Reviewer: independent read-only Sol agent `/root/conservative_admission_spec_review`.

## Verified commands

- Targeted hybrid JVM tests:
  `rtk ./gradlew :math:geometry:jvmTest --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks`
  — `BUILD SUCCESSFUL`, 20 tasks.
- Targeted hybrid JS tests:
  `rtk ./gradlew :math:geometry:jsNodeTest --tests '*PathOpsHybridTopologyF32Test*' --rerun-tasks`
  — `BUILD SUCCESSFUL`, 53 tasks.
- The previous review also ran the full JVM/JS suites, public forward/swapped endpoint-only and exact open-overlap probes, `git diff --check`, and a clean-status check.
- Final-range `rtk git diff --check 790bb74dc..7f66bc572` — clean.
- Final-range `rtk git status --short` — only the two controller-owned review documents were untracked.

## Closure of the previous Important findings

- `zipWithNext()` adjacency materialization was replaced by a complete indexed scan with no eager pair-list allocation.
- The deferred buffer capacity is converted with checked arithmetic and preflighted before `ArrayList` allocation; the later immutable copy remains separately preflighted without double debit.
- The public endpoint-only success is now covered under operand swap with equivalent output and immutable inputs.
- The unowned rejection is covered under both operand orders, and the requested collapsed/intersect/thin-lens rejection branches retain public input snapshots.
- The implementation report now states the preflight and public coverage precisely.
- Final correction: the checked `6N` projected-edge cost is debited before the mapped-list allocation, and the distinct checked `5P` post-plan guard scans all four identities for every proposal before deciding rejection.
- The final guard remains before `maxIntersections` and before coincidence/alias construction; transaction and factory-copy debits remain separate.

## Findings

### Critical

None.

### Important

None.

### Minor

None.

## Invariant checklist

- Source gate placement, complete bounded scan, forward/reverse exact keying, nondegenerate and signed-zero behavior: PASS.
- Ordinary open exact overlaps remain supported: PASS.
- Immutable projection observation and rejection before aliases/cuts/DCEL: PASS.
- Admission is limited to exact source and endpoint-only local proofs with existing identities: PASS.
- Deferred, strict-interior, collapsed, operand-local, unowned, full-cover, and unproved equivalence states reject fail-closed: PASS.
- No accepted plan exists before complete transaction validation: PASS.
- Checked-I64 preflight, no publication before admission, and candidate → projection → source-only `maxIntersections` → structural-limit precedence: PASS.
- Endpoint-only 8/9 boundary and public behavior matrix: PASS.
- Legacy routes remain unreachable from `op`, `simplify`, and `asWinding`: PASS.
- Only public behavioral/geometric/numeric tests were added; module, nomenclature, font/codec, and GM constraints: PASS.
