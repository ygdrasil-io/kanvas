# Kanvas Ticket Runner Skill Design

Date: 2026-06-14
Status: Approved design

## Purpose

Create a repo-local Codex skill named `kanvas-ticket-runner` under
`.agents/skills/kanvas-ticket-runner/`.

The skill is the single entry point for executing Kanvas tickets using the
repo-native evidence workflow established by `.upstream/specs/pure-kotlin-text/`
and `reports/pure-kotlin-text/coverage-ticket-matrix.md`.

The skill replaces the older local multi-skill workflow. Linear is not part of
the active ticket process and must not appear as a required workflow step.

## Trigger Scope

Use the skill when the user asks Codex to take, execute, continue, review,
close out, or document a Kanvas ticket or ticket slice.

Expected trigger language includes:

- Kanvas ticket
- PKT ticket or PKT slice
- evidence matrix
- checkpoint evidence
- support claim
- remaining gate
- ticket closeout
- spec-driven implementation

The initial concrete model is `PKT-*` from `pure-kotlin-text`, but the skill
must generalize to future Kanvas ticket matrices under `reports/**`.

## Source Of Truth

The skill must first discover the current repo-native ticket source:

1. Check recent Git history for the relevant specs and reports.
2. Read the active coordination matrix under `reports/**`.
3. Read the specs cited by that matrix under `.upstream/specs/**`.
4. Read target docs under `.upstream/target/**` only when the ticket domain
   requires them.
5. Treat archived migration plans and closed milestone labels as historical
   evidence only.

For the current model, the core references are:

- `.upstream/specs/pure-kotlin-text/README.md`
- `reports/pure-kotlin-text/coverage-ticket-matrix.md`
- `reports/pure-kotlin-text/boundary-contracts.json`
- `reports/pure-kotlin-text/fixture-evidence-manifest.json`

## Ticket Model

The skill must describe and enforce this repo-native ticket structure:

- A vertical ticket, such as `PKT-12`, names a capability area.
- A slice, such as `PKT-12A` or `PKT-12B`, is the unit an implementation agent
  should execute.
- A ticket matrix row includes `Classification`, `Scope`,
  `Probable Write Set`, and `Ready Evidence`.
- A completed slice gets a checkpoint section in the coordination report.

The checkpoint section must include:

- `Status`
- `Files`
- `Evidence`
- `Validation`
- `Remaining gate`
- independent review verdict when review was required

## Execution Workflow

The skill should instruct Codex to:

1. Identify the requested ticket or slice.
2. Verify the latest matrix and related specs through Git history.
3. Run a readiness check against classification, scope, write set, ready
   evidence, dependencies, and known gates.
4. Split broad vertical tickets into a narrow `A/B/C` slice before editing.
5. Implement only the selected slice.
6. Preserve stable refusal diagnostics and explicit non-claims.
7. Add focused tests, deterministic dumps, manifests, or reports required by
   the ticket evidence.
8. Update the coordination report with a checkpoint evidence section.
9. Run focused validation and `rtk git diff --check`.
10. Request or perform independent review for behavior, specs, support claims,
    or gate changes.

## Evidence Rules

The skill must prevent hidden support claims.

Partial evidence must be labeled precisely, for example:

- architecture-only
- contract-only
- planning-only
- telemetry-only
- coordination evidence
- dependency-gated

A support claim is valid only when the relevant spec gates are satisfied. For
the `pure-kotlin-text` model, this can include fixture provenance, semantic
dumps, CPU oracle evidence, GPU evidence when a GPU route is claimed, stable
route diagnostics, refusal diagnostics, and validation commands.

Every closeout must state remaining gates. If no remaining gate exists, the
checkpoint must explain which evidence removed it.

## Skill Structure

Implementation should create only:

```text
.agents/skills/kanvas-ticket-runner/
└── SKILL.md
```

No scripts are required for the first version. A generic matrix validator can
be added later after more Kanvas domains adopt the same report format.

`SKILL.md` should include:

- frontmatter with only `name` and `description`;
- required context instructions;
- ticket model instructions;
- execution workflow;
- evidence and support-claim rules;
- checkpoint template;
- validation and review expectations.

## Validation

Minimum validation for implementing the skill:

```bash
rtk git diff --check
```

If the system skill-creator scripts are used to initialize or validate the
skill, also run `quick_validate.py` against
`.agents/skills/kanvas-ticket-runner/`.

## Non-Goals

- Do not reintroduce Linear as the active ticket workflow.
- Do not recreate the deleted local skill set as multiple skills.
- Do not add generic validation scripts in the first implementation.
- Do not make `pure-kotlin-text` the only supported Kanvas domain; use it as
  the model for the project-wide methodology.
