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
| [KFONT-M6-002 - Implement GSUB single/multiple/ligature lookups](KFONT-M6-002-implement-gsub-single-multiple-ligature-lookups.md) | `blocked` | `P0` | `tracked-gap` | `shaping` | `KFONT-M6-001`, `KFONT-M2-003` | - |
| [KFONT-M6-003 - Implement GSUB contextual lookups](KFONT-M6-003-implement-gsub-contextual-lookups.md) | `proposed` | `P0` | `tracked-gap` | `shaping` | `KFONT-M6-002` | - |
| [KFONT-M6-004 - Implement GPOS single/pair positioning](KFONT-M6-004-implement-gpos-single-pair-positioning.md) | `blocked` | `P0` | `tracked-gap` | `shaping` | `KFONT-M6-001`, `KFONT-M2-003` | - |
| [KFONT-M6-005 - Implement mark and cursive positioning](KFONT-M6-005-implement-mark-and-cursive-positioning.md) | `proposed` | `P0` | `tracked-gap` | `shaping` | `KFONT-M6-004`, `KFONT-M2-003` | - |
| [KFONT-M6-006 - Add script-specific default feature policy](KFONT-M6-006-add-script-specific-default-feature-policy.md) | `blocked` | `P0` | `tracked-gap` | `shaping` | `KFONT-M6-001`, `KFONT-M5-004` | - |
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

- 2026-06-18 asset audit keeps the merged bounded slices for `KFONT-M6-002`, `KFONT-M6-004`, and `KFONT-M6-006` as accepted prerequisite evidence, but reclassifies the tickets themselves to `blocked` because their remaining gates are now clearly dependency/fixture/runtime-gated rather than active review work.
- `KFONT-M6-002` is blocked on reviewed GSUB fixture provenance plus promoted `gsub-trace.json` / `shaped-glyph-run.json` evidence beyond the current M6-001 contract goldens, with no reviewed simple LookupType 2 multiple-substitution font fixture yet identified in-repo.
- `KFONT-M6-004` is blocked on reviewed GPOS fixture provenance plus promoted `gpos-trace.json` / `shaped-glyph-run.json` evidence and layout-contract malformed/refusal diagnostics, with no reviewed simple LookupType 1 single-positioning fixture yet identified in-repo.
- `KFONT-M6-006` is blocked on per-script shaping fixture families, runtime adoption of `ResolvedFeatureSet`, and explicit `drawString` non-enablement evidence.

## Current Blockers

- 2026-06-18 asset audit: `KFONT-M6-002` still depends on absent `gsub-single-substitution.otf`, `gsub-multiple-substitution.otf`, `gsub-ligature-fi.otf`, `gsub-coverage-malformed.otf`, and `gsub-ligature-bad-component.otf`, plus promoted `gsub-trace.json` / `shaped-glyph-run.json` evidence. `Source Serif 4` covers real ligature and single-substitution candidates under `SIL-OFL-1.1`, but no reviewed simple LookupType 2 multiple-substitution fixture is identified yet.
- 2026-06-18 asset audit: `KFONT-M6-004` still depends on absent `gpos-single-adjustment.otf`, `gpos-pair-format1-kerning.otf`, `gpos-pair-format2-class.otf`, `gpos-valueformat-malformed.otf`, and `gpos-pair-out-of-range.otf`, plus promoted `gpos-trace.json` / `shaped-glyph-run.json` evidence. `Source Serif 4` and `unicode-org/text-rendering-tests` provide real pair-positioning candidates, but no reviewed simple LookupType 1 single-positioning fixture is identified yet.
- 2026-06-18 asset audit: `KFONT-M6-006` still depends on absent per-script shaping fixture families from `KFONT-M6-007`, `KFONT-M6-008`, and `KFONT-M6-009`, plus runtime adoption of `ResolvedFeatureSet` and explicit `drawString` non-enablement evidence.
- 2026-06-18 audit: `KFONT-M6-003`, `KFONT-M6-005`, `KFONT-M6-007`, `KFONT-M6-008`, `KFONT-M6-009`, and `KFONT-M6-010` remain gated by the blocked base slices above plus their own named fixture families.

## Non-Claims

- Paragraph layout, color emoji rendering, and implicit complex shaping in drawString remain separate.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.
