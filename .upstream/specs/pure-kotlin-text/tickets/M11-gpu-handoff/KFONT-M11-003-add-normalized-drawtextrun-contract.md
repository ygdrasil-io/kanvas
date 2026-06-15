---
id: "KFONT-M11-003"
title: "Add normalized `DrawTextRun` contract"
status: "review"
milestone: "M11"
priority: "P0"
owner_area: "gpu-api"
claim_impact: "GPU-gated"
depends_on: ["KFONT-M8-002", "KFONT-M9-002", "KFONT-M11-002"]
legacy_gate: null
---

# KFONT-M11-003 - Add normalized `DrawTextRun` contract

## PM Note

Ce ticket définit le paquet texte que le renderer GPU reçoit, sans reshaper ni relire les fontes.

## Problem

GPU route planning needs one normalized text command shape. The payload must point to paragraph/glyph run IDs, artifact refs, transform/clip/layer/material facts, atlas generations, upload dependencies, and text diagnostics. Without a concrete `DrawTextRunPayload`, route tickets can drift into direct glyph arrays, font parser access, or CPU-rendered texture compatibility.

## Scope

- Define `NormalizedDrawCommand.DrawTextRun` payload as immutable value objects.
- Include command ID, text layout result ID or glyph run ID, glyph run descriptor refs, glyph artifact plan refs, transform facts, clip facts, layer facts, material descriptor, blend/color facts, artifact key hashes, atlas generation, invalidation tokens, upload dependency facts, diagnostics, and evidence provenance.
- Use domain-specific UUID wrappers for text layout, glyph run, artifact, upload, and diagnostic IDs.
- Emit `draw-text-run-payload.json` with deterministic field order and no forbidden leakage.
- Add refusal for nondumpable payloads and full text CPU texture routes.

## Non-Goals

- Do not implement renderer route selection, subrun splitting, or resource upload in this ticket.
- Do not include raw text strings beyond evidence provenance fields required by validation.
- Do not carry `Sk*` objects, font bytes, platform handles, or live GPU resources.
- Do not claim A8/SDF/color/bitmap/SVG route support.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/gpu-renderer/09-draw-family-support-matrix.md`
- `.upstream/target/high-performance-wgsl-pipeline-target.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class DrawTextRunPayload(
    val commandId: DrawCommandId,
    val layoutResultId: TextLayoutResultId?,
    val glyphRunId: GlyphRunDescriptorId?,
    val glyphRuns: List<GlyphRunDescriptorRef>,
    val artifacts: List<TextArtifactRef>,
    val transform: TextTransformFacts,
    val clip: ClipFacts,
    val layer: LayerFacts,
    val material: TextMaterialDescriptor,
    val blendColor: TextBlendColorFacts,
    val artifactKeyHashes: List<StableHash>,
    val atlasGenerations: List<AtlasGeneration>,
    val uploadDependencies: List<TextUploadDependencyRef>,
    val diagnostics: List<TextRouteDiagnostic>,
    val provenance: TextEvidenceProvenance,
)
```

## Acceptance Criteria

- [ ] The payload can represent paragraph output and direct glyph-run output without re-shaping or font parsing.
- [ ] Every artifact ref includes key hash, artifact type, generation/invalidation facts where relevant, and diagnostics.
- [ ] `draw-text-run-payload.json` is deterministic and passes the no-`Sk*` leakage validation.
- [ ] Nondumpable fields refuse with `unsupported.text.payload_nondumpable`.
- [ ] CPU-rendered complete text texture payloads refuse with `unsupported.text.cpu_rendered_texture_forbidden`.

## Required Evidence

- `draw-text-run-payload.json` fixture with paragraph-produced glyph runs and A8 artifact refs.
- Direct glyph-run fixture using explicit glyph descriptors and artifact refs.
- Negative fixtures for nondumpable payload and forbidden CPU-rendered text texture route.

## Fallback / Refusal Behavior

- If required artifact refs are missing, `DrawTextRun` remains refused until the text stack supplies typed artifacts.
- The renderer must not re-shape text, choose fallback fonts, or parse font bytes to repair the payload.
- The row stays `GPU-gated` until normalized payload tests and no-leakage tests pass.

## Dashboard Impact

- Expected row: `Normalized DrawTextRun payload`.
- Expected classification: `GPU-gated`.
- Claim promotion allowed: no, because this ticket defines the command contract but not route execution.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*DrawTextRun*'
```

## Status Notes

- `proposed`: Depends on paragraph/glyph descriptors and no-leakage validation.
- Move to `ready` only after payload field list and dump schema are reviewed.
- `review`: 2026-06-15 entry-contract evidence is in
  `reports/pure-kotlin-text/2026-06-15-kfont-m11-001-003-entry-contract.md`.
  This proves the normalized payload contract only; it does not implement
  renderer route selection, resource upload, A8 atlas sampling, or product
  support.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M11`
- `area:gpu-api`
- `claim:GPU-gated`
