# Classifier mutable state and codec documentation review

## Scope

- Snapshot the mutable public `SkNamedGamut` matrices before retaining them in
  the codec classifier.
- Add a regression test that mutates and restores a public named gamut while
  proving classifier behavior remains isolated and stable.
- Limit the documented PNG `iCCP` encode contract to serializable RGB SDR
  matrix/TRC color spaces and document explicit refusal categories.

## Root cause

`allowedGamutMatrices` retained its `canonical` argument directly. Those
arguments are the public, mutable matrices exposed by `SkNamedGamut`, so a
caller could mutate a matrix after `NAMED_GAMUTS` initialization and alter the
classifier's accepted values. The serialized variants were independent, but
the canonical classifier entry still aliased public mutable state.

## TDD evidence

### RED

Added
`KanvasCodecColorSpaceTest.named gamut classification is isolated from public matrix mutation`.
The test initializes classification, mutates `SkNamedGamut.kSRGB`, expects the
mutated gamut to be refused, restores the public matrix in `finally`, and
checks the original source before and after restoration.

Command:

```text
./gradlew :codec:api:test --tests 'org.graphiks.kanvas.codec.KanvasCodecColorSpaceTest.named gamut classification is isolated from public matrix mutation'
```

Observed before the production change: **FAIL**, because the expected
`UnsupportedKanvasColorSpaceException` was not thrown.

### GREEN

`allowedGamutMatrices` now takes one deep `SkcmsMatrix3x3.copy()` snapshot and
uses it both as the canonical accepted matrix and as the input for serialized
sRGB/linear variants. The classifier no longer retains references to public
named-gamut matrices.

The same targeted command then completed with **BUILD SUCCESSFUL** and the new
test **PASSED**.

## Documentation

The PNG encode row in `SUPPORTED_CODECS.md` now states that non-sRGB `iCCP`
encode supports only supported RGB SDR matrix/TRC color spaces serializable as
ICC. HDR, LUT-based, unsupported, and otherwise non-serializable color spaces
are documented as refused before output through `null`/`false`.

## Verification

```text
./gradlew :codec:api:test checkSupportedCodecsDoc
git diff --check
```

Result: **PASS**. All 22 codec API tests passed, `checkSupportedCodecsDoc`
passed, and `git diff --check` reported no errors.

## Diff review

Task changes are limited to:

- `codec/api/src/main/kotlin/org/graphiks/kanvas/codec/KanvasCodec.kt`
- `codec/api/src/test/kotlin/org/graphiks/kanvas/codec/KanvasCodecColorSpaceTest.kt`
- `SUPPORTED_CODECS.md`
- `.superpowers/sdd/fix-classifier-doc-review.md`

Pre-existing concurrent changes under `codec/png/` were not modified or
reverted and are excluded from this task's commit.
