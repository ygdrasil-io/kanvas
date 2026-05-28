---
name: kanvas-agile-guide
description: Guide explaining the full Kanvas agile process — what each skill does, when to use it, and how the workflow stages connect from need to milestone demo.
license: MIT
compatibility: opencode
metadata:
  audience: all
  workflow: guide
---

## Overview

This skill describes the agile product evolution process for Kanvas. The process is implemented as a chain of opencode skills under `.agents/skills/`. Each skill handles one stage; the orchestrator ties them together.

```
Need → kanvas-needs-architect → kanvas-sprint-planner
         → kanvas-ticket-executor → kanvas-pipeline-code-reviewer
         → kanvas-sprint-retro → kanvas-pm-demo
```

## When To Use What

| You want to… | Load this skill | Mode |
|-------------|----------------|------|
| Turn an idea into a proper Linear ticket | `kanvas-needs-architect` | manual |
| Plan the next sprint | `kanvas-sprint-planner` | manual |
| Implement a ticket | `kanvas-ticket-executor` | manual |
| Review a pipeline PR | `kanvas-pipeline-code-reviewer` | manual |
| Close a sprint | `kanvas-sprint-retro` | manual |
| Show milestone progress | `kanvas-pm-demo` | manual |
| Run everything automatically | `kanvas-agile-orchestrator` | full/light/report |
| Create a standardized PR | `gh-pr-creator` (global) | manual |
| Understand the process | `kanvas-agile-guide` (this) | — |

## Agile Stages In Detail

### 1. Need → Ticket (`kanvas-needs-architect`)

When to load: a feature request, bug report, or architecture note exists but lacks structured requirements.

Load `kanvas-needs-architect`. It reads the target docs and produces:

- User story
- Acceptance criteria
- Non-goals
- Dependencies
- Evidence type
- Linear ticket created/updated

**Gate**: the ticket is ready to schedule when it has a parent epic, milestone, and Definition of Ready.

### 2. Ticket → Sprint Backlog (`kanvas-sprint-planner`)

When to load: before starting a sprint, or when the backlog needs ordering.

Load `kanvas-sprint-planner`. It:

- Validates the dependency graph (M0→M11 order)
- Assigns tickets to a Linear cycle
- Produces a sprint backlog with estimates

**Gate**: move to execution only when tickets in the sprint have Definition of Ready.

### 3. Backlog → Implementation (`kanvas-ticket-executor`)

When to load: a ticket is Ready, assigned, and needs code.

Load `kanvas-ticket-executor`. It:

- Reads the ticket, target docs, and specs
- Makes the smallest scoped patch
- Runs validation
- Pushes a branch and opens a PR

**Gate**: PR must be opened before moving to review.

### 4. PR → Review (`kanvas-pipeline-code-reviewer`)

When to load: a pipeline PR is open and needs independent review.

Load `kanvas-pipeline-code-reviewer`. It checks:

- Architecture constraints (no Ganesh/Graphite, WebGPU only, etc.)
- Pipeline rules (PipelineIR, BlendPlan, fallback diagnostics)
- Evidence quality
- Outputs: `APPROVE_MERGE`, `REQUEST_CHANGES`, or `BLOCKED`

**Gate**: merge only when `APPROVE_MERGE` and CI is green.

### 5. Sprint End → Retrospective (`kanvas-sprint-retro`)

When to load: at the end of a sprint cycle.

Load `kanvas-sprint-retro`. It:

- Queries Linear for completed tickets
- Calculates velocity, lead time, blockers
- Writes a structured report to `reports/wgsl-pipeline/`
- Posts retrospective with 👍/👎/🔧

**Gate**: retro marks the sprint complete.

### 6. Milestone Done → Demo (`kanvas-pm-demo`)

When to load: a milestone ticket is Done and needs PM evidence.

Load `kanvas-pm-demo`. It:

- Captures evidence: screenshots, benchmark tables, dumps, diffs
- Posts a structured comment on the Linear milestone ticket
- Names the next dependency

**Gate**: demo evidence is the final step for each milestone.

### 0. Everything At Once (`kanvas-agile-orchestrator`)

When to load: you want the whole cycle automated.

Load `kanvas-agile-orchestrator` with one of three modes:

- `full` — planning → execution loop → review → retro → demo
- `light` — just the next available ticket
- `report` — metrics and demo only

## Quick Reference

```
/g               opencode chat
skill load       load a skill by name
skill list       see available skills

# Manual flow
skill load kanvas-needs-architect
skill load kanvas-sprint-planner
skill load kanvas-ticket-executor
skill load kanvas-pipeline-code-reviewer
skill load kanvas-sprint-retro
skill load kanvas-pm-demo

# Automated flow
skill load kanvas-agile-orchestrator
# then specify mode: full / light / report

# PR creation
skill load gh-pr-creator
```
