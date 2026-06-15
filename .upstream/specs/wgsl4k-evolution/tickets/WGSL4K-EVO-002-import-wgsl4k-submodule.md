---
id: WGSL4K-EVO-002
title: "Import wgsl4k as tracked submodule"
status: proposed
priority: P0
owner_area: wgsl4k-evolution
claim_impact: DependencyGated
product_activation: false
release_blocking: false
adapter_required: false
repo_url: "https://github.com/ygdrasil-io/wgsl4k.git"
default_submodule_path: "external/wgsl4k"
depends_on: [WGSL4K-EVO-001]
---

# WGSL4K-EVO-002 - Import wgsl4k as tracked submodule

## PM Note

Ce ticket ajoute wgsl4k comme submodule suivi, sans changer les routes Kanvas.

## Problem

Kanvas needs a reproducible wgsl4k checkout for fixture development, reviewed
branch work, and pinned-SHA consumption. The dependency must be explicit instead
of relying on undocumented local state.

## Scope

- Add `https://github.com/ygdrasil-io/wgsl4k.git` as a git submodule.
- Default to `external/wgsl4k` unless the implementation plan records another
  path.
- Record the initial pinned commit SHA.
- Document branch and update policy for Kanvas consumption.
- Keep build integration opt-in until `WGSL4K-EVO-004`.

## Non-Goals

- Do not implement wgsl4k fixes in this ticket.
- Do not wire Kanvas build tasks to the submodule in this ticket.
- Do not promote any GPU/Text route.

## Spec Sources

- `.upstream/specs/wgsl4k-evolution/README.md`
- `.upstream/specs/wgsl4k-evolution/02-contribution-packet.md`

## Acceptance Criteria

- [ ] `.gitmodules` records the wgsl4k URL and path.
- [ ] The submodule is pinned to a specific commit SHA.
- [ ] Documentation records how Kanvas chooses a reviewed wgsl4k SHA.
- [ ] No Kanvas product support claim changes.

## Required Evidence

- `rtk git submodule status external/wgsl4k` or the accepted path.
- `rtk git diff --check`.
- Status note with pinned wgsl4k SHA.

## Fallback / Refusal Behavior

If the submodule cannot be imported or the URL changes, downstream tickets stay
`proposed` or move to `blocked` with the exact repository/path gate.

## Validation

```bash
rtk git submodule status external/wgsl4k
rtk git diff --check
```

## Status Notes

- `proposed`: Requires accepted execution plan and clean submodule import.

## Labels

- `wgsl4k`
- `submodule`
- `dependency`
