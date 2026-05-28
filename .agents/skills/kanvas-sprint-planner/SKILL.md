---
name: kanvas-sprint-planner
description: Slice Linear epics into milestone-ordered tickets, assign sprint cycles, validate dependency order, estimate effort, and produce a sprint backlog for the Kanvas project.
license: MIT
compatibility: opencode
metadata:
  audience: architect
  workflow: planning
---

## Required Context

- `.upstream/target/linear-agent-methodology.md`
- Linear project "Kanvas — WGSL Pipeline Target"
- Current sprint cycle or next available cycle
- Backlog of epics and unassigned tickets

## Workflow

1. **Survey** — Query Linear for all backlog/planned tickets in the project, ordered by milestone (M0→M45)
2. **Validate dependencies** — Check the dependency graph:
   - M0 → external webgpu-ktypes artifacts
   - M1 → target doc only
   - M2 → M0
   - M3 → M1
   - M4 → M0, M1, initial M2 smoke
   - M5 → M2
   - M6 → M4
   - M7 → M1, M4
   - M8 → M3, M5, M7
   - M9 → M1, M2, M4
   - M10 → M3
   - M11 → M8 or M9
3. **Check readiness** — Each ticket must have: parent epic, milestone, acceptance criteria, non-goals, dependencies
4. **Assign cycle** — Move tickets to the current or next Linear cycle
5. **Estimate** — Assign t-shirt size (S/M/L/XL) or story points (1/2/3/5/8) based on complexity
6. **Produce sprint backlog** — Comment on the Linear project or post a document listing:
   - Cycle name and dates
   - Tickets assigned with milestone, estimate, dependencies
   - Known risks and blocked items
   - Capacity note

## Selection Rules

- Process milestones in M0→M45 order
- Only pick tickets with Definition of Ready satisfied
- If a dependency is incomplete, mark the ticket as blocked, not ready
- Max 1-2 tickets touching pipeline core simultaneously
- PM demo tickets can run in parallel with core work

## Output

Post a sprint backlog comment in Linear with the full plan.
