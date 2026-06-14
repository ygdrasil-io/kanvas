# M6 - Text And Glyph Handoff

## Goal

Consume pure Kotlin text artifacts through typed GPU renderer text routes
without reshaping text, reading font bytes, or accepting CPU-rendered full text
texture compatibility.

## Dependencies

Depends on pure Kotlin text M11 deliverables for typed artifacts and on M4/M5
resource and binding infrastructure. The target source is
`21-text-glyph-pipeline.md`.

## Exit Criteria

- [ ] `DrawTextRun` payloads and artifact refs stay immutable and dumpable.
- [ ] A8 atlas routes include upload, binding, WGSL, ordering, and GPU proof.
- [ ] Unsupported SDF/color/bitmap/SVG/emoji routes refuse with stable
      diagnostics.
- [ ] Material keys exclude glyph IDs, atlas coordinates, generations, and live
      handles.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M6-001 - Accept typed pure Kotlin `DrawTextRun` payloads](KGPU-M6-001-accept-typed-pure-kotlin-drawtextrun-payloads.md) | `proposed` | `P0` | `DependencyGated` | `mixed` | `false` | `false` | `text-handoff` | `KFONT-M11-003` | `dftext` |
| [KGPU-M6-002 - Add A8 glyph atlas sampling route](KGPU-M6-002-add-a8-glyph-atlas-sampling-route.md) | `proposed` | `P0` | `TargetPrepared` | `CPUPreparedGPU` | `false` | `true` | `text-atlas` | `KGPU-M6-001`, `KFONT-M11-004` | `dftext` |
| [KGPU-M6-003 - Add text resource upload and binding plans](KGPU-M6-003-add-text-resource-upload-and-binding-plans.md) | `proposed` | `P0` | `TargetPrepared` | `CPUPreparedGPU` | `false` | `true` | `text-resources` | `KGPU-M6-002`, `KFONT-M11-007` | `dftext` |
| [KGPU-M6-004 - Add SDF and color glyph dependency gates](KGPU-M6-004-add-sdf-and-color-glyph-dependency-gates.md) | `proposed` | `P1` | `DependencyGated` | `RefuseDiagnostic` | `false` | `false` | `text-validation` | `KGPU-M6-001` | `scaledemoji_rendering`, `coloremoji_blendmodes` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :gpu-renderer:check
rtk ./gradlew --no-daemon :gpu-raster:test --tests '*Text*'
```

## Non-Claims

- No broad shaping, fallback fonts, emoji, color fonts, LCD, SDF, or arbitrary
  text layout support.
- The GPU renderer must not parse fonts or reshape text to repair payloads.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
