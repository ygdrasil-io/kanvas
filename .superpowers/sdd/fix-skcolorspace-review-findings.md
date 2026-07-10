# Fix SkColorSpace review finding 11

## Scope

Finding 11 was valid: `SkColorSpace` retained and exposed mutable
`SkcmsMatrix3x3` instances. This allowed callers to mutate an existing color
space, contaminate the sRGB and linear-sRGB singletons, and desynchronize the
stored matrix from the identity flags computed at construction time.

## TDD evidence

RED command:

```text
./gradlew :kanvas:test --tests org.skia.foundation.SkColorSpaceCompatTest
```

Before the production change, the three new regressions failed on mutated
matrix coefficients:

- `makeRGB snapshots its caller supplied matrix and identity flags`
- `toXYZD50 returns a defensive matrix copy and preserves identity flags`
- `toXYZD50 mutation cannot contaminate shared sRGB color spaces`

GREEN implementation:

- Added `SkcmsMatrix3x3.copy()` as an explicit deep copy of all nine values.
- `SkColorSpace` now snapshots `toXYZD50` during construction.
- The public `toXYZD50` getter returns a fresh deep copy on every access.
- `SkcmsMatrix3x3.vals` remains explicitly mutable; the API does not present a
  mutable array as if it were immutable.

The tests cover mutation through both ownership boundaries, singleton
isolation, and consistency of `isSRGB()`, `gammaCloseToSRGB()`, and
`gammaIsLinear()`.

## Verification

```text
./gradlew :math:jvmTest :kanvas:test
git diff --check
```

Both test tasks completed successfully and the diff check reported no errors.
The staged scope is limited to the two production files, the related Kanvas
test, and this report. Concurrent changes under `codec/` were left untouched.
