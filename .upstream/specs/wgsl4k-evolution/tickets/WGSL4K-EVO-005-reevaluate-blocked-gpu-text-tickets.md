---
id: WGSL4K-EVO-005
title: "Re-evaluate blocked GPU/Text tickets after wgsl4k evidence"
status: blocked
priority: P0
owner_area: wgsl4k-evolution
claim_impact: DependencyGated
product_activation: false
release_blocking: false
adapter_required: false
repo_url: "https://github.com/ygdrasil-io/wgsl4k.git"
depends_on: [WGSL4K-EVO-004]
---

# WGSL4K-EVO-005 - Re-evaluate blocked GPU/Text tickets after wgsl4k evidence

## PM Note

Ce ticket recontrole les tickets bloques seulement apres evidence wgsl4k fraiche.

## Problem

Several GPU/Text tickets are blocked on wgsl4k validation/reflection. Once a
reviewed wgsl4k SHA is consumed, each blocked ticket must be re-evaluated
against its own acceptance criteria instead of being promoted automatically.

## Scope

- Re-evaluate `KFONT-M11-009`.
- Re-evaluate `KGPU-M7-001`.
- Identify dependent path, coverage, atlas, image, filter, or compute tickets
  that can consume the same reflection contract.
- Move tickets only when their own evidence is fresh and complete.
- Record remaining gates for tickets that stay blocked or proposed.

## Non-Goals

- Do not bulk-promote GPU renderer support.
- Do not mark a route supported from wgsl4k evidence alone.
- Do not add readiness deltas without denominator evidence.

## Spec Sources

- `.upstream/specs/wgsl4k-evolution/README.md`
- `.upstream/specs/wgsl4k-evolution/01-validation-reflection-contract.md`
- `.upstream/specs/gpu-renderer/tickets/STATUS.md`
- `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/KFONT-M11-009-add-wgsl-parser-reflection-validation-for-text-routes.md`
- `.upstream/specs/gpu-renderer/tickets/M7-runtime-effects-color-blend/KGPU-M7-001-add-registered-runtime-effect-descriptor-route.md`

## Acceptance Criteria

- [ ] Each re-evaluated ticket links the exact wgsl4k SHA and Kanvas reports.
- [ ] Tickets move status only when all owning acceptance criteria are met.
- [ ] Tickets that remain blocked name the precise remaining gate.
- [ ] No support claim is expanded without CPU/GPU/reference evidence and route
  diagnostics.

## Required Evidence

- Ticket status updates or explicit no-change notes.
- Fresh validation report links.
- Targeted validation commands for each changed ticket.
- `rtk git diff --check`.

## Fallback / Refusal Behavior

If wgsl4k evidence is partial, stale, or unable to represent required facts,
the consuming tickets remain blocked/proposed with stable remaining gates.

## Validation

```bash
rtk rg -n "status: (ready|review|done|blocked|proposed)" .upstream/specs/gpu-renderer/tickets .upstream/specs/pure-kotlin-text/tickets
rtk git diff --check
```

Additional route-specific validations must be run only for tickets whose status
or evidence changes.

## Status Notes

- `blocked`: Requires `WGSL4K-EVO-004` Kanvas report fixtures generated from a
  reviewed or explicitly approved wgsl4k SHA. No GPU/Text tickets were reopened
  or promoted in this ticket wave.

## Labels

- `wgsl4k`
- `gpu-renderer`
- `pure-kotlin-text`
- `dependency`
