# M1 - Font Identity and Sources

## Goal

Make font source and typeface identity deterministic, dumpable, and independent from host state unless explicitly marked.

## Dependencies

M0 boundaries and diagnostics.

## Exit Criteria

- [ ] Font source and typeface identity are stable across repeated fixture runs.
- [ ] Source/typeface dumps are deterministic and reviewable.
- [ ] Fixture provenance is captured before parser support is promoted.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M1-001 - Complete `FontSourceID` provenance model](KFONT-M1-001-complete-fontsourceid-provenance-model.md) | `proposed` | `P0` | `tracked-gap` | `font-core` | `KFONT-M0-003`, `KFONT-M0-004` | - |
| [KFONT-M1-002 - Complete `TypefaceID` glyph-affecting identity](KFONT-M1-002-complete-typefaceid-glyph-affecting-identity.md) | `proposed` | `P0` | `tracked-gap` | `font-core` | `KFONT-M1-001` | `typeface` |
| [KFONT-M1-003 - Add deterministic source/typeface dumps](KFONT-M1-003-add-deterministic-source-typeface-dumps.md) | `proposed` | `P0` | `tracked-gap` | `validation` | `KFONT-M1-001`, `KFONT-M1-002` | - |
| [KFONT-M1-004 - Add bundled source fixture manifest](KFONT-M1-004-add-bundled-source-fixture-manifest.md) | `proposed` | `P1` | `fixture-gated` | `fixtures` | `KFONT-M1-001`, `KFONT-M1-003` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:core:test
```

## Non-Claims

- No fallback, shaping, or glyph cache support is claimed by identity alone.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.
