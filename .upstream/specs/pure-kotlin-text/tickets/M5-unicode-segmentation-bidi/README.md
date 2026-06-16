# M5 - Unicode Segmentation and Bidi

## Goal

Pin Unicode data and provide deterministic grapheme, bidi, script, and cluster safety foundations.

## Dependencies

M0 diagnostics and M2 cmap facts.

## Exit Criteria

- [x] Unicode data version is pinned and serialized.
- [ ] Grapheme, bidi, and script runs preserve cluster boundaries.
- [ ] Cluster safety regressions cover target shaping and emoji prerequisites.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M5-001 - Add pinned Unicode data generation](KFONT-M5-001-add-pinned-unicode-data-generation.md) | `done` | `P0` | `tracked-gap` | `unicode` | `KFONT-M0-004` | - |
| [KFONT-M5-002 - Replace basic grapheme segmenter](KFONT-M5-002-replace-basic-grapheme-segmenter.md) | `proposed` | `P0` | `tracked-gap` | `unicode` | `KFONT-M5-001` | - |
| [KFONT-M5-003 - Replace basic bidi resolver](KFONT-M5-003-replace-basic-bidi-resolver.md) | `proposed` | `P0` | `tracked-gap` | `unicode` | `KFONT-M5-001` | - |
| [KFONT-M5-004 - Add Script_Extensions itemizer](KFONT-M5-004-add-script-extensions-itemizer.md) | `proposed` | `P0` | `tracked-gap` | `unicode` | `KFONT-M5-001`, `KFONT-M5-002` | - |
| [KFONT-M5-005 - Add cluster safety regression suite](KFONT-M5-005-add-cluster-safety-regression-suite.md) | `proposed` | `P0` | `fixture-gated` | `unicode` | `KFONT-M5-002`, `KFONT-M5-003`, `KFONT-M5-004` | `scaledemoji` |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:text:test --tests '*UnicodeData*' --tests '*Grapheme*' --tests '*Bidi*'
rtk ./gradlew --no-daemon :font:text:test --tests '*ScriptItem*' --tests '*ClusterSafety*'
```

## Non-Claims

- This milestone does not implement GSUB/GPOS shaping or emoji rendering.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.
