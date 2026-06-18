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
| [KFONT-M8-004 - Implement ellipsis and max-lines policy](KFONT-M8-004-implement-ellipsis-and-max-lines-policy.md) | `proposed` | `P1` | `tracked-gap` | `paragraph` | `KFONT-M8-002`, `KFONT-M8-003` | - |
| [KFONT-M8-005 - Implement selection and hit-test maps](KFONT-M8-005-implement-selection-and-hit-test-maps.md) | `review` | `P1` | `tracked-gap` | `paragraph` | `KFONT-M8-002`, `KFONT-M8-003` | - |
| [KFONT-M8-006 - Implement placeholder layout metrics](KFONT-M8-006-implement-placeholder-layout-metrics.md) | `review` | `P1` | `tracked-gap` | `paragraph` | `KFONT-M8-001`, `KFONT-M8-003` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*Paragraph*'
```

Required evidence for this milestone includes `paragraph-input.json`, `paragraph-shaping-requests.json`, `line-breaks.json`, `paragraph-layout.json`, `placeholder-layout.json`, and `hit-test-map.json` fixtures.

## Status Notes

- `KFONT-M8-001` is `done` with deterministic paragraph input contract evidence only: rich style fields, placeholder metadata, input hashing, Unicode version pinning, and bounded refusal diagnostics are now checked in without promoting downstream layout or rendering claims.
- `KFONT-M8-002` is `done` with deterministic `paragraph-shaping-requests.json` evidence, cluster-boundary widening diagnostics, fallback-unresolved refusals, and paragraph layout segment references only; line breaking, ellipsis policy, hit testing/selection, and placeholder layout metrics remain separate gates.
- `KFONT-M8-003` is `done` with deterministic `line-breaks.json` evidence, `softWrap` contract hashing, paragraph layout line-break diagnostics, and refusal-on-missing-Unicode-data behavior only; complete UAX #14 conformance and dictionary-based refinement remain explicit non-claims.
- `KFONT-M8-005` is in `review` with bounded selection/hit-test evidence only: `hit-test-map.json` now proves deterministic multi-line selection boxes, placeholder ID consumption including non-participating overflow geometry, combining-mark snapping, emoji cluster snapping, and finite out-of-bounds clamp behavior, while bidi visual ordering and explicit word/grapheme boundary query evidence remain separate gates.
- `KFONT-M8-006` remains in `review`, but its placeholder-consumer gate is now closed by `KFONT-M8-005`; only the placeholder/ellipsis conflict evidence owned by `KFONT-M8-004` remains before `done`.
- `KFONT-M8-004` remains the active ellipsis/max-lines gate for M8 closeout.

## Non-Claims

- M8 does not claim glyph artifact generation, atlas creation, or GPU text rendering.
- Skia Paragraph, browser layout, platform shaping, and native accessibility APIs may appear only in non-normative drift reports.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.
