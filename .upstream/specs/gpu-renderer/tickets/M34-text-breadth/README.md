# M34 - Text Breadth

**Status:** active (2026-06-28) — Wave B Track 2

## Goal

Promote subpixel LCD rendering, color font/emoji support, variable font
instances, complex shaping integration, and font fallback chain from specs to
accepted routes with evidence.

## Dependencies

Depends on M0-M1. M34-002 through M34-005 depend on pure Kotlin text stack
output artifacts. M34-001 depends on adapter pixel geometry query.

## Exit Criteria

- [ ] Subpixel LCD with CPU oracle parity.
- [ ] At least one COLRv0 color font rendered.
- [ ] Variable font instances produce correct resolved glyphs.
- [ ] Complex shaping/BiDi correctly consumed.
- [ ] Fallback chain produces correct subrun splits.

## Tickets

| Ticket | Status | Priority | Claim Impact | Route Kind | Product Activation | Adapter Required | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|---|---|---|
| [KGPU-M34-001 - Subpixel LCD rendering](KGPU-M34-001-subpixel-lcd-rendering.md) | `review` | `P0` | `TargetNative` | `GPUNative` | `false` | `true` | `text` | `KGPU-M1-001` | `legacy drawText` |
| [KGPU-M34-002 - Color font pipeline](KGPU-M34-002-color-font-pipeline.md) | `blocked` | `P0` | `DependencyGated` | `GPUNative` | `false` | `true` | `text` | `KGPU-M1-001` | `legacy drawText` |
| [KGPU-M34-003 - Variable font support](KGPU-M34-003-variable-font-support.md) | `blocked` | `P1` | `DependencyGated` | `GPUNative` | `false` | `true` | `text` | `KGPU-M1-001` | `legacy drawText` |
| [KGPU-M34-004 - Complex shaping integration](KGPU-M34-004-complex-shaping-integration.md) | `blocked` | `P1` | `DependencyGated` | `GPUNative` | `false` | `true` | `text` | `KGPU-M1-001` | `legacy drawText` |
| [KGPU-M34-005 - Font fallback chain](KGPU-M34-005-font-fallback-chain.md) | `review` | `P1` | `TargetNative` | `GPUNative` | `false` | `true` | `text` | `KGPU-M1-001` | `legacy drawText` |

## Validation Bundle

```bash
rtk git diff --check && rtk ./gradlew --no-daemon :gpu-renderer:check
```

## Non-Claims

- This milestone does not claim COLRv1, SVG OpenType, or full emoji sequence
  rendering. Those remain `DependencyGated` until the pure Kotlin text stack
  delivers the required parsing artifacts.
- Variable font outline generation and complex shaping/BiDi execution remain
  in the text stack; the GPU renderer only consumes resolved artifacts.
- Font fallback selection logic lives in the text stack; the GPU renderer
  only splits subruns by fallback identity.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and
`../STATUS.md` in the same change.
