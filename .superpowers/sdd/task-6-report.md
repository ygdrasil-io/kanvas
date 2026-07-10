# Task 6 Report - Skia Color Compatibility Backed by ICC Engine

## Scope

Task 6 removes the synthetic ICC selector behavior from the Kanvas facade and
backs it with `:color-management`. The follow-up review corrections extend the
approved scope to BMP, JPEG, PNG, and WebP codec production/tests only where ICC
metadata is surfaced. No codec pixel conversion or native color-management path
was added.

The pre-existing modification to `.superpowers/sdd/task-4-report.md` was left
untouched and is excluded from both Task 6 commits.

## ICC matrix/TRC writer

`IccProfileWriter.writeMatrixTrc(ColorProfile)` writes supported RGB SDR
matrix/TRC profiles that reparse through `IccProfileParser`.

- The output is display-class RGB/XYZ-D50 ICC v4 with `acsp`, deterministic
  header data, zero reserved fields, exact profile size, and contiguous,
  four-byte-aligned `desc`, `cprt`, `wtpt`, `rXYZ`, `gXYZ`, `bXYZ`, `rTRC`,
  `gTRC`, and `bTRC` tags.
- Description/copyright use `mluc`; primaries and white use `XYZ `; all three
  curves use `para` selector 4 in `g,a,b,c,d,e,f` order.
- Non-negative PCSXYZ matrices emit ICC v4.3. A quantized matrix containing a
  negative PCSXYZ component, including Display P3 and Rec.2020, emits ICC v4.4.
- Each quantized matrix row must equal the fixed D50 header white. Differences
  of at most 64 signed 15.16 units are normalized into the largest primary
  coefficient; larger differences are refused. Identity is therefore rejected
  despite being invertible. The normalized matrix must remain finite and
  invertible.
- Signed 15.16 conversion rejects non-finite and out-of-range values. Curve
  quantization retains the bounded lower-slope adjustment needed to avoid a
  quantization-only downward jump, followed by parser-equivalent validation.
- Tests reparse sRGB, Display P3, Rec.2020, and linear Rec.2020, verify version
  semantics, exact quantized D50 row sums, semantic quantization bounds, tag
  layout, invalid identity, overflow/non-finite values, and singular matrices.

The writer still refuses GRAY, HDR, LUT, unsupported, incomplete, invalid-curve,
and otherwise unrepresentable profiles.

## Compatibility facade

- `:kanvas` exposes `:color-management` with `api(project(":color-management"))`
  because `SkcmsICCProfile` and `SkColorSpace` expose `ColorProfile`.
- `SkcmsICCProfile` retains the legacy public constructor, non-null `bytes`,
  `transferFn`, and `toXYZD50` getters, `component1/2/3`, `copy`,
  `copy$default`, default-constructor bridge, equality/hash, and data-class-like
  `toString`. Tests compile destructuring/copy source usage and invoke the exact
  binary bridges reflectively.
- Unsupported parsed profiles use non-null legacy sRGB transfer/matrix
  projections solely for ABI compatibility. `colorProfile`, `profileStatus`,
  and `profileRefusalCode` are authoritative and prevent those projections from
  being reported as supported sRGB.
- `skcmsParse` snapshots caller bytes before parser access, delegates to
  `IccProfileParser`, preserves the valid snapshot, and returns defensive copies
  from all byte getters. Mutation tests cover source bytes and returned arrays.
- Public `fromColorProfile(ColorProfile)` no longer accepts arbitrary original
  bytes. It rewrites representable profiles with `IccProfileWriter`; parsed-byte
  preservation is an internal factory used only after successful parsing.
- `SkColorSpace.make` remains a nullable strict adapter for RGB non-HDR
  matrix/TRC profiles. LUT, HDR, GRAY, incomplete, and explicit unsupported
  profiles are refused. `makeProfileAware` lets codecs retain those profiles
  with explicit stable refusal codes and `isSRGB() == false`.
- `SkICC.WriteToICC` continues to delegate to the public writer and emits valid,
  reparsable bytes rather than synthetic selector envelopes.

## Codec coherence

BMP, JPEG, PNG, and WebP now derive reported color state from a parsed embedded
ICC profile:

- Display P3 remains supported, non-sRGB, and preserves its matrix semantics in
  all four codecs.
- A valid GRAY profile remains available from `getICCProfile()` but produces
  `kUnsupported`, `icc.gray.unsupported`, and `isSRGB() == false` in all four.
- Invalid/non-parseable embedded profile bytes retain each codec's existing
  best-effort behavior and do not become a supported profile.
- PNG validates the `iCCP` data color space against IHDR. RGB profiles are
  accepted for truecolor/indexed PNG, GRAY profiles for grayscale PNG, and both
  RGB-in-gray and GRAY-in-RGB mismatches reject the image. The positive P3 test
  is now a truecolor PNG rather than the former invalid grayscale/RGB pairing.

Pixel reconstruction remains unchanged; these changes describe source metadata
and explicit refusal, not a color transform.

## TDD and verification

- Review RED: `rtk ./gradlew :kanvas:test --no-daemon` failed in
  `:kanvas:compileTestKotlin` on the intentionally missing `componentN`, `copy`,
  non-null getters, internal parsed factory, and profile status APIs.
- Core GREEN: `:color-management:test :kanvas:test` passed after ABI, snapshot,
  factory, writer version, and D50 normalization changes. An intermediate writer
  run exposed the expected normalization tolerance mismatch; the documented
  64-unit semantic bound resolved it.
- Codec RED: the first combined BMP/JPEG/PNG/WebP run failed the new P3/GRAY
  state assertions and PNG color-model mismatch assertions against the old
  fallback behavior.
- Codec GREEN: all four codec suites passed after using `makeProfileAware` and
  adding PNG IHDR/ICC validation.
- Final fresh command:
  `rtk ./gradlew :color-management:test :kanvas:test :codec:bmp:test :codec:jpeg:test :codec:png:test :codec:webp:test --rerun-tasks --no-daemon`
  completed with `BUILD SUCCESSFUL` in 30 seconds and 71 executed tasks.
- Exact final results: color-management 138, Kanvas 316, BMP 31, JPEG 48,
  PNG 59, WebP 99; total 691 tests, zero failures.
- `rtk git diff --check` passed. Scoped production scans found no former
  selector-byte reads and no AWT, ImageIO, JNI, LCMS, or native CMM dependency.
- The full rebuild emitted only pre-existing compiler/Gradle deprecation
  warnings outside this change.

No independent subagent review tool was available. The self-review covered the
review document item by item, public/binary compatibility bridges, writer
quantization/version behavior, four-codec state propagation, PNG policy,
forbidden dependencies, changed-file ownership, and the complete test matrix.

## Deliberate limitations

- The writer supports one shared scalar transfer function on RGB SDR
  matrix/TRC profiles only. It does not write LUT, HDR, GRAY, sampled/per-channel
  curves, output-class, or device-link profiles.
- `SkColorSpace` is not a general ICC transform container. Unsupported profile
  metadata is retained with an explicit refusal; no fake transform or sRGB
  support claim is made.
- Legacy non-null transfer/matrix getters cannot express unsupported profile
  shape. Callers must inspect `colorProfile` or `profileStatus` before using
  those compatibility projections.
- The practical byte-race defense is snapshot-before-parse plus immutable
  storage and mutation tests. No parser hook was introduced solely to make a
  deterministic concurrent mutation schedule.
- No PNG color transform, CICP/HDR chunk parser, APNG/streaming work, encoder
  re-embedding policy change, AWT, ImageIO, JNI, LCMS, native CMM, SIMD, or GPU
  color-management path is included.
