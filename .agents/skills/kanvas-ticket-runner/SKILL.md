---
name: kanvas-ticket-runner
description: Execute Kanvas repo-native tickets and ticket waves from spec/report matrices such as pure-kotlin-text PKT slices. Use when Codex needs to take, continue, review, close out, or document Kanvas tickets, evidence checkpoints, support claims, remaining gates, validation results, or independent review outcomes without using Linear.
---

# Kanvas Ticket Runner

Use this skill to execute Kanvas tickets through the repo-native evidence
workflow. The model comes from `.upstream/specs/pure-kotlin-text/` and
`reports/pure-kotlin-text/coverage-ticket-matrix.md`, and generalizes to future
Kanvas matrices under `reports/**`.

## Required Context

Before planning or editing, read:

- `AGENTS.md` and any referenced project instructions;
- recent Git history for the relevant specs and reports;
- the active coordination matrix under `reports/**`;
- the specs cited by the selected ticket under `.upstream/specs/**`;
- target docs under `.upstream/target/**` only when the ticket domain requires
  them.

For current pure Kotlin text work, start with:

- `.upstream/specs/pure-kotlin-text/README.md`;
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`;
- `reports/pure-kotlin-text/boundary-contracts.json` when boundary contracts
  or import rules are involved;
- `reports/pure-kotlin-text/fixture-evidence-manifest.json` when fixture,
  validation, support-claim, or dashboard classification evidence is involved.

Do not use archived migration plans, old closed milestone labels, or deleted
local skills as active backlog.

## Ticket Model

Treat the coordination matrix as the ticket source of truth.

- A vertical ticket such as `PKT-12` names a capability area.
- A slice such as `PKT-12A` or `PKT-12B` is the implementation unit.
- A matrix row should identify classification, scope, probable write set, and
  ready evidence.
- A completed slice must add or update a checkpoint evidence section in the
  coordination report.

If the user asks for a broad vertical ticket or a wave, first choose narrow
independent slices. Default to at most three slices per wave unless the user
sets a different limit.

## Readiness Check

Before editing, confirm:

1. The ticket or slice is named clearly.
2. The active matrix is the latest relevant version in Git history.
3. The owning specs and report sections have been read.
4. The classification allows the requested work now.
5. Dependency-gated work is not hidden inside the slice.
6. The probable write set is narrow enough for one implementation owner.
7. Ready evidence names concrete tests, dumps, manifests, reports, or
   diagnostics.
8. Remaining gates and non-claims are understood before implementation starts.

If readiness is missing but inferable from the specs and reports, document the
inferred slice in the coordination report. If readiness changes architecture or
support scope, stop and ask the user for a decision.

## Execution Workflow

For one slice:

1. Restate the slice goal in one sentence.
2. Check `git status --short` and preserve unrelated changes.
3. Read the owning code and tests for the probable write set.
4. Make the smallest patch that satisfies the slice.
5. Add focused tests, deterministic dumps, manifests, or report updates that
   prove the ready evidence.
6. Preserve stable refusal diagnostics and explicit non-claims.
7. Run the narrow validation command first, then broader validation if shared
   contracts changed.
8. Run `rtk git diff --check`.
9. Update the coordination report checkpoint.
10. Request or perform independent review when behavior, specs, support
    claims, gates, diagnostics, or coordination evidence changed.

For a wave:

1. Select independent slices by dependency order and non-overlapping write
   sets.
2. Use subagents for independent implementation slices when available.
3. Give each subagent one slice, exact specs/reports to read, expected
   evidence, non-claims, and validation commands.
4. Do not let subagents invent new tickets or broaden support claims.
5. Audit each returned patch and evidence before consolidation.
6. Run final validation and update each slice checkpoint separately.

## Evidence And Support Claims

Never turn partial evidence into a complete support claim.

Label partial evidence precisely:

- architecture-only;
- contract-only;
- planning-only;
- telemetry-only;
- coordination evidence;
- dependency-gated.

For pure Kotlin text support claims, require the relevant spec gates, which may
include fixture provenance, semantic dumps, CPU oracle evidence, GPU evidence
when a GPU route is claimed, stable route diagnostics, refusal diagnostics,
and validation commands.

Every closeout must state remaining gates. If no remaining gate exists, name
the evidence that removed it.

External engines such as HarfBuzz, FreeType, Fontations, native platform
shapers, browser layout, or Skia native output may be drift-report inputs only
when the active spec allows it. They are not normative pass/fail oracles.

## Checkpoint Template

Use this structure in the coordination report:

````markdown
### <SLICE-ID>: <Short Title>

Status: implemented and independently reviewed.

Files:

- `<path>`

Evidence:

- <deterministic evidence item>
- <diagnostic, dump, manifest, or behavior proof>

Validation:

```bash
rtk <focused validation command>
rtk git diff --check
```

Remaining gate: <precise non-claim or gate that remains>.
````

If independent review was not required, replace the status with the exact
state and include the reason review was not required.

## Hard Rules

- Do not use Linear as the active Kanvas ticket workflow.
- Do not restore the deleted local agile skills.
- Do not widen a slice to adjacent capability areas.
- Do not clear archived backlog rows.
- Do not port Ganesh or Graphite.
- Do not rebuild Skia's SkSL compiler, IR, or VM.
- Keep WGSL as the shader implementation target where rendering shaders are
  involved.
- Keep pure Kotlin text normative behavior free of HarfBuzz, FreeType,
  Fontations, AWT, JNI, platform shapers, or native font APIs.
- Do not claim GPU support without GPU evidence.
- Do not claim performance readiness from correctness evidence alone.
- Preserve unrelated local changes.
