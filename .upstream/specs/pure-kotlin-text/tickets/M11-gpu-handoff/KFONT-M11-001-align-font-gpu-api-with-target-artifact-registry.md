---
id: "KFONT-M11-001"
title: "Align `font:gpu-api` with target artifact registry"
status: "done"
milestone: "M11"
priority: "P0"
owner_area: "gpu-api"
claim_impact: "GPU-gated"
depends_on: ["KFONT-M9-002", "KFONT-M10-010"]
legacy_gate: ["dftext", "scaledemoji_rendering", "coloremoji_blendmodes"]
---

# KFONT-M11-001 - Align `font:gpu-api` with target artifact registry

## PM Note

Ce ticket donne au renderer GPU une liste typée des artifacts texte qu'il peut accepter ou refuser.

## Problem

The GPU handoff cannot depend on ad hoc text payloads. It needs a registry that names every text artifact type, key preimage, lifetime class, invalidation facts, upload budget, supported route, and refusal code. Without this registry, M9/M10 artifacts may exist but still be invisible to GPU route selection, or worse, accepted through untyped compatibility paths.

## Scope

- Register `GlyphAtlasArtifact`, `SDFGlyphAtlasArtifact`, `GlyphUploadPlan`, `OutlineGlyphPlan`, `ColorGlyphPlan`, `BitmapGlyphPlan`, and `SVGGlyphPlan`.
- For each artifact type, record descriptor version, owner subsystem, key preimage fields, compact hash, lifetime class, invalidation token, memory/upload budget class, supported GPU routes, and missing/stale/budget diagnostics.
- Emit `text-gpu-artifact-registry.json` with deterministic ordering and no `Sk*`, font bytes, or live GPU handles.
- Add unregistered artifact refusal mapping to `text.gpu.artifact-unregistered` and `unsupported.text.artifact_unregistered`.
- Preserve legacy gates until specific artifact routes have implementation and GPU evidence.

## Non-Goals

- Do not implement A8/SDF/color/bitmap/SVG GPU rendering in this registry ticket.
- Do not allocate WebGPU resources or build pipeline keys.
- Do not parse fonts, decode glyph payloads, shape text, or rebuild glyph atlases in `font:gpu-api`.
- Do not retire `dftext`, `scaledemoji_rendering`, or `coloremoji_blendmodes`.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/target/high-performance-wgsl-pipeline-target.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class TextGPUArtifactDescriptor(
    val artifactType: TextArtifactType,
    val descriptorVersion: ArtifactDescriptorVersion,
    val owner: ArtifactOwner = ArtifactOwner.PureKotlinText,
    val keyPreimageSchema: KeyPreimageSchema,
    val supportedRoutes: Set<GPUTextRoute>,
    val lifetimeClass: ArtifactLifetimeClass,
    val invalidationFacts: List<InvalidationFactKind>,
    val memoryBudgetClass: TextArtifactBudgetClass,
    val uploadBudgetClass: TextUploadBudgetClass?,
    val missingDiagnostic: String,
)

interface TextGPUArtifactRegistry {
    fun descriptor(type: TextArtifactType): TextGPUArtifactDescriptor?
    fun refuseUnregistered(typeName: String, artifactHash: StableHash?): GPUTextDiagnostic
}
```

## Acceptance Criteria

- [ ] The registry dump lists every target text artifact type and its supported GPU route family.
- [ ] Unregistered artifacts refuse with both handoff and renderer route diagnostics.
- [ ] Descriptor key preimage schemas include generation and invalidation facts where relevant.
- [ ] Registry descriptors contain no `Sk*` types, font bytes, raw GPU handles, or CPU-rendered full text texture routes.
- [ ] Legacy gate rows remain `GPU-gated` until individual route tickets attach GPU evidence.

## Required Evidence

- `text-gpu-artifact-registry.json` fixture covering all seven text artifact types.
- Negative fixture for unknown artifact type with `text.gpu.artifact-unregistered` and `unsupported.text.artifact_unregistered`.
- Review dump proving descriptor order and hashes are deterministic.

## Fallback / Refusal Behavior

- Unknown or stale artifacts refuse before route planning.
- Registration is not a support claim; route support stays `GPU-gated` until a route-specific ticket proves execution.
- Legacy gates `dftext`, `scaledemoji_rendering`, and `coloremoji_blendmodes` remain open until implementation evidence, diagnostics, and dashboard updates are linked.

## Dashboard Impact

- Expected row: `Text GPU artifact registry`.
- Expected classification: `GPU-gated`.
- Claim promotion allowed: no, because artifact registration alone is not renderer support.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*ArtifactRegistry*'
```

## Status Notes

- `done`: Merged into `master` by PR #1653 (`7e74ee77`) and revalidated on 2026-06-15 in `reports/pure-kotlin-text/2026-06-15-kfont-review-closeout.md`. This remains contract evidence only; no GPU text route or legacy gate is promoted.
- `proposed`: Establishes the artifact registry consumed by all M11 route tickets.
- Move to `ready` only after artifact type names, route families, and refusal code mapping are reviewed.
- `review`: 2026-06-15 entry-contract evidence is in
  `reports/pure-kotlin-text/2026-06-15-kfont-m11-001-003-entry-contract.md`.
  This is contract evidence only; it does not promote GPU text route support,
  WebGPU execution, or any legacy gate closure.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M11`
- `area:gpu-api`
- `claim:GPU-gated`
- `legacy:dftext`
- `legacy:scaledemoji_rendering`
- `legacy:coloremoji_blendmodes`
