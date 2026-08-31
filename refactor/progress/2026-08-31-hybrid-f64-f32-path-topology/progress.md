# Hybrid F64/F32 path topology progress

## Task 1 — Preserve source spans

Completed 2026-08-31. Source segment/parameter provenance now traverses flattening and split edges; the transitional source-topology model and legacy adapter are present.  Projection no longer applies unsafe late compaction to synthetic F64 contours with multiple potential witnesses, and permitted collapse is represented by `Drop`.

The Task 1 regression suite and focused JVM/complete JS verification passed before commit. See `task-1-report.md` for commands and evidence.
