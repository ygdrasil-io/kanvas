# M8 - Paragraph Engine

## Goal

Build the paragraph style, segmentation, line breaking, ellipsis, hit testing, and placeholder contracts over the shaping stack.

## Dependencies

M5 Unicode, M6 shaping, and M7 fallback contracts.

## Exit Criteria

- [ ] Paragraph inputs, shaped runs, lines, boxes, and diagnostics are dumpable.
- [ ] Line breaks and truncation preserve cluster invariants.
- [ ] Selection, hit testing, and placeholders have deterministic layout evidence.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M8-001 - Expand `TextStyle` and paragraph style contracts](KFONT-M8-001-expand-textstyle-and-paragraph-style-contracts.md) | `proposed` | `P0` | `tracked-gap` | `paragraph` | `KFONT-M5-001`, `KFONT-M6-001` | - |
| [KFONT-M8-002 - Implement multi-style shaping segmentation](KFONT-M8-002-implement-multi-style-shaping-segmentation.md) | `proposed` | `P0` | `tracked-gap` | `paragraph` | `KFONT-M8-001`, `KFONT-M6-001`, `KFONT-M7-003` | - |
| [KFONT-M8-003 - Implement UAX #14 line breaker](KFONT-M8-003-implement-uax-14-line-breaker.md) | `proposed` | `P0` | `tracked-gap` | `paragraph` | `KFONT-M5-001`, `KFONT-M8-002` | - |
| [KFONT-M8-004 - Implement ellipsis and max-lines policy](KFONT-M8-004-implement-ellipsis-and-max-lines-policy.md) | `proposed` | `P1` | `tracked-gap` | `paragraph` | `KFONT-M8-002`, `KFONT-M8-003` | - |
| [KFONT-M8-005 - Implement selection and hit-test maps](KFONT-M8-005-implement-selection-and-hit-test-maps.md) | `proposed` | `P1` | `tracked-gap` | `paragraph` | `KFONT-M8-002`, `KFONT-M8-003` | - |
| [KFONT-M8-006 - Implement placeholder layout metrics](KFONT-M8-006-implement-placeholder-layout-metrics.md) | `proposed` | `P1` | `tracked-gap` | `paragraph` | `KFONT-M8-001`, `KFONT-M8-003` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test
```

## Non-Claims

- Paragraph layout does not claim GPU text rendering by itself.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.
