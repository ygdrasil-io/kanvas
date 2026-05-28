---
name: kanvas-pm-demo
description: Prepare Kanvas milestone demo evidence — screenshots, benchmark tables, generated WGSL diffs, parser reflection dumps, and Linear evidence comments.
license: MIT
compatibility: opencode
metadata:
  audience: pm
  workflow: reporting
---

## Required Context

- Linear milestone ticket and PR
- Validation commands and output
- Artifacts: screenshots, dumps, benchmark logs, golden files

## Demo Structure

Each milestone demo must include:

- milestone and Linear issue IDs
- user-visible capability in one sentence
- before/after or baseline/current comparison
- exact command or artifact used as evidence
- known limitations and next milestone dependency
- link to PR, commit, or branch

## Milestone Evidence Examples

| Milestone | Evidence Type |
|-----------|--------------|
| M0 | Parser smoke task output: minimal WGSL + one existing shader |
| M1 | `KanvasPipelineIR` dump of solid-rect pipeline |
| M2 | All WGSL resources parse + one uniform layout reflected |
| M3 | CPU scalar renders solid rect + linear gradient fixtures |
| M4 | Generated WGSL renders `Rect + SolidColor + SrcOver` |
| M5 | Packer-vs-reflection test catches intentional mismatch |
| M6 | Pipeline cache telemetry: stable warm frame metrics |
| M7 | Unsupported blend emits stable diagnostic |
| M8 | Generated gradient WGSL matches CPU within threshold |
| M9 | One runtime effect shares descriptor for CPU and GPU |
| M10 | Java 25 Vector: scalar baseline + measured speedup |
| M11 | One shader family migrated from handwritten to generated |

## Review Checklist

- Is the demo reproducible from commands or artifacts?
- Does it show the acceptance criterion, not implementation detail?
- Are fallback reasons visible when support is intentionally incomplete?
- Are benchmark numbers tied to machine, JDK, backend, and commit?
- Is the next dependency named without implying unplanned scope is done?

## Post

Add a structured evidence comment on the Linear milestone ticket.
