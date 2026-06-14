---
id: KGPU-M6-001
title: "Accept typed pure Kotlin `DrawTextRun` payloads"
status: proposed
milestone: M6
priority: P0
owner_area: text-handoff
claim_impact: DependencyGated
route_kind: mixed
product_activation: false
release_blocking: false
adapter_required: false
depends_on: [KFONT-M11-003]
legacy_gate: dftext
---

# KGPU-M6-001 - Accept typed pure Kotlin `DrawTextRun` payloads

## PM Note

Ce ticket connecte le renderer GPU aux artifacts texte sans reshaper ni relire
les fontes.

## Problem

Text rendering must consume typed pure Kotlin artifacts and must not carry
mutable Skia-like objects, font bytes, or complete CPU-rendered text textures.

## Scope

- Accept immutable `DrawTextRun` payload facts and diagnostics.
- Add leakage/refusal checks for nondumpable or forbidden payload fields.

## Non-Goals

- Do not implement atlas sampling.
- Do not shape text inside `:gpu-renderer`.

## Spec Sources

- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`

## Design Sketch

```kotlin
data class DrawTextRunHandoff(val artifactRefs: List<String>, val diagnostics: List<String>)
```

## Acceptance Criteria

- [ ] Payload contains only dumpable value objects.
- [ ] Missing artifacts refuse with stable diagnostics.
- [ ] No `Sk*` leakage is possible.

## Required Evidence

- Payload dumps, no-leakage report, and refusal fixtures.

## Fallback / Refusal Behavior

Missing text artifacts keep the route dependency-gated and refused.

## Dashboard Impact

- Expected row: `gpu-renderer.text.drawtextrun-handoff`
- Expected classification: `DependencyGated`
- Claim promotion allowed: no.

## Validation

```bash
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk git diff --check
```

## Status Notes

- `proposed`: Depends on pure Kotlin text M11 payloads.

## Linear Labels

- `gpu-renderer`
- `milestone:M6`
- `area:text`
