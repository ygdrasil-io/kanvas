# wgsl4k Evolution Spec Pack

Status: Active dependency packet
Date: 2026-06-15
Repository: `https://github.com/ygdrasil-io/wgsl4k.git`

## Purpose

This spec pack turns the current Kanvas `wgsl4k` dependency gate into an
actionable contract and contribution packet. It defines the WGSL validation and
reflection facts Kanvas needs before promoting GPU renderer, text, runtime
effect, path, coverage, atlas, or compute routes that assemble or consume WGSL
modules.

The pack is also the handoff for contributing the missing behavior to
`ygdrasil-io/wgsl4k` through a tracked submodule branch and upstream pull
request.

This pack does not claim any Kanvas route is product-supported. It exists to
make the remaining dependency gate explicit.

## Source Of Truth

- Target architecture:
  `.upstream/target/high-performance-wgsl-pipeline-target.md`
- WGSL parser/reflection/module builder:
  `.upstream/specs/wgsl-pipeline/02-wgsl-parser-reflection-module-builder.md`
- GPU renderer WGSL ABI:
  `.upstream/specs/gpu-renderer/11-wgsl-layout-binding-abi.md`
- Text GPU handoff gate:
  `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/KFONT-M11-009-add-wgsl-parser-reflection-validation-for-text-routes.md`
- Registered runtime-effect gate:
  `.upstream/specs/gpu-renderer/tickets/M7-runtime-effects-color-blend/KGPU-M7-001-add-registered-runtime-effect-descriptor-route.md`

## Repository And Submodule Policy

The accepted upstream repository is:

```text
https://github.com/ygdrasil-io/wgsl4k.git
```

The default Kanvas submodule path is `external/wgsl4k`, unless the implementation
plan records a more specific path before `WGSL4K-EVO-002` executes.

Kanvas may consume an explicit `wgsl4k` commit SHA for validation evidence only
after one of these gates is satisfied:

- the upstream `wgsl4k` pull request is merged; or
- the user explicitly approves a reviewed branch/SHA before merge.

Until then, routes depending on the missing reflection behavior remain
`blocked`, `proposed`, or `not-promoted`.

## Current Evidence

2026-06-15 execution evidence:

- Kanvas spec PR: `https://github.com/ygdrasil-io/kanvas/pull/1650`
- wgsl4k submodule path: `external/wgsl4k`
- wgsl4k import SHA: `96410250916ac91f79269ac64bace0a9272826b9`
- wgsl4k contribution branch: `codex/kanvas-reflection-contract`
- wgsl4k contribution commit: `e5b57786936b16e0e18ca35e46faa1846e0cdec9`
- wgsl4k draft PR: `https://github.com/ygdrasil-io/wgsl4k/pull/9`
- wgsl4k public API wording: generic `Wgsl*` reflection/validation names,
  with no Kanvas product wording in the wgsl4k code path.

Kanvas remains gated from consuming the contribution commit for report evidence
until the wgsl4k PR is reviewed and either merged or explicitly approved by the
user as a consumable SHA.

## Spec Index

| Spec | Purpose |
|---|---|
| `01-validation-reflection-contract.md` | Minimal wgsl4k validation, reflection, JSON, and diagnostic contract needed by Kanvas. |
| `02-contribution-packet.md` | Fixture-first contribution packet for a wgsl4k branch and PR. |
| `tickets/README.md` | Repo-native ticket catalog for importing, contributing, consuming, and re-evaluating the dependency. |

## Current Dependency Gates

The initial consumer tickets are:

| Ticket | Gate |
|---|---|
| `KFONT-M11-009` | Text WGSL modules must parse and reflect enough facts to compare `GPUTextBinding`, text instance layout, texture/sampler slots, and SDF uniforms. |
| `KGPU-M7-001` | Registered runtime-effect WGSL modules must parse and reflect enough facts to compare descriptor uniforms, resources, child slots, and complete module ABI. |

Future path, coverage, atlas, image, filter, color-management, and compute
routes may use this same contract, but this spec does not promote them.

## Claim Policy

- Passing WGSL syntax validation alone is not product support evidence.
- Passing wgsl4k reflection alone is not visual correctness evidence.
- A Kanvas GPU route is promoted only when its owning spec also provides CPU,
  GPU, reference, diagnostic, and route-specific evidence.
- If wgsl4k cannot represent a WGSL feature Kanvas needs, Kanvas records a
  minimized case for wgsl4k and keeps the consuming route unpromoted.
- Kanvas must not add hidden local workarounds for ambiguous wgsl4k parser,
  reflection, or generator behavior.

## Non-Goals

- Do not port Ganesh or Graphite.
- Do not rebuild Skia's SkSL compiler, IR, or VM.
- Do not accept arbitrary user WGSL as product renderer code.
- Do not add CPU execution of WGSL.
- Do not use this pack to activate product rendering support.

## Execution Order

1. Review and accept this spec pack.
2. Import `wgsl4k` as a tracked submodule.
3. Create a wgsl4k branch with the fixture and reflection work.
4. Open a wgsl4k PR for user review.
5. Consume the reviewed wgsl4k SHA in Kanvas validation reports.
6. Re-evaluate blocked Kanvas tickets only after fresh evidence exists.
