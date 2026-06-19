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
| [KFONT-M6-002 - Implement GSUB single/multiple/ligature lookups](KFONT-M6-002-implement-gsub-single-multiple-ligature-lookups.md) | `done` | `P0` | `tracked-gap` | `shaping` | `KFONT-M6-001`, `KFONT-M2-003` | - |
| [KFONT-M6-003 - Implement GSUB contextual lookups](KFONT-M6-003-implement-gsub-contextual-lookups.md) | `done` | `P0` | `tracked-gap` | `shaping` | `KFONT-M6-002` | - |
| [KFONT-M6-004 - Implement GPOS single/pair positioning](KFONT-M6-004-implement-gpos-single-pair-positioning.md) | `done` | `P0` | `tracked-gap` | `shaping` | `KFONT-M6-001`, `KFONT-M2-003` | - |
| [KFONT-M6-005 - Implement mark and cursive positioning](KFONT-M6-005-implement-mark-and-cursive-positioning.md) | `done` | `P0` | `tracked-gap` | `shaping` | `KFONT-M6-004`, `KFONT-M2-003` | - |
| [KFONT-M6-006 - Add script-specific default feature policy](KFONT-M6-006-add-script-specific-default-feature-policy.md) | `blocked` | `P0` | `tracked-gap` | `shaping` | `KFONT-M6-001`, `KFONT-M5-004` | - |
| [KFONT-M6-007 - Add Arabic shaping fixtures](KFONT-M6-007-add-arabic-shaping-fixtures.md) | `blocked` | `P0` | `fixture-gated` | `shaping` | `KFONT-M5-003`, `KFONT-M6-003`, `KFONT-M6-005`, `KFONT-M6-006` | - |
| [KFONT-M6-008 - Add Devanagari shaping fixtures](KFONT-M6-008-add-devanagari-shaping-fixtures.md) | `proposed` | `P0` | `fixture-gated` | `shaping` | `KFONT-M5-002`, `KFONT-M5-004`, `KFONT-M6-003`, `KFONT-M6-005`, `KFONT-M6-006` | - |
| [KFONT-M6-009 - Add Thai and CJK shaping boundaries](KFONT-M6-009-add-thai-and-cjk-shaping-boundaries.md) | `proposed` | `P1` | `fixture-gated` | `shaping` | `KFONT-M5-004`, `KFONT-M6-004`, `KFONT-M6-005`, `KFONT-M6-006` | - |
| [KFONT-M6-010 - Implement GSUB/GPOS extension, chaining and variation-adjustment lookups](KFONT-M6-010-implement-gsub-gpos-extension-chaining-and-variation-adjustment-lookups.md) | `proposed` | `P1` | `tracked-gap` | `shaping` | `KFONT-M6-003`, `KFONT-M6-004`, `KFONT-M6-005`, `KFONT-M4-005` | - |

## Validation Bundle

```bash
rtk ./gradlew --no-daemon :font:core:test --tests org.graphiks.kanvas.font.FontFixtureManifestTest
rtk ./gradlew --no-daemon :font:text:test --tests '*OpenTypeLayoutEngine*' --tests '*GsubBasic*' --tests '*GsubContext*'
rtk ./gradlew --no-daemon :font:text:test --tests '*GposPair*' --tests '*GposMark*' --tests '*FeaturePolicy*'
rtk ./gradlew --no-daemon :font:text:test --tests '*ArabicShaping*' --tests '*DevanagariShaping*' --tests '*ExtensionLookup*'
rtk python3 scripts/validate_font_fixture_assets.py
rtk python3 scripts/validate_pure_kotlin_text_dump_index.py
rtk python3 scripts/validate_pure_kotlin_text_fixture_manifest.py
rtk git diff --check
```

## Current Slice Notes

- 2026-06-18 bounded fixture closeout: `KFONT-M6-002` and `KFONT-M6-004` now land reviewed synthetic Latin fixtures, provenance, and promoted dumps while retaining separate M6-001 contract-only goldens.
- `KFONT-M6-002` is `done` on reviewed GSUB fixture provenance plus promoted `gsub-trace.json` / `shaped-glyph-run.json` evidence beyond the current M6-001 contract goldens.
- `KFONT-M6-003` is `done` on reviewed GSUB contextual fixture provenance, refreshed `gsub-trace.json` / `shaped-glyph-run.json` evidence, and remediated review findings around format 2 coverage/subtable handling plus nested-only lookup preservation and nested lookup stability, while keeping mixed-format contextual lookups and acyclic deep re-entry budgets as explicit non-claims.
- `KFONT-M6-004` is `done` on reviewed GPOS fixture provenance plus promoted `gpos-trace.json` / `shaped-glyph-run.json` evidence and layout-contract malformed/refusal diagnostics.
- `KFONT-M6-005` is `done` on bounded mark/cursive parser/runtime support, checked-in reviewed fixture provenance, refreshed `gpos-trace.json` / `shaped-glyph-run.json` evidence, refusal-only reviewed mono-codepoint ligature evidence, and post-review regressions for ambiguous ligature-component refusal, RTL cursive logical ranges, zero-advance cursive matches, GSUB cluster preservation under mark/cursive-capable typefaces, and the bounded `kern` pair-overflow diagnostic that the reviewed cursive fixture now surfaces instead of masking.
- `KFONT-M6-006` is now `blocked` after independent review: GSUB, the bounded `kern`-routed GPOS single subset, GPOS anchor, and pair-kerning paths now honor the resolved policy, Arabic defaults explicitly include `curs`, portable OpenType `drawString` non-enablement evidence is attached, and the remaining gate is only the per-script shaping fixture families.
- `KFONT-M6-007` is now `blocked` after the bounded Arabic evidence wave landed: `ArabicShapingFixtureTest`, `arabic-gsub-trace.json`, `arabic-gpos-trace.json`, `arabic-shaped-glyph-run.json`, `arabic-shaping-plan.json`, and `arabic-shaping-report.json` now prove bounded vendored-font joining/mark rows plus generic `gdef-required` and paragraph-bidi refusals, while positive `lam-alef`, vendored positive cursive, and Arabic-specific refusal fixtures/codes remain explicit gates.

## Current Blockers

- 2026-06-18 audit: `KFONT-M6-006` remains blocked by absent per-script shaping fixture families from `KFONT-M6-007`, `KFONT-M6-008`, and `KFONT-M6-009`.
- 2026-06-19 audit: `KFONT-M6-007` remains blocked on positive `lam-alef`, vendored positive cursive, and Arabic-specific refusal fixtures/codes, while `KFONT-M6-008`, `KFONT-M6-009`, and `KFONT-M6-010` remain gated by their own named fixture families, required-script rows, or advanced lookup dependencies.

## Non-Claims

- Paragraph layout, color emoji rendering, and implicit complex shaping in drawString remain separate.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.
