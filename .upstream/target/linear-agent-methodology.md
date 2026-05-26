# Linear, Skills, And Subagent Methodology For The WGSL Pipeline Target

Date: 2026-05-26

This document describes how to execute the validated
`high-performance-wgsl-pipeline-target.md` through Linear milestones, Codex
skills, and scoped subagents. It is an execution method, not a replacement for
the architecture target.

## Source Of Truth

Linear project:

- Project: `Kanvas - WGSL Pipeline Target`
- URL: https://linear.app/forge-yg/project/kanvas-wgsl-pipeline-target-ef9e97757caa
- PM roadmap doc: https://linear.app/forge-yg/document/pm-roadmap-wgsl-pipeline-target-a018961d2083

Use the Linear project, milestones, issues, and roadmap doc as the active
execution source. Do not assume a separate Linear initiative object exists for
this work unless one is created later.

The architecture source of truth remains
`.upstream/target/high-performance-wgsl-pipeline-target.md`.

## Linear Structure

Parent epics:

| Epic | Title |
|---|---|
| GRA-10 | Parser dependency integration |
| GRA-11 | Render Pipeline IR foundation |
| GRA-12 | WGSL validation, reflection and module builder |
| GRA-13 | CPU pipeline execution |
| GRA-14 | GPU generated pipeline |
| GRA-15 | Runtime effects descriptor unification |
| GRA-16 | Performance gates and PM demos |
| GRA-17 | Migration batch and retirement policy |

Milestone tickets:

| Milestone | Issue | PM-visible capability |
|---|---|---|
| M0 Parser deps ready | GRA-18 | Kanvas can resolve and smoke-test WGSL parser/generator deps. |
| M1 Pipeline IR foundation | GRA-19 | A stable `KanvasPipelineIR` skeleton can dump a simple paint pipeline. |
| M2 WGSL validation & reflection | GRA-20 | Existing WGSL resources parse and expose one reflected layout. |
| M3 CPU scalar pilot | GRA-21 | CPU scalar pipeline renders solid rect and linear gradient fixtures. |
| M4 Generated WGSL pilot | GRA-22 | Generated WGSL renders `Rect + SolidColor + SrcOver`. |
| M5 Uniform packer generated/verified | GRA-23 | Kotlin packer layout is checked against WGSL reflection. |
| M6 GPU pipeline key & cache telemetry | GRA-24 | Warm frames expose stable pipeline-key and cache telemetry. |
| M7 BlendPlan & fallback diagnostics | GRA-25 | Blend support is selected through an explicit allowlist and diagnostics. |
| M8 Generated gradient WGSL | GRA-26 | Generated gradient WGSL matches CPU within the threshold policy. |
| M9 Runtime effect descriptor pilot | GRA-27 | One registered runtime effect shares CPU/GPU descriptor metadata. |
| M10 Java 25 Vector pilot | GRA-28 | One Vector API kernel reports scalar baseline and measured speedup. |
| M11 Migration batch 1 | GRA-29 | One shader family leaves handwritten-only execution with retirement rules. |

## Skill Map

Use skills as agent operating procedures:

| Skill | Use |
|---|---|
| `kotlin-2d-3d-pipeline-architect` | General graphics pipeline architecture review. |
| `kanvas-wgsl-pipeline-architect` | Target decisions, milestone slicing, and architecture reviews. |
| `kanvas-linear-ticket-executor` | Default implementation workflow for one Linear ticket. |
| `kanvas-wgsl-parser-integration` | Parser deps, validation, reflection, WGSL IR/generator, uniform packers. |
| `kanvas-cpu-pipeline` | PipelineIR CPU execution, scalar kernels, Java 25 Vector pilots. |
| `kanvas-gpu-generated-wgsl` | Generated WGSL, `PipelineKey`, `BlendPlan`, WebGPU caches, GPU gates. |
| `kanvas-pm-demo` | Milestone demo scripts, evidence summaries, PM-facing updates. |
| `linear:linear` | Read or update Linear issues, projects, milestones, and docs. |
| `github:github` | Read GitHub issues, comments, PRs, and CI context when linked. |

## Agent Roles

### Architect Agent

Owns target interpretation and ticket slicing.

Inputs:

- target architecture doc;
- Linear epic or milestone issue;
- GitHub review comments when present.

Outputs:

- scoped decision;
- acceptance criteria;
- files or modules likely involved;
- dependencies and non-goals;
- recommended validation evidence.

### Implementation Agent

Owns one Linear ticket at a time.

Inputs:

- Linear issue id and milestone;
- parent epic;
- relevant skill;
- architecture target.

Outputs:

- focused patch;
- tests or explicit no-test rationale;
- validation commands and results;
- fallback diagnostics when behavior is intentionally unsupported;
- handoff note for review.

### Verification Agent

Checks implementation without inheriting the implementer's assumptions.

Inputs:

- patch or branch;
- Linear acceptance criteria;
- target doc section.

Outputs:

- findings ordered by severity;
- commands run;
- missing evidence;
- merge/block recommendation.

### Reviewer Agent

Performs code-review stance on risky changes.

Focus:

- behavioral regressions;
- missed target constraints;
- incorrect fallback behavior;
- missing tests;
- performance or cache risks.

### PM Demo Agent

Turns completed technical slices into progress evidence.

Outputs:

- demo script;
- screenshots, dumps, generated source, benchmark table, or issue links;
- short milestone summary;
- next dependency.

## Milestone Workflow

Each milestone should move through the same loop:

