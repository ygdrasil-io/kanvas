---
id: WGSL4K-EVO-003
title: "Prepare wgsl4k fixture PR"
status: review
priority: P0
owner_area: wgsl4k-evolution
claim_impact: DependencyGated
product_activation: false
release_blocking: false
adapter_required: false
repo_url: "https://github.com/ygdrasil-io/wgsl4k.git"
depends_on: [WGSL4K-EVO-002]
---

# WGSL4K-EVO-003 - Prepare wgsl4k fixture PR

## PM Note

Ce ticket prepare une branche et une PR wgsl4k avec des fixtures minimales.

## Problem

Kanvas needs wgsl4k to prove validation and reflection behavior with concrete
positive and negative WGSL cases. A general request to "improve wgsl4k" is too
ambiguous to unblock Kanvas tickets.

## Scope

- Create a wgsl4k branch from the imported submodule.
- Add or adapt tests for the fixture set in the contribution packet.
- Implement or fix wgsl4k behavior until the fixture tests pass, if the
  implementation plan includes code changes.
- Open a PR against `ygdrasil-io/wgsl4k`.
- Record the PR URL and reviewed SHA.

## Non-Goals

- Do not promote Kanvas routes in this ticket.
- Do not add Kanvas-specific hidden semantics to wgsl4k.
- Do not require wgsl4k to understand Kanvas product concepts.

## Spec Sources

- `.upstream/specs/wgsl4k-evolution/01-validation-reflection-contract.md`
- `.upstream/specs/wgsl4k-evolution/02-contribution-packet.md`

## Acceptance Criteria

- [x] Positive fixtures validate and reflect expected facts.
- [x] Negative fixtures produce deterministic diagnostics.
- [x] Unsupported valid WGSL forms produce explicit unsupported-feature output.
- [x] PR URL is recorded.
- [x] User review gate is recorded before Kanvas consumes the SHA.

## Required Evidence

- wgsl4k branch name.
- wgsl4k PR URL.
- wgsl4k validation/test output.
- Reviewed or merged commit SHA.

## Fallback / Refusal Behavior

If wgsl4k cannot represent a required feature, the PR records the minimized
case and Kanvas consuming tickets remain blocked or not-promoted.

## Validation

```bash
rtk git -C external/wgsl4k status --short --branch
rtk git -C external/wgsl4k log --oneline -n 3
```

Additional wgsl4k test commands must be defined by the implementation plan
after the submodule is available.

## Status Notes

- `review`: wgsl4k branch `codex/kanvas-reflection-contract` was pushed with
  commit `f52580b31e136976a553af6d5d41a5a9e27919c3` and draft PR
  `https://github.com/ygdrasil-io/wgsl4k/pull/9`. Fresh wgsl4k validation:
  `rtk ./gradlew --no-daemon :wgsl:wgsl-core:check :wgsl:wgsl-parser:check`.
  Kanvas must not consume this SHA for report evidence until the PR is reviewed
  and either merged or explicitly approved by the user.

## Labels

- `wgsl4k`
- `fixture`
- `pull-request`
- `reflection`
