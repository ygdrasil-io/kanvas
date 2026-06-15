# M1 - Font Identity and Sources

## Goal

Make font source and typeface identity deterministic, dumpable, and independent from host state unless explicitly marked.

## Dependencies

M0 module boundaries, CI coverage, and diagnostic taxonomy.

## Exit Criteria

- [ ] `FontSourceID` covers provenance, content hash, host dependence, face count, table tags, and parser generation.
- [ ] `TypefaceID` changes for collection index, variation coordinates, palette, selected `cmap`, scaler mode, and source bytes.
- [ ] `font-source.json` and `typeface-id.json` are deterministic across repeated fixture runs.
- [ ] Fixture provenance is captured before parser or scaler support is promoted.

## Tickets

| Ticket | Status | Priority | Claim Impact | Owner Area | Depends On | Legacy Gate |
|---|---|---|---|---|---|---|
| [KFONT-M1-001 - Complete `FontSourceID` provenance model](KFONT-M1-001-complete-fontsourceid-provenance-model.md) | `review` | `P0` | `tracked-gap` | `font-core` | `KFONT-M0-003`, `KFONT-M0-004` | - |
| [KFONT-M1-002 - Complete `TypefaceID` glyph-affecting identity](KFONT-M1-002-complete-typefaceid-glyph-affecting-identity.md) | `review` | `P0` | `tracked-gap` | `font-core` | `KFONT-M1-001` | `typeface` |
| [KFONT-M1-003 - Add deterministic source/typeface dumps](KFONT-M1-003-add-deterministic-source-typeface-dumps.md) | `review` | `P0` | `tracked-gap` | `validation` | `KFONT-M1-001`, `KFONT-M1-002` | - |
| [KFONT-M1-004 - Add bundled source fixture manifest](KFONT-M1-004-add-bundled-source-fixture-manifest.md) | `review` | `P1` | `fixture-gated` | `fixtures` | `KFONT-M1-001`, `KFONT-M1-003` | - |

## Validation Bundle

```bash
rtk git diff --check
rtk ./gradlew --no-daemon :font:core:test --tests '*FontSource*' --tests '*Typeface*' --tests '*IdentityDump*' --tests '*FixtureManifest*'
```

## Non-Claims

- Source and typeface identity do not claim fallback, shaping, glyph scaling, glyph cache, or GPU support.
- Host system fonts remain non-normative unless their bytes and provenance are captured as fixtures.

## Status Update Rule

When a ticket status changes, update the ticket front matter, this table, and `../STATUS.md` in the same change.
