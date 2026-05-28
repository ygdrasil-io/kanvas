---
name: kanvas-ticket-executor
description: Execute one Kanvas Linear ticket end-to-end — gather context from Linear and target docs, apply a scoped patch, run validation, commit, and report evidence.
license: MIT
compatibility: opencode
metadata:
  audience: developer
  workflow: implementation
---

## Required Context

Read before editing code:

- `AGENTS.md`
- `CLAUDE.md`
- `.upstream/target/high-performance-wgsl-pipeline-target.md`
- `.upstream/target/linear-agent-methodology.md`
- Relevant spec `.upstream/specs/wgsl-pipeline/README.md` or `.upstream/specs/geometry-coverage/README.md`
- The Linear issue, parent epic, and milestone
- Any linked GitHub issues or PRs

## Startup Checklist

1. Restate the ticket goal in one sentence
2. Identify files and modules owning the behavior
3. Check `git status --short` for unrelated changes
4. Confirm the ticket has Definition of Ready (scope, epic, milestone, acceptance criteria)

## Execution Workflow

1. Make the smallest patch satisfying the acceptance criteria
2. Add focused tests, golden dumps, or fixtures matching the ticket risk
3. Run the narrowest useful validation first, then broader checks when shared contracts changed
4. Commit with a message referencing the Linear ticket ID

## Scope Rules

- Do not widen the ticket to adjacent milestones
- Do not clear archived backlog rows
- Do not port Ganesh or Graphite
- Do not rebuild SkSL compiler/IR/VM
- Do not add short-lived substitutes for dependency-gated font/codec work
- Preserve unrelated local changes

## Done Criteria

A ticket is ready for review when it has:

- code scoped to the Linear issue
- tests or explicit no-test rationale
- stable diagnostics for unsupported paths
- no unexpected fallback behavior
- concise note naming validation commands and result
- remaining risks called out

## Post-Implementation

Push branch, open PR linked to Linear ticket, then load `kanvas-pipeline-code-reviewer` for independent review.
