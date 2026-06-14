---
id: "KFONT-M11-002"
title: "Add no-`Sk*` leakage validation"
status: "proposed"
milestone: "M11"
priority: "P0"
owner_area: "gpu-api"
claim_impact: "GPU-gated"
depends_on: ["KFONT-M11-001"]
legacy_gate: null
---

# KFONT-M11-002 - Add no-`Sk*` leakage validation

## PM Note

Ce ticket empêche le handoff GPU texte de redevenir dépendant des objets Skia-like.

## Problem

M11's boundary rule says GPU text payloads must not carry `SkFont`, `SkTypeface`, `SkShaper`, `SkTextBlob`, `SkPaint`, font bytes, platform handles, raw GPU handles, or CPU-rendered full text textures. The current catalog does not define a validation fixture that proves the boundary. Without it, future route tickets may accidentally pass Skia-like objects through normalized commands.

## Scope

- Add payload validation for `TextGPUArtifactBundle`, `NormalizedDrawCommand.DrawTextRun`, `GPUTextRunPlan`, `GPUTextSubRunPlan`, and registry descriptors.
- Forbid `Sk*` API types, mutable text stack objects, font bytes, platform font handles, raw GPU handles, and full text CPU texture payloads.
- Emit `text-gpu-no-sk-leakage-report.json` with scanned type names, forbidden field paths, payload hashes, and diagnostics.
- Map leaks to `text.gpu.sk-type-leaked`, `unsupported.text.sk_type_leaked`, or `unsupported.text.cpu_rendered_texture_forbidden`.
- Add a positive fixture with only value-object IDs and a negative fixture for each forbidden class of field.

## Non-Goals

- Do not migrate Skia-like facade adapters in this ticket.
- Do not forbid domain-specific value objects that wrap UUIDs or stable hashes.
- Do not implement route execution or shader validation.
- Do not hide leaks by stringifying forbidden objects into labels.

## Spec Sources

- `.upstream/specs/pure-kotlin-text/ROADMAP.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/target/high-performance-wgsl-pipeline-target.md`
- `.upstream/specs/pure-kotlin-text/09-migration-from-current-font-pack.md`

## Design Sketch

```kotlin
data class TextPayloadLeakCheck(
    val payloadId: StableId,
    val payloadKind: TextPayloadKind,
    val scannedFields: List<FieldPath>,
    val forbiddenFindings: List<ForbiddenTextPayloadField>,
)

data class ForbiddenTextPayloadField(
    val path: FieldPath,
    val forbiddenKind: ForbiddenPayloadKind,
    val diagnostic: GPUTextDiagnostic,
)
```

## Acceptance Criteria

- [ ] Positive `DrawTextRun` and artifact bundle fixtures pass with only value objects, stable hashes, UUID wrappers, diagnostics, and artifact refs.
- [ ] Negative fixtures fail for `SkFont`, `SkTypeface`, `SkTextBlob`, `SkPaint`, font bytes, platform handles, raw GPU handles, and full text CPU textures.
- [ ] Leak diagnostics include payload kind, field path, and stable reason code.
- [ ] Stringified or opaque wrappers around forbidden payloads are rejected.
- [ ] The validation report is deterministic and linked from future route evidence.

## Required Evidence

- `text-gpu-no-sk-leakage-report.json` positive and negative fixtures.
- Diagnostic snapshots for `unsupported.text.sk_type_leaked` and `unsupported.text.cpu_rendered_texture_forbidden`.
- Type-scan fixture proving registry descriptors and `DrawTextRun` payloads are covered.

## Fallback / Refusal Behavior

- Any forbidden field refuses route planning before resource upload or shader selection.
- CPU-rendered full text textures remain forbidden even if they would visually match a fixture.
- The row stays `GPU-gated` until validation fixtures are enforced in the GPU API test suite.

## Dashboard Impact

- Expected row: `No Sk leakage in text GPU handoff`.
- Expected classification: `GPU-gated`.
- Claim promotion allowed: no, unless no-leakage validation evidence is attached.

## Validation

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:gpu-api:test --tests '*NoSkLeakage*'
```

## Status Notes

- `proposed`: Boundary validation for all later normalized text command and route tickets.
- Move to `ready` only after forbidden field kinds and report schema are reviewed.

## Linear Labels

- `pure-kotlin-font`
- `milestone:M11`
- `area:gpu-api`
- `claim:GPU-gated`
