# M8 - Paragraph Engine

## Goal

Build the pure Kotlin paragraph layer that turns immutable rich text input into shaped requests, UAX #14 line breaks, fitted lines, ellipsis state, placeholder boxes, selection geometry, hit-test maps, and deterministic diagnostics.

## Dependencies

M5 supplies Unicode grapheme, bidi, script, and line-break data. M6 supplies shaping contracts and shaped glyph runs. M7 supplies deterministic fallback runs. M8 does not parse fonts and does not produce GPU artifacts.

## Exit Criteria

- [ ] `ParagraphInput`, `ParagraphShapingRequest`, `LineBreakMap`, `ParagraphLayoutResult`, `PlaceholderBox`, and `HitTestMap` are dumpable value-object contracts.
- [ ] Rich style spans, fallback splits, bidi lines, UAX #14 breaks, ellipsis, placeholders, selection, and hit testing have deterministic fixtures.
- [ ] Invalid style, line-break, ellipsis, placeholder, selection, and hit-test cases emit stable `text.paragraph.*` diagnostics.
- [ ] Paragraph output can be consumed by M9 glyph artifact planning without re-shaping or re-parsing fonts.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M8-001 - Expand `TextStyle` and paragraph style contracts](KFONT-M8-001-expand-textstyle-and-paragraph-style-contracts.md) | `done` | `P0` | `tracked-gap` | `paragraph` | `KFONT-M5-001`, `KFONT-M6-001` | - |
| [KFONT-M8-002 - Implement multi-style shaping segmentation](KFONT-M8-002-implement-multi-style-shaping-segmentation.md) | `done` | `P0` | `tracked-gap` | `paragraph` | `KFONT-M8-001`, `KFONT-M6-001`, `KFONT-M7-003` | - |
| [KFONT-M8-003 - Implement UAX #14 line breaker](KFONT-M8-003-implement-uax-14-line-breaker.md) | `done` | `P0` | `tracked-gap` | `paragraph` | `KFONT-M5-001`, `KFONT-M8-002` | - |
| [KFONT-M8-004 - Implement ellipsis and max-lines policy](KFONT-M8-004-implement-ellipsis-and-max-lines-policy.md) | `done` | `P1` | `tracked-gap` | `paragraph` | `KFONT-M8-002`, `KFONT-M8-003` | - |
| [KFONT-M8-005 - Implement selection and hit-test maps](KFONT-M8-005-implement-selection-and-hit-test-maps.md) | `review` | `P1` | `tracked-gap` | `paragraph` | `KFONT-M8-002`, `KFONT-M8-003` | - |
| [KFONT-M8-006 - Implement placeholder layout metrics](KFONT-M8-006-implement-placeholder-layout-metrics.md) | `done` | `P1` | `tracked-gap` | `paragraph` | `KFONT-M8-001`, `KFONT-M8-003` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*Paragraph*'
```

Required evidence for this milestone includes `paragraph-input.json`, `paragraph-shaping-requests.json`, `line-breaks.json`, `paragraph-layout.json`, `placeholder-layout.json`, and `hit-test-map.json` fixtures.

## Status Notes

- `KFONT-M8-001` is `done` with deterministic paragraph input contract evidence only: rich style fields, placeholder metadata, input hashing, Unicode version pinning, and bounded refusal diagnostics are now checked in without promoting downstream layout or rendering claims.
- `KFONT-M8-002` is `done` with bounded segmentation evidence only: paragraph shaping requests now split/coalesce by shaping-affecting style facts, preserve placeholder exclusion, emit `text.paragraph.cluster-boundary-violation`, and pin `segmentRefs` plus `paragraph-shaping-requests.json` without promoting complete fallback policy, bidi visual ordering, or paragraph parity claims.
- `KFONT-M8-003` is `done` with bounded UAX #14 evidence only: line-break maps now pin hard newlines, spaces, hyphen punctuation, CJK adjacency, combining-mark clusters, mixed LTR/RTL spacing, emoji ZWJ clusters, and Thai locale-refinement diagnostics without claiming full dictionary-based refinement, complete UAX #14 conformance, or paragraph parity.
- `KFONT-M8-004` is `done` with bounded end-ellipsis evidence only: `ParagraphLayoutResult` now records `isEllipsized`, `visibleTextRange`, `truncatedTextRange`, and ellipsis glyph provenance, while refusing missing-glyph, no-room, and placeholder-conflict cases without claiming head/middle truncation, full bidi visual-order parity, or placeholder layout parity.
- `KFONT-M8-006` is `done` with bounded placeholder geometry evidence only: `PlaceholderStyle` now records `participatesInLineHeight`, layout emits deterministic `PlaceholderBox` geometry and `placeholder-layout.json`, and shared paragraph dumps expose `placeholderBoxes` without promoting selection/hit-test behavior, full bidi visual-order parity, or placeholder rendering claims.
- `KFONT-M8-005` is `review` with bounded paragraph interaction evidence only: `ParagraphLayoutResult.buildHitTestMap(...)` now emits deterministic `caretStops`, `selectionBoxes`, `hitEntries`, `wordBoundaries`, and `graphemeBoundaries`, reuses shaped cluster advances for line-local geometry, and refuses grapheme-cut selection ranges plus non-finite points. Remaining gate: add reviewed mixed LTR/RTL plus mixed-style fixture coverage and explicit multi-run visual-order evidence before promoting `done`, full bidi visual-order parity, platform caret behavior parity, Skia Paragraph parity, or complete target support.

## Non-Claims

- M8 does not claim glyph artifact generation, atlas creation, or GPU text rendering.
- Skia Paragraph, browser layout, platform shaping, and native accessibility APIs may appear only in non-normative drift reports.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.
