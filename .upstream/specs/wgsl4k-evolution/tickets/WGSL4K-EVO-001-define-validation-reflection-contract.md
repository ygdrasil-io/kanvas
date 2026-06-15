---
id: WGSL4K-EVO-001
title: "Define wgsl4k validation/reflection contract"
status: review
priority: P0
owner_area: wgsl4k-evolution
claim_impact: DependencyGated
product_activation: false
release_blocking: false
adapter_required: false
repo_url: "https://github.com/ygdrasil-io/wgsl4k.git"
depends_on: []
---

# WGSL4K-EVO-001 - Define wgsl4k validation/reflection contract

## PM Note

Ce ticket transforme le blocage wgsl4k en contrat testable, sans activer de
route GPU Kanvas.

## Problem

Kanvas routes require complete WGSL validation and ABI reflection before they
can be promoted. Existing tickets name wgsl4k as a gate, but the exact expected
validation, reflection, diagnostics, and contribution packet must be written in
one place.

## Scope

- Add the `wgsl4k-evolution` spec pack.
- Define the validation/reflection contract.
- Define the fixture-first contribution packet for wgsl4k.
- Create follow-up tickets for submodule import, wgsl4k PR preparation, Kanvas
  report consumption, and blocked-ticket re-evaluation.

## Non-Goals

- Do not import the submodule in this ticket.
- Do not implement wgsl4k fixes in this ticket.
- Do not promote any Kanvas GPU/Text route.

## Spec Sources

- `.upstream/specs/wgsl4k-evolution/README.md`
- `.upstream/specs/wgsl4k-evolution/01-validation-reflection-contract.md`
- `.upstream/specs/wgsl4k-evolution/02-contribution-packet.md`

## Acceptance Criteria

- [ ] The spec pack names the accepted wgsl4k repository URL.
- [ ] The validation/reflection contract lists required facts and diagnostics.
- [ ] The contribution packet defines positive and negative fixtures.
- [ ] Follow-up tickets keep submodule import, wgsl4k PR work, Kanvas
  consumption, and blocked-ticket re-evaluation separate.
- [ ] No product support claim is added.

## Required Evidence

- Spec files committed under `.upstream/specs/wgsl4k-evolution/`.
- `rtk git diff --check`.
- Self-review scan for placeholders, contradictions, and hidden support claims.

## Fallback / Refusal Behavior

Until this ticket is accepted, wgsl4k-dependent routes remain under their
existing blocked, proposed, or not-promoted gates.

## Validation

```bash
rtk rg -n "T[O]DO|T[B]D|Promoted[S]upported|product_activation: tru[e]|release_blocking: tru[e]" .upstream/specs/wgsl4k-evolution
rtk git diff --check
```

## Status Notes

- `review`: The spec pack exists and requires user review before execution
  tickets move to `ready`.

## Labels

- `wgsl4k`
- `dependency`
- `wgsl`
- `reflection`
