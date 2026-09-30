# Task 1 — Rect hairline hard geometry (LINEAR)

## Implementation

- Added public math authority `rectHairlineCoverageBandsI32(deviceRectI32, clipI32)`.
  It constructs the integer inner border of `[left, top, right + 1, bottom + 1)`
  in I64, emits top/bottom before the non-corner side bands, clips each disjoint
  band independently, and narrows only after clipping.
- Added the closed W4d branch for hard, direct-solid `RECT / STROKE / width=0`
  draws with BUTT/MITER, `miter >= 2`, SrcOver, no effects, a finite nonzero
  axis-aligned transform and integral projected bounds. The original DrawNode,
  material/source occurrence, CTM coordinates and command index remain intact.
- W4d adapts the math-owned bands into one filled path geometry using the existing
  math preparation/budget ledger. It calculates target ∩ hard clip before geometry;
  a disjoint hard clip returns `Prepared.Empty` rather than inheriting the older
  null-as-no-clip scissor fallback.
- No encoded-domain propagation, W3 fan-out, source duplication, capability cap,
  proof/seal, GM, adapter, threshold, score or exclusion changes were made.

## Tests

- `RectHairlineCoverageI32Test.integerRingIsDisjointAndClippedWithoutInventingEdges`
  checks literal bands and all twelve expected lattice points for `(2,2,5,5)`.
- `RectHairlineCoverageI32Test.thinRingAndI32EdgesDoNotOverlapOrOverflow`
  checks a width-one Rect and a clipped `Int.MAX_VALUE` right edge without wrap.
- `W7RectHairlineSurfacePixelTest.integerRectHairlineCoversEveryExpectedPixel`
  uses the required literal 8×8 B/R oracle (blue `255,17,61,211`, red
  `255,239,51,73`) before `Surface` creation.
- The remaining public witnesses pin one-hit translucent corners, target and hard
  interior clipping without a synthetic clip edge, integer scale-3 one-pixel
  coverage, thin geometry, and a fully clipped destination-preserving NoOp.
  Every accepted scenario renders twice and checks native Render+Readback evidence.

## RED evidence

The first attempted public run had a test-only `Int`/`UByte` compilation mismatch;
it was corrected before considering any behavioral result. The behavioral RED was:

```sh
rtk proxy ruby /private/tmp/kanvas-w7-encoded-hairline.AwWRwQ/bounded-run.rb /private/tmp/kanvas-w7-encoded-hairline.AwWRwQ/red-public-behavior 240 ./gradlew :kanvas:test --offline --no-daemon --no-build-cache --tests org.graphiks.kanvas.surface.W7RectHairlineSurfacePixelTest -I /private/tmp/kanvas-w7-image-opacity.YmtXjI/isolated.init.gradle -Pw7.validationDir=/private/tmp/kanvas-w7-encoded-hairline.AwWRwQ/red-public-behavior --console=plain
```

It exited `child_exit=1`, `wrapper_exit=1`, `timed_out=false`. The archived XML
reports `pixel (1,1)`: expected background `[17, 61, 211, 255]`, actual red
`[239, 51, 73, 255]`. This is the current centred `Finite(1.0)` ring leaking a
pixel outward, not a setup failure.

## GREEN evidence

Math command:

```sh
rtk proxy ruby /private/tmp/kanvas-w7-encoded-hairline.AwWRwQ/bounded-run.rb /private/tmp/kanvas-w7-encoded-hairline.AwWRwQ/green-math 240 ./gradlew :math:geometry:jvmTest --offline --no-daemon --no-build-cache --console=plain
```

It exited `child_exit=0`, `wrapper_exit=0`, `timed_out=false`; all 478 tests in
39 XML classes passed, including the two new math tests. The XML was copied to
`/private/tmp/kanvas-w7-encoded-hairline.AwWRwQ/green-math/xml` (39 files) to
preserve the evidence.

Final native command, run against the exact committed-code candidate:

```sh
rtk proxy ruby /private/tmp/kanvas-w7-encoded-hairline.AwWRwQ/bounded-run.rb /private/tmp/kanvas-w7-encoded-hairline.AwWRwQ/green-final-43-commit 240 ./gradlew :kanvas:test --offline --no-daemon --no-build-cache --tests org.graphiks.kanvas.surface.W7RectHairlineSurfacePixelTest --tests org.graphiks.kanvas.surface.W7StrokeRoutingSurfacePixelTest --tests org.graphiks.kanvas.surface.W7SurfaceCompositionPixelTest --tests org.graphiks.kanvas.surface.W7GradientAlphaSurfacePixelTest -I /private/tmp/kanvas-w7-image-opacity.YmtXjI/isolated.init.gradle -Pw7.validationDir=/private/tmp/kanvas-w7-encoded-hairline.AwWRwQ/green-final-43-commit --console=plain
```

It exited `child_exit=0`, `wrapper_exit=0`, `timed_out=false` in 38 s. Its XML
contains 48 passed, 0 failed, 0 skipped tests across four classes. The mandated
summary comparison against `baseline/xml` reports 43 matched baseline identities,
no changes and no unseen failures. Archives contain XML, `events.jsonl`,
`process.log`, and `exit.json`.

## Self-review and concerns

- Geometry is domain-neutral and has no `CompositionDomain` input.
- Four disjoint bands keep every corner in exactly one band; transparent corners
  therefore cannot double-compose.
- I64 is used for `right + 1`, `bottom + 1`, and clipping before any `RectI32`
  construction.
- The W4d branch is closed to the required hard Rect hairline scope; half-integer,
  AA, path, finite-stroke, effects and non-integral-projection routes retain their
  existing preparation.
- `git diff --check` was clean. No global suite was run; the controller owns it.
- Gradle emitted its pre-existing Java restricted-native-access, `sun.misc.Unsafe`,
  deprecated-Gradle-feature and configuration-cache suggestions. No test failures,
  crashes or timeouts occurred.

## Files

- `math/geometry/src/commonMain/kotlin/org/graphiks/math/geometry/RectHairlineCoverageI32.kt`
- `math/geometry/src/commonTest/kotlin/org/graphiks/math/geometry/RectHairlineCoverageI32Test.kt`
- `gpu-plan/src/main/kotlin/org/graphiks/kanvas/gpu/plan/W4dGeneralPathPlanCompiler.kt`
- `kanvas/src/test/kotlin/org/graphiks/kanvas/surface/W7RectHairlineSurfacePixelTest.kt`
- `.superpowers/sdd/encoded-hairline-plan/task-1-report.md`
