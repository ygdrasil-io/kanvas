# KFONT-M1-001 Font Source ID Evidence

Date: 2026-06-15
Status: implemented, pending review.
Ticket: `.upstream/specs/pure-kotlin-text/tickets/M1-font-identity-sources/KFONT-M1-001-complete-fontsourceid-provenance-model.md`

## Scope

This slice adds deterministic source identity and provenance evidence only. It does not parse glyph outlines, shape text, build fallback families, create glyph artifacts, or claim GPU/font rendering support.

## Files

- `font/core/src/main/kotlin/org/graphiks/kanvas/font/FontCore.kt`
- `font/core/src/test/kotlin/org/graphiks/kanvas/font/FontSourceIdentityTest.kt`
- `reports/pure-kotlin-text/font-source.json`

## Evidence

- `FontSourceIdentityPreimage` records source kind, declared name, license, stable origin path when present, host-dependent marker, content SHA-256 and byte length when bytes are captured, face count, normalized table tags, parser generation, and stable diagnostics.
- `FontSourceID` is derived from the canonical preimage with a deterministic UUID backed by `kotlin.uuid.Uuid`.
- Target source kinds serialize as `BundledFontSource`, `GeneratedFixtureFontSource`, `UserDataFontSource`, `UserStreamFontSource`, `UserFileFontSource`, and `SystemScannedFontSource`, while existing `MEMORY`, `FILE`, `SYSTEM`, and `RESOURCE` callers remain compatible.
- `defaultFontSourceIdentityReport()` emits fixture-equivalent `font-source.json` evidence for bundled fixture, generated fixture, user-data, and host-dependent system-scanned sources; the checked-in `reports/pure-kotlin-text/font-source.json` is asserted against the generated report.
- Repeated preimage construction has deterministic value equality, hash code, canonical JSON, and `FontSourceID`.
- `SystemScannedFontSource` rows stay non-normative without captured bytes and carry `font.source.host-dependent`.
- Every report row has `claimPromotionAllowed=false`.

## TDD Evidence

Red result:

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*FontSource*'
```

Result: failed as expected at `:font:core:compileTestKotlin` because `fontSourceIdentityPreimage`, `fontSourceIdentityDiagnostic`, `defaultFontSourceIdentityReport`, and target source-kind evidence were not yet exposed.

Green result:

```bash
rtk ./gradlew --no-daemon :font:core:test --tests '*FontSource*'
```

Result: passed; `FontSourceIdentityTest` executed 6 focused tests successfully.

## Remaining Gate

KFONT-M1-001 is prerequisite identity evidence only. `TypefaceID`, bundled fixture manifest, SFNT parser facts, scaler behavior, shaping, fallback, glyph artifact plans, color/emoji handling, and GPU routes remain owned by later tickets.