1. Architect pass: confirm target section, scope, dependencies, acceptance criteria, and demo artifact.
2. Ready check: issue has parent epic, milestone, module boundary, tests, and known blockers.
3. Implementation pass: one agent works one ticket in a dedicated branch or worktree.
4. Verification pass: a separate agent reviews code, tests, fallbacks, and docs.
5. PM evidence pass: capture the demo artifact and update Linear.
6. Closeout: link commit or PR, note residual risks, and update the next milestone if dependency facts changed.

Use existing Linear workflow statuses semantically:

- backlog/planned: not ready for an agent;
- ready: acceptance criteria and dependencies are clear;
- in progress: exactly one implementation owner;
- review: implementation complete, verification pending;
- done: merged or accepted with evidence attached.

## Definition Of Ready

A ticket is ready for an implementation subagent when it has:

- one parent epic and one milestone;
- a single primary capability;
- explicit non-goals;
- target-doc section references;
- expected fallback behavior;
- tests, benchmarks, or artifact evidence named up front;
- dependency-gated items called out instead of hidden in scope;
- no unresolved design decision that would change the module boundary.

If any of these are missing, the architect agent should first decide whether
the missing information is inferable from the target document, parent epic,
completed previous milestones, or linked GitHub context. If it is inferable,
update the Linear issue or add a Linear comment with the missing non-goals,
dependencies, blockers, and acceptance clarification, then continue execution.

Stop and create a readiness blocker only when:

- a required dependency is not completed;
- an acceptance criterion is contradictory;
- a required external artifact is missing;
- the implementation boundary cannot be inferred safely;
- the missing information changes architecture or product scope.

## Definition Of Done

A ticket is done when:

- the implementation satisfies the issue acceptance criteria;
- tests, benchmarks, or generated artifacts prove the capability;
- fallback reasons are stable and asserted when relevant;
- generated WGSL and reflected layouts are deterministic when involved;
- no archived root plan checkbox is treated as active backlog;
- Linear has a concise evidence comment or linked PR/commit;
- the PM demo artifact exists for milestone tickets.

## Dependency Order

Suggested dependency graph:

| Work | Depends on |
|---|---|
| M0 parser deps | external webgpu-ktypes parser artifacts |
| M1 PipelineIR | target doc only |
| M2 validation/reflection | M0 |
| M3 CPU scalar pilot | M1 |
| M4 generated solid WGSL | M0, M1, initial M2 smoke |
| M5 uniform packers | M2 |
| M6 GPU key/cache telemetry | M4 |
| M7 BlendPlan diagnostics | M1, M4 |
| M8 generated gradient WGSL | M3, M5, M7 |
| M9 runtime effect descriptor | M1, M2, M4 |
| M10 Java 25 Vector pilot | M3 |
| M11 migration batch 1 | M8 or M9, depending on selected shader family |

Parallelize only when agents do not edit the same module boundary. Parser,
PipelineIR, CPU scalar, and PM demo preparation can often overlap; generated
WGSL, packers, cache telemetry, and migration depend on earlier contracts.

## Subagent Prompt Template

Use this template when spawning or briefing an implementation subagent:

```text
Use skills:
- kanvas-linear-ticket-executor
- <area skill>

Repository:
- /Users/chaos/.codex/worktrees/3a5c/kanvas

Issue:
- Linear <GRA-id>: <title>
- Parent epic: <GRA-id>
- Milestone: <M-id>

Must read:
- AGENTS.md
- .upstream/target/high-performance-wgsl-pipeline-target.md
- .upstream/target/linear-agent-methodology.md
- the Linear issue and linked GitHub context

Scope:
- Implement only <capability>.
- Do not work on <explicit non-goals>.
- Preserve hard architecture decisions from AGENTS.md.

Deliverables:
- focused patch;
- tests or explicit no-test rationale;
- validation commands and results;
- fallback diagnostics if unsupported behavior is expected;
- short handoff note naming changed files and residual risks.

Do not:
- port Ganesh or Graphite;
- rebuild SkSL compiler/IR/VM;
- add short-lived substitutes for dependency-gated font/codec work;
- treat archived plans as active backlog;
- push or merge unless explicitly instructed.
```

## Review Prompt Template

Use this template for a verification or review subagent:

```text
Review Linear <GRA-id> against:
- issue acceptance criteria;
- .upstream/target/high-performance-wgsl-pipeline-target.md;
- .upstream/target/linear-agent-methodology.md.

Prioritize:
- bugs and behavioral regressions;
- target constraint violations;
- incorrect fallback or diagnostics behavior;
- missing tests or benchmark evidence;
- cache, lifecycle, or generated-source determinism risks.

Return:
- findings first, ordered by severity, with file/line references;
- commands run;
- open questions;
- residual risk and merge/block recommendation.
```

## Demo Handoff Template

Each milestone demo should leave this evidence in Linear:

```text
Milestone:
Capability:
Evidence:
Commands:
Artifacts:
Known limitations:
Next dependency:
Commit or PR:
```

Keep PM notes concrete. Prefer one screenshot, generated WGSL diff, benchmark
table, or parser/reflection dump over broad implementation narration.

## Operating Cadence

Recommended cadence:

- maintain one active milestone owner;
- run architect review before opening implementation fan-out;
- run verification before PM demo;
- update Linear when a dependency changes, not only at the end;
- avoid more than two active implementation agents touching pipeline core at once;
- keep demo evidence attached to the milestone ticket even when code lands through a PR.

The purpose of subagents is isolation of responsibility: one agent decides
scope, one changes code, one verifies, and one packages evidence. Do not use
multiple agents to create competing implementations of the same ticket unless
the task is explicitly an exploration spike.
