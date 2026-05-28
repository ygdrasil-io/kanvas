---
name: kanvas-sprint-retro
description: Collect sprint metrics from Linear (velocity, lead time, blockers), generate a structured sprint report, and post a retrospective with actionable improvements for the Kanvas project.
license: MIT
compatibility: opencode
metadata:
  audience: pm
  workflow: reporting
---

## Required Context

- Linear project "Kanvas — WGSL Pipeline Target"
- The sprint cycle to review
- Previous sprint report in `reports/wgsl-pipeline/` for comparison

## Metrics to Collect

Query Linear for all tickets closed in the cycle:

- **Velocity** — number of tickets completed + sum of story points
- **Lead time** — days from `Ready` → `Done` per ticket (median)
- **Cycle split** — by milestone (M0, M1, etc.)
- **Blockers** — tickets that were blocked, with blocker reason
- **Review ratio** — tickets that passed review first time vs needed changes
- **Carry-over** — tickets planned but not completed

## Report Structure

Save as `reports/wgsl-pipeline/<date>-<cycle-name>-sprint-report.md`:

```markdown
# Sprint <cycle-name> — <date>

## Summary
- Tickets completed: X
- Story points: Y
- Velocity trend: ↑↓→ vs previous sprint

## By Milestone
| Milestone | Tickets | Status | Notes |
|-----------|---------|--------|-------|

## Blockers
- <issue link> — <reason> — <resolution or still open>

## Retrospective
### 👍 What went well
### 👎 What to improve
### 🔧 Action items

## Next Sprint
- Recommended focus:
- Known dependencies:
```

## Post to Linear

Add the report as a comment on the cycle or project, tagging relevant milestones and team members.
