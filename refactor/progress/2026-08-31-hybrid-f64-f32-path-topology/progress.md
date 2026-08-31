# Hybrid F64/F32 path topology progress

## Task 1 — Preserve source spans

Completed 2026-08-31. Source segment/parameter provenance now traverses flattening and split edges; the transitional source-topology model and legacy adapter are present.  Projection no longer applies unsafe late compaction to synthetic F64 contours with multiple potential witnesses, and permitted collapse is represented by `Drop`.

Fix round 1: production now constructs the source topology before the legacy arrangement, retains coincident source locations/seams, and carries exact-cut boundaries through source spans.

Fix round 2: removed the legacy raw-split side channel; the transitional adapter emits all legacy edges from authoritative spans and flattened sections.

The Task 1 regression suite and focused JVM/complete JS verification passed before commit. See `task-1-report.md` for commands and evidence.
