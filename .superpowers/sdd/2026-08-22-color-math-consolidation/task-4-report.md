# Task 4 report — colour-matrix facade and codec boundaries

## RED

Updated the public-facing Skia compatibility and codec test declarations first
to require `ColorMatrix3x3F32`. The mandated dependent-test command failed at
`:kanvas:compileKotlin` as expected: `SkCodecCompat`, `SkICC`, and
`SkcmsCompat` still exposed `Matrix3x3F32` and attempted to use the removed
`iccGet` extension.

## GREEN

Migrated the colour-space boundaries to `ColorMatrix3x3F32`: Skia ICC/profile
facades, named gamuts, codec gamut classification, PNG cHRM profile creation,
and the integration comparison RGB-to-XYZ route. `SkEncodedOrigin.toMatrix`
continues to use `Matrix3x3F32`, because it is a homogeneous geometric image
orientation transform.

## Verification

- `./gradlew :kanvas:test --tests org.skia.foundation.SkColorSpaceCompatTest --tests org.skia.foundation.SkICCTest --tests org.skia.foundation.skcms.SkcmsCompatTest --no-daemon` — PASS.
- `./gradlew :kanvas:test :codec:api:test :codec:png:test :codec:bmp:test :codec:jpeg:test :codec:webp:test :integration-tests:test-utils:test --no-daemon` — PASS.
- `git diff --check` — PASS.

## Commit and files

Commit SHA: recorded in the task delivery after this report is committed.

Changed production files: `SkCodecCompat.kt`, `SkICC.kt`, `SkcmsCompat.kt`,
`KanvasCodec.kt`, `PngCodec.kt`, and `ComparisonUtils.kt`. Changed tests:
Skia colour-space/ICC/skcms tests plus the codec API and PNG encoder colour
matrix fixtures.

## Limits and risks

No compatibility bridge was added; downstream source that supplied a geometry
matrix to a colour-space API must migrate to `ColorMatrix3x3F32`. The only
remaining scoped `Matrix3x3F32` import is the intentional geometric
orientation matrix in `SkEncodedOrigin`.
