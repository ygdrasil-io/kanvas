# Task 4 Report - Alpha-Safe Matrix/TRC Color Transforms

## Implementation

- Added `MatrixColorTransform`, a compiled scalar matrix/TRC plan. It caches
  defensive copies of the source-to-XYZ D50 matrix, the inverse destination
  matrix, source and destination transfer functions, and alpha mode.
- Matrix/TRC compilation now supports `OPAQUE`, `UNPREMULTIPLIED`, and
  `PREMULTIPLIED`. Premultiplied RGB is unpremultiplied only when alpha is
  nonzero, transformed, then re-premultiplied. Alpha storage is never written.
- P1 follow-up: finite nonzero premultiplied alpha divides each RGB component
  directly, so `Float.MIN_VALUE` cannot overflow through a cached reciprocal.
  Zero and non-finite premultiplied alpha store transparent black before any
  re-premultiplication while leaving alpha bits untouched.
- Matrix/TRC endpoints select the compiled matrix plan; Task 3 LUT/matrix
  endpoint composition remains unchanged. Nonidentical LUT composition with
  `PREMULTIPLIED` is explicitly refused as
  `color.alpha.premultiplied.unsupported`.
- Equal supported profiles continue to use the no-op plan, including
  premultiplied matrix profiles.

## Numeric Policy

- Source transfer decoding, linear RGB, XYZ D50 PCS, destination linear RGB,
  and transfer encoding are not clipped.
- Only final encoded destination RGB is clamped to `[0, 1]`. A non-finite
  final encoded value becomes `0`.
- A premultiplied pixel with zero or non-finite alpha writes transparent black
  without division or re-premultiplication. Its alpha is never written, so
  `NaN`, `+Infinity`, `-Infinity`, and signed zero preserve their raw bits.
- A finite nonzero premultiplied alpha divides individual source RGB
  components directly and is then used for re-premultiplication.
- Matrix inversion happens while compiling the transform, never while applying
  pixels.

## Tests and Verification

- Required focused RED:
  `rtk ./gradlew :color-management:test --tests '*ColorTransformContractTest' --rerun-tasks`
  failed with three expected premultiplied matrix failures before production
  code changed.
- P1 focused RED: after adding the subnormal and non-finite alpha regressions,
  the focused command failed with the expected transparent-black and
  finite-RGB assertions before production code changed.
- Focused GREEN: the same command passed with 20 contract tests.
- Full verification:
  `rtk ./gradlew :color-management:test --rerun-tasks` passed successfully.
- `rtk git diff --check` passed with no whitespace errors.
- Contract coverage includes P3-to-sRGB non-noop goldens, opaque and
  unpremultiplied alpha preservation for LUT and matrix routes,
  premultiplied conversion, zero/subnormal/non-finite alpha, matrix-plan
  defensive copies, premultiplied identity, and explicit LUT refusal.

## Limitations

- Premultiplied nonidentical LUT composition is deliberately not implemented;
  it has a stable typed refusal rather than a fallback or no-op.
- This remains the deterministic scalar Kotlin path. It does not add SIMD,
  AWT, ImageIO, JNI, LCMS, PNG, HDR, CICP, codec, or facade integration.

## Files Changed

- `color-management/src/main/kotlin/org/graphiks/kanvas/color/ColorTransform.kt`
- `color-management/src/main/kotlin/org/graphiks/kanvas/color/MatrixColorTransform.kt`
- `color-management/src/test/kotlin/org/graphiks/kanvas/color/ColorTransformContractTest.kt`

This report is ignored and is not part of the Task 4 commit.
