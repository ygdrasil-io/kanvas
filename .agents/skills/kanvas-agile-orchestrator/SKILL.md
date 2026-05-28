---
name: kanvas-agile-orchestrator
description: Orchestrate the full Kanvas agile cycle — plan sprint, execute tickets, run code review, collect sprint metrics, prepare milestone demos. Top-level entry point for automated agile product evolution.
license: MIT
compatibility: opencode
metadata:
  audience: orchestrator
  workflow: automation
---

## Required Context

- Linear project "Kanvas — WGSL Pipeline Target"
- `.upstream/target/linear-agent-methodology.md`
- `.upstream/target/high-performance-wgsl-pipeline-target.md`
- `AGENTS.md`

## Modes

### Full Cycle — `mode: full`

Runs the complete agile loop in sequence:

1. **Planning** — Load `kanvas-sprint-planner` to build the sprint backlog
2. **Triage** — Load `kanvas-needs-architect` for any epic or unstructured need found in the backlog
3. **Execution loop** — For each ticket in the sprint backlog (max 1-2 active at a time):
   a. Load `kanvas-ticket-executor` — implement the ticket
   b. Load `kanvas-pipeline-code-reviewer` — review the PR
   c. If `REQUEST_CHANGES`, fix and loop back to review
   d. If `BLOCKED`, stop and report
   e. Merge when `APPROVE_MERGE` and CI green
4. **Retro** — When the sprint cycle ends, load `kanvas-sprint-retro`
5. **Demo** — For each completed milestone, load `kanvas-pm-demo`

### Light — `mode: light`

Process just the next available ticket (equivalent to the task-prompt.md workflow):

1. Find the next non-closed milestone/ticket in M0→M11 order
2. Check Definition of Ready
3. Execute (in sequence per ticket): `kanvas-ticket-executor` → `kanvas-pipeline-code-reviewer`
4. Merge if review passes and CI is green
5. Post evidence — load `kanvas-pm-demo` if milestone completed

### Report — `mode: report`

Collect sprint metrics only, no implementation:

1. Load `kanvas-sprint-retro` for the current or last cycle
2. Load `kanvas-pm-demo` for any milestones completed since last report

## Transition Gates

| Step | Required gate |
|------|--------------|
| Planning → Execution | Each ticket has Definition of Ready |
| Implementation → Review | PR open, CI green (or running) |
| Review → Merge | `APPROVE_MERGE` verdict |
| Merge → Demo | Ticket Done in Linear, PR merged |
| Demo → Next cycle | Retro posted, demo evidence attached |

## Status Reporting

After each step, post a brief status comment on the Linear issue or project:

- What was done
- What blocked or is at risk
- Next step and estimated effort
