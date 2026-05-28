---
name: kanvas-needs-architect
description: Turn product needs into actionable Linear epics and tickets with acceptance criteria, non-goals, target-doc sections, and dependency gates for the Kanvas WGSL pipeline.
license: MIT
compatibility: opencode
metadata:
  audience: architect
  workflow: planning
---

## Required Context

Read before writing tickets:

- `AGENTS.md` — hard architecture decisions
- `.upstream/target/high-performance-wgsl-pipeline-target.md`
- `.upstream/target/linear-agent-methodology.md`
- Relevant spec in `.upstream/specs/wgsl-pipeline/README.md` (M0-M11) or `.upstream/specs/geometry-coverage/README.md` (M12+)
- The target doc section matching the need
- Existing epic or Linear project context

## Workflow

1. Restate the product need as a **User Story**: `En tant que <rôle>, je veux <capabilité> afin de <bénéfice>`
2. Identify the target doc section it maps to
3. Define **Acceptance Criteria** — observable, testable, one sentence each
4. Define **Non-goals** — explicitly what this ticket will not do
5. Define **Dependencies** — blocked-by and blocks, with gate criteria
6. Define **Expected evidence** — test output, benchmark, golden diff, dump, screenshot
7. Define **Fallback behavior** — what happens when a path is intentionally unsupported
8. Write or update the Linear ticket with all fields filled

## Output Contract

A ticket is well-formed when it has:

- parent epic
- milestone assignment (M0-M45)
- one primary capability
- acceptance criteria (3-5 bullets)
- non-goals (2-3 bullets)
- dependency gates named
- fallback behavior stated
- evidence type named
- no unresolved design decisions

## Validation

Ask: "If an implementation agent picked this ticket up with zero context, would they know exactly what to build and what to leave alone?"

If no, add the missing context before considering the ticket ready.
