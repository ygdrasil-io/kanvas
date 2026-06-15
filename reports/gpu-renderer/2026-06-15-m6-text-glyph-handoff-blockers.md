# GPU Renderer M6 Text/Glyph Handoff Blocker Audit

Date: 2026-06-15

## Scope

Reviewed the GPU renderer ticket catalog, `STATUS.md`, M6 milestone tickets,
and the cited specs:

- `.upstream/specs/gpu-renderer/21-text-glyph-pipeline.md`
- `.upstream/specs/gpu-renderer/17-payload-gathering-and-slots.md`
- `.upstream/specs/gpu-renderer/18-texture-image-ownership.md`
- `.upstream/specs/pure-kotlin-text/05-color-fonts-bitmap-svg-emoji.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`

## Decision Update

Follow-up work on 2026-06-15 closed the two non-adapter M6 tickets:
KGPU-M6-001 and KGPU-M6-004. The remaining M6 implementation work is still not
actionable as a renderer route wave because KGPU-M6-002 and KGPU-M6-003 require
resource/upload contracts plus adapter-backed upload, binding, WGSL/reflection,
sampling, and readback evidence. The GPU renderer must still not parse fonts,
reshape text, decode font glyph payloads, or accept CPU-rendered text textures
to clear these gates.

## Ticket Gates

| Ticket | Status | Remaining gate |
|---|---|---|
| KGPU-M6-001 | `done` | Closed by `reports/gpu-renderer/2026-06-15-m6-001-text-handoff.md`; no text GPU route is promoted. |
| KGPU-M6-002 | `blocked` | KGPU-M6-003 plus KFONT-M11-004, KFONT-M11-008, KFONT-M11-009 glyph/artifact evidence and adapter-backed A8 atlas sampling/readback proof. |
| KGPU-M6-003 | `blocked` | KFONT-M11-007 resource/upload contracts and adapter-backed upload/binding/generation evidence. |
| KGPU-M6-004 | `done` | Closed by `reports/gpu-renderer/2026-06-15-m6-004-text-representation-gates.md`; refusal rows remain non-promoted. |

## Non-Claims

- No broad shaping, fallback fonts, emoji, color fonts, LCD, SDF, arbitrary
  text layout, or A8 text rendering route is claimed.
- No KGPU-M6 ticket retires `dftext`, `scaledemoji_rendering`, or
  `coloremoji_blendmodes`.
- No CPU-rendered full text texture fallback is accepted.

## Validation

```bash
rtk git diff --check
```
