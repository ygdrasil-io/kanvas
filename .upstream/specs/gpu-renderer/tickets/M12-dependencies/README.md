# M12 - Dependencies

## Goal

Complete all dependency foundation work (pure-Kotlin text, codec pipeline, wgsl4k integration) required before any GPU route activation can proceed. M12 must finish first; all subsequent milestones depend on it.

## Dependencies

Depends on existing M0-M11 contract/refusal ticket work. No upstream GPU route dependencies; this is the foundational dependency layer.

## Exit Criteria

- [ ] All 10 M12 tickets have accepted evidence
- [ ] Pure-Kotlin text stack produces deterministic glyph output
- [ ] Codec pipeline decodes PNG/JPEG/WebP/GIF to RGBA8Unorm
- [ ] wgsl4k parser-backed reflection and ABI validation gate all WGSL modules
- [ ] No ticket claims product route activation

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M12-001 - Finalize SFNT parser + glyf/CFF/CFF2 scaler with deterministic output](KGPU-M12-001-sfnt-parser-scaler.md) | `proposed` | `P0` | `TargetNative` | `CPUReferenceOnly` | `false` | `false` | `text-shaper` | [] | null |
| [KGPU-M12-002 - Add A8 glyph rasterizer with strike key + cache invalidation](KGPU-M12-002-a8-glyph-rasterizer.md) | `proposed` | `P0` | `TargetNative` | `CPUReferenceOnly` | `false` | `false` | `text-shaper` | [KGPU-M12-001] | null |
| [KGPU-M12-003 - Add GPU glyph atlas upload plan with texture region packing](KGPU-M12-003-glyph-atlas-upload-plan.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `text-resources` | [KGPU-M12-001, KGPU-M12-002] | null |
| [KGPU-M12-004 - Wire GPU renderer text handoff: GlyphRunDescriptor -> DrawTextRun accepted](KGPU-M12-004-text-handoff-wired.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `text-recording` | [KGPU-M12-001, KGPU-M12-002, KGPU-M12-003] | null |
| [KGPU-M12-005 - Add GPU image decode plan: codec selection -> decode -> RGBA8Unorm pixels](KGPU-M12-005-image-decode-plan.md) | `proposed` | `P0` | `TargetNative` | `CPUReferenceOnly` | `false` | `false` | `codec-decode` | [] | null |
| [KGPU-M12-006 - Add GPU texture upload from decoded pixels with format validation](KGPU-M12-006-texture-upload-validation.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `codec-upload` | [KGPU-M12-005] | null |
| [KGPU-M12-007 - Wire codec provenance into GPUImagePipelinePlan (accept PNG/JPEG/WebP/GIF)](KGPU-M12-007-codec-pipeline-plan.md) | `proposed` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `codec-pipeline` | [KGPU-M12-005, KGPU-M12-006] | null |
| [KGPU-M12-008 - Integrate wgsl4k parser into WGSLModuleAssembler for live reflection](KGPU-M12-008-wgsl4k-parser-integration.md) | `proposed` | `P0` | `ImplementationCandidate` | `CPUReferenceOnly` | `false` | `false` | `wgsl4k-parser` | [] | null |
| [KGPU-M12-009 - Add WGSL ABI validation: reflected layout vs Kotlin packing byte-match](KGPU-M12-009-wgsl-abi-validation.md) | `proposed` | `P0` | `ImplementationCandidate` | `CPUReferenceOnly` | `false` | `false` | `wgsl4k-abi` | [KGPU-M12-008] | null |
| [KGPU-M12-010 - Add wgsl4k evolution gate: parser-backed reflection for all first-route WGSL](KGPU-M12-010-wgsl4k-evolution-gate.md) | `proposed` | `P0` | `ImplementationCandidate` | `CPUReferenceOnly` | `false` | `false` | `wgsl4k-gate` | [KGPU-M12-008, KGPU-M12-009] | null |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:test
rtk ./gradlew --no-daemon :codec:test
rtk ./gradlew --no-daemon :gpu-renderer:test --tests '*WGSL*'
```

## Non-Claims

- No product route activation
- No GPU text or image rendering (gated by M20 and M17)
- Latin glyphs only; no bidi, complex shaping, COLRv1, SVG glyphs
- No HEIF/AVIF decode; no HDR; no animated images

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
