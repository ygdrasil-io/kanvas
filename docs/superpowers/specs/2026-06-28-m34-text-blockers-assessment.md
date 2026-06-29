# M34 Text Breadth — Blocker Assessment

**Date:** 2026-06-28
**Author:** Wave B execution audit
**Decision needed:** Unblock with reduced scope or keep blocked?

## Tickets Concerned

| Ticket | Blocked reason |
|--------|---------------|
| M34-002 | Color font pipeline — "gated on pure-kotlin-text COLRv0 parsing artifacts" |
| M34-003 | Variable font support — "gated on pure-kotlin-text variable font resolution artifacts" |
| M34-004 | Complex shaping integration — "gated on pure-kotlin-text shaping/BiDi output artifacts" |

## Findings: Handoff Types Already Exist

All 3 tickets claim `DependencyGated` on artifacts that **are already available**:

### M34-002 — COLRv0

| Artifact claimed missing | Reality |
|--------------------------|---------|
| COLRv0 parsing | `COLRV0Parser` + `CPALV0Parser` fully implemented in text stack |
| GPU handoff type | `ColorGlyphPlanRef` is defined and registered (M11) |
| `:gpu-renderer` can consume? | Yes — for route planning and diagnostics |
| `:gpu-renderer` can NOT do | GPU composite rendering of COLRv0 color glyphs (no proof) |

### M34-003 — Variable Fonts

| Artifact claimed missing | Reality |
|--------------------------|---------|
| fvar/gvar parsing | Fully implemented in text stack SFNT parsing |
| GPU handoff type | `TypefaceID` carries variation coordinates; `GPUGlyphRunDescriptor.typefaceID` flows through |
| `:gpu-renderer` can consume? | Yes — variation-resolved identity available via TypefaceID |
| `:gpu-renderer` can NOT do | CFF2 variable outlines (gated on M4 CFF2 scaler delivery) |

### M34-004 — Complex Shaping/BiDi

| Artifact claimed missing | Reality |
|--------------------------|---------|
| Shaping engine | `BasicOpenTypeShapingEngine` fully wired: segmentation → script runs → bidi → cmap → GSUB → GPOS → clusters |
| BiDi | `DefaultBidiResolver` — full UAX #9 pipeline with explicit controls, weak/neutral resolution, run building |
| GPU handoff type | `GPUGlyphRunDescriptor` carries bidiLevel, script, glyphIDs, advances, offsets |
| `:gpu-renderer` can consume? | Yes — Latin/Greek/Cyrillic/Hebrew shaped runs already route through GPU |
| `:gpu-renderer` can NOT do | Arabic/Devanagari/Thai/CJK fixtures not yet validated (M6 per-script gates open) |

## Cross-cutting limitation

All `TextGPUArtifactDescriptor` entries have `productActivation = false` — types exist but no GPU rendering proof exists for color fonts, variable fonts, or complex shaping.

## Recommendation

**Option A: Unblock with reduced scope.** Change claim impact from `DependencyGated` to `TargetNative`, scope to:

- M34-002: Consume `ColorGlyphPlanRef` for route planning + diagnostics. No claim of GPU composite color glyph rendering.
- M34-003: Validate that variable font glyphs route through `TypefaceID` on `GPUGlyphRunDescriptor`. No claim of CFF2 geometry.
- M34-004: Verify shaped runs (Latin minimum) pass through GPU route with BiDi ordering respected. No claim of Arabic/Devanagari/CJK.

**Option B: Keep blocked.** Wait for M10/M11 GPU rendering evidence + M6 per-script fixture gates to close.

## Source Files

- `.upstream/specs/pure-kotlin-text/05-color-fonts-bitmap-svg-emoji.md`
- `.upstream/specs/pure-kotlin-text/06-gpu-renderer-handoff.md`
- `.upstream/specs/pure-kotlin-text/tickets/M10-color-fonts-emoji/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/M11-gpu-handoff/README.md`
- `.upstream/specs/pure-kotlin-text/tickets/M6-opentype-layout-shaping/README.md`
- `.upstream/specs/gpu-renderer/tickets/M34-text-breadth/`
