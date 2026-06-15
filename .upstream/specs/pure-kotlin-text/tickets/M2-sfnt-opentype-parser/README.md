# M2 - SFNT/OpenType Parser

## Goal

Harden SFNT, TTC, `cmap`, and table fact parsing with bounded reads, deterministic dumps, and stable diagnostics.

## Dependencies

M1 deterministic identity and fixture manifest.

## Exit Criteria

- [ ] Single-face and collection fonts parse through one bounded request/result contract.
- [ ] Required and optional table failures have stable diagnostics and manifest-backed malformed fixtures.
- [ ] `cmap-map.json` covers formats 12, 4, 14, 6, 0, missing code point behavior, and unsupported-format refusal.
- [ ] `sfnt-directory.json` and `sfnt-tables.json` are deterministic and do not imply scaler, shaping, color, or GPU support.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M2-001 - Normalize SFNT/TTC parser entry points](KFONT-M2-001-normalize-sfnt-ttc-parser-entry-points.md) | `review` | `P0` | `tracked-gap` | `font-sfnt` | `KFONT-M1-001`, `KFONT-M1-004` | - |
| [KFONT-M2-002 - Add bounded table directory diagnostics](KFONT-M2-002-add-bounded-table-directory-diagnostics.md) | `review` | `P0` | `tracked-gap` | `font-sfnt` | `KFONT-M0-004`, `KFONT-M2-001` | - |
| [KFONT-M2-003 - Complete cmap format coverage](KFONT-M2-003-complete-cmap-format-coverage.md) | `proposed` | `P0` | `tracked-gap` | `font-sfnt` | `KFONT-M2-001`, `KFONT-M2-002` | - |
| [KFONT-M2-004 - Add OpenType table fact dumps](KFONT-M2-004-add-opentype-table-fact-dumps.md) | `proposed` | `P0` | `tracked-gap` | `validation` | `KFONT-M1-003`, `KFONT-M2-001`, `KFONT-M2-003` | - |
| [KFONT-M2-005 - Add malformed SFNT fixture suite](KFONT-M2-005-add-malformed-sfnt-fixture-suite.md) | `proposed` | `P0` | `fixture-gated` | `fixtures` | `KFONT-M2-002`, `KFONT-M2-004` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:sfnt:test --tests '*SFNTParser*' --tests '*TTC*' --tests '*TableDirectory*' --tests '*CMap*' --tests '*TableFactDump*' --tests '*MalformedSFNT*'
```

## Non-Claims

- Metadata parsing does not claim glyph rendering, shaping, color glyph, paragraph, fallback, or GPU support.
- Table presence does not mean table payload support unless a later ticket supplies fixture, dump, oracle, and diagnostic evidence.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.
