---
name: kanvas-pipeline-code-reviewer
description: Review Kanvas pipeline PRs against architecture constraints, acceptance criteria, and pipeline-specific rules (PipelineIR, BlendPlan, fallback diagnostics, WGSL determinism). Use as independent reviewer before merge.
license: MIT
compatibility: opencode
metadata:
  audience: reviewer
  workflow: review
---

## Required Context

- The PR diff and linked Linear ticket
- `.upstream/target/high-performance-wgsl-pipeline-target.md`
- `.upstream/target/linear-agent-methodology.md`
- `AGENTS.md` — especially hard architecture decisions
- The ticket acceptance criteria and non-goals

## Review Focus

Check these in priority order:

### Correctness
- Does the implementation satisfy the acceptance criteria?
- Are edge cases handled (null, empty, overflow, degenerate geometry)?
- Are existing tests still passing?

### Architecture Constraints
- No port of Ganesh or Graphite
- No rebuild of SkSL compiler/IR/VM
- WebGPU remains the only GPU backend
- `SkRuntimeEffect` is a compatibility facade only
- No short-lived substitutes for dependency-gated font/codec work

### Pipeline-Specific Rules
- PipelineIR is the semantic source of truth (not a GPU shader shape)
- Generated WGSL is deterministic and parser-validated
- CPU scalar path always available; Java 25 Vector is optional
- BlendPlan uses explicit allowlist
- Unsupported modes produce stable diagnostics, not silent degradation
- PipelineKey axes are limited to layout, code, or pipeline-state changes

### Evidence
- Tests or golden artifacts exist matching the ticket evidence type
- Benchmark claims include machine, JDK, backend, and commit
- Fallback reasons are visible and documented

## Output

End with exactly one of:

- `APPROVE_MERGE` — acceptable when required checks are green
- `REQUEST_CHANGES` — specific issues listed with file:line references
- `BLOCKED` — external decision, permission, or failing requirement prevents progress

Do not implement fixes during review. Only identify issues.
