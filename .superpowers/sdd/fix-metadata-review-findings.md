# PNG metadata review findings 14, 15, 16

## Scope

- Production: `codec/png/src/main/kotlin/org/graphiks/kanvas/codec/png/PngMetadata.kt`
- Contract documentation: `codec/png/src/main/kotlin/org/graphiks/kanvas/codec/png/PngDocument.kt`
- Tests: `codec/png/src/test/kotlin/org/graphiks/kanvas/codec/png/PngDocumentTest.kt`
- Finding 14 is a KDoc clarification, not a behavioral projection change.

## TDD evidence

The regression-test diff was applied to a temporary clone of `HEAD`, without
the production changes, then the focused suite was run:

```text
./gradlew :codec:png:test --tests org.graphiks.kanvas.codec.png.PngDocumentTest
42 tests completed, 2 failed
```

The two expected RED failures were:

- `iTXt rejects malformed BCP47 language tags with stable diagnostic`
- `mDCV rejects non normative primary order with stable diagnostic`

With the production changes present, the same focused suite passed all 42
tests.

## Implementation

- `iTXt` language tags now pass an RFC 5646 syntax parser after the existing
  ASCII check. Empty language tags remain accepted as the PNG representation
  of an unspecified language. The parser supports regular language tags,
  private-use tags, extensions, and the closed grandfathered production from
  RFC 5646. Empty subtags, leading/trailing hyphens, bare singletons, duplicate
  extension singletons, and duplicate variants are refused with
  `png.metadata.iTXt.language.invalid`.
- `mDCV` now checks the PNG Third Edition primary order before exposing typed
  metadata: greatest `x`, then greatest `y` among the remaining primaries,
  then the final primary. Invalid permutations are refused with
  `png.metadata.mDCV.primaries.order`.
- `PngDocument.metadata` now documents the three write impacts explicitly:
  `NONE` describes source metadata, `ANCILLARY` describes projected output,
  and `CRITICAL` emits no output and therefore does not project pending edits.

## IANA registry limit

This change does **not** claim complete BCP 47 validity or IANA Language
Subtag Registry validation. The repository has no versioned IANA registry
snapshot for this parser, so language, extlang, script, region, and variant
subtags are checked against RFC 5646 syntax only, not against registry
membership, deprecation, or preferred-value data. The grandfathered list is
the closed production copied from RFC 5646 section 2.1; it is not presented as
an IANA registry snapshot.

Complete registry-aware validation remains separate work and requires a
reviewed, versioned registry source plus update policy and tests.

## References

- [PNG Third Edition section 11.3.3.4, iTXt](https://www.w3.org/TR/2025/REC-png-3-20250624/#11iTXt)
- [PNG Third Edition section 11.3.2.7, mDCV](https://www.w3.org/TR/2025/REC-png-3-20250624/#11mDCV)
- [RFC 5646](https://www.rfc-editor.org/rfc/rfc5646.html)

## Verification

- Focused `PngDocumentTest`: pass, 42/42.
- Full `:codec:png:test`: pass.
- `git diff --check`: pending final scope check.
