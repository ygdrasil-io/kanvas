# M6 - OpenType Layout Shaping

## Goal

Implement the target OpenType Layout shaping contract, basic and advanced lookups, script policy, and required script evidence.

## Dependencies

M2 parser facts and M5 Unicode segmentation foundations.

## Exit Criteria

- [ ] Shaping contracts and stage dumps are stable.
- [ ] Required GSUB/GPOS lookup classes have positive and refusal evidence.
- [ ] Script support claims match fixture evidence and diagnostics.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M6-001 - Define `OpenTypeLayoutEngine` contract and dumps](KFONT-M6-001-define-opentypelayoutengine-contract-and-dumps.md) | `done` | `P0` | `tracked-gap` | `shaping` | `KFONT-M2-003`, `KFONT-M2-004`, `KFONT-M5-002`, `KFONT-M5-003`, `KFONT-M5-004` | - |
| [KFONT-M6-002 - Implement GSUB single/multiple/ligature lookups](KFONT-M6-002-implement-gsub-single-multiple-ligature-lookups.md) | `review` | `P0` | `tracked-gap` | `shaping` | `KFONT-M6-001`, `KFONT-M2-003` | - |
| [KFONT-M6-003 - Implement GSUB contextual lookups](KFONT-M6-003-implement-gsub-contextual-lookups.md) | `proposed` | `P0` | `tracked-gap` | `shaping` | `KFONT-M6-002` | - |
| [KFONT-M6-004 - Implement GPOS single/pair positioning](KFONT-M6-004-implement-gpos-single-pair-positioning.md) | `review` | `P0` | `tracked-gap` | `shaping` | `KFONT-M6-001`, `KFONT-M2-003` | - |
| [KFONT-M6-005 - Implement mark and cursive positioning](KFONT-M6-005-implement-mark-and-cursive-positioning.md) | `proposed` | `P0` | `tracked-gap` | `shaping` | `KFONT-M6-004`, `KFONT-M2-003` | - |
| [KFONT-M6-006 - Add script-specific default feature policy](KFONT-M6-006-add-script-specific-default-feature-policy.md) | `review` | `P0` | `tracked-gap` | `shaping` | `KFONT-M6-001`, `KFONT-M5-004` | - |
| [KFONT-M6-007 - Add Arabic shaping fixtures](KFONT-M6-007-add-arabic-shaping-fixtures.md) | `proposed` | `P0` | `fixture-gated` | `shaping` | `KFONT-M5-003`, `KFONT-M6-003`, `KFONT-M6-005`, `KFONT-M6-006` | - |
| [KFONT-M6-008 - Add Devanagari shaping fixtures](KFONT-M6-008-add-devanagari-shaping-fixtures.md) | `proposed` | `P0` | `fixture-gated` | `shaping` | `KFONT-M5-002`, `KFONT-M5-004`, `KFONT-M6-003`, `KFONT-M6-005`, `KFONT-M6-006` | - |
| [KFONT-M6-009 - Add Thai and CJK shaping boundaries](KFONT-M6-009-add-thai-and-cjk-shaping-boundaries.md) | `proposed` | `P1` | `fixture-gated` | `shaping` | `KFONT-M5-004`, `KFONT-M6-004`, `KFONT-M6-005`, `KFONT-M6-006` | - |
| [KFONT-M6-010 - Implement GSUB/GPOS extension, chaining and variation-adjustment lookups](KFONT-M6-010-implement-gsub-gpos-extension-chaining-and-variation-adjustment-lookups.md) | `proposed` | `P1` | `tracked-gap` | `shaping` | `KFONT-M6-003`, `KFONT-M6-004`, `KFONT-M6-005`, `KFONT-M4-005` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*OpenTypeLayoutEngine*' --tests '*GsubBasic*' --tests '*GsubContext*'
rtk ./gradlew --no-daemon :font:text:test --tests '*GposPair*' --tests '*GposMark*' --tests '*FeaturePolicy*'
rtk ./gradlew --no-daemon :font:text:test --tests '*ArabicShaping*' --tests '*DevanagariShaping*' --tests '*ExtensionLookup*'
```

## Current Slice Notes

- `KFONT-M6-004` is in `review` for a bounded parser/runtime slice: `font/sfnt` now exposes GPOS single adjustments plus pair value records, and `BasicOpenTypeShapingEngine` applies the validated `xPlacement`/`yPlacement`/`xAdvance` subset.
- The milestone still has no full GPOS support claim. Remaining gates for `KFONT-M6-004` are fixture-backed `gpos-trace.json`, `shaped-glyph-run.json`, and layout-contract level malformed/refusal evidence.

## Non-Claims

- Paragraph layout, color emoji rendering, and implicit complex shaping in drawString remain separate.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.
